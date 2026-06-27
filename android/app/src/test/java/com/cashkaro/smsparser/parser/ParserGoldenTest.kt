package com.cashkaro.smsparser.parser

import com.cashkaro.smsparser.parser.config.TestConfigSource
import com.cashkaro.smsparser.parser.model.Decision
import com.cashkaro.smsparser.parser.model.TxnType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Golden-set test (Phase 3): run all 25 sample texts (src/test/resources/samples.json,
 * mirrored from src/data) through the fully-wired [SmsParser] and assert against
 * the independently-derived oracle (src/test/resources/oracle.json):
 *  - decision + excludeReason (accepted-set for the ambiguous sample 1),
 *  - key transaction fields (type / bank / amount / currency / cardLastFour / date),
 *  - confidence as a [min, max] BAND (never exact),
 *  - transaction == null for EVERY exclude,
 *  - the aggregate 7/18 split and INR debit / credit-refund totals.
 *
 * Merchant is intentionally NOT strictly asserted — perfect merchant extraction
 * is explicitly not graded.
 */
class ParserGoldenTest {

    private val parser = SmsParser.create(TestConfigSource().load())
    private val gson = Gson()
    private val oracle: Oracle = gson.fromJson(resource("oracle.json"), Oracle::class.java)
    private val texts: Map<Int, String> = gson
        .fromJson<List<SampleInput>>(resource("samples.json"), object : TypeToken<List<SampleInput>>() {}.type)
        .associate { it.id to it.text }

    private fun resource(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().use { it.readText() }

    @Test
    fun golden_set_matches_oracle() {
        val failures = mutableListOf<String>()
        for (o in oracle.samples) {
            val text = texts[o.id] ?: error("no sample text for id ${o.id}")
            val r = parser.parse(text)

            if (r.decision.name != o.decision) {
                failures += "#${o.id}: decision ${r.decision} != ${o.decision}"
                continue
            }

            if (o.decision == "EXCLUDE") {
                if (r.transaction != null) failures += "#${o.id}: EXCLUDE must have null transaction"
                val actual = r.excludeReason?.name
                val ok = when {
                    o.excludeReasonAnyOf != null -> actual in o.excludeReasonAnyOf
                    o.excludeReason != null -> actual == o.excludeReason
                    else -> true
                }
                if (!ok) failures += "#${o.id}: reason $actual not in ${o.excludeReason ?: o.excludeReasonAnyOf}"
            } else {
                val t = r.transaction
                if (t == null) {
                    failures += "#${o.id}: INCLUDE must have a transaction"
                } else {
                    if (o.type != null && t.type.name != o.type) failures += "#${o.id}: type ${t.type} != ${o.type}"
                    val bankOk = when {
                        o.bankAnyOf != null -> t.bank in o.bankAnyOf
                        o.bank != null -> t.bank == o.bank
                        else -> true
                    }
                    if (!bankOk) failures += "#${o.id}: bank ${t.bank} not in ${o.bank ?: o.bankAnyOf}"
                    if (o.amount != null && abs(t.amount - o.amount) > 0.001) failures += "#${o.id}: amount ${t.amount} != ${o.amount}"
                    if (o.currency != null && t.currency != o.currency) failures += "#${o.id}: currency ${t.currency} != ${o.currency}"
                    if (o.cardLastFour != null && t.cardLastFour != o.cardLastFour) failures += "#${o.id}: cardLastFour ${t.cardLastFour} != ${o.cardLastFour}"
                    if (o.date != null && t.date != o.date) failures += "#${o.id}: date ${t.date} != ${o.date}"
                }
            }

            if (r.confidence < o.confidenceMin - EPS || r.confidence > o.confidenceMax + EPS) {
                failures += "#${o.id}: confidence ${r.confidence} not in [${o.confidenceMin}, ${o.confidenceMax}]"
            }
        }
        assertTrue("Golden mismatches:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun every_exclude_has_null_transaction() {
        for (o in oracle.samples) {
            val r = parser.parse(texts[o.id]!!)
            if (r.decision == Decision.EXCLUDE) assertNull("#${o.id} EXCLUDE transaction", r.transaction)
        }
    }

    @Test
    fun aggregate_summary_matches_spec() {
        val results = oracle.samples.map { parser.parse(texts[it.id]!!) }
        assertEquals("included count", oracle.aggregate.included, results.count { it.decision == Decision.INCLUDE })
        assertEquals("excluded count", oracle.aggregate.excluded, results.count { it.decision == Decision.EXCLUDE })

        val inrDebit = results.filter {
            it.decision == Decision.INCLUDE && it.transaction!!.currency == "INR" && it.transaction!!.type == TxnType.DEBIT
        }.sumOf { it.transaction!!.amount }
        val inrCreditRefund = results.filter {
            it.decision == Decision.INCLUDE && it.transaction!!.currency == "INR" &&
                (it.transaction!!.type == TxnType.REFUND || it.transaction!!.type == TxnType.CREDIT)
        }.sumOf { it.transaction!!.amount }

        assertEquals("INR debit total", oracle.aggregate.inrDebitTotal, inrDebit, 0.01)
        assertEquals("INR credit/refund total", oracle.aggregate.inrCreditRefundTotal, inrCreditRefund, 0.01)
    }

    private data class Oracle(val aggregate: Aggregate, val samples: List<OracleSample>)
    private data class Aggregate(
        val included: Int,
        val excluded: Int,
        val inrDebitTotal: Double,
        val inrCreditRefundTotal: Double,
    )
    private data class OracleSample(
        val id: Int,
        val decision: String,
        val excludeReason: String?,
        val excludeReasonAnyOf: List<String>?,
        val type: String?,
        val bank: String?,
        val bankAnyOf: List<String>?,
        val amount: Double?,
        val currency: String?,
        val cardLastFour: String?,
        val merchant: String?,
        val date: String?,
        val confidenceMin: Double,
        val confidenceMax: Double,
    )
    private data class SampleInput(val id: Int, val text: String)

    private companion object {
        const val EPS = 1e-9
    }
}
