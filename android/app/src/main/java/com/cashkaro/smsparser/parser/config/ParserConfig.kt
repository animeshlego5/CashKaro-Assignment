package com.cashkaro.smsparser.parser.config

/**
 * Aggregated, immutable parser configuration — loaded ONCE via a [ConfigSource].
 *
 * Adding a bank / rule / currency is a JSON edit only (C5): the parser logic
 * reads these lists and never hard-codes patterns. All string TOKENS in here are
 * normalised to lowercase at load time, so components match them against
 * `NormalizedSms.lower`. Human-facing names (BankPattern.canonical,
 * CardProduct.*) keep their original case.
 */
data class ParserConfig(
    val banks: List<BankPattern>,
    val cardProducts: List<CardProduct>,
    /** Credit-card signal tokens (e.g. "credit card", "avl limit") — products.json. */
    val creditCardSignals: List<String>,
    /** Non-card signal tokens (e.g. "debit card", "a/c", "upi") — products.json. */
    val nonCardSignals: List<String>,
    val exclusionRules: List<ExclusionRuleDef>,
    val currencies: List<CurrencyDef>,
    val merchant: MerchantConfig,
    val dateFormats: List<String>,
)

/** A direct issuer bank and the body substrings that identify it (banks.json). */
data class BankPattern(val canonical: String, val patterns: List<String>)

/** A co-brand / fintech product name -> issuer mapping (card-products.json). */
data class CardProduct(val product: String, val issuer: String)

/**
 * One ordered exclusion rule (exclusion-rules.json).
 *
 * FROZEN qualifier semantics — a rule MATCHES when:
 *   - ANY token in [any] is present in the SMS, AND
 *   - NO token in [unless] is present, AND
 *   - both flags hold: if [withCard] a credit-card signal must be present (via
 *     CardSignal); if [notCreditCard] NO credit-card signal may be present.
 * Rules are evaluated in order; the FIRST match wins (C3 exclusion-first).
 * [reason] names an [com.cashkaro.smsparser.parser.model.ExcludeReason].
 */
data class ExclusionRuleDef(
    val reason: String,
    val any: List<String>,
    val unless: List<String>,
    val withCard: Boolean,
    val notCreditCard: Boolean,
)

/** A currency code and the case-insensitive tokens that indicate it (currencies.json). */
data class CurrencyDef(val code: String, val tokens: List<String>)

/** Merchant extraction hints (merchants.json). Perfect extraction is NOT graded. */
data class MerchantConfig(
    val atPrepositions: List<String>,
    val stripSuffixes: List<String>,
    val stripCity: Boolean,
)
