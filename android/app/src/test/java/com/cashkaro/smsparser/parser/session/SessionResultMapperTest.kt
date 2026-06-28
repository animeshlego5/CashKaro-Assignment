package com.cashkaro.smsparser.parser.session

import com.cashkaro.smsparser.parser.ResultMapper
import com.cashkaro.smsparser.parser.SmsParser
import com.cashkaro.smsparser.parser.config.TestConfigSource
import com.cashkaro.smsparser.parser.session.model.SmsRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS-3 bridge/mapper proof (buildphase-v2.md §6/§7): the session map exposes the
 * §7 key set, and every embedded core result STILL carries EXACTLY the five frozen
 * keys (rawSms, decision, excludeReason, transaction, confidence) in order — the
 * additive session schema must never disturb the graded parseSms contract (V1/V6).
 */
class SessionResultMapperTest {

    private val config = TestConfigSource().load()
    private val parser = SmsParser.create(config)
    private val engine = ContextualEngine.create(config, parser)

    private val s2 = "INR 1,250.00 spent on HDFC Bank Credit Card xx5678 at SWIGGY on 03-04-2026. Avl Limit: INR 1,45,300.00."
    private val s21 = "Refund of Rs 450.00 has been credited to your HDFC Card xx5678 from BIGBASKET on 12-04-26 against original txn dated 02-04-26."
    private val otp = "123456 is your OTP for txn of Rs 500. Do not share it with anyone."

    private val FROZEN_CORE_KEYS = listOf("rawSms", "decision", "excludeReason", "transaction", "confidence")

    @Test
    fun sessionMap_hasExactTopLevelKeys() {
        val session = engine.process(listOf(SmsRecord(s2), SmsRecord(s21)))
        val map = SessionResultMapper.toMap(session)
        assertEquals(listOf("results", "threads", "merchants"), map.keys.toList())
        assertEquals(SessionResultMapper.SESSION_KEYS, map.keys.toList())
    }

    @Test
    fun enrichedResult_hasFrozenCoreKeysThenAdditiveEnrichment() {
        val session = engine.process(listOf(SmsRecord(s2), SmsRecord(s21), SmsRecord(otp)))
        val map = SessionResultMapper.toMap(session)

        @Suppress("UNCHECKED_CAST")
        val results = map["results"] as List<Map<String, Any?>>
        assertTrue("results must be 1:1 with input", results.size == 3)

        for (r in results) {
            val keys = r.keys.toList()
            // §7: EnrichedResult extends ParsedResult — the five frozen keys lead,
            // in EXACT order, then the six additive enrichment keys.
            assertEquals(
                "enriched result must start with the five frozen core keys, in order",
                FROZEN_CORE_KEYS,
                keys.take(5),
            )
            assertEquals(SessionResultMapper.ENRICHED_KEYS, keys)
            // The frozen core key list must be byte-identical to ResultMapper's.
            assertEquals(ResultMapper.TOP_LEVEL_KEYS, keys.take(5))

            // When a transaction is present it must carry the exact 7 frozen txn keys.
            val txn = r["transaction"]
            if (txn != null) {
                @Suppress("UNCHECKED_CAST")
                val tm = txn as Map<String, Any?>
                assertEquals(ResultMapper.TRANSACTION_KEYS, tm.keys.toList())
            }
        }
    }

    @Test
    fun threadAndMerchantMaps_haveExactKeySets() {
        val session = engine.process(listOf(SmsRecord(s2), SmsRecord(s21)))
        val map = SessionResultMapper.toMap(session)

        @Suppress("UNCHECKED_CAST")
        val threads = map["threads"] as List<Map<String, Any?>>
        for (t in threads) {
            assertEquals(SessionResultMapper.THREAD_KEYS, t.keys.toList())
            @Suppress("UNCHECKED_CAST")
            val events = t["events"] as List<Map<String, Any?>>
            for (e in events) {
                assertEquals(SessionResultMapper.ENRICHED_KEYS, e.keys.toList())
                assertEquals(ResultMapper.TOP_LEVEL_KEYS, e.keys.toList().take(5))
            }
        }

        @Suppress("UNCHECKED_CAST")
        val merchants = map["merchants"] as List<Map<String, Any?>>
        for (m in merchants) {
            assertEquals(SessionResultMapper.MERCHANT_KEYS, m.keys.toList())
        }
    }
}
