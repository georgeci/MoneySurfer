plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.ksp)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.kotest.multiplatform)
    alias(libs.plugins.kover)
    alias(libs.plugins.kmp.lib)
}

kotlin {
    android {
        namespace = "com.georgeci.moneysurfer.data.local"
    }

    // Adding the manual jvm+android edge below opts out of the auto-applied
    // source-set hierarchy, so re-apply the default template explicitly to keep
    // the iOS (native) intermediates wired to commonMain.
    applyDefaultHierarchyTemplate()

    sourceSets {
        // JVM and Android share the same javax.crypto / JCA backend, so the
        // BackupCrypto actual lives once in this intermediate set rather than
        // as two byte-identical twins. Only the iOS actual (CommonCrypto) differs.
        val jvmAndroidMain by creating {
            dependsOn(commonMain.get())
        }
        jvmMain {
            dependsOn(jvmAndroidMain)
        }
        androidMain {
            dependsOn(jvmAndroidMain)
        }

        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.datetime)
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.sqlite.bundled)
                implementation(libs.androidx.datastore.preferences)
                implementation(libs.kotlinx.coroutinesCore)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.okio)
                implementation(libs.arrow.core)
                implementation(libs.kermit)
                implementation(projects.domain)
                implementation(projects.sync.api)
                implementation(projects.appConfig.api)
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.annotations)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
                implementation(libs.turbine)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotest.runner.junit5)
                implementation(libs.fixture.monkey.kotlin)
                implementation(projects.domainTestFixtures)
            }
        }

        androidMain {
            dependencies {}
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.runner)
                implementation(libs.androidx.core)
                implementation(libs.androidx.testExt.junit)
            }
        }

        iosMain {
            dependencies {}
        }
    }
}

koinCompiler {
    compileSafety = false
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
}

// Exported schemas are committed (see `schemas/`) and are the input to
// `verifyRoomMigrations` below: a new `<n>.json` with no migration fails the build.
room {
    schemaDirectory("$projectDir/schemas")
}

/**
 * Migration policy gate — see `docs/architecture/persistence.md` → "Room schema versioning".
 *
 * Fails when an exported schema version at or above the frozen release baseline cannot be
 * reached by a hand-written `Migration`, or when the exported schemas and
 * `MONEY_SURFER_DB_VERSION` disagree. Versions below the baseline never shipped, so their
 * upgrade paths are allowed to be missing.
 */
val verifyRoomMigrations = tasks.register("verifyRoomMigrations") {
    group = "verification"
    description = "Fail if an exported Room schema at/above the release baseline has no migration."

    val schemasDir = layout.projectDirectory.dir("schemas")
    val dbSourceDir = layout.projectDirectory.dir("src/commonMain/kotlin/com/georgeci/moneysurfer/data/db")
    val outputMarker = layout.buildDirectory.file("reports/room/verifyRoomMigrations.txt")

    inputs.dir(schemasDir).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(dbSourceDir).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(outputMarker)

    doLast {
        val dbSource = dbSourceDir.file("MoneySurferDatabase.kt").asFile.readText()
        val builderSource = dbSourceDir.file("DatabaseBuilder.kt").asFile.readText()
        val migrationSources = dbSourceDir.dir("migration").asFile
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        fun constant(name: String): Int =
            Regex("""const val $name:\s*Int\s*=\s*(\d+)""").find(dbSource)
                ?.groupValues?.get(1)?.toInt()
                ?: error("Could not read `$name` from MoneySurferDatabase.kt")

        val declaredVersion = constant("MONEY_SURFER_DB_VERSION")
        val baseline = constant("MONEY_SURFER_DB_RELEASE_BASELINE_VERSION")

        val exported = schemasDir.asFile.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            .toSortedSet()

        val problems = mutableListOf<String>()
        if (exported.isEmpty()) {
            problems += "No exported schemas found under ${schemasDir.asFile}."
        } else if (exported.max() != declaredVersion) {
            problems += "Highest exported schema is ${exported.max()} but MONEY_SURFER_DB_VERSION " +
                "is $declaredVersion. Build the module so Room re-exports the schema, and commit it."
        }
        if (declaredVersion !in exported) {
            problems += "No exported schema `$declaredVersion.json` for the current " +
                "MONEY_SURFER_DB_VERSION. Commit `schemas/**/$declaredVersion.json`."
        }

        // Every step from the frozen baseline up to the current version must be carried by a
        // declared *and* registered migration; a hole means a released install cannot upgrade.
        for (from in baseline until declaredVersion) {
            val name = "MIGRATION_${from}_${from + 1}"
            if (!migrationSources.contains("val $name")) {
                problems += "Missing migration `$name`: schema $from → ${from + 1} is at or above " +
                    "the frozen release baseline ($baseline) and needs a hand-written Migration in " +
                    "`data/db/migration/`."
            } else if (!builderSource.contains(name)) {
                problems += "Migration `$name` exists but is not passed to `addMigrations(...)` in " +
                    "DatabaseBuilder.kt, so Room will never run it."
            }
        }

        val marker = outputMarker.get().asFile
        marker.parentFile.mkdirs()
        if (problems.isNotEmpty()) {
            marker.delete()
            throw GradleException(
                problems.joinToString(
                    prefix = "Room migration policy violated " +
                        "(docs/architecture/persistence.md → \"Room schema versioning\"):\n  - ",
                    separator = "\n  - ",
                ),
            )
        }
        marker.writeText(
            "verified: baseline=$baseline current=$declaredVersion exported=${exported.joinToString(",")}\n",
        )
    }
}

tasks.named("check") {
    dependsOn(verifyRoomMigrations)
}

lint {
    disable += "RestrictedApi"
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
