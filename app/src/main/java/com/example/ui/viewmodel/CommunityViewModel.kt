package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CommunityTipEntity
import com.example.data.p2p.MeshSyncPhase
import com.example.data.p2p.PeerMeshSyncResult
import com.example.data.repository.CommunityTipRepository
import com.example.data.sync.CommunitySyncOutcome
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.util.SafeLog

data class CommunityUiState(
    val tips: List<CommunityTipEntity> = emptyList(),
    val statusUpdates: List<CommunityTipEntity> = emptyList(),
    val syncPhase: MeshSyncPhase = MeshSyncPhase.IDLE,
    val lastSyncResult: PeerMeshSyncResult? = null,
    val postTipSuccessMessage: String? = null
)

class CommunityViewModel(
    private val communityRepository: CommunityTipRepository
) : ViewModel() {

    val allCommunityTips: StateFlow<List<CommunityTipEntity>> = communityRepository.allTipsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allStatusUpdates: StateFlow<List<CommunityTipEntity>> = communityRepository.allStatusUpdatesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _meshSyncPhase = MutableStateFlow(MeshSyncPhase.IDLE)
    val meshSyncPhase: StateFlow<MeshSyncPhase> = _meshSyncPhase.asStateFlow()

    private val _lastMeshSyncResult = MutableStateFlow<PeerMeshSyncResult?>(null)
    val lastMeshSyncResult: StateFlow<PeerMeshSyncResult?> = _lastMeshSyncResult.asStateFlow()

    private val _postTipSuccessMessage = MutableStateFlow<String?>(null)
    val postTipSuccessMessage: StateFlow<String?> = _postTipSuccessMessage.asStateFlow()

    val uiState: StateFlow<CommunityUiState> = combine(
        allCommunityTips,
        allStatusUpdates,
        _meshSyncPhase,
        _lastMeshSyncResult,
        _postTipSuccessMessage
    ) { tips, statuses, phase, result, msg ->
        CommunityUiState(
            tips = tips,
            statusUpdates = statuses,
            syncPhase = phase,
            lastSyncResult = result,
            postTipSuccessMessage = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CommunityUiState())

    init {
        viewModelScope.launch {
            communityRepository.ensureInitialTipsSeeded()
        }
    }

    fun triggerSync() {
        if (_meshSyncPhase.value != MeshSyncPhase.IDLE) return
        viewModelScope.launch {
            val result = communityRepository.syncWithNearbyPeers { phase ->
                _meshSyncPhase.value = phase
            }
            _lastMeshSyncResult.value = result
            try {
                communityRepository.syncWithCloud()
            } catch (e: Exception) {
                SafeLog.w("CommunityVM", "Cloud sync delayed: ${e.message}")
            }
        }
    }

    fun postTip(
        spotId: String,
        spotTitle: String,
        content: String,
        isStatusUpdate: Boolean = false,
        statusCategory: String = "INSIGHT"
    ) {
        viewModelScope.launch {
            communityRepository.postTip(
                spotId = spotId,
                spotTitle = spotTitle,
                content = content,
                isStatusUpdate = isStatusUpdate,
                statusCategory = statusCategory
            ) { outcome ->
                // The message reflects the real outcome of the cloud post: the daily
                // community limit is surfaced explicitly, and a pending local-only post
                // is never announced as already shared with the community.
                _postTipSuccessMessage.value = when (outcome) {
                    CommunitySyncOutcome.SYNCED ->
                        "Tip shared with the global scout community via cloud sync!"
                    CommunitySyncOutcome.RATE_LIMITED ->
                        "Daily sharing limit reached (20 tips per 24 hours). Your note is saved locally and will share when the limit resets."
                    CommunitySyncOutcome.PENDING ->
                        "Tip saved locally — it will share with the community on the next sync."
                }
            }
            delay(3000)
            _postTipSuccessMessage.value = null
        }
    }

    fun upvoteTip(tipId: String) {
        viewModelScope.launch {
            val currentUid = try {
                FirebaseAuth.getInstance().currentUser?.uid ?: ""
            } catch (e: Exception) {
                ""
            }
            if (currentUid.isNotBlank()) {
                communityRepository.upvoteTip(tipId, currentUid)
            }
        }
    }

    fun clearPostTipSuccessMessage() {
        _postTipSuccessMessage.value = null
    }
}
