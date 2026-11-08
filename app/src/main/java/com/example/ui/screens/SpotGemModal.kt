package com.example.ui.screens

import android.content.Intent
import android.location.Location
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.SurfaceContainerLowest

@Composable
fun SpotGemModal(
    onDismiss: () -> Unit,
    onSubmit: (name: String, category: String, photoUri: String?, notes: String) -> Unit,
    draftCount: Int = 1,
    userLocation: Location? = null,
    userCoordinates: Pair<Double, Double> = Pair(38.7112, -9.1423),
    isUsingGpsLocation: Boolean = true,
    selectedGlobalCity: String? = null
) {
    val context = LocalContext.current

    var spotName by remember { mutableStateOf("Miradouro Secreto do Beco do Forno") }
    var spotNotes by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("eats") }
    var selectedPhotoUri: Uri? by remember { mutableStateOf(null) }
    val photoSelected = selectedPhotoUri != null
    var isSubmittedSuccess by remember { mutableStateOf(false) }

    // Real photo picker: the selected image is stored as its content:// reference and
    // uploaded through the trusted finalizePhotoUpload pipeline when the draft syncs
    // (FirestoreSyncManager). A persistable read grant is requested so the reference
    // survives process death while the draft waits offline in the retry queue.
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        selectedPhotoUri = uri
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { onDismiss() }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
                    .shadow(elevation = 24.dp, shape = RoundedCornerShape(24.dp))
                    .testTag("spot_gem_modal_dialog"),
                shape = RoundedCornerShape(24.dp),
                color = SurfaceContainerLowest
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SpotGemModalHeader(onDismiss = onDismiss)
                    SpotGemNameAndCategoryFields(
                        spotName = spotName,
                        onSpotNameChange = { spotName = it },
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it }
                    )
                    SpotGemPhotoAndLocationSection(
                        photoSelected = photoSelected,
                        onPickPhoto = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        isUsingGpsLocation = isUsingGpsLocation,
                        userLocation = userLocation,
                        userCoordinates = userCoordinates,
                        selectedGlobalCity = selectedGlobalCity
                    )
                    SpotGemNotesAndSubmitSection(
                        spotNotes = spotNotes,
                        onSpotNotesChange = { spotNotes = it },
                        isSubmittedSuccess = isSubmittedSuccess,
                        onSubmitClick = {
                            isSubmittedSuccess = true
                            onSubmit(
                                spotName,
                                selectedCategory,
                                if (photoSelected) selectedPhotoUri?.toString() else null,
                                spotNotes
                            )
                        }
                    )
                }
            }
        }
    }
}
