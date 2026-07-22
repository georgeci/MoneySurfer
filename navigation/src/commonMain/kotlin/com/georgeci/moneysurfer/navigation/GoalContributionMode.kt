package com.georgeci.moneysurfer.navigation

/**
 * Which direction the contribution screen moves money. Carried through [Route.GoalContribution]
 * as a name so the route stays a plain serializable data class.
 */
enum class GoalContributionMode {
    ADD,
    WITHDRAW,
    ;

    companion object {
        fun fromName(name: String?): GoalContributionMode =
            entries.firstOrNull { it.name == name } ?: ADD
    }
}
