package com.vikas.varchive.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vikas.varchive.archive.ArchiveFormat
import com.vikas.varchive.archive.CompressionLevel
import com.vikas.varchive.archive.ConflictPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("varchive_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultFormat: ArchiveFormat = ArchiveFormat.ZIP,
    val defaultLevel: CompressionLevel = CompressionLevel.NORMAL,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.RENAME,
    val showHidden: Boolean = false,
    val bombWarnings: Boolean = true,
    val backgroundOperations: Boolean = true,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val format = stringPreferencesKey("format")
        val level = stringPreferencesKey("level")
        val conflict = stringPreferencesKey("conflict")
        val showHidden = booleanPreferencesKey("show_hidden")
        val bombWarnings = booleanPreferencesKey("bomb_warnings")
        val background = booleanPreferencesKey("background_operations")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.theme].enumOr(ThemeMode.SYSTEM),
            defaultFormat = prefs[Keys.format].enumOr(ArchiveFormat.ZIP),
            defaultLevel = prefs[Keys.level].enumOr(CompressionLevel.NORMAL),
            conflictPolicy = prefs[Keys.conflict].enumOr(ConflictPolicy.RENAME),
            showHidden = prefs[Keys.showHidden] ?: false,
            bombWarnings = prefs[Keys.bombWarnings] ?: true,
            backgroundOperations = prefs[Keys.background] ?: true,
        )
    }

    suspend fun setTheme(value: ThemeMode) = context.settingsStore.edit { it[Keys.theme] = value.name }
    suspend fun setDefaultFormat(value: ArchiveFormat) = context.settingsStore.edit { it[Keys.format] = value.name }
    suspend fun setDefaultLevel(value: CompressionLevel) = context.settingsStore.edit { it[Keys.level] = value.name }
    suspend fun setConflict(value: ConflictPolicy) = context.settingsStore.edit { it[Keys.conflict] = value.name }
    suspend fun setShowHidden(value: Boolean) = context.settingsStore.edit { it[Keys.showHidden] = value }
    suspend fun setBombWarnings(value: Boolean) = context.settingsStore.edit { it[Keys.bombWarnings] = value }
}

private inline fun <reified T : Enum<T>> String?.enumOr(default: T): T =
    this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
