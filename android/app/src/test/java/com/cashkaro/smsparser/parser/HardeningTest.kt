package com.cashkaro.smsparser.parser

import com.cashkaro.smsparser.parser.config.BankPattern
import com.cashkaro.smsparser.parser.config.TestConfigSource
import com.cashkaro.smsparser.parser.model.Decision
import com.cashkaro.smsparser.parser.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hardening tests beyond the required 8: config-extensibility (C5), conservative
 * bias (C2), the null-transaction contract for a NON-malformed exclude, and the
 * CREDIT type path. All drive the full [SmsParser].
 */
class HardeningTest {

    private val baseConfig = TestConfigSource().load()
    private val parser = SmsParser.create(baseConfig)

    private val acmeSms =
        "Rs 500.00 spent on Acme Bank Credit Card XX0001 at SHOP on 01-06-26. Avl Limit Rs 10,000."

    @Test
    fun config_extensibility_new_bank_via_data_only() {
        // Add a brand-new bank as pure DATA (no parser code change) — C5.
        val extended = baseConfig.copy(banks = baseConfig.banks + BankPattern("Acme Bank", listOf("acme")))
        val extendedParser = SmsParser.create(extended)

        val resolved = extendedParser.parse(acmeSms)
        assertEquals(Decision.INCLUDE, resolved.decision)
        assertEquals("Acme Bank", resolved.transaction?.bank)

        // The base parser doesn't know Acme Bank -> issuer null (still INCLUDE, just unresolved bank).
        assertNull("base config must not resolve Acme Bank", parser.parse(acmeSms).transaction?.bank)
    }

    @Test
    fun conservative_bias_bare_card_no_limit_is_low_confidence() {
        // Ambiguous bare "Card" — no limit/credit-card language, no account/UPI/debit
        // signal — must default-deny, never a confident INCLUDE (C2).
        val r = parser.parse("Rs 1,500.00 spent on your Card at SOME STORE on 10-06-26.")
        assertEquals(Decision.EXCLUDE, r.decision)
        assertEquals("LOW_CONFIDENCE", r.excludeReason?.name)
        assertNull(r.transaction)
        assertTrue("ambiguous deny must not be high-confidence", r.confidence < 0.75)
    }

    @Test
    fun null_transaction_contract_for_non_malformed_exclude() {
        // The null-transaction contract holds for EVERY exclude, not only MALFORMED_SMS.
        val r = parser.parse("Use 458219 as your OTP for HDFC Bank Net Banking login. Do NOT share with anyone.")
        assertEquals(Decision.EXCLUDE, r.decision)
        assertEquals("OTP", r.excludeReason?.name) // a non-malformed exclude
        assertNull(r.transaction)
    }

    @Test
    fun credit_type_path_reward_credited_to_card() {
        // A reward CREDITED to a credit card (not a spend, refund, or promotional
        // "cashback"/offer) exercises the CREDIT type.
        val r = parser.parse("Rs 350 reward credited to your HDFC Bank Credit Card xx5678 on 20-05-26. Avl Limit: Rs 95,000.")
        assertEquals(Decision.INCLUDE, r.decision)
        assertEquals(TxnType.CREDIT, r.transaction?.type)
        assertEquals("INR", r.transaction?.currency)
    }
}
