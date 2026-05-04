package com.georgeci.moneysurfer.utils

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer

object AmountInputTransformation : InputTransformation {

    override fun TextFieldBuffer.transformInput() {
        val text = asCharSequence().toString()

        if (text.isEmpty()) return

        val result = validateAndNormalize(text)
        if (result == null) {
            revertAllChanges()
            return
        }

        if (result != text) {
            replace(0, length, result)
        }
    }

    /**
     * Validates and normalizes amount input.
     * Returns the normalized string if valid, or null if the input should be rejected.
     *
     * Rules:
     * - Only digits and one decimal separator (`.` or `,`) allowed
     * - Comma is normalized to dot
     * - No leading zeros (e.g. "007"), except bare "0" or "0."
     * - Maximum 2 digits after the decimal point
     */
    private val AMOUNT_PATTERN = Regex("^\\d*\\.?\\d*$")
    private const val MAX_DECIMAL_DIGITS = 2

    fun validateAndNormalize(input: String): String? {
        val normalized = input.replace(',', '.')
        val parts = normalized.split('.')
        val hasInvalidFormat = !normalized.matches(AMOUNT_PATTERN)
        val hasLeadingZeros = parts[0].length > 1 && parts[0].startsWith('0')
        val hasTooManyDecimals = parts.size == 2 && parts[1].length > MAX_DECIMAL_DIGITS

        return normalized.takeUnless { hasInvalidFormat || hasLeadingZeros || hasTooManyDecimals }
    }
}
