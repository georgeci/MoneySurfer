package com.georgeci.moneysurfer.appconfig

/**
 * String ↔ value bridge for one configuration key.
 *
 * A string is the common denominator of every backing store — a DataStore preference, a
 * `config_entry.value` column, a Firestore field — so every layer except the in-memory Build
 * map holds encoded strings and the codec is what makes them typed again.
 *
 * Public API of `app-config/api` on purpose: key objects are `internal` to the module that owns
 * their facade, but a public key constructor cannot take an internal parameter type, so the
 * codecs those keys are built from have to be visible.
 */
interface ConfigCodec<T : Any> {

    fun encode(value: T): String

    /**
     * `null` means "undecodable". The engine then treats the layer as **not holding this key**
     * and resolution continues downwards — see [Config].
     */
    fun decode(raw: String): T?

    /**
     * Shape of the value, so the debug panel can render a real control instead of a free-text
     * field. `PaletteSource`'s `PRESET:<seed>` format makes typing raw strings a routine way to
     * fail, which is what this hint exists to avoid.
     */
    val valueKind: ConfigValueKind
}

/** What kind of control can edit a key — derived from its codec, never from its name. */
sealed interface ConfigValueKind {
    data object Bool : ConfigValueKind

    /** Closed set of accepted encodings, in declaration order. */
    data class Choice(val choices: List<String>) : ConfigValueKind

    data object FreeText : ConfigValueKind
}

/** Accepts `true`/`false` case-insensitively; anything else is undecodable, not `false`. */
object BooleanConfigCodec : ConfigCodec<Boolean> {
    override fun encode(value: Boolean): String = value.toString()

    override fun decode(raw: String): Boolean? = when (raw.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }

    override val valueKind: ConfigValueKind = ConfigValueKind.Bool
}

object StringConfigCodec : ConfigCodec<String> {
    override fun encode(value: String): String = value
    override fun decode(raw: String): String = raw
    override val valueKind: ConfigValueKind = ConfigValueKind.FreeText
}

/**
 * Enum name ↔ entry. Replaces the hand-rolled
 * `runCatching { Enum.valueOf(stored) }.getOrDefault(default)` blocks that were repeated per
 * field in `UiPreferencesImpl` — an unknown name now falls through to the layer below instead of
 * silently becoming the default.
 */
class EnumConfigCodec<E : Enum<E>>(private val entries: List<E>) : ConfigCodec<E> {
    override fun encode(value: E): String = value.name
    override fun decode(raw: String): E? = entries.firstOrNull { it.name == raw }
    override val valueKind: ConfigValueKind = ConfigValueKind.Choice(entries.map { it.name })
}
