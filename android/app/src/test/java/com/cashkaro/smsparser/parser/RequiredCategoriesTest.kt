package com.cashkaro.smsparser.parser

import com.cashkaro.smsparser.parser.config.TestConfigSource
import com.cashkaro.smsparser.parser.model.Decision
import com.cashkaro.smsparser.parser.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 8 unit-test categories REQUIRED by docs/Testing.md, each a focused, named
 * test driving the FULL [SmsParser] end-to-end over the relevant sample wording.
 * (These overlap the golden set but make the required matrix explicit + readable.)
 */
class RequiredCategoriesTest {

    private val parser = SmsParser.create(TestConfigSource().load())

    @Test
    fun category1_clear_credit_card_spend() {
        val r = parser.parse(
            "INR 1,250.00 spent on HDFC Bank Credit Card xx5678 at SWIGGY on 03-04-2026. Avl Limit: INR 1,45,300.00.",
        )
        assertEquals(Decision.INCLUDE, r.decision)
        val t = r.transaction!!
        assertEquals(TxnType.DEBIT, t.type)
        assertEquals("HDFC Bank", t.bank)
        assertEquals(1250.0, t.amount, 0.001)
        assertEquals("INR", t.currency)
        assertEquals("5678", t.cardLastFour)
        assertEquals("SWIGGY", t.merchant)
        assertEquals("2026-04-03", t.date)
        assertTrue("a clear spend should be high-confidence", r.confidence >= 0.85)
    }

    @Test
    fun category2_debit_card_exclusion() {
        val r = parser.parse(
            "Transaction Alert: Rs. 500.00 debited from your HDFC Bank Debit Card ending 1234 at SWIGGY on 06-04-26.",
        )
        assertEquals(Decision.EXCLUDE, r.decision)
        assertEquals("DEBIT_CARD", r.excludeReason?.name)
        assertNull(r.transaction)
    }

    @Test
    fun category3_otp_exclusion() {
        val r = parser.parse(
            "Use 458219 as your OTP for HDFC Bank Net Banking login. Valid for 5 mins. Do NOT share with anyone.",
        )
        assertEquals(Decision.EXCLUDE, r.decision)
        assertEquals("OTP", r.excludeReason?.name)
        assertNull(r.transaction)
    }

    @Test
    fun category4_upi_savings_account_exclusion() {
        val r = parser.parse(
            "Rs 1,200 debited from A/c XX4521 via UPI on 11-04-26. UPI/P2A/MOHAN-SHARMA@OKAXIS/Personal. UPI Ref: 240411887211.",
        )
        assertEquals(Decision.EXCLUDE, r.decision)
        assertEquals("UPI_BANK_ACCOUNT", r.excludeReason?.name)
        assertNull(r.transaction)
    }

    @Test
    fun category5a_cobranded_jupiter_edge_resolves_to_federal_bank() {
        val r = parser.parse(
            "Hey there, you've spent Rs 1836.00 to HOSPITALITY PVT DELHI IN on your Edge Federal Bank Credit Card " +
                "ending 4422 on 07-04-2026. Tap to view your transactions in the Jupiter app.",
        )
        assertEquals(Decision.INCLUDE, r.decision)
        assertEquals("Federal Bank", r.transaction?.bank)
    }

    @Test
    fun category5b_cobranded_bobcard_resolves_to_bank_of_baroda() {
        val r = parser.parse(
            "You've spent Rs. 849.00 at Blackwater Coffee, Gurgaon with your BOBCARD One Credit Card " +
                "ending in XX9907 on 08-04-2026.",
        )
        assertEquals(Decision.INCLUDE, r.decision)
        assertEquals("Bank of Baroda", r.transaction?.bank)
    }

    @Test
    fun category6_refund() {
        val r = parser.parse(
            "Refund of Rs 450.00 has been credited to your HDFC Card xx5678 from BIGBASKET on 12-04-26 " +
                "against original txn dated 02-04-26.",
        )
        assertEquals(Decision.INCLUDE, r.decision)
        val t = r.transaction!!
        assertEquals(TxnType.REFUND, t.type)
        assertEquals("HDFC Bank", t.bank)
        assertEquals(450.0, t.amount, 0.001)
        assertEquals("INR", t.currency)
        assertEquals("2026-04-12", t.date)
    }

    @Test
    fun category7_foreign_currency_transaction() {
        val r = parser.parse(
            "USD 49.99 spent on your Axis Bank Card XX9876 at NETFLIX.COM/US on 13-APR-26. " +
                "Foreign currency markup of 3.5% will be applied. INR equivalent will appear in statement.",
        )
        assertEquals(Decision.INCLUDE, r.decision)
        val t = r.transaction!!
        assertEquals(TxnType.DEBIT, t.type)
        assertEquals("USD", t.currency)
        assertEquals(49.99, t.amount, 0.001)
        assertEquals("Axis Bank", t.bank)
        // C7: a USD spend contributes 0 to INR totals.
        val inrTotal = listOf(r).filter { it.transaction?.currency == "INR" }.sumOf { it.transaction!!.amount }
        assertEquals(0.0, inrTotal, 0.001)
    }

    @Test
    fun category8_malformed_sms() {
        val r = parser.parse("Spent Rs. 2,4")
        assertEquals(Decision.EXCLUDE, r.decision)
        assertEquals("MALFORMED_SMS", r.excludeReason?.name)
        assertNull(r.transaction)
        assertTrue("malformed confidence should be ~0.1", r.confidence <= 0.2)
    }

    // ---------------------------------------------------------------------
    // D2 (buildphase-v2.md §3): KEEP SALARY_CREDIT / INVESTMENT / INSURANCE.
    // End-to-end proof that they are LOAD-BEARING for CARD-BASED messages — a
    // future reader must NOT "simplify" these rules away. These messages carry a
    // credit-card signal AND a spend verb; without the dedicated exclusion rules
    // the parser would wrongly INCLUDE them as a DEBIT spend (and the generic
    // notCreditCard SAVINGS_ACCOUNT catch-all would not fire to save us). The
    // assertions below pin decision=EXCLUDE with the specific reason code and a
    // null transaction (never INCLUDE/DEBIT, never SAVINGS_ACCOUNT/LOW_CONFIDENCE).
    // ---------------------------------------------------------------------

    @Test
    fun card_based_insurance_premium_excludes_as_insurance_not_a_spend() {
        val r = parser.parse(
            "Rs 12,500 spent on your HDFC Bank Credit Card xx5678 for HDFC Life Insurance Premium.",
        )
        assertEquals(Decision.EXCLUDE, r.decision)
        assertEquals("INSURANCE", r.excludeReason?.name)
        assertNull(r.transaction)
    }

    @Test
    fun card_based_sip_excludes_as_investment_not_a_spend() {
        val r = parser.parse(
            "Rs 5,000 spent on your HDFC Bank Credit Card xx5678 towards your SIP in Mirae Asset Mutual Fund.",
        )
        assertEquals(Decision.EXCLUDE, r.decision)
        assertEquals("INVESTMENT", r.excludeReason?.name)
        assertNull(r.transaction)
    }
}
