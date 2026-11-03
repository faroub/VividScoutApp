package com.example.util

/**
 * Resolves language fallback chain order for Wikipedia history extracts.
 * Order: User Selected Language -> English ("en") -> Portuguese ("pt") -> Spanish ("es").
 */
object WikipediaHistoryResolver {

    /**
     * Builds the ordered list of language codes to query based on user's active locale selection.
     */
    fun buildLanguageFallbackChain(userLangCode: String?): List<String> {
        val chain = mutableListOf<String>()

        if (!userLangCode.isNullOrBlank()) {
            val code = userLangCode.lowercase().take(2)
            chain.add(code)
        }

        val defaults = listOf("en", "pt", "es")
        for (lang in defaults) {
            if (!chain.contains(lang)) {
                chain.add(lang)
            }
        }

        return chain
    }

    /**
     * Selects the best available extract from a map of language -> extract strings.
     */
    fun selectBestExtract(
        extractsByLang: Map<String, String>,
        userLangCode: String?
    ): Pair<String, String>? {
        val chain = buildLanguageFallbackChain(userLangCode)

        for (lang in chain) {
            val text = extractsByLang[lang]
            if (!text.isNullOrBlank()) {
                return Pair(lang, text)
            }
        }

        return null
    }
}
