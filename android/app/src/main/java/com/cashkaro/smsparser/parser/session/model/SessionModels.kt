package com.cashkaro.smsparser.parser.session.model

import com.cashkaro.smsparser.parser.model.ParsedResult

/**
 * Session models for the ADDITIVE contextual engine (buildphase-v2.md §7).
 *
 * These are a SEPARATE schema from the frozen [ParsedResult] (V6). The contextual
 * engine never mutates the core five fields — on the Kotlin side it nests the
 * untouched [ParsedResult] under [EnrichedResult.core] and layers enrichment
 * alongside it. The bridge (WS-3) maps these to JS via a dedicated
 * SessionResultMapper which SPREADS the five core keys to the top level of each
 * enriched object (so the JS `EnrichedResult extends ParsedResult` per §7),
 * leaving ResultMapper's keys intact.
 */

/**
 * One input record for the session API.
 *
 * @param text the raw SMS body — fed verbatim through the existing stateless parser.
 * @param receivedAt epoch millis from the SMS inbox; NULL when unknown (e.g.
 *   imported files). Threading must degrade to the in-body date, then input order.
 *   Never required.
 * @param sender the SMS sender id (e.g. "VM-HDFCBK"); informational only.
 */
data class SmsRecord(
    val text: String,
    val receivedAt: Long? = null,
    val sender: String? = null,
)

/**
 * A [ParsedResult] (core fields byte-identical to parseSms) plus additive
 * enrichment. [core] is nested here on the Kotlin side and never modified
 * (V1/V6); the bridge mapper spreads its five keys to the top level for JS.
 */
data class EnrichedResult(
    /** The untouched parse — its five fields are exactly what parseSms returns. */
    val core: ParsedResult,
    /** Stable id within this session (input index), used by [linkedTo]/threads. */
    val id: String,
    /** Effective timestamp used for ordering/windowing, or null. */
    val receivedAt: Long?,
    /** The thread this result belongs to; every result has one (singletons too). */
    val threadId: String?,
    /** Canonical merchant (e.g. "Netflix"), or null when no token hits. */
    val merchantCanonical: String?,
    /** Category (e.g. "entertainment"), or null. */
    val category: String?,
    /** True when the merchant recurs in-session or is a known subscription. */
    val recurring: Boolean,
    /** Ids of the OTHER messages linked into this result's thread, or null. */
    val linkedTo: List<String>?,
)

/** A lifecycle thread: a primary transaction and its linked events. */
data class Thread(
    val threadId: String,
    val card4: String?,
    val merchantCanonical: String?,
    /** Spend (DEBIT) minus refunds (REFUND) within the thread, best-effort. */
    val netAmount: Double,
    /** Ordered auth/OTP -> spend -> refund/EMI/bill. */
    val events: List<EnrichedResult>,
)

/** Cross-message rollup for one canonical merchant. */
data class MerchantSummary(
    val canonical: String,
    val category: String?,
    val count: Int,
    val totalSpend: Double,
    val recurring: Boolean,
)

/** The full session output (§7). [results] stays 1:1 and in input order. */
data class SessionResult(
    val results: List<EnrichedResult>,
    val threads: List<Thread>,
    val merchants: List<MerchantSummary>,
)
