package com.cashkaro.smsparser

import com.cashkaro.smsparser.parser.ResultMapper
import com.cashkaro.smsparser.parser.SmsParser
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

    private val parser: SmsParser by lazy {
        SmsParser.create(AssetConfigSource(reactApplicationContext.assets).load())
    }

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

    /** Generic transcription of the pure schema Map into a React WritableMap. */
    @Suppress("UNCHECKED_CAST")
    private fun toWritableMap(map: Map<String, Any?>): WritableMap {
        val wm = Arguments.createMap()
        for ((key, value) in map) {
            when (value) {
                null -> wm.putNull(key)
                is String -> wm.putString(key, value)
                is Boolean -> wm.putBoolean(key, value)
                is Int -> wm.putInt(key, value)
                is Double -> wm.putDouble(key, value)
                is Map<*, *> -> wm.putMap(key, toWritableMap(value as Map<String, Any?>))
                else -> wm.putString(key, value.toString())
            }
        }
        return wm
    }

    companion object {
        const val NAME = "SmsParser"
    }
}
