package com.example.data.offline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineMapManagerTest {

    private lateinit var context: Context
    private lateinit var manager: OfflineMapManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        // Clean up offline_map_packs directory before tests
        val dir = File(context.filesDir, "offline_map_packs")
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        manager = OfflineMapManager(context)
    }

    @Test
    fun initialState_containsPresetPacks() {
        val packs = manager.packsState.value
        assertTrue(packs.isNotEmpty())
        assertTrue(packs.any { it.id == "dresden_saxony" })
        assertTrue(packs.any { it.id == "lisbon_cascais" })
    }

    @Test
    fun downloadPack_savesFileToFilesDir_andUpdatesPackState() = runBlocking {
        manager.downloadPack("dresden_saxony")

        val packFile = File(context.filesDir, "offline_map_packs/dresden_saxony.json")
        assertTrue("Pack JSON file should exist in internal storage", packFile.exists())
        assertTrue("Pack file size should be greater than 0", packFile.length() > 0)

        val updatedPacks = manager.packsState.value
        val dresdenPack = updatedPacks.find { it.id == "dresden_saxony" }
        assertTrue("Pack state should be set to downloaded", dresdenPack?.isDownloaded == true)
        assertTrue("Total storage bytes should be > 0", manager.totalStorageBytes.value > 0)
    }

    @Test
    fun deletePack_removesFileFromFilesDir_andResetsState() = runBlocking {
        manager.downloadPack("dresden_saxony")
        var packFile = File(context.filesDir, "offline_map_packs/dresden_saxony.json")
        assertTrue(packFile.exists())

        manager.deletePack("dresden_saxony")

        packFile = File(context.filesDir, "offline_map_packs/dresden_saxony.json")
        assertFalse("Pack JSON file should be removed after deletion", packFile.exists())

        val updatedPacks = manager.packsState.value
        val dresdenPack = updatedPacks.find { it.id == "dresden_saxony" }
        assertFalse("Pack state should no longer be marked downloaded", dresdenPack?.isDownloaded == true)
    }
}
