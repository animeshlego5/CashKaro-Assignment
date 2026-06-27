package com.cashkaro.smsparser.parser.extract

import com.cashkaro.smsparser.parser.NormalizedSms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [DefaultAmountExtractor] — picking the TRANSACTION amount and never
 * a balance / limit / markup / EMI figure. Covers the relevant samples plus a
 * novel-wording (hidden-style) case.
 */
class AmountExtractorTest {

    private val extractor = DefaultAmountExtractor()

    private fun sms(text: String) = NormalizedSms(text, text, text.lowercase())

    private fun amount(text: String): Double? = extractor.extract(sms(text))

    @Test
    fun spend_amount_not_avl_limit() {
        // Sample 2: must pick 1,250.00, NOT the 1,45,300.00 available limit.
        val v = amount(
            "INR 1,250.00 spent on HDFC Bank Credit Card xx5678 at SWIGGY " +
                "on 03-04-2026. Avl Limit: INR 1,45,300.00.",
        )
        assertEquals(1250.0, v!!, 0.0001)
    }

    @Test
    fun rupee_dot_amount_with_decimals() {
        // Sample 1 style: Rs.450.00
        assertEquals(450.0, amount("Sent Rs.450.00 From HDFC Bank A/C *4521 To BIGBASKET")!!, 0.0001)
    }

    @Test
    fun plain_rupee_amount_no_decimals() {
        // Sample 4 style: Rs 50000
        assertEquals(50000.0, amount("Dear Customer, Rs 50000 credited to your A/c XX4521")!!, 0.0001)
    }

    @Test
    fun lakh_grouped_amount_standalone() {
        assertEquals(145300.0, amount("1,45,300.00")!!, 0.0001)
    }

    @Test
    fun foreign_spend_not_markup_percentage() {
        // Sample 22: USD 49.99 spend, NOT the 3.5% markup.
        val v = amount(
            "USD 49.99 spent on your Axis Bank Card XX9876 at NETFLIX.COM/US " +
                "on 13-APR-26. Foreign currency markup of 3.5% will be applied. " +
                "INR equivalent will appear in statement.",
        )
        assertEquals(49.99, v!!, 0.0001)
    }

    @Test
    fun no_amount_returns_null() {
        assertNull(amount("Use 458219 as your OTP for HDFC Bank Net Banking login."))
    }

    @Test
    fun empty_returns_null() {
        assertNull(amount(""))
    }

    @Test
    fun spend_amount_not_available_limit_axis() {
        // Sample 5: 320.00 spend, NOT 87,500.00 available limit.
        val v = amount(
            "INR 320.00 spent using Axis Bank Card no. XX9876 on 06-APR-26 " +
                "at AMAZON. Available Limit: INR 87,500.00.",
        )
        assertEquals(320.0, v!!, 0.0001)
    }

    @Test
    fun spend_amount_not_avl_lmt_abbrev() {
        // Sample 7: 1200.00 spend, NOT 78,500 avl lmt.
        val v = amount(
            "Spent Rs. 1200.00 on YES BANK Credit Card XX8888 at AMAZON " +
                "on 07-04-26. Avl Lmt: Rs 78,500.",
        )
        assertEquals(1200.0, v!!, 0.0001)
    }

    @Test
    fun refund_amount_picked() {
        // Sample 21 style: refund amount.
        val v = amount(
            "Refund of Rs 450.00 has been credited to your HDFC Card xx5678 " +
                "from BIGBASKET on 12-04-26 against original txn dated 02-04-26.",
        )
        assertEquals(450.0, v!!, 0.0001)
    }

    @Test
    fun novel_wording_hidden_style_ignores_balance() {
        // Hidden-style: different bank, "purchase", balance figure must be ignored.
        val v = amount(
            "Purchase of EUR 89.50 made on your SBI Card ending 4410 at IKEA " +
                "BERLIN. Your available balance is EUR 5,210.75.",
        )
        assertEquals(89.50, v!!, 0.0001)
    }

    @Test
    fun novel_wording_emi_instalment_ignored_for_principal() {
        // Sample 17 style: principal 75,000.00 spend, NOT the 6,847 EMI instalment.
        val v = amount(
            "Your Rs 75,000.00 spend on HDFC Card xx5678 at CROMA-ELECTRONICS " +
                "has been converted to EMI of Rs 6,847/month for 12 months at 13% interest.",
        )
        assertEquals(75000.0, v!!, 0.0001)
    }
}
