package com.saarathi.app.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saarathi.app.AppContainer
import com.saarathi.app.ui.components.KrishnaAvatar
import com.saarathi.app.ui.theme.SaarathiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val c = SaarathiTheme.colors
    var shown by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(900), label = "alpha")
    val scale by animateFloatAsState(if (shown) 1f else 0.92f, tween(900), label = "scale")
    LaunchedEffect(Unit) { shown = true; delay(1500); onFinished() }
    Box(Modifier.fillMaxSize().background(c.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(alpha).scale(scale)) {
            KrishnaAvatar(size = 160.dp, glow = true)
            Spacer(Modifier.height(28.dp))
            Text("SAARATHI", style = MaterialTheme.typography.displayLarge.copy(letterSpacing = 6.sp), color = c.gold, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Every Arjuna needs a Saarathi.", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
        }
    }
}

private data class Page(val title: String, val body: String)

private val PAGES = listOf(
    Page("Meet your Saarathi", "Saarathi is a private companion. You talk to Krishna, an AI interpretation inspired by his wisdom in the Bhagavad Gita and the Mahabharata, about whatever is on your mind: worries, work, relationships, anger, purpose, or just your day."),
    Page("Grounded in your scriptures", "Every verse the app shows is pulled from the scripture library on your phone and checked before it appears. The companion may interpret and explain, but Sanskrit, verse numbers and translations always come from the sources, never from the AI's imagination."),
    Page("Completely offline", "The language model, the scripture index and all your conversations live on this phone. Saarathi has no internet permission at all. Airplane mode changes nothing."),
    Page("An interpretation, not a deity", "Krishna here is a respectful creative interpretation, not a literal divine presence. It has no supernatural knowledge, cannot predict your future, and is not a substitute for doctors, counsellors or the people who love you."),
    Page("Your data, your control", "Chats, bookmarks and memories stay in app storage. Memory of past conversations is off until you switch it on, and you can view, edit or delete anything at any time."),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(container: AppContainer, onDone: () -> Unit) {
    val c = SaarathiTheme.colors
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(c.background).padding(24.dp)) {
        Spacer(Modifier.height(32.dp))
        KrishnaAvatar(size = 88.dp, glow = true, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(24.dp))
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { i ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(PAGES[i].title, style = MaterialTheme.typography.headlineMedium, color = c.gold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text(PAGES[i].body, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(PAGES.size) { i ->
                Box(Modifier.padding(4.dp).size(if (i == pager.currentPage) 10.dp else 7.dp).clip(CircleShape).background(if (i == pager.currentPage) c.accent else c.krishnaBubbleBorder))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { scope.launch { container.settings.update { it.copy(onboardingDone = true) }; onDone() } }) { Text("Skip", color = c.textSecondary) }
            Spacer(Modifier.width(8.dp))
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                if (pager.currentPage < PAGES.size - 1) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                else scope.launch { container.settings.update { it.copy(onboardingDone = true) }; onDone() }
            }) { Text(if (pager.currentPage < PAGES.size - 1) "Next" else "Begin") }
        }
    }
}
