package com.georgeci.moneysurfer.domain.logging

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

class PiiRedactionTest : StringSpec({

    "redactEmail masks local part and domain but keeps a correlation hint" {
        "john.doe@example.com".redactEmail() shouldBe "j***@***.com"
    }

    "redactEmail never leaks the raw address" {
        val redacted = "alice.smith@company.co.uk".redactEmail()
        redacted shouldNotContain "alice"
        redacted shouldNotContain "smith"
        redacted shouldNotContain "company"
        redacted shouldBe "a***@***.uk"
    }

    "redactEmail returns a fixed marker for blank input" {
        "".redactEmail() shouldBe "<blank-email>"
        "   ".redactEmail() shouldBe "<blank-email>"
        null.redactEmail() shouldBe "<blank-email>"
    }

    "redactEmail returns a fixed marker for malformed input" {
        "not-an-email".redactEmail() shouldBe "<redacted-email>"
        "@example.com".redactEmail() shouldBe "<redacted-email>"
        "trailing@".redactEmail() shouldBe "<redacted-email>"
    }

    "redactEmail handles a domain without a dot" {
        "bob@localhost".redactEmail() shouldBe "b***@***"
    }

    "redactUid keeps a short prefix and drops the rest" {
        "abcd1234efgh5678".redactUid() shouldBe "abcd***"
    }

    "redactUid never leaks the full identifier" {
        val uid = "Xy9ZkLmNoPqRsTuVwXyZ0123456789"
        uid.redactUid() shouldNotContain uid
        uid.redactUid() shouldBe "Xy9Z***"
    }

    "redactUid fully masks short values" {
        "ab".redactUid() shouldBe "***"
        "abcd".redactUid() shouldBe "***"
    }

    "redactUid returns a fixed marker for blank input" {
        "".redactUid() shouldBe "<blank-uid>"
        "  ".redactUid() shouldBe "<blank-uid>"
        null.redactUid() shouldBe "<blank-uid>"
    }
})
