package com.cashkaro.smsparser.parser.classify

import com.cashkaro.smsparser.parser.NormalizedSms
import com.cashkaro.smsparser.parser.config.ExclusionRuleDef
import com.cashkaro.smsparser.parser.model.ExcludeReason

/**
 * A single compiled exclusion rule — a thin, pure wrapper around one
 * [ExclusionRuleDef] that knows how to test itself against a [NormalizedSms].
 *
 * Compiling once (mapping [ExclusionRuleDef.reason] to its [ExcludeReason] up
 * front, and snapshotting the empty/non-empty token lists) keeps the per-SMS hot
 * path allocation-free and makes the FROZEN qualifier semantics explicit in one
 * place. The matcher itself is still pure data: nothing about the 25 samples is
 * baked in here — only the rule's own `any` / `unless` tokens and flags drive it.
 *
 * FROZEN qualifier semantics (see [ExclusionRuleDef]) — a rule MATCHES when:
 *  - ANY token in [ExclusionRuleDef.any] is a substring of `sms.lower`, AND
 *  - NO token in [ExclusionRuleDef.unless] is a substring of `sms.lower`, AND
 *  - if [ExclusionRuleDef.withCard]: a credit-card signal IS present, AND
 *  - if [ExclusionRuleDef.notCreditCard]: a credit-card signal is NOT present.
 *
 * Conservative degenerate handling (C2): a rule whose `any` list is empty after
 * normalisation can never match (it would otherwise blanket-exclude everything),
 * so it is treated as a no-op rather than firing.
 */
internal class ExclusionRule(
    private val def: ExclusionRuleDef,
    private val cardSignal: CardSignal,
) {
    /** Pre-resolved reason for this rule (unknown/typo codes fall back safely). */
    val reason: ExcludeReason = ExcludeReason.fromCode(def.reason)

    // Compile each token once into a presence-test. Purely word-like tokens
    // (letters/digits/spaces) match at WORD BOUNDARIES, so e.g. "upi" does not
    // fire inside "Jupiter" and "sip" does not fire inside "gossip" (C6 hidden-
    // sample safety). Tokens containing punctuation ("a/c", "@ok", "/p2a/",
    // "% off", "t&c apply") keep plain substring matching, where word boundaries
    // do not apply cleanly. Blank tokens are dropped at config load; guarded too.
    private val anyMatchers: List<(String) -> Boolean> =
        def.any.filter { it.isNotEmpty() }.map { tokenMatcher(it) }
    private val unlessMatchers: List<(String) -> Boolean> =
        def.unless.filter { it.isNotEmpty() }.map { tokenMatcher(it) }
    private val withCard: Boolean = def.withCard
    private val notCreditCard: Boolean = def.notCreditCard

    /** True iff this rule fires for [sms] under the frozen qualifier semantics. */
    fun matches(sms: NormalizedSms): Boolean {
        // A rule with no positive triggers must never fire (would exclude all).
        if (anyMatchers.isEmpty()) return false

        val lower = sms.lower
        if (anyMatchers.none { it(lower) }) return false
        if (unlessMatchers.any { it(lower) }) return false

        // Credit-card-signal qualifiers are evaluated lazily — only the rules
        // that actually carry a flag pay the CardSignal lookup cost.
        if (withCard || notCreditCard) {
            val hasCard = cardSignal.hasCreditCardSignal(sms)
            if (withCard && !hasCard) return false
            if (notCreditCard && hasCard) return false
        }
        return true
    }

    private companion object {
        /** Build a presence-test for one token (word-boundary for word-like tokens). */
        fun tokenMatcher(token: String): (String) -> Boolean {
            val wordLike = token.all { it.isLetterOrDigit() || it == ' ' }
            return if (wordLike) {
                { haystack -> wordBoundaryContains(haystack, token) }
            } else {
                { haystack -> haystack.contains(token) }
            }
        }

        /** Substring search requiring non-alphanumeric boundaries on both sides. */
        fun wordBoundaryContains(haystack: String, token: String): Boolean {
            var from = 0
            while (true) {
                val idx = haystack.indexOf(token, from)
                if (idx < 0) return false
                val before = if (idx == 0) ' ' else haystack[idx - 1]
                val afterIdx = idx + token.length
                val after = if (afterIdx >= haystack.length) ' ' else haystack[afterIdx]
                if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
                from = idx + 1
            }
        }
    }
}
