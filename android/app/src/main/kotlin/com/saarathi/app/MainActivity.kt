package com.saarathi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.content.Intent
import androidx.activity.enableEdgeToEdge
import com.saarathi.app.daily.DailyWisdom
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.saarathi.app.ui.navigation.AppViewModel
import com.saarathi.app.ui.navigation.SaarathiNavHost
import com.saarathi.app.ui.theme.SaarathiTheme
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setTheme(R.style.Theme_Saarathi)
        val container = (application as SaarathiApp).container
        intent?.getStringExtra(DailyWisdom.EXTRA_ROUTE)?.let { container.pendingRoute = it }
        setContent {
            val appVm: AppViewModel = viewModel(factory = AppViewModel.factory(container))
            val settings by appVm.settings.collectAsState()
            SaarathiTheme(themeKey = settings.theme, textScale = settings.textScale) {
                SaarathiNavHost(container = container, appVm = appVm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(DailyWisdom.EXTRA_ROUTE)?.let { (application as SaarathiApp).container.pendingRoute = it }
    }
}
