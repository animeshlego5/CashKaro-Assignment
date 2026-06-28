package com.cashkaro.smsparser

import com.cashkaro.smsparser.parser.ResultMapper
import com.cashkaro.smsparser.parser.SmsParser
import com.cashkaro.smsparser.parser.session.ContextualEngine
import com.cashkaro.smsparser.parser.session.SessionResultMapper
import com.cashkaro.smsparser.parser.session.model.SmsRecord
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap

/**
 * React Native bridge for the SMS parser.
 *
 * Together with [AssetConfigSource], this is the ONLY Android-aware layer: it
 * loads config from assets, runs the pure-Kotlin [SmsParser], and converts the
 * results to a WritableArray for JS. ALL parsing/classification/scoring lives in
 * the pure core (C1) — this class does no parsing of its own.
 *
 * The parser is built lazily once (config load + stage wiring) and reused.
 * Result shape (docs/Functions.md): rawSms, decision, excludeReason,
 * transaction, confidence — produced by the shared [ResultMapper].
 */
class SmsParserModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = NAME

    // Config + parser are built lazily once and reused. The session engine (WS-3)
    // reuses the SAME config + parser — the graded parseSms path is untouched.
    private val config by lazy { AssetConfigSource(reactApplicationContext.assets).load() }
    private val parser: SmsParser by lazy { SmsParser.create(config) }
    private val sessionEngine: ContextualEngine by lazy { ContextualEngine.create(config, parser) }

    @ReactMethod
    fun parseSms(samples: ReadableArray, promise: Promise) {
        try {
            // Iterate by size() so any-length input works (C6 — never assumes 25).
            val inputs = ArrayList<String>(samples.size())
            for (i in 0 until samples.size()) {
                inputs.add(if (samples.isNull(i)) "" else samples.getString(i))
            }
            val out: WritableArray = Arguments.createArray()
            for (result in parser.parseAll(inputs)) {
                out.pushMap(toWritableMap(ResultMapper.toMap(result)))
            }
            promise.resolve(out)
        } catch (e: Throwable) {
            promise.reject("SMS_PARSE_ERROR", e)
        }
    }

    /**
     * Session API (WS-3): run an ordered batch of records through the contextual
     * engine and return the §7 SessionResult. ADDITIVE — does not touch parseSms
     * or its frozen schema. Each element is a map { text, receivedAt?, sender? }.
     */
    @ReactMethod
    fun parseSmsSession(records: ReadableArray, promise: Promise) {
        try {
            // Iterate by size() so any-length input works (C6 — never assumes 25).
            val inputs = ArrayList<SmsRecord>(records.size())
            for (i in 0 until records.size()) {
                if (records.isNull(i)) {
                    inputs.add(SmsRecord(text = "", receivedAt = null, sender = null))
                    continue
                }
                val rec = records.getMap(i)
                val text = if (rec.hasKey("text") && !rec.isNull("text")) {
                    rec.getString("text") ?: ""
                } else {
                    ""
                }
                val receivedAt = if (rec.hasKey("receivedAt") && !rec.isNull("receivedAt")) {
                    rec.getDouble("receivedAt").toLong()
                } else {
                    null
                }
                val sender = if (rec.hasKey("sender") && !rec.isNull("sender")) {
                    rec.getString("sender")
                } else {
                    null
                }
                inputs.add(SmsRecord(text = text, receivedAt = receivedAt, sender = sender))
            }
            val session = sessionEngine.process(inputs)
            promise.resolve(toWritableMap(SessionResultMapper.toMap(session)))
        } catch (e: Throwable) {
            // Never throw across the bridge (C8).
            promise.reject("SMS_SESSION_ERROR", e)
        }
    }

    /**
     * Generic transcription of a pure schema Map into a React WritableMap. Handles
     * the same value kinds the parseSms path uses, plus List (for the session
     * rollups) and Long (epoch millis) — additive, the parseSms tree never carries
     * those, so its output is unchanged.
     */
    @Suppress("UNCHECKED_CAST")
    private fun toWritableMap(map: Map<String, Any?>): WritableMap {
        val wm = Arguments.createMap()
        for ((key, value) in map) {
            when (value) {
                null -> wm.putNull(key)
                is String -> wm.putString(key, value)
                is Boolean -> wm.putBoolean(key, value)
                is Int -> wm.putInt(key, value)
                is Long -> wm.putDouble(key, value.toDouble())
                is Double -> wm.putDouble(key, value)
                is Map<*, *> -> wm.putMap(key, toWritableMap(value as Map<String, Any?>))
                is List<*> -> wm.putArray(key, toWritableArray(value))
                else -> wm.putString(key, value.toString())
            }
        }
        return wm
    }

    /** Generic transcription of a pure List into a React WritableArray (session API). */
    @Suppress("UNCHECKED_CAST")
    private fun toWritableArray(list: List<*>): WritableArray {
        val wa = Arguments.createArray()
        for (value in list) {
            when (value) {
                null -> wa.pushNull()
                is String -> wa.pushString(value)
                is Boolean -> wa.pushBoolean(value)
                is Int -> wa.pushInt(value)
                is Long -> wa.pushDouble(value.toDouble())
                is Double -> wa.pushDouble(value)
                is Map<*, *> -> wa.pushMap(toWritableMap(value as Map<String, Any?>))
                is List<*> -> wa.pushArray(toWritableArray(value))
                else -> wa.pushString(value.toString())
            }
        }
        return wa
    }

    companion object {
        const val NAME = "SmsParser"
    }
}
