package com.cashkaro.smsparser.parser.classify

import com.cashkaro.smsparser.parser.NormalizedSms
import com.cashkaro.smsparser.parser.config.ParserConfig

/**
 * FROZEN shared helper (Phase 1). Detects a credit-card signal from the tokens
 * in products.json, and is consulted by BOTH the ExclusionEngine (to evaluate
 * the `withCard` / `notCreditCard` rule qualifiers) and the InclusionClassifier.
 * This is what breaks the otherwise-hidden 2A<->2B dependency.
 *
 * CRITICAL: tokens are matched as whole substrings, so "avl limit" /
 * "available limit" (a credit-card signal) is never collapsed to the bare
 * "avl " prefix and is kept distinct from "avl bal" / "available balance" (a
 * balance alert, which is deliberately NOT in creditCardSignals). Tokens arrive
 * already trimmed + lowercased from config.
 */
class CardSignal(
    private val creditCardSignals: List<String>,
    private val nonCardSignals: List<String>,
) {
    constructor(config: ParserConfig) : this(config.creditCardSignals, config.nonCardSignals)

    /** True if any credit-card signal token appears in the SMS. */
    fun hasCreditCardSignal(sms: NormalizedSms): Boolean =
        creditCardSignals.any { it.isNotEmpty() && sms.lower.contains(it) }

    /** True if any non-card (debit / account / UPI) signal token appears. */
    fun hasNonCardSignal(sms: NormalizedSms): Boolean =
        nonCardSignals.any { it.isNotEmpty() && sms.lower.contains(it) }
}
