package com.example

import android.app.Application
import com.example.data.location.DefaultLocationConfig
import com.example.di.AppContainer
import com.example.di.DefaultAppContainer
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VividScoutApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        DefaultLocationConfig.initFromPreferences(this)
        container = DefaultAppContainer(this)

        CoroutineScope(Dispatchers.IO).launch {
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
    }
}
