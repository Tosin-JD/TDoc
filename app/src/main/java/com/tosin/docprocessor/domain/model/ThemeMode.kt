package com.tosin.docprocessor.domain.model

/**
 * The single user-facing light/dark preference.
 *
 * [SYSTEM] follows the device setting; [LIGHT] and [DARK] are explicit choices
 * made from the editor menu. The resolved boolean is computed once at the app
 * root (see `ui.theme.resolveThemeMode`) so every surface agrees.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        /**
         * Parses a persisted value, falling back to [SYSTEM] for blank,
         * unknown or corrupt input.
         */
        fun fromString(value: String?): ThemeMode {
            val normalized = value?.trim()?.uppercase()
            return entries.firstOrNull { it.name == normalized } ?: SYSTEM
        }
    }
}
