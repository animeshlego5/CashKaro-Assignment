package com.cashkaro.smsparser.parser.extract

import com.cashkaro.smsparser.parser.NormalizedSms
import com.cashkaro.smsparser.parser.config.TestConfigSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [DefaultCurrencyExtractor] — detecting the currency from config
 * tokens, never assuming INR (C7). Config is loaded from the mirrored test
 * resources so the production tokens are exercised.
 */
class CurrencyExtractorTest {

    private val extractor = DefaultCurrencyExtractor(TestConfigSource().load().currencies)

    private fun sms(text: String) = NormalizedSms(text, text, text.lowercase())

    private fun currency(text: String): String? = extractor.extract(sms(text))

    @Test
    fun inr_prefix_token() {
        assertEquals("INR", currency("INR 1,250.00 spent on HDFC Bank Credit Card xx5678"))
    }

    @Test
    fun rupee_dot_token_is_inr() {
        assertEquals("INR", currency("Sent Rs.450.00 From HDFC Bank A/C *4521"))
    }

    @Test
    fun plain_rs_token_is_inr() {
        assertEquals("INR", currency("Spent Rs. 1200.00 on YES BANK Credit Card XX8888"))
    }

    @Test
    fun usd_token_wins_over_inr_equivalent() {
        // Sample 22: USD spend even though "INR equivalent" is also mentioned.
        assertEquals(
            "USD",
            currency(
                "USD 49.99 spent on your Axis Bank Card XX9876 at NETFLIX.COM/US " +
                    "on 13-APR-26. INR equivalent will appear in statement.",
            ),
        )
    }

    @Test
    fun eur_token_detected() {
        assertEquals(
            "EUR",
            currency("Purchase of EUR 89.50 made on your SBI Card ending 4410 at IKEA BERLIN."),
        )
    }

    @Test
    fun aed_token_detected() {
        assertEquals(
            "AED",
            currency("AED 250.00 spent on your Card XX1234 at DUBAI MALL on 14-04-26."),
        )
    }

    @Test
    fun rupee_symbol_token_is_inr() {
        assertEquals("INR", currency("₹ 999.00 spent on your Credit Card XX5678"))
    }

    @Test
    fun no_currency_token_returns_null() {
        assertNull(currency("Use 458219 as your OTP for Net Banking login. Valid for 5 mins."))
    }

    @Test
    fun empty_returns_null() {
        assertNull(currency(""))
    }

    @Test
    fun foreign_currency_anywhere_takes_priority() {
        // Hidden-style: INR mentioned first, but the actual spend is in USD.
        assertEquals(
            "USD",
            currency(
                "Your card was charged. Statement INR balance updated. " +
                    "Spend amount USD 12.00 at APPLE.COM.",
            ),
        )
    }
}
