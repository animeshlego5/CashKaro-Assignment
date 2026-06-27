package com.cashkaro.smsparser.parser

import com.cashkaro.smsparser.parser.model.ExcludeReason
import com.cashkaro.smsparser.parser.model.ParsedResult
import com.cashkaro.smsparser.parser.model.Transaction
import com.cashkaro.smsparser.parser.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 1 exit: verbatim field-name snapshot. Locks the JSON keys to the
 * docs/Functions.md schema EXACTLY (and in order), guarding against silent drift
 * like cardLast4 / txnType.
 */
class FieldNameSnapshotTest {

    @Test
    fun includeResult_has_exact_top_level_and_transaction_keys() {
        val txn = Transaction(1250.0, "INR", "HDFC Bank", "5678", "SWIGGY", TxnType.DEBIT, "2026-04-03")
        val map = ResultMapper.toMap(ParsedResult.included("raw", txn, 0.95))

        assertEquals(
            listOf("rawSms", "decision", "excludeReason", "transaction", "confidence"),
            map.keys.toList(),
        )
        assertEquals(ResultMapper.TOP_LEVEL_KEYS, map.keys.toList())

        @Suppress("UNCHECKED_CAST")
        val tm = map["transaction"] as Map<String, Any?>
        assertEquals(
            listOf("amount", "currency", "bank", "cardLastFour", "merchant", "type", "date"),
            tm.keys.toList(),
        )
        assertEquals(ResultMapper.TRANSACTION_KEYS, tm.keys.toList())
        assertEquals("INCLUDE", map["decision"])
        assertNull(map["excludeReason"])
        assertEquals("DEBIT", tm["type"])
    }

    @Test
    fun excludeResult_has_null_transaction_and_string_reason() {
        val map = ResultMapper.toMap(ParsedResult.excluded("raw", ExcludeReason.OTP, 0.95))
        assertEquals(ResultMapper.TOP_LEVEL_KEYS, map.keys.toList())
        assertEquals("EXCLUDE", map["decision"])
        assertEquals("OTP", map["excludeReason"])
        assertNull("every EXCLUDE has a null transaction", map["transaction"])
    }
}
