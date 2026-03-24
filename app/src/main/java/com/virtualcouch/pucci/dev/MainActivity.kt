package com.virtualcouch.pucci.dev

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.virtualcouch.pucci.dev.ui.composables.AppNavigation
import com.virtualcouch.pucci.dev.ui.theme.VirtualCouchTheme
import com.virtualcouch.pucci.dev.viewmodel.TikTokViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<TikTokViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            VirtualCouchTheme {
                AppNavigation(
                    viewModel = viewModel
                )
            }
        }
    }
}

