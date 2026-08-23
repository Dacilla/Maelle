package com.maelle.core.device

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstallIdProvider @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun get(): String {
        val existing = preferences.getString(KEY_INSTALL_ID, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }

        val created = UUID.randomUUID().toString()
        preferences.edit().putString(KEY_INSTALL_ID, created).apply()
        return created
    }

    private companion object {
        const val PREFERENCES_NAME = "maelle.install"
        const val KEY_INSTALL_ID = "install_id"
    }
}
