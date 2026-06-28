package com.cashkaro.smsparser.parser.extract

import com.cashkaro.smsparser.parser.DateExtractor
import com.cashkaro.smsparser.parser.NormalizedSms
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Config-driven [DateExtractor] (Component 2E). Scans the SMS for date-shaped
 * tokens and parses the FIRST that matches any configured format into ISO
 * `yyyy-MM-dd`. Returns null when nothing parses — never guesses (C7).
 *
 * Why [SimpleDateFormat] and not `java.time`: minSdk is 23, and `java.time`
 * (`LocalDate`, `DateTimeFormatter`) is API 26+ and would need core-library
 * desugaring. `SimpleDateFormat` (java.util) works on every API level and on the
 * JVM test runtime, so the parser core stays pure-Kotlin and uniformly testable.
 *
 * Two-digit years (e.g. `26`, `Apr-26`) map to 20YY: each parser's
 * `set2DigitYearStart` is pinned to a 2000-01-01 pivot, so `26` resolves to 2026
 * rather than the default-century 1926. Parsing is strict
 * ([SimpleDateFormat.setLenient] = false) so a numeric pattern never silently
 * swallows a `MMM` month token (and vice-versa), letting format priority order
 * disambiguate cleanly.
 *
 * Multi-date messages (e.g. "...on 12-04-26 against original txn dated
 * 02-04-26") return the FIRST date — the primary transaction date — because
 * candidate tokens are scanned left-to-right and the first successful parse wins.
 *
 * Formats arrive from dates.json (e.g. `dd/MM/yy`, `dd-MM-yyyy`, `dd-MM-yy`,
 * `dd-MMM-yyyy`, `dd-MMM-yy`, plus spaced month-name shapes `dd MMM yyyy`,
 * `dd MMM yy`, `d MMM yyyy`, `dd MMMM yyyy`); adding a new shape is a config
 * edit, no code change (C5).
 */
class DefaultDateExtractor(formats: List<String>) : DateExtractor {

    /** ISO output format, fixed regardless of the (locale-dependent) input shape. */
    private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply { isLenient = false }

    /**
     * One pre-built, strict parser per configured format. A 2000-01-01 pivot
     * makes two-digit years resolve into the 2000s; for four-digit-year formats
     * the pivot is harmless. Patterns that fail to compile are dropped (C2).
     */
    private val parsers: List<SimpleDateFormat> = formats.mapNotNull { pattern ->
        val trimmed = pattern.trim()
        if (trimmed.isEmpty()) return@mapNotNull null
        try {
            SimpleDateFormat(trimmed, Locale.ENGLISH).apply {
                isLenient = false
                set2DigitYearStart(pivot2000())
            }
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Date-shaped tokens, two alternative shapes (the configured formats do the
     * real validation — this is deliberately loose):
     *  - dash/slash: `d`/`dd`, a `-` or `/` separator, a numeric OR 3-letter
     *    alphabetic month, the same kind of separator, then a 2-to-4-digit year
     *    (e.g. `02/04/26`, `04-Apr-26`, `03-04-2026`).
     *  - spaced month-name: `d`/`dd`, whitespace, a 3-to-9-letter month name,
     *    whitespace, then a 2-to-4-digit year (e.g. `03 Apr 2026`, `3 Apr 26`,
     *    `03 April 2026`). Kept as a SEPARATE alternative so the original dash/
     *    slash branch is byte-for-byte unchanged.
     */
    private val candidate = Regex(
        "\\b\\d{1,2}[-/][A-Za-z0-9]{1,3}[-/]\\d{2,4}\\b" +
            "|\\b\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{2,4}\\b",
    )

    override fun extract(sms: NormalizedSms): String? {
        if (parsers.isEmpty()) return null
        // Left-to-right: the first token that parses is the primary date.
        for (match in candidate.findAll(sms.text)) {
            val token = match.value
            val parsed = parse(token) ?: continue
            return iso.format(parsed)
        }
        return null
    }

    /** Try each configured format in order; the FIRST FULL-string match wins. */
    private fun parse(token: String): Date? {
        for (parser in parsers) {
            val pos = ParsePosition(0)
            val date = parser.parse(token, pos)
            // Require the WHOLE token to be consumed so e.g. a `dd-MM-yyyy` parser
            // doesn't partially accept a shorter token.
            if (date == null || pos.index != token.length) continue
            // Both `yy` and `yyyy` read digits greedily, so a `yyyy` pattern will
            // happily turn a 2-digit token year ("26") into year 0026. Reject any
            // implausible <1000 year so the matching 2-digit-year format (pivoted
            // into the 2000s) wins instead. Real txn years are always 4 digits.
            if (yearOf(date) < MIN_YEAR) continue
            return date
        }
        return null
    }

    private fun yearOf(date: Date): Int {
        val cal = Calendar.getInstance(Locale.ENGLISH)
        cal.time = date
        return cal.get(Calendar.YEAR)
    }

    private companion object {
        /** Below this, a parsed year is treated as a 4-digit pattern misreading a 2-digit token. */
        const val MIN_YEAR = 1000

        /** A calendar pinned to 2000-01-01, used as the two-digit-year pivot. */
        fun pivot2000(): Date {
            val cal = Calendar.getInstance(Locale.ENGLISH)
            cal.clear()
            cal.set(2000, Calendar.JANUARY, 1, 0, 0, 0)
            return cal.time
        }
    }
}
