package com.cashkaro.smsparser.parser.session

import com.cashkaro.smsparser.parser.config.MerchantCategoryDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for the config-driven [MerchantCanonicalizer] (WS-2). */
class MerchantCanonicalizerTest {

    private val entries = listOf(
        MerchantCategoryDef("Netflix", "entertainment", true, listOf("netflix")),
        MerchantCategoryDef("Swiggy", "food", false, listOf("swiggy instamart", "swiggy")),
        MerchantCategoryDef("BigBasket", "groceries", false, listOf("bigbasket", "big basket")),
    )
    private val canon = MerchantCanonicalizer(entries)

    @Test
    fun netflix_variants_all_collapse() {
        for (v in listOf("NETFLIX.COM/US", "NETFLIX-MONTHLY", "NETFLIX_SUBSCRIPTION", "netflix")) {
            assertEquals("variant $v", "Netflix", canon.canonicalize(v, null)?.canonical)
        }
    }

    @Test
    fun multi_word_token_matches_with_internal_space() {
        assertEquals("BigBasket", canon.canonicalize("BIGBASKET", null)?.canonical)
        assertEquals("BigBasket", canon.canonicalize("Big Basket Store", null)?.canonical)
    }

    @Test
    fun unknown_merchant_returns_null_conservatively() {
        assertNull(canon.canonicalize("FOREIGN MERCHANT", null))
        assertNull(canon.canonicalize(null, "Avl Bal in your A/C is INR 1,02,450"))
    }

    @Test
    fun body_fallback_finds_token_inside_longer_phrase() {
        val m = canon.canonicalize(null, "Rs 99 debited to NETFLIX-MONTHLY. Avl Bal: Rs 1,02,351")
        assertEquals("Netflix", m?.canonical)
        assertEquals("entertainment", m?.category)
    }

    @Test
    fun category_is_carried_through() {
        assertEquals("food", canon.canonicalize("SWIGGY", null)?.category)
    }
}
