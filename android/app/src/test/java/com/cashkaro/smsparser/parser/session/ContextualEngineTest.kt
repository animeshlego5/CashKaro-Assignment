package com.cashkaro.smsparser.parser.session

import com.cashkaro.smsparser.parser.SmsParser
import com.cashkaro.smsparser.parser.config.TestConfigSource
import com.cashkaro.smsparser.parser.model.Decision
import com.cashkaro.smsparser.parser.session.model.SmsRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS-2 contextual-engine proof (buildphase-v2.md §6 exit criteria):
 *  (a) Netflix variants canonicalise to one merchant.
 *  (b) sample 21 (BigBasket refund) threads onto its original spend (sample 1).
 *  (c) sample 17 (EMI conversion) threads onto its original spend.
 *  (d) a recurring merchant is flagged.
 *  (e) two ₹-equal spends with DIFFERENT card4 do NOT merge (V4 conservatism).
 *  (f) the engine NEVER changes the underlying ParsedResult core fields (V1/V6).
 */
class ContextualEngineTest {

    private val config = TestConfigSource().load()
    private val parser = SmsParser.create(config)
    private val engine = ContextualEngine.create(config, parser)

    // --- the corpus samples used by the threading tests ---
    private val s1 = "Sent Rs.450.00 From HDFC Bank A/C *4521 To BIGBASKET on 02/04/26. Ref 405617287211. Not You? Call 18002586161/SMS BLOCK CC to 7308080808 to block CC."
    private val s2 = "INR 1,250.00 spent on HDFC Bank Credit Card xx5678 at SWIGGY on 03-04-2026. Avl Limit: INR 1,45,300.00."
    private val s17 = "Your Rs 75,000.00 spend on HDFC Card xx5678 at CROMA-ELECTRONICS has been converted to EMI of Rs 6,847/month for 12 months at 13% interest."
    private val s21 = "Refund of Rs 450.00 has been credited to your HDFC Card xx5678 from BIGBASKET on 12-04-26 against original txn dated 02-04-26."
    private val s22 = "USD 49.99 spent on your Axis Bank Card XX9876 at NETFLIX.COM/US on 13-APR-26. Foreign currency markup of 3.5% will be applied. INR equivalent will appear in statement."

    @Test
    fun a_netflix_variants_canonicalise_to_one_merchant() {
        val canon = MerchantCanonicalizer(config.merchantCategories)
        val a = canon.canonicalize(null, "NETFLIX.COM/US")
        val b = canon.canonicalize(null, "Rs 2,500 ... for NETFLIX_SUBSCRIPTION.")
        val c = canon.canonicalize(null, "to NETFLIX-MONTHLY. Avl Bal")
        assertNotNull(a); assertNotNull(b); assertNotNull(c)
        assertEquals("Netflix", a!!.canonical)
        assertEquals("Netflix", b!!.canonical)
        assertEquals("Netflix", c!!.canonical)
        assertEquals("entertainment", a.category)
    }

    @Test
    fun b_refund_threads_onto_original_bigbasket_spend() {
        val result = engine.process(listOf(SmsRecord(s1), SmsRecord(s2), SmsRecord(s21)))
        val r1 = result.results[0]
        val r21 = result.results[2]
        // #21 must share a thread with #1 via the explicit back-reference (date 02-04-26).
        assertEquals("#21 should thread onto #1", r1.threadId, r21.threadId)
        assertNotNull(r21.linkedTo)
        assertTrue("#21 linkedTo should include #1", r21.linkedTo!!.contains(r1.id))
        // The thread should net to ~0 (450 spend - 450 refund). #1 is an account
        // EXCLUDE so it contributes no INCLUDE amount; the refund nets -450 here.
        val thread = result.threads.first { it.threadId == r21.threadId }
        assertTrue("thread carries both events", thread.events.size >= 2)
    }

    @Test
    fun c_emi_conversion_threads_onto_original_spend() {
        // An explicit prior Croma spend on the same card + amount, then the EMI note.
        val priorSpend = "INR 75,000.00 spent on HDFC Bank Credit Card xx5678 at CROMA-ELECTRONICS on 09-04-2026. Avl Limit: INR 50,000.00."
        val result = engine.process(listOf(SmsRecord(priorSpend), SmsRecord(s17)))
        val spend = result.results[0]
        val emi = result.results[1]
        assertEquals("EMI should thread onto the original Croma spend", spend.threadId, emi.threadId)
        assertNotNull(emi.linkedTo)
        assertTrue(emi.linkedTo!!.contains(spend.id))
    }

    @Test
    fun d_recurring_merchant_is_flagged() {
        // Netflix appears once (sample 22) but is a known subscription -> recurring.
        val result = engine.process(listOf(SmsRecord(s22)))
        val netflix = result.merchants.first { it.canonical == "Netflix" }
        assertTrue("Netflix is a known subscription => recurring", netflix.recurring)
        assertTrue("the result row is flagged recurring", result.results[0].recurring)
    }

    @Test
    fun d2_repeat_merchant_is_flagged_recurring() {
        // Two BigBasket events in-session -> recurring by repetition.
        val result = engine.process(listOf(SmsRecord(s1), SmsRecord(s21)))
        val bb = result.merchants.first { it.canonical == "BigBasket" }
        assertEquals(2, bb.count)
        assertTrue("appears >=2x => recurring", bb.recurring)
    }

    @Test
    fun e_equal_amount_different_card_do_not_merge() {
        // Two ₹450 spends, same merchant token, DIFFERENT card4 -> must stay separate.
        val cardA = "INR 450.00 spent on HDFC Bank Credit Card xx1111 at SWIGGY on 03-04-2026. Avl Limit: INR 9,000.00."
        val cardB = "INR 450.00 spent on HDFC Bank Credit Card xx2222 at SWIGGY on 03-04-2026. Avl Limit: INR 9,000.00."
        val result = engine.process(listOf(SmsRecord(cardA), SmsRecord(cardB)))
        assertTrue(
            "different card4 must NOT merge (V4)",
            result.results[0].threadId != result.results[1].threadId,
        )
        assertNull(result.results[0].linkedTo)
        assertNull(result.results[1].linkedTo)
    }

    @Test
    fun f_core_fields_are_never_changed() {
        val texts = listOf(s1, s2, s17, s21, s22)
        val standalone = texts.map { parser.parse(it) }
        val records = texts.map { SmsRecord(it) }
        val enriched = engine.process(records).results

        assertEquals(texts.size, enriched.size)
        for (i in texts.indices) {
            val a = standalone[i]
            val b = enriched[i].core
            assertEquals("#$i rawSms", a.rawSms, b.rawSms)
            assertEquals("#$i decision", a.decision, b.decision)
            assertEquals("#$i excludeReason", a.excludeReason, b.excludeReason)
            assertEquals("#$i confidence", a.confidence, b.confidence, 0.0)
            assertEquals("#$i transaction", a.transaction, b.transaction)
            // EXCLUDE must keep a null transaction even after enrichment.
            if (b.decision == Decision.EXCLUDE) assertNull(b.transaction)
        }
    }

    @Test
    fun results_stay_one_to_one_and_in_input_order() {
        val texts = listOf(s2, s1, s22, s21)
        val result = engine.process(texts.map { SmsRecord(it) })
        assertEquals(texts.size, result.results.size)
        for (i in texts.indices) assertEquals(texts[i], result.results[i].core.rawSms)
    }
}
