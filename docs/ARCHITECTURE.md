# Architecture

## Overview

```
 user message
     │
     ▼
 QueryAnalyzer (rules + lexicon.json)  ──► intent, themes, keywords, explicit refs, character names
     │
     ▼
 Retriever (scripture.db)              ──► 0–4 verified verse cards, 0–2 Mahabharata passages
     │
     ▼
 PromptBuilder                         ──► system prompt (stable) + history + user turn with CONTEXT block
     │
     ▼
 LlamaEngine ──JNI──► llama.cpp        ──► streamed tokens (prefix cache keeps old turns in KV memory)
     │
     ▼
 KrishnaOrchestrator                   ──► verifies "Gita c.v" references against the DB, stores message + citations
     │
     ▼
 ChatScreen                            ──► bubbles + ShlokaCards rendered from the DB, never from model text
```

## Modules (package `com.saarathi.app`)

| Package | Responsibility |
|---|---|
| `AppContainer` | Lazy hand-rolled dependency graph; one instance per process |
| `data.prefs` | DataStore-backed `UserSettings` (theme, language, lengths, model, linked PDFs, memory flag) |
| `data.db` | Room (schema v2): conversations, messages (with verified citation JSON), bookmarks, memories, journal entries, Karma Steps, verse reads |
| `karma`, `compass`, `journey` | Pure Phase 3 logic: list-of-steps extraction, Decision Compass composition, journey progress and reflection prompts |
| `data.scripture` | `ScriptureDatabase` (raw SQLite over the bundled `scripture.db`, FTS4) and `ScriptureRepository` (translation preference, verse cards, edition pack import) |
| `data.model` | GGUF model files in app storage, device/RAM report, CPU feature check |
| `llm` | `LlamaNative` JNI surface, `LlamaEngine` (single native thread, streaming `Flow`) |
| `rag` | `Lexicon`, `QueryAnalyzer`, `Retriever`, `PromptBuilder`, `KrishnaOrchestrator` |
| `ingest` | `AttachmentProcessor`: ML Kit OCR (Latin + Devanagari), PdfBox text extraction, text files |
| `ui` | Compose screens: splash, onboarding, setup, chat, history, library, reader, PDF page, saved, settings, diagnostics, daily wisdom, journal, Karma Steps, Decision Compass, Gita journey |

## Scripture database (`scripture.db`)

| Table | Content |
|---|---|
| `sources` | The four sources with license notes and whether they are bundled |
| `gita_chapters` | Title, summary, themes, verse counts, first pages |
| `gita_verses` | One row per printed verse (combined verses like 1.21-22 are one row): IAST, Devanagari, speaker, page in each PDF, curated themes, weight |
| `gita_translations` | `(verse_id, source_key)` → text, page. Bundled: `besant1922`. Imported: `as_it_is` |
| `gita_commentary` | Purports from the imported edition only |
| `mb_passages` | 9.5k Ganguli paragraphs with book, parva, section, speaker, page, Gita chapter marker |
| `gita_fts`, `mb_fts` | FTS4 (unicode61) over translations + notes and over passages |
| `gita_embeddings` | 384-float MiniLM vector per verse (translation + themes + note), used for cosine re-ranking |

Devanagari is generated from the IAST of the "Slokas Only" PDF with the `indic_transliteration` library after joining
sandhi-bound words; the Macmillan scan's Devanagari is never OCR'd because the error rate would be unacceptable.

## Retrieval scoring

`rag/RetrievalScoring.kt` holds the pure functions: theme overlap (primary, scaled by the verse's life-guidance
weight), FTS keyword hits decaying with rank, and embedding cosine above a 0.30 floor (worth at most about one theme
match). The query is embedded on the phone by `rag/Embedder.kt` (ONNX Runtime, `WordPieceTokenizer.kt` mirrors the
HuggingFace BERT tokenizer) and compared against the precomputed verse vectors loaded once from `gita_embeddings`.

## Daily Wisdom

`daily/DailyWisdom.kt` picks the verse of the day from verses with weight >= 0.8 using the epoch day and a fixed
stride, so every phone shows the same verse without any server. A WorkManager periodic worker posts a local
notification at the user's hour; tapping it opens the `daily` route through `AppContainer.pendingRoute`.

## Citation verification

1. The prompt lists candidate verses as `Gita 2.47 (spoken by Krishna): <translation>`.
2. The model is told to cite only by reference, never to write Sanskrit or quote translations.
3. After generation, `KrishnaOrchestrator.verifyCitations` finds every `Gita c.v` in the reply, looks it up in
   `gita_verses` (ranges included) and keeps at most three that exist. A verse outside the offered context is kept
   only if it has a translation, so a card is never empty.
4. `ShlokaCard` renders from the database row and labels the translation source and page.
5. If the model cited nothing but the top retrieved verse scored high, the card is shown as "A related teaching",
   separate from the reply, so the reply text is never implied to quote it.

## Native layer

`llama_jni.cpp` keeps one model + one context. `generate()` tokenizes the full templated prompt, reuses the longest
common prefix already in KV memory (`llama_memory_seq_rm` beyond it), decodes the rest in batches of 512, then samples
with penalties → top-k → top-p → temperature. Tokens are pushed to Kotlin through a `TokenCallback`; returning false
stops. All native calls run on one `saarathi-llama` thread.

The arm64 build uses `-march=armv8.2-a+dotprod+fp16`. `CpuFeatures` checks `/proc/cpuinfo` for `asimddp` and the app
refuses to load a model on older cores instead of crashing.

## Privacy and offline guarantees

* `AndroidManifest.xml` removes `INTERNET` and `ACCESS_NETWORK_STATE` with `tools:node="remove"`, which also strips
  them from ML Kit and Coil. The installed app cannot open a socket.
* ML Kit models are the bundled variants (`text-recognition`, `text-recognition-devanagari`), not the Play-services
  downloadable ones.
* No analytics, no crash reporting SDKs, `allowBackup=false`.
