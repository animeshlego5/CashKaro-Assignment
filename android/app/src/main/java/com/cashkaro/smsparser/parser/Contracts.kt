package com.cashkaro.smsparser.parser

import com.cashkaro.smsparser.parser.model.Decision
import com.cashkaro.smsparser.parser.model.ExcludeReason
import com.cashkaro.smsparser.parser.model.Transaction
import com.cashkaro.smsparser.parser.model.TxnType

/*
 * ============================================================================
 *  FROZEN PIPELINE CONTRACTS (Phase 1)
 *
 *  These interfaces + DTOs are the contract the Phase 2 component agents build
 *  against. DO NOT change a signature here without re-freezing centrally — the
 *  entire point is that agents 2A..2F implement in parallel against a stable
 *  shape with no cross-file coupling.
 *
 *  Pipeline order (see SmsParser):
 *    Normalizer -> MalformedGate -> ExclusionEngine -> InclusionClassifier
 *      -> extractors (Amount, Currency, Date, Card, Merchant, Bank)
 *      -> ConfidenceScorer
 *
 *  Ownership: Normalizer, MalformedGate, ConfidenceScorer, and the CardSignal
 *  helper are orchestrator-owned. The remaining stage IMPLEMENTATIONS are the
 *  Phase 2 fan-out (2A ExclusionEngine, 2B InclusionClassifier, 2C BankResolver,
 *  2D Amount/Currency, 2E Date, 2F Card/Merchant).
 * ============================================================================
 */

/** The cleaned form of an SMS, shared by every stage. */
data class NormalizedSms(
    /** Exactly as received. */
    val raw: String,
    /** Whitespace-collapsed + trimmed; ORIGINAL case preserved (for extraction). */
    val text: String,
    /** [text] lowercased — match config tokens (already lowercased) against this. */
    val lower: String,
)

/** Stage 1: clean / standardise the raw text. */
interface Normalizer {
    fun normalize(rawSms: String): NormalizedSms
}

/** Stage 2: C8 fast-exit for truncated / empty / insufficient input. */
interface MalformedGate {
    /** true => the orchestrator emits EXCLUDE / MALFORMED_SMS / null / ~0.1. */
    fun isMalformed(sms: NormalizedSms): Boolean
}

/** Stage 3 (C3): ordered, config-driven exclusion rules. */
interface ExclusionEngine {
    /** The FIRST matching exclusion reason, or null if no rule fires. */
    fun firstMatchingReason(sms: NormalizedSms): ExcludeReason?
}

/** Outcome of the inclusion stage: include with a type, or exclude with a reason. */
sealed interface InclusionDecision {
    data class Include(val type: TxnType) : InclusionDecision
    data class Exclude(val reason: ExcludeReason) : InclusionDecision
}

/**
 * Stage 4: decide whether a non-excluded SMS is a credit-card transaction and,
 * if so, its [TxnType]. Default-deny (C2): ambiguous-but-not-malformed with no
 * credit-card signal => Exclude(LOW_CONFIDENCE), never a confident Include.
 */
interface InclusionClassifier {
    fun classify(sms: NormalizedSms): InclusionDecision
}

/** Stage 5 — extractors (run only for an Include candidate). */

interface AmountExtractor {
    /** The TRANSACTION amount (not balance / limit / markup), or null. */
    fun extract(sms: NormalizedSms): Double?
}

interface CurrencyExtractor {
    /** Detected currency code (INR/USD/EUR/AED...), or null if undetectable (C7 — never assume). */
    fun extract(sms: NormalizedSms): String?
}

interface DateExtractor {
    /** ISO YYYY-MM-DD, or null when no date is present (never a guess). */
    fun extract(sms: NormalizedSms): String?
}

/** Card-type token detected in the body. Informational — the classifier relies on CardSignal. */
enum class CardType { CREDIT_CARD, DEBIT_CARD, BARE_CARD, ACCOUNT, UNKNOWN }

data class CardInfo(val lastFour: String?, val cardType: CardType)

interface CardExtractor {
    fun extract(sms: NormalizedSms): CardInfo
}

interface MerchantExtractor {
    /** Best-effort merchant, or null. Perfect extraction is NOT graded. */
    fun extract(sms: NormalizedSms): String?
}

/** Stage 5 (C4): resolve the ISSUER bank from the body; null if unknown (never guess). */
interface BankResolver {
    fun resolve(sms: NormalizedSms): String?
}

/** Signal flags the orchestrator computes from stage outputs and hands to the scorer. */
data class Signals(
    val malformed: Boolean = false,
    val explicitCreditCard: Boolean = false,
    val limitLanguage: Boolean = false,
    val coBranded: Boolean = false,
    val bankResolved: Boolean = false,
    val amountFound: Boolean = false,
    val currencyFound: Boolean = false,
    val dateFound: Boolean = false,
    val merchantFound: Boolean = false,
    val cardLastFourFound: Boolean = false,
    val ambiguousCard: Boolean = false,
    val strongExclusion: Boolean = false,
)

/** Everything the scorer needs to assign a 0.0..1.0 confidence. */
data class ScoringContext(
    val decision: Decision,
    val excludeReason: ExcludeReason?,
    val transaction: Transaction?,
    val signals: Signals,
)

/** Stage 6: confidence model (real implementation lands in Phase 3). */
interface ConfidenceScorer {
    fun score(ctx: ScoringContext): Double
}
