package com.kimi3.client.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kimi_settings")

data class AppSettings(
    val baseUrl: String = "http://10.0.2.2:8000/v1",
    val token: String = "",
    val model: String = "kimi-k3",
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val TOKEN = stringPreferencesKey("token")
        val MODEL = stringPreferencesKey("model")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            baseUrl = prefs[Keys.BASE_URL] ?: AppSettings().baseUrl,
            token = prefs[Keys.TOKEN] ?: "",
            model = prefs[Keys.MODEL] ?: AppSettings().model,
        )
    }

    suspend fun save(baseUrl: String, token: String, model: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = baseUrl.trim().trimEnd('/')
            prefs[Keys.TOKEN] = token.trim()
            prefs[Keys.MODEL] = model.trim()
        }
    }

    /** Used by the WebView login flow — preserves baseUrl/model. */
    suspend fun setToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TOKEN] = token.trim()
        }
    }

    suspend fun clearToken() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.TOKEN)
        }
    }
}
