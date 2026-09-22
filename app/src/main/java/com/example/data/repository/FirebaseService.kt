package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.model.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

object FirebaseService {
    private const val TAG = "FirebaseService"
    private var isFirebaseAvailable = false
    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null
    private var storage: FirebaseStorage? = null

    fun initialize(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            auth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance()
            storage = FirebaseStorage.getInstance()
            isFirebaseAvailable = true
            Log.d(TAG, "Firebase SDK initialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase SDK initialization skipped (missing google-services.json or options). Falling back gracefully.", e)
            isFirebaseAvailable = false
        }
    }

    fun isAvailable(): Boolean = isFirebaseAvailable

    // --- Authentication ---
    suspend fun signIn(email: String, role: UserRole): User {
        val authInstance = auth ?: throw IllegalStateException("Firebase Auth not initialized")
        
        val result = try {
            authInstance.signInWithEmailAndPassword(email, "CivicDexFallbackP@ssword").await()
        } catch (e: Exception) {
            authInstance.createUserWithEmailAndPassword(email, "CivicDexFallbackP@ssword").await()
        }
        
        val firebaseUser = result.user ?: throw Exception("Auth user is null")
        
        // Fetch or create Firestore profile
        var userProfile = getUserProfile(firebaseUser.uid)
        if (userProfile == null) {
            val formattedName = email.substringBefore("@").replaceFirstChar { it.uppercase() }
            userProfile = User(
                id = firebaseUser.uid,
                name = formattedName,
                email = email,
                avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
                reputationPoints = 15,
                role = role
            )
            saveUserProfile(userProfile)
        }
        return userProfile
    }

    suspend fun register(name: String, email: String, role: UserRole): User {
        val authInstance = auth ?: throw IllegalStateException("Firebase Auth not initialized")
        val result = authInstance.createUserWithEmailAndPassword(email, "CivicDexFallbackP@ssword").await()
        val firebaseUser = result.user ?: throw Exception("Auth registration returned null user")
        
        val newUser = User(
            id = firebaseUser.uid,
            name = name,
            email = email,
            avatarUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150",
            reputationPoints = 25,
            role = role
        )
        saveUserProfile(newUser)
        return newUser
    }

    suspend fun signInWithGoogleToken(idToken: String, role: UserRole): User {
        val authInstance = auth ?: throw IllegalStateException("Firebase Auth not initialized")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = authInstance.signInWithCredential(credential).await()
        val firebaseUser = result.user ?: throw Exception("Google sign in returned null user")
        
        var userProfile = getUserProfile(firebaseUser.uid)
        if (userProfile == null) {
            userProfile = User(
                id = firebaseUser.uid,
                name = firebaseUser.displayName ?: "Google Citizen",
                email = firebaseUser.email ?: "google_citizen@gmail.com",
                avatarUrl = firebaseUser.photoUrl?.toString() ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
                reputationPoints = 20,
                role = role
            )
            saveUserProfile(userProfile)
        }
        return userProfile
    }

    fun signOut() {
        auth?.signOut()
    }

    // --- Firestore Operations ---

    // 1. User collection
    suspend fun getUserProfile(userId: String): User? {
        val dbInstance = firestore ?: return null
        val doc = dbInstance.collection("users").document(userId).get().await()
        return if (doc.exists()) {
            val data = doc.data ?: return null
            User(
                id = userId,
                name = data["name"] as? String ?: "",
                email = data["email"] as? String ?: "",
                avatarUrl = data["avatarUrl"] as? String ?: "",
                reputationPoints = (data["reputationPoints"] as? Long)?.toInt() ?: 10,
                role = UserRole.valueOf(data["role"] as? String ?: "CITIZEN"),
                reportedCount = (data["reportedCount"] as? Long)?.toInt() ?: 0,
                verifiedCount = (data["verifiedCount"] as? Long)?.toInt() ?: 0,
                trustScore = (data["trustScore"] as? Long)?.toInt() ?: 75,
                communityScore = (data["communityScore"] as? Long)?.toInt() ?: 50,
                heroLevel = data["heroLevel"] as? String ?: "Citizen"
            )
        } else {
            null
        }
    }

    suspend fun saveUserProfile(user: User) {
        val dbInstance = firestore ?: return
        val data = mapOf(
            "id" to user.id,
            "name" to user.name,
            "email" to user.email,
            "avatarUrl" to user.avatarUrl,
            "reputationPoints" to user.reputationPoints,
            "role" to user.role.name,
            "reportedCount" to user.reportedCount,
            "verifiedCount" to user.verifiedCount,
            "trustScore" to user.trustScore,
            "communityScore" to user.communityScore,
            "heroLevel" to user.heroLevel
        )
        dbInstance.collection("users").document(user.id).set(data).await()
    }

    suspend fun incrementUserStat(userId: String, field: String, incrementValue: Int = 1) {
        val dbInstance = firestore ?: return
        val docRef = dbInstance.collection("users").document(userId)
        dbInstance.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val currentValue = snapshot.getLong(field) ?: 0L
            transaction.update(docRef, field, currentValue + incrementValue)
        }.await()
    }

    // 2. Issues collection
    suspend fun saveIssue(issue: InfrastructureIssue) {
        val dbInstance = firestore ?: return
        val data = mapOf(
            "id" to issue.id,
            "title" to issue.title,
            "description" to issue.description,
            "category" to issue.category.name,
            "latitude" to issue.latitude,
            "longitude" to issue.longitude,
            "locationName" to issue.locationName,
            "reporterId" to issue.reporterId,
            "reporterName" to issue.reporterName,
            "imageUrl" to issue.imageUrl,
            "status" to issue.status.name,
            "timestamp" to issue.timestamp,
            "severity" to issue.severity.name,
            "upvotesCount" to issue.upvotesCount,
            "verificationsCount" to issue.verificationsCount,
            "verificationThreshold" to issue.verificationThreshold,
            "resolvedImage" to issue.resolvedImage,
            "resolutionNotes" to issue.resolutionNotes,
            "aiSummary" to issue.aiSummary,
            "confidenceScore" to issue.confidenceScore,
            "suggestedPriority" to issue.suggestedPriority,
            "assignedWorkerId" to issue.assignedWorkerId
        )
        dbInstance.collection("issues").document(issue.id).set(data).await()
    }

    suspend fun getIssues(): List<InfrastructureIssue> {
        val dbInstance = firestore ?: return emptyList()
        val query = dbInstance.collection("issues").get().await()
        return query.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            InfrastructureIssue(
                id = doc.id,
                title = data["title"] as? String ?: "",
                description = data["description"] as? String ?: "",
                category = IssueCategory.valueOf(data["category"] as? String ?: "ROADS"),
                latitude = data["latitude"] as? Double ?: 0.0,
                longitude = data["longitude"] as? Double ?: 0.0,
                locationName = data["locationName"] as? String ?: "",
                reporterId = data["reporterId"] as? String ?: "",
                reporterName = data["reporterName"] as? String ?: "",
                imageUrl = data["imageUrl"] as? String,
                status = IssueStatus.valueOf(data["status"] as? String ?: "REPORTED"),
                timestamp = data["timestamp"] as? Long ?: System.currentTimeMillis(),
                severity = SeverityLevel.valueOf(data["severity"] as? String ?: "MEDIUM"),
                upvotesCount = (data["upvotesCount"] as? Long)?.toInt() ?: 0,
                verificationsCount = (data["verificationsCount"] as? Long)?.toInt() ?: 0,
                verificationThreshold = (data["verificationThreshold"] as? Long)?.toInt() ?: 3,
                resolvedImage = data["resolvedImage"] as? String,
                resolutionNotes = data["resolutionNotes"] as? String,
                aiSummary = data["aiSummary"] as? String,
                confidenceScore = data["confidenceScore"] as? String,
                suggestedPriority = data["suggestedPriority"] as? String,
                assignedWorkerId = data["assignedWorkerId"] as? String
            )
        }
    }

    // 3. Verifications collection
    suspend fun saveVerification(vote: VerificationVote) {
        val dbInstance = firestore ?: return
        val data = mapOf(
            "id" to vote.id,
            "issueId" to vote.issueId,
            "verifierId" to vote.verifierId,
            "verifierName" to vote.verifierName,
            "isLegitimate" to vote.isLegitimate,
            "comment" to vote.comment,
            "timestamp" to vote.timestamp,
            "verificationType" to vote.verificationType,
            "evidenceImageUrl" to vote.evidenceImageUrl
        )
        dbInstance.collection("verifications").document(vote.id).set(data).await()
    }

    suspend fun getVerifications(issueId: String): List<VerificationVote> {
        val dbInstance = firestore ?: return emptyList()
        val query = dbInstance.collection("verifications")
            .whereEqualTo("issueId", issueId)
            .get().await()
        return query.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            VerificationVote(
                id = doc.id,
                issueId = data["issueId"] as? String ?: "",
                verifierId = data["verifierId"] as? String ?: "",
                verifierName = data["verifierName"] as? String ?: "",
                isLegitimate = data["isLegitimate"] as? Boolean ?: true,
                comment = data["comment"] as? String,
                timestamp = data["timestamp"] as? Long ?: System.currentTimeMillis(),
                verificationType = data["verificationType"] as? String ?: "CONFIRM",
                evidenceImageUrl = data["evidenceImageUrl"] as? String
            )
        }
    }

    // 4. Notifications collection
    suspend fun saveNotification(notification: CivicNotification) {
        val dbInstance = firestore ?: return
        val data = mapOf(
            "id" to notification.id,
            "userId" to notification.userId,
            "title" to notification.title,
            "message" to notification.message,
            "type" to notification.type,
            "timestamp" to notification.timestamp,
            "isRead" to notification.isRead
        )
        dbInstance.collection("notifications").document(notification.id).set(data).await()
    }

    suspend fun getNotifications(userId: String): List<CivicNotification> {
        val dbInstance = firestore ?: return emptyList()
        val query = dbInstance.collection("notifications")
            .whereEqualTo("userId", userId)
            .get().await()
        return query.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            CivicNotification(
                id = doc.id,
                userId = data["userId"] as? String ?: "",
                title = data["title"] as? String ?: "",
                message = data["message"] as? String ?: "",
                type = data["type"] as? String ?: "",
                timestamp = data["timestamp"] as? Long ?: System.currentTimeMillis(),
                isRead = data["isRead"] as? Boolean ?: false
            )
        }
    }

    // --- Storage ---
    suspend fun uploadImage(uri: Uri): String {
        val storageInstance = storage ?: throw IllegalStateException("Firebase Storage not initialized")
        val filename = "images/${UUID.randomUUID()}.jpg"
        val ref = storageInstance.reference.child(filename)
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }
}
