package com.kimimobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kimimobile.ui.ChatViewModel
import com.kimimobile.ui.screens.ChatScreen
import com.kimimobile.ui.screens.LoginScreen
import com.kimimobile.ui.screens.MarketplaceScreen
import com.kimimobile.ui.screens.SettingsScreen
import com.kimimobile.ui.screens.WelcomeScreen
import com.kimimobile.ui.screens.UpdateScreen
import com.kimimobile.ui.theme.KimiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KimiTheme {
                App()
            }
        }
    }
}

private enum class Screen { Welcome, Chat, Settings, Login, Marketplace, Updates }

@Composable
private fun App(viewModel: ChatViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsState()
    var screen by remember { mutableStateOf<Screen?>(null) }

    // First launch lands on Welcome; afterwards straight into the chat.
    LaunchedEffect(settings.onboarded) {
        if (screen == null) {
            screen = if (settings.onboarded) Screen.Chat else Screen.Welcome
        }
    }

    when (screen) {
        null -> Unit
        Screen.Welcome -> WelcomeScreen(
            onSignInKimi = {
                viewModel.completeOnboarding(defaultToFreeModel = false)
                screen = Screen.Login
            },
            onUseZenKey = {
                viewModel.completeOnboarding(defaultToFreeModel = false)
                screen = Screen.Settings
            },
            onSkip = {
                viewModel.completeOnboarding(defaultToFreeModel = true)
                screen = Screen.Chat
            },
        )
        Screen.Chat -> ChatScreen(
            viewModel = viewModel,
            onOpenSettings = { screen = Screen.Settings },
            onOpenMarketplace = { screen = Screen.Marketplace },
        )
        Screen.Settings -> SettingsScreen(
            viewModel = viewModel,
            onBack = { screen = Screen.Chat },
            onOpenLogin = { screen = Screen.Login },
            onOpenMarketplace = { screen = Screen.Marketplace },
            onOpenUpdates = { screen = Screen.Updates },
        )
        Screen.Login -> LoginScreen(
            store = viewModel.store,
            onLoggedIn = { screen = Screen.Chat },
            // Back from a first-run login should land in the chat, not in
            // Settings the user never opened.
            onBack = { screen = if (settings.token.isBlank()) Screen.Chat else Screen.Settings },
        )
        Screen.Marketplace -> MarketplaceScreen(
            viewModel = viewModel,
            onBack = { screen = Screen.Chat },
        )
        Screen.Updates -> UpdateScreen(
            viewModel = viewModel,
            onBack = { screen = Screen.Settings },
        )
    }
}
