package com.cashkaro.smsparser.parser.classify

import com.cashkaro.smsparser.parser.ExclusionEngine
import com.cashkaro.smsparser.parser.NormalizedSms
import com.cashkaro.smsparser.parser.config.ExclusionRuleDef
import com.cashkaro.smsparser.parser.model.ExcludeReason

/**
 * Stage 3 (C3): ordered, config-driven exclusion engine.
 *
 * Walks the injected [ExclusionRuleDef] list IN ORDER and returns the
 * [ExcludeReason] of the FIRST rule that matches the SMS, or `null` if none do
 * (the latter meaning "not an exclusion — let the InclusionClassifier decide").
 *
 * The engine itself contains NO knowledge of any specific bank, merchant, or
 * sample: every trigger token, guard, and flag comes from
 * exclusion-rules.json (C5). Adding/removing an exclusion category is a pure JSON
 * edit — no code change here. Each rule is compiled once into an [ExclusionRule]
 * so the per-SMS path is a simple ordered scan.
 *
 * Ordering matters and is owned by the config (C3 "first match wins"). The seed
 * config places every action-based exclusion (OTP, DECLINED, OFFER,
 * FUTURE_AUTO_DEBIT, EMI_CONVERSION, FEE_OR_CHARGE, CARD_PAYMENT, BILL_DUE,
 * INSURANCE, INVESTMENT, DEBIT_CARD, UPI_BANK_ACCOUNT, SALARY_CREDIT) and the
 * BALANCE_ALERT rule before the generic SAVINGS_ACCOUNT catch-all, and
 * UPI_BANK_ACCOUNT before BALANCE_ALERT, so that e.g. a UPI debit that also
 * quotes "Avl Bal" is classified by its action, not its balance line. The
 * BALANCE_ALERT rule additionally carries an `unless` guard so it never masks a
 * real spend/debit/credit. The engine simply honours whatever order it is given.
 */
class DefaultExclusionEngine(
    rules: List<ExclusionRuleDef>,
    cardSignal: CardSignal,
) : ExclusionEngine {

    /** Rules compiled once, preserving config order (first match wins). */
    private val compiled: List<ExclusionRule> = rules.map { ExclusionRule(it, cardSignal) }

    override fun firstMatchingReason(sms: NormalizedSms): ExcludeReason? {
        for (rule in compiled) {
            if (rule.matches(sms)) return rule.reason
        }
        return null
    }
}
