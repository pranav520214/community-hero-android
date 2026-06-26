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
}
