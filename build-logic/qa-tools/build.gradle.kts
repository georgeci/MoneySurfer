plugins {
    `kotlin-dsl`
}

// Deliberately dependency-free. `ms.qa-tools` is applied by the *root* build
// script, whose `classpath` configuration is dependency-locked
// (buildscript-gradle.lockfile, see docs/security/supply-chain.md). Anything
// declared here as `implementation` would leak onto that classpath and break
// the lock state — which is exactly why these helpers do not live in
// `build-logic/kmp` next to the other convention plugins: that module needs
// easylauncher / play-publisher at runtime.
//
// MaestroTools / AllureTools use nothing but `java.io` and the JDK XML parser,
// so keep it that way.

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

gradlePlugin {
    plugins {
        register("qaToolsConvention") {
            id = "ms.qa-tools"
            implementationClass = "com.georgeci.moneysurfer.buildlogic.QaToolsConventionPlugin"
            displayName = "MoneySurfer QA Tools Plugin"
            description = "Exports MaestroTools/AllureTools to the root build's script classpath for gradle/qa.gradle.kts."
        }
    }
}
