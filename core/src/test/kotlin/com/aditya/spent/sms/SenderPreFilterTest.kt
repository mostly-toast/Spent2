package com.aditya.spent.sms

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SenderPreFilterTest {

    @Test
    fun phoneNumbersAreDropped() {
        val verdict = SenderPreFilter.evaluate(SenderNormalizer.normalize("9876543210"), userIgnored = false)
        assertIs<SenderPreFilter.Verdict.Dropped>(verdict)
        assertEquals(SenderPreFilter.DropReason.NOT_A_HEADER, (verdict as SenderPreFilter.Verdict.Dropped).reason)
        assertFalse(SenderPreFilter.isAlphanumericHeader("9876543210"))
    }

    @Test
    fun phoneNumbersWithSpacesAndCountryCodeAreDropped() {
        val verdict = SenderPreFilter.evaluate(SenderNormalizer.normalize("+91 98765 43210"), userIgnored = false)
        assertIs<SenderPreFilter.Verdict.Dropped>(verdict)
        assertEquals(SenderPreFilter.DropReason.NOT_A_HEADER, (verdict as SenderPreFilter.Verdict.Dropped).reason)
    }

    @Test
    fun alphanumericHeadersAreNotPhoneNumbers() {
        assertTrue(SenderPreFilter.isAlphanumericHeader("ICICIT"))
        assertTrue(SenderPreFilter.isAlphanumericHeader("TXNBNK"))
        assertTrue(SenderPreFilter.isAlphanumericHeader("AD-ICICIT-S"))
    }

    @Test
    fun promotionalSendersAreDropped() {
        val verdict = SenderPreFilter.evaluate(SenderNormalizer.normalize("JD-VTXBKP-P"), userIgnored = false)
        assertIs<SenderPreFilter.Verdict.Dropped>(verdict)
        assertEquals(SenderPreFilter.DropReason.PROMOTIONAL_OR_GOVERNMENT, (verdict as SenderPreFilter.Verdict.Dropped).reason)
    }

    @Test
    fun governmentSendersAreDropped() {
        val verdict = SenderPreFilter.evaluate(SenderNormalizer.normalize("VM-GOVTG-G"), userIgnored = false)
        assertIs<SenderPreFilter.Verdict.Dropped>(verdict)
        assertEquals(SenderPreFilter.DropReason.PROMOTIONAL_OR_GOVERNMENT, (verdict as SenderPreFilter.Verdict.Dropped).reason)
    }

    @Test
    fun transactionalAndServiceSendersAreRead() {
        assertIs<SenderPreFilter.Verdict.Read>(SenderPreFilter.evaluate(SenderNormalizer.normalize("JM-MAHABK-T"), false))
        assertIs<SenderPreFilter.Verdict.Read>(SenderPreFilter.evaluate(SenderNormalizer.normalize("AD-ICICIT-S"), false))
    }

    @Test
    fun unknownSuffixIsStillRead() {
        assertIs<SenderPreFilter.Verdict.Read>(SenderPreFilter.evaluate(SenderNormalizer.normalize("ICICIT"), false))
    }

    @Test
    fun userIgnoredSenderIsDropped() {
        val verdict = SenderPreFilter.evaluate(SenderNormalizer.normalize("MAHABK"), userIgnored = true)
        assertIs<SenderPreFilter.Verdict.Dropped>(verdict)
        assertEquals(SenderPreFilter.DropReason.USER_IGNORED, (verdict as SenderPreFilter.Verdict.Dropped).reason)
    }
}