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
 * insurance / salary-credit messages have already been filtered out. This stage
 * therefore only answers: "given it survived exclusion, is this a real
 * credit-card transaction, and is it a spend, a refund, or a relevant credit?"
 *
 * Decision flow (DEFAULT-DENY, C2):
 *  1. No credit-card signal at all (per [CardSignal.hasCreditCardSignal]) =>
 *     Exclude(LOW_CONFIDENCE). A bare "card" with a non-card signal (a/c, upi,
 *     debit card) and no limit/credit-card language never reaches here as a
 *     credit-card txn — it falls through to the default deny.
 *  2. A credit-card signal IS present => it is a credit-card transaction. Type:
 *       * refund / reversal wording => REFUND.
 *       * a non-refund amount clearly credited TO the card (cashback / reward) =>
 *         CREDIT (distinct from an excluded bill-payment receipt, already removed
 *         as CARD_PAYMENT upstream).
 *       * otherwise (spend / purchase / debit on the card) => DEBIT.
 *
 * The type-discrimination vocabulary is generic English transaction wording, NOT
 * regexes keyed to the 25 sample bodies (C5/C6). Spend wording is matched at WORD
 * BOUNDARIES, so the noun "spends" (e.g. "cashback for May spends") does not
 * masquerade as the verb "spend" and wrongly veto a genuine CREDIT.
 */
class DefaultInclusionClassifier(
    private val cardSignal: CardSignal,
) : InclusionClassifier {

    override fun classify(sms: NormalizedSms): InclusionDecision {
        // (1) Default-deny: no credit-card signal => not confidently a CC txn.
        if (!cardSignal.hasCreditCardSignal(sms)) {
            return InclusionDecision.Exclude(ExcludeReason.LOW_CONFIDENCE)
        }

        val lower = sms.lower

        // (2a) Refund / reversal wins over plain credit/debit wording.
        if (containsAny(lower, REFUND_TOKENS)) {
            return InclusionDecision.Include(TxnType.REFUND)
        }

        // (2b) A relevant non-refund credit TO the card (cashback / reward).
        if (isRelevantCardCredit(lower)) {
            return InclusionDecision.Include(TxnType.CREDIT)
        }

        // (2c) Default for a confirmed credit-card txn: a spend / purchase.
        return InclusionDecision.Include(TxnType.DEBIT)
    }

    /**
     * A non-refund credit that should count as a [TxnType.CREDIT]: an amount is
     * credited TO the card with reward / cashback wording rather than a spend.
     * Guard: a genuine spend VERB (matched at word boundaries) vetoes CREDIT, but
     * the noun "spends" (as in "cashback for your spends") does NOT.
     */
    private fun isRelevantCardCredit(lower: String): Boolean {
        if (!containsAny(lower, CREDIT_VERB_TOKENS)) return false
        if (containsAnyWord(lower, SPEND_VERB_TOKENS)) return false
        return true
    }

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

        /**
         * Spend VERBS — matched at word boundaries so "spends"/"spending" (nouns)
         * do not veto a CREDIT, but "spent"/"spend"/"debited" (a real spend) do.
         */
        val SPEND_VERB_TOKENS: List<String> = listOf(
            "spent", "spend", "purchase", "purchased", "debited", "debit",
        )
    }
}
