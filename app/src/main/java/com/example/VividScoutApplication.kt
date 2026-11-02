package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.location.DefaultLocationConfig
import com.example.di.AppContainer
import com.example.di.DefaultAppContainer
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class VividScoutApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        DefaultLocationConfig.initFromPreferences(this)
        container = DefaultAppContainer(this)

        val applicationScope = CoroutineScope(Dispatchers.IO)

        applicationScope.launch {
            try {
                if (FirebaseApp.getApps(this@VividScoutApplication).isEmpty()) {
                    FirebaseApp.initializeApp(this@VividScoutApplication)
                }
                val auth = FirebaseAuth.getInstance()
                if (auth.currentUser == null) {
                    auth.signInAnonymously()
                }
            } catch (e: Exception) {
                // Background auth initialization handled gracefully
            }
        }

        applicationScope.launch {
            container.networkMonitor.onReconnected.collect {
                android.util.Log.i("VividScoutApplication", "Network reconnection detected! Triggering background photo re-sync...")
                container.gemRepository.triggerPhotoMetadataReSync()
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", "VividScoutApp/1.0 (Android; Location Photo Resolver; contact: scout@example.com)")
                    .header("Accept", "image/jpeg,image/png,image/webp,image/svg+xml,*/*;q=0.8")
                    .build()
                chain.proceed(requestWithUserAgent)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024L * 1024L) // 100 MB persistent disk cache for 1200px HD photos
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }
}
