package com.cashkaro.smsparser.parser.pipeline

import com.cashkaro.smsparser.parser.NormalizedSms
import com.cashkaro.smsparser.parser.Normalizer

/**
 * Default [Normalizer] (orchestrator-owned, real even in Phase 1 — it is shared
 * infrastructure, not a Phase 2 component). Collapses runs of whitespace to a
 * single space and trims; preserves original case in [NormalizedSms.text] and
 * exposes a lowercased [NormalizedSms.lower] for case-insensitive matching.
 */
class DefaultNormalizer : Normalizer {
    private val whitespace = Regex("\\s+")

    override fun normalize(rawSms: String): NormalizedSms {
        val text = whitespace.replace(rawSms, " ").trim()
        return NormalizedSms(raw = rawSms, text = text, lower = text.lowercase())
    }
}
