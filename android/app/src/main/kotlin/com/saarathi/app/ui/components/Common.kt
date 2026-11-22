package com.saarathi.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saarathi.app.R
import com.saarathi.app.ui.theme.SaarathiTheme

@Composable
fun KrishnaAvatar(size: Dp, modifier: Modifier = Modifier, glow: Boolean = false) {
    val c = SaarathiTheme.colors
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (glow) {
            Box(
                Modifier.size(size + 10.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.55f), Color.Transparent)))
            )
        }
        Image(
            painter = painterResource(R.drawable.krishna_avatar),
            contentDescription = "Krishna",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape).border(1.dp, c.gold.copy(alpha = 0.7f), CircleShape),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaarathiTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    val c = SaarathiTheme.colors
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = c.surface, titleContentColor = c.textPrimary, navigationIconContentColor = c.textPrimary, actionIconContentColor = c.textPrimary),
    )
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = SaarathiTheme.colors
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface)
            .border(1.dp, c.krishnaBubbleBorder, RoundedCornerShape(16.dp)).padding(16.dp)
    ) { content() }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = SaarathiTheme.colors.gold, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
}

@Composable
fun StatusRow(label: String, value: String, ok: Boolean? = null) {
    val c = SaarathiTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (ok != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (ok) c.success else c.danger))
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
    }
}

@Composable
fun OfflineBadge() {
    val c = SaarathiTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(c.success))
        Spacer(Modifier.width(5.dp))
        Text("Offline", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
    }
}
