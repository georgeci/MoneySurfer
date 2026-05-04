package com.georgeci.moneysurfer.domain.preferences

/**
 * Visual style of card-like surfaces (`SurferCard`) across the app. Maps 1:1 onto
 * `uikit.theme.SurferContainerStyle`; declared in the domain layer so the choice can be
 * persisted without leaking UI types into `data`.
 */
enum class ContainerStyle {
    Filled,
    Outlined,
    Card,
}
