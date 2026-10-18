package com.greenguardians.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.greenguardians.app.model.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.store by preferencesDataStore("green_guardians")

/** Local cache only: the server is authoritative for EP, CO2, coins and completions. */
class PrefsRepository(private val context: Context) {
    private object K {
        val dark = booleanPreferencesKey("dark_mode")
        val monet = booleanPreferencesKey("monet_colors")
        val token = stringPreferencesKey("session_token")
        val me = stringPreferencesKey("me_json")
    }

    /** null = follow system. */
    val darkMode: Flow<Boolean?> = context.store.data.map { it[K.dark] }
    val monetColors: Flow<Boolean> = context.store.data.map { it[K.monet] ?: false }

    val session: Flow<Session> = context.store.data.map { p ->
        Session(
            loaded = true,
            token = p[K.token],
            me = p[K.me]?.let { runCatching { Json.me(JSONObject(it)) }.getOrNull() },
        )
    }

    suspend fun setDarkMode(dark: Boolean) {
        context.store.edit { it[K.dark] = dark }
    }
    suspend fun setMonetColors(enabled: Boolean) {
        context.store.edit { it[K.monet] = enabled }
    }

    suspend fun saveSession(token: String, meJson: String) {
        context.store.edit {
            it[K.token] = token
            it[K.me] = meJson
        }
    }

    suspend fun saveMe(meJson: String) {
        context.store.edit { it[K.me] = meJson }
    }

    suspend fun clearSession() {
        context.store.edit {
            it.remove(K.token)
            it.remove(K.me)
        }
    }
}
