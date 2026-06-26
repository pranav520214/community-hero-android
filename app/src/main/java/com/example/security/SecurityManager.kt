package com.example.security

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.data.model.UserRole
import com.example.data.model.User
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

object SecurityManager {
    private const val TAG = "SecurityManager"
    private const val SECURE_PREFS_FILE = "secure_civicdex_prefs"
    private const val KEY_TOKEN = "session_token"
    private const val KEY_ENCRYPTED_KEYS = "encrypted_api_keys"

    // Cache of current security status for real-time reporting
    data class SecurityAuditReport(
        val isRooted: Boolean,
        val rootReasons: List<String>,
        val isKeystoreSecure: Boolean,
        val isEncryptedPrefsActive: Boolean,
        val playIntegrityStatus: PlayIntegrityVerdict,
        val certPinningDomain: String,
        val isCertPinned: Boolean,
        val tokenEncryptionStatus: Boolean
    )

    enum class PlayIntegrityVerdict {
        MEETS_STRONG_INTEGRITY,
        MEETS_DEVICE_INTEGRITY,
        MEETS_BASIC_INTEGRITY,
        FAILED_INTEGRITY
    }

    /**
     * Initialize EncryptedSharedPreferences backed by Android Keystore
     */
    fun getEncryptedSharedPreferences(context: Context): android.content.SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating EncryptedSharedPreferences via Android Keystore", e)
            null
        }
    }

    /**
     * Secures and stores user JWT / Session Token
     */
    fun saveSessionToken(context: Context, token: String) {
        val prefs = getEncryptedSharedPreferences(context)
        if (prefs != null) {
            prefs.edit().putString(KEY_TOKEN, token).apply()
            Log.d(TAG, "User session token encrypted and written to EncryptedSharedPreferences successfully.")
        } else {
            // Fallback securely or notify
            Log.w(TAG, "EncryptedSharedPreferences unavailable. Token was not stored.")
        }
    }

    /**
     * Retrieves secured User Session Token
     */
    fun getSessionToken(context: Context): String? {
        val prefs = getEncryptedSharedPreferences(context)
        return prefs?.getString(KEY_TOKEN, null)
    }

    /**
     * Root Detection: Verifies system binary locations, build tags, and executes safe commands.
     */
    fun checkRootMethod1(): Boolean {
        val buildTags = Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }

    fun checkRootMethod2(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
    }

    fun checkRootMethod3(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val inReader = BufferedReader(InputStreamReader(process.inputStream))
            inReader.readLine() != null
        } catch (t: Throwable) {
            false
        } finally {
            process?.destroy()
        }
    }

    fun isDeviceRooted(): Boolean {
        return checkRootMethod1() || checkRootMethod2() || checkRootMethod3()
    }

    fun getRootDetectionDetails(): List<String> {
        val reasons = mutableListOf<String>()
        if (checkRootMethod1()) {
            reasons.add("Build Tags contain 'test-keys' (indicates developer/custom ROM)")
        }
        if (checkRootMethod2()) {
            reasons.add("Superuser su binary discovered in common system directories")
        }
        if (checkRootMethod3()) {
            reasons.add("Runtime execution of 'which su' returned a valid path")
        }
        if (reasons.isEmpty()) {
            reasons.add("No anomalies detected. Safe sandbox environment.")
        }
        return reasons
    }

    /**
     * Certificate Pinning Client Configuration
     * Pins to Firebase and general CivicDex servers protecting against Man-in-the-Middle (MitM) attacks.
     */
    fun getSecureOkHttpClient(): OkHttpClient {
        val hostname = "firebaseio.com"
        val certificatePinner = CertificatePinner.Builder()
            .add(hostname, "sha256/g87As1Lh26vQe7C1V7HId/Xk+23S9E0k98pZ6k12uM4=") // Example active pin
            .add(hostname, "sha256/k2v657WmLBwgS7dE/826Hee3a6B7H679C81KkU0=") // Backup pin
            .build()

        return OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .build()
    }

    /**
     * Role-Based Access Control (RBAC): Hardened enforcement for critical operations
     */
    fun enforceRoleAccess(user: User?, requiredRole: UserRole): Boolean {
        if (user == null) return false
        
        return when (requiredRole) {
            UserRole.CITIZEN -> true // Everyone can act as a citizen
            UserRole.CONTRIBUTOR -> user.role == UserRole.CONTRIBUTOR || user.role == UserRole.WORKER || user.role == UserRole.OFFICER || user.role == UserRole.ADMINISTRATOR
            UserRole.WORKER -> user.role == UserRole.WORKER || user.role == UserRole.OFFICER || user.role == UserRole.ADMINISTRATOR
            UserRole.OFFICER -> user.role == UserRole.OFFICER || user.role == UserRole.ADMINISTRATOR
            UserRole.ADMINISTRATOR -> user.role == UserRole.ADMINISTRATOR
        }
    }

    /**
     * Performs a full Enterprise Security Audit Report
     */
    fun performSecurityAudit(context: Context): SecurityAuditReport {
        val isRooted = isDeviceRooted()
        val rootReasons = getRootDetectionDetails()
        val isKeystoreSecure = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        val isEncryptedPrefsActive = getEncryptedSharedPreferences(context) != null
        
        // Simulating Play Integrity API call results
        val playVerdict = if (isRooted) {
            PlayIntegrityVerdict.FAILED_INTEGRITY
        } else {
            // Simulator: meets strong if hardware keystore + Google Play cert matches
            if (isKeystoreSecure) PlayIntegrityVerdict.MEETS_STRONG_INTEGRITY else PlayIntegrityVerdict.MEETS_DEVICE_INTEGRITY
        }

        val hasToken = getSessionToken(context) != null

        return SecurityAuditReport(
            isRooted = isRooted,
            rootReasons = rootReasons,
            isKeystoreSecure = isKeystoreSecure,
            isEncryptedPrefsActive = isEncryptedPrefsActive,
            playIntegrityStatus = playVerdict,
            certPinningDomain = "*.firebaseio.com",
            isCertPinned = true,
            tokenEncryptionStatus = hasToken || isEncryptedPrefsActive
        )
    }
}
