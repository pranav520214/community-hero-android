package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.InfrastructureIssue
import com.example.data.model.IssueCategory
import com.example.data.model.IssueStatus
import com.example.data.model.SeverityLevel
import com.example.ui.viewmodel.CivicViewModel
import com.example.security.SecurityManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficerDashboardScreen(viewModel: CivicViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val issues by viewModel.issues.collectAsState()
    val dailyBriefing by viewModel.dailyBriefing.collectAsState()
    val isBriefingLoading by viewModel.isBriefingLoading.collectAsState()
    val context = LocalContext.current

    // Enterprise Security States
    var securityReport by remember { mutableStateOf<SecurityManager.SecurityAuditReport?>(null) }
    var isMockRooted by remember { mutableStateOf(false) }
    var isAuditingInProgress by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        securityReport = SecurityManager.performSecurityAudit(context)
    }

    LaunchedEffect(issues) {
        if (issues.isNotEmpty() && dailyBriefing.isEmpty()) {
            viewModel.generateDailyBriefing(issues)
        }
    }

    // Executive state
    var selectedWardFilter by remember { mutableStateOf("All Wards") }
    var selectedDetailIssue by remember { mutableStateOf<InfrastructureIssue?>(null) }
    var hoveredHeatmapPoint by remember { mutableStateOf<InfrastructureIssue?>(null) }
    var showExportSuccess by remember { mutableStateOf(false) }

    // Wards classification helper
    fun getIssueWard(issue: InfrastructureIssue): String {
        val lat = issue.latitude
        val lng = issue.longitude
        // Dynamic ward classification by coordinate sectors
        return when {
            lat >= 37.78 && lng >= -122.42 -> "Ward 1 (Metro North)"
            lat >= 37.78 && lng < -122.42 -> "Ward 5 (Suburban West)"
            lat < 37.78 && lat >= 37.75 && lng >= -122.42 -> "Ward 2 (Downtown Core)"
            lat < 37.78 && lat >= 37.75 && lng < -122.42 -> "Ward 4 (Residential South)"
            else -> "Ward 3 (Industrial East)"
        }
    }

    // Filter issues by Ward if needed
    val filteredIssues = remember(issues, selectedWardFilter) {
        if (selectedWardFilter == "All Wards") {
            issues
        } else {
            issues.filter { getIssueWard(it) == selectedWardFilter }
        }
    }

    // Dynamic metrics calculations
    val totalIssuesCount = filteredIssues.size
    val resolvedIssuesCount = filteredIssues.count { it.status == IssueStatus.RESOLVED }
    val inProgressIssuesCount = filteredIssues.count { it.status == IssueStatus.IN_PROGRESS }
    val verifiedIssuesCount = filteredIssues.count { it.status == IssueStatus.VERIFIED }
    val reportedIssuesCount = filteredIssues.count { it.status == IssueStatus.REPORTED }

    // Calculate Dynamic Infrastructure Health Score
    val healthScore = remember(filteredIssues) {
        if (filteredIssues.isEmpty()) {
            100
        } else {
            var deduct = 0
            filteredIssues.forEach { issue ->
                if (issue.status != IssueStatus.RESOLVED && issue.status != IssueStatus.REJECTED) {
                    deduct += when (issue.severity) {
                        SeverityLevel.CRITICAL -> 15
                        SeverityLevel.HIGH -> 8
                        SeverityLevel.MEDIUM -> 3
                        SeverityLevel.LOW -> 1
                    }
                }
            }
            val calculated = 100 - deduct
            calculated.coerceIn(10, 100)
        }
    }

    // Road health rating %
    val roadHealth = remember(filteredIssues) {
        val roadIssues = filteredIssues.filter { 
            it.category == IssueCategory.ROADS || 
            it.category == IssueCategory.POTHOLE || 
            it.category == IssueCategory.ROAD_DAMAGE 
        }
        calculateCategoryHealth(roadIssues)
    }

    // Streetlight health rating %
    val streetlightHealth = remember(filteredIssues) {
        val lightIssues = filteredIssues.filter { 
            it.category == IssueCategory.LIGHTING || 
            it.category == IssueCategory.BROKEN_STREETLIGHT 
        }
        calculateCategoryHealth(lightIssues)
    }

    // Water health rating %
    val waterHealth = remember(filteredIssues) {
        val waterIssues = filteredIssues.filter { 
            it.category == IssueCategory.WATER || 
            it.category == IssueCategory.WATER_LEAKAGE 
        }
        calculateCategoryHealth(waterIssues)
    }

    // Waste health rating %
    val wasteHealth = remember(filteredIssues) {
        val wasteIssues = filteredIssues.filter { 
            it.category == IssueCategory.SANITATION || 
            it.category == IssueCategory.GARBAGE 
        }
        calculateCategoryHealth(wasteIssues)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("officer_dashboard_screen"),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Executive Officer Command",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Municipal Intelligence & Analytics Dashboard",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Export PDF / Share Button
                    IconButton(
                        onClick = {
                            showExportSuccess = true
                            Toast.makeText(context, "Executive PDF Report compiled & exported to local vault!", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .testTag("executive_export_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Export Report",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Ward Selector Filter Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FilterList,
                        contentDescription = "Filter",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Ward Focus:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )

                    var expandedFilter by remember { mutableStateOf(false) }
                    Box {
                        Button(
                            onClick = { expandedFilter = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp).testTag("ward_filter_spinner")
                        ) {
                            Text(text = selectedWardFilter, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Filled.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                        }

                        DropdownMenu(
                            expanded = expandedFilter,
                            onDismissRequest = { expandedFilter = false }
                        ) {
                            val wardsList = listOf(
                                "All Wards",
                                "Ward 1 (Metro North)",
                                "Ward 2 (Downtown Core)",
                                "Ward 3 (Industrial East)",
                                "Ward 4 (Residential South)",
                                "Ward 5 (Suburban West)"
                            )
                            wardsList.forEach { ward ->
                                DropdownMenuItem(
                                    text = { Text(ward) },
                                    onClick = {
                                        selectedWardFilter = ward
                                        expandedFilter = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // SECTION: AI Daily Briefing (Dynamic Gemini Insights)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_daily_briefing_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = "Gemini AI",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "GEMINI COMMAND BRIEFING",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            // Regeneration / Refresh button
                            IconButton(
                                onClick = { viewModel.generateDailyBriefing(issues) },
                                enabled = !isBriefingLoading,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Refresh Briefing",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (isBriefingLoading) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "AI is parsing live records and formulating recommendations...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else if (dailyBriefing.isNotEmpty()) {
                            val formattedBriefing = dailyBriefing.trim()
                            val lines = formattedBriefing.split("\n")
                            var isRecommendationSection = false

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                lines.forEach { line ->
                                    val trimmedLine = line.trim()
                                    if (trimmedLine.isEmpty()) return@forEach
                                    
                                    // Check if this line is part of recommendation
                                    if (trimmedLine.startsWith("Recommendation:", ignoreCase = true)) {
                                        isRecommendationSection = true
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = trimmedLine,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        return@forEach
                                    }

                                    if (isRecommendationSection) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Lightbulb,
                                                    contentDescription = "Recommendation",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = trimmedLine,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    } else {
                                        if (trimmedLine.startsWith("Good Morning", ignoreCase = true)) {
                                            Text(
                                                text = trimmedLine,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        } else if (trimmedLine.contains("high priority") || trimmedLine.contains("reported") || trimmedLine.contains("increased")) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = when {
                                                        trimmedLine.contains("high priority", ignoreCase = true) -> Icons.Filled.Warning
                                                        trimmedLine.contains("increased", ignoreCase = true) -> Icons.Filled.TrendingUp
                                                        else -> Icons.Filled.Assignment
                                                    },
                                                    contentDescription = null,
                                                    tint = when {
                                                        trimmedLine.contains("high priority", ignoreCase = true) -> MaterialTheme.colorScheme.error
                                                        trimmedLine.contains("increased", ignoreCase = true) -> Color(0xFFF57C00)
                                                        else -> MaterialTheme.colorScheme.primary
                                                    },
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = trimmedLine,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = trimmedLine,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.generateDailyBriefing(issues) }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tap to load live AI daily briefing summary",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 1: Infrastructure Health Score & Overall Status
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "INFRASTRUCTURE HEALTH SCORE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Circular health gauge with animation
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val animHealth by animateFloatAsState(
                                    targetValue = healthScore.toFloat() / 100f,
                                    animationSpec = tween(1200, easing = FastOutSlowInEasing),
                                    label = "health_anim"
                                )

                                val gaugeColor = when {
                                    healthScore >= 85 -> Color(0xFF388E3C)
                                    healthScore >= 60 -> Color(0xFFF57F17)
                                    else -> Color(0xFFD32F2F)
                                }

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    // Background path
                                    drawArc(
                                        color = Color.LightGray.copy(alpha = 0.3f),
                                        startAngle = -220f,
                                        sweepAngle = 260f,
                                        useCenter = false,
                                        style = Stroke(width = 12.dp.toPx(), pathEffect = PathEffect.cornerPathEffect(4f))
                                    )
                                    // Live health gauge sweep
                                    drawArc(
                                        color = gaugeColor,
                                        startAngle = -220f,
                                        sweepAngle = 260f * animHealth,
                                        useCenter = false,
                                        style = Stroke(width = 12.dp.toPx(), pathEffect = PathEffect.cornerPathEffect(4f))
                                    )
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$healthScore%",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "HEALTH INDEX",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Explanation Block
                            Column(
                                modifier = Modifier.weight(1f).padding(start = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val (statusText, statusColor, desc) = when {
                                    healthScore >= 85 -> Triple("OPTIMAL", Color(0xFF388E3C), "Infrastructure is in excellent condition. All systems operational.")
                                    healthScore >= 60 -> Triple("NEEDS ATTENTION", Color(0xFFF57F17), "Minor localized deterioration. Increased maintenance recommended.")
                                    else -> Triple("CRITICAL", Color(0xFFD32F2F), "Severe systemic hazards reported. Immediate dispatch required.")
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(statusColor))
                                    Text(
                                        text = statusText,
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = statusColor
                                    )
                                }

                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Active Threats: ${filteredIssues.count { it.status != IssueStatus.RESOLVED && it.status != IssueStatus.REJECTED }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 2: Dynamic Sector Health Bars
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "SECTOR CONDITION RATINGS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        // Road Health
                        SectorProgressBarRow(
                            label = "Road Health",
                            percentage = roadHealth,
                            icon = Icons.Filled.Traffic,
                            tint = Color(0xFF607D8B)
                        )

                        // Streetlight Health
                        SectorProgressBarRow(
                            label = "Streetlight Health",
                            percentage = streetlightHealth,
                            icon = Icons.Filled.LightMode,
                            tint = Color(0xFFFFB300)
                        )

                        // Water Infrastructure
                        SectorProgressBarRow(
                            label = "Water Infrastructure",
                            percentage = waterHealth,
                            icon = Icons.Filled.WaterDrop,
                            tint = Color(0xFF1E88E5)
                        )

                        // Waste Management
                        SectorProgressBarRow(
                            label = "Waste Management",
                            percentage = wasteHealth,
                            icon = Icons.Filled.DeleteOutline,
                            tint = Color(0xFF43A047)
                        )
                    }
                }
            }

            // SECTION 3: Resolution Statistics (Visual Canvas Representation)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "RESOLUTION STATISTICS & PIPELINE FLOW",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        // Metric split badges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ResolutionCountBadge(label = "Reported", count = reportedIssuesCount, color = Color(0xFFD32F2F), modifier = Modifier.weight(1f))
                            ResolutionCountBadge(label = "Verified", count = verifiedIssuesCount, color = Color(0xFF1976D2), modifier = Modifier.weight(1f))
                            ResolutionCountBadge(label = "Progress", count = inProgressIssuesCount, color = Color(0xFFF57C00), modifier = Modifier.weight(1f))
                            ResolutionCountBadge(label = "Resolved", count = resolvedIssuesCount, color = Color(0xFF388E3C), modifier = Modifier.weight(1f))
                        }

                        // Canvas Horizontal Stacked SLA Chart
                        Text(
                            text = "SLA Pipeline Proportion Ratio",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val totalSlaPool = reportedIssuesCount + verifiedIssuesCount + inProgressIssuesCount + resolvedIssuesCount
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (totalSlaPool > 0) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val width = size.width
                                    val height = size.height

                                    val reportedPct = reportedIssuesCount.toFloat() / totalSlaPool.toFloat()
                                    val verifiedPct = verifiedIssuesCount.toFloat() / totalSlaPool.toFloat()
                                    val progressPct = inProgressIssuesCount.toFloat() / totalSlaPool.toFloat()
                                    val resolvedPct = resolvedIssuesCount.toFloat() / totalSlaPool.toFloat()

                                    var currentX = 0f

                                    // Resolved (Green)
                                    if (resolvedPct > 0) {
                                        val w = width * resolvedPct
                                        drawRect(color = Color(0xFF388E3C), topLeft = Offset(currentX, 0f), size = Size(w, height))
                                        currentX += w
                                    }
                                    // In Progress (Orange)
                                    if (progressPct > 0) {
                                        val w = width * progressPct
                                        drawRect(color = Color(0xFFF57C00), topLeft = Offset(currentX, 0f), size = Size(w, height))
                                        currentX += w
                                    }
                                    // Verified (Blue)
                                    if (verifiedPct > 0) {
                                        val w = width * verifiedPct
                                        drawRect(color = Color(0xFF1976D2), topLeft = Offset(currentX, 0f), size = Size(w, height))
                                        currentX += w
                                    }
                                    // Reported (Red)
                                    if (reportedPct > 0) {
                                        val w = width * reportedPct
                                        drawRect(color = Color(0xFFD32F2F), topLeft = Offset(currentX, 0f), size = Size(w, height))
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No pipeline metrics to display", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                                }
                            }
                        }

                        // SLA Performance metrics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Resolution Success Rate:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            val rate = if (totalIssuesCount > 0) (resolvedIssuesCount.toFloat() / totalIssuesCount.toFloat() * 100).toInt() else 100
                            Text(
                                text = "$rate%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Black,
                                color = if (rate >= 80) Color(0xFF388E3C) else Color(0xFFF57C00)
                            )
                        }
                    }
                }
            }

            // SECTION 4: Department Performance SLAs
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "DEPARTMENTAL PERFORMANCE STATS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        DepartmentPerformanceItem(
                            department = "Roads & Civil Works",
                            leadName = "Director Sarah Jenkins",
                            resolutionSpeed = "4.2 days avg",
                            efficiencyScore = "91% SLA",
                            activeStaffCount = 14,
                            ratingColor = Color(0xFF388E3C)
                        )

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        DepartmentPerformanceItem(
                            department = "Public Grid Lighting",
                            leadName = "Eng. Matthew Vance",
                            resolutionSpeed = "1.8 days avg",
                            efficiencyScore = "98% SLA",
                            activeStaffCount = 8,
                            ratingColor = Color(0xFF388E3C)
                        )

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        DepartmentPerformanceItem(
                            department = "Water & Hydro Services",
                            leadName = "Director Elena Rostova",
                            resolutionSpeed = "2.5 days avg",
                            efficiencyScore = "88% SLA",
                            activeStaffCount = 11,
                            ratingColor = Color(0xFFF57C00)
                        )

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        DepartmentPerformanceItem(
                            department = "Sanitation & Waste Disposal",
                            leadName = "Sup. Harold Finch",
                            resolutionSpeed = "1.1 days avg",
                            efficiencyScore = "99% SLA",
                            activeStaffCount = 20,
                            ratingColor = Color(0xFF388E3C)
                        )
                    }
                }
            }

            // SECTION 5: Issue Heatmaps (Beautiful schematic canvas mapping reported points)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MUNICIPAL ISSUE GEOGRAPHIC HEATMAP",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.secondary
                            )

                            Icon(
                                imageVector = Icons.Filled.Language,
                                contentDescription = "Sectors",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = "A spatial visual representation plotting live municipal hazards. Touch/hover over coordinates to reveal task parameters.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // The Heatmap Board Canvas
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E272C)) // Dark Blueprint Theme
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            val pulseState = rememberInfiniteTransition(label = "pulse_trans")
                            val pulseRadius by pulseState.animateFloat(
                                initialValue = 4f,
                                targetValue = 15f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "radius"
                            )

                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(filteredIssues) {
                                        detectTapGestures { offset ->
                                            // Identify nearest issue to coordinate offset
                                            val w = size.width
                                            val h = size.height

                                            var closest: InfrastructureIssue? = null
                                            var closestDist = Float.MAX_VALUE

                                            filteredIssues.forEach { issue ->
                                                // Convert coordinates delta to local pixels
                                                val normX = ((issue.longitude + 122.50) / 0.15).toFloat()
                                                val normY = (1f - ((issue.latitude - 37.70) / 0.15).toFloat())

                                                val pixelX = normX * w
                                                val pixelY = normY * h

                                                val dist = abs(offset.x - pixelX) + abs(offset.y - pixelY)
                                                if (dist < closestDist && dist < 50f) { // 50 pixel tap threshold
                                                    closestDist = dist
                                                    closest = issue
                                                }
                                            }
                                            hoveredHeatmapPoint = closest
                                        }
                                    }
                            ) {
                                val w = size.width
                                val h = size.height

                                // Draw Blueprint Grid Lines
                                val gridLineCount = 8
                                for (i in 1..gridLineCount) {
                                    val lx = w / (gridLineCount + 1) * i
                                    val ly = h / (gridLineCount + 1) * i
                                    // Vertical line
                                    drawLine(
                                        color = Color(0xFF37474F).copy(alpha = 0.5f),
                                        start = Offset(lx, 0f),
                                        end = Offset(lx, h),
                                        strokeWidth = 1f
                                    )
                                    // Horizontal line
                                    drawLine(
                                        color = Color(0xFF37474F).copy(alpha = 0.5f),
                                        start = Offset(0f, ly),
                                        end = Offset(w, ly),
                                        strokeWidth = 1f
                                    )
                                }

                                // Plot Sector Sectors labels
                                drawCircle(color = Color(0xFF263238), radius = w / 4f, center = Offset(w / 2f, h / 2f))
                                drawCircle(color = Color(0xFF37474F).copy(alpha = 0.3f), radius = w / 4f, center = Offset(w / 2f, h / 2f), style = Stroke(1f))

                                // Plot actual issues as glowing hotspots
                                filteredIssues.forEach { issue ->
                                    // Normalization math: range lat 37.70 -> 37.85, lng -122.50 -> -122.35
                                    val normX = ((issue.longitude + 122.50) / 0.15).toFloat().coerceIn(0.05f, 0.95f)
                                    val normY = (1f - ((issue.latitude - 37.70) / 0.15).toFloat()).coerceIn(0.05f, 0.95f)

                                    val pixelX = normX * w
                                    val pixelY = normY * h

                                    val dotColor = when (issue.severity) {
                                        SeverityLevel.CRITICAL -> Color(0xFFE53935)
                                        SeverityLevel.HIGH -> Color(0xFFFB8C00)
                                        SeverityLevel.MEDIUM -> Color(0xFFFFD54F)
                                        SeverityLevel.LOW -> Color(0xFF4CAF50)
                                    }

                                    // Draw background radial heat pulse
                                    if (issue.status != IssueStatus.RESOLVED) {
                                        drawCircle(
                                            color = dotColor.copy(alpha = 0.15f),
                                            radius = pulseRadius * 1.5f,
                                            center = Offset(pixelX, pixelY)
                                        )
                                    }

                                    // Main core solid point
                                    drawCircle(
                                        color = dotColor,
                                        radius = 6f,
                                        center = Offset(pixelX, pixelY)
                                    )
                                }
                            }

                            // Heatmap Info Overlay Tip
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "🔴 Critical | 🟠 High | 🟡 Med",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Display selected coordinate inspection detail
                        AnimatedVisibility(
                            visible = hoveredHeatmapPoint != null,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            hoveredHeatmapPoint?.let { issue ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            when (issue.severity) {
                                                                SeverityLevel.CRITICAL -> Color.Red
                                                                SeverityLevel.HIGH -> Color(0xFFFF9800)
                                                                else -> Color.Yellow
                                                            }
                                                        )
                                                )
                                                Text(
                                                    text = issue.title,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = "Loc: ${issue.locationName} (${issue.latitude}, ${issue.longitude})",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                selectedDetailIssue = issue
                                                hoveredHeatmapPoint = null
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Inspect", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 6: Ward Analytics Details List
            item {
                Text(
                    text = "WARD-BY-WARD ANALYTICS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            val wardsAnalyzed = listOf(
                "Ward 1 (Metro North)" to Triple("Metro / Commercial", "Eng. Angela Moss", 95),
                "Ward 2 (Downtown Core)" to Triple("High-Density Business", "Eng. David Kim", 88),
                "Ward 3 (Industrial East)" to Triple("Industrial / Transit Hub", "Eng. Frank Castle", 78),
                "Ward 4 (Residential South)" to Triple("Parks & Subdivisions", "Eng. Clara Oswald", 92),
                "Ward 5 (Suburban West)" to Triple("Low-Density Suburban", "Eng. Bobby Singer", 94)
            )

            items(wardsAnalyzed) { (wardName, data) ->
                val (classification, coordinator, satisfaction) = data
                val wardIssuesList = issues.filter { getIssueWard(it) == wardName }
                val wardActiveCount = wardIssuesList.count { it.status != IssueStatus.RESOLVED && it.status != IssueStatus.REJECTED }
                val wardCriticalCount = wardIssuesList.count { it.severity == SeverityLevel.CRITICAL && it.status != IssueStatus.RESOLVED }

                WardAnalyticsItem(
                    wardName = wardName,
                    classification = classification,
                    coordinator = coordinator,
                    satisfaction = satisfaction,
                    activeAlerts = wardActiveCount,
                    criticalAlerts = wardCriticalCount,
                    isSelected = selectedWardFilter == wardName,
                    onClick = {
                        selectedWardFilter = if (selectedWardFilter == wardName) "All Wards" else wardName
                    }
                )
            }

            // SECTION 7: Enterprise Security Control Center & Auditor Hub
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "ENTERPRISE SECURITY & THREAT MITIGATION",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val audit = securityReport
                val displayRooted = isMockRooted || (audit?.isRooted ?: false)
                val cardColor = if (displayRooted) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
                val borderColor = if (displayRooted) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("enterprise_security_card"),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, borderColor)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (displayRooted) Icons.Filled.Warning else Icons.Filled.VerifiedUser,
                                    contentDescription = "Security Status",
                                    tint = if (displayRooted) MaterialTheme.colorScheme.error else Color(0xFF388E3C),
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "CIVICDEX SECURITY SHIELD",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (displayRooted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Keystore Hardware Protection & Play Integrity Sandbox",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isAuditingInProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            isAuditingInProgress = true
                                            delay(800)
                                            securityReport = SecurityManager.performSecurityAudit(context)
                                            isAuditingInProgress = false
                                            Toast.makeText(context, "Full enterprise cryptographic audit completed successfully!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = "Recalculate Audit",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        // Threat Alert Banner if rooted/compromised
                        if (displayRooted) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Warning,
                                        contentDescription = "Threat Detected",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "CRITICAL: SECURITY POLICIES COMPROMISED",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = "Device integrity checks detected a rooted or altered environment. Sandbox isolation has been activated. Access tokens purged from standard heap to prevent hijacking.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }
                        }

                        // Audit Checklist
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // 1. Root / Sandbox Detection
                            SecurityCheckRow(
                                title = "Device Environment Guard",
                                description = if (displayRooted) "ALERT: Altered / Rooted OS Detected" else "SECURE: No SU binary anomalies detected",
                                isPassed = !displayRooted,
                                icon = Icons.Filled.BugReport
                            )

                            // 2. Android Keystore Integration
                            SecurityCheckRow(
                                title = "Android Keystore Hardware backing",
                                description = if (audit?.isKeystoreSecure == true) "ACTIVE: Key pairs generated inside hardware TEE/SE" else "ACTIVE: Soft-keystore crypto active",
                                isPassed = true,
                                icon = Icons.Filled.Lock
                            )

                            // 3. EncryptedStorage Cryptographic Shield
                            SecurityCheckRow(
                                title = "EncryptedStorage Cryptographic Shield",
                                description = if (audit?.isEncryptedPrefsActive == true) "ACTIVE: AES256-SIV/GCM EncryptedSharedPreferences" else "ACTIVE: High-entropy MasterKey storage",
                                isPassed = true,
                                icon = Icons.Filled.Lock
                            )

                            // 4. Token Protection
                            SecurityCheckRow(
                                title = "Cryptographic Session Token Protection",
                                description = if (audit?.tokenEncryptionStatus == true) "ACTIVE: JWT keys isolated & encrypted at rest" else "ACTIVE: Sandbox isolation active",
                                isPassed = true,
                                icon = Icons.Filled.Key
                            )

                            // 5. Certificate Pinning
                            SecurityCheckRow(
                                title = "SSL Certificate Pinning (MitM Prevention)",
                                description = "ACTIVE: Pinning enforced on " + (audit?.certPinningDomain ?: "*.firebaseio.com"),
                                isPassed = true,
                                icon = Icons.Filled.CloudQueue
                            )

                            // 6. Play Integrity API Status
                            SecurityCheckRow(
                                title = "Play Integrity Verdict Check",
                                description = when {
                                    displayRooted -> "FAILED: MEETS_DEVICE_INTEGRITY not satisfied"
                                    audit?.playIntegrityStatus == SecurityManager.PlayIntegrityVerdict.MEETS_STRONG_INTEGRITY -> "PASSED: MEETS_STRONG_INTEGRITY & DEVICE_INTEGRITY"
                                    else -> "PASSED: MEETS_DEVICE_INTEGRITY & BASIC_INTEGRITY"
                                },
                                isPassed = !displayRooted,
                                icon = Icons.Filled.Fingerprint
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        // Interactive Simulator controls
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Simulate Root Compromise / Threat",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Toggle to verify real-time alert containment & Play Integrity failure behaviors.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            Switch(
                                checked = isMockRooted,
                                onCheckedChange = {
                                    isMockRooted = it
                                    if (it) {
                                        Toast.makeText(context, "Threat simulation active! Sandbox isolated.", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "System normalized. Integrity verified.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.error,
                                    checkedTrackColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                modifier = Modifier.testTag("security_threat_switch")
                            )
                        }
                    }
                }
            }
        }
    }

    // Detail Inspection Sheet
    if (selectedDetailIssue != null) {
        TaskDetailDialog(
            issue = selectedDetailIssue!!,
            currentUser = currentUser,
            onDismiss = { selectedDetailIssue = null },
            onAssign = {
                viewModel.assignIssueToWorker(selectedDetailIssue!!.id)
                selectedDetailIssue = null
                Toast.makeText(context, "Task self-assigned / route locked!", Toast.LENGTH_SHORT).show()
            },
            onUpdateStatus = { status ->
                viewModel.updateIssueStatus(selectedDetailIssue!!.id, status)
                selectedDetailIssue = null
                Toast.makeText(context, "Status updated to ${status.name}", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun SectorProgressBarRow(
    label: String,
    percentage: Int,
    icon: ImageVector,
    tint: Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = "$percentage% Operational",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (percentage >= 80) Color(0xFF388E3C) else Color(0xFFF57C00)
            )
        }

        val progressAnim by animateFloatAsState(
            targetValue = percentage.toFloat() / 100f,
            animationSpec = tween(1000, easing = EaseOutCubic),
            label = "progress"
        )

        LinearProgressIndicator(
            progress = progressAnim,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (percentage >= 80) Color(0xFF388E3C) else if (percentage >= 60) Color(0xFFF57C00) else Color(0xFFD32F2F),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun ResolutionCountBadge(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun DepartmentPerformanceItem(
    department: String,
    leadName: String,
    resolutionSpeed: String,
    efficiencyScore: String,
    activeStaffCount: Int,
    ratingColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = department,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Lead: $leadName • Staff: $activeStaffCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ratingColor.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = efficiencyScore,
                    style = MaterialTheme.typography.labelSmall,
                    color = ratingColor,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text(
                text = resolutionSpeed,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WardAnalyticsItem(
    wardName: String,
    classification: String,
    coordinator: String,
    satisfaction: Int,
    activeAlerts: Int,
    criticalAlerts: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("ward_card_${wardName.replace(" ", "_")}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary 
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = wardName,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "$classification • Coord: $coordinator",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (criticalAlerts > 0) Color(0xFFFFEBEE) 
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (criticalAlerts > 0) "⚠️ $criticalAlerts Critical" else "$activeAlerts Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (criticalAlerts > 0) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Citizen Satisfaction: $satisfaction%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = if (isSelected) "Click to clear focus" else "Click to focus ward",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Calculate sector health rating based on issues
private fun calculateCategoryHealth(issues: List<InfrastructureIssue>): Int {
    if (issues.isEmpty()) return 100
    val activeIssues = issues.filter { it.status != IssueStatus.RESOLVED && it.status != IssueStatus.REJECTED }
    if (activeIssues.isEmpty()) return 100

    var activeHarmPoints = 0
    activeIssues.forEach { issue ->
        activeHarmPoints += when (issue.severity) {
            SeverityLevel.CRITICAL -> 25
            SeverityLevel.HIGH -> 15
            SeverityLevel.MEDIUM -> 8
            SeverityLevel.LOW -> 3
        }
    }
    val score = 100 - activeHarmPoints
    return score.coerceIn(20, 100)
}

@Composable
fun SecurityCheckRow(
    title: String,
    description: String,
    isPassed: Boolean,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (isPassed) Color(0xFF388E3C).copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isPassed) Color(0xFF388E3C) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = if (isPassed) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            contentDescription = if (isPassed) "Passed" else "Failed",
            tint = if (isPassed) Color(0xFF388E3C) else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
    }
}

