package com.cashkaro.smsparser.parser.classify

import com.cashkaro.smsparser.parser.InclusionDecision
import com.cashkaro.smsparser.parser.NormalizedSms
import com.cashkaro.smsparser.parser.config.TestConfigSource
import com.cashkaro.smsparser.parser.model.ExcludeReason
import com.cashkaro.smsparser.parser.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [DefaultInclusionClassifier] — Stage 4. It runs AFTER the
 * ExclusionEngine, so the inputs here are messages that survived exclusion. It
 * decides credit-card-or-not (via the config-driven [CardSignal]) and, if so,
 * the [TxnType]. Default-deny (C2): no credit-card signal => Exclude(LOW_CONFIDENCE).
 *
 * Coverage: real sample wordings (2, 5, 7, 22 spends; 21 refund) plus
 * hidden-style novel wordings (synthetic cashback CREDIT; an ambiguous bare
 * "Card" + "A/C" with no limit language => deny).
 */
class InclusionClassifierTest {

    private val classifier = DefaultInclusionClassifier(CardSignal(TestConfigSource().load()))

    private fun sms(text: String) = NormalizedSms(text, text, text.lowercase())

    private fun classify(text: String) = classifier.classify(sms(text))

    private fun assertInclude(expected: TxnType, text: String) {
        val d = classify(text)
        assertTrue("expected Include but got $d for: $text", d is InclusionDecision.Include)
        assertEquals(expected, (d as InclusionDecision.Include).type)
    }

    private fun assertExclude(expected: ExcludeReason, text: String) {
        val d = classify(text)
        assertTrue("expected Exclude but got $d for: $text", d is InclusionDecision.Exclude)
        assertEquals(expected, (d as InclusionDecision.Exclude).reason)
    }

    // ---- Sample-derived inclusions (DEBIT spends) ----------------------------

    @Test
    fun sample2_hdfc_credit_card_spent_is_debit() {
        // Explicit "Credit Card" + "Avl Limit" + "spent" => clear card spend.
        assertInclude(
            TxnType.DEBIT,
            "INR 1,250.00 spent on HDFC Bank Credit Card xx5678 at SWIGGY on 03-04-2026. Avl Limit: INR 1,45,300.00.",
        )
    }

    @Test
    fun sample5_axis_bare_card_with_available_limit_is_debit() {
        // Bare "Card no." BUT "Available Limit" supplies the credit-card signal.
        assertInclude(
            TxnType.DEBIT,
            "INR 320.00 spent using Axis Bank Card no. XX9876 on 06-APR-26 at AMAZON. Available Limit: INR 87,500.00.",
        )
    }

    @Test
    fun sample7_yesbank_avl_lmt_is_debit() {
        // "Avl Lmt" abbreviation must still be a credit-card signal.
        assertInclude(
            TxnType.DEBIT,
            "Spent Rs. 1200.00 on YES BANK Credit Card XX8888 at AMAZON on 07-04-26. Avl Lmt: Rs 78,500.",
        )
    }

    @Test
    fun sample22_foreign_markup_spend_is_debit() {
        // "Foreign currency markup" carries the credit-card signal even on bare "Card".
        assertInclude(
            TxnType.DEBIT,
            "USD 49.99 spent on your Axis Bank Card XX9876 at NETFLIX.COM/US on 13-APR-26. " +
                "Foreign currency markup of 3.5% will be applied. INR equivalent will appear in statement.",
        )
    }

    // ---- Refund -------------------------------------------------------------

    @Test
    fun refund_credited_back_to_credit_card_is_refund() {
        // Refund wording wins over the "credited to" credit verb.
        assertInclude(
            TxnType.REFUND,
            "Refund of Rs 450.00 has been credited to your HDFC Bank Credit Card xx5678 from BIGBASKET on 12-04-26.",
        )
    }

    @Test
    fun reversal_wording_is_refund() {
        assertInclude(
            TxnType.REFUND,
            "Rs 999.00 reversal processed on your ICICI Credit Card XX1122. Avl Limit restored.",
        )
    }

    // ---- Relevant non-refund credit (cashback / reward) ----------------------

    @Test
    fun synthetic_cashback_credited_to_credit_card_is_credit() {
        // Hidden-style: a reward credited TO the card, not a spend, not a refund.
        assertInclude(
            TxnType.CREDIT,
            "Rs 200 cashback credited to your HDFC Credit Card xx5678 for May spends. Avl Limit updated.",
        )
    }

    @Test
    fun reward_points_credit_to_card_is_credit() {
        // Novel wording — "reward" + "credited to your" + a credit-card signal.
        assertInclude(
            TxnType.CREDIT,
            "Rs 350 reward has been credited to your Axis Credit Card XX9876. No action needed.",
        )
    }

    // ---- Default-deny (C2) ---------------------------------------------------

    @Test
    fun ambiguous_bare_card_with_account_and_no_limit_is_low_confidence() {
        // No credit-card signal (bare "Card" + "A/C", no limit/credit-card phrase) => deny.
        assertExclude(
            ExcludeReason.LOW_CONFIDENCE,
            "Rs 1,500 spent on your Card linked to A/C XX4521 on 10-04-26.",
        )
    }

    @Test
    fun message_with_no_card_signal_at_all_is_low_confidence() {
        // Plain-spend wording with zero credit-card signal => default-deny.
        assertExclude(
            ExcludeReason.LOW_CONFIDENCE,
            "Rs 800 spent at SWIGGY on 09-04-26.",
        )
    }

    @Test
    fun empty_body_is_low_confidence_not_a_crash() {
        // Robustness: no signal, no tokens => deny (never throws, never includes).
        assertExclude(ExcludeReason.LOW_CONFIDENCE, "")
    }

    @Test
    fun debit_on_credit_card_with_credit_word_nearby_stays_debit() {
        // "credit card" signal + "spent"; the word "credit" must not flip to CREDIT.
        assertInclude(
            TxnType.DEBIT,
            "Rs 2,100 spent on your HDFC Credit Card xx5678 at CROMA on 11-04-26. Avl Limit: Rs 90,000.",
        )
    }
}
