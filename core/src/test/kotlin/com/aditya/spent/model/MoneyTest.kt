package com.aditya.spent.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoneyTest {
    @Test fun wholeRupees() = assertEquals(50000L, Money.rupeesToPaise("500").getOrThrow())
    @Test fun oneDecimal() = assertEquals(50050L, Money.rupeesToPaise("500.5").getOrThrow())
    @Test fun twoDecimals() = assertEquals(50050L, Money.rupeesToPaise("500.50").getOrThrow())
    @Test fun zeroRupeesWithDecimals() = assertEquals(50L, Money.rupeesToPaise("0.50").getOrThrow())
    @Test fun leadingZeros() = assertEquals(100L, Money.rupeesToPaise("001.00").getOrThrow())

    @Test fun rejectsThreeDecimals() { assertFailsWith<IllegalArgumentException> { Money.rupeesToPaise("500.500").getOrThrow() } }
    @Test fun rejectsNegative() { assertFailsWith<IllegalArgumentException> { Money.rupeesToPaise("-100").getOrThrow() } }
    @Test fun rejectsNonNumeric() { assertFailsWith<IllegalArgumentException> { Money.rupeesToPaise("abc").getOrThrow() } }
    @Test fun rejectsEmpty() { assertFailsWith<IllegalArgumentException> { Money.rupeesToPaise("").getOrThrow() } }
    @Test fun rejectsZero() { assertFailsWith<IllegalArgumentException> { Money.rupeesToPaise("0").getOrThrow() } }
    @Test fun rejectsZeroWithDecimals() { assertFailsWith<IllegalArgumentException> { Money.rupeesToPaise("0.00").getOrThrow() } }

    @Test fun paiseToRupeesFormatsCorrectly() {
        assertEquals("INR 500.00", Money.paiseToRupees(50000L))
        assertEquals("INR 500.50", Money.paiseToRupees(50050L))
        assertEquals("INR 0.50", Money.paiseToRupees(50L))
    }
}
