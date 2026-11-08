package com.example.ui.dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.VividScoutApplication
import com.example.data.local.ScoutSpotEntity
import com.example.data.repository.OlderPhotosPage
import com.example.data.repository.PhotoModerationRepository
import com.example.data.repository.PhotoPageCursor
import com.example.data.repository.SpotPhotoData
import com.example.data.repository.SpotPhotosState
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.Secondary
import com.example.ui.theme.SurfaceContainerLow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun SpotDetailPhotosSection(
    spot: ScoutSpotEntity,
    onUploadPhoto: (suspend (spotId: String, uri: Uri) -> Result<SpotPhotoData>)? = null,
    onReportPhoto: (suspend (photoId: String) -> Result<Int>)? = null,
    onLoadOlderPhotos: (suspend (spotId: String, cursor: PhotoPageCursor?) -> Result<OlderPhotosPage>)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isUploadingPhoto by remember { mutableStateOf(false) }
    var uploadStatusMessage by remember { mutableStateOf<String?>(null) }
    var reportStatusMessage by remember { mutableStateOf<String?>(null) }

    val appContainer = (context.applicationContext as? VividScoutApplication)?.container
    // The photo stream distinguishes loaded photos, an empty result, and listener
    // failure (e.g. a missing Firestore index) — a failed listener must never render
    // as an empty photo grid as if the spot simply had no photos.
    val spotPhotosState by remember(spot.id) {
        appContainer?.photoModerationRepository?.photosForSpotFlow(spot.id)
            ?: flowOf(SpotPhotosState.Loaded(emptyList()))
    }.collectAsState(initial = SpotPhotosState.Loading)

    // The live stream is bounded to the newest PHOTOS_PAGE_SIZE photos; older photos
    // are paged in on demand. olderPhotos keeps the raw fetched page (hidden photos
    // included, same as the live page) so the paging cursor uses the true oldest
    // fetched document; display filters duplicates and moderation-hidden photos.
    // Pagination completion is tracked EXPLICITLY: a page shorter than the page bound
    // (including an empty page) removes the load-more button instead of relying on the
    // accumulated list being non-empty — an empty first page must not re-show the
    // button forever.
    var olderPhotos by remember(spot.id) { mutableStateOf<List<SpotPhotoData>>(emptyList()) }
    var isLoadingOlderPhotos by remember(spot.id) { mutableStateOf(false) }
    var hasMoreOlderPhotos by remember(spot.id) { mutableStateOf<Boolean?>(null) }
    var olderPhotosCursor by remember(spot.id) { mutableStateOf<PhotoPageCursor?>(null) }
    var olderPhotosStatusMessage by remember(spot.id) { mutableStateOf<String?>(null) }

    val livePhotos = (spotPhotosState as? SpotPhotosState.Loaded)?.photos ?: emptyList()
    // The cursor of the live page's oldest document seeds the first older-photos query.
    val livePageCursor = (spotPhotosState as? SpotPhotosState.Loaded)?.pageCursor
    val displayedPhotos = livePhotos.asSequence()
        .filter { !it.isHidden }
        .plus(olderPhotos.asSequence().filter { older ->
            !older.isHidden && livePhotos.none { it.id == older.id }
        })
        .toList()
    val photosErrorMessage = (spotPhotosState as? SpotPhotosState.Error)?.let {
        "Community photos are unavailable${it.throwable.message?.let { m -> ": $m" } ?: "."}"
    }
    val canLoadMorePhotos = !isLoadingOlderPhotos && when {
        // A live page smaller than the page size means the spot's whole (visible)
        // history fits in the current page — there is nothing older to load.
        livePhotos.size < PhotoModerationRepository.PHOTOS_PAGE_SIZE -> false
        // Never paged yet, but the live page is full: more may exist.
        hasMoreOlderPhotos == null -> true
        else -> hasMoreOlderPhotos == true
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { pickedUri: Uri? ->
        // Cancellation (null Uri) must never be reported as an action or success.
        if (pickedUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isUploadingPhoto = true
            uploadStatusMessage = null
            val uploadResult = onUploadPhoto?.invoke(spot.id, pickedUri)
            isUploadingPhoto = false
            uploadStatusMessage = when {
                uploadResult == null -> "Photo upload unavailable."
                uploadResult.isSuccess -> "Photo uploaded successfully!"
                else -> uploadResult.exceptionOrNull()?.message
                    ?: "Photo upload failed. Please try again."
            }
        }
    }

    // Community Photos (Upload & Moderation Report)
    Surface(
        color = SurfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "COMMUNITY PHOTOS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    ),
                    color = BrandTeal
                )

                if (photosErrorMessage != null) {
                    Text(
                        text = photosErrorMessage!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandCoral,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                if (onUploadPhoto != null) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        enabled = !isUploadingPhoto,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("upload_spot_photo_btn")
                    ) {
                        if (isUploadingPhoto) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Upload Photo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (displayedPhotos.isEmpty()) {
                Text(
                    text = if (onUploadPhoto != null) {
                        "No community photos yet. Share the first one!"
                    } else {
                        "No community photos yet."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                displayedPhotos.forEach { photo ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .padding(vertical = 4.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(photo.photoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Community photo for ${spot.title}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (onReportPhoto != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.55f))
                            ) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            reportStatusMessage = null
                                            val reportResult = onReportPhoto.invoke(photo.id)
                                            reportStatusMessage = if (reportResult.isSuccess) {
                                                "Report submitted. Thank you for keeping VividScout safe!"
                                            } else {
                                                reportResult.exceptionOrNull()?.message
                                                    ?: "Report failed. Please try again."
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag("report_photo_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Flag,
                                        contentDescription = "Report this photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (uploadStatusMessage != null) {
                Text(
                    text = uploadStatusMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = Secondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (canLoadMorePhotos && onLoadOlderPhotos != null) {
                OutlinedButton(
                    onClick = {
                        if (isLoadingOlderPhotos) return@OutlinedButton
                        scope.launch {
                            isLoadingOlderPhotos = true
                            olderPhotosStatusMessage = null
                            // Continue strictly after the oldest document
                            // fetched so far — the cursor carries the full
                            // (timestamp, document id) ordering position, so
                            // equal timestamps neither skip nor repeat photos.
                            val result = onLoadOlderPhotos.invoke(
                                spot.id,
                                olderPhotosCursor ?: livePageCursor
                            )
                            isLoadingOlderPhotos = false
                            if (result == null) {
                                olderPhotosStatusMessage = "Loading older photos is unavailable."
                                return@launch
                            }
                            if (result.isFailure) {
                                olderPhotosStatusMessage = result.exceptionOrNull()?.message
                                    ?: "Could not load older photos. Please try again."
                                return@launch
                            }
                            val page = result.getOrThrow()
                            // A page shorter than the page bound (empty pages
                            // included) marks the history exhausted; a full page
                            // yields its last document's cursor for the next one.
                            hasMoreOlderPhotos = page.nextCursor != null
                            olderPhotosCursor = page.nextCursor
                            // Append without duplicating photos already present
                            // in the live page or an earlier older page (a live
                            // photo arriving mid-pagination).
                            olderPhotos = olderPhotos + page.photos.filter { p ->
                                livePhotos.none { it.id == p.id } && olderPhotos.none { it.id == p.id }
                            }
                        }
                    },
                    enabled = !isLoadingOlderPhotos,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .testTag("load_older_photos_btn")
                ) {
                    if (isLoadingOlderPhotos) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = "Show older photos",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (olderPhotosStatusMessage != null) {
                Text(
                    text = olderPhotosStatusMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandCoral,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (reportStatusMessage != null) {
                Text(
                    text = reportStatusMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandCoral,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
