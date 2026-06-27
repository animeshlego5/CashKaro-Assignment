package com.cashkaro.smsparser.parser.extract

import com.cashkaro.smsparser.parser.CardType
import com.cashkaro.smsparser.parser.NormalizedSms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [DefaultCardExtractor]: every masked last-four form drawn from
 * the 25 samples, each card-type token, plus a novel (hidden-style) wording and
 * the no-card case.
 */
class CardExtractorTest {

    private val extractor = DefaultCardExtractor()

    private fun sms(text: String) = NormalizedSms(text, text, text.lowercase())

    // ---- lastFour forms ----

    @Test
    fun lowercase_xx_mask() {
        assertEquals("5678", extractor.extract(sms("HDFC Bank Credit Card xx5678 at SWIGGY")).lastFour)
    }

    @Test
    fun uppercase_XX_mask() {
        assertEquals("9876", extractor.extract(sms("Axis Bank Card no. XX9876 on 06-APR-26")).lastFour)
    }

    @Test
    fun star_mask_on_account() {
        assertEquals("4521", extractor.extract(sms("From HDFC Bank A/C *4521 To BIGBASKET")).lastFour)
    }

    @Test
    fun ending_keyword_without_mask() {
        assertEquals("1234", extractor.extract(sms("HDFC Bank Debit Card ending 1234 at SWIGGY")).lastFour)
    }

    @Test
    fun ending_in_with_xx_mask() {
        assertEquals("9907", extractor.extract(sms("BOBCARD One Credit Card ending in XX9907 on 08-04-2026")).lastFour)
    }

    @Test
    fun card_no_form() {
        assertEquals("9876", extractor.extract(sms("Card no. XX9876")).lastFour)
    }

    @Test
    fun credit_card_bare_mask_form() {
        assertEquals("8888", extractor.extract(sms("YES BANK Credit Card XX8888 at AMAZON")).lastFour)
    }

    @Test
    fun three_digit_masked_group_does_not_match() {
        // "Acct XX123" exposes only three digits — conservative: no false last-four.
        assertNull(extractor.extract(sms("ICICI Bank Acct XX123 debited Rs 2,500.00")).lastFour)
    }

    @Test
    fun reference_number_is_not_mistaken_for_last_four() {
        // No mask / ending keyword in front of the long ref => no last-four.
        assertNull(extractor.extract(sms("UPI Ref:240412345678. Call 18002662 if not you.")).lastFour)
    }

    // ---- card types ----

    @Test
    fun credit_card_type() {
        assertEquals(CardType.CREDIT_CARD, extractor.extract(sms("HDFC Bank Credit Card xx5678")).cardType)
    }

    @Test
    fun debit_card_type() {
        assertEquals(CardType.DEBIT_CARD, extractor.extract(sms("HDFC Bank Debit Card ending 1234")).cardType)
    }

    @Test
    fun account_type_from_ac() {
        assertEquals(CardType.ACCOUNT, extractor.extract(sms("Rs 1,200 debited from A/c XX4521 via UPI")).cardType)
    }

    @Test
    fun account_type_from_acct() {
        assertEquals(CardType.ACCOUNT, extractor.extract(sms("ICICI Bank Acct XX123 debited")).cardType)
    }

    @Test
    fun bare_card_type() {
        // "Card" with no credit/debit qualifier.
        assertEquals(CardType.BARE_CARD, extractor.extract(sms("HDFC Card xx5678 at CROMA")).cardType)
    }

    @Test
    fun unknown_type_when_no_card_token() {
        val info = extractor.extract(sms("Use 458219 as your OTP for HDFC Bank Net Banking login."))
        assertEquals(CardType.UNKNOWN, info.cardType)
        assertNull(info.lastFour)
    }

    @Test
    fun no_card_returns_unknown_and_null() {
        assertEquals(
            com.cashkaro.smsparser.parser.CardInfo(null, CardType.UNKNOWN),
            extractor.extract(sms("Get flat 50% off this weekend. T&C apply.")),
        )
    }

    // ---- novel / hidden-style wording ----

    @Test
    fun novel_wording_kotak_credit_card() {
        val info = extractor.extract(sms("Rs 999 spent on Kotak Credit Card **2468 at FLIPKART on 20-05-26"))
        assertEquals("2468", info.lastFour)
        assertEquals(CardType.CREDIT_CARD, info.cardType)
    }

    @Test
    fun novel_wording_ending_in_bare_digits() {
        assertEquals("4242", extractor.extract(sms("SBI Card ending in 4242 used for purchase")).lastFour)
    }
}
