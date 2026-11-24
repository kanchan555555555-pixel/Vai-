package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppLanguage
import com.example.model.CastMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hotcast_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("app_language")
        val CAST_MODE = stringPreferencesKey("cast_mode")
        val SLIDESHOW_SEC = intPreferencesKey("slideshow_sec")
    }

    val languageFlow: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.LANGUAGE]) {
            AppLanguage.ENGLISH.code -> AppLanguage.ENGLISH
            else -> AppLanguage.BENGALI
        }
    }

    val castModeFlow: Flow<CastMode> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.CAST_MODE]) {
            CastMode.DLNA_TV.name -> CastMode.DLNA_TV
            else -> CastMode.WEB_RECEIVER
        }
    }

    val slideshowIntervalFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[Keys.SLIDESHOW_SEC] ?: 6).coerceIn(3, 30)
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setCastMode(mode: CastMode) {
        context.dataStore.edit { it[Keys.CAST_MODE] = mode.name }
    }

    suspend fun setSlideshowInterval(seconds: Int) {
        context.dataStore.edit { it[Keys.SLIDESHOW_SEC] = seconds.coerceIn(3, 30) }
    }
}
