package com.example.ui.permissions

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationPermissionsTest {

    @Test
    fun nextAction_permissionAlreadyGranted_returnsRefresh() {
        val result = LocationPermissionDecisions.nextAction(
            hasLocationPermission = true,
            locationDeniedCount = 0
        )
        assertEquals(
            "Granted permission should refresh state instead of prompting the user again",
            LocationPermissionAction.Refresh,
            result
        )
    }

    @Test
    fun nextAction_notGranted_andFewerThanTwoDenials_returnsShowRationale() {
        val result = LocationPermissionDecisions.nextAction(
            hasLocationPermission = false,
            locationDeniedCount = 1
        )
        assertEquals(
            "First denial should still show the rationale before the next system request",
            LocationPermissionAction.ShowRationale,
            result
        )
    }

    @Test
    fun nextAction_notGranted_andTwoOrMoreDenials_returnsShowSettings() {
        val result = LocationPermissionDecisions.nextAction(
            hasLocationPermission = false,
            locationDeniedCount = 2
        )
        assertEquals(
            "After two denials the system dialog no longer helps, so offer app settings",
            LocationPermissionAction.ShowSettings,
            result
        )
    }

    @Test
    fun nextAction_notGranted_andNeverDenied_returnsShowRationale() {
        val result = LocationPermissionDecisions.nextAction(
            hasLocationPermission = false,
            locationDeniedCount = 0
        )
        assertEquals(LocationPermissionAction.ShowRationale, result)
    }

    @Test
    fun nextAction_grantedIgnoresDeniedCount_returnsRefresh() {
        val result = LocationPermissionDecisions.nextAction(
            hasLocationPermission = true,
            locationDeniedCount = 5
        )
        assertEquals(
            "Granted permission must win over a stale denial count",
            LocationPermissionAction.Refresh,
            result
        )
    }
}
