package com.cashkaro.smsparser

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap

/**
 * React Native bridge for the SMS parser (PHASE 0 STUB).
 *
 * This is the only Android-aware layer. In later phases it will construct the
 * pure-Kotlin SmsParser (via AssetConfigSource) and delegate to it. For now it
 * echoes each input back as a schema-valid EXCLUDE / LOW_CONFIDENCE result so
 * the JS <-> Kotlin round-trip is provable end to end.
 *
 * Result shape (docs/Functions.md): rawSms, decision, excludeReason,
 * transaction (null here), confidence.
 */
class SmsParserModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String = NAME

  @ReactMethod
  fun parseSms(samples: ReadableArray, promise: Promise) {
    try {
      val results: WritableArray = Arguments.createArray()
      // Iterate by size() so any-length input works (C6 — no fixed 25).
      for (i in 0 until samples.size()) {
        val raw = if (samples.isNull(i)) "" else samples.getString(i)
        val result: WritableMap = Arguments.createMap()
        result.putString("rawSms", raw)
        result.putString("decision", "EXCLUDE")
        result.putString("excludeReason", "LOW_CONFIDENCE")
        result.putNull("transaction")
        result.putDouble("confidence", 0.0)
        results.pushMap(result)
      }
      promise.resolve(results)
    } catch (e: Exception) {
      promise.reject("SMS_PARSE_ERROR", e)
    }
  }

  companion object {
    const val NAME = "SmsParser"
  }
}
