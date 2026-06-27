package com.cashkaro.smsparser.parser

import com.cashkaro.smsparser.parser.bank.DefaultBankResolver
import com.cashkaro.smsparser.parser.classify.CardSignal
import com.cashkaro.smsparser.parser.classify.DefaultExclusionEngine
import com.cashkaro.smsparser.parser.classify.DefaultInclusionClassifier
import com.cashkaro.smsparser.parser.confidence.DefaultConfidenceScorer
import com.cashkaro.smsparser.parser.config.ParserConfig
import com.cashkaro.smsparser.parser.extract.DefaultAmountExtractor
import com.cashkaro.smsparser.parser.extract.DefaultCardExtractor
import com.cashkaro.smsparser.parser.extract.DefaultCurrencyExtractor
import com.cashkaro.smsparser.parser.extract.DefaultDateExtractor
import com.cashkaro.smsparser.parser.extract.DefaultMerchantExtractor
import com.cashkaro.smsparser.parser.model.Decision
import com.cashkaro.smsparser.parser.model.ExcludeReason
import com.cashkaro.smsparser.parser.model.ParsedResult
import com.cashkaro.smsparser.parser.model.Transaction
import com.cashkaro.smsparser.parser.pipeline.DefaultMalformedGate
import com.cashkaro.smsparser.parser.pipeline.DefaultNormalizer

/**
 * Pure-Kotlin pipeline orchestrator (no android.*). Wires the six stages as
 * injected interfaces (see [Contracts.kt]) in the C3 order:
 *
 *   normalize -> malformed gate -> exclusion -> inclusion -> extract -> score
 *
 * Conservative throughout (C2): malformed and ambiguous inputs fail safe to
 * EXCLUDE; an INCLUDE candidate that lacks a usable amount/currency is downgraded
 * to EXCLUDE/LOW_CONFIDENCE rather than emitting a junk transaction. Never throws
 * (C8) — any unexpected error becomes a conservative MALFORMED exclude.
 */
class SmsParser(
    private val normalizer: Normalizer,
    private val malformedGate: MalformedGate,
    private val exclusionEngine: ExclusionEngine,
    private val inclusionClassifier: InclusionClassifier,
    private val amountExtractor: AmountExtractor,
    private val currencyExtractor: CurrencyExtractor,
    private val dateExtractor: DateExtractor,
    private val cardExtractor: CardExtractor,
    private val merchantExtractor: MerchantExtractor,
    private val bankResolver: BankResolver,
    private val confidenceScorer: ConfidenceScorer,
    private val cardSignal: CardSignal,
    /** Lowercased co-brand product tokens, for the co-brand confidence signal. */
    private val coBrandTokens: List<String> = emptyList(),
) {

    /** Parse one raw SMS into a schema-valid result. Never throws (C8). */
    fun parse(rawSms: String): ParsedResult =
        try {
            parseInternal(rawSms)
        } catch (t: Throwable) {
            ParsedResult.excluded(rawSms, ExcludeReason.MALFORMED_SMS, 0.1)
        }

    /** Parse a batch (any length — C6). */
    fun parseAll(rawList: List<String>): List<ParsedResult> = rawList.map { parse(it) }

    private fun parseInternal(rawSms: String): ParsedResult {
        val sms = normalizer.normalize(rawSms)

        // C8 — malformed fast-exit.
        if (malformedGate.isMalformed(sms)) {
            return finalize(rawSms, Decision.EXCLUDE, ExcludeReason.MALFORMED_SMS, null, Signals(malformed = true))
        }

        // C3 — exclusion-first.
        exclusionEngine.firstMatchingReason(sms)?.let { reason ->
            return finalize(rawSms, Decision.EXCLUDE, reason, null, signalsFor(sms, strongExclusion = true))
        }

        // Inclusion / type.
        return when (val decision = inclusionClassifier.classify(sms)) {
            is InclusionDecision.Exclude ->
                finalize(rawSms, Decision.EXCLUDE, decision.reason, null, signalsFor(sms))

            is InclusionDecision.Include -> {
                val amount = amountExtractor.extract(sms)
                val currency = currencyExtractor.extract(sms)
                // Conservative: an INCLUDE needs a usable amount + detected currency; else default-deny.
                if (amount == null || currency == null) {
                    return finalize(rawSms, Decision.EXCLUDE, ExcludeReason.LOW_CONFIDENCE, null, signalsFor(sms))
                }
                val card = cardExtractor.extract(sms)
                val txn = Transaction(
                    amount = amount,
                    currency = currency,
                    bank = bankResolver.resolve(sms),
                    cardLastFour = card.lastFour,
                    merchant = merchantExtractor.extract(sms),
                    type = decision.type,
                    date = dateExtractor.extract(sms),
                )
                finalize(rawSms, Decision.INCLUDE, null, txn, signalsFor(sms, txn = txn))
            }
        }
    }

    private fun finalize(
        rawSms: String,
        decision: Decision,
        reason: ExcludeReason?,
        txn: Transaction?,
        signals: Signals,
    ): ParsedResult {
        val confidence = confidenceScorer.score(ScoringContext(decision, reason, txn, signals))
        return ParsedResult(rawSms, decision, reason, txn, confidence)
    }

    /** Signal flags for the scorer, computed from the SMS + (for includes) the built transaction. */
    private fun signalsFor(sms: NormalizedSms, txn: Transaction? = null, strongExclusion: Boolean = false): Signals {
        val hasCardSignal = cardSignal.hasCreditCardSignal(sms)
        return Signals(
            malformed = false,
            explicitCreditCard = sms.lower.contains("credit card"),
            limitLanguage = hasCardSignal,
            coBranded = coBrandTokens.any { it.isNotEmpty() && sms.lower.contains(it) },
            bankResolved = txn?.bank != null,
            amountFound = txn != null,
            currencyFound = txn != null,
            dateFound = txn?.date != null,
            merchantFound = txn?.merchant != null,
            cardLastFourFound = txn?.cardLastFour != null,
            ambiguousCard = !hasCardSignal && sms.lower.contains("card") && !cardSignal.hasNonCardSignal(sms),
            strongExclusion = strongExclusion,
        )
    }

    companion object {
        /** Build a fully-wired parser from config (all real Phase 2/3 components). */
        fun create(config: ParserConfig): SmsParser {
            val cardSignal = CardSignal(config)
            return SmsParser(
                normalizer = DefaultNormalizer(),
                malformedGate = DefaultMalformedGate(),
                exclusionEngine = DefaultExclusionEngine(config.exclusionRules, cardSignal),
                inclusionClassifier = DefaultInclusionClassifier(cardSignal),
                amountExtractor = DefaultAmountExtractor(),
                currencyExtractor = DefaultCurrencyExtractor(config.currencies),
                dateExtractor = DefaultDateExtractor(config.dateFormats),
                cardExtractor = DefaultCardExtractor(),
                merchantExtractor = DefaultMerchantExtractor(config.merchant),
                bankResolver = DefaultBankResolver(config.banks, config.cardProducts),
                confidenceScorer = DefaultConfidenceScorer(),
                cardSignal = cardSignal,
                coBrandTokens = config.cardProducts.map { it.product.lowercase() },
            )
        }
    }
}
