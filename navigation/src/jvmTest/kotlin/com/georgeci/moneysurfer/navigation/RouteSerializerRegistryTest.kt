package com.georgeci.moneysurfer.navigation

import androidx.navigation3.runtime.NavKey
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModuleCollector
import kotlin.reflect.KClass

/**
 * Guards [navKeySerializersModule] against drift from [Route]: every concrete route that can land
 * on the saved back stack must be registered polymorphically, or saved-state restoration fails at
 * runtime with a serializer-not-found error for that route (the class of bug that dropped
 * `Route.SettingsCsv` and `Route.Legal`).
 */
class RouteSerializerRegistryTest : StringSpec({

    "every concrete Route is registered in navKeySerializersModule" {
        val missing = concreteRoutes() - registeredNavKeyClasses()
        // Failure message lists exactly which routes need a subclass(...) line added.
        missing.map { it.qualifiedName }.shouldBeEmpty()
    }
})

/** All concrete (non-abstract) leaves of the sealed [Route] hierarchy, found reflectively. */
private fun concreteRoutes(): Set<KClass<*>> {
    val leaves = mutableSetOf<KClass<*>>()
    fun visit(clazz: KClass<*>) {
        if (clazz.isSealed) {
            clazz.sealedSubclasses.forEach(::visit)
        } else {
            leaves += clazz
        }
    }
    visit(Route::class)
    return leaves
}

/** Concrete classes registered under [NavKey] in the module, collected via [dumpTo]. */
@OptIn(ExperimentalSerializationApi::class)
private fun registeredNavKeyClasses(): Set<KClass<*>> {
    val registered = mutableSetOf<KClass<*>>()
    navKeySerializersModule.dumpTo(
        object : SerializersModuleCollector {
            override fun <T : Any> contextual(
                kClass: KClass<T>,
                provider: (typeArgumentsSerializers: List<KSerializer<*>>) -> KSerializer<*>,
            ) = Unit

            override fun <Base : Any, Sub : Base> polymorphic(
                baseClass: KClass<Base>,
                actualClass: KClass<Sub>,
                actualSerializer: KSerializer<Sub>,
            ) {
                if (baseClass == NavKey::class) registered += actualClass
            }

            override fun <Base : Any> polymorphicDefaultSerializer(
                baseClass: KClass<Base>,
                defaultSerializerProvider: (value: Base) -> SerializationStrategy<Base>?,
            ) = Unit

            override fun <Base : Any> polymorphicDefaultDeserializer(
                baseClass: KClass<Base>,
                defaultDeserializerProvider: (className: String?) -> DeserializationStrategy<Base>?,
            ) = Unit
        },
    )
    return registered
}
