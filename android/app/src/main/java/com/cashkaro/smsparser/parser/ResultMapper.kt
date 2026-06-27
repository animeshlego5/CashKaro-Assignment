package com.cashkaro.smsparser.parser

import com.cashkaro.smsparser.parser.model.ParsedResult

/**
 * Pure (no android.*) mapping of a [ParsedResult] to an ordered Map with EXACTLY
 * the docs/Functions.md schema keys. This is the single source of truth for the
 * JSON field names: the Android bridge transcribes this Map into a WritableMap
 * generically, and a JVM test asserts the key set + order here (guarding against
 * silent drift like cardLast4 / txnType).
 */
object ResultMapper {

    val TOP_LEVEL_KEYS = listOf("rawSms", "decision", "excludeReason", "transaction", "confidence")
    val TRANSACTION_KEYS = listOf("amount", "currency", "bank", "cardLastFour", "merchant", "type", "date")

    fun toMap(r: ParsedResult): LinkedHashMap<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        map["rawSms"] = r.rawSms
        map["decision"] = r.decision.name
        map["excludeReason"] = r.excludeReason?.name
        map["transaction"] = r.transaction?.let { t ->
            val tm = LinkedHashMap<String, Any?>()
            tm["amount"] = t.amount
            tm["currency"] = t.currency
            tm["bank"] = t.bank
            tm["cardLastFour"] = t.cardLastFour
            tm["merchant"] = t.merchant
            tm["type"] = t.type.name
            tm["date"] = t.date
            tm
        }
        map["confidence"] = r.confidence
        return map
    }
}
