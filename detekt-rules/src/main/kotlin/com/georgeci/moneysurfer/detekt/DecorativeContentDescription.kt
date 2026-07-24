package com.georgeci.moneysurfer.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * Flags `contentDescription = null` written as a bare literal.
 *
 * `null` is the right *value* for an element a screen reader should skip, but on its own it does
 * not say whether anyone decided that. A forgotten label on an icon-only button looks exactly the
 * same, and the codebase had both until they were told apart by hand. Passing
 * `SurferSemantics.Decorative` — which is itself `null` — keeps the behaviour and records the
 * decision.
 *
 * Matching is on the named argument, so no type resolution is needed: `contentDescription` is a
 * Compose accessibility parameter wherever it appears.
 */
class DecorativeContentDescription(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "DecorativeContentDescription",
        severity = Severity.Style,
        description = "Bare `contentDescription = null` does not say whether the element is " +
            "deliberately decorative or simply unlabelled. Use SurferSemantics.Decorative for " +
            "the former; give the element a real description if it is the latter.",
        debt = Debt.FIVE_MINS,
    )

    override fun visitArgument(argument: KtValueArgument) {
        super.visitArgument(argument)
        if (argument.getArgumentName()?.asName?.identifier != ARGUMENT_NAME) return
        val expression = argument.getArgumentExpression() ?: return
        if (expression.text != "null") return
        // Anchored on the `null` itself: `Entity.from(argument)` resolves to the enclosing call,
        // which on a multi-line Compose call is several lines away from the offending argument.
        report(CodeSmell(issue, Entity.from(expression), issue.description))
    }

    private companion object {
        const val ARGUMENT_NAME = "contentDescription"
    }
}
