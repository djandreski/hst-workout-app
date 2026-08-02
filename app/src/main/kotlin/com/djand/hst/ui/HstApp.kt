package com.djand.hst.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.djand.hst.data.settings.ThemeMode
import com.djand.hst.ui.navigation.HstNavHost
import com.djand.hst.ui.setup.SetupScreen
import com.djand.hst.ui.theme.HstTheme

/**
 * The single root composable of the app.
 *
 * Until the settings have loaded, a spinner is shown (this takes a frame or two at
 * most). On first launch — or after "Reset cycle" — the setup wizard replaces the
 * whole UI; once setup is complete the normal NavHost appears. Because the swap is
 * driven by the persisted [com.djand.hst.data.settings.AppSettings.setupComplete]
 * flag, finishing the wizard or resetting progress switches screens automatically.
 */
@Composable
fun HstApp(viewModel: RootViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    HstTheme(themeMode = settings?.themeMode ?: ThemeMode.SYSTEM) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val loaded = settings
            when {
                loaded == null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                !loaded.setupComplete -> SetupScreen()
                else -> HstNavHost()
            }
        }
    }
}
