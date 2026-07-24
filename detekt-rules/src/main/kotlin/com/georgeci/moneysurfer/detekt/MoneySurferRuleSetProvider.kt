package com.georgeci.moneysurfer.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/** Project-specific detekt rules. Configured under the `moneysurfer` key in `detekt.yml`. */
class MoneySurferRuleSetProvider : RuleSetProvider {

    override val ruleSetId: String = "moneysurfer"

    override fun instance(config: Config): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            DecorativeContentDescription(config),
        ),
    )
}
