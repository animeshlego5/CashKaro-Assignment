package com.cashkaro.smsparser.parser

import com.cashkaro.smsparser.parser.config.TestConfigSource
import com.cashkaro.smsparser.parser.model.Decision
import com.cashkaro.smsparser.parser.model.TxnType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hidden-sample resilience (Phase 3, C6/C8): the parser must be order-independent,
 * survive an empty batch, behave conservatively on never-before-seen wordings
 * (new bank, multi-bank footer, fresh OTP, foreign currency, a synthetic CREDIT),
 * and never crash on garbage. NONE of these assert against fixed sample strings.
 */
class ResilienceTest {

    private val parser = SmsParser.create(TestConfigSource().load())
    private val samples: List<Pair<Int, String>> = Gson()
        .fromJson<List<SampleInput>>(
            javaClass.classLoader!!.getResourceAsStream("samples.json")!!.bufferedReader().use { it.readText() },
            object : TypeToken<List<SampleInput>>() {}.type,
        ).map { it.id to it.text }

    @Test
    fun results_are_order_independent() {
        // Parsing is per-string and stateless; reordering inputs must not change a
        // given SMS's result. Guards against any accidental index/order reliance (C6).
        val inOrder = samples.associate { (id, text) -> id to parser.parse(text) }
        for ((id, text) in samples.reversed()) {
            val r = parser.parse(text)
            assertEquals("#$id decision", inOrder[id]!!.decision, r.decision)
            assertEquals("#$id reason", inOrder[id]!!.excludeReason, r.excludeReason)
            assertEquals("#$id type", inOrder[id]!!.transaction?.type, r.transaction?.type)
        }
    }

    @Test
    fun empty_batch_returns_empty() {
        assertTrue(parser.parseAll(emptyList()).isEmpty())
    }

    @Test
    fun appended_synthetic_credit_card_credit_is_CREDIT() {
        // A reward CREDITED to a credit card (not a spend, not a refund, and not a
        // promotional "cashback"/"% off" offer) exercises the CREDIT type path.
        val r = parser.parse(
            "Rs 350 reward credited to your HDFC Bank Credit Card xx5678 on 20-05-26. Avl Limit: Rs 95,000.",
        )
        assertEquals(Decision.INCLUDE, r.decision)
        assertEquals(TxnType.CREDIT, r.transaction?.type)
        assertEquals("INR", r.transaction?.currency)
    }

    @Test
    fun appended_multi_bank_resolves_issuer_not_footer() {
        // Issuer (SBI) sits next to card/spend/limit language; the helpline footer
        // bank (HDFC) must lose the proximity tie-break.
        val r = parser.parse(
            "Rs 1,499.00 spent on SBI Credit Card XX7788 at CROMA on 21-05-26. Avl Limit Rs 60,000. " +
                "Call HDFC Bank helpline if not you.",
        )
        assertEquals(Decision.INCLUDE, r.decision)
        assertEquals("State Bank of India", r.transaction?.bank)
    }

    @Test
    fun appended_fresh_otp_wording_is_excluded() {
        val r = parser.parse("123456 is your one time password for ICICI net banking. Valid 10 min.")
        assertEquals(Decision.EXCLUDE, r.decision)
        assertEquals("OTP", r.excludeReason?.name)
        assertNull(r.transaction)
    }

    @Test
    fun appended_foreign_currency_spend_is_included_non_INR() {
        val r = parser.parse(
            "EUR 75.00 spent on your HDFC Bank Credit Card xx5678 at ZARA BERLIN on 22-05-26. " +
                "Foreign currency markup of 3.5% applies.",
        )
        assertEquals(Decision.INCLUDE, r.decision)
        assertEquals("EUR", r.transaction?.currency)
        assertEquals(75.0, r.transaction!!.amount, 0.01)
    }

    @Test
    fun appended_unknown_bank_spend_still_conservative() {
        // Unknown issuer -> bank null (never guessed), but a clear credit-card spend
        // is still INCLUDE/DEBIT with the amount/currency extracted.
        val r = parser.parse(
            "Rs 999.00 spent on your IDFC FIRST Credit Card xx3344 at DECATHLON on 23-05-26. Avl Limit Rs 50,000.",
        )
        assertEquals(Decision.INCLUDE, r.decision)
        assertEquals(TxnType.DEBIT, r.transaction?.type)
        assertNull("unknown bank must not be guessed", r.transaction?.bank)
    }

    @Test
    fun garbage_and_malformed_never_crash_and_exclude() {
        for (junk in listOf("", "   ", "Spent Rs", "Rs. 2,4", "!!!", "12345", "hello world")) {
            val r = parser.parse(junk)
            assertEquals("junk '$junk' should EXCLUDE", Decision.EXCLUDE, r.decision)
            assertNull("junk '$junk' transaction", r.transaction)
            assertTrue("junk '$junk' confidence in range", r.confidence in 0.0..1.0)
        }
    }

    private data class SampleInput(val id: Int, val text: String)
}
