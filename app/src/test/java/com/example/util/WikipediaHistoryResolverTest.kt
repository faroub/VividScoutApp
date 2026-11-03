package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WikipediaHistoryResolverTest {

    @Test
    fun buildLanguageFallbackChain_ordersUserLangFirstThenDefaults() {
        val chainFr = WikipediaHistoryResolver.buildLanguageFallbackChain("fr")
        assertEquals(listOf("fr", "en", "pt", "es"), chainFr)

        val chainAr = WikipediaHistoryResolver.buildLanguageFallbackChain("ar")
        assertEquals(listOf("ar", "en", "pt", "es"), chainAr)

        val chainEn = WikipediaHistoryResolver.buildLanguageFallbackChain("en")
        assertEquals(listOf("en", "pt", "es"), chainEn)

        val chainNull = WikipediaHistoryResolver.buildLanguageFallbackChain(null)
        assertEquals(listOf("en", "pt", "es"), chainNull)
    }

    @Test
    fun selectBestExtract_selectsUserLanguageIfAvailable() {
        val extracts = mapOf(
            "fr" to "Extrait de Wikipédia en français.",
            "en" to "English Wikipedia extract.",
            "pt" to "Extrato da Wikipédia em português."
        )

        val selectedFr = WikipediaHistoryResolver.selectBestExtract(extracts, "fr")
        assertNotNull(selectedFr)
        assertEquals("fr", selectedFr!!.first)
        assertEquals("Extrait de Wikipédia en français.", selectedFr.second)
    }

    @Test
    fun selectBestExtract_fallsBackToEnglishWhenUserLangMissing() {
        val extracts = mapOf(
            "en" to "English Wikipedia extract.",
            "pt" to "Extrato da Wikipédia em português."
        )

        val selectedAr = WikipediaHistoryResolver.selectBestExtract(extracts, "ar")
        assertNotNull(selectedAr)
        assertEquals("en", selectedAr!!.first)
        assertEquals("English Wikipedia extract.", selectedAr.second)
    }

    @Test
    fun selectBestExtract_fallsBackToPortugueseWhenUserAndEnglishMissing() {
        val extracts = mapOf(
            "pt" to "Extrato da Wikipédia em português."
        )

        val selectedAr = WikipediaHistoryResolver.selectBestExtract(extracts, "ar")
        assertNotNull(selectedAr)
        assertEquals("pt", selectedAr!!.first)
        assertEquals("Extrato da Wikipédia em português.", selectedAr.second)
    }

    @Test
    fun selectBestExtract_returnsNullWhenNoExtractMatchesChain() {
        val emptyExtracts = emptyMap<String, String>()
        val result = WikipediaHistoryResolver.selectBestExtract(emptyExtracts, "fr")
        assertNull(result)
    }
}
