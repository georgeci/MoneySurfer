package com.georgeci.moneysurfer.data.repository

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * The catalogue is hard-coded, so what is worth pinning is not its contents but its shape: every
 * entry has to carry a code, a symbol and a name, and the codes have to be unique — a duplicate
 * would silently give the currency picker two identical rows.
 */
class CurrencyRepositoryImplTest : StringSpec({

    val repository = CurrencyRepositoryImpl()

    "the catalogue is not empty" {
        runTest {
            repository.getAll().first().shouldNotBeEmpty()
        }
    }

    "every currency carries a code, a symbol and a display name" {
        runTest {
            repository.getAll().first().forEach { currency ->
                currency.code.value.length shouldBe 3
                currency.symbol.isNotBlank() shouldBe true
                currency.displayName.isNotBlank() shouldBe true
            }
        }
    }

    "codes are unique" {
        runTest {
            val codes = repository.getAll().first().map { it.code.value }

            codes shouldContainExactly codes.distinct()
        }
    }
})
