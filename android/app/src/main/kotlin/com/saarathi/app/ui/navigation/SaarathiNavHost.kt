package com.saarathi.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.saarathi.app.AppContainer
import com.saarathi.app.ui.chat.ChatScreen
import com.saarathi.app.ui.compass.DecisionCompassScreen
import com.saarathi.app.ui.daily.DailyWisdomScreen
import com.saarathi.app.ui.history.HistoryScreen
import com.saarathi.app.ui.journal.JournalEditScreen
import com.saarathi.app.ui.journal.JournalListScreen
import com.saarathi.app.ui.journey.JourneyScreen
import com.saarathi.app.ui.karma.KarmaStepsScreen
import com.saarathi.app.ui.library.LibraryScreen
import com.saarathi.app.ui.library.PdfPageScreen
import com.saarathi.app.ui.library.ReaderScreen
import com.saarathi.app.ui.onboarding.OnboardingScreen
import com.saarathi.app.ui.onboarding.SplashScreen
import com.saarathi.app.ui.saved.SavedScreen
import com.saarathi.app.ui.settings.DiagnosticsScreen
import com.saarathi.app.ui.settings.SettingsScreen
import com.saarathi.app.ui.setup.SetupScreen

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val SETUP = "setup"
    const val CHAT = "chat?conversationId={conversationId}&mode={mode}"
    fun chat(conversationId: Long = -1, mode: String = "chat") = "chat?conversationId=$conversationId&mode=$mode"
    const val HISTORY = "history"
    const val LIBRARY = "library"
    const val READER = "reader/{chapter}"
    fun reader(chapter: Int) = "reader/$chapter"
    const val PDF_PAGE = "pdf/{source}/{page}"
    fun pdfPage(source: String, page: Int) = "pdf/$source/$page"
    const val SAVED = "saved"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
    const val DAILY = "daily"
    const val JOURNAL = "journal"
    const val JOURNAL_EDIT = "journal/edit/{id}"
    fun journalEdit(id: Long = -1) = "journal/edit/$id"
    const val KARMA = "karma"
    const val COMPASS = "compass"
    const val JOURNEY = "journey"
}

@Composable
fun SaarathiNavHost(container: AppContainer, appVm: AppViewModel, navController: NavHostController = rememberNavController()) {
    val settings by appVm.settings.collectAsState()

    /** Opens a chat on top of a fresh back stack: the chat is the home screen. */
    fun openChat(conversationId: Long = -1, mode: String = "chat") =
        navController.navigate(Routes.chat(conversationId, mode)) { popUpTo(Routes.CHAT) { inclusive = true } }

    fun discuss(text: String) { container.pendingPrompt = text; openChat() }

    fun newJournalEntry(title: String, body: String) { container.pendingJournalDraft = title to body; navController.navigate(Routes.journalEdit(-1)) }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        enterTransition = { fadeIn() + slideInHorizontally { it / 8 } },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() + slideOutHorizontally { it / 8 } },
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(onFinished = {
                val next = if (!settings.onboardingDone) Routes.ONBOARDING else if (settings.modelFileName.isBlank()) Routes.SETUP else Routes.chat()
                navController.navigate(next) { popUpTo(Routes.SPLASH) { inclusive = true } }
                if (container.pendingRoute == Routes.DAILY && settings.onboardingDone) { container.pendingRoute = null; navController.navigate(Routes.DAILY) }
            })
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(container = container, onDone = {
                navController.navigate(Routes.SETUP) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.SETUP) {
            SetupScreen(container = container, appVm = appVm, onContinue = {
                navController.navigate(Routes.chat()) { popUpTo(Routes.SETUP) { inclusive = true } }
            }, onBack = { navController.popBackStack() })
        }
        composable(
            Routes.CHAT,
            arguments = listOf(navArgument("conversationId") { type = NavType.LongType; defaultValue = -1L }, navArgument("mode") { type = NavType.StringType; defaultValue = "chat" })
        ) { entry ->
            ChatScreen(
                container = container, appVm = appVm,
                conversationId = entry.arguments?.getLong("conversationId") ?: -1L,
                mode = entry.arguments?.getString("mode") ?: "chat",
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenSaved = { navController.navigate(Routes.SAVED) },
                onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenSetup = { navController.navigate(Routes.SETUP) },
                onOpenDaily = { navController.navigate(Routes.DAILY) },
                onOpenJournal = { navController.navigate(Routes.JOURNAL) },
                onOpenKarma = { navController.navigate(Routes.KARMA) },
                onOpenCompass = { navController.navigate(Routes.COMPASS) },
                onOpenJourney = { navController.navigate(Routes.JOURNEY) },
                onOpenPdf = { source, page -> navController.navigate(Routes.pdfPage(source, page)) },
                onNewConversation = { mode -> openChat(-1, mode) },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(container = container, onOpen = { id -> openChat(id) }, onBack = { navController.popBackStack() })
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(container = container, onOpenChapter = { navController.navigate(Routes.reader(it)) }, onBack = { navController.popBackStack() },
                onOpenPdf = { source, page -> navController.navigate(Routes.pdfPage(source, page)) }, onOpenJourney = { navController.navigate(Routes.JOURNEY) })
        }
        composable(Routes.READER, arguments = listOf(navArgument("chapter") { type = NavType.IntType })) { entry ->
            ReaderScreen(container = container, chapter = entry.arguments?.getInt("chapter") ?: 1,
                onOpenPdf = { source, page -> navController.navigate(Routes.pdfPage(source, page)) },
                onDiscuss = { text -> discuss(text) },
                onJournal = { title, body -> newJournalEntry(title, body) },
                onOpenJourney = { navController.navigate(Routes.JOURNEY) },
                onBack = { navController.popBackStack() })
        }
        composable(Routes.PDF_PAGE, arguments = listOf(navArgument("source") { type = NavType.StringType }, navArgument("page") { type = NavType.IntType })) { entry ->
            PdfPageScreen(container = container, source = entry.arguments?.getString("source") ?: "", page = entry.arguments?.getInt("page") ?: 1, onBack = { navController.popBackStack() })
        }
        composable(Routes.SAVED) {
            SavedScreen(container = container, onOpenPdf = { source, page -> navController.navigate(Routes.pdfPage(source, page)) }, onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(container = container, appVm = appVm, onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) }, onOpenSetup = { navController.navigate(Routes.SETUP) }, onBack = { navController.popBackStack() }, onOpenDaily = { navController.navigate(Routes.DAILY) })
        }
        composable(Routes.DAILY) {
            DailyWisdomScreen(container = container,
                onOpenPdf = { source, page -> navController.navigate(Routes.pdfPage(source, page)) },
                onDiscuss = { text -> discuss(text) },
                onBack = { if (!navController.popBackStack()) navController.navigate(Routes.chat()) })
        }
        composable(Routes.DIAGNOSTICS) {
            DiagnosticsScreen(container = container, appVm = appVm, onBack = { navController.popBackStack() })
        }
        // Phase 3
        composable(Routes.JOURNAL) {
            JournalListScreen(container = container, onOpen = { id -> navController.navigate(Routes.journalEdit(id)) }, onNew = { navController.navigate(Routes.journalEdit(-1)) }, onBack = { navController.popBackStack() })
        }
        composable(Routes.JOURNAL_EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { entry ->
            JournalEditScreen(container = container, entryId = entry.arguments?.getLong("id") ?: -1L,
                onReflect = { openChat() },
                onOpenConversation = { id -> openChat(id) },
                onBack = { navController.popBackStack() })
        }
        composable(Routes.KARMA) {
            KarmaStepsScreen(container = container, onOpenConversation = { id -> openChat(id) }, onBack = { navController.popBackStack() })
        }
        composable(Routes.COMPASS) {
            DecisionCompassScreen(container = container, onBegin = { id -> openChat(id, "arjuna") }, onBack = { navController.popBackStack() })
        }
        composable(Routes.JOURNEY) {
            JourneyScreen(container = container, onOpenChapter = { navController.navigate(Routes.reader(it)) }, onBack = { navController.popBackStack() })
        }
    }
}
