package com.cashkaro.smsparser.parser.classify

import com.cashkaro.smsparser.parser.NormalizedSms
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1: the frozen CardSignal helper. Critically, it must keep "avl limit"
 * (credit-card signal) distinct from "avl bal" (balance alert) — never collapse
 * the "avl " prefix.
 */
class CardSignalTest {

    private val signal = CardSignal(
        creditCardSignals = listOf("credit card", "avl limit", "available limit", "avl lmt"),
        nonCardSignals = listOf("debit card", "a/c", "upi"),
    )

    private fun sms(text: String) = NormalizedSms(text, text, text.lowercase())

    @Test
    fun creditCard_phrase_is_a_credit_card_signal() {
        assertTrue(signal.hasCreditCardSignal(sms("HDFC Bank Credit Card xx5678 at SWIGGY")))
    }

    @Test
    fun avlLimit_is_a_credit_card_signal() {
        assertTrue(signal.hasCreditCardSignal(sms("Avl Limit: INR 1,45,300.00")))
        assertTrue(signal.hasCreditCardSignal(sms("Available Limit: INR 87,500")))
    }

    @Test
    fun avlBal_is_NOT_a_credit_card_signal() {
        assertFalse(signal.hasCreditCardSignal(sms("Avl Bal in your A/C XX4521 is INR 1,02,450")))
        assertFalse(signal.hasCreditCardSignal(sms("Available Balance: Rs 50,000")))
    }

    @Test
    fun debitCard_and_account_are_nonCard_signals() {
        assertTrue(signal.hasNonCardSignal(sms("HDFC Bank Debit Card ending 1234")))
        assertTrue(signal.hasNonCardSignal(sms("debited from A/C XX4521 via UPI")))
    }

    @Test
    fun creditCard_message_has_no_nonCard_signal() {
        assertFalse(signal.hasNonCardSignal(sms("Credit Card xx5678 Avl Limit INR 1,45,300")))
    }
}
