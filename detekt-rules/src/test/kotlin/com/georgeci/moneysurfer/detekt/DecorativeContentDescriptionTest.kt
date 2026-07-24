package com.georgeci.moneysurfer.detekt

import io.gitlab.arturbosch.detekt.test.lint
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class DecorativeContentDescriptionTest : StringSpec({

    val rule = DecorativeContentDescription()

    "reports a bare null content description" {
        val findings = rule.lint(
            """
            fun call() {
                Icon(imageVector = Add, contentDescription = null)
            }
            """.trimIndent(),
        )

        findings.size shouldBe 1
    }

    "accepts the SurferSemantics.Decorative marker" {
        val findings = rule.lint(
            """
            fun call() {
                Icon(imageVector = Add, contentDescription = SurferSemantics.Decorative)
            }
            """.trimIndent(),
        )

        findings.shouldBeEmpty()
    }

    "accepts a real description" {
        val findings = rule.lint(
            """
            fun call() {
                Icon(imageVector = Close, contentDescription = stringResource(R.string.close))
            }
            """.trimIndent(),
        )

        findings.shouldBeEmpty()
    }

    "ignores a null passed to some other parameter" {
        val findings = rule.lint(
            """
            fun call() {
                Icon(imageVector = Add, tint = null)
            }
            """.trimIndent(),
        )

        findings.shouldBeEmpty()
    }

    "reports each site separately" {
        val findings = rule.lint(
            """
            fun call() {
                Icon(imageVector = Add, contentDescription = null)
                Image(painter = art, contentDescription = null)
            }
            """.trimIndent(),
        )

        findings.size shouldBe 2
    }
})
