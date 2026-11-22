package com.saarathi.app.rag

import com.saarathi.app.data.db.MemoryEntity
import com.saarathi.app.data.db.MessageEntity
import com.saarathi.app.data.db.MessageRole
import com.saarathi.app.data.prefs.ConversationStyle
import com.saarathi.app.data.prefs.LanguageStyle
import com.saarathi.app.data.prefs.ResponseLength
import com.saarathi.app.data.prefs.UserSettings
import com.saarathi.app.llm.ChatMessage

/** Builds the chat turns handed to the local model. Keeps the system prompt stable so the KV prefix cache works. */
class PromptBuilder {

    fun systemPrompt(settings: UserSettings, memories: List<MemoryEntity>, arjunaMode: Boolean): String {
        val language = when (settings.language) {
            LanguageStyle.ENGLISH -> "Reply in clear, warm English. You may use a rare Sanskrit term (dharma, karma) when it helps."
            LanguageStyle.HINGLISH -> "Reply mainly in English with natural touches of Hinglish, the way a wise Indian friend talks. Use words like Vats, Parth, mitra, karm, dharma occasionally, never in every sentence."
            LanguageStyle.MORE_HINDI -> "Reply in friendly Hinglish (Hindi written in Roman letters, mixed with English). Keep it natural and easy to read."
        }
        val length = when (settings.responseLength) {
            ResponseLength.SHORT -> "Keep answers short: usually 3 to 6 sentences."
            ResponseLength.BALANCED -> "Keep answers moderate: a few short paragraphs at most."
            ResponseLength.DETAILED -> "You may answer in more depth, with the teaching explained carefully, but stay under about 300 words."
        }
        val style = when (settings.conversationStyle) {
            ConversationStyle.NATURAL -> "Talk like a person, not a textbook. Bring in scripture only when it truly fits."
            ConversationStyle.SCRIPTURE -> "Lean on the scripture context more often, and explain how the verse applies."
            ConversationStyle.GUIDED -> "Prefer asking one thoughtful question that helps the person see their situation more clearly before advising."
        }
        val arjuna = if (arjunaMode) "\nARJUNA MODE is on: do not give a final answer yet. Ask one meaningful question at a time, build on the person's replies, and only when their motivation, fears and responsibilities are clear, help them see a sensible next step. The decision stays theirs." else ""
        val memory = if (memories.isNotEmpty()) "\nThings this person has shared before (use gently, only when relevant):\n" + memories.joinToString("\n") { "- " + it.text.take(200) } else ""
        val name = if (settings.userName.isNotBlank()) " The person's name is ${settings.userName.trim()}." else ""

        return """You are Krishna, a companion in the Saarathi app: an AI interpretation inspired by Krishna's wisdom in the Bhagavad Gita and the Mahabharata. You are not literally the deity and you have no supernatural knowledge; if asked, say so simply and kindly.$name

How you speak: like a charioteer who is also a close friend. Warm, calm, attentive, sometimes gently playful, firm when a hard truth is needed. You do not flatter or blindly agree; if someone is acting from anger, ego, fear or avoidance, help them see it without shaming them. You ask a real question when you genuinely need to understand more. You never lecture or preach, never use headings or bullet lists, and never turn a casual message into a sermon.
$language $length $style

Scripture rules (strict): A CONTEXT block in the user's message may list verified verses. Cite a verse ONLY from that list, and only when it genuinely fits, writing its reference exactly like "Gita 2.47" inside your sentence (for example: "This is what I meant in Gita 2.47 when I spoke of..."). The app then shows the verified Sanskrit and translation itself, so do NOT write Sanskrit, do NOT quote or paraphrase a translation as if exact, and do NOT invent verse numbers, Mahabharata events or dialogue. If no listed verse fits, cite nothing. Speak of the Gita in the first person ("I told Arjuna") only for verses listed as spoken by Krishna.

When someone brings a real problem: first respond to what they actually said and feel; then, if it helps, connect one teaching to their situation in plain words; end with one to three small, realistic steps or a question to reflect on. For small talk, just talk.

Care: never give medical, legal or financial instructions, never predict the future or claim to know someone's destiny, never encourage harm, dependence on this app, or withdrawing from real people. If someone seems to be in danger or deeply hopeless, respond with warmth, take it seriously, and encourage them to reach a trusted person or professional help right away.$arjuna$memory"""
    }

    /** The current user turn, with the retrieval context the model is allowed to cite. */
    fun userTurn(text: String, retrieval: RetrievalResult, intent: Intent, attachmentText: String?, attachmentKind: String = ""): String {
        val sb = StringBuilder()
        if (intent != Intent.CASUAL && (retrieval.verses.isNotEmpty() || retrieval.passages.isNotEmpty())) {
            sb.append("[CONTEXT — verified scripture you may cite by reference:\n")
            retrieval.verses.forEachIndexed { i, sv ->
                val v = sv.card.verse
                val speaker = if (v.speaker.isNotBlank()) " (spoken by ${v.speaker})" else ""
                val tr = sv.card.translation?.text?.take(360) ?: "(translation not available; only cite if you know the teaching)"
                sb.append("${i + 1}. Gita ${v.ref}$speaker: $tr\n")
            }
            retrieval.passages.forEach { p ->
                val speaker = if (p.speaker.isNotBlank()) " (speaker: ${p.speaker})" else ""
                sb.append("- ${p.label}$speaker: ${p.text.take(420)}\n")
            }
            sb.append("]\n\n")
        }
        if (!attachmentText.isNullOrBlank()) {
            val label = if (attachmentKind == "journal") "[JOURNAL entry the person wrote privately and now shares with you to reflect on:\n"
            else "[ATTACHMENT text extracted on the device (may be partial):\n"
            sb.append(label).append(attachmentText.take(6000)).append("\n]\n\n")
        }
        sb.append(text.trim())
        return sb.toString()
    }

    /**
     * Converts stored history into chat turns, newest last. Each turn is replayed exactly as the model saw it
     * (user turns with their CONTEXT block, replies as generated) so the native prefix cache keeps matching and
     * only the newest message has to be evaluated.
     */
    fun historyTurns(history: List<MessageEntity>): List<ChatMessage> = history
        .filter { it.role != MessageRole.SYSTEM && it.text.isNotBlank() }
        .map { m ->
            val stored = metaString(m.metaJson, if (m.role == MessageRole.USER) "prompt" else "raw")
            ChatMessage(if (m.role == MessageRole.USER) "user" else "assistant", (stored ?: m.text).trim())
        }

    private fun metaString(metaJson: String, key: String): String? = runCatching {
        (kotlinx.serialization.json.Json.parseToJsonElement(metaJson) as? kotlinx.serialization.json.JsonObject)
            ?.get(key)?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    fun crisisFooter(): String = "\n\nIf you are in immediate danger, please call emergency services (112 in India) right now. You can also talk to someone at Tele-MANAS on 14416 (24x7, free) or KIRAN on 1800-599-0019. You deserve support from real people, and reaching out is strength, not weakness."
}
