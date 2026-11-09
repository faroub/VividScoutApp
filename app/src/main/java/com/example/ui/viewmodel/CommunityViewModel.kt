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
    val postTipSuccessMessage: String? = null,
    val reportMessage: String? = null
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

    private val _reportMessage = MutableStateFlow<String?>(null)
    val reportMessage: StateFlow<String?> = _reportMessage.asStateFlow()

    // Nested pair-reduction combine: Kotlin's typed combine only goes up to 5 flows,
    // and the Array<Any?> vararg overload would force unchecked casts.
    val uiState: StateFlow<CommunityUiState> = combine(
        combine(allCommunityTips, allStatusUpdates) { tips, statuses -> tips to statuses },
        combine(_meshSyncPhase, _lastMeshSyncResult) { phase, result -> phase to result },
        combine(_postTipSuccessMessage, _reportMessage) { msg, reportMsg -> msg to reportMsg }
    ) { (tips, statuses), (phase, result), msgAndReport ->
        val (msg, reportMsg) = msgAndReport
        CommunityUiState(
            tips = tips,
            statusUpdates = statuses,
            syncPhase = phase,
            lastSyncResult = result,
            postTipSuccessMessage = msg,
            reportMessage = reportMsg
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

    /**
     * Reports a community tip for moderation through the trusted reportCommunityTip
     * Cloud Function. The message reflects the real backend outcome: definitive
     * rejections (duplicate report, self-report, hidden tip, hourly limit) surface
     * the backend's explanation; a successful report is never announced as a takedown
     * — it is queued for moderator review.
     */
    fun reportTip(tipId: String) {
        viewModelScope.launch {
            val currentUid = try {
                FirebaseAuth.getInstance().currentUser?.uid ?: ""
            } catch (e: Exception) {
                ""
            }
            if (currentUid.isNotBlank()) {
                val result = communityRepository.reportTip(tipId, currentUid)
                _reportMessage.value = result.fold(
                    onSuccess = { "Thanks — this tip has been reported and will be reviewed by our moderation team." },
                    onFailure = { it.message ?: "Report failed. Please try again later." }
                )
                delay(3000)
                _reportMessage.value = null
            }
        }
    }

    fun clearPostTipSuccessMessage() {
        _postTipSuccessMessage.value = null
    }
}
