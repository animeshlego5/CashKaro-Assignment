package com.cashkaro.smsparser.parser.session

import com.cashkaro.smsparser.parser.ResultMapper
import com.cashkaro.smsparser.parser.session.model.EnrichedResult
import com.cashkaro.smsparser.parser.session.model.MerchantSummary
import com.cashkaro.smsparser.parser.session.model.SessionResult
import com.cashkaro.smsparser.parser.session.model.Thread

/**
 * Pure (no android.*) mapping of a [SessionResult] to ordered Maps/Lists for the
 * ADDITIVE session API (buildphase-v2.md §7). This is the SEPARATE counterpart to
 * [ResultMapper] — it MUST NOT touch [ResultMapper]'s five frozen core keys.
 *
 * The shape produced here is a plain `Map<String, Any?>` tree (values are
 * String/Boolean/Int/Double/null, nested Map, or List). The Android bridge
 * transcribes it generically into WritableMap/WritableArray, so this object stays
 * Android-free and JVM-testable.
 *
 * Each [EnrichedResult] is the untouched core (the five frozen keys, produced by
 * the shared [ResultMapper.toMap]) SPREAD at the top level — so on the JS side
 * `EnrichedResult extends ParsedResult` exactly (§7) — followed by the additive
 * enrichment keys. The five frozen keys lead, byte-identical to parseSms (V1/V6);
 * [ResultMapper] itself is never touched.
 */
object SessionResultMapper {

    /**
     * The §7 EnrichedResult key set: the five frozen core keys (from
     * [ResultMapper.TOP_LEVEL_KEYS]) followed by the six additive enrichment keys.
     */
    val ENRICHED_KEYS = ResultMapper.TOP_LEVEL_KEYS + listOf(
        "id", "receivedAt", "threadId",
        "merchantCanonical", "category", "recurring", "linkedTo",
    )
    val THREAD_KEYS = listOf("threadId", "card4", "merchantCanonical", "netAmount", "events")
    val MERCHANT_KEYS = listOf("canonical", "category", "count", "totalSpend", "recurring")
    val SESSION_KEYS = listOf("results", "threads", "merchants")

    fun toMap(session: SessionResult): LinkedHashMap<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        map["results"] = session.results.map { enrichedToMap(it) }
        map["threads"] = session.threads.map { threadToMap(it) }
        map["merchants"] = session.merchants.map { merchantToMap(it) }
        return map
    }

    /**
     * One enriched result: the byte-identical core (5 frozen keys, via the shared
     * [ResultMapper]) embedded under `core`, plus the additive §7 enrichment keys.
     */
    fun enrichedToMap(r: EnrichedResult): LinkedHashMap<String, Any?> {
        // Spread the five frozen core keys (rawSms, decision, excludeReason,
        // transaction, confidence) at the top level, byte-identical to parseSms.
        val map = LinkedHashMap<String, Any?>(ResultMapper.toMap(r.core))
        map["id"] = r.id
        map["receivedAt"] = r.receivedAt
        map["threadId"] = r.threadId
        map["merchantCanonical"] = r.merchantCanonical
        map["category"] = r.category
        map["recurring"] = r.recurring
        map["linkedTo"] = r.linkedTo
        return map
    }

    private fun threadToMap(t: Thread): LinkedHashMap<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        map["threadId"] = t.threadId
        map["card4"] = t.card4
        map["merchantCanonical"] = t.merchantCanonical
        map["netAmount"] = t.netAmount
        map["events"] = t.events.map { enrichedToMap(it) }
        return map
    }

    private fun merchantToMap(m: MerchantSummary): LinkedHashMap<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        map["canonical"] = m.canonical
        map["category"] = m.category
        map["count"] = m.count
        map["totalSpend"] = m.totalSpend
        map["recurring"] = m.recurring
        return map
    }
}
