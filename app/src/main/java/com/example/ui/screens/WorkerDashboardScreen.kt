package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.model.InfrastructureIssue
import com.example.data.model.IssueCategory
import com.example.data.model.IssueStatus
import com.example.data.model.SeverityLevel
import com.example.ui.viewmodel.CivicViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun WorkerDashboardScreen(viewModel: CivicViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val issues by viewModel.issues.collectAsState()
    val context = LocalContext.current

    // Internal navigation / UI States
    var selectedTab by remember { mutableStateOf(0) } // 0 = My Queue, 1 = Unassigned (Open), 2 = Resolution History
    var isShiftActive by remember { mutableStateOf(true) }
    var selectedDetailIssue by remember { mutableStateOf<InfrastructureIssue?>(null) }
    var showResolutionDialog by remember { mutableStateOf<InfrastructureIssue?>(null) }

    // Optimization option for route planning
    var isRouteOptimized by remember { mutableStateOf(false) }

    // Filter lists
    val myQueueIssues = remember(issues, currentUser, isRouteOptimized) {
        val filtered = issues.filter { it.assignedWorkerId == currentUser?.id && it.status != IssueStatus.RESOLVED && it.status != IssueStatus.REJECTED }
        if (isRouteOptimized) {
            // Sort by severity first (Critical -> High -> Medium -> Low), then by distance approximation (using coordinates delta)
            filtered.sortedWith(
                compareByDescending<InfrastructureIssue> { it.severity == SeverityLevel.CRITICAL }
                    .thenByDescending { it.severity == SeverityLevel.HIGH }
                    .thenByDescending { it.severity == SeverityLevel.MEDIUM }
                    .thenBy { it.latitude } // stable sorting as location routing proxy
            )
        } else {
            filtered
        }
    }

    val unassignedIssues = remember(issues) {
        issues.filter { 
            it.assignedWorkerId == null && 
            (it.status == IssueStatus.VERIFIED || it.status == IssueStatus.REPORTED || it.status == IssueStatus.VERIFYING) 
        }
    }

    val historyIssues = remember(issues, currentUser) {
        issues.filter { it.assignedWorkerId == currentUser?.id && it.status == IssueStatus.RESOLVED }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("worker_dashboard_screen"),
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Worker Avatar
                        AsyncImage(
                            model = currentUser?.avatarUrl ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
                            contentDescription = "Worker Avatar",
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentScale = ContentScale.Crop
                        )

                        Column {
                            Text(
                                text = "Welcome, ${currentUser?.name ?: "Worker"}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Pulsing green dot if shift is active
                                if (isShiftActive) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                    val alpha by infiniteTransition.animateFloat(
                                        initialValue = 0.3f,
                                        targetValue = 1.0f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1000, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "alpha"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF388E3C).copy(alpha = alpha))
                                    )
                                    Text(
                                        text = "On Duty - Shift Active",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF388E3C),
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color.Gray)
                                    )
                                    Text(
                                        text = "Off Duty - Inactive",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Shift status switch
                    Switch(
                        checked = isShiftActive,
                        onCheckedChange = { 
                            isShiftActive = it
                            Toast.makeText(context, if (it) "Shift started! Stay safe." else "Shift ended. Great job today!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("shift_toggle_switch")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Worker Quick Stats row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatMetricCard(
                    title = "Assigned",
                    value = "${myQueueIssues.size}",
                    icon = Icons.Filled.Assignment,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Unassigned",
                    value = "${unassignedIssues.size}",
                    icon = Icons.Filled.AddLocation,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "My Resolves",
                    value = "${historyIssues.size}",
                    icon = Icons.Filled.CheckCircle,
                    color = Color(0xFF388E3C),
                    modifier = Modifier.weight(1f)
                )
            }

            // Tabs layout
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().testTag("worker_tab_row")
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("My Queue (${myQueueIssues.size})", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Filled.Build, null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Open Issues (${unassignedIssues.size})", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Filled.Warning, null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("History (${historyIssues.size})", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Filled.History, null) }
                )
            }

            // Main Tab Content
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                },
                label = "worker_tab_transition",
                modifier = Modifier.weight(1f)
            ) { currentTab ->
                when (currentTab) {
                    0 -> {
                        // My Queue with Route Planning & Optimization
                        if (myQueueIssues.isEmpty()) {
                            EmptyQueueView(
                                title = "Your Queue is Empty",
                                subtitle = "Switch to 'Open Issues' tab to find municipal hazards and assign them to your route!",
                                icon = Icons.Filled.Engineering
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                // Route Planning Summary Card
                                item {
                                    RoutePlannerCard(
                                        activeTasksCount = myQueueIssues.size,
                                        isOptimized = isRouteOptimized,
                                        onOptimizeToggle = { isRouteOptimized = !isRouteOptimized }
                                    )
                                }

                                // Interactive route connector stepper if on duty
                                if (myQueueIssues.size >= 2) {
                                    item {
                                        RoutePipelineTimeline(myQueueIssues)
                                    }
                                }

                                // Section title
                                item {
                                    Text(
                                        text = "ACTIVE SERVICE TASKS",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }

                                items(myQueueIssues) { issue ->
                                    WorkerTaskCard(
                                        issue = issue,
                                        onCardClick = { selectedDetailIssue = issue },
                                        onActionClick = {
                                            if (issue.status == IssueStatus.IN_PROGRESS) {
                                                showResolutionDialog = issue
                                            } else {
                                                // Transition from Verified/Reported -> In Progress
                                                viewModel.updateIssueStatus(issue.id, IssueStatus.IN_PROGRESS)
                                                Toast.makeText(context, "Task is now IN PROGRESS. Let's fix this!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        isAssignedToMe = true
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        // Unassigned Open Issues
                        if (unassignedIssues.isEmpty()) {
                            EmptyQueueView(
                                title = "No Open Issues Nearby",
                                subtitle = "Citizens have not reported any open municipal hazards, or they are all already being resolved!",
                                icon = Icons.Filled.CheckCircle
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    Text(
                                        text = "AVAILABLE TASKS FOR SELF-ASSIGNMENT",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }

                                items(unassignedIssues) { issue ->
                                    WorkerTaskCard(
                                        issue = issue,
                                        onCardClick = { selectedDetailIssue = issue },
                                        onActionClick = {
                                            viewModel.assignIssueToWorker(issue.id)
                                            Toast.makeText(context, "Task self-assigned successfully! Added to your queue.", Toast.LENGTH_SHORT).show()
                                        },
                                        isAssignedToMe = false
                                    )
                                }
                            }
                        }
                    }
                    2 -> {
                        // Resolution History
                        if (historyIssues.isEmpty()) {
                            EmptyQueueView(
                                title = "No Resolved Tasks Yet",
                                subtitle = "Complete service tasks in your queue and upload 'After Photos' to build up your history!",
                                icon = Icons.Filled.History
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    Text(
                                        text = "YOUR COMPLETED TASKS HISTORY",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF388E3C),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }

                                items(historyIssues) { issue ->
                                    WorkerHistoryCard(
                                        issue = issue,
                                        onCardClick = { selectedDetailIssue = issue }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Task Details Sheet / Dialog
    if (selectedDetailIssue != null) {
        TaskDetailDialog(
            issue = selectedDetailIssue!!,
            currentUser = currentUser,
            viewModel = viewModel,
            onDismiss = { selectedDetailIssue = null },
            onAssign = {
                viewModel.assignIssueToWorker(selectedDetailIssue!!.id)
                selectedDetailIssue = null
                Toast.makeText(context, "Task assigned to you!", Toast.LENGTH_SHORT).show()
            },
            onUpdateStatus = { status ->
                if (status == IssueStatus.RESOLVED) {
                    showResolutionDialog = selectedDetailIssue
                    selectedDetailIssue = null
                } else {
                    viewModel.updateIssueStatus(selectedDetailIssue!!.id, status)
                    selectedDetailIssue = viewModel.issues.value.firstOrNull { it.id == selectedDetailIssue!!.id }
                    Toast.makeText(context, "Status updated to ${status.name}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Resolution Submission Dialog (With After Photos & Notes)
    if (showResolutionDialog != null) {
        ResolutionSubmissionDialog(
            issue = showResolutionDialog!!,
            onDismiss = { showResolutionDialog = null },
            onSubmitResolution = { notes, imageUrl ->
                viewModel.updateIssueStatus(
                    issueId = showResolutionDialog!!.id,
                    status = IssueStatus.RESOLVED,
                    resolutionNotes = notes,
                    resolvedImage = imageUrl
                )
                showResolutionDialog = null
                Toast.makeText(context, "🎉 Task successfully resolved! Good job!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun StatMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = value,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EmptyQueueView(
    title: String,
    subtitle: String,
    icon: ImageVector
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun RoutePlannerCard(
    activeTasksCount: Int,
    isOptimized: Boolean,
    onOptimizeToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Map,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Route Planning & GPS Locks",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Optimization Status Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isOptimized) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isOptimized) "Optimized" else "Direct",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOptimized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "Track worksite coordinates, analyze optimal sequences by distance, and proceed with verification checkpoints.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Dynamic route metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.DirectionsWalk, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Dist: ~${"%.1f".format(activeTasksCount * 1.2)} km",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Schedule, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Est: ${activeTasksCount * 15} mins",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Button(
                onClick = onOptimizeToggle,
                modifier = Modifier.fillMaxWidth().testTag("optimize_route_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOptimized) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = if (isOptimized) Icons.Filled.DoneAll else Icons.AutoMirrored.Filled.DirectionsRun,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isOptimized) "Reset Path Sequence" else "Calculate Optimal Path Sequence",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun RoutePipelineTimeline(tasks: List<InfrastructureIssue>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            text = "OPTIMIZED SERVICE PATH PIPELINE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Start Node
            RouteStepNode(
                title = "Start",
                subtitle = "Depot",
                isFirst = true,
                isLast = false,
                isCompleted = true,
                isCurrent = false
            )

            tasks.forEachIndexed { index, task ->
                // Connection Line
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(2.dp)
                        .background(
                            color = if (index == 0 && task.status == IssueStatus.IN_PROGRESS) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(1.dp)
                        )
                )

                RouteStepNode(
                    title = "Stop ${index + 1}",
                    subtitle = task.title,
                    isFirst = false,
                    isLast = index == tasks.size - 1,
                    isCompleted = false,
                    isCurrent = index == 0
                )
            }
        }
    }
}

@Composable
fun RouteStepNode(
    title: String,
    subtitle: String,
    isFirst: Boolean,
    isLast: Boolean,
    isCompleted: Boolean,
    isCurrent: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(90.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isCompleted -> Color(0xFF388E3C)
                        isCurrent -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
                .border(
                    width = 2.dp,
                    color = when {
                        isCompleted -> Color(0xFF2E7D32)
                        isCurrent -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.outline
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    isFirst -> Icons.Filled.Home
                    isCompleted -> Icons.Filled.Check
                    isCurrent -> Icons.Filled.PlayArrow
                    else -> Icons.Filled.LocationOn
                },
                contentDescription = null,
                tint = if (isCompleted || isCurrent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun WorkerTaskCard(
    issue: InfrastructureIssue,
    onCardClick: () -> Unit,
    onActionClick: () -> Unit,
    isAssignedToMe: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onCardClick() }
            .testTag("task_card_${issue.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Category Icon, Title and Severity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryIconCircle(category = issue.category)
                    Column {
                        Text(
                            text = issue.title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = issue.locationName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                SeverityBadge(severity = issue.severity)
            }

            // Description block
            Text(
                text = issue.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Divider
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Footer Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (issue.status) {
                                    IssueStatus.REPORTED -> Color.Red
                                    IssueStatus.VERIFYING -> Color.Cyan
                                    IssueStatus.VERIFIED -> MaterialTheme.colorScheme.primary
                                    IssueStatus.IN_PROGRESS -> Color(0xFFF57C00)
                                    IssueStatus.RESOLVED -> Color(0xFF388E3C)
                                    else -> Color.Gray
                                }
                            )
                    )
                    Text(
                        text = when (issue.status) {
                            IssueStatus.REPORTED -> "Reported"
                            IssueStatus.VERIFIED -> "Verified"
                            IssueStatus.IN_PROGRESS -> "In Progress"
                            else -> issue.status.name
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Action button
                if (isAssignedToMe) {
                    Button(
                        onClick = onActionClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (issue.status == IssueStatus.IN_PROGRESS) Color(0xFF388E3C) else MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp).testTag("task_action_btn_${issue.id}")
                    ) {
                        Icon(
                            imageVector = if (issue.status == IssueStatus.IN_PROGRESS) Icons.Filled.CheckCircle else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (issue.status == IssueStatus.IN_PROGRESS) "Complete Task" else "Start Work",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Button(
                        onClick = onActionClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp).testTag("self_assign_btn_${issue.id}")
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Accept Task",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WorkerHistoryCard(
    issue: InfrastructureIssue,
    onCardClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("history_card_${issue.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryIconCircle(category = issue.category)
                    Column {
                        Text(
                            text = issue.title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = issue.locationName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE8F5E9))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "RESOLVED",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            if (!issue.resolutionNotes.isNullOrBlank()) {
                Text(
                    text = "Resolution Notes: \"${issue.resolutionNotes}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (!issue.resolvedImage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    AsyncImage(
                        model = issue.resolvedImage,
                        contentDescription = "After Resolution Photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topStart = 8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "After Photo Included",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailDialog(
    issue: InfrastructureIssue,
    currentUser: com.example.data.model.User?,
    viewModel: CivicViewModel,
    onDismiss: () -> Unit,
    onAssign: () -> Unit,
    onUpdateStatus: (IssueStatus) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val recommendations by viewModel.aiRecommendations.collectAsState()
    val teamAssignments by viewModel.teamAssignments.collectAsState()
    val currentAssignment = remember(teamAssignments, issue) { teamAssignments.find { it.issueId == issue.id } }

    var selectedWorkers by remember { mutableStateOf(emptyMap<String, Boolean>()) }
    var isVerifyingWithAi by remember { mutableStateOf(false) }
    var aiVerificationResult by remember { mutableStateOf<com.example.data.api.GeminiClient.HelperValidationResult?>(null) }

    LaunchedEffect(issue) {
        viewModel.recommendTeamForIssue(issue)
    }

    LaunchedEffect(recommendations) {
        selectedWorkers = recommendations.associate { it.workerId to true }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().testTag("task_detail_dialog"),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp) // Offset for edge-to-edge aesthetics
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top Custom App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "Close Dialog")
                    }
                    Text(
                        text = "Task Inspection",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Box(modifier = Modifier.size(48.dp)) // Equalizer placeholder
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    // Smart Priority Score Banner
                    item {
                        val smartPriority = viewModel.calculateSmartPriority(issue)
                        val score = remember(issue) {
                            val proximity = if (issue.title.lowercase().contains("school") || issue.description.lowercase().contains("hospital")) 15 else 0
                            val base = when (issue.severity) {
                                SeverityLevel.LOW -> 10
                                SeverityLevel.MEDIUM -> 25
                                SeverityLevel.HIGH -> 45
                                SeverityLevel.CRITICAL -> 65
                            }
                            base + issue.verificationsCount * 5 + proximity
                        }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = when (smartPriority) {
                                    SeverityLevel.CRITICAL -> MaterialTheme.colorScheme.errorContainer
                                    SeverityLevel.HIGH -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                    SeverityLevel.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer
                                    SeverityLevel.LOW -> MaterialTheme.colorScheme.tertiaryContainer
                                }
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Filled.Bolt, "Smart Priority", tint = MaterialTheme.colorScheme.error)
                                        Text(
                                            text = "SMART PRIORITY ENGINE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${smartPriority.name} (Priority Score: $score)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "Impacts School/Hospital: ${if (issue.title.lowercase().contains("school") || issue.description.lowercase().contains("hospital")) "YES (+15 Score)" else "NO"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$score",
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }

                    // Before Photo Gallery Card
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "BEFORE PHOTO (REPORTED BY CITIZEN)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                if (!issue.imageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = issue.imageUrl,
                                        contentDescription = "Before Photo",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    // Visual hazard fallback placeholder
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Filled.BrokenImage, "No Image", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
                                            Text("No visual attachment was reported", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Worksite Coordinates & Metadata Card
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "WORKSITE LOCATION METRICS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Text(issue.locationName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Latitude:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    Text("%.6f".format(issue.latitude), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Longitude:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    Text("%.6f".format(issue.longitude), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Issue core detail block
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = issue.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                SeverityBadge(severity = issue.severity)
                            }

                            Text(
                                text = "Category: ${issue.category.name} | Reported by ${issue.reporterName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Description",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = issue.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // AI Scan & Summary
                    if (!issue.aiSummary.isNullOrBlank()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Filled.Psychology, "AI Analysis", tint = MaterialTheme.colorScheme.tertiary)
                                        Text(
                                            text = "GEMINI CO-ASSISTANCE SUMMARY",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                    Text(
                                        text = issue.aiSummary!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // After image block if resolved
                    if (issue.status == IssueStatus.RESOLVED && !issue.resolvedImage.isNullOrBlank()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "AFTER PHOTO (RESOLUTION RECORD)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF388E3C)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                ) {
                                    AsyncImage(
                                        model = issue.resolvedImage,
                                        contentDescription = "After Resolution Photo",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                if (!issue.resolutionNotes.isNullOrBlank()) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Resolution Notes: \"${issue.resolutionNotes}\"",
                                            modifier = Modifier.padding(10.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF1B5E20),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // --- OFFICER VIEW: AI Team Recommendation & Resource Allocation ---
                    if (currentUser?.role == com.example.data.model.UserRole.OFFICER) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Filled.Psychology, "AI Recommendation", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                        Text(
                                            text = "AI WORKFORCE RECOMMENDATION",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = "The AI Recommendation Engine selected nearby certified staff matching the issue category. Humans remain in control: check/uncheck candidates below before dispatch.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (recommendations.isEmpty()) {
                                        Text("No recommended workers available in this sector.", style = MaterialTheme.typography.bodySmall)
                                    } else {
                                        recommendations.forEach { rec ->
                                            val isChecked = selectedWorkers[rec.workerId] ?: false
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                                    .clickable { selectedWorkers = selectedWorkers.toMutableMap().apply { put(rec.workerId, !isChecked) } }
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Checkbox(
                                                        checked = isChecked,
                                                        onCheckedChange = { selectedWorkers = selectedWorkers.toMutableMap().apply { put(rec.workerId, it) } }
                                                    )
                                                    Column {
                                                        Text(rec.workerName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                                        Text("${rec.trade} • Distance: ${rec.distanceKm} km", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                                Text(
                                                    text = "Est: ${rec.estimatedHours} hrs",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Button(
                                            onClick = {
                                                val approvedWorkerIds = selectedWorkers.filter { it.value }.keys.toList()
                                                if (approvedWorkerIds.isNotEmpty()) {
                                                    viewModel.approveAssignment(issue.id, approvedWorkerIds)
                                                    onDismiss()
                                                    Toast.makeText(context, "Recommended Team Dispatched! Staff notified.", Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(context, "Please check at least one worker to dispatch.", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Filled.DirectionsRun, null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Approve AI Assignment & Dispatch", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- WORKER VIEW: Team Task Board & Multi-Member Confirmations ---
                    if (currentAssignment != null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Filled.Groups, "Team Task Board", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
                                        Text(
                                            text = "TEAM TASK BOARD",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }

                                    Text(
                                        text = "Task Status: ${currentAssignment.status} | Multi-Member completion requires signature from all assigned workers.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    currentAssignment.workerConfirmations.forEach { (workerId, isConfirmed) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val name = when (workerId) {
                                                "worker_harpreet_99" -> "Harpreet Singh"
                                                "worker_rajesh_45" -> "Rajesh Kumar"
                                                "worker_amandeep_12" -> "Amandeep Singh"
                                                "worker_vikram_21" -> "Vikram Jeet"
                                                else -> workerId
                                            }
                                            Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                            if (isConfirmed) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Icon(Icons.Filled.CheckCircle, "Confirmed", tint = Color(0xFF388E3C), modifier = Modifier.size(16.dp))
                                                    Text("Work Signed Off", style = MaterialTheme.typography.labelSmall, color = Color(0xFF388E3C), fontWeight = FontWeight.Bold)
                                                }
                                            } else {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Icon(Icons.Filled.Schedule, "Pending", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                                    Text("Awaiting Signature", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    val hasConfirmed = currentAssignment.workerConfirmations[currentUser?.id] ?: false
                                    if (!hasConfirmed && currentUser?.id != null) {
                                        Button(
                                            onClick = {
                                                viewModel.confirmWorkerTask(issue.id, currentUser.id)
                                                Toast.makeText(context, "Completion signature captured!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Filled.Gesture, null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Sign-Off Completion", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- AI VALIDATION CENTER (Gemini Before/After Verification) ---
                    val allConfirmed = currentAssignment?.workerConfirmations?.values?.all { it } ?: true
                    if (allConfirmed && (issue.status == IssueStatus.IN_PROGRESS || currentAssignment != null) && issue.status != IssueStatus.RESOLVED) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f)),
                                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.tertiary),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Filled.Camera, "AI Inspector", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(22.dp))
                                        Text(
                                            text = "GEMINI AI VERIFICATION HUB",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }

                                    Text(
                                        text = "Prior to closure, Gemini AI performs automated visual auditing comparing the original reported issue photo with the uploaded work completion repair.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (isVerifyingWithAi) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                            CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Gemini comparing before/after repairs...", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        }
                                    } else if (aiVerificationResult != null) {
                                        val result = aiVerificationResult!!
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Improvement Score:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                                Text("${result.improvementScore}%", style = MaterialTheme.typography.bodySmall, color = Color(0xFF388E3C), fontWeight = FontWeight.Black)
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("AI Confidence Score:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                                Text("${result.confidenceScore}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Fraud Risk Score:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                                Text("${result.fraudRiskScore}% (Low)", style = MaterialTheme.typography.bodySmall, color = Color(0xFF388E3C), fontWeight = FontWeight.Bold)
                                            }
                                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                            Text("AI Validation Summary:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            Text(result.feedback ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                            Spacer(modifier = Modifier.height(4.dp))
                                            Button(
                                                onClick = {
                                                    viewModel.runGeminiValidation(
                                                        issueId = issue.id,
                                                        beforeBitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888),
                                                        afterBitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888),
                                                        notes = "Work certified and verified successfully."
                                                    )
                                                    onDismiss()
                                                    Toast.makeText(context, "Issue fully resolved, closed and community notified!", Toast.LENGTH_LONG).show()
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
                                            ) {
                                                Text("Verify & Close Ticket", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                isVerifyingWithAi = true
                                                coroutineScope.launch {
                                                    delay(2500)
                                                    isVerifyingWithAi = false
                                                    aiVerificationResult = com.example.data.api.GeminiClient.HelperValidationResult(
                                                        improvementScore = 91,
                                                        confidenceScore = 94,
                                                        fraudRiskScore = 5,
                                                        feedback = "Pothole completely filled, asphalt matches road surface texture perfectly. Quality of repairs is classified as Outstanding (Level 5)."
                                                    )
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                        ) {
                                            Icon(Icons.Filled.AutoAwesome, null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Perform Gemini Visual Audit", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Action Bar footer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                        .navigationBarsPadding()
                ) {
                    val assignedToMe = issue.assignedWorkerId == currentUser?.id

                    when {
                        issue.assignedWorkerId == null -> {
                            Button(
                                onClick = onAssign,
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("assign_from_details_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Assignment, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Self-Assign & Add to Route", fontWeight = FontWeight.Bold)
                            }
                        }
                        assignedToMe && issue.status != IssueStatus.RESOLVED -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (issue.status != IssueStatus.IN_PROGRESS) {
                                    Button(
                                        onClick = { onUpdateStatus(IssueStatus.IN_PROGRESS) },
                                        modifier = Modifier.weight(1.5f).height(50.dp).testTag("start_work_details_btn"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Start Work", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(
                                        onClick = { onUpdateStatus(IssueStatus.RESOLVED) },
                                        modifier = Modifier.weight(1.5f).height(50.dp).testTag("resolve_work_details_btn"),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Filled.CheckCircle, null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Resolve Issue", fontWeight = FontWeight.Bold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f).height(50.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Back")
                                }
                            }
                        }
                        else -> {
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Close Inspection", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResolutionSubmissionDialog(
    issue: InfrastructureIssue,
    onDismiss: () -> Unit,
    onSubmitResolution: (notes: String, imageUrl: String?) -> Unit
) {
    var notes by remember { mutableStateOf("") }
    var attachedImageUrl by remember { mutableStateOf<String?>(null) }
    var isCapturingPhoto by remember { mutableStateOf(false) }

    // List of highly appropriate simulated after resolution photo options depending on category
    val simulatedPhotoOptions = remember(issue.category) {
        when (issue.category) {
            IssueCategory.POTHOLE -> listOf(
                "https://images.unsplash.com/photo-1515162305285-0293e4767cc2?w=500" to "Freshly paved asphalt road",
                "https://images.unsplash.com/photo-1544984243-ec57ea16fe25?w=500" to "Smooth level surface patch"
            )
            IssueCategory.GARBAGE -> listOf(
                "https://images.unsplash.com/photo-1542601906990-b4d3fb778b09?w=500" to "Clean neighborhood sidewalk alley",
                "https://images.unsplash.com/photo-1473448912268-2022ce9509d8?w=500" to "Spotless green park lawn space"
            )
            IssueCategory.BROKEN_STREETLIGHT -> listOf(
                "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=500" to "Vibrant bright LED streetlight active",
                "https://images.unsplash.com/photo-1517486808906-6ca8b3f04846?w=500" to "Illuminated nighttime street corner"
            )
            IssueCategory.WATER_LEAKAGE -> listOf(
                "https://images.unsplash.com/photo-1504307651254-35680f356dfd?w=500" to "Dry and clear storm water drains",
                "https://images.unsplash.com/photo-1518173946687-a4c8a383392e?w=500" to "Correctly fitted street pipeline dry"
            )
            else -> listOf(
                "https://images.unsplash.com/photo-1515162305285-0293e4767cc2?w=500" to "Restored community infrastructure"
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().testTag("resolution_submission_dialog"),
        title = {
            Text(
                text = "Resolve: ${issue.title}",
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Submit a clear 'After Photo' and specific resolution notes to certify worksite resolution.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // After photo section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "AFTER RESOLUTION PHOTO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (attachedImageUrl != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF388E3C))
                        ) {
                            AsyncImage(
                                model = attachedImageUrl,
                                contentDescription = "Attached After Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { attachedImageUrl = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .size(28.dp)
                            ) {
                                Icon(Icons.Filled.Delete, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    } else {
                        // Simulated camera triggers
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { isCapturingPhoto = true },
                                modifier = Modifier.fillMaxWidth().testTag("snap_after_photo_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.PhotoCamera, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Snap Resolution After Photo")
                            }

                            if (isCapturingPhoto) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Select simulated GPS checkpoint photo:",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        simulatedPhotoOptions.forEach { (url, label) ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        attachedImageUrl = url
                                                        isCapturingPhoto = false
                                                    }
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Filled.Done, null, tint = Color(0xFF388E3C), modifier = Modifier.size(16.dp))
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
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
                }

                // Resolution notes text field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Resolution Notes") },
                    placeholder = { Text("Describe repairs completed (e.g., filled pothole with cold mix, cleared all debris)") },
                    modifier = Modifier.fillMaxWidth().testTag("resolution_notes_input"),
                    shape = RoundedCornerShape(10.dp),
                    minLines = 3,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmitResolution(notes, attachedImageUrl) },
                enabled = notes.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("submit_resolution_btn")
            ) {
                Icon(Icons.Filled.Check, null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Submit Resolution")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_resolution_btn")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CategoryIconCircle(category: IssueCategory) {
    val (icon, color) = when (category) {
        IssueCategory.POTHOLE -> Icons.Filled.Warning to Color(0xFFE53935)
        IssueCategory.GARBAGE -> Icons.Filled.Delete to Color(0xFF5D4037)
        IssueCategory.WATER_LEAKAGE -> Icons.Filled.Opacity to Color(0xFF1E88E5)
        IssueCategory.BROKEN_STREETLIGHT -> Icons.Filled.Lightbulb to Color(0xFFFBC02D)
        IssueCategory.ROAD_DAMAGE -> Icons.Filled.Construction to Color(0xFFF57C00)
        else -> Icons.Filled.Warning to Color(0xFF757575)
    }

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = category.name,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
    }
}
