package com.cashkaro.smsparser.parser

import com.cashkaro.smsparser.parser.model.ExcludeReason

/*
 * PHASE 1 STUB STAGES (orchestrator-owned, temporary).
 *
 * No-op implementations that let the full pipeline compile and return
 * schema-valid output before the Phase 2 agents implement the real components.
 * Each is replaced by a `Default*` implementation in Phase 2/3, after which this
 * file is deleted. Deliberately CONSERVATIVE: the classifier default-denies, so
 * the stubbed pipeline excludes everything (like the Phase 0 stub bridge) and
 * never produces a false-positive INCLUDE.
 */

internal class StubExclusionEngine : ExclusionEngine {
    override fun firstMatchingReason(sms: NormalizedSms): ExcludeReason? = null
}

internal class StubInclusionClassifier : InclusionClassifier {
    override fun classify(sms: NormalizedSms): InclusionDecision =
        InclusionDecision.Exclude(ExcludeReason.LOW_CONFIDENCE)
}

internal class StubAmountExtractor : AmountExtractor {
    override fun extract(sms: NormalizedSms): Double? = null
}

internal class StubCurrencyExtractor : CurrencyExtractor {
    override fun extract(sms: NormalizedSms): String? = null
}

internal class StubDateExtractor : DateExtractor {
    override fun extract(sms: NormalizedSms): String? = null
}

internal class StubCardExtractor : CardExtractor {
    override fun extract(sms: NormalizedSms): CardInfo = CardInfo(lastFour = null, cardType = CardType.UNKNOWN)
}

internal class StubMerchantExtractor : MerchantExtractor {
    override fun extract(sms: NormalizedSms): String? = null
}

internal class StubBankResolver : BankResolver {
    override fun resolve(sms: NormalizedSms): String? = null
}

/** Stub scorer: 0.1 for malformed (C8), a neutral-low value otherwise. Real banded model: Phase 3. */
internal class StubConfidenceScorer : ConfidenceScorer {
    override fun score(ctx: ScoringContext): Double = if (ctx.signals.malformed) 0.1 else 0.5
}
