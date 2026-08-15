package com.kimi3.client

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
import com.kimi3.client.ui.ChatViewModel
import com.kimi3.client.ui.screens.ChatScreen
import com.kimi3.client.ui.screens.LoginScreen
import com.kimi3.client.ui.screens.SettingsScreen
import com.kimi3.client.ui.theme.KimiTheme

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

private enum class Screen { Chat, Settings, Login }

@Composable
private fun App(viewModel: ChatViewModel = viewModel()) {
    var screen by remember { mutableStateOf(Screen.Chat) }

    when (screen) {
        Screen.Chat -> ChatScreen(
            viewModel = viewModel,
            onOpenSettings = { screen = Screen.Settings },
        )
        Screen.Settings -> SettingsScreen(
            viewModel = viewModel,
            onBack = { screen = Screen.Chat },
            onOpenLogin = { screen = Screen.Login },
        )
        Screen.Login -> LoginScreen(
            store = viewModel.store,
            onLoggedIn = { screen = Screen.Chat },
            onBack = { screen = Screen.Settings },
        )
    }
}
