package com.djand.hst

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.djand.hst.ui.HstApp
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single activity of the app. Everything else is Compose; see [HstApp].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HstApp() }
    }
}
