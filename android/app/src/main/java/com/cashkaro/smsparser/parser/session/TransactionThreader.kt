package com.cashkaro.smsparser.parser.session

import kotlin.math.abs

/**
 * Groups an ordered batch of parsed messages into lifecycle threads (WS-2, V4).
 *
 * Conservative by construction: a message starts in its OWN thread and is only
 * merged into an earlier one on a STRONG signal. A wrong link is worse than no
 * link, so when in doubt we leave a singleton thread of one.
 *
 * Two merge signals, both strong:
 *  1. **Strong key** — same non-null `card4` AND equal `amount` AND same non-null
 *     `canonicalMerchant`, within the time window. This links a spend to its EMI
 *     conversion (#17: both carry card 5678 + the Rs 75,000 amount + Croma).
 *  2. **Explicit back-reference** — a message carrying a `backRefDate` ("against
 *     original txn dated 02-04-26") merges onto an earlier message whose
 *     `primaryDate` equals that back-ref date AND they agree on a strong pair
 *     (same merchant AND amount, or same card4 AND amount). This links the
 *     BigBasket refund (#21) onto the original BigBasket debit (#1) even though
 *     the refund posts to the card and the original came off the account.
 *
 * Window: when both messages have a `receivedAt`, they must be within
 * [windowMillis]. When only in-body dates exist, "same day" is required. When a
 * message has no time at all, the time gate is skipped (order-only fallback) — we
 * still require the strong key, so this stays conservative.
 */
class TransactionThreader(private val windowMinutes: Int = 15) {

    private val windowMillis: Long = windowMinutes.toLong() * 60_000L

    /** One message's correlation view + its session id, in input order. */
    data class Node(val id: String, val signals: CorrelationSignals)

    /**
     * Returns, for each input node (by index), the thread id it belongs to and the
     * ordered membership. Result preserves input order of nodes.
     */
    data class Threading(
        /** node index -> threadId */
        val threadIdByIndex: List<String>,
        /** threadId -> ordered member node indices (lifecycle order, then input order) */
        val membersByThread: LinkedHashMap<String, MutableList<Int>>,
    )

    fun thread(nodes: List<Node>): Threading {
        // Union-find over node indices; root index seeds the thread id.
        val parent = IntArray(nodes.size) { it }

        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != c) { val n = parent[c]; parent[c] = r; c = n }
            return r
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra == rb) return
            // Keep the EARLIER (smaller index) node as the root so the primary
            // transaction anchors the thread.
            if (ra < rb) parent[rb] = ra else parent[ra] = rb
        }

        for (i in nodes.indices) {
            for (j in 0 until i) {
                if (shouldLink(nodes[j].signals, nodes[i].signals)) {
                    union(i, j)
                }
            }
        }

        val threadIdByIndex = ArrayList<String>(nodes.size)
        val members = LinkedHashMap<String, MutableList<Int>>()
        for (i in nodes.indices) {
            val root = find(i)
            val tid = nodes[root].id
            threadIdByIndex.add(tid)
        }
        // Build membership in input order, then sort each thread by lifecycle stage.
        for (i in nodes.indices) {
            val tid = threadIdByIndex[i]
            members.getOrPut(tid) { mutableListOf() }.add(i)
        }
        for ((_, idxs) in members) {
            idxs.sortWith(compareBy({ nodes[it].signals.stage.order }, { it }))
        }
        return Threading(threadIdByIndex, members)
    }

    /** True only on a strong link (earlier = a, later = b; b may back-reference a). */
    private fun shouldLink(a: CorrelationSignals, b: CorrelationSignals): Boolean {
        if (strongKeyMatch(a, b) && withinWindow(a, b)) return true
        if (backReferenceMatch(earlier = a, later = b)) return true
        return false
    }

    private fun strongKeyMatch(a: CorrelationSignals, b: CorrelationSignals): Boolean {
        val card = a.card4 != null && a.card4 == b.card4
        val amt = a.amount != null && b.amount != null && abs(a.amount - b.amount) < 0.001
        val merch = a.canonicalMerchant != null && a.canonicalMerchant == b.canonicalMerchant
        return card && amt && merch
    }

    /**
     * The later message names an original txn date that equals the earlier
     * message's primary date, and they corroborate on a strong pair. Time window
     * is intentionally NOT applied here — the back-reference IS the explicit link.
     */
    private fun backReferenceMatch(earlier: CorrelationSignals, later: CorrelationSignals): Boolean {
        val ref = later.backRefDate ?: return false
        if (earlier.primaryDate == null || earlier.primaryDate != ref) return false
        val sameMerchant = earlier.canonicalMerchant != null && earlier.canonicalMerchant == later.canonicalMerchant
        val sameAmount = earlier.amount != null && later.amount != null && abs(earlier.amount - later.amount) < 0.001
        val sameCard = earlier.card4 != null && earlier.card4 == later.card4
        // Require at least a strong PAIR to corroborate the dated back-reference.
        return (sameMerchant && sameAmount) || (sameCard && sameAmount) || (sameCard && sameMerchant)
    }

    private fun withinWindow(a: CorrelationSignals, b: CorrelationSignals): Boolean {
        val ta = a.effectiveTime
        val tb = b.effectiveTime
        // No timestamp on either side -> order-only fallback; strong key still gates.
        if (ta == null || tb == null) return true
        val diff = abs(ta - tb)
        // When both resolve to the same in-body day (date-only), diff is 0 and passes.
        // With real receivedAt timestamps, require the configured window.
        return diff <= maxOf(windowMillis, SAME_DAY_TOLERANCE_FOR_DATE_ONLY)
    }

    private companion object {
        /**
         * Date-only effective times resolve to UTC midnight, so two same-day
         * messages differ by 0ms. This tolerance keeps the window check from
         * rejecting same-day date-only pairs even if the configured window is tiny.
         */
        const val SAME_DAY_TOLERANCE_FOR_DATE_ONLY = 0L
    }
}
