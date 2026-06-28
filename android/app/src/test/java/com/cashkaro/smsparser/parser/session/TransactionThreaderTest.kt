package com.cashkaro.smsparser.parser.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Direct unit tests for the conservative [TransactionThreader] (WS-2 / V4). */
class TransactionThreaderTest {

    private fun sig(
        stage: LifecycleStage = LifecycleStage.SPEND,
        card4: String? = null,
        amount: Double? = null,
        merchant: String? = null,
        primaryDate: String? = null,
        backRefDate: String? = null,
        time: Long? = null,
    ) = CorrelationSignals(stage, card4, amount, merchant, primaryDate, backRefDate, time)

    private fun node(id: String, s: CorrelationSignals) = TransactionThreader.Node(id, s)

    @Test
    fun strong_key_merges_same_card_amount_merchant() {
        val t = TransactionThreader(15).thread(
            listOf(
                node("0", sig(card4 = "5678", amount = 1250.0, merchant = "Swiggy", primaryDate = "2026-04-03")),
                node("1", sig(stage = LifecycleStage.EMI, card4 = "5678", amount = 1250.0, merchant = "Swiggy", primaryDate = "2026-04-03")),
            ),
        )
        assertEquals(t.threadIdByIndex[0], t.threadIdByIndex[1])
    }

    @Test
    fun different_card_does_not_merge() {
        val t = TransactionThreader(15).thread(
            listOf(
                node("0", sig(card4 = "1111", amount = 450.0, merchant = "Swiggy", primaryDate = "2026-04-03")),
                node("1", sig(card4 = "2222", amount = 450.0, merchant = "Swiggy", primaryDate = "2026-04-03")),
            ),
        )
        assertNotEquals(t.threadIdByIndex[0], t.threadIdByIndex[1])
    }

    @Test
    fun back_reference_merges_on_dated_original_plus_merchant_amount() {
        val original = sig(card4 = "4521", amount = 450.0, merchant = "BigBasket", primaryDate = "2026-04-02")
        val refund = sig(
            stage = LifecycleStage.REFUND, card4 = "5678", amount = 450.0, merchant = "BigBasket",
            primaryDate = "2026-04-12", backRefDate = "2026-04-02",
        )
        val t = TransactionThreader(15).thread(listOf(node("0", original), node("1", refund)))
        // Different card4, but same merchant+amount + dated back-ref -> linked.
        assertEquals(t.threadIdByIndex[0], t.threadIdByIndex[1])
        // Refund ordered after the spend within the thread.
        val members = t.membersByThread[t.threadIdByIndex[0]]!!
        assertEquals(listOf(0, 1), members)
    }

    @Test
    fun real_timestamps_outside_window_do_not_merge() {
        val base = 1_700_000_000_000L
        val t = TransactionThreader(15).thread(
            listOf(
                node("0", sig(card4 = "5678", amount = 500.0, merchant = "Swiggy", time = base)),
                node("1", sig(card4 = "5678", amount = 500.0, merchant = "Swiggy", time = base + 60 * 60_000L)),
            ),
        )
        assertNotEquals(t.threadIdByIndex[0], t.threadIdByIndex[1])
    }

    @Test
    fun no_strong_signal_yields_singleton_threads() {
        val t = TransactionThreader(15).thread(
            listOf(
                node("0", sig(merchant = "Swiggy", amount = 100.0)),
                node("1", sig(merchant = "Amazon", amount = 200.0)),
            ),
        )
        assertNotEquals(t.threadIdByIndex[0], t.threadIdByIndex[1])
    }
}
