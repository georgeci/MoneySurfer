package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.sync.plugin.RemoteDocument

/**
 * Reads a collection under `users/{uid}` — the user-scoped counterpart of
 * [WorkspaceCollectionReader].
 *
 * Deliberately whole-collection and cursorless, unlike every workspace read. The one collection
 * behind it (`config`) is about ten tiny documents and is not expected to grow much, so incremental
 * reads would buy nothing but cursor bookkeeping in a brand-new scope — and the Firestore rule then
 * only has to permit a bare `list` rather than prove a filtered, ordered query.
 *
 * "Not expected to grow much" is an expectation, not a guarantee, which is why [limit] is not
 * optional. Nothing prunes these documents: the config plugin stores keys it does not recognise so
 * a mixed-version pair of devices cannot lose each other's settings, and no code path deletes one
 * outside the account-deletion purge — so every renamed or retired `SettingKey` leaves an orphan
 * behind for good. Uncapped, a collection that grew through that drift (or a client bug, or a
 * compromised device) would be materialised whole into memory and re-applied to Room on every
 * foreground sync, on every one of the user's devices.
 */
interface UserCollectionReader {

    /** At most [limit] documents. The caller decides what to do when the cap is reached. */
    suspend fun fetchAll(uid: String, collectionName: String, limit: Int): List<RemoteDocument>
}
