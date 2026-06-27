package com.cashkaro.smsparser.parser.extract

import com.cashkaro.smsparser.parser.NormalizedSms
import com.cashkaro.smsparser.parser.config.TestConfigSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Component 2E — [DefaultDateExtractor].
 *
 * Drives the extractor with the REAL configured formats (loaded from
 * dates.json via [TestConfigSource]) so the tests exercise the same date shapes
 * the app ships with. Covers every format present in the 25 samples, the
 * two-digit-year => 20YY rule, the multi-date "first date wins" rule, and
 * several hidden-style novelty inputs (single-digit day/month, slash 4-digit
 * year, phone numbers / OTPs / long ref numbers that must NOT be misread).
 */
class DateExtractorTest {

    private val extractor = DefaultDateExtractor(TestConfigSource().load().dateFormats)

    private fun sms(text: String) = NormalizedSms(text, text, text.lowercase())

    private fun extract(text: String): String? = extractor.extract(sms(text))

    // ---- one assertion per format that appears in the samples ----

    @Test
    fun ddSlashMMSlashyy_format() {
        // Sample 1
        assertEquals("2026-04-02", extract("Sent Rs.450.00 ... on 02/04/26. Ref 405617287211."))
    }

    @Test
    fun ddDashMMDashyyyy_format() {
        // Sample 2
        assertEquals("2026-04-03", extract("INR 1,250.00 spent ... on 03-04-2026. Avl Limit"))
    }

    @Test
    fun ddDashMMDashyy_format() {
        // Sample 6
        assertEquals("2026-04-06", extract("Rs. 500.00 debited ... on 06-04-26."))
    }

    @Test
    fun ddDashMMMDashyy_mixedCase_format() {
        // Sample 3 (mixed-case month) and Sample 5 (upper-case month)
        assertEquals("2026-04-04", extract("ICICI ... debited Rs 2,500.00 on 04-Apr-26 & credited"))
        assertEquals("2026-04-06", extract("INR 320.00 spent ... on 06-APR-26 at AMAZON."))
    }

    @Test
    fun ddDashMMMDashyy_upperCaseMonth_sample22() {
        // Sample 22
        assertEquals("2026-04-13", extract("USD 49.99 spent ... on 13-APR-26. Foreign currency markup"))
    }

    @Test
    fun fourDigitYear_isPreferredCorrectly_notMisreadAsYear0026() {
        // Regression: a `dd-MM-yyyy` parser will greedily read just "26" into year
        // 0026; the extractor must reject that and pick the 2-digit-year format.
        assertEquals("2026-04-07", extract("ending 4422 on 07-04-2026. Tap to view"))
        assertEquals("2026-04-06", extract("debited from Debit Card ending 1234 on 06-04-26."))
    }

    // ---- two-digit year always maps to 20YY ----

    @Test
    fun twoDigitYear_mapsTo20YY_not19YY() {
        assertEquals("2026-04-15", extract("bill of Rs 23,450.00 is due on 15-04-26."))
        assertEquals("2027-01-01", extract("txn on 1-Jan-27 cleared."))
    }

    // ---- multi-date message returns the FIRST (primary) date ----

    @Test
    fun multipleDates_returnsFirst() {
        // Sample 21: refund date 12-04-26 comes first, original txn 02-04-26 second.
        assertEquals(
            "2026-04-12",
            extract("Refund of Rs 450.00 ... on 12-04-26 against original txn dated 02-04-26."),
        )
    }

    // ---- no date => null (never a guess, C7) ----

    @Test
    fun noDate_returnsNull() {
        assertNull(extract("No date here at all, just some words."))
    }

    @Test
    fun otpDigits_areNotADate() {
        // Sample 10 — the 6-digit OTP and "5 mins" must not be read as a date.
        assertNull(extract("Use 458219 as your OTP for HDFC Bank Net Banking login. Valid for 5 mins."))
    }

    @Test
    fun truncatedMalformed_returnsNull() {
        // Sample 25
        assertNull(extract("Spent Rs. 2,4"))
    }

    @Test
    fun longRefAndPhoneNumbers_areNotMisreadAsDates() {
        // Sample 1 tail: a long ref number and phone numbers with a slash must not
        // produce a false date. The only real date (02/04/26) is returned.
        assertEquals(
            "2026-04-02",
            extract(
                "Sent Rs.450.00 From HDFC Bank A/C *4521 To BIGBASKET on 02/04/26. " +
                    "Ref 405617287211. Not You? Call 18002586161/SMS BLOCK CC to 7308080808 to block CC.",
            ),
        )
    }

    @Test
    fun phoneOnlyBody_hasNoDate() {
        assertNull(extract("Not You? Call 18002586161/SMS BLOCK CC to 7308080808 to block CC."))
    }

    // ---- hidden-style novelty wordings ----

    @Test
    fun novel_singleDigitDayAndMonth_slashFourDigitYear() {
        assertEquals("2026-03-05", extract("Spent Rs 99 on 5/3/2026 at CAFE PRIME."))
    }

    @Test
    fun novel_dashSeparated_singleDigitDay_namedMonth() {
        assertEquals("2026-12-09", extract("Purchase on 9-Dec-2026 posted to your account."))
    }

    @Test
    fun novel_invalidDateFallsThroughToValidOne() {
        // 32-13-26 is not a real date (day 32, month 13) under strict parsing, so
        // the extractor skips it and returns the next, valid candidate.
        assertEquals("2026-04-05", extract("garbled 32-13-26 then real 05-04-26"))
    }

    @Test
    fun novel_digitsWithoutSeparators_areNotDates() {
        assertNull(extract("ref 020426 processed successfully"))
    }

    // ---- empty config => no parsers => null (degrades safely) ----

    @Test
    fun emptyFormats_returnsNull() {
        val empty = DefaultDateExtractor(emptyList())
        assertNull(empty.extract(sms("spent on 03-04-2026")))
    }
}
