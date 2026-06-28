package com.cashkaro.smsparser.parser.session

import com.cashkaro.smsparser.parser.config.MerchantCategoryDef

/**
 * Config-driven merchant canonicaliser (WS-2, V2/V3). Maps a raw merchant string
 * and/or SMS body to a canonical merchant + category, first-match-wins.
 *
 * Conservative (V4): returns null when no configured token hits — never guesses.
 * Variants such as `NETFLIX.COM/US`, `NETFLIX-MONTHLY`, `NETFLIX_SUBSCRIPTION`
 * all collapse to "Netflix" because matching is substring-based over a normalised
 * (lowercased, punctuation->space) haystack, so `netflix` is found in every form.
 *
 * Token order matters within a single entry's [MerchantCategoryDef.tokens]; the
 * config author lists the most specific alias first (e.g. "swiggy instamart"
 * before "swiggy"). Across entries, the FIRST entry with any hit wins — config
 * order is the priority.
 *
 * Pure Kotlin, no android.* — fully JVM-testable.
 */
class MerchantCanonicalizer(private val entries: List<MerchantCategoryDef>) {

    /** Resolved canonical merchant + category + subscription flag, or null. */
    data class Match(val canonical: String, val category: String?, val subscription: Boolean)

    /**
     * Resolve from a raw merchant (preferred) falling back to the whole body.
     * Either argument may be null/blank; at least one should carry text.
     */
    fun canonicalize(merchant: String?, body: String?): Match? {
        // Prefer the extracted merchant; the body is a fallback so token hits inside
        // longer phrases (e.g. "NETFLIX_SUBSCRIPTION" in the body) still resolve.
        merchant?.let { m -> match(normalize(m))?.let { return it } }
        body?.let { b -> match(normalize(b))?.let { return it } }
        return null
    }

    private fun match(haystack: String): Match? {
        if (haystack.isBlank()) return null
        for (e in entries) {
            for (token in e.tokens) {
                if (token.isNotEmpty() && haystack.contains(token)) {
                    return Match(e.canonical, e.category, e.subscription)
                }
            }
        }
        return null
    }

    /**
     * Lowercase and replace non-alphanumeric runs with single spaces so that
     * `NETFLIX.COM/US`, `NETFLIX-MONTHLY`, `NETFLIX_SUBSCRIPTION` all expose the
     * bare `netflix` token. Multi-word config tokens (e.g. "big basket") still
     * match because their internal space survives normalisation.
     */
    private fun normalize(s: String): String =
        " " + s.lowercase().replace(NON_ALNUM, " ").trim().replace(MULTISPACE, " ") + " "

    private companion object {
        val NON_ALNUM = Regex("[^a-z0-9]+")
        val MULTISPACE = Regex("\\s+")
    }
}
