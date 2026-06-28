package com.cashkaro.smsparser.parser.config

import com.google.gson.Gson

/**
 * Pure-Kotlin (no android.*) assembler of a [ParserConfig] from the seven JSON
 * config files. It is given a [readFile] function so the SOURCE of the bytes
 * (Android assets vs JVM test resources) is injected — see [ConfigSource].
 *
 * Uses Gson. Gson bypasses Kotlin constructors, so data-class default values do
 * NOT apply to missing JSON fields; therefore every RAW DTO field below is
 * nullable and is normalised to a safe, non-null domain type here. String tokens
 * are lowercased so components can match them against `NormalizedSms.lower`.
 */
class JsonConfigParser(private val gson: Gson = Gson()) {

    /** @param readFile maps a file name like "banks.json" to its UTF-8 contents. */
    fun parse(readFile: (String) -> String): ParserConfig {
        val banks = gson.fromJson(readFile(BANKS), BanksFile::class.java)
        val cardProducts = gson.fromJson(readFile(CARD_PRODUCTS), CardProductsFile::class.java)
        val products = gson.fromJson(readFile(PRODUCTS), ProductsFile::class.java)
        val rules = gson.fromJson(readFile(EXCLUSION_RULES), ExclusionRulesFile::class.java)
        val currencies = gson.fromJson(readFile(CURRENCIES), CurrenciesFile::class.java)
        val merchants = gson.fromJson(readFile(MERCHANTS), MerchantsFile::class.java)
        val dates = gson.fromJson(readFile(DATES), DatesFile::class.java)
        // Additive (V3): the contextual engine's merchant-canonical/category seed.
        // Tolerate absence so the frozen parseSms path never depends on it.
        val merchantCategories = runCatching {
            gson.fromJson(readFile(MERCHANT_CATEGORIES), MerchantCategoriesFile::class.java)
        }.getOrNull()

        return ParserConfig(
            banks = banks?.banks.orEmpty().mapNotNull { it?.toDomain() },
            cardProducts = cardProducts?.cardProducts.orEmpty().mapNotNull { it?.toDomain() },
            creditCardSignals = products?.creditCardSignals.toLowerList(),
            nonCardSignals = products?.nonCardSignals.toLowerList(),
            exclusionRules = rules?.rules.orEmpty().mapNotNull { it?.toDomain() },
            currencies = currencies?.currencies.orEmpty().mapNotNull { it?.toDomain() },
            merchant = (merchants ?: MerchantsFile()).toDomain(),
            dateFormats = dates?.formats.orEmpty().filterNotNull().map { it.trim() }.filter { it.isNotEmpty() },
            merchantCategories = merchantCategories?.merchants.orEmpty().mapNotNull { it?.toDomain() },
            threadWindowMinutes = merchantCategories?.threadWindowMinutes?.takeIf { it > 0 } ?: 15,
        )
    }

    // ---- raw Gson DTOs (all nullable; one wrapper per file) ----

    private data class BanksFile(val banks: List<RawBank?>? = null)
    private data class RawBank(val canonical: String? = null, val patterns: List<String?>? = null) {
        fun toDomain(): BankPattern? {
            val c = canonical?.trim().orEmpty()
            if (c.isEmpty()) return null
            return BankPattern(c, patterns.toLowerList())
        }
    }

    private data class CardProductsFile(val cardProducts: List<RawCardProduct?>? = null)
    private data class RawCardProduct(val product: String? = null, val issuer: String? = null) {
        fun toDomain(): CardProduct? {
            val p = product?.trim().orEmpty()
            val i = issuer?.trim().orEmpty()
            return if (p.isEmpty() || i.isEmpty()) null else CardProduct(p, i)
        }
    }

    private data class ProductsFile(
        val creditCardSignals: List<String?>? = null,
        val nonCardSignals: List<String?>? = null,
    )

    private data class ExclusionRulesFile(val rules: List<RawRule?>? = null)
    private data class RawRule(
        val reason: String? = null,
        val any: List<String?>? = null,
        val unless: List<String?>? = null,
        val withCard: Boolean? = null,
        val notCreditCard: Boolean? = null,
    ) {
        fun toDomain(): ExclusionRuleDef? {
            val r = reason?.trim().orEmpty()
            if (r.isEmpty()) return null
            return ExclusionRuleDef(
                reason = r,
                any = any.toLowerList(),
                unless = unless.toLowerList(),
                withCard = withCard ?: false,
                notCreditCard = notCreditCard ?: false,
            )
        }
    }

    private data class CurrenciesFile(val currencies: List<RawCurrency?>? = null)
    private data class RawCurrency(val code: String? = null, val tokens: List<String?>? = null) {
        fun toDomain(): CurrencyDef? {
            val c = code?.trim().orEmpty()
            if (c.isEmpty()) return null
            return CurrencyDef(c.uppercase(), tokens.toLowerList())
        }
    }

    private data class MerchantsFile(
        val atPrepositions: List<String?>? = null,
        val stripSuffixes: List<String?>? = null,
        val stripCity: Boolean? = null,
    ) {
        fun toDomain(): MerchantConfig = MerchantConfig(
            atPrepositions = atPrepositions.toLowerList(),
            stripSuffixes = stripSuffixes.toLowerList(),
            stripCity = stripCity ?: false,
        )
    }

    private data class DatesFile(val formats: List<String?>? = null)

    private data class MerchantCategoriesFile(
        val merchants: List<RawMerchantCategory?>? = null,
        val threadWindowMinutes: Int? = null,
    )
    private data class RawMerchantCategory(
        val canonical: String? = null,
        val category: String? = null,
        val subscription: Boolean? = null,
        val tokens: List<String?>? = null,
    ) {
        fun toDomain(): MerchantCategoryDef? {
            val c = canonical?.trim().orEmpty()
            val toks = tokens.toLowerList()
            if (c.isEmpty() || toks.isEmpty()) return null
            val cat = category?.trim()?.lowercase()?.ifEmpty { null }
            return MerchantCategoryDef(
                canonical = c,
                category = cat,
                subscription = subscription ?: false,
                tokens = toks,
            )
        }
    }

    companion object {
        const val BANKS = "banks.json"
        const val CARD_PRODUCTS = "card-products.json"
        const val PRODUCTS = "products.json"
        const val EXCLUSION_RULES = "exclusion-rules.json"
        const val CURRENCIES = "currencies.json"
        const val MERCHANTS = "merchants.json"
        const val DATES = "dates.json"

        /**
         * Additive (V3): contextual-engine merchant-canonical/category seed. Kept
         * OUT of [FILE_NAMES] so the frozen parseSms config-load contract is
         * unchanged; loaded best-effort and tolerated when absent.
         */
        const val MERCHANT_CATEGORIES = "merchant-categories.json"

        /** All config file names, in a stable order (used by mirror/load tests). */
        val FILE_NAMES = listOf(
            BANKS, CARD_PRODUCTS, PRODUCTS, EXCLUSION_RULES, CURRENCIES, MERCHANTS, DATES,
        )

        /** Trim, drop blanks/nulls, lowercase — the standard token normalisation. */
        private fun List<String?>?.toLowerList(): List<String> =
            this.orEmpty().filterNotNull().map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    }
}
