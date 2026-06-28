package com.cashkaro.smsparser.parser.session

import com.cashkaro.smsparser.parser.AmountExtractor
import com.cashkaro.smsparser.parser.CardExtractor
import com.cashkaro.smsparser.parser.DateExtractor
import com.cashkaro.smsparser.parser.Normalizer

/**
 * Correlation stage of a message — the signals the [TransactionThreader] keys on.
 *
 * Why re-derive instead of reading [com.cashkaro.smsparser.parser.model.Transaction]:
 * the frozen contract makes `transaction` NULL for every EXCLUDE result, yet the
 * lifecycle we must thread includes EXCLUDE messages (EMI conversion #17, bill
 * #12/#19, balance, future auto-debit #14). So the engine re-extracts the linking
 * signals straight from the raw body using the SAME stateless extractors, leaving
 * the core [com.cashkaro.smsparser.parser.model.ParsedResult] untouched (V1/V6).
 *
 * @param stage lifecycle position used only to ORDER events within a thread.
 * @param card4 masked card last-four, or null.
 * @param amount primary transaction amount, or null.
 * @param canonicalMerchant resolved canonical merchant, or null.
 * @param primaryDate the FIRST in-body date (ISO), or null.
 * @param backRefDate a SECONDARY in-body date introduced by a back-reference
 *   phrase ("original txn dated ..."), or null — the explicit lifecycle link.
 * @param effectiveTime receivedAt when present, else the in-body date as millis,
 *   else null (caller falls back to input order).
 */
data class CorrelationSignals(
    val stage: LifecycleStage,
    val card4: String?,
    val amount: Double?,
    val canonicalMerchant: String?,
    val primaryDate: String?,
    val backRefDate: String?,
    val effectiveTime: Long?,
)

/**
 * Lifecycle position, used to ORDER events inside a thread (auth/OTP -> spend ->
 * refund/EMI/bill). Detection is keyword-based and conservative; it never changes
 * the core decision (D4/D6).
 */
enum class LifecycleStage(val order: Int) {
    AUTH(0),      // OTP/auth carrying an amount + card
    SPEND(1),     // a confirmed spend / debit
    REFUND(2),    // refund / reversal
    EMI(3),       // spend converted to EMI
    BILL(4),      // statement / bill / payment-received
    OTHER(5),     // anything else (balance alerts, declines, ...)
}

/**
 * Re-derives [CorrelationSignals] from a raw SMS body using the stateless
 * extractors. Pure Kotlin; reuses the same Normalizer/Amount/Card/Date components
 * the parser uses so signals are consistent with the parse.
 */
class CorrelationExtractor(
    private val normalizer: Normalizer,
    private val amountExtractor: AmountExtractor,
    private val cardExtractor: CardExtractor,
    private val dateExtractor: DateExtractor,
    private val canonicalizer: MerchantCanonicalizer,
) {

    fun extract(rawSms: String, receivedAt: Long?): CorrelationSignals {
        val sms = normalizer.normalize(rawSms)
        val lower = sms.lower
        val card4 = cardExtractor.extract(sms).lastFour
        val amount = amountExtractor.extract(sms)
        val merchant = canonicalizer.canonicalize(merchant = null, body = sms.text)?.canonical
        val primaryDate = dateExtractor.extract(sms)
        val backRefDate = extractBackRefDate(sms.text, primaryDate)
        val effectiveTime = receivedAt ?: primaryDate?.let { isoToMillis(it) }
        return CorrelationSignals(
            stage = detectStage(lower),
            card4 = card4,
            amount = amount,
            canonicalMerchant = merchant,
            primaryDate = primaryDate,
            backRefDate = backRefDate,
            effectiveTime = effectiveTime,
        )
    }

    /** Keyword lifecycle classification — ordering only, never a decision. */
    private fun detectStage(lower: String): LifecycleStage = when {
        lower.contains("refund") || lower.contains("reversal") || lower.contains("reversed") -> LifecycleStage.REFUND
        lower.contains("converted to emi") || lower.contains("emi of") || lower.contains("convert to emi") -> LifecycleStage.EMI
        lower.contains("otp") || lower.contains("one time password") || lower.contains("one-time password") -> LifecycleStage.AUTH
        lower.contains("bill of") || lower.contains("bill is due") || lower.contains("payment of") ||
            lower.contains("received towards") || lower.contains("statement") -> LifecycleStage.BILL
        lower.contains("spent") || lower.contains("debited") || lower.contains("sent ") ||
            lower.contains("purchase") || lower.contains("paid") -> LifecycleStage.SPEND
        else -> LifecycleStage.OTHER
    }

    /**
     * A back-reference date is a SECOND date that is NOT the primary, introduced by
     * a phrase like "original txn dated 02-04-26" / "txn dated ...". We scan all
     * date-shaped tokens and return the first that differs from [primaryDate].
     */
    private fun extractBackRefDate(text: String, primaryDate: String?): String? {
        if (!BACK_REF_PHRASE.containsMatchIn(text)) return null
        // The dateExtractor returns only the first date. Walk candidate tokens and
        // find one whose ISO form differs from the primary.
        for (m in DATE_CANDIDATE.findAll(text)) {
            val iso = isoFromToken(m.value) ?: continue
            if (iso != primaryDate) return iso
        }
        return null
    }

    /** Parse a single date-shaped token to ISO via the shared date extractor logic. */
    private fun isoFromToken(token: String): String? {
        val sms = normalizer.normalize(token)
        return dateExtractor.extract(sms)
    }

    private companion object {
        /** Mirrors DefaultDateExtractor's candidate shapes (dash/slash + spaced month). */
        val DATE_CANDIDATE = Regex(
            "\\b\\d{1,2}[-/][A-Za-z0-9]{1,3}[-/]\\d{2,4}\\b" +
                "|\\b\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{2,4}\\b",
        )

        /** Phrases that introduce a back-referenced original transaction. */
        val BACK_REF_PHRASE = Regex(
            "original\\s+txn|original\\s+transaction|against\\s+(?:original|txn)|txn\\s+dated|bill\\s+dated",
            RegexOption.IGNORE_CASE,
        )

        /**
         * Convert an ISO `yyyy-MM-dd` date to UTC midnight millis for same-day /
         * window comparisons. Manual (no java.time) to stay minSdk-23 safe and
         * deterministic regardless of the default timezone.
         */
        fun isoToMillis(iso: String): Long? {
            val parts = iso.split("-")
            if (parts.size != 3) return null
            val y = parts[0].toIntOrNull() ?: return null
            val mo = parts[1].toIntOrNull() ?: return null
            val d = parts[2].toIntOrNull() ?: return null
            return daysFromEpoch(y, mo, d) * 86_400_000L
        }

        /** Days since 1970-01-01 (proleptic Gregorian) — pure integer arithmetic. */
        private fun daysFromEpoch(year: Int, month: Int, day: Int): Long {
            // Howard Hinnant's days_from_civil algorithm.
            val y = if (month <= 2) year - 1 else year
            val era = (if (y >= 0) y else y - 399) / 400
            val yoe = (y - era * 400).toLong()
            val doy = ((153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1).toLong()
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return era.toLong() * 146097 + doe - 719468
        }
    }
}
