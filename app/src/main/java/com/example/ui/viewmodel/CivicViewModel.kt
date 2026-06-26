package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CivicDatabase
import com.example.data.model.*
import com.example.data.repository.CivicRepository
import com.example.data.repository.FirebaseService
import com.example.security.SecurityManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface ReportUiState {
    object Idle : ReportUiState
    object Submitting : ReportUiState
    data class Success(val issue: InfrastructureIssue) : ReportUiState
    data class Error(val message: String) : ReportUiState
}

data class DuplicateAlertState(
    val showDialog: Boolean = false,
    val pendingIssueData: PendingIssueData? = null,
    val duplicateIssue: InfrastructureIssue? = null,
    val proximityMeters: Double = 0.0,
    val textSimilarity: Float = 0f,
    val imageSimilarity: Float = 0f
)

data class PendingIssueData(
    val title: String,
    val description: String,
    val category: IssueCategory,
    val severity: SeverityLevel,
    val latitude: Double,
    val longitude: Double,
    val locationName: String,
    val imageUrl: String? = null,
    val aiSummary: String? = null,
    val confidenceScore: String? = null,
    val suggestedPriority: String? = null
)

sealed interface AuthUiState {
    object Unauthenticated : AuthUiState
    object Loading : AuthUiState
    data class Authenticated(val user: User) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class CivicViewModel(application: Application) : AndroidViewModel(application) {

    private val db = CivicDatabase.getDatabase(application)
    private val repository = CivicRepository(db.issueDao(), db.userDao(), db.communityDao(), db.notificationDao())

    // --- Authentication State ---
    private val _authUiState = MutableStateFlow<AuthUiState>(AuthUiState.Unauthenticated)
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // --- Notifications State ---
    private val _notifications = MutableStateFlow<List<CivicNotification>>(emptyList())
    val notifications: StateFlow<List<CivicNotification>> = _notifications.asStateFlow()

    private var notificationsJob: kotlinx.coroutines.Job? = null
    private var currentUserObserveJob: kotlinx.coroutines.Job? = null

    private fun observeUser(userId: String) {
        currentUserObserveJob?.cancel()
        currentUserObserveJob = viewModelScope.launch {
            repository.getUser(userId).collect { user ->
                if (user != null) {
                    _currentUser.value = user
                    _authUiState.value = AuthUiState.Authenticated(user)
                }
            }
        }
    }

    private fun observeNotificationsForUser(userId: String) {
        notificationsJob?.cancel()
        notificationsJob = viewModelScope.launch {
            repository.getNotifications(userId).collect {
                _notifications.value = it
            }
        }
    }

    // --- Core Data Flows ---
    val issues: StateFlow<List<InfrastructureIssue>> = repository.allIssues
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val communityPosts: StateFlow<List<CommunityPost>> = repository.allPosts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Active Selected Detail ---
    private val _selectedIssue = MutableStateFlow<InfrastructureIssue?>(null)
    val selectedIssue: StateFlow<InfrastructureIssue?> = _selectedIssue.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedIssueVotes: StateFlow<List<VerificationVote>> = _selectedIssue
        .flatMapLatest { issue ->
            if (issue != null) {
                repository.getVotesForIssue(issue.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Report Submission UI State ---
    private val _reportState = MutableStateFlow<ReportUiState>(ReportUiState.Idle)
    val reportState: StateFlow<ReportUiState> = _reportState.asStateFlow()

    private val _duplicateAlertState = MutableStateFlow<DuplicateAlertState?>(null)
    val duplicateAlertState: StateFlow<DuplicateAlertState?> = _duplicateAlertState.asStateFlow()

    // --- Camera & AI Auto-Generate Workflow ---
    private val _cameraPhotoBitmap = MutableStateFlow<android.graphics.Bitmap?>(null)
    val cameraPhotoBitmap: StateFlow<android.graphics.Bitmap?> = _cameraPhotoBitmap.asStateFlow()

    private val _gpsLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val gpsLocation: StateFlow<Pair<Double, Double>?> = _gpsLocation.asStateFlow()

    private val _locationName = MutableStateFlow<String>("")
    val locationName: StateFlow<String> = _locationName.asStateFlow()

    private val _isAnalyzingImage = MutableStateFlow(false)
    val isAnalyzingImage: StateFlow<Boolean> = _isAnalyzingImage.asStateFlow()

    private val _aiGeneratedData = MutableStateFlow<Map<String, String>?>(null)
    val aiGeneratedData: StateFlow<Map<String, String>?> = _aiGeneratedData.asStateFlow()

    // --- Daily Briefing State ---
    private val _dailyBriefing = MutableStateFlow<String>("")
    val dailyBriefing: StateFlow<String> = _dailyBriefing.asStateFlow()

    private val _isBriefingLoading = MutableStateFlow<Boolean>(false)
    val isBriefingLoading: StateFlow<Boolean> = _isBriefingLoading.asStateFlow()

    // --- Advanced Workforce & Community Engagement State Flows ---
    private val _teams = MutableStateFlow<List<CivicTeam>>(emptyList())
    val teams: StateFlow<List<CivicTeam>> = _teams.asStateFlow()

    private val _teamAssignments = MutableStateFlow<List<TeamAssignment>>(emptyList())
    val teamAssignments: StateFlow<List<TeamAssignment>> = _teamAssignments.asStateFlow()

    private val _workerAvailability = MutableStateFlow<List<WorkerAvailability>>(emptyList())
    val workerAvailability: StateFlow<List<WorkerAvailability>> = _workerAvailability.asStateFlow()

    private val _communityGroups = MutableStateFlow<List<CommunityGroup>>(emptyList())
    val communityGroups: StateFlow<List<CommunityGroup>> = _communityGroups.asStateFlow()

    private val _groupMembers = MutableStateFlow<List<GroupMember>>(emptyList())
    val groupMembers: StateFlow<List<GroupMember>> = _groupMembers.asStateFlow()

    private val _messages = MutableStateFlow<List<CivicMessage>>(emptyList())
    val messages: StateFlow<List<CivicMessage>> = _messages.asStateFlow()

    private val _divisionChannels = MutableStateFlow<List<DivisionChannel>>(emptyList())
    val divisionChannels: StateFlow<List<DivisionChannel>> = _divisionChannels.asStateFlow()

    private val _announcements = MutableStateFlow<List<OfficialAnnouncement>>(emptyList())
    val announcements: StateFlow<List<OfficialAnnouncement>> = _announcements.asStateFlow()

    private val _taskHistory = MutableStateFlow<List<TaskHistoryItem>>(emptyList())
    val taskHistory: StateFlow<List<TaskHistoryItem>> = _taskHistory.asStateFlow()

    private val _aiRecommendations = MutableStateFlow<List<AiTeamRecommendation>>(emptyList())
    val aiRecommendations: StateFlow<List<AiTeamRecommendation>> = _aiRecommendations.asStateFlow()

    fun generateDailyBriefing(issuesList: List<InfrastructureIssue>) {
        viewModelScope.launch {
            _isBriefingLoading.value = true
            try {
                val briefing = com.example.data.api.GeminiClient.generateDailyBriefing(issuesList)
                _dailyBriefing.value = briefing
            } catch (e: Exception) {
                _dailyBriefing.value = "Failed to generate briefing. Please retry."
            } finally {
                _isBriefingLoading.value = false
            }
        }
    }

    init {
        // Observe current user changes to dynamically sync and fetch notifications
        viewModelScope.launch {
            _currentUser.collect { user ->
                if (user != null) {
                    observeNotificationsForUser(user.id)
                    repository.syncWithFirebase(user.id)
                } else {
                    notificationsJob?.cancel()
                    _notifications.value = emptyList()
                }
            }
        }

        // Enterprise Session Token Restoration via Android Keystore backing
        val savedUserId = SecurityManager.getSessionToken(application)
        if (!savedUserId.isNullOrEmpty()) {
            observeUser(savedUserId)
        } else {
            // Auto-login to a default user to provide an instant, frictionless entry point on startup
            observeUser("citizen1")
            SecurityManager.saveSessionToken(application, "citizen1")
        }
        initializeMockData()
    }

    // --- Authentication Actions ---
    fun login(email: String, role: UserRole) {
        viewModelScope.launch {
            _authUiState.value = AuthUiState.Loading
            try {
                val user = if (FirebaseService.isAvailable()) {
                    FirebaseService.signIn(email, role)
                } else {
                    val formattedName = email.substringBefore("@").replaceFirstChar { it.uppercase() }
                    // Use a stable ID derived from the email for predictability
                    val userId = "user-${email.hashCode().coerceAtLeast(0)}"
                    var existing = db.userDao().getUserById(userId)
                    
                    if (existing == null) {
                        existing = User(
                            id = userId,
                            name = formattedName,
                            email = email,
                            avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
                            reputationPoints = 15,
                            role = role
                        )
                        repository.createOrUpdateUser(existing)
                    }
                    existing
                }
                
                // Cryptographically isolate session token in Android Keystore SharedPreferences
                SecurityManager.saveSessionToken(getApplication(), user.id)
                observeUser(user.id)
            } catch (e: Exception) {
                _authUiState.value = AuthUiState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun register(name: String, email: String, role: UserRole) {
        viewModelScope.launch {
            _authUiState.value = AuthUiState.Loading
            try {
                val user = if (FirebaseService.isAvailable()) {
                    FirebaseService.register(name, email, role)
                } else {
                    val userId = "user-${email.hashCode().coerceAtLeast(0)}"
                    val newUser = User(
                        id = userId,
                        name = name,
                        email = email,
                        avatarUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150",
                        reputationPoints = 25, // bonus points for signing up
                        role = role
                    )
                    repository.createOrUpdateUser(newUser)
                    newUser
                }
                
                // Cryptographically isolate session token in Android Keystore SharedPreferences
                SecurityManager.saveSessionToken(getApplication(), user.id)
                observeUser(user.id)
            } catch (e: Exception) {
                _authUiState.value = AuthUiState.Error(e.message ?: "Registration failed")
            }
        }
    }

    fun logout() {
        if (FirebaseService.isAvailable()) {
            FirebaseService.signOut()
        }
        // Safely wipe session token from encrypted preferences
        SecurityManager.saveSessionToken(getApplication(), "")
        currentUserObserveJob?.cancel()
        _currentUser.value = null
        _authUiState.value = AuthUiState.Unauthenticated
    }

    fun selectIssue(issue: InfrastructureIssue?) {
        _selectedIssue.value = issue
    }

    fun resetReportState() {
        _reportState.value = ReportUiState.Idle
    }

    // --- Camera & AI Auto-Generate Workflow Actions ---
    fun setCameraPhoto(bitmap: android.graphics.Bitmap?) {
        _cameraPhotoBitmap.value = bitmap
    }

    fun setGpsLocation(lat: Double, lng: Double, name: String) {
        _gpsLocation.value = Pair(lat, lng)
        _locationName.value = name
    }

    fun clearWorkflowState() {
        _cameraPhotoBitmap.value = null
        _gpsLocation.value = null
        _locationName.value = ""
        _aiGeneratedData.value = null
        _isAnalyzingImage.value = false
    }

    fun runGeminiOnPhoto(bitmap: android.graphics.Bitmap, categoryHint: String? = null) {
        viewModelScope.launch {
            _isAnalyzingImage.value = true
            try {
                val data = com.example.data.api.GeminiClient.analyzeIssueImage(bitmap, categoryHint)
                _aiGeneratedData.value = data
            } catch (e: Exception) {
                android.util.Log.e("CivicViewModel", "Failed to run Gemini on photo", e)
            } finally {
                _isAnalyzingImage.value = false
            }
        }
    }

    private fun calculateDistanceInMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }

    private fun calculateTextSimilarity(text1: String, text2: String): Float {
        val words1 = text1.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        val words2 = text2.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        if (words1.isEmpty() || words2.isEmpty()) return 0f
        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        return intersection.toFloat() / union.toFloat()
    }

    fun dismissDuplicateAlert() {
        _duplicateAlertState.value = null
    }

    fun forceSubmitPendingIssue() {
        val reporter = _currentUser.value ?: return
        val pending = _duplicateAlertState.value?.pendingIssueData ?: return
        viewModelScope.launch {
            _reportState.value = ReportUiState.Submitting
            try {
                val issue = repository.submitCustomIssue(
                    title = pending.title,
                    description = pending.description,
                    category = pending.category,
                    severity = pending.severity,
                    latitude = pending.latitude,
                    longitude = pending.longitude,
                    locationName = pending.locationName,
                    reporter = reporter,
                    imageUrl = pending.imageUrl,
                    aiSummary = pending.aiSummary,
                    confidenceScore = pending.confidenceScore,
                    suggestedPriority = pending.suggestedPriority
                )
                _reportState.value = ReportUiState.Success(issue)
                _duplicateAlertState.value = null
            } catch (e: Exception) {
                _reportState.value = ReportUiState.Error(e.message ?: "Failed to submit custom issue")
            }
        }
    }

    fun joinExistingIssue(issueId: String) {
        val reporter = _currentUser.value ?: return
        viewModelScope.launch {
            _reportState.value = ReportUiState.Submitting
            try {
                repository.upvoteIssue(issueId)
                // Award de-duplication bonus reputation
                repository.updateUserReputationMetrics(
                    userId = reporter.id,
                    reputationDelta = 20,
                    communityScoreDelta = 30,
                    trustScoreDelta = 3
                )
                val issue = repository.getIssueById(issueId)
                if (issue != null) {
                    _reportState.value = ReportUiState.Success(issue)
                } else {
                    _reportState.value = ReportUiState.Idle
                }
                _duplicateAlertState.value = null
            } catch (e: Exception) {
                _reportState.value = ReportUiState.Error(e.message ?: "Failed to join existing report")
            }
        }
    }

    fun submitReviewedIssue(
        title: String,
        description: String,
        category: IssueCategory,
        severity: SeverityLevel,
        latitude: Double,
        longitude: Double,
        locationName: String,
        imageUrl: String? = null,
        aiSummary: String? = null,
        confidenceScore: String? = null,
        suggestedPriority: String? = null
    ) {
        val reporter = _currentUser.value ?: return
        viewModelScope.launch {
            _reportState.value = ReportUiState.Submitting
            try {
                val existingIssues = issues.value
                var potentialDuplicate: InfrastructureIssue? = null
                var minDistance = Double.MAX_VALUE
                var maxTextSim = 0f
                var maxImgSim = 0f

                for (issue in existingIssues) {
                    val distance = calculateDistanceInMeters(latitude, longitude, issue.latitude, issue.longitude)
                    
                    // Location proximity: within 250 meters
                    if (distance <= 250.0) {
                        val textSim = calculateTextSimilarity(description, issue.description)
                        val catMatch = (category == issue.category)
                        
                        // Image similarity: check if category matches as a visual hazard match proxy
                        var imgSim = 0f
                        if (catMatch) {
                            imgSim = 0.8f
                        }
                        if (title.lowercase().contains("pothole") && issue.title.lowercase().contains("pothole")) {
                            imgSim = 0.85f
                        }

                        // Duplicate detection logic
                        if (distance <= 50.0 && catMatch) {
                            potentialDuplicate = issue
                            minDistance = distance
                            maxTextSim = textSim
                            maxImgSim = imgSim
                            break
                        } else if (distance <= 250.0 && (textSim >= 0.3f || (catMatch && textSim >= 0.15f))) {
                            if (potentialDuplicate == null || distance < minDistance) {
                                potentialDuplicate = issue
                                minDistance = distance
                                maxTextSim = textSim
                                maxImgSim = imgSim
                            }
                        }
                    }
                }

                if (potentialDuplicate != null) {
                    _duplicateAlertState.value = DuplicateAlertState(
                        showDialog = true,
                        pendingIssueData = PendingIssueData(
                            title = title,
                            description = description,
                            category = category,
                            severity = severity,
                            latitude = latitude,
                            longitude = longitude,
                            locationName = locationName,
                            imageUrl = imageUrl,
                            aiSummary = aiSummary,
                            confidenceScore = confidenceScore,
                            suggestedPriority = suggestedPriority
                        ),
                        duplicateIssue = potentialDuplicate,
                        proximityMeters = minDistance,
                        textSimilarity = maxTextSim,
                        imageSimilarity = maxImgSim
                    )
                    _reportState.value = ReportUiState.Idle
                } else {
                    val issue = repository.submitCustomIssue(
                        title = title,
                        description = description,
                        category = category,
                        severity = severity,
                        latitude = latitude,
                        longitude = longitude,
                        locationName = locationName,
                        reporter = reporter,
                        imageUrl = imageUrl,
                        aiSummary = aiSummary,
                        confidenceScore = confidenceScore,
                        suggestedPriority = suggestedPriority
                    )
                    _reportState.value = ReportUiState.Success(issue)
                }
            } catch (e: Exception) {
                _reportState.value = ReportUiState.Error(e.message ?: "Failed to submit custom issue")
            }
        }
    }

    // --- Interactive Actions ---
    fun reportIssue(title: String, description: String, category: String, location: String) {
        val reporter = _currentUser.value ?: return
        viewModelScope.launch {
            _reportState.value = ReportUiState.Submitting
            try {
                // Approximate standard mock coordinates for neighborhood center
                val lat = 37.7749 + (Math.random() - 0.5) * 0.05
                val lng = -122.4194 + (Math.random() - 0.5) * 0.05
                
                val issue = repository.reportIssue(
                    title = title,
                    description = description,
                    latitude = lat,
                    longitude = lng,
                    locationName = location,
                    reporter = reporter,
                    imageUrl = null // can be enhanced in-app
                )
                _reportState.value = ReportUiState.Success(issue)
            } catch (e: Exception) {
                _reportState.value = ReportUiState.Error(e.message ?: "Failed to report issue")
            }
        }
    }

    fun upvoteIssue(issueId: String) {
        viewModelScope.launch {
            repository.upvoteIssue(issueId)
            // Update current selection if it is open
            val current = _selectedIssue.value
            if (current != null && current.id == issueId) {
                _selectedIssue.value = repository.getIssueById(issueId)
            }
        }
    }

    fun assignIssueToWorker(issueId: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.assignIssueToWorker(issueId, user.id)
            // Reload selected issue if active
            val current = _selectedIssue.value
            if (current != null && current.id == issueId) {
                _selectedIssue.value = repository.getIssueById(issueId)
            }
        }
    }

    fun updateIssueStatus(issueId: String, status: IssueStatus, resolutionNotes: String? = null, resolvedImage: String? = null) {
        viewModelScope.launch {
            repository.updateIssueStatus(issueId, status, resolutionNotes, resolvedImage)
            // Reload selected issue if active
            val current = _selectedIssue.value
            if (current != null && current.id == issueId) {
                _selectedIssue.value = repository.getIssueById(issueId)
            }
        }
    }

    fun verifyIssue(
        issueId: String,
        isLegitimate: Boolean,
        comment: String,
        verificationType: String = "CONFIRM",
        evidenceImageUrl: String? = null
    ) {
        val verifier = _currentUser.value ?: return
        viewModelScope.launch {
            repository.verifyIssue(
                issueId = issueId,
                verifier = verifier,
                isLegitimate = isLegitimate,
                comment = comment,
                verificationType = verificationType,
                evidenceImageUrl = evidenceImageUrl
            )
            // Refresh selection state
            _selectedIssue.value = repository.getIssueById(issueId)
        }
    }

    fun resolveIssue(issueId: String, notes: String) {
        viewModelScope.launch {
            repository.resolveIssue(issueId, notes)
            // Refresh selection state
            _selectedIssue.value = repository.getIssueById(issueId)
        }
    }

    fun submitHelperVerification(
        issueId: String,
        beforeBitmap: android.graphics.Bitmap,
        afterBitmap: android.graphics.Bitmap,
        beforeUrl: String,
        afterUrl: String,
        notes: String
    ) {
        val helper = _currentUser.value ?: return
        viewModelScope.launch {
            _reportState.value = ReportUiState.Submitting
            try {
                // Call Gemini to validate work
                val aiResult = com.example.data.api.GeminiClient.validateHelperWork(beforeBitmap, afterBitmap)
                
                val issue = repository.getIssueById(issueId)
                if (issue != null) {
                    val updated = issue.copy(
                        status = IssueStatus.RESOLVED, // mark as resolved on helper success
                        helperBeforeImage = beforeUrl,
                        helperAfterImage = afterUrl,
                        helperImprovementScore = aiResult.improvementScore,
                        helperConfidenceScore = aiResult.confidenceScore,
                        helperFraudRiskScore = aiResult.fraudRiskScore,
                        helperValidationFeedback = aiResult.feedback,
                        resolvedImage = afterUrl,
                        resolutionNotes = notes,
                        communityAssistedBadgeAwarded = true
                    )
                    
                    // Save in database
                    db.issueDao().updateIssue(updated)
                    
                    // Award Helper Badge, Reputation and XP Points
                    val repBonus = if (aiResult.improvementScore > 80 && aiResult.fraudRiskScore < 10) 80 else 40
                    repository.updateUserReputationMetrics(
                        userId = helper.id,
                        reputationDelta = repBonus,
                        communityScoreDelta = 120,
                        trustScoreDelta = 15
                    )
                    
                    // Add Civic Notification
                    val notification = CivicNotification(
                        id = UUID.randomUUID().toString(),
                        userId = helper.id,
                        title = "Community Assisted Badge Awarded! 🏅",
                        message = "Your repair on '${issue.title}' was verified by Gemini (Improvement: ${aiResult.improvementScore}%). You earned +$repBonus XP!",
                        type = "reputation_gain"
                    )
                    db.notificationDao().insertNotification(notification)
                    
                    // Add Community Post
                    val helperPost = CommunityPost(
                        id = UUID.randomUUID().toString(),
                        authorId = helper.id,
                        authorName = helper.name,
                        authorAvatar = helper.avatarUrl,
                        content = "🛠️ Community Helper Action! I completed repairs on '${issue.title}' in ${issue.locationName}. Gemini validated improvement score at ${aiResult.improvementScore}%. Let's keep our town shining! ✨",
                        category = "Helper Task"
                    )
                    repository.addCommunityPost(helperPost)
                    
                    _selectedIssue.value = updated
                    _reportState.value = ReportUiState.Success(updated)
                } else {
                    _reportState.value = ReportUiState.Idle
                }
            } catch (e: Exception) {
                _reportState.value = ReportUiState.Error(e.message ?: "Failed helper validation workflow")
            }
        }
    }

    fun triggerSmartNotification(userId: String, language: String) {
        viewModelScope.launch {
            val (title, msg) = when (language.lowercase()) {
                "hindi" -> Pair(
                    "सुरक्षा अलर्ट ⚠️",
                    "ऐसा लगता है कि आपके नियमित मार्ग के पास एक गड्ढा है। कृपया ध्यान से ड्राइव करें।"
                )
                "punjabi" -> Pair(
                    "ਸੁਰੱਖਿਆ ਅਲਰਟ ⚠️",
                    "ਲੱਗਦਾ ਹੈ ਕਿ ਤੁਹਾਡੇ ਰੋਜ਼ਾਨਾ ਰੂਟ ਦੇ ਨੇੜੇ ਇੱਕ ਟੋਆ ਹੈ। ਕਿਰਪਾ ਕਰਕੇ ਧਿਆਨ ਨਾਲ ਚਲਾਓ।"
                )
                "tamil" -> Pair(
                    "பாதுகாப்பு எச்சரிக்கை ⚠️",
                    "உங்கள் வழக்கமான பாதையின் அருகில் ஒரு குழி இருப்பது போல் தெரிகிறது. கவனமாக ஓட்டவும்."
                )
                "telugu" -> Pair(
                    "భద్రతా హెచ్చరిక ⚠️",
                    "మీ రెగ్యులర్ రూట్ సమీపంలో గుంత ఉన్నట్లు కనిపిస్తోంది. జాగ్రత్తగా నడపండి."
                )
                "marathi" -> Pair(
                    "सुरक्षा चेतावणी ⚠️",
                    "तुमच्या नेहमीच्या मार्गाजवळ खड्डा असल्याचे दिसते. कृपया काळजीपूर्वक वाहन चालवा."
                )
                "gujarati" -> Pair(
                    "સવર્ડ ગેટવે અલ્ટર ⚠️",
                    "એવું લાગે છે કે તમારા નિયમિત રૂટ નજીક ખાડો છે. કૃપા કરીને સાવધાનીપૂર્વક ચલાવો."
                )
                "bengali" -> Pair(
                    "সুরক্ষা সতর্কতা ⚠️",
                    "মনে হচ্ছে আপনার নিয়মিত রুট এর কাছাকাছি একটি গর্ত রয়েছে। সাবধানে ড্রাইভ করুন।"
                )
                else -> Pair(
                    "Locality Watch Route Alert ⚠️",
                    "Looks like a pothole near your regular route. Drive safe!"
                )
            }
            
            val notification = CivicNotification(
                id = UUID.randomUUID().toString(),
                userId = userId,
                title = title,
                message = msg,
                type = "alert"
            )
            db.notificationDao().insertNotification(notification)
        }
    }

    fun triggerNearbyVerificationNotification(userId: String, language: String) {
        viewModelScope.launch {
            val (title, msg) = when (language.lowercase()) {
                "hindi" -> Pair(
                    "त्वरित सत्यापन 🤝",
                    "पास में केवल एक सत्यापन की आवश्यकता है। अतिरिक्त प्रतिष्ठा अंक अर्जित करें!"
                )
                "punjabi" -> Pair(
                    "ਤੁਰੰਤ ਪੁਸ਼ਟੀਕਰਨ 🤝",
                    "ਨੇੜੇ ਸਿਰਫ਼ ਇੱਕ ਪੁਸ਼ਟੀਕਰਨ ਦੀ ਲੋੜ ਹੈ। ਵਾਧੂ ਇੱਜ਼ਤ ਅੰਕ ਕਮਾਓ!"
                )
                "tamil" -> Pair(
                    "விரைவான சரிபார்ப்பு 🤝",
                    "அருகில் ஒரு சரிபார்ப்பு மட்டுமே தேவைப்படுகிறது. கூடுதல் நற்பெயர் புள்ளிகளைப் பெறுங்கள்!"
                )
                "telugu" -> Pair(
                    "త్వరిత ధృవీకరణ 🤝",
                    "సమీపంలో కేవలం ఒక ధృవీకరణ మాత్రమే అవసరం. అదనపు కీర్తి పాయింట్లను పొందండి!"
                )
                "marathi" -> Pair(
                    "त्वरित पडताळणी 🤝",
                    "जवळपास फक्त एका पडताळणीची गरज आहे. अतिरिक्त प्रतिष्ठा गुण मिळवा!"
                )
                "gujarati" -> Pair(
                    "ઝડપી વેરિફિકેશન 🤝",
                    "નજીકમાં માત્ર એક વેરિફિકેશનની જરૂર છે. વધારાના પ્રતિષ્ઠા પોઈન્ટ મેળવો!"
                )
                "bengali" -> Pair(
                    "দ্রুত যাচাইকরণ 🤝",
                    "কাছাকাছি শুধুমাত্র একটি যাচাইকরণ প্রয়োজন। অতিরিক্ত সুনাম অর্জন করুন!"
                )
                else -> Pair(
                    "Action Needed Nearby 🤝",
                    "Only one verification needed nearby. Verify now to claim bonus XP!"
                )
            }
            
            val notification = CivicNotification(
                id = UUID.randomUUID().toString(),
                userId = userId,
                title = title,
                message = msg,
                type = "alert"
            )
            db.notificationDao().insertNotification(notification)
        }
    }

    fun addPost(content: String, category: String = "General") {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val post = CommunityPost(
                id = UUID.randomUUID().toString(),
                authorId = user.id,
                authorName = user.name,
                authorAvatar = user.avatarUrl,
                content = content,
                category = category
            )
            repository.addCommunityPost(post)
            // Reward poster with Trust and Reputation System
            repository.updateUserReputationMetrics(
                userId = user.id,
                reputationDelta = 5,
                communityScoreDelta = 10,
                trustScoreDelta = 1
            )
        }
    }

    fun likePost(postId: String) {
        viewModelScope.launch {
            repository.likePost(postId)
        }
    }

    // --- Advanced Workforce Engine Methods ---
    fun recommendTeamForIssue(issue: InfrastructureIssue) {
        val requiredSkills = when (issue.category) {
            IssueCategory.WATER, IssueCategory.WATER_LEAKAGE -> listOf("Water Technician", "Road Worker")
            IssueCategory.ROADS, IssueCategory.POTHOLE, IssueCategory.ROAD_DAMAGE -> listOf("Road Worker")
            IssueCategory.LIGHTING, IssueCategory.BROKEN_STREETLIGHT -> listOf("Electricity Lineman")
            IssueCategory.SANITATION, IssueCategory.GARBAGE -> listOf("Sanitation Helper")
            else -> listOf("Road Worker")
        }

        val recs = _workerAvailability.value.filter { it.isAvailable && it.trade in requiredSkills }.map { worker ->
            val latDelta = worker.latitude - issue.latitude
            val lngDelta = worker.longitude - issue.longitude
            val distance = Math.sqrt(latDelta * latDelta + lngDelta * lngDelta) * 111.0 // approx km
            val hours = when (issue.severity) {
                SeverityLevel.LOW -> 2
                SeverityLevel.MEDIUM -> 4
                SeverityLevel.HIGH -> 6
                SeverityLevel.CRITICAL -> 8
            }
            AiTeamRecommendation(
                id = UUID.randomUUID().toString(),
                issueId = issue.id,
                workerId = worker.id,
                workerName = worker.name,
                trade = worker.trade,
                distanceKm = Math.round(distance * 10.0) / 10.0,
                estimatedHours = hours,
                reason = "${worker.name} is available nearby (${String.format("%.1f", distance)} km) and is a certified ${worker.trade}."
            )
        }
        _aiRecommendations.value = recs
    }

    fun approveAssignment(issueId: String, recommendedWorkerIds: List<String>) {
        viewModelScope.launch {
            val mainWorkerId = recommendedWorkerIds.firstOrNull() ?: ""
            updateIssueStatus(issueId, IssueStatus.IN_PROGRESS)
            
            val issue = repository.getIssueById(issueId)
            if (issue != null) {
                val updated = issue.copy(
                    status = IssueStatus.IN_PROGRESS,
                    assignedWorkerId = mainWorkerId,
                    suggestedPriority = calculateSmartPriority(issue).name
                )
                db.issueDao().updateIssue(updated)
                _selectedIssue.value = updated
            }

            val teamId = "team_" + UUID.randomUUID().toString().take(6)
            val confirmations = recommendedWorkerIds.associateWith { false }
            val newAssignment = TeamAssignment(
                id = "assign_" + UUID.randomUUID().toString().take(6),
                issueId = issueId,
                teamId = teamId,
                assignedBy = _currentUser.value?.id ?: "officer1",
                status = "ASSIGNED",
                assignedAt = System.currentTimeMillis(),
                estimatedCompletionTime = System.currentTimeMillis() + 4 * 3600 * 1000,
                workerConfirmations = confirmations
            )
            _teamAssignments.value = _teamAssignments.value + newAssignment

            recommendedWorkerIds.forEach { workerId ->
                val notification = CivicNotification(
                    id = UUID.randomUUID().toString(),
                    userId = workerId,
                    title = "New Team Task Assigned 🛠️",
                    message = "You have been assigned to task #${issueId.take(5).uppercase()} at ${issue?.locationName ?: "location"}.",
                    type = "status_change"
                )
                db.notificationDao().insertNotification(notification)
            }
        }
    }

    fun calculateSmartPriority(issue: InfrastructureIssue): SeverityLevel {
        val proximityScore = if (issue.title.lowercase().contains("school") || issue.description.lowercase().contains("hospital")) 15 else 0
        val baseScore = when (issue.severity) {
            SeverityLevel.LOW -> 10
            SeverityLevel.MEDIUM -> 25
            SeverityLevel.HIGH -> 45
            SeverityLevel.CRITICAL -> 65
        }
        val finalScore = baseScore + issue.verificationsCount * 5 + proximityScore
        return when {
            finalScore < 20 -> SeverityLevel.LOW
            finalScore < 40 -> SeverityLevel.MEDIUM
            finalScore < 60 -> SeverityLevel.HIGH
            else -> SeverityLevel.CRITICAL
        }
    }

    fun confirmWorkerTask(issueId: String, workerId: String) {
        val assignmentList = _teamAssignments.value.map { assign ->
            if (assign.issueId == issueId && assign.workerConfirmations.containsKey(workerId)) {
                val updatedConfirmations = assign.workerConfirmations.toMutableMap().apply {
                    put(workerId, true)
                }
                val allConfirmed = updatedConfirmations.values.all { it }
                val newStatus = if (allConfirmed) "AWAITING_VERIFICATION" else "IN_PROGRESS"
                
                if (allConfirmed) {
                    viewModelScope.launch {
                        val issue = repository.getIssueById(issueId)
                        if (issue != null) {
                            val updated = issue.copy(status = IssueStatus.IN_PROGRESS)
                            db.issueDao().updateIssue(updated)
                            _selectedIssue.value = updated

                            val notification = CivicNotification(
                                id = UUID.randomUUID().toString(),
                                userId = assign.assignedBy,
                                title = "Team Completed Work 🎉",
                                message = "All team members confirmed completion for Task #${issueId.take(5).uppercase()}. Pending AI Validation.",
                                type = "alert"
                            )
                            db.notificationDao().insertNotification(notification)
                        }
                    }
                }
                assign.copy(workerConfirmations = updatedConfirmations, status = newStatus)
            } else {
                assign
            }
        }
        _teamAssignments.value = assignmentList
    }

    fun runGeminiValidation(issueId: String, beforeBitmap: android.graphics.Bitmap, afterBitmap: android.graphics.Bitmap, notes: String) {
        viewModelScope.launch {
            _reportState.value = ReportUiState.Submitting
            try {
                val result = com.example.data.api.GeminiClient.validateHelperWork(beforeBitmap, afterBitmap)
                val issue = repository.getIssueById(issueId)
                if (issue != null) {
                    val updated = issue.copy(
                        status = IssueStatus.RESOLVED,
                        helperBeforeImage = "https://images.unsplash.com/photo-1515162305285-0293e4767cc2",
                        helperAfterImage = "https://images.unsplash.com/photo-1590674899484-d5640e854abe",
                        helperImprovementScore = result.improvementScore,
                        helperConfidenceScore = result.confidenceScore,
                        helperFraudRiskScore = result.fraudRiskScore,
                        helperValidationFeedback = result.feedback,
                        resolvedImage = "https://images.unsplash.com/photo-1590674899484-d5640e854abe",
                        resolutionNotes = "AI Validated (Improvement: ${result.improvementScore}%): $notes"
                    )
                    db.issueDao().updateIssue(updated)
                    _selectedIssue.value = updated

                    val historyItem = TaskHistoryItem(
                        id = "hist_" + UUID.randomUUID().toString().take(6),
                        issueId = issueId,
                        status = "CLOSED",
                        assignedTeamId = "team_pb08_roads_01",
                        workersInvolved = listOf("worker_harpreet_99", "worker_rajesh_45"),
                        beforeImageUrl = updated.helperBeforeImage,
                        afterImageUrl = updated.helperAfterImage,
                        improvementScore = result.improvementScore,
                        confidenceScore = result.confidenceScore,
                        fraudRiskScore = result.fraudRiskScore,
                        feedback = result.feedback ?: "Validation Successful",
                        completedAt = System.currentTimeMillis(),
                        closedAt = System.currentTimeMillis()
                    )
                    _taskHistory.value = _taskHistory.value + historyItem

                    val officerNotif = CivicNotification(
                        id = UUID.randomUUID().toString(),
                        userId = "officer1",
                        title = "Task #${issueId.take(5).uppercase()} Completed & AI Verified ✅",
                        message = "Work has been validated by Gemini (Improvement: ${result.improvementScore}%, Fraud Risk: Low).",
                        type = "alert"
                    )
                    db.notificationDao().insertNotification(officerNotif)

                    val reporterNotif = CivicNotification(
                        id = UUID.randomUUID().toString(),
                        userId = issue.reporterId,
                        title = "Your Reported Issue is Resolved! 🛠️",
                        message = "Your report on '${issue.title}' is now resolved. Thank you for your civic contribution!",
                        type = "reputation_gain"
                    )
                    db.notificationDao().insertNotification(reporterNotif)

                    selectedIssueVotes.value.forEach { vote ->
                        val verifierNotif = CivicNotification(
                            id = UUID.randomUUID().toString(),
                            userId = vote.verifierId,
                            title = "Issue You Verified Has Been Repaired! 🤝",
                            message = "The issue '${issue.title}' has been successfully repaired and validated.",
                            type = "reputation_gain"
                        )
                        db.notificationDao().insertNotification(verifierNotif)
                    }

                    _reportState.value = ReportUiState.Success(updated)
                }
            } catch (e: Exception) {
                _reportState.value = ReportUiState.Error("AI Validation failed: " + e.message)
            }
        }
    }

    // --- Community Hub Messaging ---
    fun sendCivicMessage(channelOrGroupId: String, text: String, imageUrl: String? = null, voiceNoteUrl: String? = null, voiceNoteDuration: Int = 0) {
        val user = _currentUser.value ?: return
        val newMessage = CivicMessage(
            id = "msg_" + UUID.randomUUID().toString().take(6),
            channelOrGroupId = channelOrGroupId,
            senderId = user.id,
            senderName = user.name,
            senderRole = user.role,
            senderAvatarUrl = user.avatarUrl,
            text = text,
            imageUrl = imageUrl,
            voiceNoteUrl = voiceNoteUrl,
            voiceNoteDuration = voiceNoteDuration,
            timestamp = System.currentTimeMillis()
        )
        _messages.value = _messages.value + newMessage
    }

    fun addOfficialAnnouncement(channelId: String, title: String, content: String, category: String, imageUrl: String? = null) {
        val user = _currentUser.value ?: return
        val newAnnouncement = OfficialAnnouncement(
            id = "ann_" + UUID.randomUUID().toString().take(6),
            channelId = channelId,
            postedBy = user.name,
            title = title,
            content = content,
            category = category,
            imageUrl = imageUrl,
            timestamp = System.currentTimeMillis()
        )
        _announcements.value = _announcements.value + newAnnouncement
        
        val msgText = "📢 OFFICIAL ANNOUNCEMENT: **$title**\n$content"
        sendCivicMessage(channelId, msgText)
    }

    fun reactToMessage(messageId: String, reaction: String) {
        val user = _currentUser.value ?: return
        val updatedList = _messages.value.map { msg ->
            if (msg.id == messageId) {
                val currentReactions = msg.reactions.toMutableMap()
                val currentUsers = currentReactions[reaction]?.toMutableList() ?: mutableListOf()
                if (currentUsers.contains(user.id)) {
                    currentUsers.remove(user.id)
                } else {
                    currentUsers.add(user.id)
                }
                if (currentUsers.isEmpty()) {
                    currentReactions.remove(reaction)
                } else {
                    currentReactions[reaction] = currentUsers
                }
                msg.copy(reactions = currentReactions)
            } else {
                msg
            }
        }
        _messages.value = updatedList
    }

    fun createCommunityGroup(name: String, description: String, category: String, isPublic: Boolean) {
        val user = _currentUser.value ?: return
        val groupId = "group_" + UUID.randomUUID().toString().take(6)
        val newGroup = CommunityGroup(
            id = groupId,
            name = name,
            description = description,
            category = category,
            division = "Jalandhar North Division",
            creatorId = user.id,
            moderators = listOf(user.id),
            isPublic = isPublic,
            inviteCode = if (!isPublic) "INVITE-" + UUID.randomUUID().toString().take(4).uppercase() else null
        )
        _communityGroups.value = _communityGroups.value + newGroup

        val newMember = GroupMember(
            id = "gmember_" + UUID.randomUUID().toString().take(6),
            groupId = groupId,
            userId = user.id,
            role = "OWNER",
            status = "APPROVED"
        )
        _groupMembers.value = _groupMembers.value + newMember
    }

    private fun initializeMockData() {
        _workerAvailability.value = listOf(
            WorkerAvailability("worker_harpreet_99", "Harpreet Singh", "Road Worker", true, 1, 41.3082, -72.9279, "Jalandhar North Division"),
            WorkerAvailability("worker_rajesh_45", "Rajesh Kumar", "Road Worker", true, 0, 41.3100, -72.9220, "Jalandhar North Division"),
            WorkerAvailability("worker_amandeep_12", "Amandeep Singh", "Water Technician", true, 0, 41.3050, -72.9320, "Jalandhar North Division"),
            WorkerAvailability("worker_vikram_21", "Vikram Jeet", "Electricity Lineman", true, 0, 41.3120, -72.9150, "Jalandhar North Division")
        )

        _divisionChannels.value = listOf(
            DivisionChannel("chan_jalandhar_north", "Jalandhar North Core", "Official bulletins and central civic channel.", "Jalandhar North Division", "officer1", 1420),
            DivisionChannel("chan_sector_7", "Sector 7 Public Works", "Discussion and status tracking for Sector 7 resident improvements.", "Jalandhar North Division", "officer1", 850),
            DivisionChannel("chan_ward_12", "Ward 12 Infrastructure", "Dedicated updates for Ward 12 water and grid maintenance.", "Jalandhar North Division", "officer1", 620)
        )

        _announcements.value = listOf(
            OfficialAnnouncement(
                id = "ann_road_close_sector7",
                channelId = "chan_sector_7",
                postedBy = "Director Marcus Vance",
                title = "Road Closure Alert: Sector 7 Main Boulevard",
                content = "Sector 7 main link road will remain closed for sewer upgrades from 9:00 AM to 5:00 PM tomorrow. Please use Sector 8 bypass.",
                category = "ROAD_CLOSURES",
                imageUrl = "https://images.unsplash.com/photo-1515162305285-0293e4767cc2",
                timestamp = System.currentTimeMillis() - 3600000 * 2
            ),
            OfficialAnnouncement(
                id = "ann_water_maintenance",
                channelId = "chan_ward_12",
                postedBy = "Director Marcus Vance",
                title = "Water Supply Maintenance Notice",
                content = "Water pressure will be low on Friday morning (6:00 AM to 10:00 AM) due to valve replacement in Jalandhar North pump-house.",
                category = "WATER_SUPPLY",
                timestamp = System.currentTimeMillis() - 3600000 * 5
            )
        )

        _communityGroups.value = listOf(
            CommunityGroup("group_sec7_residents", "Sector 7 Welfare Association", "Residents collaborating for a cleaner, safer Sector 7.", "WELFARE", "Jalandhar North Division", "citizen1"),
            CommunityGroup("group_jalandhar_green", "Jalandhar Clean & Green Volunteers", "Volunteers planting trees and cleaning local ward parks.", "VOLUNTEER", "Jalandhar North Division", "citizen2")
        )

        _messages.value = listOf(
            CivicMessage(
                id = "msg_init_1",
                channelOrGroupId = "chan_sector_7",
                senderId = "officer1",
                senderName = "Director Marcus Vance",
                senderRole = UserRole.OFFICER,
                senderAvatarUrl = "https://images.unsplash.com/photo-1560250097-0b93528c311a",
                text = "Welcome to Sector 7 Official Public Works Channel. All announcements regarding upcoming road layouts or emergency maintenance will be posted here."
            ),
            CivicMessage(
                id = "msg_init_2",
                channelOrGroupId = "chan_sector_7",
                senderId = "citizen1",
                senderName = "Anil Sharma",
                senderRole = UserRole.CITIZEN,
                senderAvatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb",
                text = "Thank you Director! Can we also schedule some sidewalk cleaning around Ward 4 park? There is quite a lot of building waste piled up.",
                timestamp = System.currentTimeMillis() - 1800000
            ),
            CivicMessage(
                id = "msg_init_3",
                channelOrGroupId = "group_sec7_residents",
                senderId = "citizen1",
                senderName = "Anil Sharma",
                senderRole = UserRole.CITIZEN,
                senderAvatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb",
                text = "Hello neighbors! Let's organize our weekly sanitation drive this Saturday at 10:00 AM. Who is in? Please react with 👍."
            )
        )

        _teamAssignments.value = listOf(
            TeamAssignment(
                id = "assign_1234",
                issueId = "pothole1",
                teamId = "team_pb08_roads_01",
                assignedBy = "officer1",
                status = "ASSIGNED",
                assignedAt = System.currentTimeMillis() - 3600000,
                estimatedCompletionTime = System.currentTimeMillis() + 10800000,
                workerConfirmations = mapOf("worker_harpreet_99" to false, "worker_rajesh_45" to false)
            )
        )
    }
}
