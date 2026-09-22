package com.example.data.local

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IssueDao {
    @Query("SELECT * FROM issues ORDER BY timestamp DESC")
    fun getAllIssues(): Flow<List<InfrastructureIssue>>

    @Query("SELECT * FROM issues WHERE id = :id")
    suspend fun getIssueById(id: String): InfrastructureIssue?

    @Query("SELECT * FROM issues WHERE category = :category ORDER BY timestamp DESC")
    fun getIssuesByCategory(category: IssueCategory): Flow<List<InfrastructureIssue>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIssue(issue: InfrastructureIssue)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIssues(issues: List<InfrastructureIssue>)

    @Update
    suspend fun updateIssue(issue: InfrastructureIssue)

    @Query("UPDATE issues SET upvotesCount = upvotesCount + 1 WHERE id = :id")
    suspend fun incrementUpvote(id: String)

    @Query("UPDATE issues SET verificationsCount = verificationsCount + 1 WHERE id = :id")
    suspend fun incrementVerificationCount(id: String)

    @Query("DELETE FROM issues WHERE id = :id")
    suspend fun deleteIssueById(id: String)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id")
    fun getUserByIdFlow(id: String): Flow<User?>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Query("UPDATE users SET reputationPoints = reputationPoints + :points WHERE id = :userId")
    suspend fun updateReputation(userId: String, points: Int)

    @Query("UPDATE users SET reportedCount = reportedCount + 1 WHERE id = :userId")
    suspend fun incrementReportCount(userId: String)

    @Query("UPDATE users SET verifiedCount = verifiedCount + 1 WHERE id = :userId")
    suspend fun incrementVerifyCount(userId: String)
}

@Dao
interface CommunityDao {
    @Query("SELECT * FROM community_posts ORDER BY timestamp DESC")
    fun getAllPosts(): Flow<List<CommunityPost>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: CommunityPost)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<CommunityPost>)

    @Query("UPDATE community_posts SET likesCount = likesCount + 1 WHERE id = :id")
    suspend fun likePost(id: String)

    @Query("SELECT * FROM verification_votes WHERE issueId = :issueId")
    fun getVotesForIssue(issueId: String): Flow<List<VerificationVote>>

    @Query("SELECT * FROM verification_votes WHERE issueId = :issueId")
    suspend fun getVotesForIssueDirect(issueId: String): List<VerificationVote>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVote(vote: VerificationVote)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY timestamp DESC")
    fun getNotificationsForUser(userId: String): Flow<List<CivicNotification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: CivicNotification)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<CivicNotification>)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)
}
