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
    val officeAddress: String
)

fun getHyperlocalSupport(latitude: Double, longitude: Double): HyperlocalSupportInfo {
    return when {
        longitude < -72.930 -> HyperlocalSupportInfo(
            division = "Division A (West Haven)",
            ward = "Ward 12",
            officerName = "Officer James Vance",
            officerPhone = "+1 (203) 946-6201",
            officerEmail = "jvance@newhaven.gov",
            officeAddress = "200 Orange St, West Haven, CT"
        )
        longitude > -72.915 -> HyperlocalSupportInfo(
            division = "Division C (East Rock / Fair Haven)",
            ward = "Ward 4",
            officerName = "Officer Sarah Jenkins",
            officerPhone = "+1 (203) 946-8152",
            officerEmail = "sjenkins@newhaven.gov",
            officeAddress = "12 Fair Haven Rd, New Haven, CT"
        )
        else -> HyperlocalSupportInfo(
            division = "Division B (Downtown Central)",
            ward = "Ward 7",
            officerName = "Director Marcus Vance",
            officerPhone = "+1 (203) 946-3000",
            officerEmail = "mvance@newhaven.gov",
            officeAddress = "165 Church Street, New Haven, CT"
        )
    }
}
