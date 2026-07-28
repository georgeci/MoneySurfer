package com.georgeci.moneysurfer.domain.preferences

/**
 * Grouping and decimal separators used when an amount is written out.
 *
 * The separators live on the entry rather than in a `when` in the UI because [sample] is the only
 * label this setting can have: "1,234.56" *is* the option, and a translated word for it would be
 * both longer and less clear.
 */
enum class NumberFormatStyle(val groupSeparator: String, val decimalSeparator: String) {
    /** `1,234.56` */
    CommaGroupDotDecimal(groupSeparator = ",", decimalSeparator = "."),

    /** `1.234,56` */
    DotGroupCommaDecimal(groupSeparator = ".", decimalSeparator = ","),

    /** `1 234,56` — non-breaking space, so the number never wraps mid-group. */
    SpaceGroupCommaDecimal(groupSeparator = " ", decimalSeparator = ","),

    ;

    /** The style rendered as itself. Never localized — digits and separators are the whole point. */
    val sample: String get() = "1${groupSeparator}234${decimalSeparator}56"

    companion object {
        val DEFAULT: NumberFormatStyle = CommaGroupDotDecimal
    }
}
