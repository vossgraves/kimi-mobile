package com.kimimobile.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kimi_settings")

data class AppSettings(
    val baseUrl: String = "http://10.0.2.2:8000/v1",
    val token: String = "",
    val model: String = "kimi-k2-0905-preview",
    val searchEnabled: Boolean = false,
    val researchEnabled: Boolean = false,
    val mathEnabled: Boolean = false,
    // Context window management. Set from the selected model (K2 = 256k,
    // Moonshot 8k/32k/128k); the web API reports no usage, so we estimate
    // from characters and compare against this max.
    val maxContextTokens: Long = 262_144L,
    val autoCompact: Boolean = true,
    val compactThresholdPct: Int = 80,
    val agentEnabled: Boolean = false,
    val installedSkills: Set<String> = setOf(
        "web_search", "fetch_url", "wikipedia", "calculator", "datetime", "memory",
    ),
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val TOKEN = stringPreferencesKey("token")
        val MODEL = stringPreferencesKey("model")
        val SEARCH = booleanPreferencesKey("search_enabled")
        val RESEARCH = booleanPreferencesKey("research_enabled")
        val MATH = booleanPreferencesKey("math_enabled")
        val MAX_CONTEXT_TOKENS = longPreferencesKey("max_context_tokens")
        val AUTO_COMPACT = booleanPreferencesKey("auto_compact")
        val COMPACT_THRESHOLD = intPreferencesKey("compact_threshold")
        val AGENT_ENABLED = booleanPreferencesKey("agent_enabled")
        val INSTALLED_SKILLS = stringSetPreferencesKey("installed_skills")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            baseUrl = prefs[Keys.BASE_URL] ?: AppSettings().baseUrl,
            token = prefs[Keys.TOKEN] ?: "",
            model = prefs[Keys.MODEL] ?: AppSettings().model,
            searchEnabled = prefs[Keys.SEARCH] ?: false,
            researchEnabled = prefs[Keys.RESEARCH] ?: false,
            mathEnabled = prefs[Keys.MATH] ?: false,
            maxContextTokens = prefs[Keys.MAX_CONTEXT_TOKENS] ?: AppSettings().maxContextTokens,
            autoCompact = prefs[Keys.AUTO_COMPACT] ?: AppSettings().autoCompact,
            compactThresholdPct = prefs[Keys.COMPACT_THRESHOLD] ?: AppSettings().compactThresholdPct,
            agentEnabled = prefs[Keys.AGENT_ENABLED] ?: AppSettings().agentEnabled,
            installedSkills = prefs[Keys.INSTALLED_SKILLS] ?: AppSettings().installedSkills,
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

    suspend fun setModel(id: String) {
        context.dataStore.edit { prefs -> prefs[Keys.MODEL] = id }
    }

    suspend fun setSearchEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.SEARCH] = enabled }
    }

    suspend fun setResearchEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.RESEARCH] = enabled }
    }

    suspend fun setMathEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.MATH] = enabled }
    }

    suspend fun setMaxContextTokens(max: Long) {
        context.dataStore.edit { prefs -> prefs[Keys.MAX_CONTEXT_TOKENS] = max }
    }

    suspend fun setAutoCompact(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.AUTO_COMPACT] = enabled }
    }

    suspend fun setCompactThreshold(pct: Int) {
        context.dataStore.edit { prefs -> prefs[Keys.COMPACT_THRESHOLD] = pct }
    }

    suspend fun setAgentEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.AGENT_ENABLED] = enabled }
    }

    suspend fun setInstalledSkills(skills: Set<String>) {
        context.dataStore.edit { prefs -> prefs[Keys.INSTALLED_SKILLS] = skills }
    }
}
