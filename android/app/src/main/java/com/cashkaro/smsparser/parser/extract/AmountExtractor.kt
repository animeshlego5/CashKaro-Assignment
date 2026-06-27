package com.cashkaro.smsparser.parser.extract

import com.cashkaro.smsparser.parser.AmountExtractor
import com.cashkaro.smsparser.parser.NormalizedSms

/**
 * Default [AmountExtractor].
 *
 * Extracts the TRANSACTION amount from an SMS, deliberately rejecting numbers
 * that describe a balance, a credit limit, a foreign-currency markup, an EMI
 * instalment — or that are not money at all (OTP codes, reference/phone numbers,
 * dates). Generalised (C6), not keyed to the 25 sample bodies (C5):
 *
 *  1. Scan every Indian-format numeric literal (`1,250.00`, `1,45,300.00`,
 *     `50000`, `49.99`) and remember WHERE it sits.
 *  2. Drop a literal when the text just BEFORE it is balance / limit / markup /
 *     EMI language, or when it is immediately followed by `%` (a rate).
 *  3. Keep a literal only if it looks like MONEY: it is money-formatted (a comma
 *     group or a decimal part), OR a currency token sits immediately before it,
 *     OR spend/movement wording is nearby. A bare digit run with none of these
 *     (an OTP, a 12-digit reference, a phone number) is NOT a transaction amount.
 *  4. Among survivors, prefer the one nearest spend/debit/credit/refund wording;
 *     otherwise the first surviving money literal (the amount usually leads).
 *
 * Conservative (C2): returns null when no plausible transaction amount remains.
 * Pure Kotlin — no android.* imports.
 *
 * @param contextKeywords words that, just before a number, mark it as a
 *        NON-transaction figure (balance / limit / markup / instalment).
 * @param transactionKeywords spend/movement wording used to qualify + rank.
 */
class DefaultAmountExtractor(
    private val contextKeywords: List<String> = DEFAULT_CONTEXT_KEYWORDS,
    private val transactionKeywords: List<String> = DEFAULT_TRANSACTION_KEYWORDS,
) : AmountExtractor {

    /** A numeric literal found in the body, with where it begins/ends and its raw text. */
    private data class Candidate(val value: Double, val start: Int, val end: Int, val raw: String)

    override fun extract(sms: NormalizedSms): Double? {
        val text = sms.text
        val lower = sms.lower

        val candidates = NUMBER.findAll(text).mapNotNull { m ->
            val raw = m.value
            val value = raw.replace(",", "").toDoubleOrNull() ?: return@mapNotNull null
            Candidate(value, m.range.first, m.range.last + 1, raw)
        }.filter { c ->
            !isPercentage(text, c.end) && !hasBlockingContext(lower, c.start)
        }.toList()

        if (candidates.isEmpty()) return null

        // Keep only literals that actually look like money — money-formatted, OR
        // currency-anchored, OR near transaction wording. This rejects OTP codes,
        // reference/phone numbers and bare years that survived the filters above.
        val moneyLike = candidates.filter { c ->
            isMoneyFormatted(c.raw) ||
                isCurrencyAnchored(lower, c.start) ||
                distanceToNearestKeyword(lower, c, transactionKeywords) >= 0
        }
        if (moneyLike.isEmpty()) return null

        // Prefer the survivor closest to transaction wording, if any.
        val nearTxn = moneyLike
            .map { it to distanceToNearestKeyword(lower, it, transactionKeywords) }
            .filter { it.second >= 0 }
            .minByOrNull { it.second }
        if (nearTxn != null) return nearTxn.first.value

        // Otherwise the transaction amount almost always leads the body.
        return moneyLike.minByOrNull { it.start }!!.value
    }

    /** "Money-formatted" = carries a comma group or a decimal part (e.g. 1,250.00 / 49.99). */
    private fun isMoneyFormatted(raw: String): Boolean = raw.contains(',') || raw.contains('.')

    /** True if a currency token sits immediately before the number (word-bounded). */
    private fun isCurrencyAnchored(lower: String, start: Int): Boolean =
        CURRENCY_ANCHOR.containsMatchIn(lower.substring(0, start))

    /** A number directly trailed by `%` (allowing one optional space) is a rate. */
    private fun isPercentage(text: String, end: Int): Boolean {
        var i = end
        if (i < text.length && text[i] == ' ') i++
        return i < text.length && text[i] == '%'
    }

    /**
     * True if any blocking keyword (balance / limit / markup / EMI) ends within a
     * short window immediately before the number — e.g. "Avl Limit: INR 1,45,300".
     */
    private fun hasBlockingContext(lower: String, start: Int): Boolean {
        val windowStart = (start - CONTEXT_WINDOW).coerceAtLeast(0)
        val window = lower.substring(windowStart, start)
        return contextKeywords.any { window.contains(it) }
    }

    /**
     * Shortest distance (chars) from this candidate to any transaction keyword,
     * looking on BOTH sides; -1 if no keyword is within [TXN_WINDOW].
     */
    private fun distanceToNearestKeyword(lower: String, c: Candidate, keywords: List<String>): Int {
        var best = -1
        for (kw in keywords) {
            if (kw.isEmpty()) continue
            var from = 0
            while (true) {
                val idx = lower.indexOf(kw, from)
                if (idx < 0) break
                val kwEnd = idx + kw.length
                val gap = when {
                    kwEnd <= c.start -> c.start - kwEnd        // keyword before number
                    idx >= c.end -> idx - c.end                // keyword after number
                    else -> 0                                  // overlapping
                }
                if (gap <= TXN_WINDOW && (best < 0 || gap < best)) best = gap
                from = idx + 1
            }
        }
        return best
    }

    companion object {
        /** Indian-format number: digit run optionally split by commas, optional decimals. */
        private val NUMBER = Regex("""\d+(?:,\d+)*(?:\.\d+)?""")

        /**
         * A currency token immediately before the number (optional trailing spaces).
         * The leading boundary (`^` or non-alphanumeric) stops false hits like the
         * "rs" at the end of "hours". This is a generic money-detection heuristic;
         * the actual currency VALUE is resolved by the config-driven CurrencyExtractor.
         */
        private val CURRENCY_ANCHOR =
            Regex("""(?:^|[^a-z0-9])(?:inr|rs\.?|usd|us\$|eur|aed|₹|\$)\s*$""")

        private const val CONTEXT_WINDOW = 24
        private const val TXN_WINDOW = 40

        /** NON-transaction context markers (lowercased), matched just before a number. */
        val DEFAULT_CONTEXT_KEYWORDS: List<String> = listOf(
            "avl limit", "available limit", "avl lmt", "available credit limit", "credit limit",
            "avl bal", "available balance", "available bal", "avbl bal", "balance",
            "bal:", "limit:", "limit of", "markup", "markup of", "emi of", "interest",
        )

        /** Spend/movement wording used to qualify + rank surviving candidates. */
        val DEFAULT_TRANSACTION_KEYWORDS: List<String> = listOf(
            "spent", "spend", "debited", "credited", "refund", "received",
            "purchase", "txn", "transaction", "paid", "charged",
        )
    }
}
