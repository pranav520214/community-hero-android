package com.example.data.local

import android.content.Context
import androidx.room.*
import com.example.data.model.*

class CivicConverters {
    @TypeConverter
    fun fromUserRole(value: UserRole): String = value.name

    @TypeConverter
    fun toUserRole(value: String): UserRole = UserRole.valueOf(value)

    @TypeConverter
    fun fromIssueCategory(value: IssueCategory): String = value.name

    @TypeConverter
    fun toIssueCategory(value: String): IssueCategory = IssueCategory.valueOf(value)

    @TypeConverter
    fun fromIssueStatus(value: IssueStatus): String = value.name

    @TypeConverter
    fun toIssueStatus(value: String): IssueStatus = IssueStatus.valueOf(value)

    @TypeConverter
    fun fromSeverityLevel(value: SeverityLevel): String = value.name

    @TypeConverter
    fun toSeverityLevel(value: String): SeverityLevel = SeverityLevel.valueOf(value)
}

@Database(
    entities = [
        User::class,
        InfrastructureIssue::class,
        VerificationVote::class,
        CommunityPost::class,
        CivicNotification::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(CivicConverters::class)
abstract class CivicDatabase : RoomDatabase() {
    abstract fun issueDao(): IssueDao
    abstract fun userDao(): UserDao
    abstract fun communityDao(): CommunityDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: CivicDatabase? = null

        fun getDatabase(context: Context): CivicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CivicDatabase::class.java,
                    "civicdex_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
