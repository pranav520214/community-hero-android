package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class UserRole {
    CITIZEN,
    CONTRIBUTOR,
    WORKER,
    OFFICER,
    ADMINISTRATOR
}

enum class IssueCategory {
    ROADS,
    LIGHTING,
    SANITATION,
    WATER,
    POWER,
    PARKS,
    OTHER,
    POTHOLE,
    GARBAGE,
    WATER_LEAKAGE,
    BROKEN_STREETLIGHT,
    ROAD_DAMAGE
}

enum class IssueStatus {
    REPORTED,
    VERIFYING,
    VERIFIED,
    IN_PROGRESS,
    RESOLVED,
    REJECTED
}

enum class SeverityLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String,
    val reputationPoints: Int = 10,
    val role: UserRole = UserRole.CITIZEN,
    val reportedCount: Int = 0,
    val verifiedCount: Int = 0,
    val trustScore: Int = 75,
    val communityScore: Int = 50,
    val heroLevel: String = "Citizen"
)

@Entity(tableName = "issues")
data class InfrastructureIssue(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val category: IssueCategory,
    val latitude: Double,
    val longitude: Double,
    val locationName: String,
    val reporterId: String,
    val reporterName: String,
    val imageUrl: String? = null,
    val status: IssueStatus = IssueStatus.REPORTED,
    val timestamp: Long = System.currentTimeMillis(),
    val severity: SeverityLevel = SeverityLevel.MEDIUM,
    val upvotesCount: Int = 0,
    val verificationsCount: Int = 0,
    val verificationThreshold: Int = 3,
    val resolvedImage: String? = null,
    val resolutionNotes: String? = null,
    val aiSummary: String? = null,
    val confidenceScore: String? = null,
    val suggestedPriority: String? = null,
    val assignedWorkerId: String? = null,
    val helperBeforeImage: String? = null,
    val helperAfterImage: String? = null,
    val helperImprovementScore: Int? = null,
    val helperConfidenceScore: Int? = null,
    val helperFraudRiskScore: Int? = null,
    val helperValidationFeedback: String? = null,
    val communityAssistedBadgeAwarded: Boolean = false
)

@Entity(tableName = "verification_votes")
data class VerificationVote(
    @PrimaryKey val id: String,
    val issueId: String,
    val verifierId: String,
    val verifierName: String,
    val isLegitimate: Boolean,
    val comment: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val verificationType: String = "CONFIRM", // "CONFIRM", "NOT_PRESENT", "EVIDENCE"
    val evidenceImageUrl: String? = null
)

@Entity(tableName = "community_posts")
data class CommunityPost(
    @PrimaryKey val id: String,
    val authorId: String,
    val authorName: String,
    val authorAvatar: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val likesCount: Int = 0,
    val category: String = "General"
)

@Entity(tableName = "notifications")
data class CivicNotification(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val message: String,
    val type: String, // "alert", "status_change", "reputation_gain"
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

data class HyperlocalSupportInfo(
    val division: String,
    val ward: String,
    val officerName: String,
    val officerPhone: String,
    val officerEmail: String,
    val officeAddress: String,
    val workingHours: String = "9:00 AM - 5:00 PM (Mon-Fri)"
)

fun getHyperlocalSupport(latitude: Double, longitude: Double): HyperlocalSupportInfo {
    return when {
        longitude < -122.430 -> HyperlocalSupportInfo(
            division = "Jalandhar West Division",
            ward = "Ward 12 Updates",
            officerName = "Officer James Vance",
            officerPhone = "+91 (987) 654-3210",
            officerEmail = "jvance@civicdex.gov.in",
            officeAddress = "West Division Office, Sector 4, Jalandhar",
            workingHours = "8:30 AM - 4:30 PM (Mon-Sat)"
        )
        longitude > -122.410 -> HyperlocalSupportInfo(
            division = "Jalandhar East Division",
            ward = "Sector 7 Community",
            officerName = "Officer Sarah Jenkins",
            officerPhone = "+91 (987) 654-3211",
            officerEmail = "sjenkins@civicdex.gov.in",
            officeAddress = "East Division Office, Cantt Road, Jalandhar",
            workingHours = "9:00 AM - 5:00 PM (Mon-Fri)"
        )
        else -> HyperlocalSupportInfo(
            division = "Jalandhar North Division",
            ward = "Ward 4 (Downtown Core)",
            officerName = "Director Marcus Vance",
            officerPhone = "+91 (987) 654-3000",
            officerEmail = "mvance@civicdex.gov.in",
            officeAddress = "Municipal HQ, Court Road, Jalandhar North",
            workingHours = "9:00 AM - 5:30 PM (Mon-Fri)"
        )
    }
}

data class CivicTeam(
    val id: String,
    val name: String,
    val division: String,
    val leadWorkerId: String,
    val members: List<String>,
    val specialties: List<String>,
    val status: String,
    val activeAssignmentId: String? = null
)

data class TeamAssignment(
    val id: String,
    val issueId: String,
    val teamId: String,
    val assignedBy: String,
    val status: String, // "ASSIGNED", "ACCEPTED", "IN_PROGRESS", "AWAITING_VERIFICATION", "COMPLETED", "CLOSED"
    val assignedAt: Long,
    val estimatedCompletionTime: Long,
    val workerConfirmations: Map<String, Boolean> = emptyMap()
)

data class WorkerAvailability(
    val id: String,
    val name: String,
    val trade: String,
    val isAvailable: Boolean,
    val currentWorkloadCount: Int,
    val latitude: Double,
    val longitude: Double,
    val division: String
)

data class CommunityGroup(
    val id: String,
    val name: String,
    val description: String,
    val category: String, // "NEIGHBORHOOD", "VOLUNTEER", "STREET", "WELFARE", "ENVIRONMENTAL"
    val division: String,
    val creatorId: String,
    val moderators: List<String> = emptyList(),
    val isPublic: Boolean = true,
    val inviteCode: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class GroupMember(
    val id: String,
    val groupId: String,
    val userId: String,
    val role: String, // "OWNER", "MODERATOR", "MEMBER"
    val status: String, // "PENDING", "APPROVED", "BLOCKED"
    val joinedAt: Long = System.currentTimeMillis()
)

data class CivicMessage(
    val id: String,
    val channelOrGroupId: String,
    val senderId: String,
    val senderName: String,
    val senderRole: UserRole,
    val senderAvatarUrl: String,
    val text: String,
    val imageUrl: String? = null,
    val documentUrl: String? = null,
    val voiceNoteUrl: String? = null,
    val voiceNoteDuration: Int = 0,
    val reactions: Map<String, List<String>> = emptyMap(), // reaction -> userIds
    val replyToId: String? = null,
    val isPinned: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class DivisionChannel(
    val id: String,
    val name: String,
    val description: String,
    val division: String,
    val officialOfficerId: String,
    val memberCount: Int = 0
)

data class OfficialAnnouncement(
    val id: String,
    val channelId: String,
    val postedBy: String,
    val title: String,
    val content: String,
    val category: String, // "ROAD_CLOSURES", "WATER_SUPPLY", "REPAIR_UPDATES", "EMERGENCY_ALERTS", "COMMUNITY_NOTICES"
    val imageUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class TaskHistoryItem(
    val id: String,
    val issueId: String,
    val status: String,
    val assignedTeamId: String,
    val workersInvolved: List<String>,
    val beforeImageUrl: String?,
    val afterImageUrl: String?,
    val improvementScore: Int,
    val confidenceScore: Int,
    val fraudRiskScore: Int,
    val feedback: String,
    val completedAt: Long,
    val closedAt: Long
)

data class AiTeamRecommendation(
    val id: String,
    val issueId: String,
    val workerId: String,
    val workerName: String,
    val trade: String,
    val distanceKm: Double,
    val estimatedHours: Int,
    val reason: String
)
