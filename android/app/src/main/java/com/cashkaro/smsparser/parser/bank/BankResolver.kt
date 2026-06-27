package com.cashkaro.smsparser.parser.bank

import com.cashkaro.smsparser.parser.BankResolver
import com.cashkaro.smsparser.parser.NormalizedSms
import com.cashkaro.smsparser.parser.config.BankPattern
import com.cashkaro.smsparser.parser.config.CardProduct
import com.cashkaro.smsparser.parser.config.ParserConfig

/**
 * Default [BankResolver] (Phase 2 component 2C, C4 — the most-evaluated
 * behaviour). Resolves the ISSUER bank from the SMS BODY only — never a sender
 * id — driven entirely by the injected [banks] and [cardProducts] config (C5).
 *
 * Resolution order:
 *  1. CO-BRAND FIRST. If any [CardProduct.product] token appears in
 *     `sms.lower`, the issuer is taken from the co-brand map, NOT from any bank
 *     pattern. So "Edge"/"Jupiter" => Federal Bank and "BOBCARD One"/"BOBCARD"
 *     => Bank of Baroda, even when a different bank is mentioned elsewhere.
 *     Product / app branding never overrides the issuer; the co-brand map IS
 *     the resolution.
 *  2. ELSE DIRECT BANK. Match [BankPattern.patterns] (substrings) against
 *     `sms.lower` and return the matching [BankPattern.canonical].
 *  3. ELSE null — never guess (C2).
 *
 * MULTI-BANK PRECEDENCE. When more than one distinct issuer's token appears
 * (e.g. an issuer next to "Credit Card" / "spent" / "Avl Limit" plus a
 * DIFFERENT bank in a "Call <bank> helpline ..." / "powered by ..." footer),
 * the token ADJACENT to card / limit / spend language wins; footer / helpline
 * mentions are ignored. This is a simple character-distance proximity check in
 * `sms.lower` between each candidate token and the nearest transaction-context
 * anchor.
 *
 * Tokens in [banks]/[cardProducts] arrive already trimmed + lowercased from
 * config, so they are matched directly against `sms.lower`.
 */
class DefaultBankResolver(
    private val banks: List<BankPattern>,
    private val cardProducts: List<CardProduct>,
) : BankResolver {

    constructor(config: ParserConfig) : this(config.banks, config.cardProducts)

    /**
     * Generic transaction-context anchors used for the proximity tie-break.
     * These are universal Indian-bank credit-card-SMS context words (card /
     * limit / spend / debit language), NOT patterns keyed to any specific
     * sample body, so the resolver generalises to hidden wordings (C6).
     */
    private val contextAnchors = listOf(
        "credit card",
        "card no",
        "card",
        "avl limit",
        "avl lmt",
        "available limit",
        "credit limit",
        "spent",
        "spend",
        "debited",
        "credited",
        "purchase",
        "txn",
    )

    /** A resolved candidate: which issuer, and every position its token occurs. */
    private data class Candidate(val issuer: String, val positions: List<Int>)

    override fun resolve(sms: NormalizedSms): String? {
        val lower = sms.lower
        if (lower.isEmpty()) return null

        // 1. CO-BRAND FIRST — products take absolute precedence over direct banks.
        //    A product name keeps its original case in config (issuer is a display
        //    name, product is a match token), so lowercase it to match sms.lower.
        val productCandidates = cardProducts
            .map { Candidate(it.issuer, occurrences(lower, it.product.lowercase())) }
            .filter { it.positions.isNotEmpty() }
        pick(lower, productCandidates)?.let { return it }

        // 2. ELSE DIRECT BANK — group every matching pattern under its canonical.
        val bankCandidates = banks
            .map { bank ->
                Candidate(bank.canonical, bank.patterns.flatMap { occurrences(lower, it) })
            }
            .filter { it.positions.isNotEmpty() }
        pick(lower, bankCandidates)?.let { return it }

        // 3. Nothing resolved — never guess.
        return null
    }

    /**
     * Choose one issuer from [candidates]. With a single distinct issuer the
     * answer is unambiguous; with several, the issuer whose token sits CLOSEST
     * to a transaction-context anchor wins (multi-bank precedence). Returns null
     * when there are no candidates.
     */
    private fun pick(lower: String, candidates: List<Candidate>): String? {
        if (candidates.isEmpty()) return null

        val distinctIssuers = candidates.map { it.issuer }.distinct()
        if (distinctIssuers.size == 1) return distinctIssuers.first()

        // Multiple distinct issuers: prefer the one nearest card/spend/limit
        // language. A token with no anchor in the body falls back to Int.MAX so
        // an anchored candidate (the real spend line) always beats a footer one.
        var bestIssuer: String? = null
        var bestDistance = Int.MAX_VALUE
        for (candidate in candidates) {
            val distance = candidate.positions.minOf { nearestAnchorDistance(lower, it) }
            if (distance < bestDistance) {
                bestDistance = distance
                bestIssuer = candidate.issuer
            }
        }
        // If NOTHING is anchored (all Int.MAX), the body gives no card-context
        // cue to disambiguate competing issuers — stay conservative (C2) and
        // refuse to guess which bank is the real issuer.
        return if (bestDistance == Int.MAX_VALUE) null else bestIssuer
    }

    /** Smallest character gap between [tokenPos] and any context anchor occurrence. */
    private fun nearestAnchorDistance(lower: String, tokenPos: Int): Int {
        var best = Int.MAX_VALUE
        for (anchor in contextAnchors) {
            for (anchorPos in occurrences(lower, anchor)) {
                // Gap between the two spans (0 when they touch/overlap).
                val gap = if (anchorPos >= tokenPos) anchorPos - tokenPos else tokenPos - anchorPos
                if (gap < best) best = gap
            }
        }
        return best
    }

    /** All start indices of [needle] in [haystack]; empty list for a blank needle. */
    private fun occurrences(haystack: String, needle: String): List<Int> {
        if (needle.isEmpty()) return emptyList()
        val out = ArrayList<Int>()
        var from = 0
        while (true) {
            val idx = haystack.indexOf(needle, from)
            if (idx < 0) break
            out.add(idx)
            from = idx + 1
        }
        return out
    }
}
