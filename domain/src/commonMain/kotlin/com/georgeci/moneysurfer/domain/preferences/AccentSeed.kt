package com.georgeci.moneysurfer.domain.preferences

/**
 * Predefined accent seed identifiers exposed in the appearance picker.
 * Concrete colour values live in `uikit.tokens.AccentSeeds`; the domain only
 * carries the choice itself so persistence stays platform/UI agnostic.
 */
enum class AccentSeed {
    Plum,
    Sky,
    Sand,
    Mint,
    Rose,
}
