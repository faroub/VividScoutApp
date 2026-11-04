package com.example.ui.viewmodel

import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.example.data.location.LocationTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeLocationTracker: FakeLocationTracker
    private lateinit var viewModel: LocationViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeLocationTracker = FakeLocationTracker()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun manualCitySelection_withoutPermission_transitionsStartupToExplore() = runTest {
        fakeLocationTracker.hasPermissionFlow.value = false
        viewModel = LocationViewModel(fakeLocationTracker)
        backgroundScope.launch { viewModel.userCoordinates.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Initial State without permission
        assertFalse("Permission must be false initially", viewModel.hasLocationPermission.value)
        assertEquals(GpsAcquisitionStatus.PermissionRequired, viewModel.gpsStatus.value)

        // 2. Select Paris manually
        viewModel.setManualCoordinates(48.8566, 2.3522, "Paris, France")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("isUsingGpsLocation must be false in manual mode", viewModel.isUsingGpsLocation.value)
        val status = viewModel.gpsStatus.value
        assertTrue("Manual selection MUST lock status to transition out of startup screen", status is GpsAcquisitionStatus.Locked)
        val lockedStatus = status as GpsAcquisitionStatus.Locked
        assertEquals("Paris, France", lockedStatus.cityName)
        assertEquals(Pair(48.8566, 2.3522), viewModel.userCoordinates.value)
    }

    @Test
    fun manualCitySelection_afterGpsTimeout_unlocksStartupScreen() = runTest {
        fakeLocationTracker.hasPermissionFlow.value = true
        fakeLocationTracker.isGpsEnabled = true
        viewModel = LocationViewModel(fakeLocationTracker)
        backgroundScope.launch { viewModel.userCoordinates.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        // Initial State acquiring lock
        assertEquals(GpsAcquisitionStatus.AcquiringLock, viewModel.gpsStatus.value)

        // Manual city override (Dresden, Germany)
        viewModel.setManualCoordinates(51.0504, 13.7373, "Dresden, Germany")
        testDispatcher.scheduler.advanceUntilIdle()

        val status = viewModel.gpsStatus.value
        assertTrue("Manual city selection must lock status", status is GpsAcquisitionStatus.Locked)
        val lockedStatus = status as GpsAcquisitionStatus.Locked
        assertEquals("Dresden, Germany", lockedStatus.cityName)
        assertEquals(Pair(51.0504, 13.7373), viewModel.userCoordinates.value)
    }

    @Test
    fun returningToGpsMode_resumesGpsTracking() = runTest {
        fakeLocationTracker.hasPermissionFlow.value = true
        fakeLocationTracker.isGpsEnabled = true
        viewModel = LocationViewModel(fakeLocationTracker)
        backgroundScope.launch { viewModel.userCoordinates.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        // Set manual coordinates first
        viewModel.setManualCoordinates(48.8566, 2.3522, "Paris, France")
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.isUsingGpsLocation.value)

        // Return to GPS Mode
        viewModel.enableGpsMode()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("isUsingGpsLocation must be re-enabled", viewModel.isUsingGpsLocation.value)
        assertEquals(GpsAcquisitionStatus.AcquiringLock, viewModel.gpsStatus.value)
        assertTrue(fakeLocationTracker.isTrackingStarted)
    }

    @Test
    fun permissionDeniedRecoveryPath_incrementsDeniedCountAndOpensDialogs() = runTest {
        fakeLocationTracker.hasPermissionFlow.value = false
        viewModel = LocationViewModel(fakeLocationTracker)
        backgroundScope.launch { viewModel.userCoordinates.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        // First denial -> Rationale dialog
        viewModel.onPermissionResult(fineGranted = false, coarseGranted = false)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.locationDeniedCount.value)
        assertTrue(viewModel.showLocationRationaleDialog.value)

        // Second denial -> App Settings dialog
        viewModel.onPermissionResult(fineGranted = false, coarseGranted = false)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.locationDeniedCount.value)
        assertTrue(viewModel.showLocationSettingsDialog.value)
    }

    @Test
    fun gpsDisabledRecoveryPath_presentsGpsDisabledStatus() = runTest {
        fakeLocationTracker.hasPermissionFlow.value = true
        fakeLocationTracker.isGpsEnabled = false // GPS Provider turned off in Android Settings
        viewModel = LocationViewModel(fakeLocationTracker)
        backgroundScope.launch { viewModel.userCoordinates.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(GpsAcquisitionStatus.GpsDisabled, viewModel.gpsStatus.value)
    }

    @Test
    fun gpsPermissionRestoredFromSettings_refreshesAndAcquiresLock() = runTest {
        fakeLocationTracker.hasPermissionFlow.value = false
        viewModel = LocationViewModel(fakeLocationTracker)
        backgroundScope.launch { viewModel.userCoordinates.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(GpsAcquisitionStatus.PermissionRequired, viewModel.gpsStatus.value)

        // Simulate user enabling permission in Android Settings
        fakeLocationTracker.hasPermissionFlow.value = true
        fakeLocationTracker.isGpsEnabled = true
        viewModel.refreshLocationPermissions()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.hasLocationPermission.value)
        assertEquals(GpsAcquisitionStatus.AcquiringLock, viewModel.gpsStatus.value)
    }

    // Fake LocationTracker
    class FakeLocationTracker(context: Context = ApplicationProvider.getApplicationContext()) : LocationTracker(context) {
        val hasPermissionFlow = MutableStateFlow(false)
        override val hasPermission: StateFlow<Boolean> = hasPermissionFlow

        val isPreciseFlow = MutableStateFlow(false)
        override val isPrecise: StateFlow<Boolean> = isPreciseFlow

        private val currentLocationFlow = MutableStateFlow<Location?>(null)
        override val currentLocation: StateFlow<Location?> = currentLocationFlow

        var isGpsEnabled = true
        var isTrackingStarted = false

        override fun hasFinePermission(): Boolean = isPreciseFlow.value
        override fun hasCoarsePermission(): Boolean = hasPermissionFlow.value
        override fun isGpsProviderEnabled(): Boolean = isGpsEnabled

        override fun startTracking() {
            isTrackingStarted = true
        }

        override fun stopTracking() {
            isTrackingStarted = false
        }

        override fun onPermissionResult(fineGranted: Boolean, coarseGranted: Boolean) {
            hasPermissionFlow.value = fineGranted || coarseGranted
            isPreciseFlow.value = fineGranted
        }
    }
}
