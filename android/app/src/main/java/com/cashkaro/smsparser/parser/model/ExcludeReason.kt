package com.cashkaro.smsparser.parser.model

/**
 * Complete, FROZEN set of exclusion reason codes.
 *
 * The codes mirror docs/Functions.md; SALARY_CREDIT is a custom code
 * (Functions.md lists salary credits as a category but defines no code).
 * LOW_CONFIDENCE is the ambiguous default-deny fallback; MALFORMED_SMS is the
 * C8 fail-safe.
 *
 * The pipeline carries this enum internally; [ParsedResult] serialises it to the
 * schema's string `excludeReason` via its `.name`. Config (exclusion-rules.json)
 * names reasons by these enum names; the engine maps a config string back via
 * [fromCode], which falls back to LOW_CONFIDENCE so an unknown/typo'd code never
 * crashes the parser (a unit test asserts every seed rule maps to a real code).
 */
enum class ExcludeReason {
    OTP,
    DEBIT_CARD,
    SAVINGS_ACCOUNT,
    UPI_BANK_ACCOUNT,
    BALANCE_ALERT,
    BILL_DUE,
    OFFER,
    FUTURE_AUTO_DEBIT,
    DECLINED,
    EMI_CONVERSION,
    FEE_OR_CHARGE,
    CARD_PAYMENT,
    INVESTMENT,
    INSURANCE,
    SALARY_CREDIT,
    MALFORMED_SMS,
    LOW_CONFIDENCE;

    companion object {
        /** Safe lookup by code name; unknown/blank -> LOW_CONFIDENCE (never throws). */
        fun fromCode(code: String?): ExcludeReason {
            val needle = code?.trim().orEmpty()
            return entries.firstOrNull { it.name.equals(needle, ignoreCase = true) }
                ?: LOW_CONFIDENCE
        }
    }
}
