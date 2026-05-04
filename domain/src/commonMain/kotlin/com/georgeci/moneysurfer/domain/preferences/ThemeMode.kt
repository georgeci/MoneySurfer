package com.georgeci.moneysurfer.domain.preferences

/**
 * User's theme-mode choice. `System` defers to the OS setting; `Light` and `Dark` force the
 * scheme regardless of the system value.
 */
enum class ThemeMode {
    System,
    Light,
    Dark,
}
