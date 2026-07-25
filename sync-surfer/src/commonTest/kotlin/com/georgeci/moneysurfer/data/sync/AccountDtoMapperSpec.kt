package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.data.db.entity.AccountExtraDetailsColumn
import com.georgeci.moneysurfer.data.remote.AccountDoc
import com.georgeci.moneysurfer.data.remote.AccountExtraDetailDoc
import com.georgeci.moneysurfer.domain.model.AccountExtraDetail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private const val WORKSPACE = "ws-1"
private const val ACCOUNT = "acc-1"

private fun accountDoc(extraDetails: List<AccountExtraDetailDoc> = emptyList()) = AccountDoc(
    name = "Everyday",
    type = "BANK",
    currency = "EUR",
    balance = 1_000L,
    updatedAt = 2L,
    extraDetails = extraDetails,
)

class AccountDtoMapperSpec : StringSpec({

    "extra details round-trip through the wire format, order included" {
        val doc = accountDoc(
            listOf(
                AccountExtraDetailDoc(key = "IBAN", value = "PL61 1090 1014"),
                AccountExtraDetailDoc(key = "Broker code", value = "MS-4417"),
            ),
        )

        doc.toEntity(id = ACCOUNT, workspaceId = WORKSPACE).toDoc().extraDetails shouldBe
            doc.extraDetails
    }

    "an account with no extra details maps to the empty column and back to an empty list" {
        val entity = accountDoc().toEntity(id = ACCOUNT, workspaceId = WORKSPACE)

        entity.extraDetails shouldBe AccountExtraDetailsColumn.EMPTY
        entity.toDoc().extraDetails shouldBe emptyList()
    }

    "a remote doc from a client we do not control is normalized on the way in" {
        val doc = accountDoc(
            listOf(
                AccountExtraDetailDoc(key = " IBAN ", value = "PL61"),
                AccountExtraDetailDoc(key = "iban", value = "duplicate"),
                AccountExtraDetailDoc(key = "", value = "orphan"),
                AccountExtraDetailDoc(key = "BIC", value = "   "),
            ),
        )

        AccountExtraDetailsColumn.decode(
            doc.toEntity(id = ACCOUNT, workspaceId = WORKSPACE).extraDetails,
        ) shouldBe listOf(AccountExtraDetail(key = "IBAN", value = "PL61"))
    }

    "the list position survives the wire, so a reorder reaches the other device" {
        val doc = accountDoc().copy(sortOrder = 4)

        val entity = doc.toEntity(id = ACCOUNT, workspaceId = WORKSPACE)

        entity.sortOrder shouldBe 4
        entity.toDoc().sortOrder shouldBe 4
    }

    "a doc from a client that predates sortOrder lands at the front rather than nowhere" {
        accountDoc().toEntity(id = ACCOUNT, workspaceId = WORKSPACE).sortOrder shouldBe 0
    }
})
