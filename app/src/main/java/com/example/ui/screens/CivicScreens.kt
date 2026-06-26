package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.*
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.CivicViewModel
import com.example.ui.viewmodel.ReportUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.ui.theme.frostedGlass

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke

// --- Helper Date Formatter ---
fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun StatusBadge(status: IssueStatus) {
    val (color, text) = when (status) {
        IssueStatus.REPORTED -> Pair(MaterialTheme.colorScheme.error, "Reported")
        IssueStatus.VERIFYING -> Pair(MaterialTheme.colorScheme.tertiary, "Verifying")
        IssueStatus.VERIFIED -> Pair(MaterialTheme.colorScheme.primary, "Verified")
        IssueStatus.IN_PROGRESS -> Pair(Color(0xFFF57C00), "In Progress")
        IssueStatus.RESOLVED -> Pair(Color(0xFF388E3C), "Resolved")
        IssueStatus.REJECTED -> Pair(Color(0xFF757575), "Flagged")
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SeverityBadge(severity: SeverityLevel) {
    val color = when (severity) {
        SeverityLevel.LOW -> Color(0xFF1E88E5)
        SeverityLevel.MEDIUM -> Color(0xFFFBC02D)
        SeverityLevel.HIGH -> Color(0xFFF57C00)
        SeverityLevel.CRITICAL -> Color(0xFFD32F2F)
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = severity.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ==========================================
// 1. AUTH SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(viewModel: CivicViewModel) {
    var isRegister by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var idInput by remember { mutableStateOf("") }
    
    var selectedRole by remember { mutableStateOf(UserRole.CITIZEN) }
    var authTab by remember { mutableStateOf("GOOGLE") } // "GOOGLE", "PHONE", "ID_PWD", "EMAIL_PWD"
    var otpSent by remember { mutableStateOf(false) }
    
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var recoveryEmail by remember { mutableStateOf("") }
    var recoveryPhone by remember { mutableStateOf("") }
    var recoverySentMessage by remember { mutableStateOf<String?>(null) }
    var formatError by remember { mutableStateOf<String?>(null) }
    
    val uiState by viewModel.authUiState.collectAsState()
    val context = LocalContext.current

    // Dynamically adjust authentication tabs based on role choice
    LaunchedEffect(selectedRole) {
        formatError = null
        authTab = when (selectedRole) {
            UserRole.CITIZEN, UserRole.CONTRIBUTOR -> "GOOGLE"
            UserRole.WORKER, UserRole.OFFICER -> "ID_PWD"
            UserRole.ADMINISTRATOR -> "EMAIL_PWD"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .frostedGlass(RoundedCornerShape(28.dp))
                .testTag("auth_card"),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with custom icon and title
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = "CivicDex Logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "CivicDex",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Enterprise-Grade Strategic Civic Portal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))

                // Role Selection Tab Bar
                Text(
                    text = "SELECT SECURITY PROFILE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                // Horizontal scrolling chip row for standard/advanced roles
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(UserRole.values()) { role ->
                        val isSelected = selectedRole == role
                        val label = when (role) {
                            UserRole.CITIZEN -> "Citizen"
                            UserRole.CONTRIBUTOR -> "Helper"
                            UserRole.WORKER -> "Worker"
                            UserRole.OFFICER -> "Officer"
                            UserRole.ADMINISTRATOR -> "Admin"
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedRole = role },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("role_chip_${role.name.lowercase()}"),
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Filled.Check, null, modifier = Modifier.size(12.dp)) }
                            } else null
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Render dynamic authentication inputs depending on selected tab
                when (authTab) {
                    "GOOGLE" -> {
                        // Toggle between Google and Phone Sign-in for Citizens/Helpers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { authTab = "GOOGLE" },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (authTab == "GOOGLE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text("Google", style = MaterialTheme.typography.labelMedium)
                            }
                            Button(
                                onClick = { authTab = "PHONE" },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (authTab == "PHONE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text("Phone OTP", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))

                        // Social Google Sign-In Button
                        Button(
                            onClick = {
                                // Simulate OAuth token sign in
                                val simulatedEmail = if (selectedRole == UserRole.CITIZEN) "citizen1@gmail.com" else "helper1@civicdex.org"
                                Toast.makeText(context, "Sign in as $simulatedEmail via Google Account Link", Toast.LENGTH_SHORT).show()
                                viewModel.login(simulatedEmail, selectedRole)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("google_sso_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stream, // Styled as generic platform connector
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Sign In with Google SSO",
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    "PHONE" -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { authTab = "GOOGLE" },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Text("Google", style = MaterialTheme.typography.labelMedium)
                            }
                            Button(
                                onClick = { authTab = "PHONE" },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Phone OTP", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // Phone text field
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("Phone Number") },
                            placeholder = { Text("+1 (555) 019-2834") },
                            leadingIcon = { Icon(Icons.Filled.Phone, null) },
                            modifier = Modifier.fillMaxWidth().testTag("auth_phone_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (otpSent) {
                            OutlinedTextField(
                                value = otpCode,
                                onValueChange = { otpCode = it },
                                label = { Text("6-Digit OTP Verification Code") },
                                placeholder = { Text("123456") },
                                leadingIcon = { Icon(Icons.Filled.Lock, null) },
                                modifier = Modifier.fillMaxWidth().testTag("auth_otp_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (!otpSent) {
                                    if (phoneNumber.length >= 8) {
                                        otpSent = true
                                        Toast.makeText(context, "Verification code sent to $phoneNumber", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Please enter a valid phone number", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    if (otpCode.length == 6) {
                                        val derivedEmail = "phone-${phoneNumber.filter { it.isDigit() }}@civicdex.org"
                                        viewModel.login(derivedEmail, selectedRole)
                                    } else {
                                        Toast.makeText(context, "OTP must be exactly 6 digits", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("phone_otp_action_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (otpSent) "Verify OTP & Sign In" else "Generate Secure OTP")
                        }
                    }

                    "ID_PWD" -> {
                        // Worker / Officer login with structured identifier checks
                        val prefixHint = if (selectedRole == UserRole.WORKER) "e.g. PB08-D02-ELC-00125" else "e.g. PB08-D02-OFC-00012"
                        val idLabel = if (selectedRole == UserRole.WORKER) "Worker ID" else "Officer ID"

                        OutlinedTextField(
                            value = idInput,
                            onValueChange = { 
                                idInput = it 
                                formatError = null
                            },
                            label = { Text(idLabel) },
                            placeholder = { Text(prefixHint) },
                            leadingIcon = { Icon(Icons.Filled.Badge, null) },
                            modifier = Modifier.fillMaxWidth().testTag("auth_id_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Secure Password") },
                            leadingIcon = { Icon(Icons.Filled.Lock, null) },
                            modifier = Modifier.fillMaxWidth().testTag("auth_pwd_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        
                        if (formatError != null) {
                            Text(
                                text = formatError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                formatError = null
                                // Format assertions
                                if (selectedRole == UserRole.WORKER) {
                                    // WORKER ID pattern: CITYCODE-ZONE-DIVISION-TRADE-UNIQUEID (e.g. PB08-D02-ELC-00125)
                                    val regex = Regex("^[A-Z0-9]{4}-[A-Z0-9]{3}-[A-Z0-9]{3}-[0-9]{5}$")
                                    val fallbackRegex = Regex("^[A-Za-z0-9]+-[A-Za-z0-9]+-[A-Za-z0-9]+-[0-9]+$")
                                    if (!idInput.matches(regex) && !idInput.matches(fallbackRegex)) {
                                        formatError = "Invalid Format! Required format: CITYCODE-ZONE-DIVISION-TRADE-UNIQUEID (e.g. PB08-D02-ELC-00125)"
                                        return@Button
                                    }
                                } else if (selectedRole == UserRole.OFFICER) {
                                    // OFFICER ID pattern: CITYCODE-ZONE-DIVISION-OFC-UNIQUEID (e.g. PB08-D02-OFC-00012)
                                    val regex = Regex("^[A-Z0-9]{4}-[A-Z0-9]{3}-OFC-[0-9]{5}$")
                                    val fallbackRegex = Regex("^[A-Za-z0-9]+-[A-Za-z0-9]+-OFC-[0-9]+$")
                                    if (!idInput.matches(regex) && !idInput.matches(fallbackRegex)) {
                                        formatError = "Invalid Format! Required format: CITYCODE-ZONE-DIVISION-OFC-UNIQUEID (e.g. PB08-D02-OFC-00012)"
                                        return@Button
                                    }
                                }

                                if (password.length < 4) {
                                    formatError = "Password must be at least 4 characters for testing"
                                    return@Button
                                }

                                // Derive functional simulated email matching ID structure
                                val functionalEmail = "${idInput.lowercase()}@civicdex.org"
                                viewModel.login(functionalEmail, selectedRole)
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("id_pwd_login_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Secure Login")
                        }
                    }

                    "EMAIL_PWD" -> {
                        // Admin standard logins
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Administrator Email") },
                            leadingIcon = { Icon(Icons.Filled.Email, null) },
                            modifier = Modifier.fillMaxWidth().testTag("auth_admin_email"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Admin Console Password") },
                            leadingIcon = { Icon(Icons.Filled.Lock, null) },
                            modifier = Modifier.fillMaxWidth().testTag("auth_admin_pwd"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (email.contains("@") && password.length >= 4) {
                                    viewModel.login(email, selectedRole)
                                } else {
                                    Toast.makeText(context, "Valid credentials required", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Administrative Sign In")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Account Recovery text links
                TextButton(
                    onClick = { 
                        showRecoveryDialog = true 
                        recoverySentMessage = null
                    },
                    modifier = Modifier.testTag("forgot_credentials_btn")
                ) {
                    Text("Forgot credentials? Recover account", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }

                if (uiState is AuthUiState.Error) {
                    Text(
                        text = (uiState as AuthUiState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }
    }

    // Account Recovery dialog
    if (showRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { showRecoveryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.SystemUpdateAlt, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Enterprise Identity Recovery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter your verified security recovery email or phone number below to bypass lock.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedTextField(
                        value = recoveryEmail,
                        onValueChange = { 
                            recoveryEmail = it
                            recoveryPhone = "" 
                        },
                        label = { Text("Email Recovery") },
                        placeholder = { Text("e.g. director@newhaven.gov") },
                        leadingIcon = { Icon(Icons.Filled.Email, null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text("— OR —", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))

                    OutlinedTextField(
                        value = recoveryPhone,
                        onValueChange = { 
                            recoveryPhone = it
                            recoveryEmail = ""
                        },
                        label = { Text("SMS Phone Recovery OTP") },
                        placeholder = { Text("e.g. +1 (555) 012-3456") },
                        leadingIcon = { Icon(Icons.Filled.Phone, null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (recoverySentMessage != null) {
                        Text(
                            text = recoverySentMessage!!,
                            color = Color(0xFF388E3C),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (recoveryEmail.isNotEmpty()) {
                            recoverySentMessage = "Password reset link cryptographically transmitted to $recoveryEmail."
                        } else if (recoveryPhone.isNotEmpty()) {
                            recoverySentMessage = "OTP security override code dispatched to $recoveryPhone via secure gate."
                        } else {
                            Toast.makeText(context, "Provide either email or phone", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Trigger Recovery")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecoveryDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

// ==========================================
// 2. HOME SCREEN (DASHBOARD)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: CivicViewModel, onNavigateToReport: () -> Unit) {
    HomeMapScreen(viewModel = viewModel, onNavigateToReport = onNavigateToReport)
}

@Composable
fun IssueItemCard(
    issue: InfrastructureIssue,
    onUpvote: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .frostedGlass(RoundedCornerShape(24.dp))
            .testTag("issue_card_${issue.id}"),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Status + Category
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusBadge(issue.status)
                    SeverityBadge(issue.severity)
                }
                Text(
                    text = issue.category.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title + description
            Text(
                text = issue.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = issue.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Location
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = "Location",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = issue.locationName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Verification Progress or resolved banner
            if (issue.status == IssueStatus.RESOLVED) {
                Surface(
                    color = Color(0xFFE8F5E9),
                    contentColor = Color(0xFF2E7D32),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Verified, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Resolved: ${issue.resolutionNotes}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                // Progress Bar towards verification
                val progress = issue.verificationsCount.toFloat() / issue.verificationThreshold.toFloat()
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Verification Progress",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${issue.verificationsCount}/${issue.verificationThreshold}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = progress.coerceIn(0f, 1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer actions
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reported ${formatTime(issue.timestamp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onUpvote,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Filled.ThumbUp, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${issue.upvotesCount}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. VERIFY SCREEN (VOTE FOR REAL HAZARDS)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyScreen(viewModel: CivicViewModel) {
    val issues by viewModel.issues.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    
    // Grab reported/unverified issues
    val verificationFeed = remember(issues) {
        issues.filter { it.status == IssueStatus.REPORTED || it.status == IssueStatus.VERIFYING }
    }

    var commentText by remember { mutableStateOf("") }
    var currentVerifyIndex by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Hyperlocal Verification Feed", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (currentVerifyIndex >= verificationFeed.size) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.SafetyCheck,
                            contentDescription = "Completed verification",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "You're All Caught Up!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Excellent civic responsibility! No pending verification issues nearby.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val issue = verificationFeed[currentVerifyIndex]

                Text(
                    text = "PENDING COMMUNITY AUDIT (${currentVerifyIndex + 1}/${verificationFeed.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .frostedGlass(RoundedCornerShape(28.dp))
                        .testTag("verify_card_${issue.id}"),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                StatusBadge(issue.status)
                                SeverityBadge(issue.severity)
                            }
                        }

                        item {
                            Text(
                                text = issue.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = issue.locationName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = issue.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        if (issue.aiSummary != null) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                ) {
                                    Row(modifier = Modifier.padding(12.dp)) {
                                        Icon(Icons.Filled.Psychology, "AI", tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Gemini AI Integrity Assessment", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Text(issue.aiSummary, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Cast Verification Vote",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Confirm if this hazard exists, or flag as spam to protect civic integrity.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = commentText,
                                onValueChange = { commentText = it },
                                label = { Text("Verification notes (e.g., 'Confirming pothole, lane is blocked')") },
                                modifier = Modifier.fillMaxWidth().testTag("verification_notes_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.verifyIssue(issue.id, false, commentText)
                                        commentText = ""
                                        currentVerifyIndex++
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("verify_flag_button"),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.Flag, null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Flag / Spam")
                                }

                                Button(
                                    onClick = {
                                        viewModel.verifyIssue(issue.id, true, commentText)
                                        commentText = ""
                                        currentVerifyIndex++
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("verify_confirm_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.Verified, null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Verify Legit")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. REPORT SCREEN (AI-POWERED)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(viewModel: CivicViewModel, onReportSuccess: () -> Unit) {
    val reportState by viewModel.reportState.collectAsState()
    val duplicateAlertState by viewModel.duplicateAlertState.collectAsState()
    
    // Captured and workflow states
    val cameraPhoto by viewModel.cameraPhotoBitmap.collectAsState()
    val gpsLoc by viewModel.gpsLocation.collectAsState()
    val locName by viewModel.locationName.collectAsState()
    val isAnalyzing by viewModel.isAnalyzingImage.collectAsState()
    val aiGeneratedData by viewModel.aiGeneratedData.collectAsState()

    var currentStep by remember { mutableStateOf(1) }

    // Reviewed / Editable fields in Step 4
    var reviewedTitle by remember { mutableStateOf("") }
    var reviewedDescription by remember { mutableStateOf("") }
    var reviewedCategory by remember { mutableStateOf(IssueCategory.POTHOLE) }
    var reviewedSeverity by remember { mutableStateOf(SeverityLevel.MEDIUM) }
    var reviewedAdvice by remember { mutableStateOf("") }
    var reviewedConfidence by remember { mutableStateOf("94%") }
    var reviewedPriority by remember { mutableStateOf("HIGH") }

    var selectedCategoryPreset by remember { mutableStateOf<IssueCategory?>(null) }
    var showSimulatorDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Launch camera preview activity
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            viewModel.setCameraPhoto(bitmap)
            selectedCategoryPreset = null // reset preset since they took a real photo
        }
    }

    // Auto-advance to Step 4 after Gemini completes analysis in Step 3
    LaunchedEffect(aiGeneratedData, isAnalyzing) {
        if (aiGeneratedData != null && !isAnalyzing && currentStep == 3) {
            currentStep = 4
        }
    }

    // Initialize editable review fields with AI outputs upon entering Step 4
    LaunchedEffect(currentStep, aiGeneratedData) {
        if (currentStep == 4 && aiGeneratedData != null) {
            val data = aiGeneratedData ?: return@LaunchedEffect
            reviewedTitle = data["title"] ?: ""
            reviewedDescription = data["description"] ?: ""
            reviewedCategory = try {
                IssueCategory.valueOf(data["category"] ?: "POTHOLE")
            } catch (e: Exception) {
                IssueCategory.POTHOLE
            }
            reviewedSeverity = try {
                SeverityLevel.valueOf(data["severity"] ?: "MEDIUM")
            } catch (e: Exception) {
                SeverityLevel.MEDIUM
            }
            reviewedAdvice = data["advice"] ?: "Observe precautions in this sector."
            reviewedConfidence = data["confidence"] ?: "94%"
            reviewedPriority = data["suggested_priority"] ?: "HIGH"
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Report Infrastructure Hazard", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (currentStep > 1 && reportState is ReportUiState.Idle) {
                        IconButton(onClick = { 
                            if (currentStep == 4) {
                                currentStep = 2 // go back to location
                            } else {
                                currentStep-- 
                            }
                        }) {
                            Icon(Icons.Filled.ArrowBack, "Back")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (showSimulatorDialog) {
                CameraSimulatorDialog(
                    onDismiss = { showSimulatorDialog = false },
                    onPhotoSelected = { bitmap, cat ->
                        viewModel.setCameraPhoto(bitmap)
                        selectedCategoryPreset = cat
                        showSimulatorDialog = false
                    }
                )
            }

            if (duplicateAlertState?.showDialog == true) {
                val alert = duplicateAlertState!!
                val duplicateIssue = alert.duplicateIssue
                if (duplicateIssue != null) {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissDuplicateAlert() },
                        modifier = Modifier.testTag("duplicate_alert_dialog"),
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.errorContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = "Duplicate warning",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        },
                        title = {
                            Text(
                                text = "Potential Duplicate Detected",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center
                            )
                        },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "An active issue with matching characteristics is reported nearby. To prevent spam and keep municipal dispatch organized, we suggest joining this report.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Similarity metrics card
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "MATCH CHARACTERISTICS",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                Text("Proximity:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            }
                                            Text(
                                                text = "${alert.proximityMeters.toInt()} meters away",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (alert.proximityMeters <= 50) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                Text("Description Match:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            }
                                            Text(
                                                text = "${(alert.textSimilarity * 100).toInt()}%",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(Icons.Filled.PhotoCamera, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                Text("Image Similarity:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            }
                                            Text(
                                                text = "${(alert.imageSimilarity * 100).toInt()}%",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                // Existing issue card preview
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "EXISTING REPORT PREVIEW",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = duplicateIssue.title,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = duplicateIssue.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = duplicateIssue.locationName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = duplicateIssue.status.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = "🎁 Joining this report immediately upvotes it, helps city crews prioritize it, and earns you a bonus +20 Civic XP!",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = { viewModel.joinExistingIssue(duplicateIssue.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("join_duplicate_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.ThumbUp, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Join & Upvote (+20 XP)", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.forceSubmitPendingIssue() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("submit_anyway_btn"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Create Separate Report Anyway")
                                }
                                
                                TextButton(
                                    onClick = { viewModel.dismissDuplicateAlert() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("cancel_duplicate_alert_btn")
                                ) {
                                    Text("Go Back and Edit", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    )
                }
            }

            AnimatedContent(
                targetState = reportState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "report_state_anim"
            ) { state ->
                when (state) {
                    is ReportUiState.Idle, is ReportUiState.Error -> {
                        when (currentStep) {
                            1 -> {
                                // Step 1: Camera
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    ReportWorkflowStepper(currentStep = 1)

                                    Text(
                                        text = "Step 1: Capture Hazard Proof",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.Start)
                                    )
                                    Text(
                                        text = "Submit a photo of the public hazard. Our smart camera detects the hazard, or you can use a preset for testing.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.Start)
                                    )

                                    // Viewfinder Box
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color.Black)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (cameraPhoto != null) {
                                            Image(
                                                bitmap = cameraPhoto!!.asImageBitmap(),
                                                contentDescription = "Captured photo",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            // Success Banner
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(12.dp)
                                                    .background(Color(0xFF2E7D32), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                    Text("Ready", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        } else {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                val pad = 40f
                                                val len = 50f
                                                val stroke = 4f
                                                val col = Color.White.copy(alpha = 0.4f)
                                                // Top Left
                                                drawLine(col, androidx.compose.ui.geometry.Offset(pad, pad), androidx.compose.ui.geometry.Offset(pad + len, pad), stroke)
                                                drawLine(col, androidx.compose.ui.geometry.Offset(pad, pad), androidx.compose.ui.geometry.Offset(pad, pad + len), stroke)
                                                // Top Right
                                                drawLine(col, androidx.compose.ui.geometry.Offset(size.width - pad, pad), androidx.compose.ui.geometry.Offset(size.width - pad - len, pad), stroke)
                                                drawLine(col, androidx.compose.ui.geometry.Offset(size.width - pad, pad), androidx.compose.ui.geometry.Offset(size.width - pad, pad + len), stroke)
                                                // Bottom Left
                                                drawLine(col, androidx.compose.ui.geometry.Offset(pad, size.height - pad), androidx.compose.ui.geometry.Offset(pad + len, size.height - pad), stroke)
                                                drawLine(col, androidx.compose.ui.geometry.Offset(pad, size.height - pad), androidx.compose.ui.geometry.Offset(pad, size.height - pad - len), stroke)
                                                // Bottom Right
                                                drawLine(col, androidx.compose.ui.geometry.Offset(size.width - pad, size.height - pad), androidx.compose.ui.geometry.Offset(size.width - pad - len, size.height - pad), stroke)
                                                drawLine(col, androidx.compose.ui.geometry.Offset(size.width - pad, size.height - pad), androidx.compose.ui.geometry.Offset(size.width - pad, size.height - pad - len), stroke)
                                            }

                                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Filled.Camera, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(56.dp))
                                                Text("Awaiting Image Capture", color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                                Text("Use your device camera or tap 'AI Photo Preset' to choose a hazard image.", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                                            }
                                        }
                                    }

                                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    try {
                                                        cameraLauncher.launch()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Camera fallback active", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.weight(1f).height(48.dp).testTag("device_camera_btn"),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Filled.PhotoCamera, null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Capture Photo")
                                            }

                                            Button(
                                                onClick = { showSimulatorDialog = true },
                                                modifier = Modifier.weight(1f).height(48.dp).testTag("camera_preset_btn"),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                            ) {
                                                Icon(Icons.Filled.AutoAwesome, null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("AI Photo Preset")
                                            }
                                        }

                                        if (cameraPhoto != null) {
                                            Button(
                                                onClick = {
                                                    currentStep = 2
                                                    simulateLocationFetch(context, viewModel)
                                                },
                                                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("proceed_to_gps_btn"),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Text("Proceed to Location Lock", fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(Icons.Filled.ArrowForward, null)
                                            }
                                        }
                                    }
                                }
                            }

                            2 -> {
                                // Step 2: Location
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    ReportWorkflowStepper(currentStep = 2)

                                    Text(
                                        text = "Step 2: Establish GPS Lock",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.Start)
                                    )

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(120.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Filled.MyLocation, "Locating", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                                            }

                                            Spacer(modifier = Modifier.height(20.dp))

                                            if (gpsLoc == null) {
                                                Text("Awaiting GPS Signal...", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                                Text("Securing coordinates from local networks & beacons.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            } else {
                                                Text("GPS Signal Locked!", fontWeight = FontWeight.Black, color = Color(0xFF2E7D32), style = MaterialTheme.typography.titleMedium)
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier
                                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                                        .padding(16.dp)
                                                        .fillMaxWidth(),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                        Text("LAT: ${String.format("%.5f", gpsLoc?.first)}, LNG: ${String.format("%.5f", gpsLoc?.second)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                    }
                                                    Text("Landmark: $locName", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                                    Text("Accuracy: ±4 meters", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }

                                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { simulateLocationFetch(context, viewModel) },
                                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("acquire_gps_btn"),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                        ) {
                                            Icon(Icons.Filled.MyLocation, null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (gpsLoc == null) "Acquire GPS Lock" else "Re-verify Coordinates")
                                        }

                                        if (gpsLoc != null) {
                                            Button(
                                                onClick = {
                                                    currentStep = 3
                                                    cameraPhoto?.let { bitmap ->
                                                        viewModel.runGeminiOnPhoto(bitmap, selectedCategoryPreset?.name)
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("run_ai_analysis_btn"),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Text("Analyze with Gemini AI", fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(Icons.Filled.Psychology, null)
                                            }
                                        }
                                    }
                                }
                            }

                            3 -> {
                                // Step 3: Running Gemini AI
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    ReportWorkflowStepper(currentStep = 3)

                                    Spacer(modifier = Modifier.height(32.dp))

                                    Box(
                                        modifier = Modifier
                                            .size(180.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color.Black),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (cameraPhoto != null) {
                                            Image(
                                                bitmap = cameraPhoto!!.asImageBitmap(),
                                                contentDescription = "Scanning",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                                alpha = 0.5f
                                            )
                                        }
                                        // Futuristic blue glowing scanner line
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text("Gemini AI Analyzing...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                    Text("Extracting hazard details and drafting safety bulletins.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            4 -> {
                                // Step 4: User Reviews and Edits Data
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    item {
                                        ReportWorkflowStepper(currentStep = 4)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Step 4: Review AI Assessment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("Review and edit the Gemini-generated details before final dispatch.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    item {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f)),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Filled.AutoAwesome, "Gemini AI", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Gemini Vision Diagnostics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.tertiary)
                                                }
                                                
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text("CONFIDENCE SCORE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text(reviewedConfidence, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                                    }
                                                    
                                                    Column(horizontalAlignment = Alignment.End) {
                                                        Text("SUGGESTED DISPATCH PRIORITY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        ) {
                                                            Text(reviewedPriority, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.tertiary)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    item {
                                        OutlinedTextField(
                                            value = reviewedTitle,
                                            onValueChange = { reviewedTitle = it },
                                            label = { Text("Issue Title") },
                                            modifier = Modifier.fillMaxWidth().testTag("review_title_input"),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }

                                    item {
                                        OutlinedTextField(
                                            value = reviewedDescription,
                                            onValueChange = { reviewedDescription = it },
                                            label = { Text("Detailed Description") },
                                            modifier = Modifier.fillMaxWidth().height(120.dp).testTag("review_desc_input"),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }

                                    item {
                                        Text("Issue Category", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val filterCats = listOf(
                                            IssueCategory.POTHOLE,
                                            IssueCategory.GARBAGE,
                                            IssueCategory.WATER_LEAKAGE,
                                            IssueCategory.BROKEN_STREETLIGHT,
                                            IssueCategory.ROAD_DAMAGE
                                        )
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(filterCats) { cat ->
                                                val isSel = reviewedCategory == cat
                                                FilterChip(
                                                    selected = isSel,
                                                    onClick = { reviewedCategory = cat },
                                                    label = { 
                                                        Text(
                                                            when (cat) {
                                                                IssueCategory.POTHOLE -> "Pothole"
                                                                IssueCategory.GARBAGE -> "Garbage"
                                                                IssueCategory.WATER_LEAKAGE -> "Water Leakage"
                                                                IssueCategory.BROKEN_STREETLIGHT -> "Broken Streetlight"
                                                                IssueCategory.ROAD_DAMAGE -> "Road Damage"
                                                                else -> cat.name
                                                            }
                                                        ) 
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    item {
                                        Text("Reported Severity", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            SeverityLevel.values().forEach { level ->
                                                val isSel = reviewedSeverity == level
                                                val col = when (level) {
                                                    SeverityLevel.LOW -> Color(0xFF2E7D32)
                                                    SeverityLevel.MEDIUM -> Color(0xFFFBC02D)
                                                    SeverityLevel.HIGH -> Color(0xFFE64A19)
                                                    SeverityLevel.CRITICAL -> Color(0xFFC62828)
                                                }
                                                FilterChip(
                                                    selected = isSel,
                                                    onClick = { reviewedSeverity = level },
                                                    label = { Text(level.name) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = col.copy(alpha = 0.2f),
                                                        selectedLabelColor = col
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    item {
                                        // Display Advisor Note Card
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
                                                Column {
                                                    Text("AI Safety Advice Preview", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                    Text(reviewedAdvice, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }

                                    if (state is ReportUiState.Error) {
                                        item {
                                            Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }

                                    item {
                                        Button(
                                            onClick = {
                                                if (reviewedTitle.isNotEmpty() && reviewedDescription.isNotEmpty() && gpsLoc != null) {
                                                    viewModel.submitReviewedIssue(
                                                        title = reviewedTitle,
                                                        description = reviewedDescription,
                                                        category = reviewedCategory,
                                                        severity = reviewedSeverity,
                                                        latitude = gpsLoc!!.first,
                                                        longitude = gpsLoc!!.second,
                                                        locationName = locName,
                                                        aiSummary = reviewedAdvice,
                                                        confidenceScore = reviewedConfidence,
                                                        suggestedPriority = reviewedPriority
                                                    )
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("report_submit_button"),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Icon(Icons.Filled.Publish, null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Submit Report", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is ReportUiState.Submitting -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(56.dp))
                                Text("Publishing Report to Council Board...", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Text("Syncing local cache and Firestore cloud storage.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    is ReportUiState.Success -> {
                        val submittedIssue = state.issue
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Success",
                                tint = Color(0xFF388E3C),
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Civic Alert Dispatched!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                            Text("Reputation Points Awarded: +15 Civic XP", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)

                            Spacer(modifier = Modifier.height(24.dp))

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().testTag("ai_result_box")
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Psychology, "Gemini", tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Gemini AI Assessment Approved", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("CATEGORY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                when (submittedIssue.category) {
                                                    IssueCategory.POTHOLE -> "Pothole"
                                                    IssueCategory.GARBAGE -> "Garbage"
                                                    IssueCategory.WATER_LEAKAGE -> "Water Leakage"
                                                    IssueCategory.BROKEN_STREETLIGHT -> "Broken Streetlight"
                                                    IssueCategory.ROAD_DAMAGE -> "Road Damage"
                                                    else -> submittedIssue.category.name
                                                }, 
                                                fontWeight = FontWeight.Bold, 
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("SEVERITY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            SeverityBadge(submittedIssue.severity)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("CONFIDENCE SCORE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(submittedIssue.confidenceScore ?: "94%", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("SUGGESTED PRIORITY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(submittedIssue.suggestedPriority ?: "HIGH", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("NEIGHBORHOOD ADVISORY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(submittedIssue.aiSummary ?: "No advisory generated.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            Button(
                                onClick = {
                                    viewModel.resetReportState()
                                    viewModel.clearWorkflowState()
                                    onReportSuccess()
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("ai_ok_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Back to Dashboard")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun simulateLocationFetch(context: android.content.Context, viewModel: CivicViewModel) {
    val neighborhoods = listOf(
        "Mission District (Sector 4)",
        "SoMa Tech Corridor (Sector 7)",
        "Castro St / Market Intersection",
        "Pacific Heights Outer Area",
        "Civic Center Square Plaza",
        "Financial District Waterfront"
    )
    val randomLat = 37.7749 + (Math.random() - 0.5) * 0.02
    val randomLng = -122.4194 + (Math.random() - 0.5) * 0.02
    val randomSector = neighborhoods.random()
    viewModel.setGpsLocation(randomLat, randomLng, randomSector)
}

@Composable
fun ReportWorkflowStepper(currentStep: Int) {
    val steps = listOf(
        Triple(1, Icons.Filled.CameraAlt, "Capture"),
        Triple(2, Icons.Filled.MyLocation, "GPS Lock"),
        Triple(3, Icons.Filled.Psychology, "AI Scan"),
        Triple(4, Icons.Filled.Checklist, "Approve")
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, (stepNum, icon, label) ->
            val isActive = currentStep >= stepNum
            val isCurrent = currentStep == stepNum

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCurrent) MaterialTheme.colorScheme.primary
                            else if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            width = if (isCurrent) 2.dp else 0.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                        else if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            if (index < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .weight(0.5f)
                        .height(2.dp)
                        .background(
                            if (currentStep > stepNum) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
    }
}

@Composable
fun CameraSimulatorDialog(
    onDismiss: () -> Unit,
    onPhotoSelected: (android.graphics.Bitmap, IssueCategory) -> Unit
) {
    val presets = listOf(
        Triple(IssueCategory.POTHOLE, "Pothole", "Deep asphalt pothole in travel lane"),
        Triple(IssueCategory.GARBAGE, "Garbage Pile", "Stray commercial trash accumulation"),
        Triple(IssueCategory.WATER_LEAKAGE, "Water Leakage", "Water main pipe leak at curbside"),
        Triple(IssueCategory.BROKEN_STREETLIGHT, "Broken Streetlight", "Dark unlit street lamp"),
        Triple(IssueCategory.ROAD_DAMAGE, "Road Damage", "Severe cracked buckled concrete pavement")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Text("AI Camera Simulator", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Simulate capturing public hazards with realistic images to test Gemini AI auto-generation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                presets.forEach { (cat, name, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable {
                                val width = 300
                                val height = 300
                                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(bitmap)
                                val paint = android.graphics.Paint()
                                paint.color = when (cat) {
                                    IssueCategory.POTHOLE -> android.graphics.Color.DKGRAY
                                    IssueCategory.GARBAGE -> android.graphics.Color.GREEN
                                    IssueCategory.WATER_LEAKAGE -> android.graphics.Color.BLUE
                                    IssueCategory.BROKEN_STREETLIGHT -> android.graphics.Color.YELLOW
                                    IssueCategory.ROAD_DAMAGE -> android.graphics.Color.RED
                                    else -> android.graphics.Color.GRAY
                                }
                                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                                onPhotoSelected(bitmap, cat)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when (cat) {
                                        IssueCategory.POTHOLE -> Color.DarkGray
                                        IssueCategory.GARBAGE -> Color(0xFF2E7D32)
                                        IssueCategory.WATER_LEAKAGE -> Color(0xFF1976D2)
                                        IssueCategory.BROKEN_STREETLIGHT -> Color(0xFFFBC02D)
                                        IssueCategory.ROAD_DAMAGE -> Color(0xFFE64A19)
                                        else -> Color.Gray
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (cat) {
                                    IssueCategory.POTHOLE -> Icons.Filled.Warning
                                    IssueCategory.GARBAGE -> Icons.Filled.DeleteOutline
                                    IssueCategory.WATER_LEAKAGE -> Icons.Filled.Water
                                    IssueCategory.BROKEN_STREETLIGHT -> Icons.Filled.LightbulbCircle
                                    IssueCategory.ROAD_DAMAGE -> Icons.Filled.Construction
                                    else -> Icons.Filled.Warning
                                },
                                contentDescription = null,
                                tint = Color.White
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ==========================================
// 5. COMMUNITY FORUM SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(viewModel: CivicViewModel) {
    val posts by viewModel.communityPosts.collectAsState()
    var postText by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("All") }

    val filteredPosts = remember(posts, selectedTab) {
        if (selectedTab == "All") posts else posts.filter { it.category == selectedTab }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Neighborhood Council Board", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Composer Box
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .frostedGlass(RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = postText,
                        onValueChange = { postText = it },
                        placeholder = { Text("Share an update, schedule a neighborhood fix, or post a civic victory...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .testTag("post_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                if (postText.isNotEmpty()) {
                                    viewModel.addPost(postText, "General")
                                    postText = ""
                                }
                            },
                            enabled = postText.isNotEmpty(),
                            modifier = Modifier.testTag("post_submit_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Send, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                }
            }

            // Categories horizontal filter
            val categories = listOf("All", "General", "Alerts", "Milestones")
            TabRow(
                selectedTabIndex = categories.indexOf(selectedTab),
                containerColor = Color.Transparent
            ) {
                categories.forEach { cat ->
                    Tab(
                        selected = selectedTab == cat,
                        onClick = { selectedTab = cat },
                        text = { Text(cat) }
                    )
                }
            }

            // Posts lazy feed
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredPosts, key = { it.id }) { post ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .frostedGlass(RoundedCornerShape(24.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Author avatar placeholder / standard async load
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(post.authorAvatar)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Avatar",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = post.authorName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${post.category} • ${formatTime(post.timestamp)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = post.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { viewModel.likePost(post.id) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Favorite,
                                        contentDescription = "Like",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${post.likesCount}",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 6. PROFILE & CREDENTIALS SCREEN
// ==========================================
// ==========================================
// 6. PROFILE & CREDENTIALS SCREEN
// ==========================================
data class GamifiedBadge(
    val id: String,
    val title: String,
    val description: String,
    val target: String,
    val progress: Float,
    val progressLabel: String,
    val isUnlocked: Boolean,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val activeColor: Color,
    val xpReward: Int,
    val playfulTip: String
)

data class TimelineItem(
    val id: String,
    val title: String,
    val description: String,
    val timestamp: Long,
    val type: String,
    val status: String,
    val pointsLabel: String
)

@Composable
fun FitnessRings(
    trustProgress: Float,
    communityProgress: Float,
    levelProgress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(100.dp)) {
        val width = size.width
        val height = size.height
        val center = androidx.compose.ui.geometry.Offset(width / 2, height / 2)
        
        // Ring 1 (Trust Progress) - Green/Teal
        val ring1Radius = width / 2 - 8.dp.toPx()
        drawCircle(
            color = Color(0xFF4CAF50).copy(alpha = 0.15f), // background shadow base
            radius = ring1Radius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8.dp.toPx())
        )
        drawArc(
            color = Color(0xFF4CAF50),
            startAngle = -90f,
            sweepAngle = trustProgress * 360f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(center.x - ring1Radius, center.y - ring1Radius),
            size = androidx.compose.ui.geometry.Size(ring1Radius * 2, ring1Radius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 8.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )

        // Ring 2 (Community Progress) - Coral/Salmon
        val ring2Radius = ring1Radius - 12.dp.toPx()
        drawCircle(
            color = Color(0xFFFF5722).copy(alpha = 0.15f),
            radius = ring2Radius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8.dp.toPx())
        )
        drawArc(
            color = Color(0xFFFF5722),
            startAngle = -90f,
            sweepAngle = communityProgress * 360f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(center.x - ring2Radius, center.y - ring2Radius),
            size = androidx.compose.ui.geometry.Size(ring2Radius * 2, ring2Radius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 8.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )

        // Ring 3 (Level Progress) - Gold/Yellow
        val ring3Radius = ring2Radius - 12.dp.toPx()
        drawCircle(
            color = Color(0xFFFFC107).copy(alpha = 0.15f),
            radius = ring3Radius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8.dp.toPx())
        )
        drawArc(
            color = Color(0xFFFFC107),
            startAngle = -90f,
            sweepAngle = levelProgress * 360f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(center.x - ring3Radius, center.y - ring3Radius),
            size = androidx.compose.ui.geometry.Size(ring3Radius * 2, ring3Radius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 8.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: CivicViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val communityPosts by viewModel.communityPosts.collectAsState()
    val issues by viewModel.issues.collectAsState()

    var selectedBadge by remember { mutableStateOf<GamifiedBadge?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Citizen Hero Profile", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.testTag("logout_button")
                    ) {
                        Icon(Icons.Filled.ExitToApp, "Log Out", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { innerPadding ->
        currentUser?.let { user ->
            val userPostsCount = communityPosts.count { it.authorId == user.id }

            val badges = remember(user, userPostsCount) {
                listOf(
                    GamifiedBadge(
                        id = "first_report",
                        title = "Pothole Pioneer",
                        description = "Reported at least 1 infrastructure issue in your neighborhood.",
                        target = "1 Report",
                        progress = if (user.reportedCount >= 1) 1f else 0f,
                        progressLabel = "${user.reportedCount}/1",
                        isUnlocked = user.reportedCount >= 1,
                        icon = Icons.Filled.AddLocation,
                        activeColor = Color(0xFF4CAF50),
                        xpReward = 15,
                        playfulTip = "Amazing! You took the first step to claim your neighborhood back!"
                    ),
                    GamifiedBadge(
                        id = "verifications",
                        title = "Integrity Auditor",
                        description = "Verified 3 reports near you with precision.",
                        target = "3 Verifications",
                        progress = (user.verifiedCount / 3f).coerceAtMost(1f),
                        progressLabel = "${user.verifiedCount}/3",
                        isUnlocked = user.verifiedCount >= 3,
                        icon = Icons.Filled.SafetyCheck,
                        activeColor = Color(0xFF1E88E5),
                        xpReward = 25,
                        playfulTip = "Accurate verifications help city workers prioritize fast fixes!"
                    ),
                    GamifiedBadge(
                        id = "trust_master",
                        title = "Elite Watchdog",
                        description = "Achieve an exceptional trust rating of 85% or higher.",
                        target = "85% Trust Score",
                        progress = (user.trustScore / 85f).coerceAtMost(1f),
                        progressLabel = "${user.trustScore}% / 85%",
                        isUnlocked = user.trustScore >= 85,
                        icon = Icons.Filled.Shield,
                        activeColor = Color(0xFF9C27B0),
                        xpReward = 50,
                        playfulTip = "High trust score makes your new reports instantly visible to city agencies!"
                    ),
                    GamifiedBadge(
                        id = "community_posts",
                        title = "Local Anchor",
                        description = "Post at least 2 community updates or safety warnings.",
                        target = "2 Posts",
                        progress = (userPostsCount / 2f).coerceAtMost(1f),
                        progressLabel = "$userPostsCount/2",
                        isUnlocked = userPostsCount >= 2,
                        icon = Icons.Filled.Forum,
                        activeColor = Color(0xFFFF5722),
                        xpReward = 10,
                        playfulTip = "Keeping your neighbors informed is the ultimate civic superpower."
                    ),
                    GamifiedBadge(
                        id = "reputation_legend",
                        title = "Golden Champion",
                        description = "Reach 200 civic reputation points.",
                        target = "200 XP",
                        progress = (user.reputationPoints / 200f).coerceAtMost(1f),
                        progressLabel = "${user.reputationPoints}/200",
                        isUnlocked = user.reputationPoints >= 200,
                        icon = Icons.Filled.EmojiEvents,
                        activeColor = Color(0xFFFFD700),
                        xpReward = 100,
                        playfulTip = "Your legendary standing inspires hundreds of citizens every day!"
                    ),
                    GamifiedBadge(
                        id = "streak_fire",
                        title = "Action Streaker",
                        description = "Maintain a daily contribution streak.",
                        target = "7 Days",
                        progress = 1.0f,
                        progressLabel = "7/7 Days",
                        isUnlocked = true,
                        icon = Icons.Filled.LocalFireDepartment,
                        activeColor = Color(0xFFFF9800),
                        xpReward = 30,
                        playfulTip = "Look at you burn! That Duolingo fire is shining bright."
                    ),
                    GamifiedBadge(
                        id = "community_assisted",
                        title = "Community Assisted",
                        description = "Successfully perform neighborhood repairs validated by Gemini AI.",
                        target = "1 Handyman Repair",
                        progress = if (issues.any { it.communityAssistedBadgeAwarded }) 1.0f else 0.0f,
                        progressLabel = if (issues.any { it.communityAssistedBadgeAwarded }) "1/1 Repair" else "0/1 Repair",
                        isUnlocked = issues.any { it.communityAssistedBadgeAwarded },
                        icon = Icons.Filled.BuildCircle,
                        activeColor = Color(0xFFE91E63),
                        xpReward = 80,
                        playfulTip = "Your hands-on action keeps the community pristine and safe!"
                    )
                )
            }

            val userIssues = issues.filter { it.reporterId == user.id }
            val userPosts = communityPosts.filter { it.authorId == user.id }
            val timelineItems = remember(userIssues, userPosts) {
                val items = mutableListOf<TimelineItem>()
                userIssues.forEach { issue ->
                    items.add(
                        TimelineItem(
                            id = issue.id,
                            title = "Reported: ${issue.title}",
                            description = "Filed a '${issue.category.name.lowercase().capitalize()}' issue at ${issue.locationName}.",
                            timestamp = issue.timestamp,
                            type = "report",
                            status = issue.status.name,
                            pointsLabel = "+15 XP"
                        )
                    )
                }
                userPosts.forEach { post ->
                    items.add(
                        TimelineItem(
                            id = post.id,
                            title = "Posted: Update in #${post.category}",
                            description = "\"${post.content}\"",
                            timestamp = post.timestamp,
                            type = "post",
                            status = "PUBLISHED",
                            pointsLabel = "+5 XP"
                        )
                    )
                }
                items.sortedByDescending { it.timestamp }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("profile_scroll_view"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Header Profile Box
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(user.avatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Profile Avatar",
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(CircleShape)
                                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    .testTag("profile_avatar"),
                                contentScale = ContentScale.Crop
                            )
                            // Playful crown icon for top users
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                                modifier = Modifier.size(32.dp),
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.MilitaryTech,
                                        contentDescription = "Reputation Tier",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = user.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.testTag("profile_name")
                        )
                        Text(
                            text = user.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("profile_email")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Hero Level badge (converts levels to colors)
                        val levelColor = when (user.heroLevel) {
                            "Legend" -> Color(0xFFE5C158) // Gold
                            "Guardian" -> Color(0xFF6200EE) // Royal purple
                            "Hero" -> Color(0xFF03DAC5) // Teal
                            "Contributor" -> Color(0xFF2196F3) // Blue
                            else -> Color(0xFF9E9E9E) // Slate
                        }
                        
                        Surface(
                            color = levelColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, levelColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = levelColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${user.heroLevel.uppercase()} LEVEL",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = levelColor
                                )
                            }
                        }
                    }
                }

                // 2. Apple Fitness style Concentric Progress Rings Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fitness_rings_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                FitnessRings(
                                    trustProgress = (user.trustScore / 100f).coerceIn(0f, 1f),
                                    communityProgress = ((user.communityScore % 250) / 250f).coerceIn(0f, 1f),
                                    levelProgress = ((user.reputationPoints % 100) / 100f).coerceIn(0f, 1f),
                                    modifier = Modifier.testTag("profile_fitness_rings")
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Activity Rings",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    // Ring legends
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                                        Text(
                                            text = "Trust: ${user.trustScore}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF5722)))
                                        Text(
                                            text = "Community Score: ${user.communityScore}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFFC107)))
                                        Text(
                                            text = "Level Progress: ${user.reputationPoints % 100}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            Spacer(modifier = Modifier.height(10.dp))

                            // Duolingo fire streak
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocalFireDepartment,
                                    contentDescription = "Streak",
                                    tint = Color(0xFFFF9800),
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "7-Day Contribution Streak! Keep it burning by checking alerts today.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 3. Google Play Games style XP Level & Core Metrics Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("xp_metrics_card"),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            val currentLevel = (user.reputationPoints / 100) + 1
                            val currentLevelProgress = user.reputationPoints % 100
                            val pointsRemaining = 100 - currentLevelProgress

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Level $currentLevel",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "${user.reputationPoints} Total XP",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            LinearProgressIndicator(
                                progress = currentLevelProgress.toFloat() / 100f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .testTag("xp_progress_bar"),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "$pointsRemaining XP to Level ${currentLevel + 1}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // Three columns for primary stats
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val stats = listOf(
                                    Triple("Reports", "${user.reportedCount}", Icons.Filled.AddLocation),
                                    Triple("Verifications", "${user.verifiedCount}", Icons.Filled.CheckCircle),
                                    Triple("Impact Rating", "${user.trustScore}%", Icons.Filled.Star)
                                )

                                stats.forEach { (label, value, icon) ->
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = label,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = value,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Black
                                            )
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Badges & Achievements (Horizontal Scroll)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "My Achievements & Badges",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            items(badges) { badge ->
                                Card(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .clickable { selectedBadge = badge }
                                        .testTag("badge_card_${badge.id}"),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (badge.isUnlocked) badge.activeColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(
                                        width = 1.5.dp,
                                        color = if (badge.isUnlocked) badge.activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Surface(
                                                color = if (badge.isUnlocked) badge.activeColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                                shape = CircleShape,
                                                modifier = Modifier.size(54.dp)
                                            ) {}
                                            
                                            Icon(
                                                imageVector = if (badge.isUnlocked) badge.icon else Icons.Filled.Lock,
                                                contentDescription = badge.title,
                                                tint = if (badge.isUnlocked) badge.activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = badge.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Text(
                                            text = badge.progressLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (badge.isUnlocked) badge.activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Hyperlocal Ward Support details
                item {
                    val supportInfo = remember(user) {
                        getHyperlocalSupport(41.3082, -72.9279) // Default New Haven Central
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hyperlocal_support_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocalPolice,
                                    contentDescription = "Hyperlocal Support",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "YOUR HYPERLOCAL SUPPORT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Text(
                                text = "Division Support Focus: ${supportInfo.division}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Text(
                                text = "Assigned Sector: ${supportInfo.ward}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text(
                                        text = supportInfo.officerName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Responsible Officer & Ward Lead",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Filled.Phone, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                Text(text = supportInfo.officerPhone, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                                Icon(Icons.Filled.Email, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                Text(text = supportInfo.officerEmail, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                                Icon(Icons.Filled.Home, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                Text(text = supportInfo.officeAddress, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                // 5. Contribution Timeline
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Contribution Timeline",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        if (timelineItems.isEmpty()) {
                            // Beautiful Encouraging Empty State
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.HistoryToggleOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Your timeline is empty",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Report issues or verify alerts nearby to build your public record of community action!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            // Render Timeline
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                            ) {
                                timelineItems.forEachIndexed { index, item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        // Left timeline indicator (bullet & line)
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.width(20.dp)
                                        ) {
                                            val bulletColor = when (item.type) {
                                                "report" -> Color(0xFF4CAF50) // green
                                                else -> Color(0xFFFF5722) // orange
                                            }

                                            // Draw Bullet
                                            Surface(
                                                color = bulletColor,
                                                shape = CircleShape,
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .padding(1.dp),
                                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)
                                            ) {}

                                            // Draw Line to next item
                                            if (index < timelineItems.lastIndex) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(2.dp)
                                                        .height(60.dp)
                                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                                )
                                            }
                                        }

                                        // Timeline Details Card
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(bottom = 12.dp)
                                                .testTag("timeline_item_${item.id}"),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                            ),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = item.title,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = item.pointsLabel,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Black,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Text(
                                                    text = item.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                                Spacer(modifier = Modifier.height(6.dp))

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = formatTime(item.timestamp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                    )
                                                    
                                                    val statusColor = when (item.status) {
                                                        "VERIFIED", "PUBLISHED" -> Color(0xFF4CAF50)
                                                        "RESOLVED" -> Color(0xFF1E88E5)
                                                        "REJECTED" -> Color(0xFFF44336)
                                                        else -> Color(0xFFFF9800)
                                                    }
                                                    Text(
                                                        text = item.status,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Black,
                                                        color = statusColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Interactive Badge Details Dialog (inspired by Google Play Games & Duolingo)
    selectedBadge?.let { badge ->
        AlertDialog(
            onDismissRequest = { selectedBadge = null },
            confirmButton = {
                TextButton(
                    onClick = { selectedBadge = null },
                    modifier = Modifier.testTag("dismiss_badge_dialog")
                ) {
                    Text("Awesome!", fontWeight = FontWeight.Bold)
                }
            },
            icon = {
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = if (badge.isUnlocked) badge.activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        shape = CircleShape,
                        modifier = Modifier.fillMaxSize()
                    ) {}
                    Icon(
                        imageVector = if (badge.isUnlocked) badge.icon else Icons.Filled.Lock,
                        contentDescription = badge.title,
                        tint = if (badge.isUnlocked) badge.activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(44.dp)
                    )
                }
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = badge.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        color = if (badge.isUnlocked) badge.activeColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (badge.isUnlocked) "UNLOCKED" else "LOCKED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = if (badge.isUnlocked) badge.activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = badge.description,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = badge.progress,
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (badge.isUnlocked) badge.activeColor else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${badge.progressLabel} toward ${badge.target}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "💡 Tip",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = badge.playfulTip,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            },
            modifier = Modifier.testTag("badge_detail_dialog")
        )
    }
}

// ==========================================
// DETAIL SHEET DIALOG OVERLAY
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueDetailDialog(
    issue: InfrastructureIssue,
    currentUser: User,
    onDismiss: () -> Unit,
    onUpvote: () -> Unit,
    onResolve: (String) -> Unit
) {
    var showResolutionInput by remember { mutableStateOf(false) }
    var resolutionNotes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("issue_detail_dialog"),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp) // allow visual backdrop depth
                .frostedGlass(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header with cross dismiss
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("dialog_close_button")) {
                        Icon(Icons.Filled.Close, "Dismiss")
                    }
                    StatusBadge(issue.status)
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = issue.category.name + " ISSUE",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = issue.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black
                        )
                    }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = issue.locationName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SeverityBadge(issue.severity)
                            Text(
                                text = "Reported by ${issue.reporterName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Large description
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .frostedGlass(RoundedCornerShape(20.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("CITIZEN DESCRIPTION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = issue.description, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }

                    // AI Advice Card
                    if (issue.aiSummary != null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(modifier = Modifier.padding(16.dp)) {
                                    Icon(
                                        imageVector = Icons.Filled.Psychology,
                                        contentDescription = "Gemini",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Gemini AI Safety Action Recommendation",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = issue.aiSummary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )

                                        if (issue.confidenceScore != null || issue.suggestedPriority != null) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                if (issue.confidenceScore != null) {
                                                    Column {
                                                        Text("CONFIDENCE SCORE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text(issue.confidenceScore, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                                    }
                                                }
                                                if (issue.suggestedPriority != null) {
                                                    Column {
                                                        Text("SUGGESTED PRIORITY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text(issue.suggestedPriority, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Resolve panel
                    if (issue.status == IssueStatus.RESOLVED) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(modifier = Modifier.padding(16.dp)) {
                                    Icon(Icons.Filled.Verified, null, tint = Color(0xFF2E7D32))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("RESOLUTION CONFIRMED", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(issue.resolutionNotes ?: "Completed successfully.", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }

                    // Admin/Officer/Worker Resolve actions
                    if ((currentUser.role == UserRole.OFFICER || currentUser.role == UserRole.WORKER) && issue.status != IssueStatus.RESOLVED) {
                        item {
                            if (showResolutionInput) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = resolutionNotes,
                                        onValueChange = { resolutionNotes = it },
                                        label = { Text("Resolution Dispatch Notes") },
                                        modifier = Modifier.fillMaxWidth().testTag("resolution_notes_input")
                                    )
                                    Button(
                                        onClick = {
                                            if (resolutionNotes.isNotEmpty()) {
                                                onResolve(resolutionNotes)
                                                showResolutionInput = false
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("resolve_submit_button")
                                    ) {
                                        Text("Confirm Resolution")
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { showResolutionInput = true },
                                    modifier = Modifier.fillMaxWidth().testTag("resolve_action_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
                                ) {
                                    Icon(Icons.Filled.DoneAll, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Dispatch Repair Resolution")
                                }
                            }
                        }
                    }
                }

                // Static bottom detail footer upvote
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onUpvote,
                        modifier = Modifier.weight(1f).height(50.dp).testTag("dialog_upvote_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.ThumbUp, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upvote (${issue.upvotesCount})")
                    }
                }
            }
        }
    }
}
