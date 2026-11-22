package com.saarathi.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saarathi.app.data.scripture.ScriptureRepository
import com.saarathi.app.ui.theme.SaarathiTheme

/**
 * Verified verse card. Everything on it comes from the local scripture database, never from the model.
 * Sanskrit, transliteration, translation (with its source) and commentary are kept visually distinct.
 */
@Composable
fun ShlokaCard(
    card: ScriptureRepository.VerseCard,
    bookmarked: Boolean,
    onBookmark: () -> Unit,
    onOpenPdf: ((source: String, page: Int) -> Unit)?,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    header: String? = null,
) {
    val c = SaarathiTheme.colors
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val clipboard = LocalClipboardManager.current
    val v = card.verse

    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(c.shlokaCard, c.surface)))
            .border(1.dp, c.shlokaBorder.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
            .clickable { expanded = !expanded }
            .padding(14.dp)
    ) {
        if (header != null) {
            Text(header, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            Spacer(Modifier.height(4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Bhagavad Gita ${v.ref}", style = MaterialTheme.typography.titleSmall, color = c.gold, fontWeight = FontWeight.SemiBold)
                val who = if (v.speaker.isNotBlank()) "Spoken by ${v.speaker}" else ""
                val ch = if (card.chapterTitle.isNotBlank()) "Chapter ${v.chapter}: ${card.chapterTitle}" else "Chapter ${v.chapter}"
                Text(listOf(ch, who).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = c.textSecondary)
        }
        Spacer(Modifier.height(10.dp))
        Text(v.devanagari, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 30.sp), color = c.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(v.iast, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), color = c.textSecondary)
        Spacer(Modifier.height(10.dp))
        if (card.translation != null) {
            Text(card.translation.text, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(card.translationLabel + (card.translation.page?.let { " · page $it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
        } else {
            Text("No verified translation is available for this verse in the installed editions.", style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), color = c.textSecondary)
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                if (card.otherTranslations.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    for (t in card.otherTranslations) {
                        Text(t.text, style = MaterialTheme.typography.bodySmall, color = c.textPrimary.copy(alpha = 0.9f))
                        Text(when (t.sourceKey) { "besant1922" -> "Annie Besant, 1922 (public domain)"; "as_it_is" -> "Bhagavad-gītā As It Is (your edition)"; else -> t.sourceKey }, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                        Spacer(Modifier.height(6.dp))
                    }
                }
                if (card.commentary != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Commentary (purport) from your imported edition — this is the commentator's explanation, not the verse itself:", style = MaterialTheme.typography.labelSmall, color = c.gold)
                    Spacer(Modifier.height(4.dp))
                    Text(card.commentary.text.take(1600) + if (card.commentary.text.length > 1600) "…" else "", style = MaterialTheme.typography.bodySmall, color = c.textPrimary.copy(alpha = 0.9f))
                }
                Spacer(Modifier.height(8.dp))
                val pages = buildList {
                    v.pageShlokas?.let { add("Slokas PDF p.$it") }
                    (card.translation?.page ?: v.pageEdition)?.let { add("As It Is PDF p.$it") }
                }
                if (pages.isNotEmpty()) Text("Source pages: " + pages.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                val text = "Bhagavad Gita ${v.ref}\n${v.devanagari}\n${v.iast}\n\n${card.translation?.text ?: ""}\n(${card.translationLabel})"
                clipboard.setText(AnnotatedString(text))
            }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy verse", tint = c.textSecondary) }
            IconButton(onClick = onBookmark) {
                Icon(if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = "Bookmark verse", tint = if (bookmarked) c.accent else c.textSecondary)
            }
            if (onOpenPdf != null) {
                val editionPage = card.translation?.takeIf { it.sourceKey == "as_it_is" }?.page ?: v.pageEdition
                val target = when {
                    editionPage != null -> "gita" to editionPage
                    v.pageShlokas != null -> "shlokas" to v.pageShlokas
                    else -> null
                }
                if (target != null) {
                    IconButton(onClick = { onOpenPdf(target.first, target.second) }) { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Open in PDF", tint = c.textSecondary) }
                    Spacer(Modifier.width(2.dp))
                    Text("Open page", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                }
            }
        }
    }
}
