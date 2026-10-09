package com.aditya.spent.sms

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SenderRegistryTest {

    private val registry = SenderRegistry.bundled()

    @Test
    fun bundledRegistryLoads() {
        assertEquals(1535, registry.size)
    }

    @Test
    fun looksUpBankByNormalisedHeader() {
        assertEquals("ICICI Bank", registry.bankFor(SenderNormalizer.normalize("AD-ICICIT-S").candidateHeaders))
        assertEquals("Bank of Maharashtra", registry.bankFor(SenderNormalizer.normalize("JM-MAHABK-T").candidateHeaders))
    }

    @Test
    fun looksUpHeaderWithoutOperatorPrefix() {
        assertEquals("ICICI Bank", registry.bankFor(listOf("ICICIT")))
        assertEquals("Bank of Maharashtra", registry.bankFor(listOf("mahabk")))
    }

    @Test
    fun unknownHeaderHasNoBank() {
        assertEquals(null, registry.bankFor(listOf("NOTABANK")))
        assertFalse(registry.isKnown(listOf("NOTABANK")))
        assertTrue(registry.isKnown(listOf("MAHABK")))
    }

    @Test
    fun registryEntriesKeepTheirProvenance() {
        val entry = registry.entryFor(listOf("MAHABK"))
        assertEquals("seen", entry?.status)
        assertEquals("phone+trai-2020", entry?.source)
        assertEquals("bank", entry?.kind)
    }

    @Test
    fun thePhoneSendersAreMarkedAsSeen() {
        val seen = registry.entries.filter { it.status != "listed" }
        assertEquals(6, seen.size)
        assertTrue(seen.any { it.header == "MAHABK" && it.status == "seen" })
        assertTrue(seen.any { it.header == "ICICIT" && it.status == "seen-unverified" })
    }
}