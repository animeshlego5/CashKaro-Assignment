package com.cashkaro.smsparser.parser.pipeline

import com.cashkaro.smsparser.parser.MalformedGate
import com.cashkaro.smsparser.parser.NormalizedSms

/**
 * Default [MalformedGate] (orchestrator-owned, real). Flags input too short /
 * truncated / structurally insufficient to be a parseable transaction alert
 * (C8). It is intentionally STRUCTURAL (length/words/truncated-amount), not
 * vocabulary-based, so it generalises to hidden samples; genuine-but-ambiguous
 * messages are left for the downstream default-deny rather than mislabelled
 * MALFORMED here.
 */
class DefaultMalformedGate(
    private val minChars: Int = 15,
    private val minWords: Int = 4,
) : MalformedGate {

    // A currency token followed by a tiny, clearly-cut-off number at end of text,
    // e.g. "Spent Rs. 2,4" -> "rs. 2,4".
    private val trailingPartialAmount =
        Regex("""(rs\.?|inr|usd|eur|aed)\s*\d{1,3}(,\d{0,2})?\s*$""")

    override fun isMalformed(sms: NormalizedSms): Boolean {
        val t = sms.text
        if (t.isBlank()) return true
        if (t.length < minChars) return true
        if (t.split(' ').count { it.isNotBlank() } < minWords) return true
        if (trailingPartialAmount.containsMatchIn(sms.lower)) return true
        return false
    }
}
