package com.cashkaro.smsparser.parser.extract

import com.cashkaro.smsparser.parser.CurrencyExtractor
import com.cashkaro.smsparser.parser.NormalizedSms
import com.cashkaro.smsparser.parser.config.CurrencyDef

/**
 * Default [CurrencyExtractor].
 *
 * Detects the transaction currency purely from the tokens declared in
 * currencies.json (C5). It NEVER assumes INR (C7): if no configured token is
 * present, it returns null.
 *
 * Resolution rules:
 *  - A FOREIGN currency (anything other than INR) wins if any of its tokens
 *    appear — a foreign spend ("USD 49.99 ... INR equivalent will appear")
 *    should report USD, not the INR mentioned for the statement equivalent.
 *  - Otherwise the earliest-appearing token in the body decides the code, so the
 *    currency adjacent to the (leading) transaction amount is chosen.
 *
 * Token matching is longest-token-first within each currency so a specific token
 * like "us$" / "rs." is preferred over its shorter prefix ("$" / "rs") and an
 * embedded shorter token cannot mask a more specific one.
 *
 * Pure Kotlin — no android.* imports.
 *
 * @param currencies the configured currency definitions (already lowercased).
 */
class DefaultCurrencyExtractor(
    private val currencies: List<CurrencyDef>,
) : CurrencyExtractor {

    /** A token hit: which currency code it maps to, and where it first appears. */
    private data class Hit(val code: String, val index: Int, val isForeign: Boolean)

    override fun extract(sms: NormalizedSms): String? {
        val lower = sms.lower

        val hits = currencies.flatMap { def ->
            val foreign = !def.code.equals(INR, ignoreCase = true)
            // Longest token first so "us$" beats "$", "rs." beats "rs".
            def.tokens
                .filter { it.isNotEmpty() }
                .sortedByDescending { it.length }
                .mapNotNull { token ->
                    val idx = lower.indexOf(token)
                    if (idx < 0) null else Hit(def.code, idx, foreign)
                }
                // Keep only the earliest hit per currency.
                .minByOrNull { it.index }
                ?.let { listOf(it) } ?: emptyList()
        }

        if (hits.isEmpty()) return null

        // A foreign currency for the spend takes priority over INR.
        val foreignHit = hits.filter { it.isForeign }.minByOrNull { it.index }
        if (foreignHit != null) return foreignHit.code

        // Otherwise the earliest token in the body wins.
        return hits.minByOrNull { it.index }!!.code
    }

    companion object {
        private const val INR = "INR"
    }
}
