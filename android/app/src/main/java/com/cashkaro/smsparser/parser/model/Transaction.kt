package com.cashkaro.smsparser.parser.model

/**
 * A parsed credit-card transaction. Present ONLY when decision == INCLUDE
 * (transaction is null for every EXCLUDE result).
 *
 * Field rules (docs/Functions.md):
 * - amount: numeric.
 * - currency: detected from the SMS (never assumed) — INR, USD, EUR, AED, ...
 * - bank: resolved ISSUER bank (not the sender id); null if unresolved.
 * - cardLastFour: last four digits when available; else null.
 * - merchant: extracted as cleanly as possible; else null.
 * - type: DEBIT | CREDIT | REFUND.
 * - date: ISO YYYY-MM-DD when extractable; else null.
 */
data class Transaction(
    val amount: Double,
    val currency: String,
    val bank: String?,
    val cardLastFour: String?,
    val merchant: String?,
    val type: TxnType,
    val date: String?,
)
