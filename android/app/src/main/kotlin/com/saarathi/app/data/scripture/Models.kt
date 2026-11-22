package com.saarathi.app.data.scripture

import kotlinx.serialization.Serializable

data class ScriptureSource(
    val key: String, val title: String, val edition: String, val author: String, val kind: String,
    val license: String, val fileName: String, val pages: Int, val bundled: Boolean,
)

data class GitaChapter(
    val chapter: Int, val title: String, val titleEdition: String, val summary: String,
    val themes: List<String>, val verseCount: Int, val firstPageShlokas: Int?, val firstPageEdition: Int?,
)

data class Translation(val sourceKey: String, val text: String, val page: Int?)

data class GitaVerse(
    val id: Int, val chapter: Int, val verseStart: Int, val verseEnd: Int, val ref: String, val speaker: String,
    val iast: String, val devanagari: String, val pageShlokas: Int?, val pageEdition: Int?,
    val themes: List<String>, val keywords: String, val weight: Double,
) {
    val label: String get() = "Bhagavad Gita $ref"
    val isRange: Boolean get() = verseStart != verseEnd
}

data class MbPassage(
    val id: Int, val book: Int, val parva: String, val subParva: String, val section: Int, val sectionRoman: String,
    val speaker: String, val text: String, val page: Int, val gitaChapter: Int?,
) {
    val label: String get() = "Mahabharata, $parva, Section $sectionRoman"
}

/** A reference the app has verified against the local scripture database before showing it. */
@Serializable
sealed class Citation {
    @Serializable
    data class Verse(val verseId: Int, val ref: String, val translationSource: String) : Citation()

    @Serializable
    data class Passage(val passageId: Int, val label: String) : Citation()
}
