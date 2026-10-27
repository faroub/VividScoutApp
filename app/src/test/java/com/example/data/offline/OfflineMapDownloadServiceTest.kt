package com.example.data.offline

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineMapDownloadServiceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
    }

    @Test
    fun service_createsNotificationChannel_onInit() {
        val controller = Robolectric.buildService(OfflineMapDownloadService::class.java)
        val service = controller.create().get()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(OfflineMapDownloadService.CHANNEL_ID)
            assertNotNull("Offline Map Downloads notification channel should be created", channel)
            assertTrue("Channel name should be 'Offline Map Downloads'", channel.name == "Offline Map Downloads")
        }
        controller.destroy()
    }
}
