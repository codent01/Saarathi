package com.saarathi.app.rag

import java.text.Normalizer

/**
 * BERT uncased WordPiece tokenizer, matching HuggingFace's BertWordPieceTokenizer with
 * lowercase=true and strip_accents=true, which is what the compiler used to embed the verses.
 */
class WordPieceTokenizer(vocabLines: List<String>) {
    private val vocab: Map<String, Int> = vocabLines.withIndex().associate { (i, w) -> w to i }
    val clsId = vocab["[CLS]"] ?: 101
    val sepId = vocab["[SEP]"] ?: 102
    val unkId = vocab["[UNK]"] ?: 100
    val padId = vocab["[PAD]"] ?: 0

    fun encode(text: String, maxLen: Int = 256): IntArray {
        val ids = ArrayList<Int>(64)
        ids += clsId
        for (word in basicTokenize(text)) {
            if (ids.size >= maxLen - 1) break
            for (id in wordPiece(word)) {
                if (ids.size >= maxLen - 1) break
                ids += id
            }
        }
        ids += sepId
        return ids.toIntArray()
    }

    private fun basicTokenize(text: String): List<String> {
        val cleaned = StringBuilder()
        for (ch in text) {
            val code = ch.code
            if (code == 0 || code == 0xFFFD || Character.isISOControl(ch) && ch != '\n' && ch != '\t') continue
            cleaned.append(if (ch.isWhitespace()) ' ' else ch)
        }
        // lowercase + strip accents (NFD, drop combining marks)
        val lowered = Normalizer.normalize(cleaned.toString().lowercase(), Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        val out = ArrayList<String>()
        val cur = StringBuilder()
        fun flush() { if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() } }
        for (ch in lowered) {
            when {
                ch.isWhitespace() -> flush()
                isPunctuation(ch) || isCjk(ch) -> { flush(); out += ch.toString() }
                else -> cur.append(ch)
            }
        }
        flush()
        return out
    }

    private fun wordPiece(word: String): List<Int> {
        if (word.length > 100) return listOf(unkId)
        val out = ArrayList<Int>(4)
        var start = 0
        while (start < word.length) {
            var end = word.length
            var found: Int? = null
            while (start < end) {
                val piece = (if (start > 0) "##" else "") + word.substring(start, end)
                val id = vocab[piece]
                if (id != null) { found = id; break }
                end--
            }
            if (found == null) return listOf(unkId)
            out += found
            start = end
        }
        return out
    }

    private fun isPunctuation(ch: Char): Boolean {
        val c = ch.code
        if ((c in 33..47) || (c in 58..64) || (c in 91..96) || (c in 123..126)) return true
        val t = Character.getType(ch)
        return t == Character.CONNECTOR_PUNCTUATION.toInt() || t == Character.DASH_PUNCTUATION.toInt() || t == Character.START_PUNCTUATION.toInt() ||
            t == Character.END_PUNCTUATION.toInt() || t == Character.INITIAL_QUOTE_PUNCTUATION.toInt() || t == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
            t == Character.OTHER_PUNCTUATION.toInt()
    }

    private fun isCjk(ch: Char): Boolean {
        val c = ch.code
        return (c in 0x4E00..0x9FFF) || (c in 0x3400..0x4DBF) || (c in 0xF900..0xFAFF)
    }
}
