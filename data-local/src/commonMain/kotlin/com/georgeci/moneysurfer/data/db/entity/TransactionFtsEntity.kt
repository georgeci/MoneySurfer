package com.georgeci.moneysurfer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

/**
 * Full-text index over [TransactionEntity.note].
 *
 * External-content table (`contentEntity`): the note text is not duplicated — the
 * virtual table stores only the inverted index and Room generates the triggers that
 * keep it in sync with `transactions`. Rows are joined back via the shared `rowid`.
 *
 * `unicode61` rather than the default `simple` tokenizer: `simple` only splits on
 * ASCII non-alphanumerics, so Cyrillic notes would end up as one opaque token and
 * would never match. `remove_diacritics=1` also makes "ё" match "е"-style input.
 *
 * FTS4 (not FTS5) because Room's annotations cover Fts3/Fts4 only.
 */
@Fts4(
    contentEntity = TransactionEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics=1"],
)
@Entity(tableName = "transactions_fts")
data class TransactionFtsEntity(
    @ColumnInfo(name = "note") val note: String,
)
