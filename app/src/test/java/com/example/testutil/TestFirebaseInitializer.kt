package com.example.testutil

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

object TestFirebaseInitializer {
    private const val TEST_API_KEY = "AIzaSyTestApiKeyForRobolectric12345"
    private const val TEST_APP_ID = "1:1234567890:android:abcdef123456"
    private const val TEST_PROJECT_ID = "vividscout-test"

    fun initTestFirebase(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApplicationId(TEST_APP_ID)
                .setApiKey(TEST_API_KEY)
                .setProjectId(TEST_PROJECT_ID)
                .build()
            FirebaseApp.initializeApp(context, options)
        }
    }
}
