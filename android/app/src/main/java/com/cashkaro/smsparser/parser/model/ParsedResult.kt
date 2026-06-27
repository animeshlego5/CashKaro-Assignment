package com.cashkaro.smsparser.parser.model

/**
 * One parsed SMS result (docs/Functions.md schema). Serialised to JS as:
 * { rawSms, decision, excludeReason, transaction, confidence }.
 */
data class ParsedResult(
    val rawSms: String,
    val decision: Decision,
    /** Required when decision == EXCLUDE; null when INCLUDE. */
    val excludeReason: ExcludeReason?,
    /** Present only when decision == INCLUDE; null for EVERY EXCLUDE. */
    val transaction: Transaction?,
    /** 0.0..1.0 — confidence in the decision + extracted fields. */
    val confidence: Double,
) {
    companion object {
        /** The common EXCLUDE result; transaction is always null (the null contract). */
        fun excluded(rawSms: String, reason: ExcludeReason, confidence: Double): ParsedResult =
            ParsedResult(rawSms, Decision.EXCLUDE, reason, null, confidence)

        /** An INCLUDE result carrying a transaction. */
        fun included(rawSms: String, transaction: Transaction, confidence: Double): ParsedResult =
            ParsedResult(rawSms, Decision.INCLUDE, null, transaction, confidence)
    }
}
