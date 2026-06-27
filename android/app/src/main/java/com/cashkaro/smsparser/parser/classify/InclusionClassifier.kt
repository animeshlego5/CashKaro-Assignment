package com.cashkaro.smsparser.parser.classify

import com.cashkaro.smsparser.parser.InclusionClassifier
import com.cashkaro.smsparser.parser.InclusionDecision
import com.cashkaro.smsparser.parser.NormalizedSms
import com.cashkaro.smsparser.parser.model.ExcludeReason
import com.cashkaro.smsparser.parser.model.TxnType

/**
 * Stage 4 — decide whether a NON-excluded SMS is a credit-card transaction and,
 * if so, its [TxnType]. Runs AFTER the [com.cashkaro.smsparser.parser.ExclusionEngine],
 * so OTP / debit-card / savings / UPI-bank-account / balance-alert / bill-due /
 * offer / future-auto-debit / declined / EMI / fee / card-payment / investment /
 * insurance / salary-credit messages have already been filtered out.
 *
 * Decision flow (DEFAULT-DENY, C2):
 *  1. Refund / reversal: a refund that survived exclusion is NOT a debit-card /
 *     account / UPI refund (those were excluded), so a refund referencing a
 *     "card" with no non-card signal is a credit-card refund — even without an
 *     explicit "credit card"/limit phrase (e.g. "credited to your HDFC Card") =>
 *     REFUND.
 *  2. Otherwise a positive credit-card signal is REQUIRED (per [CardSignal]); no
 *     signal => Exclude(LOW_CONFIDENCE). A bare "Card" spend with only a non-card
 *     signal (a/c, upi, debit card) never includes — it falls through to deny.
 *  3. With a signal: a non-refund amount clearly credited TO the card (cashback /
 *     reward) => CREDIT; otherwise (spend / purchase) => DEBIT.
 *
 * Type-discrimination vocabulary is generic English transaction wording, NOT
 * regexes keyed to the 25 samples (C5/C6). Spend wording is matched at WORD
 * BOUNDARIES so the noun "spends" does not masquerade as the verb "spend".
 */
class DefaultInclusionClassifier(
    private val cardSignal: CardSignal,
) : InclusionClassifier {

    override fun classify(sms: NormalizedSms): InclusionDecision {
        val lower = sms.lower
        val hasCardSignal = cardSignal.hasCreditCardSignal(sms)

        // (1) Refund / reversal — see flow note above.
        if (containsAny(lower, REFUND_TOKENS)) {
            if (hasCardSignal || (mentionsCard(lower) && !cardSignal.hasNonCardSignal(sms))) {
                return InclusionDecision.Include(TxnType.REFUND)
            }
        }

        // (2) Spends / credits require a positive credit-card signal (default-deny).
        if (!hasCardSignal) {
            return InclusionDecision.Exclude(ExcludeReason.LOW_CONFIDENCE)
        }

        // (3) A relevant non-refund credit TO the card (cashback / reward).
        if (isRelevantCardCredit(lower)) {
            return InclusionDecision.Include(TxnType.CREDIT)
        }

        // (4) Default for a confirmed credit-card txn: a spend / purchase.
        return InclusionDecision.Include(TxnType.DEBIT)
    }

    /**
     * A non-refund credit counting as [TxnType.CREDIT]: an amount credited TO the
     * card with reward / cashback wording rather than a spend. A genuine spend
     * VERB (word-bounded) vetoes CREDIT, but the noun "spends" does not.
     */
    private fun isRelevantCardCredit(lower: String): Boolean {
        if (!containsAny(lower, CREDIT_VERB_TOKENS)) return false
        if (containsAnyWord(lower, SPEND_VERB_TOKENS)) return false
        return true
    }

    private fun mentionsCard(lower: String): Boolean = wordBoundaryContains(lower, "card")

    private fun containsAny(haystack: String, needles: List<String>): Boolean =
        needles.any { it.isNotEmpty() && haystack.contains(it) }

    /** Word-boundary variant: a needle counts only with non-alphanumeric edges. */
    private fun containsAnyWord(haystack: String, needles: List<String>): Boolean =
        needles.any { it.isNotEmpty() && wordBoundaryContains(haystack, it) }

    private fun wordBoundaryContains(haystack: String, token: String): Boolean {
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

    private companion object {
        /** Merchant refunds / reversals credited back to the card. */
        val REFUND_TOKENS: List<String> = listOf(
            "refund", "refunded", "reversal", "reversed", "charge back", "chargeback",
        )

        /** Verbs indicating money was credited TO the card (reward / cashback). */
        val CREDIT_VERB_TOKENS: List<String> = listOf(
            "cashback", "cash back", "reward", "credited to your", "credited to", "has been credited",
        )

        /** Spend VERBS — matched at word boundaries so "spends" (noun) does not veto CREDIT. */
        val SPEND_VERB_TOKENS: List<String> = listOf(
            "spent", "spend", "purchase", "purchased", "debited", "debit",
        )
    }
}
