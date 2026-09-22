package com.example.data.repository

import android.util.Log
import com.example.data.api.GeminiClient
import com.example.data.local.CommunityDao
import com.example.data.local.IssueDao
import com.example.data.local.UserDao
import com.example.data.local.NotificationDao
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class CivicRepository(
    private val issueDao: IssueDao,
    private val userDao: UserDao,
    private val communityDao: CommunityDao,
    private val notificationDao: NotificationDao,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val TAG = "CivicRepository"

    val allIssues: Flow<List<InfrastructureIssue>> = issueDao.getAllIssues()
    val allPosts: Flow<List<CommunityPost>> = communityDao.getAllPosts()

    fun getNotifications(userId: String): Flow<List<CivicNotification>> = notificationDao.getNotificationsForUser(userId)

    suspend fun syncWithFirebase(userId: String?) = withContext(Dispatchers.IO) {
        if (!FirebaseService.isAvailable()) return@withContext
        try {
            // 1. Sync remote issues to local Room cache
            val remoteIssues = FirebaseService.getIssues()
            if (remoteIssues.isNotEmpty()) {
                issueDao.insertIssues(remoteIssues)
            }

            // 2. Sync remote notifications to local Room cache
            if (userId != null) {
                val remoteNotifications = FirebaseService.getNotifications(userId)
                if (remoteNotifications.isNotEmpty()) {
                    notificationDao.insertNotifications(remoteNotifications)
                }

                // 3. Sync profile
                val remoteUser = FirebaseService.getUserProfile(userId)
                if (remoteUser != null) {
                    userDao.insertUser(remoteUser)
                }
            }
            Log.d(TAG, "Synchronized Room cache with Firestore cloud data successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Dynamic Firestore sync failed, running in robust local-only mode.", e)
        }
    }

    init {
        // Seed initial high-fidelity data if empty so the app has live, fully realized community state on launch.
        externalScope.launch {
            try {
                seedInitialDataIfEmpty()
            } catch (e: Exception) {
                Log.e(TAG, "Error seeding data", e)
            }
        }
    }

    fun getUser(userId: String): Flow<User?> = userDao.getUserByIdFlow(userId)

    suspend fun getIssueById(issueId: String): InfrastructureIssue? = withContext(Dispatchers.IO) {
        issueDao.getIssueById(issueId)
    }

    suspend fun createOrUpdateUser(user: User) = withContext(Dispatchers.IO) {
        userDao.insertUser(user)
        if (FirebaseService.isAvailable()) {
            try {
                FirebaseService.saveUserProfile(user)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload user profile to Firebase", e)
            }
        }
    }

    /**
     * Update user metrics and re-calculate level based on scores
     */
    suspend fun updateUserReputationMetrics(
        userId: String,
        reputationDelta: Int = 0,
        communityScoreDelta: Int = 0,
        trustScoreDelta: Int = 0,
        reportedCountDelta: Int = 0,
        verifiedCountDelta: Int = 0
    ) = withContext(Dispatchers.IO) {
        val user = userDao.getUserById(userId) ?: return@withContext
        
        val newReputation = maxOf(0, user.reputationPoints + reputationDelta)
        val newReported = maxOf(0, user.reportedCount + reportedCountDelta)
        val newVerified = maxOf(0, user.verifiedCount + verifiedCountDelta)
        val newTrust = (user.trustScore + trustScoreDelta).coerceIn(10, 100)
        val newCommunity = maxOf(0, user.communityScore + communityScoreDelta)
        
        // Levels: Citizen, Contributor, Hero, Guardian, Legend
        val newLevel = when {
            newCommunity >= 1000 && newTrust >= 95 -> "Legend"
            newCommunity >= 500 && newTrust >= 90 -> "Guardian"
            newCommunity >= 250 && newTrust >= 85 -> "Hero"
            newCommunity >= 100 && newTrust >= 80 -> "Contributor"
            else -> "Citizen"
        }
        
        val updatedUser = user.copy(
            reputationPoints = newReputation,
            reportedCount = newReported,
            verifiedCount = newVerified,
            trustScore = newTrust,
            communityScore = newCommunity,
            heroLevel = newLevel
        )
        
        userDao.insertUser(updatedUser)
        
        if (FirebaseService.isAvailable()) {
            try {
                FirebaseService.saveUserProfile(updatedUser)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync user stats with Firebase", e)
            }
        }
    }

    /**
     * Citizen reports a new infrastructure issue.
     * Triggers parallel Gemini AI parsing to auto-detect category, severity, and build safety tips!
     */
    suspend fun reportIssue(
        title: String,
        description: String,
        latitude: Double,
        longitude: Double,
        locationName: String,
        reporter: User,
        imageUrl: String? = null
    ): InfrastructureIssue = withContext(Dispatchers.IO) {
        // Call Gemini Client for AI-powered assessment
        val (category, severity, aiSummary) = GeminiClient.analyzeIssue(title, description)

        val issue = InfrastructureIssue(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            category = category,
            latitude = latitude,
            longitude = longitude,
            locationName = locationName,
            reporterId = reporter.id,
            reporterName = reporter.name,
            imageUrl = imageUrl,
            status = IssueStatus.REPORTED,
            severity = severity,
            aiSummary = aiSummary
        )

        // Save to Room cache
        issueDao.insertIssue(issue)

        // Update reporter stats and grant Civic Points with Trust and Reputation System
        updateUserReputationMetrics(
            userId = reporter.id,
            reputationDelta = 15,
            communityScoreDelta = 25,
            trustScoreDelta = 2,
            reportedCountDelta = 1
        )

        // Create community post automatically to notify the neighborhood board
        val communityNotification = CommunityPost(
            id = UUID.randomUUID().toString(),
            authorId = "SYSTEM",
            authorName = "CivicAlert Bot",
            authorAvatar = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=150",
            content = "📢 NEW REPORT: A '${category.name.lowercase().capitalize()}' issue has been reported at '$locationName' ($title). Neighbors in this vicinity are requested to review and verify this issue! Gemini AI Safety Tip: $aiSummary",
            category = "Alerts"
        )
        communityDao.insertPost(communityNotification)

        // Create system notification for reporter
        val systemNotification = CivicNotification(
            id = UUID.randomUUID().toString(),
            userId = reporter.id,
            title = "Report Submitted Successfully!",
            message = "Your infrastructure report '$title' at '$locationName' is now live on the map. Earn reputation points by keeping track of updates!",
            type = "alert"
        )
        notificationDao.insertNotification(systemNotification)

        // Sync with Firebase if available
        if (FirebaseService.isAvailable()) {
            try {
                FirebaseService.saveIssue(issue)
                FirebaseService.saveNotification(systemNotification)
                FirebaseService.incrementUserStat(reporter.id, "reportedCount", 1)
                FirebaseService.incrementUserStat(reporter.id, "reputationPoints", 15)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync issue report with Firebase", e)
            }
        }

        issue
    }

    /**
     * Submit a reviewed and approved custom infrastructure issue.
     */
    suspend fun submitCustomIssue(
        title: String,
        description: String,
        category: IssueCategory,
        severity: SeverityLevel,
        latitude: Double,
        longitude: Double,
        locationName: String,
        reporter: User,
        imageUrl: String? = null,
        aiSummary: String? = null,
        confidenceScore: String? = null,
        suggestedPriority: String? = null
    ): InfrastructureIssue = withContext(Dispatchers.IO) {
        val issue = InfrastructureIssue(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            category = category,
            latitude = latitude,
            longitude = longitude,
            locationName = locationName,
            reporterId = reporter.id,
            reporterName = reporter.name,
            imageUrl = imageUrl,
            status = IssueStatus.REPORTED,
            severity = severity,
            aiSummary = aiSummary ?: "Observe warnings and report updates to help municipal workers resolve the issue.",
            confidenceScore = confidenceScore,
            suggestedPriority = suggestedPriority
        )

        // Save to Room cache
        issueDao.insertIssue(issue)

        // Update reporter stats and grant Civic Points with Trust and Reputation System
        updateUserReputationMetrics(
            userId = reporter.id,
            reputationDelta = 15,
            communityScoreDelta = 25,
            trustScoreDelta = 2,
            reportedCountDelta = 1
        )

        // Create community post automatically to notify the neighborhood board
        val communityNotification = CommunityPost(
            id = UUID.randomUUID().toString(),
            authorId = "SYSTEM",
            authorName = "CivicAlert Bot",
            authorAvatar = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=150",
            content = "📢 NEW REPORT: A '${category.name.lowercase().capitalize()}' issue has been reported at '$locationName' ($title). Neighbors in this vicinity are requested to review and verify this issue! Gemini AI Safety Tip: ${issue.aiSummary}",
            category = "Alerts"
        )
        communityDao.insertPost(communityNotification)

        // Create system notification for reporter
        val systemNotification = CivicNotification(
            id = UUID.randomUUID().toString(),
            userId = reporter.id,
            title = "Report Submitted Successfully!",
            message = "Your infrastructure report '$title' at '$locationName' is now live on the map. Earn reputation points by keeping track of updates!",
            type = "alert"
        )
        notificationDao.insertNotification(systemNotification)

        // Sync with Firebase if available
        if (FirebaseService.isAvailable()) {
            try {
                FirebaseService.saveIssue(issue)
                FirebaseService.saveNotification(systemNotification)
                FirebaseService.incrementUserStat(reporter.id, "reportedCount", 1)
                FirebaseService.incrementUserStat(reporter.id, "reputationPoints", 15)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync issue report with Firebase", e)
            }
        }

        issue
    }

    /**
     * Citizen upvotes an issue to increase awareness
     */
    suspend fun upvoteIssue(issueId: String) = withContext(Dispatchers.IO) {
        issueDao.incrementUpvote(issueId)
        if (FirebaseService.isAvailable()) {
            try {
                val updated = issueDao.getIssueById(issueId)
                if (updated != null) {
                    FirebaseService.saveIssue(updated)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync upvote with Firebase", e)
            }
        }
    }

    /**
     * Citizen cast a verification vote (Verify/Flag)
     */
    suspend fun verifyIssue(
        issueId: String,
        verifier: User,
        isLegitimate: Boolean,
        comment: String?,
        verificationType: String = "CONFIRM",
        evidenceImageUrl: String? = null
    ) = withContext(Dispatchers.IO) {
        val vote = VerificationVote(
            id = UUID.randomUUID().toString(),
            issueId = issueId,
            verifierId = verifier.id,
            verifierName = verifier.name,
            isLegitimate = isLegitimate,
            comment = comment,
            verificationType = verificationType,
            evidenceImageUrl = evidenceImageUrl
        )

        communityDao.insertVote(vote)

        // Check if verification threshold reached
        val issue = issueDao.getIssueById(issueId)
        if (issue != null) {
            val allVotes = communityDao.getVotesForIssueDirect(issueId)
            val positiveVotes = allVotes.count { it.isLegitimate }
            val negativeVotes = allVotes.count { !it.isLegitimate }
            val totalVotes = allVotes.size

            // Compute dynamic community confidence
            val confidencePercent = if (totalVotes == 0) 90 else (positiveVotes.toFloat() / totalVotes * 100).toInt()
            val confidenceScoreStr = "$confidencePercent%"

            var updatedStatus = issue.status
            if (positiveVotes >= issue.verificationThreshold && issue.status == IssueStatus.REPORTED) {
                updatedStatus = IssueStatus.VERIFIED
            } else if (negativeVotes >= issue.verificationThreshold && (issue.status == IssueStatus.REPORTED || issue.status == IssueStatus.VERIFYING)) {
                updatedStatus = IssueStatus.REJECTED
            }

            val updatedIssue = issue.copy(
                verificationsCount = positiveVotes,
                status = updatedStatus,
                confidenceScore = confidenceScoreStr
            )
            issueDao.updateIssue(updatedIssue)

            // Reward verifier with Trust and Reputation System
            updateUserReputationMetrics(
                userId = verifier.id,
                reputationDelta = 5,
                communityScoreDelta = 15,
                trustScoreDelta = 3,
                verifiedCountDelta = 1
            )

            // Create notification for original reporter
            val reporterNotification = CivicNotification(
                id = UUID.randomUUID().toString(),
                userId = issue.reporterId,
                title = "Report Verified!",
                message = "${verifier.name} has verified your report: '${issue.title}'",
                type = "status_change"
            )
            notificationDao.insertNotification(reporterNotification)

            if (FirebaseService.isAvailable()) {
                try {
                    FirebaseService.saveVerification(vote)
                    FirebaseService.saveIssue(updatedIssue)
                    FirebaseService.saveNotification(reporterNotification)
                    FirebaseService.incrementUserStat(verifier.id, "verifiedCount", 1)
                    FirebaseService.incrementUserStat(verifier.id, "reputationPoints", 5)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync verification with Firebase", e)
                }
            }
        }
    }

    /**
     * Get flow of verification votes for a specific issue
     */
    fun getVotesForIssue(issueId: String): Flow<List<VerificationVote>> {
        return communityDao.getVotesForIssue(issueId)
    }

    /**
     * Resolve an issue (Admin/Verified authority)
     */
    suspend fun resolveIssue(
        issueId: String,
        resolutionNotes: String,
        resolvedImage: String? = null
    ) = withContext(Dispatchers.IO) {
        val issue = issueDao.getIssueById(issueId)
        if (issue != null) {
            val updated = issue.copy(
                status = IssueStatus.RESOLVED,
                resolvedImage = resolvedImage,
                resolutionNotes = resolutionNotes
            )
            issueDao.updateIssue(updated)

            // Reward the original reporter with resolution bonus reputation!
            updateUserReputationMetrics(
                userId = issue.reporterId,
                reputationDelta = 50,
                communityScoreDelta = 100,
                trustScoreDelta = 10
            )

            // Publish community victory post
            val victoryPost = CommunityPost(
                id = UUID.randomUUID().toString(),
                authorId = "SYSTEM",
                authorName = "CivicVictory Bot",
                authorAvatar = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=150",
                content = "🎉 CIVIC RESOLUTION: '${issue.title}' in '$issue.locationName' is now marked as RESOLVED! Note: \"$resolutionNotes\". Huge thanks to original reporter ${issue.reporterName} and verifiers for keeping our neighborhood safe!",
                category = "Milestones"
            )
            communityDao.insertPost(victoryPost)

            // Create notification for original reporter
            val victoryNotification = CivicNotification(
                id = UUID.randomUUID().toString(),
                userId = issue.reporterId,
                title = "Report Resolved! 🎉",
                message = "The issue '${issue.title}' you reported has been resolved. Notes: $resolutionNotes (+50 Reputation)",
                type = "reputation_gain"
            )
            notificationDao.insertNotification(victoryNotification)

            if (FirebaseService.isAvailable()) {
                try {
                    FirebaseService.saveIssue(updated)
                    FirebaseService.saveNotification(victoryNotification)
                    FirebaseService.incrementUserStat(issue.reporterId, "reputationPoints", 50)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync issue resolution to Firebase", e)
                }
            }
        }
    }

    /**
     * Self-assign an issue to a worker
     */
    suspend fun assignIssueToWorker(issueId: String, workerId: String) = withContext(Dispatchers.IO) {
        val issue = issueDao.getIssueById(issueId)
        if (issue != null) {
            val updated = issue.copy(
                assignedWorkerId = workerId,
                status = if (issue.status == IssueStatus.REPORTED || issue.status == IssueStatus.VERIFIED || issue.status == IssueStatus.VERIFYING) IssueStatus.IN_PROGRESS else issue.status
            )
            issueDao.updateIssue(updated)
            if (FirebaseService.isAvailable()) {
                try {
                    FirebaseService.saveIssue(updated)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync issue assignment with Firebase", e)
                }
            }
        }
    }

    /**
     * Update status of an issue directly (Worker / Admin flow)
     */
    suspend fun updateIssueStatus(
        issueId: String,
        status: IssueStatus,
        resolutionNotes: String? = null,
        resolvedImage: String? = null
    ) = withContext(Dispatchers.IO) {
        val issue = issueDao.getIssueById(issueId)
        if (issue != null) {
            val updated = issue.copy(
                status = status,
                resolutionNotes = resolutionNotes ?: issue.resolutionNotes,
                resolvedImage = resolvedImage ?: issue.resolvedImage
            )
            issueDao.updateIssue(updated)

            // If resolving, trigger resolution notifications & community posts similar to resolveIssue
            if (status == IssueStatus.RESOLVED) {
                // Reward reporter
                updateUserReputationMetrics(
                    userId = issue.reporterId,
                    reputationDelta = 50,
                    communityScoreDelta = 100,
                    trustScoreDelta = 10
                )
                // Victory Notification and Post
                val victoryPost = CommunityPost(
                    id = UUID.randomUUID().toString(),
                    authorId = "SYSTEM",
                    authorName = "CivicVictory Bot",
                    authorAvatar = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=150",
                    content = "🎉 CIVIC RESOLUTION: '${issue.title}' in '$issue.locationName' is now marked as RESOLVED! Note: \"${resolutionNotes ?: ""}\". Huge thanks to original reporter ${issue.reporterName} and verifiers for keeping our neighborhood safe!",
                    category = "Milestones"
                )
                communityDao.insertPost(victoryPost)

                val victoryNotification = CivicNotification(
                    id = UUID.randomUUID().toString(),
                    userId = issue.reporterId,
                    title = "Report Resolved! 🎉",
                    message = "The issue '${issue.title}' you reported has been resolved. Notes: ${resolutionNotes ?: ""} (+50 Reputation)",
                    type = "reputation_gain"
                )
                notificationDao.insertNotification(victoryNotification)

                if (FirebaseService.isAvailable()) {
                    try {
                        FirebaseService.saveIssue(updated)
                        FirebaseService.saveNotification(victoryNotification)
                        FirebaseService.incrementUserStat(issue.reporterId, "reputationPoints", 50)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync issue resolution to Firebase", e)
                    }
                }
            } else {
                if (FirebaseService.isAvailable()) {
                    try {
                        FirebaseService.saveIssue(updated)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync status update to Firebase", e)
                    }
                }
            }
        }
    }

    /**
     * Add a community post
     */
    suspend fun addCommunityPost(post: CommunityPost) = withContext(Dispatchers.IO) {
        communityDao.insertPost(post)
    }

    /**
     * Like a community post
     */
    suspend fun likePost(postId: String) = withContext(Dispatchers.IO) {
        communityDao.likePost(postId)
    }

    /**
     * Seed initial content so the platform is highly interactive and complete out-of-the-box
     */
    private suspend fun seedInitialDataIfEmpty() {
        // Only seed if empty
        val existingIssues = allIssues.firstOrNull() ?: emptyList()
        if (existingIssues.isNotEmpty()) return

        // 1. Initial Users
        val users = listOf(
            User("SYSTEM", "CivicDesk Bot", "system@civicdex.org", "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150", 100, UserRole.OFFICER, 0, 0, 90, 200, "Contributor"),
            User("verifier1", "David Kim (Certified Contributor)", "david@civicdex.org", "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=150", 250, UserRole.CONTRIBUTOR, 0, 0, 95, 520, "Guardian"),
            User("citizen1", "Elena Rostova", "elena@outlook.com", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150", 85, UserRole.CITIZEN, 0, 0, 88, 180, "Contributor")
        )
        for (u in users) {
            userDao.insertUser(u)
        }

        // 2. Mock Issues
        val initialIssues = listOf(
            InfrastructureIssue(
                id = "issue-1",
                title = "Deep Crater Pothole",
                description = "Extremely deep pothole in the middle of the right-most lane. Several small cars have hit it and incurred damage. Dangerous for motorcyclists at night.",
                category = IssueCategory.ROADS,
                latitude = 37.7749,
                longitude = -122.4194,
                locationName = "452 Elmwood Blvd, Near Central Park Entrance",
                reporterId = "citizen1",
                reporterName = "Elena Rostova",
                status = IssueStatus.REPORTED,
                severity = SeverityLevel.HIGH,
                upvotesCount = 12,
                verificationsCount = 1,
                verificationThreshold = 3,
                aiSummary = "[AI Analysis] Motorcyclist hazard detected. Keep speed below 15mph. Local verifiers requested to snap updated daytime photo."
            ),
            InfrastructureIssue(
                id = "issue-2",
                title = "Broken Streetlamps - Complete Blackout",
                description = "The entire sequence of three lamps along the sidewalk is burnt out. The pathway is pitch black, making women and children feel unsafe walking home.",
                category = IssueCategory.LIGHTING,
                latitude = 37.7833,
                longitude = -122.4167,
                locationName = "98 Pine Grove Lane, Underpass",
                reporterId = "verifier1",
                reporterName = "David Kim",
                status = IssueStatus.VERIFIED,
                severity = SeverityLevel.CRITICAL,
                upvotesCount = 24,
                verificationsCount = 4,
                verificationThreshold = 3,
                aiSummary = "[AI Analysis] Critical security concern in dark corridor. Carry hand-flashlights. Local administration has queued dispatcher for replacing 250W sodium bulbs."
            ),
            InfrastructureIssue(
                id = "issue-3",
                title = "Overflowing Trash Containers",
                description = "Commercial waste bins are completely packed and neighbors are piling trash bags next to them. Stray dogs are ripping bags open, causing bad smells.",
                category = IssueCategory.SANITATION,
                latitude = 37.7699,
                longitude = -122.4468,
                locationName = "Market Square Alleyway, Behind Bistro 101",
                reporterId = "citizen1",
                reporterName = "Elena Rostova",
                status = IssueStatus.RESOLVED,
                severity = SeverityLevel.MEDIUM,
                upvotesCount = 8,
                verificationsCount = 3,
                verificationThreshold = 3,
                resolvedImage = "https://images.unsplash.com/photo-1532996122724-e3c354a0b15b?w=400",
                resolutionNotes = "Municipality added extra high-capacity bin and expedited pickup schedules to thrice a week.",
                aiSummary = "[AI Analysis] Sanitation hazard. Avoid dumping outside designated bins. Scheduled cleanup complete."
            )
        )
        issueDao.insertIssues(initialIssues)

        // 3. Mock Posts
        val initialPosts = listOf(
            CommunityPost(
                id = "post-1",
                authorId = "verifier1",
                authorName = "David Kim",
                authorAvatar = "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=150",
                content = "👋 Hello neighbors! I'm David, your local block verifier. If you see any public infrastructure hazards, drop them in the 'Report' tab. I will personally survey and certify them so the city council prioritizes them immediately. Let's work together!",
                category = "General",
                likesCount = 18
            ),
            CommunityPost(
                id = "post-2",
                authorId = "SYSTEM",
                authorName = "CivicAlert Bot",
                authorAvatar = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150",
                content = "📢 ALERT: Monthly Neighborhood Clean-Up drive is scheduled for Saturday, June 27th starting 8:00 AM at Market Square. Bring your own gloves! +50 Reputation points awarded to all active check-ins.",
                category = "Alerts",
                likesCount = 34
            )
        )
        communityDao.insertPosts(initialPosts)
    }
}
