plugins {
    alias(libs.plugins.kotlinJvm)
}

// Build tooling, not product code: this module is loaded into detekt's own
// classpath, so it stays off the KMP/Compose convention plugins every other
// module uses and out of the Kover/Sonar aggregation in the root build.
dependencies {
    // compileOnly — detekt supplies its own API at rule-loading time. Bringing it
    // in transitively would drop the whole detekt runtime into every module's
    // `detektPlugins` configuration, and with it a lockfile rewrite per module.
    compileOnly(libs.detekt.api)

    testImplementation(libs.detekt.test)
    testImplementation(libs.kotest.framework.engine)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.runner.junit5)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
