package com.hourstracker.app.ui.theme

enum class ThemeMode {
    LIGHT,
    DARK,
    AUTO;

    companion object {
        fun fromString(value: String?): ThemeMode {
            return when (value?.lowercase()) {
                "light" -> LIGHT
                "dark" -> DARK
                "auto" -> AUTO
                else -> AUTO // Default to auto (system)
            }
        }
    }
}
