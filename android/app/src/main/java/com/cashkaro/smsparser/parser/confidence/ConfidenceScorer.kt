package com.cashkaro.smsparser.parser.confidence

import com.cashkaro.smsparser.parser.ConfidenceScorer
import com.cashkaro.smsparser.parser.ScoringContext
import com.cashkaro.smsparser.parser.Signals
import com.cashkaro.smsparser.parser.model.Decision
import com.cashkaro.smsparser.parser.model.ExcludeReason

/**
 * Default confidence model (Phase 3). Maps a [ScoringContext] to a 0.0..1.0
 * score for how sure the parser is about the decision + extracted fields.
 *
 * Documented bands (see README §3):
 *  - Clear credit-card spend (explicit "Credit Card" + fields): ~0.90-0.97.
 *  - Credit-card signal via limit-language on a bare "Card":    ~0.85-0.93.
 *  - Co-brand resolved via body (Jupiter/BOBCARD):              ~0.85-0.90 (small penalty).
 *  - Refund/credit to a card with no explicit signal:           ~0.83-0.88.
 *  - Clear keyword-strong exclusion (OTP/debit/UPI/...):         ~0.93.
 *  - Softer/generic exclusion (savings fallback, EMI):          ~0.78-0.86.
 *  - Ambiguous default-deny (LOW_CONFIDENCE):                    ~0.60.
 *  - Malformed:                                                  ~0.10 (C8).
 *
 * Field completeness adds small bonuses; an unresolved co-brand issuer subtracts
 * a small penalty (it is the least-certain resolution). Pure Kotlin.
 */
class DefaultConfidenceScorer : ConfidenceScorer {

    override fun score(ctx: ScoringContext): Double {
        if (ctx.signals.malformed) return MALFORMED // C8 fail-safe
        val raw = when (ctx.decision) {
            Decision.INCLUDE -> scoreInclude(ctx.signals)
            Decision.EXCLUDE -> scoreExclude(ctx.excludeReason)
        }
        return raw.coerceIn(0.0, 1.0)
    }

    private fun scoreInclude(s: Signals): Double {
        var c = INCLUDE_BASE
        c += when {
            s.explicitCreditCard -> EXPLICIT_CARD_BONUS // literal "Credit Card"
            s.limitLanguage -> LIMIT_LANGUAGE_BONUS     // avl-limit / markup signal on a bare "Card"
            else -> 0.0
        }
        if (s.bankResolved) c += BANK_BONUS
        if (s.dateFound) c += DATE_BONUS
        if (s.merchantFound) c += MERCHANT_BONUS
        if (s.cardLastFourFound) c += CARD_BONUS
        if (s.coBranded) c -= COBRAND_PENALTY
        return c.coerceAtMost(INCLUDE_CAP)
    }

    private fun scoreExclude(reason: ExcludeReason?): Double = when (reason) {
        ExcludeReason.MALFORMED_SMS -> MALFORMED
        ExcludeReason.LOW_CONFIDENCE, null -> AMBIGUOUS_EXCLUDE
        ExcludeReason.SAVINGS_ACCOUNT -> 0.78 // generic account catch-all — a touch less certain
        ExcludeReason.EMI_CONVERSION -> 0.86
        ExcludeReason.SALARY_CREDIT,
        ExcludeReason.FUTURE_AUTO_DEBIT,
        ExcludeReason.INSURANCE -> 0.90
        ExcludeReason.INVESTMENT -> 0.92
        else -> STRONG_EXCLUDE // OTP, DEBIT_CARD, UPI, BALANCE_ALERT, BILL_DUE, OFFER, DECLINED, FEE_OR_CHARGE, CARD_PAYMENT
    }

    private companion object {
        const val MALFORMED = 0.10
        const val AMBIGUOUS_EXCLUDE = 0.60
        const val STRONG_EXCLUDE = 0.93

        const val INCLUDE_BASE = 0.77
        const val EXPLICIT_CARD_BONUS = 0.14
        const val LIMIT_LANGUAGE_BONUS = 0.08
        const val BANK_BONUS = 0.03
        const val DATE_BONUS = 0.02
        const val MERCHANT_BONUS = 0.02
        const val CARD_BONUS = 0.02
        const val COBRAND_PENALTY = 0.08
        const val INCLUDE_CAP = 0.97
    }
}
