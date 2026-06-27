package com.cashkaro.smsparser.parser.bank

import com.cashkaro.smsparser.parser.NormalizedSms
import com.cashkaro.smsparser.parser.config.BankPattern
import com.cashkaro.smsparser.parser.config.CardProduct
import com.cashkaro.smsparser.parser.config.TestConfigSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [DefaultBankResolver] (C4). Covers co-brand resolution (the
 * most-evaluated behaviour), direct-bank resolution, multi-bank precedence via
 * proximity, the conservative null path, and config extensibility (C5) — the
 * last built against an ad-hoc config so adding a bank is a pure data change.
 */
class BankResolverTest {

    /** Resolver wired from the real bundled config (the 25-sample oracle source). */
    private val resolver = DefaultBankResolver(TestConfigSource().load())

    private fun sms(text: String): NormalizedSms = NormalizedSms(text, text, text.lowercase())

    // ---- co-brand FIRST (fintech / product branding => issuer) ----

    @Test
    fun edgeFederalCard_resolvesToFederalBank() {
        val text = "Hey there, you've spent Rs 1836.00 to HOSPITALITY PVT DELHI IN on your " +
            "Edge Federal Bank Credit Card ending 4422 on 07-04-2026. " +
            "Tap to view your transactions in the Jupiter app."
        assertEquals("Federal Bank", resolver.resolve(sms(text)))
    }

    @Test
    fun jupiterAppBranding_resolvesToFederalBank() {
        // App branding alone (no explicit "Federal Bank" in body) still resolves
        // via the co-brand map — branding must not hide the real issuer.
        val text = "You've spent Rs 200 on your Jupiter app Credit Card ending 1212 on 01-05-26."
        assertEquals("Federal Bank", resolver.resolve(sms(text)))
    }

    @Test
    fun bobcardOne_resolvesToBankOfBaroda() {
        val text = "You've spent Rs. 849.00 at Blackwater Coffee, Gurgaon with your " +
            "BOBCARD One Credit Card ending in XX9907 on 08-04-2026."
        assertEquals("Bank of Baroda", resolver.resolve(sms(text)))
    }

    @Test
    fun bareBobcard_resolvesToBankOfBaroda() {
        val text = "Rs 300 spent on your BOBCARD Credit Card XX1111 on 02-05-26."
        assertEquals("Bank of Baroda", resolver.resolve(sms(text)))
    }

    // ---- direct bank resolution from the body ----

    @Test
    fun hdfcCreditCard_resolvesToHdfcBank() {
        val text = "INR 1,250.00 spent on HDFC Bank Credit Card xx5678 at SWIGGY on 03-04-2026. " +
            "Avl Limit: INR 1,45,300.00."
        assertEquals("HDFC Bank", resolver.resolve(sms(text)))
    }

    @Test
    fun axisCardSample_resolvesToAxisBank() {
        val text = "INR 320.00 spent using Axis Bank Card no. XX9876 on 06-APR-26 at AMAZON. " +
            "Available Limit: INR 87,500.00."
        assertEquals("Axis Bank", resolver.resolve(sms(text)))
    }

    @Test
    fun iciciCreditCard_resolvesToIciciBank() {
        val text = "Transaction Declined: Attempt to spend Rs. 9,999 on your ICICI Credit Card " +
            "XX1122 at FOREIGN MERCHANT was declined due to insufficient credit limit."
        assertEquals("ICICI Bank", resolver.resolve(sms(text)))
    }

    @Test
    fun yesBankCreditCard_resolvesToYesBank() {
        val text = "Spent Rs. 1200.00 on YES BANK Credit Card XX8888 at AMAZON on 07-04-26. " +
            "Avl Lmt: Rs 78,500."
        assertEquals("Yes Bank", resolver.resolve(sms(text)))
    }

    // ---- multi-bank precedence: issuer adjacent to card/spend wins ----

    @Test
    fun multiBank_issuerNearCardWins_helplineBankIgnored() {
        val text = "Axis Bank Credit Card XX1234 spent Rs 500.00 at AMAZON on 09-04-26. " +
            "Avl Limit Rs 50,000. Call HDFC Bank helpline 1800-123-456 if not you."
        assertEquals("Axis Bank", resolver.resolve(sms(text)))
    }

    @Test
    fun multiBank_poweredByFooterIgnored() {
        // Issuer (ICICI) sits next to spend/limit language; the "powered by"
        // footer naming a different bank must lose.
        val text = "Rs 750.00 spent on ICICI Bank Credit Card XX2233 at FLIPKART on 10-04-26. " +
            "Avl Limit Rs 40,000. Network powered by Yes Bank, T&C apply."
        assertEquals("ICICI Bank", resolver.resolve(sms(text)))
    }

    @Test
    fun coBrandProductBeatsDirectBankFooter() {
        // A product (Jupiter => Federal) is present alongside a direct bank
        // mention in a footer; co-brand-first must still win.
        val text = "Rs 425.00 spent on your Jupiter Credit Card ending 7788 on 11-04-26. " +
            "Call HDFC Bank helpline if not you."
        assertEquals("Federal Bank", resolver.resolve(sms(text)))
    }

    // ---- conservative null path (never guess) ----

    @Test
    fun unknownBank_resolvesToNull() {
        val text = "Rs 500.00 spent on Foobar Bank Credit Card XX0000 at SHOP on 12-04-26."
        assertNull(resolver.resolve(sms(text)))
    }

    @Test
    fun noBankToken_resolvesToNull() {
        val text = "Rs 500.00 spent at SHOP on 12-04-26. Ref 12345."
        assertNull(resolver.resolve(sms(text)))
    }

    @Test
    fun emptyBody_resolvesToNull() {
        assertNull(resolver.resolve(sms("")))
    }

    // ---- hidden-style novel wording ----

    @Test
    fun novelWording_kotakResolvesFromBody() {
        // Wording not present in the 25 samples; generalises via the "kotak"
        // pattern + spend context.
        val text = "Purchase of Rs 999 done on Kotak Mahindra Bank Credit Card xx4242 at " +
            "STARBUCKS, Pune. Available limit Rs 1,20,000."
        assertEquals("Kotak Mahindra Bank", resolver.resolve(sms(text)))
    }

    @Test
    fun novelWording_sbiAbbreviationResolves() {
        val text = "Txn of Rs 2,100 on your SBI Credit Card XX5151 at BIG BAZAAR on 15-05-26."
        assertEquals("State Bank of India", resolver.resolve(sms(text)))
    }

    // ---- config extensibility (C5): a brand-new bank resolves with NO code change ----

    @Test
    fun configExtensibility_brandNewBankResolves() {
        val extendedBanks = listOf(
            BankPattern("HDFC Bank", listOf("hdfc")),
            BankPattern("AU Small Finance Bank", listOf("au small finance", "au bank")),
        )
        val customResolver = DefaultBankResolver(extendedBanks, emptyList())
        val text = "Rs 600 spent on AU Bank Credit Card XX3434 at MALL on 16-05-26. Avl Limit Rs 70,000."
        assertEquals("AU Small Finance Bank", customResolver.resolve(sms(text)))
    }

    @Test
    fun configExtensibility_brandNewCoBrandResolves() {
        val products = listOf(CardProduct("scapia", "Federal Bank"))
        val customResolver = DefaultBankResolver(emptyList(), products)
        val text = "Rs 350 spent on your Scapia Credit Card ending 9090 on 17-05-26."
        assertEquals("Federal Bank", customResolver.resolve(sms(text)))
    }
}
