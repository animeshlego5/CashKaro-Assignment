package com.cashkaro.smsparser.parser.session

import com.cashkaro.smsparser.parser.SmsParser
import com.cashkaro.smsparser.parser.config.ParserConfig
import com.cashkaro.smsparser.parser.extract.DefaultAmountExtractor
import com.cashkaro.smsparser.parser.extract.DefaultCardExtractor
import com.cashkaro.smsparser.parser.extract.DefaultDateExtractor
import com.cashkaro.smsparser.parser.model.Decision
import com.cashkaro.smsparser.parser.model.TxnType
import com.cashkaro.smsparser.parser.pipeline.DefaultNormalizer
import com.cashkaro.smsparser.parser.session.model.EnrichedResult
import com.cashkaro.smsparser.parser.session.model.MerchantSummary
import com.cashkaro.smsparser.parser.session.model.SessionResult
import com.cashkaro.smsparser.parser.session.model.SmsRecord
import com.cashkaro.smsparser.parser.session.model.Thread

/**
 * The ADDITIVE contextual engine (WS-2). Takes an ORDERED batch of [SmsRecord]s
 * and returns enriched results + cross-message rollups, WITHOUT ever changing the
 * underlying [com.cashkaro.smsparser.parser.model.ParsedResult] core fields
 * (V1/V6). It REUSES the stateless [SmsParser] for the per-message parse and
 * layers correlation + enrichment on top (V2 — all logic is Kotlin).
 *
 * Pipeline per session:
 *   1. parse each record.text via the stateless parser (core untouched);
 *   2. re-derive correlation signals from each raw body (so EXCLUDE messages —
 *      EMI conversion, bill, future-auto-debit — can still thread);
 *   3. canonicalise the merchant;
 *   4. thread on strong keys + explicit back-references (conservative, V4);
 *   5. flag recurring merchants (in-session repeat OR known subscription, plus
 *      future-auto-debit mandates as a recurring signal);
 *   6. assemble the §7 SessionResult (results stay 1:1 and in input order).
 *
 * Confidence and decisions are NEVER recalibrated (D4/D6).
 */
class ContextualEngine(
    private val parser: SmsParser,
    private val canonicalizer: MerchantCanonicalizer,
    private val correlation: CorrelationExtractor,
    private val threader: TransactionThreader,
    /** Categories whose merchants are treated as known subscriptions. */
    private val subscriptionCategories: Set<String>,
) {

    fun process(records: List<SmsRecord>): SessionResult {
        // 1+2+3 — parse, signals, canonical merchant per record.
        val nodes = ArrayList<TransactionThreader.Node>(records.size)
        val parsed = records.mapIndexed { index, record ->
            val core = parser.parse(record.text)
            val signals = correlation.extract(record.text, record.receivedAt)
            val id = index.toString()
            nodes.add(TransactionThreader.Node(id, signals))
            ParsedRow(id, index, record, core, signals)
        }

        // 4 — threading.
        val threading = threader.thread(nodes)

        // 5 — recurring detection (per canonical merchant, session-wide).
        val recurringMerchants = computeRecurring(parsed)

        // 6a — enriched results (1:1, input order).
        val results = parsed.map { row ->
            val canonical = row.signals.canonicalMerchant
            val tid = threading.threadIdByIndex[row.index]
            val linked = threading.membersByThread[tid]
                ?.filter { it != row.index }
                ?.map { it.toString() }
                ?.ifEmpty { null }
            EnrichedResult(
                core = row.core,
                id = row.id,
                receivedAt = row.record.receivedAt,
                threadId = tid,
                merchantCanonical = canonical,
                category = canonical?.let { categoryFor(row) },
                recurring = canonical != null && recurringMerchants.contains(canonical),
                linkedTo = linked,
            )
        }
        val byIndex = results.associateBy { it.id }

        // 6b — threads (ordered events; net amount = spend - refund within thread).
        val threads = threading.membersByThread.map { (tid, idxs) ->
            val events = idxs.mapNotNull { byIndex[it.toString()] }
            val anyCard = idxs.firstNotNullOfOrNull { parsed[it].signals.card4 }
            val anyMerchant = idxs.firstNotNullOfOrNull { parsed[it].signals.canonicalMerchant }
            Thread(
                threadId = tid,
                card4 = anyCard,
                merchantCanonical = anyMerchant,
                netAmount = netAmount(events),
                events = events,
            )
        }

        // 6c — merchant rollups.
        val merchants = computeMerchantSummaries(parsed, recurringMerchants)

        return SessionResult(results = results, threads = threads, merchants = merchants)
    }

    /** Net = INCLUDE DEBIT amounts minus INCLUDE REFUND amounts in the thread. */
    private fun netAmount(events: List<EnrichedResult>): Double {
        var net = 0.0
        for (e in events) {
            val t = e.core.transaction ?: continue
            if (e.core.decision != Decision.INCLUDE) continue
            when (t.type) {
                TxnType.DEBIT, TxnType.CREDIT -> net += t.amount
                TxnType.REFUND -> net -= t.amount
            }
        }
        return net
    }

    /**
     * A canonical merchant recurs when it appears in >=2 messages this session, OR
     * is a known subscription (config subscription flag / subscription category),
     * OR carries a future auto-debit mandate (#14) for that merchant.
     */
    private fun computeRecurring(rows: List<ParsedRow>): Set<String> {
        val counts = HashMap<String, Int>()
        val subscriptionHits = HashSet<String>()
        for (row in rows) {
            val canonical = row.signals.canonicalMerchant ?: continue
            counts[canonical] = (counts[canonical] ?: 0) + 1
            val match = canonicalizer.canonicalize(merchant = null, body = row.record.text)
            if (match != null) {
                val isKnownSub = match.subscription ||
                    (match.category != null && subscriptionCategories.contains(match.category))
                if (isKnownSub) subscriptionHits.add(canonical)
            }
            if (isFutureAutoDebit(row)) subscriptionHits.add(canonical)
        }
        val recurring = HashSet<String>()
        for ((merchant, count) in counts) if (count >= 2) recurring.add(merchant)
        recurring.addAll(subscriptionHits)
        return recurring
    }

    private fun isFutureAutoDebit(row: ParsedRow): Boolean {
        val lower = row.record.text.lowercase()
        return lower.contains("auto debit") || lower.contains("auto-debit") ||
            lower.contains("e-mandate") || lower.contains("emandate") || lower.contains("mandate")
    }

    private fun categoryFor(row: ParsedRow): String? =
        canonicalizer.canonicalize(merchant = null, body = row.record.text)?.category

    private fun computeMerchantSummaries(
        rows: List<ParsedRow>,
        recurring: Set<String>,
    ): List<MerchantSummary> {
        data class Acc(var category: String?, var count: Int, var spend: Double)
        val acc = LinkedHashMap<String, Acc>()
        for (row in rows) {
            val canonical = row.signals.canonicalMerchant ?: continue
            val match = canonicalizer.canonicalize(merchant = null, body = row.record.text)
            val a = acc.getOrPut(canonical) { Acc(match?.category, 0, 0.0) }
            if (a.category == null) a.category = match?.category
            a.count += 1
            val t = row.core.transaction
            if (t != null && row.core.decision == Decision.INCLUDE) {
                when (t.type) {
                    TxnType.DEBIT, TxnType.CREDIT -> a.spend += t.amount
                    TxnType.REFUND -> a.spend -= t.amount
                }
            }
        }
        return acc.map { (canonical, a) ->
            MerchantSummary(
                canonical = canonical,
                category = a.category,
                count = a.count,
                totalSpend = a.spend,
                recurring = recurring.contains(canonical),
            )
        }
    }

    private data class ParsedRow(
        val id: String,
        val index: Int,
        val record: SmsRecord,
        val core: com.cashkaro.smsparser.parser.model.ParsedResult,
        val signals: CorrelationSignals,
    )

    companion object {
        /**
         * Build a fully-wired engine from config + a parser. Reuses the same
         * stateless extractor implementations the parser uses so correlation
         * signals stay consistent with the parse.
         */
        fun create(config: ParserConfig, parser: SmsParser): ContextualEngine {
            val canonicalizer = MerchantCanonicalizer(config.merchantCategories)
            val correlation = CorrelationExtractor(
                normalizer = DefaultNormalizer(),
                amountExtractor = DefaultAmountExtractor(),
                cardExtractor = DefaultCardExtractor(),
                dateExtractor = DefaultDateExtractor(config.dateFormats),
                canonicalizer = canonicalizer,
            )
            val subscriptionCategories = config.merchantCategories
                .filter { it.subscription }
                .mapNotNull { it.category }
                .toMutableSet()
            // Also honour any subscription categories declared in config (entertainment, recharge).
            // (Already covered by per-merchant flags; categories add breadth conservatively.)
            return ContextualEngine(
                parser = parser,
                canonicalizer = canonicalizer,
                correlation = correlation,
                threader = TransactionThreader(config.threadWindowMinutes),
                subscriptionCategories = subscriptionCategories,
            )
        }
    }
}
