package com.georgeci.moneysurfer.feature.login

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignInFeatureConfigTest {

    @Test
    fun `defaults enable every entry point`() {
        val config = SignInFeatureConfig()

        assertTrue(config.emailPassword)
        assertTrue(config.anonymous)
        assertTrue(config.demo)
        assertFalse(config.demoOnly, "demoOnly is false when other entry points are also enabled")
    }

    @Test
    fun `demoOnly is true only when demo is the single enabled entry point`() {
        val config = SignInFeatureConfig(
            emailPassword = false,
            anonymous = false,
            demo = true,
        )

        assertTrue(config.demoOnly)
    }

    @Test
    fun `demoOnly is false when email-password is also enabled`() {
        val config = SignInFeatureConfig(
            emailPassword = true,
            anonymous = false,
            demo = true,
        )

        assertFalse(config.demoOnly)
    }

    @Test
    fun `demoOnly is false when anonymous is also enabled`() {
        val config = SignInFeatureConfig(
            emailPassword = false,
            anonymous = true,
            demo = true,
        )

        assertFalse(config.demoOnly)
    }

    @Test
    fun `demoOnly is false when demo itself is disabled`() {
        val config = SignInFeatureConfig(
            emailPassword = false,
            anonymous = false,
            demo = false,
        )

        assertFalse(config.demoOnly)
    }
}
