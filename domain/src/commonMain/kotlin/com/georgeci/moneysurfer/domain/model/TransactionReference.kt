package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.TransactionId

private const val REFERENCE_PREFIX = "TX-"
private const val REFERENCE_DIGITS = 4

/**
 * Short human-facing label for a transaction (`TX-8A13`), shown on the details screen.
 *
 * Derived, never stored: it is a pure function of [TransactionId], so there is no column to
 * migrate, no wire field for the rules to validate, and nothing that can drift from the id it
 * labels. [TransactionId] is UUID-backed, so its trailing hex digits are already uniformly
 * distributed and no hashing is needed.
 *
 * This is a display label, not a key: four hex digits collide, and nothing looks a transaction
 * up by it. If a bank-supplied reference ever has to survive a statement import, that is a
 * *stored* field with different semantics — add it then, next to this one, not instead of it.
 */
val TransactionId.reference: String
    get() {
        val digits = value.filter(Char::isReferenceDigit)
            .takeLast(REFERENCE_DIGITS)
            .padStart(REFERENCE_DIGITS, '0')
        return REFERENCE_PREFIX + digits.uppercase()
    }

// Ids reaching us from an import may not be UUIDs; keeping only hex digits guarantees a
// stable, printable label for any id shape rather than echoing stray punctuation.
private fun Char.isReferenceDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
