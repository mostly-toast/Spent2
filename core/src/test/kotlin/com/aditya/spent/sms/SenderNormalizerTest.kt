package com.aditya.spent.sms

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SenderNormalizerTest {

    @Test
    fun operatorPrefixAndServiceSuffix() {
        val sender = SenderNormalizer.normalize("AD-ICICIT-S")
        assertEquals("ICICIT", sender.header)
        assertEquals("AD", sender.operatorPrefix)
        assertEquals(SenderSuffix.SERVICE, sender.suffix)
    }

    @Test
    fun operatorPrefixAndTransactionalSuffix() {
        val sender = SenderNormalizer.normalize("AX-MAHABK-T")
        assertEquals("MAHABK", sender.header)
        assertEquals("AX", sender.operatorPrefix)
        assertEquals(SenderSuffix.TRANSACTIONAL, sender.suffix)
    }

    @Test
    fun promotionalAndGovernmentSuffixes() {
        assertEquals(SenderSuffix.PROMOTIONAL, SenderNormalizer.normalize("JD-VTXBKP-P").suffix)
        assertEquals(SenderSuffix.GOVERNMENT, SenderNormalizer.normalize("VM-GOVTG-G").suffix)
    }

    @Test
    fun missingSuffixIsUnknown() {
        val sender = SenderNormalizer.normalize("AD-ICICIT")
        assertEquals("ICICIT", sender.header)
        assertEquals(SenderSuffix.UNKNOWN, sender.suffix)
    }

    @Test
    fun headerWithoutOperatorPrefix() {
        val sender = SenderNormalizer.normalize("MAHABK")
        assertEquals("MAHABK", sender.header)
        assertNull(sender.operatorPrefix)
        assertEquals(SenderSuffix.UNKNOWN, sender.suffix)
    }

    @Test
    fun lowercaseAndPaddedInputIsUppercasedAndTrimmed() {
        assertEquals("ICICIT", SenderNormalizer.normalize("  ad-icicit-s ").header)
    }

    @Test
    fun candidateHeadersIncludeTheWholeToken() {
        assertEquals(listOf("ICICIT", "AD-ICICIT-S"), SenderNormalizer.normalize("AD-ICICIT-S").candidateHeaders)
        assertEquals(listOf("MAHABK"), SenderNormalizer.normalize("MAHABK").candidateHeaders)
    }
}