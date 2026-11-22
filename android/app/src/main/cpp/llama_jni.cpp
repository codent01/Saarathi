// JNI bridge between Kotlin (com.saarathi.app.llm.LlamaNative) and llama.cpp.
//
// Design notes
//  * One model + one context per handle. Saarathi only ever runs one conversation at a time.
//  * Generation streams tokens to Kotlin through a callback; returning false from the callback stops it.
//  * A simple prefix cache keeps the already-evaluated prompt tokens in the KV cache, so a new turn
//    that shares a prefix (system prompt + earlier history) only evaluates the new suffix.
//  * Everything runs on whatever thread Kotlin calls from (a dedicated dispatcher); nothing touches the UI.

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

#define TAG "SaarathiLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

struct Session {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    int n_ctx = 0;
    int n_threads = 0;
    std::vector<llama_token> cached;   // tokens currently evaluated in the KV cache (prefix cache)
    std::atomic<bool> cancel{false};
    std::mutex mutex;                  // one generation at a time per session
};

Session *as_session(jlong handle) { return reinterpret_cast<Session *>(handle); }

std::string jstring_to_std(JNIEnv *env, jstring s) {
    if (s == nullptr) return {};
    const char *chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars ? chars : "");
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

// JNI's NewStringUTF rejects invalid modified-UTF-8, and llama pieces can split a multibyte character.
// Encode via a byte array + String(bytes, UTF_8) to be safe.
jstring bytes_to_jstring(JNIEnv *env, const std::string &bytes) {
    jclass string_cls = env->FindClass("java/lang/String");
    jmethodID ctor = env->GetMethodID(string_cls, "<init>", "([BLjava/lang/String;)V");
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(bytes.size()));
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(bytes.size()),
                            reinterpret_cast<const jbyte *>(bytes.data()));
    jstring charset = env->NewStringUTF("UTF-8");
    auto result = static_cast<jstring>(env->NewObject(string_cls, ctor, arr, charset));
    env->DeleteLocalRef(arr);
    env->DeleteLocalRef(charset);
    env->DeleteLocalRef(string_cls);
    return result;
}

std::vector<llama_token> tokenize(const llama_vocab *vocab, const std::string &text, bool add_special) {
    int n = -llama_tokenize(vocab, text.c_str(), (int32_t) text.size(), nullptr, 0, add_special, true);
    std::vector<llama_token> tokens(n > 0 ? n : 0);
    if (n > 0) {
        int written = llama_tokenize(vocab, text.c_str(), (int32_t) text.size(), tokens.data(), n, add_special, true);
        if (written < 0) tokens.clear(); else tokens.resize(written);
    }
    return tokens;
}

std::string token_to_piece(const llama_vocab *vocab, llama_token token) {
    char buf[256];
    int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, true);
    if (n < 0) {
        std::string big(-n, '\0');
        int m = llama_token_to_piece(vocab, token, big.data(), (int32_t) big.size(), 0, true);
        if (m > 0) big.resize(m); else big.clear();
        return big;
    }
    return std::string(buf, n);
}

// Evaluate tokens [start, end) in batches. Returns false on decode failure.
bool decode_range(Session *s, const std::vector<llama_token> &tokens, size_t start, size_t end, int batch_size,
                  JNIEnv *env = nullptr, jobject callback = nullptr, jmethodID on_progress = nullptr) {
    for (size_t i = start; i < end; i += batch_size) {
        if (s->cancel.load()) return false;
        size_t n = std::min(static_cast<size_t>(batch_size), end - i);
        llama_batch batch = llama_batch_init((int32_t) n, 0, 1);
        batch.n_tokens = (int32_t) n;
        for (size_t j = 0; j < n; ++j) {
            batch.token[j] = tokens[i + j];
            batch.pos[j] = (llama_pos) (i + j);
            batch.n_seq_id[j] = 1;
            batch.seq_id[j][0] = 0;
            batch.logits[j] = 0;
        }
        // We need logits only for the very last token of the whole prompt.
        if (i + n == end) batch.logits[n - 1] = 1;
        int rc = llama_decode(s->ctx, batch);
        llama_batch_free(batch);
        if (rc != 0) {
            LOGE("llama_decode failed with %d at %zu", rc, i);
            return false;
        }
        if (env && on_progress) env->CallVoidMethod(callback, on_progress, (jint) (i + n), (jint) end);
    }
    return true;
}

} // namespace

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM *, void *) {
    llama_backend_init();
    llama_log_set([](ggml_log_level level, const char *text, void *) {
        // llama.cpp prints loading progress as lone dots; keep only real warnings/errors.
        if (text == nullptr || text[0] == '.' || text[0] == '\n' || text[0] == '\0') return;
        if (level >= GGML_LOG_LEVEL_WARN) __android_log_write(ANDROID_LOG_WARN, TAG, text);
    }, nullptr);
    return JNI_VERSION_1_6;
}

JNIEXPORT jstring JNICALL
Java_com_saarathi_app_llm_LlamaNative_systemInfo(JNIEnv *env, jobject) {
    return bytes_to_jstring(env, llama_print_system_info());
}

JNIEXPORT jlong JNICALL
Java_com_saarathi_app_llm_LlamaNative_loadModel(JNIEnv *env, jobject, jstring jpath, jint n_ctx, jint n_threads, jint n_batch, jboolean repack) {
    std::string path = jstring_to_std(env, jpath);
    auto *s = new Session();

    llama_model_params mparams = llama_model_default_params();
    mparams.load_mode = LLAMA_LOAD_MODE_MMAP;
    // Keep weights file-backed: repacking copies 2.5 GB into anonymous memory, which phones then swap to zram.
    mparams.use_extra_bufts = repack;   // Kotlin enables repacking only for models small enough to duplicate in RAM
    mparams.n_gpu_layers = 0;   // CPU only in Phase 1
    s->model = llama_model_load_from_file(path.c_str(), mparams);
    if (s->model == nullptr) {
        LOGE("failed to load model from %s", path.c_str());
        delete s;
        return 0;
    }
    s->vocab = llama_model_get_vocab(s->model);

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = (uint32_t) n_ctx;
    cparams.n_batch = (uint32_t) n_batch;
    cparams.n_ubatch = (uint32_t) std::min(n_batch, 256);
    cparams.n_threads = n_threads;
    cparams.n_threads_batch = n_threads;
    // flash_attn_type is left at its default (auto); the CPU backend picks the right path.
    // Gemma 3 uses sliding-window attention on most layers; a window-sized cache cuts KV memory several-fold.
    cparams.swa_full = false;
    s->ctx = llama_init_from_model(s->model, cparams);
    if (s->ctx == nullptr) {
        LOGE("failed to create context (n_ctx=%d)", n_ctx);
        llama_model_free(s->model);
        delete s;
        return 0;
    }
    s->n_ctx = (int) llama_n_ctx(s->ctx);
    s->n_threads = n_threads;
    LOGI("model loaded: ctx=%d threads=%d", s->n_ctx, n_threads);
    return reinterpret_cast<jlong>(s);
}

JNIEXPORT void JNICALL
Java_com_saarathi_app_llm_LlamaNative_freeModel(JNIEnv *, jobject, jlong handle) {
    auto *s = as_session(handle);
    if (s == nullptr) return;
    s->cancel.store(true);
    std::lock_guard<std::mutex> lock(s->mutex);
    if (s->ctx) llama_free(s->ctx);
    if (s->model) llama_model_free(s->model);
    delete s;
}

JNIEXPORT jstring JNICALL
Java_com_saarathi_app_llm_LlamaNative_modelInfo(JNIEnv *env, jobject, jlong handle) {
    auto *s = as_session(handle);
    if (s == nullptr) return bytes_to_jstring(env, "{}");
    char desc[256];
    llama_model_desc(s->model, desc, sizeof(desc));
    std::string json = "{";
    json += "\"description\":\"" + std::string(desc) + "\",";
    json += "\"sizeBytes\":" + std::to_string(llama_model_size(s->model)) + ",";
    json += "\"params\":" + std::to_string(llama_model_n_params(s->model)) + ",";
    json += "\"ctxTrain\":" + std::to_string(llama_model_n_ctx_train(s->model)) + ",";
    json += "\"ctx\":" + std::to_string(s->n_ctx) + ",";
    json += "\"threads\":" + std::to_string(s->n_threads);
    json += "}";
    return bytes_to_jstring(env, json);
}

// roles/contents are parallel arrays. Uses the chat template embedded in the GGUF.
JNIEXPORT jstring JNICALL
Java_com_saarathi_app_llm_LlamaNative_applyChatTemplate(JNIEnv *env, jobject, jlong handle,
                                                        jobjectArray jroles, jobjectArray jcontents, jboolean add_assistant) {
    auto *s = as_session(handle);
    if (s == nullptr) return bytes_to_jstring(env, "");
    jsize n = env->GetArrayLength(jroles);
    std::vector<std::string> roles(n), contents(n);
    std::vector<llama_chat_message> msgs(n);
    for (jsize i = 0; i < n; ++i) {
        auto r = static_cast<jstring>(env->GetObjectArrayElement(jroles, i));
        auto c = static_cast<jstring>(env->GetObjectArrayElement(jcontents, i));
        roles[i] = jstring_to_std(env, r);
        contents[i] = jstring_to_std(env, c);
        env->DeleteLocalRef(r);
        env->DeleteLocalRef(c);
        msgs[i].role = roles[i].c_str();
        msgs[i].content = contents[i].c_str();
    }
    const char *tmpl = llama_model_chat_template(s->model, nullptr);
    std::string buf(16384, '\0');
    int len = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), add_assistant, buf.data(), (int32_t) buf.size());
    if (len < 0) {
        // Unknown template: fall back to a plain ChatML-style layout.
        std::string out;
        for (auto &m : msgs) {
            out += "<|im_start|>" + std::string(m.role) + "\n" + m.content + "<|im_end|>\n";
        }
        if (add_assistant) out += "<|im_start|>assistant\n";
        return bytes_to_jstring(env, out);
    }
    if ((size_t) len > buf.size()) {
        buf.resize(len);
        len = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), add_assistant, buf.data(), (int32_t) buf.size());
    }
    buf.resize(len > 0 ? len : 0);
    return bytes_to_jstring(env, buf);
}

JNIEXPORT jint JNICALL
Java_com_saarathi_app_llm_LlamaNative_tokenCount(JNIEnv *env, jobject, jlong handle, jstring jtext) {
    auto *s = as_session(handle);
    if (s == nullptr) return 0;
    return (jint) tokenize(s->vocab, jstring_to_std(env, jtext), true).size();
}

JNIEXPORT void JNICALL
Java_com_saarathi_app_llm_LlamaNative_cancel(JNIEnv *, jobject, jlong handle) {
    auto *s = as_session(handle);
    if (s) s->cancel.store(true);
}

JNIEXPORT void JNICALL
Java_com_saarathi_app_llm_LlamaNative_clearCache(JNIEnv *, jobject, jlong handle) {
    auto *s = as_session(handle);
    if (s == nullptr) return;
    std::lock_guard<std::mutex> lock(s->mutex);
    llama_memory_clear(llama_get_memory(s->ctx), true);
    s->cached.clear();
}

// Returns number of generated tokens, or a negative error code:
//  -1 prompt too long, -2 decode failure, -3 busy.
JNIEXPORT jint JNICALL
Java_com_saarathi_app_llm_LlamaNative_generate(JNIEnv *env, jobject, jlong handle, jstring jprompt,
                                               jint max_tokens, jfloat temperature, jfloat top_p, jint top_k,
                                               jfloat repeat_penalty, jobjectArray jstops, jobject callback) {
    auto *s = as_session(handle);
    if (s == nullptr) return -2;
    std::unique_lock<std::mutex> lock(s->mutex, std::try_to_lock);
    if (!lock.owns_lock()) return -3;
    s->cancel.store(false);

    std::string prompt = jstring_to_std(env, jprompt);
    std::vector<std::string> stops;
    if (jstops) {
        jsize n = env->GetArrayLength(jstops);
        for (jsize i = 0; i < n; ++i) {
            auto js = static_cast<jstring>(env->GetObjectArrayElement(jstops, i));
            stops.push_back(jstring_to_std(env, js));
            env->DeleteLocalRef(js);
        }
    }

    jclass cb_cls = env->GetObjectClass(callback);
    jmethodID on_token = env->GetMethodID(cb_cls, "onToken", "(Ljava/lang/String;)Z");
    jmethodID on_progress = env->GetMethodID(cb_cls, "onPromptProgress", "(II)V");

    std::vector<llama_token> tokens = tokenize(s->vocab, prompt, true);
    if (tokens.empty()) return -1;
    if ((int) tokens.size() + 8 >= s->n_ctx) {
        LOGW("prompt of %zu tokens does not fit in ctx %d", tokens.size(), s->n_ctx);
        return -1;
    }
    // Leave room for the answer; Kotlin trims history so this rarely triggers.
    int budget = s->n_ctx - (int) tokens.size() - 4;
    if (max_tokens > budget) max_tokens = budget;

    // ---- prefix cache -------------------------------------------------------------------
    size_t common = 0;
    while (common < s->cached.size() && common < tokens.size() && s->cached[common] == tokens[common]) ++common;
    // Always re-evaluate at least the final prompt token so we get fresh logits.
    if (common == tokens.size()) common = tokens.size() - 1;
    llama_memory_t mem = llama_get_memory(s->ctx);
    if (common == 0) {
        llama_memory_clear(mem, true);
    } else {
        llama_memory_seq_rm(mem, 0, (llama_pos) common, -1);
    }
    LOGI("prompt tokens=%zu reused=%zu", tokens.size(), common);
    if (on_progress) env->CallVoidMethod(callback, on_progress, (jint) common, (jint) tokens.size());

    if (!decode_range(s, tokens, common, tokens.size(), 512, env, callback, on_progress)) {
        s->cached.clear();
        llama_memory_clear(mem, true);
        return s->cancel.load() ? 0 : -2;
    }
    s->cached.assign(tokens.begin(), tokens.end());
    if (on_progress) env->CallVoidMethod(callback, on_progress, (jint) tokens.size(), (jint) tokens.size());

    // ---- sampler ------------------------------------------------------------------------
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(llama_vocab_n_tokens(s->vocab), 64, repeat_penalty, 0.0f, 0.0f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    // ---- generation loop ----------------------------------------------------------------
    int generated = 0;
    std::string tail;  // recent text used for stop-sequence matching
    size_t max_stop = 0;
    for (auto &st : stops) max_stop = std::max(max_stop, st.size());

    while (generated < max_tokens && !s->cancel.load()) {
        llama_token tok = llama_sampler_sample(sampler, s->ctx, -1);
        llama_sampler_accept(sampler, tok);
        if (llama_vocab_is_eog(s->vocab, tok)) break;

        std::string piece = token_to_piece(s->vocab, tok);
        generated++;

        bool stop_hit = false;
        if (!stops.empty()) {
            tail += piece;
            for (auto &st : stops) {
                if (tail.find(st) != std::string::npos) { stop_hit = true; break; }
            }
            if (tail.size() > max_stop * 4) tail.erase(0, tail.size() - max_stop * 2);
        }
        if (stop_hit) break;

        jstring jpiece = bytes_to_jstring(env, piece);
        jboolean cont = env->CallBooleanMethod(callback, on_token, jpiece);
        env->DeleteLocalRef(jpiece);
        if (env->ExceptionCheck()) { env->ExceptionClear(); break; }
        if (!cont) break;

        // feed the token back
        llama_batch batch = llama_batch_get_one(&tok, 1);
        // llama_batch_get_one assigns positions sequentially from the memory state; we track our own.
        int rc = llama_decode(s->ctx, batch);
        if (rc != 0) {
            LOGE("decode during generation failed: %d", rc);
            s->cached.clear();
            llama_memory_clear(mem, true);
            llama_sampler_free(sampler);
            return -2;
        }
        s->cached.push_back(tok);
    }

    llama_sampler_free(sampler);
    return generated;
}

} // extern "C"
