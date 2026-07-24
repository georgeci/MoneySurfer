package com.georgeci.moneysurfer.uikit.semantics

/** Intent markers for the accessibility semantics of a composable. */
object SurferSemantics {

    /**
     * The element carries no meaning of its own: the surrounding text — a button label, a row
     * title, a field label — already names it, and a separate description would only repeat that
     * to a screen reader.
     *
     * This is `null`, which is what Compose reads as "no semantic label". The constant exists so
     * a deliberate omission looks different from a forgotten one: a bare `contentDescription =
     * null` is indistinguishable from a control nobody got around to labelling, and the codebase
     * had both. Anything that is the *only* description of its control needs a real string
     * instead.
     */
    val Decorative: String? = null
}
