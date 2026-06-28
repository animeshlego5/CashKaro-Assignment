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
    /**
     * Merchant canonicalisation / category seed (merchant-categories.json) — used
     * ONLY by the additive contextual engine (parser/session), never by the frozen
     * parseSms pipeline. Empty when the file is absent (graceful degrade).
     */
    val merchantCategories: List<MerchantCategoryDef> = emptyList(),
    /**
     * Contextual-engine threading window in minutes (default 15). Used only when
     * `receivedAt` timestamps are present; when only in-body dates exist the
     * threader falls back to same-day. Config-driven (V3).
     */
    val threadWindowMinutes: Int = 15,
)

/**
 * One canonical-merchant entry (merchant-categories.json). [tokens] are lowercased
 * aliases; the FIRST entry whose token is found in the (lowercased) merchant/body
 * text wins. [subscription] marks a known recurring service (Netflix, Prime, ...).
 * Used only by the contextual engine (V3) — never bends the frozen result schema.
 */
data class MerchantCategoryDef(
    val canonical: String,
    val category: String?,
    val subscription: Boolean,
    val tokens: List<String>,
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
