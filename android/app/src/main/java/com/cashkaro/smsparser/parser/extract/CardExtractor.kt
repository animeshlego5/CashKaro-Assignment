package com.cashkaro.smsparser.parser.extract

import com.cashkaro.smsparser.parser.CardExtractor
import com.cashkaro.smsparser.parser.CardInfo
import com.cashkaro.smsparser.parser.CardType
import com.cashkaro.smsparser.parser.NormalizedSms

/**
 * Extracts the masked card / account last-four digits and the card-type token
 * from an SMS body.
 *
 * **lastFour** — Indian bank SMS mask the instrument and print only the trailing
 * four digits behind a marker: `xx5678`, `XX9876`, `*4521`, `ending 1234`,
 * `ending in XX9907`, `Card no. XX9876`, `Credit Card XX8888`, `A/C *4521`.
 * We therefore capture exactly four digits that follow either a masking marker
 * (`x`/`X`/`*`) or the literal `ending [in]` keyword. Requiring such a marker is
 * deliberately conservative (C2): it ignores raw amounts, dates and 12-digit
 * reference numbers, which never sit behind a card mask. A group shorter than
 * four digits (e.g. `XX123`) does not match, so `lastFour` is left null rather
 * than reporting a wrong value.
 *
 * **cardType** — derived purely from the card token present in the body:
 * `credit card` => CREDIT_CARD, `debit card` => DEBIT_CARD,
 * `a/c`/`acct`/`account` => ACCOUNT, a bare `card` (no credit/debit) =>
 * BARE_CARD, otherwise UNKNOWN. This is informational; the classifier relies on
 * the shared CardSignal helper, not on this enum.
 *
 * Pure Kotlin, no config required (the marker grammar is universal across Indian
 * issuers); no `android.*` imports.
 */
class DefaultCardExtractor : CardExtractor {

    override fun extract(sms: NormalizedSms): CardInfo =
        CardInfo(lastFour = extractLastFour(sms.text), cardType = detectType(sms.lower))

    /** First masked / `ending` four-digit group, or null. */
    private fun extractLastFour(text: String): String? {
        val match = LAST_FOUR.find(text) ?: return null
        // Two alternation branches each have their own capture group; take whichever fired.
        return match.groupValues.drop(1).firstOrNull { it.length == 4 }
    }

    /** Most specific card token wins; never guesses. */
    private fun detectType(lower: String): CardType = when {
        lower.contains("credit card") -> CardType.CREDIT_CARD
        lower.contains("debit card") -> CardType.DEBIT_CARD
        ACCOUNT_TOKENS.any { lower.contains(it) } -> CardType.ACCOUNT
        // A bare "card" (no credit/debit qualifier) — word-boundary so "cardholder" etc. don't trip it.
        BARE_CARD.containsMatchIn(lower) -> CardType.BARE_CARD
        else -> CardType.UNKNOWN
    }

    private companion object {
        /**
         * Branch 1: an optional `ending [in]`, then a mask run of `x`/`X`/`*`,
         * then exactly four digits — covers `xx5678`, `*4521`, `ending in XX9907`,
         * `Card no. XX9876`. Branch 2: a bare `ending [in] 1234` with no mask.
         * `(?![0-9])` rejects a longer numeric run so reference numbers are skipped.
         */
        val LAST_FOUR = Regex(
            "(?:ending(?:\\s+in)?\\s+)?[x*]+\\s*([0-9]{4})(?![0-9])" +
                "|ending(?:\\s+in)?\\s+([0-9]{4})(?![0-9])",
            RegexOption.IGNORE_CASE,
        )

        val ACCOUNT_TOKENS = listOf("a/c", "acct", "account")

        /** Bare "card" as a standalone word. */
        val BARE_CARD = Regex("\\bcard\\b", RegexOption.IGNORE_CASE)
    }
}
