package com.kimimobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
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

private enum class Screen { Chat, Settings, Login, Marketplace }

@Composable
private fun App(viewModel: ChatViewModel = viewModel()) {
    var screen by remember { mutableStateOf(Screen.Chat) }

    when (screen) {
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
        )
        Screen.Login -> LoginScreen(
            store = viewModel.store,
            onLoggedIn = { screen = Screen.Chat },
            onBack = { screen = Screen.Settings },
        )
        Screen.Marketplace -> MarketplaceScreen(
            viewModel = viewModel,
            onBack = { screen = Screen.Chat },
        )
    }
}
