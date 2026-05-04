package com.georgeci.moneysurfer.data.emulator

import io.kotest.core.NamedTag

/**
 * Marker tag for tests that talk to a running Firebase Emulator Suite. Default
 * `:data:jvmTest` excludes this tag (see [TestProjectConfig]) so devs can run
 * unit tests without booting the emulator. A dedicated `:data:emulatorTest`
 * Gradle task selects only this tag.
 */
val EmulatorTag = NamedTag("emulator")
