package com.georgeci.moneysurfer.feature.transaction.creation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionCreationFeatureConfigTest {

    @Test
    fun `defaults enable transfer`() {
        assertTrue(TransactionCreationFeatureConfig().transferEnabled)
    }

    @Test
    fun `transfer can be disabled for the offline build`() {
        assertFalse(TransactionCreationFeatureConfig(transferEnabled = false).transferEnabled)
    }
}
