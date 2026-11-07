package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.example.data.repository.OlderPhotosPage
import com.example.data.repository.PhotoPageCursor
import com.example.data.repository.SpotPhotoData
import com.example.data.repository.SpotPhotoRepository
import com.example.data.repository.SpotPhotosState
import kotlinx.coroutines.flow.Flow

/**
 * Owner of the spot photo workflow: real photo uploads originating from the Android
 * Photo Picker, atomic photo reports addressed by real Firestore document IDs, and the
 * live stream of a spot's photos. All operations return their full result so the UI can
 * distinguish success, failure, and duplicate-report states, and the photo stream keeps
 * loaded data, an empty result, and listener failure distinct — without ever inventing
 * outcomes.
 */
class SpotPhotoViewModel(
    private val photoRepository: SpotPhotoRepository
) : ViewModel() {

    suspend fun uploadSpotPhoto(
        context: Context,
        spotId: String,
        uri: Uri
    ): Result<SpotPhotoData> = photoRepository.uploadPhoto(context = context, spotId = spotId, uri = uri)

    suspend fun reportSpotPhoto(photoId: String): Result<Int> =
        photoRepository.reportPhoto(photoId)

    fun photosForSpotFlow(spotId: String): Flow<SpotPhotosState> =
        photoRepository.photosForSpotFlow(spotId)

    /**
     * Loads the next older-photos page for [spotId], continuing after [cursor] (the
     * cursor of the live page for the first load, then the last page's cursor). The
     * page's [OlderPhotosPage.nextCursor] tells the UI whether more history may exist,
     * distinguishing a successful empty short page from a query failure.
     */
    suspend fun loadOlderPhotos(spotId: String, cursor: PhotoPageCursor?): Result<OlderPhotosPage> =
        photoRepository.getOlderPhotos(spotId = spotId, cursor = cursor)
}
