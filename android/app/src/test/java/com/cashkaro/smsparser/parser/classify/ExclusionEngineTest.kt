package com.cashkaro.smsparser.parser.classify

import com.cashkaro.smsparser.parser.NormalizedSms
import com.cashkaro.smsparser.parser.config.ExclusionRuleDef
import com.cashkaro.smsparser.parser.config.TestConfigSource
import com.cashkaro.smsparser.parser.model.ExcludeReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [DefaultExclusionEngine] (Component 2A).
 *
 * Coverage:
 *  - Every [ExcludeReason] category that the seed rules can emit is exercised
 *    against a representative sample SMS (the 25-sample oracle plus novel,
 *    hidden-style wordings).
 *  - The two HARD invariants: a credit-card "Avl/Available Limit" message must
 *    NOT hit BALANCE_ALERT, and the 7 real credit-card spends/refunds must
 *    return null (no exclusion).
 *  - Ordering invariants (UPI before BALANCE_ALERT; action rules + BALANCE_ALERT
 *    before the generic SAVINGS_ACCOUNT catch-all).
 *  - C5 config-extensibility: a brand-new rule supplied purely as data fires
 *    without any engine code change.
 */
class ExclusionEngineTest {

    /** Engine built from the real (mirrored) config — the production rule set. */
    private val engine: DefaultExclusionEngine = run {
        val config = TestConfigSource().load()
        DefaultExclusionEngine(config.exclusionRules, CardSignal(config))
    }

    private fun sms(text: String) = NormalizedSms(text, text, text.lowercase())

    private fun reasonOf(text: String): ExcludeReason? = engine.firstMatchingReason(sms(text))

    // ---------------------------------------------------------------------
    // Per-category coverage (sample oracle).
    // ---------------------------------------------------------------------

    @Test
    fun otp_is_excluded() {
        // Sample 10
        val s = "Use 458219 as your OTP for HDFC Bank Net Banking login. Valid for 5 mins. Do NOT share with anyone."
        assertEquals(ExcludeReason.OTP, reasonOf(s))
    }

    @Test
    fun declined_is_excluded() {
        // Sample 15 — note this also mentions a credit card, but the action wins.
        val s = "Transaction Declined: Attempt to spend Rs. 9,999 on your ICICI Credit Card XX1122 at FOREIGN MERCHANT was declined due to insufficient credit limit."
        assertEquals(ExcludeReason.DECLINED, reasonOf(s))
    }

    @Test
    fun offer_is_excluded() {
        // Sample 13
        val s = "Get flat 50% off + extra 10% cashback on travel bookings with HDFC Credit Cards this weekend. T&C apply. Visit hdfcbank.com/offers."
        assertEquals(ExcludeReason.OFFER, reasonOf(s))
    }

    @Test
    fun futureAutoDebit_is_excluded() {
        // Sample 14
        val s = "Dear Customer, Rs 2,500 will be auto debited via E-Mandate from your HDFC Card XX5678 on 12-04-26 for NETFLIX_SUBSCRIPTION. Please maintain sufficient limit."
        assertEquals(ExcludeReason.FUTURE_AUTO_DEBIT, reasonOf(s))
    }

    @Test
    fun emiConversion_is_excluded() {
        // Sample 17
        val s = "Your Rs 75,000.00 spend on HDFC Card xx5678 at CROMA-ELECTRONICS has been converted to EMI of Rs 6,847/month for 12 months at 13% interest."
        assertEquals(ExcludeReason.EMI_CONVERSION, reasonOf(s))
    }

    @Test
    fun feeOrCharge_is_excluded() {
        // Sample 18
        val s = "Finance charge of Rs 1,250.45 + GST Rs 225.08 has been debited from your HDFC Credit Card xx5678 for late payment on bill dated 31-03-2026."
        assertEquals(ExcludeReason.FEE_OR_CHARGE, reasonOf(s))
    }

    @Test
    fun cardPayment_is_excluded_only_with_card_signal() {
        // Sample 19 — "payment of" + credit-card signal => CARD_PAYMENT.
        val s = "Payment of Rs 23,450.00 received towards your HDFC Bank Credit Card xx5678 on 11-04-26. Thank you."
        assertEquals(ExcludeReason.CARD_PAYMENT, reasonOf(s))
    }

    @Test
    fun billDue_is_excluded() {
        // Sample 12
        val s = "Your HDFC Bank Credit Card xx5678 bill of Rs 23,450.00 is due on 15-04-26. View your bill at hdfcbank.com/billview."
        assertEquals(ExcludeReason.BILL_DUE, reasonOf(s))
    }

    @Test
    fun insurance_is_excluded() {
        // Sample 23
        val s = "Premium of Rs 12,500 debited from A/c XX4521 on 13-04-26 for HDFC Life Insurance Policy XYZ-2026. Renewal complete."
        assertEquals(ExcludeReason.INSURANCE, reasonOf(s))
    }

    @Test
    fun investment_is_excluded() {
        // Sample 16
        val s = "Your SIP of Rs 5,000 in Mirae Asset Large Cap Fund folio 12345678 has been debited from A/c XX4521 on 10-04-26."
        assertEquals(ExcludeReason.INVESTMENT, reasonOf(s))
    }

    @Test
    fun debitCard_is_excluded() {
        // Sample 6
        val s = "Transaction Alert: Rs. 500.00 debited from your HDFC Bank Debit Card ending 1234 at SWIGGY on 06-04-26."
        assertEquals(ExcludeReason.DEBIT_CARD, reasonOf(s))
    }

    @Test
    fun upiBankAccount_is_excluded() {
        // Sample 20 — UPI debit from a savings account.
        val s = "Rs 1,200 debited from A/c XX4521 via UPI on 11-04-26. UPI/P2A/MOHAN-SHARMA@OKAXIS/Personal. UPI Ref: 240411887211."
        assertEquals(ExcludeReason.UPI_BANK_ACCOUNT, reasonOf(s))
    }

    @Test
    fun salaryCredit_is_excluded() {
        // Sample 4 — salary credit to a savings account (also quotes Avl Bal).
        val s = "Dear Customer, Rs 50000 credited to your A/c XX4521 on 05-04-2026 by SALARY-ACMECORP. Avl Bal: Rs 1,52,300.45."
        assertEquals(ExcludeReason.SALARY_CREDIT, reasonOf(s))
    }

    @Test
    fun balanceAlert_is_excluded() {
        // Sample 11 — pure balance enquiry, no spend/debit action.
        val s = "Avl Bal in your A/C XX4521 as on 08-04-26 is INR 1,02,450.30. Call 18002586161 for details."
        assertEquals(ExcludeReason.BALANCE_ALERT, reasonOf(s))
    }

    @Test
    fun savingsAccount_is_the_catch_all_for_a_bare_account_debit() {
        // Sample 1 — account debit with no more-specific action token.
        val s = "Sent Rs.450.00 From HDFC Bank A/C *4521 To BIGBASKET on 02/04/26. Ref 405617287211. Not You? Call 18002586161/SMS BLOCK CC to 7308080808 to block CC."
        assertEquals(ExcludeReason.SAVINGS_ACCOUNT, reasonOf(s))
    }

    // ---------------------------------------------------------------------
    // Ordering invariants.
    // ---------------------------------------------------------------------

    @Test
    fun upi_takes_precedence_over_balanceAlert_when_both_present() {
        // Sample 24 — a UPI debit that also quotes "Avl Bal". UPI must win.
        val s = "Rs.99 debited from A/c XX4521 via UPI on 14-04-26. UPI Ref: 240478234511 to NETFLIX-MONTHLY. Avl Bal: Rs 1,02,351.30."
        assertEquals(ExcludeReason.UPI_BANK_ACCOUNT, reasonOf(s))
    }

    @Test
    fun balanceAlert_takes_precedence_over_savings_catchAll() {
        // "Avl Bal" + "A/C" both present => BALANCE_ALERT must win over SAVINGS.
        val s = "Avl Bal in your A/C XX1234 is Rs 5,000.00 as on 01-01-26."
        assertEquals(ExcludeReason.BALANCE_ALERT, reasonOf(s))
    }

    @Test
    fun upiBankAccount_takes_precedence_over_savings_catchAll() {
        // Sample 3 — account debited, then UPI credit; UPI rule precedes SAVINGS.
        val s = "ICICI Bank Acct XX123 debited Rs 2,500.00 on 04-Apr-26 & credited to UPI/swiggy@hdfc/Payment. UPI Ref:240412345678. Call 18002662 if not you."
        assertEquals(ExcludeReason.UPI_BANK_ACCOUNT, reasonOf(s))
    }

    // ---------------------------------------------------------------------
    // HARD invariant 1: credit-card limit language must NOT hit BALANCE_ALERT.
    // ---------------------------------------------------------------------

    @Test
    fun avlLimit_does_not_trigger_balanceAlert() {
        // Sample 2 — a real spend whose only "avl ..." token is the credit limit.
        val s = "INR 1,250.00 spent on HDFC Bank Credit Card xx5678 at SWIGGY on 03-04-2026. Avl Limit: INR 1,45,300.00."
        val r = reasonOf(s)
        assertTrue("avl limit must never be read as a balance alert", r != ExcludeReason.BALANCE_ALERT)
    }

    @Test
    fun availableLimit_does_not_trigger_balanceAlert() {
        // Sample 5 — "Available Limit", a credit-card signal, not a balance alert.
        val s = "INR 320.00 spent using Axis Bank Card no. XX9876 on 06-APR-26 at AMAZON. Available Limit: INR 87,500.00."
        val r = reasonOf(s)
        assertTrue("available limit must never be read as a balance alert", r != ExcludeReason.BALANCE_ALERT)
    }

    // ---------------------------------------------------------------------
    // HARD invariant 2: the 7 credit-card spends/refunds must return null.
    // ---------------------------------------------------------------------

    @Test
    fun the_seven_credit_card_spends_and_refunds_are_not_excluded() {
        val keepers = listOf(
            // 2 — HDFC Credit Card spend
            "INR 1,250.00 spent on HDFC Bank Credit Card xx5678 at SWIGGY on 03-04-2026. Avl Limit: INR 1,45,300.00.",
            // 5 — Axis Card spend, Available Limit
            "INR 320.00 spent using Axis Bank Card no. XX9876 on 06-APR-26 at AMAZON. Available Limit: INR 87,500.00.",
            // 7 — YES BANK Credit Card spend, Avl Lmt
            "Spent Rs. 1200.00 on YES BANK Credit Card XX8888 at AMAZON on 07-04-26. Avl Lmt: Rs 78,500.",
            // 8 — Edge Federal Bank Credit Card spend
            "Hey there, you've spent Rs 1836.00 to HOSPITALITY PVT DELHI IN on your Edge Federal Bank Credit Card ending 4422 on 07-04-2026. Tap to view your transactions in the Jupiter app.",
            // 9 — BOBCARD One Credit Card spend
            "You've spent Rs. 849.00 at Blackwater Coffee, Gurgaon with your BOBCARD One Credit Card ending in XX9907 on 08-04-2026.",
            // 21 — refund credited to a credit card
            "Refund of Rs 450.00 has been credited to your HDFC Card xx5678 from BIGBASKET on 12-04-26 against original txn dated 02-04-26.",
            // 22 — USD foreign-currency credit-card spend
            "USD 49.99 spent on your Axis Bank Card XX9876 at NETFLIX.COM/US on 13-APR-26. Foreign currency markup of 3.5% will be applied. INR equivalent will appear in statement.",
        )
        for (text in keepers) {
            assertNull("must NOT be excluded: $text", reasonOf(text))
        }
    }

    @Test
    fun refund_credited_phrase_does_not_trip_balanceAlert_or_salary() {
        // The word "credited" appears in the refund (21); ensure no balance/salary
        // false-positive sneaks in, and the result is a clean null.
        val s = "Refund of Rs 450.00 has been credited to your HDFC Card xx5678 from BIGBASKET on 12-04-26 against original txn dated 02-04-26."
        assertNull(reasonOf(s))
    }

    // ---------------------------------------------------------------------
    // Hidden-style (novel-wording) cases — generalisation, no sample copying.
    // ---------------------------------------------------------------------

    @Test
    fun novel_otp_wording_is_excluded() {
        // Different bank, different OTP phrasing than any sample.
        val s = "123456 is your verification code for SBI Card. Do not share this one time password with anyone."
        assertEquals(ExcludeReason.OTP, reasonOf(s))
    }

    @Test
    fun novel_annual_fee_is_a_fee_or_charge() {
        // Annual-fee debit on a credit card — a fee, never a countable spend.
        val s = "Annual fee of Rs 499 + GST has been levied on your Kotak Credit Card ending 7788."
        assertEquals(ExcludeReason.FEE_OR_CHARGE, reasonOf(s))
    }

    @Test
    fun novel_standing_instruction_is_future_auto_debit() {
        val s = "Rs 999 will be debited via standing instruction from your SBI Credit Card on 20-05-26 for SPOTIFY."
        assertEquals(ExcludeReason.FUTURE_AUTO_DEBIT, reasonOf(s))
    }

    @Test
    fun novel_clean_credit_card_spend_is_not_excluded() {
        // A bank/merchant/format never seen in the 25 samples; must stay null.
        val s = "AED 75.00 spent on your IndusInd Bank Credit Card ending 3344 at CARREFOUR DUBAI on 19-May-2026. Avl Limit: AED 12,000.00."
        assertNull(reasonOf(s))
    }

    // ---------------------------------------------------------------------
    // C5: rules are pure data — a brand-new in-code rule fires with no code edit.
    // ---------------------------------------------------------------------

    @Test
    fun custom_in_code_rule_fires_proving_config_driven_design() {
        val cardSignal = CardSignal(
            creditCardSignals = listOf("credit card"),
            nonCardSignals = listOf("a/c"),
        )
        // A category the seed config does not have: crypto-purchase exclusion.
        val customRules = listOf(
            ExclusionRuleDef(
                reason = "INVESTMENT",
                any = listOf("crypto", "bitcoin"),
                unless = emptyList(),
                withCard = false,
                notCreditCard = false,
            ),
        )
        val customEngine = DefaultExclusionEngine(customRules, cardSignal)
        val s = sms("Rs 5,000 used to buy Bitcoin on WazirX from your account.")
        assertEquals(ExcludeReason.INVESTMENT, customEngine.firstMatchingReason(s))
        // And a message lacking the custom trigger is untouched.
        assertNull(customEngine.firstMatchingReason(sms("INR 200 spent at CAFE on your Credit Card.")))
    }

    @Test
    fun unknown_reason_code_falls_back_to_low_confidence() {
        // fromCode maps an unknown code to LOW_CONFIDENCE rather than crashing.
        val rules = listOf(
            ExclusionRuleDef(
                reason = "TOTALLY_MADE_UP_CODE",
                any = listOf("gambling"),
                unless = emptyList(),
                withCard = false,
                notCreditCard = false,
            ),
        )
        val e = DefaultExclusionEngine(rules, CardSignal(listOf("credit card"), listOf("a/c")))
        assertEquals(ExcludeReason.LOW_CONFIDENCE, e.firstMatchingReason(sms("Rs 100 spent on gambling site.")))
    }

    @Test
    fun withCard_qualifier_requires_a_credit_card_signal() {
        // CARD_PAYMENT is withCard:true — "payment of" without a card signal must
        // NOT be claimed by it. Here it falls through to SAVINGS (account debit).
        val noCard = "Payment of Rs 1,000 made from your A/c XX4521 on 01-01-26."
        assertEquals(ExcludeReason.SAVINGS_ACCOUNT, reasonOf(noCard))
    }

    @Test
    fun notCreditCard_qualifier_blocks_savings_for_a_credit_card_message() {
        // SAVINGS is notCreditCard:true. A message with the word "account" AND a
        // credit-card signal must not be force-excluded as a savings account.
        val s = "Your credit card account statement spend of Rs 500 at SHOP is recorded."
        val r = reasonOf(s)
        assertTrue("credit-card message must not be excluded as SAVINGS_ACCOUNT", r != ExcludeReason.SAVINGS_ACCOUNT)
    }

    @Test
    fun no_rule_matches_returns_null() {
        // Generic credit-card spend with no exclusion trigger at all.
        val s = "Rs 250.00 spent on your HDFC Bank Credit Card xx9999 at STARBUCKS on 01-06-26."
        assertNull(reasonOf(s))
    }
}
