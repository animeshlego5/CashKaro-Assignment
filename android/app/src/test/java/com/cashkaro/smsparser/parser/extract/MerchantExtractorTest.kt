package com.cashkaro.smsparser.parser.extract

import com.cashkaro.smsparser.parser.NormalizedSms
import com.cashkaro.smsparser.parser.config.TestConfigSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [DefaultMerchantExtractor], built from the real merchants.json
 * via [TestConfigSource]. Covers the documented examples (boundary handling,
 * city/suffix stripping, internal-dot preservation), a novel wording, and the
 * absent-merchant case.
 */
class MerchantExtractorTest {

    private val merchantConfig = TestConfigSource().load().merchant
    private val extractor = DefaultMerchantExtractor(merchantConfig)

    private fun sms(text: String) = NormalizedSms(text, text, text.lowercase())

    @Test
    fun stops_at_on_date_clause() {
        assertEquals(
            "SWIGGY",
            extractor.extract(sms("INR 1,250.00 spent on HDFC Bank Credit Card xx5678 at SWIGGY on 03-04-2026.")),
        )
    }

    @Test
    fun stops_at_sentence_terminating_dot() {
        assertEquals(
            "AMAZON",
            extractor.extract(sms("INR 320.00 spent using Axis Bank Card no. XX9876 on 06-APR-26 at AMAZON. Available Limit: INR 87,500.00.")),
        )
    }

    @Test
    fun strips_trailing_country_suffix_keeps_inner_pvt() {
        assertEquals(
            "HOSPITALITY PVT DELHI",
            extractor.extract(sms("you've spent Rs 1836.00 to HOSPITALITY PVT DELHI IN on your Edge Federal Bank Credit Card ending 4422")),
        )
    }

    @Test
    fun strips_trailing_city_before_with_boundary() {
        assertEquals(
            "Blackwater Coffee",
            extractor.extract(sms("You've spent Rs. 849.00 at Blackwater Coffee, Gurgaon with your BOBCARD One Credit Card ending in XX9907 on 08-04-2026.")),
        )
    }

    @Test
    fun preserves_internal_dot_in_domain_merchant() {
        assertEquals(
            "NETFLIX.COM/US",
            extractor.extract(sms("USD 49.99 spent on your Axis Bank Card XX9876 at NETFLIX.COM/US on 13-APR-26.")),
        )
    }

    @Test
    fun null_when_no_preposition_present() {
        assertNull(extractor.extract(sms("Use 458219 as your OTP for HDFC Bank Net Banking login. Valid for 5 mins.")))
    }

    @Test
    fun null_when_preposition_absent_in_balance_alert() {
        assertNull(extractor.extract(sms("Avl Bal in your A/C XX4521 as on 08-04-26 is INR 1,02,450.30.")))
    }

    // ---- novel / hidden-style wording ----

    @Test
    fun novel_wording_simple_at_merchant() {
        assertEquals(
            "STARBUCKS",
            extractor.extract(sms("Rs 450 spent on SBI Credit Card xx1234 at STARBUCKS on 21-05-26")),
        )
    }

    @Test
    fun novel_wording_to_merchant_with_ltd_suffix() {
        assertEquals(
            "RELIANCE RETAIL",
            extractor.extract(sms("Rs 2,000 paid to RELIANCE RETAIL LTD on your Kotak Credit Card xx9999 on 22-05-26")),
        )
    }
}
