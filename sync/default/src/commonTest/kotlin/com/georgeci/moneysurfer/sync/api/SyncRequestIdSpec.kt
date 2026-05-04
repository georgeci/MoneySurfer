package com.georgeci.moneysurfer.sync.api

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank

class SyncRequestIdSpec : StringSpec({

    "uuid generates non-empty value" {
        SyncRequestId.uuid().value.shouldNotBeBlank()
    }

    "uuid generates unique values" {
        SyncRequestId.uuid() shouldNotBe SyncRequestId.uuid()
    }

    "explicit value is preserved" {
        SyncRequestId("custom-123").value shouldBe "custom-123"
    }
})
