package com.example.util

import com.example.data.network.WikipediaPhotoService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPhotoResolverTest {

    @Test
    fun testLandmarkCatalogDirectAndFuzzyMatching() {
        // Exact match
        val santaLuzia = LisbonLandmarkCatalog.findLandmark("Miradouro de Santa Luzia")
        assertNotNull(santaLuzia)
        assertEquals("Alfama", santaLuzia?.district)
        assertTrue(santaLuzia?.imageUrl?.contains("MiradouroSantaLuzia.JPG") == true)

        // Substring / alias match
        val carmo = LisbonLandmarkCatalog.findLandmark("Convento do Carmo Ruins & Gothic Arches")
        assertNotNull(carmo)
        assertEquals("Chiado", carmo?.district)
        assertTrue(carmo?.imageUrl?.contains("Convento_do_Carmo") == true)

        // Bakeries
        val manteigaria = LisbonLandmarkCatalog.findLandmark("Pastelaria Manteigaria — Chiado")
        assertNotNull(manteigaria)
        assertTrue(manteigaria?.imageUrl?.contains("Pastel_de_Nata_Manteigaria") == true)

        val pasteisBelem = LisbonLandmarkCatalog.findLandmark("Fábrica dos Pastéis de Belém")
        assertNotNull(pasteisBelem)
        assertEquals("Belém", pasteisBelem?.district)
    }

    @Test
    fun testIsGenericOrStockPhotoDetection() {
        // Category drawables and empty URLs
        assertTrue(LocationPhotoResolver.isGenericOrStockPhoto(null))
        assertTrue(LocationPhotoResolver.isGenericOrStockPhoto(""))
        assertTrue(LocationPhotoResolver.isGenericOrStockPhoto("ic_cat_sights"))
        assertTrue(LocationPhotoResolver.isGenericOrStockPhoto("ic_cat_eats"))

        // Known repeated generic stock photos
        assertTrue(LocationPhotoResolver.isGenericOrStockPhoto("https://images.unsplash.com/photo-1509440159596-0249088772ff?w=600"))
        assertTrue(LocationPhotoResolver.isGenericOrStockPhoto("https://images.unsplash.com/photo-1513688275127-1be5a8359473?w=600"))

        // Authentic Wikimedia Commons and Wikipedia URLs should NOT be flagged as generic
        assertFalse(LocationPhotoResolver.isGenericOrStockPhoto("https://upload.wikimedia.org/wikipedia/commons/thumb/c/cd/MiradouroSantaLuzia.JPG/800px-MiradouroSantaLuzia.JPG"))
        assertFalse(LocationPhotoResolver.isGenericOrStockPhoto("https://commons.wikimedia.org/wiki/Special:FilePath/Torre_Belem.jpg?width=800"))
    }

    @Test
    fun testResolvePhotoSynchronousWithOsmTags() {
        // 1. Direct OSM image tag
        val osmTagsWithImage = mapOf("image" to "https://upload.wikimedia.org/wikipedia/commons/custom.jpg")
        val result1 = LocationPhotoResolver.resolvePhotoSynchronous("Random Node", "sights", osmTags = osmTagsWithImage)
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/custom.jpg", result1.url)
        assertTrue(result1.isVerifiedLandmark)

        // 2. OSM wikimedia_commons tag
        val osmTagsWithCommons = mapOf("wikimedia_commons" to "File:Miradouro de Santa Luzia.jpg")
        val result2 = LocationPhotoResolver.resolvePhotoSynchronous("Local View", "sights", osmTags = osmTagsWithCommons)
        assertTrue(result2.url.contains("Special:FilePath/Miradouro_de_Santa_Luzia.jpg"))
        assertTrue(result2.isVerifiedLandmark)
    }

    @Test
    fun testResolvePhotoSynchronousCatalogAndFallbacks() {
        // Landmark match
        val resultLandmark = LocationPhotoResolver.resolvePhotoSynchronous(
            title = "Miradouro da Senhora do Monte Highest Overlook",
            category = "sights"
        )
        assertTrue(resultLandmark.isVerifiedLandmark)
        assertTrue(resultLandmark.url.contains("Miradouro_da_Senhora_do_Monte"))

        // Keyword fallback (artisan bakery)
        val resultBakery = LocationPhotoResolver.resolvePhotoSynchronous(
            title = "Padaria Portuguesa Baixa",
            category = "eats"
        )
        assertFalse(resultBakery.isVerifiedLandmark)
        assertTrue(resultBakery.url.contains("Pastel_de_nata_em_Lisboa.jpg"))

        // District fallback
        val resultDistrict = LocationPhotoResolver.resolvePhotoSynchronous(
            title = "Boutique Atelier",
            category = "sights",
            districtOrLocation = "Alfama"
        )
        assertTrue(resultDistrict.url.contains("Alfama_Lisbon_street_scene.jpg"))
    }

    @Test
    fun testWikimediaCommonsTagParsing() {
        val wikiService = WikipediaPhotoService()
        val url = wikiService.resolveWikimediaCommonsTag("File:Castelo de São Jorge.jpg", targetWidth = 800)
        assertNotNull(url)
        assertTrue(url!!.startsWith("https://commons.wikimedia.org/wiki/Special:FilePath/"))
        assertTrue(url.contains("width=800"))
    }

    @Test
    fun testImageResolverModelResolution() {
        // Given a spot with placeholder "ic_cat_sights" for Miradouro de Santa Luzia
        val model = ImageResolver.resolveImageModel(
            imageUrl = "ic_cat_sights",
            category = "sights",
            title = "Miradouro de Santa Luzia"
        )

        // Should intercept and return authentic Wikimedia URL instead of vector drawable
        assertTrue(model is String)
        val urlString = model as String
        assertTrue(urlString.contains("MiradouroSantaLuzia.JPG"))

        // Attribution
        val attr = ImageResolver.getAttribution(
            title = "Miradouro de Santa Luzia",
            imageUrl = urlString,
            category = "sights"
        )
        assertTrue(attr.contains("Wikimedia Commons"))
    }
}
