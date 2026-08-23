package com.maelle.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userSettingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "maelle_user_settings",
)

data class UserSettings(
    val developerMode: Boolean = false,
    val burnSubtitlesByDefault: Boolean = false,
)

@Singleton
class UserSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val settings: Flow<UserSettings> = context.userSettingsStore.data.map { preferences ->
        UserSettings(
            developerMode = preferences[KEY_DEVELOPER_MODE] ?: false,
            burnSubtitlesByDefault = preferences[KEY_BURN_SUBTITLES] ?: false,
        )
    }

    suspend fun setDeveloperMode(enabled: Boolean) {
        context.userSettingsStore.edit { it[KEY_DEVELOPER_MODE] = enabled }
    }

    suspend fun setBurnSubtitlesByDefault(enabled: Boolean) {
        context.userSettingsStore.edit { it[KEY_BURN_SUBTITLES] = enabled }
    }

    private companion object {
        val KEY_DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        val KEY_BURN_SUBTITLES = booleanPreferencesKey("burn_subtitles_by_default")
    }
}
