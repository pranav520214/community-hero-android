package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.*
import com.example.ui.theme.frostedGlass
import com.example.ui.viewmodel.CivicViewModel

// Coordinates bounds for Riverside SF simulation
private const val MIN_LAT = 37.765
private const val MAX_LAT = 37.790
private const val MIN_LNG = -122.455
private const val MAX_LNG = -122.410

@Composable
fun HomeMapScreen(
    viewModel: CivicViewModel,
    onNavigateToReport: () -> Unit
) {
    val issues by viewModel.issues.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val selectedIssueDetail by viewModel.selectedIssue.collectAsState()
    val selectedIssueVotes by viewModel.selectedIssueVotes.collectAsState()

    var viewMode by remember { mutableStateOf("map") } // "map" or "feed"
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<IssueCategory?>(null) }

    var showVerifyDialog by remember { mutableStateOf(false) }
    var showEvidenceDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Filtered issues for feed and map
    val filteredIssues = remember(issues, searchQuery, selectedCategory) {
        issues.filter { issue ->
            val matchesCategory = selectedCategory == null || issue.category == selectedCategory
            val matchesSearch = searchQuery.isEmpty() ||
                    issue.title.contains(searchQuery, ignoreCase = true) ||
                    issue.description.contains(searchQuery, ignoreCase = true) ||
                    issue.locationName.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            // Hide FAB if map detail bottom sheet is active to prevent visual clutter
            val showFab = selectedIssueDetail == null || viewMode == "feed"
            AnimatedVisibility(
                visible = showFab,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToReport,
                    icon = { Icon(Icons.Filled.AddAlert, "Report Hazard") },
                    text = { Text("Report Issue") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("home_report_fab")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header Switch: Map View vs List Feed (Inspired by Google Maps / Yelp split screens)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("map" to "Map View", "feed" to "List Feed").forEach { (mode, label) ->
                    val isSelected = viewMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable {
                                viewMode = mode
                                if (mode == "feed") {
                                    // Deselect issue in feed to prevent layout overlays
                                    viewModel.selectIssue(null)
                                }
                            }
                            .padding(vertical = 10.dp)
                            .testTag("toggle_view_$mode"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (mode == "map") Icons.Filled.Map else Icons.Filled.ListAlt,
                                contentDescription = label,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Quick Category Filter Row (Sticky on top)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All Categories") },
                        leadingIcon = { Icon(Icons.Filled.FilterList, "All", modifier = Modifier.size(16.dp)) }
                    )
                }
                items(IssueCategory.values()) { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = { Text(category.name.lowercase().capitalize()) },
                        leadingIcon = {
                            Icon(
                                imageVector = getCategoryIcon(category),
                                contentDescription = category.name,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Body Switcher
            Box(modifier = Modifier.weight(1f)) {
                if (viewMode == "map") {
                    // MAP CONTAINER (Primary View)
                    Box(modifier = Modifier.fillMaxSize()) {
                        InteractiveMapView(
                            issues = issues,
                            selectedIssue = selectedIssueDetail,
                            onIssueClick = { viewModel.selectIssue(it) },
                            searchQuery = searchQuery,
                            selectedCategory = selectedCategory,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Floating Search Overlay on top of Map
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .align(Alignment.TopCenter)
                                .frostedGlass(RoundedCornerShape(24.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Search map icon",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search map...", style = MaterialTheme.typography.bodyMedium) },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("map_search_field")
                                )
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Filled.Clear, "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    modifier = Modifier.size(32.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = currentUser?.name?.take(1)?.uppercase() ?: "C",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        // Floating Layer controls on the Map (Google style)
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SmallFloatingActionButton(
                                onClick = {
                                    Toast.makeText(context, "Switching to Satellite Hybrid simulation", Toast.LENGTH_SHORT).show()
                                },
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ) {
                                Icon(Icons.Filled.Layers, "Layers")
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    Toast.makeText(context, "Centered on your active position • Riverside District", Toast.LENGTH_SHORT).show()
                                },
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ) {
                                Icon(Icons.Filled.MyLocation, "My Location")
                            }
                        }

                        // Smoothly animated Map Bottom Sheet Overlay
                        androidx.compose.animation.AnimatedVisibility(
                            visible = selectedIssueDetail != null,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                            ) + fadeIn(),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                            ) + fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .zIndex(100f)
                        ) {
                            selectedIssueDetail?.let { issue ->
                                GoogleMapsBottomSheet(
                                    issue = issue,
                                    currentUser = currentUser ?: User("sys", "Citizen", "", ""),
                                    votes = selectedIssueVotes,
                                    onDismiss = { viewModel.selectIssue(null) },
                                    onVerifyClick = { showVerifyDialog = true },
                                    onAddEvidenceClick = { showEvidenceDialog = true },
                                    onShareClick = {
                                        clipboardManager.setText(AnnotatedString("https://civicdex.org/issue/${issue.id}"))
                                        Toast.makeText(context, "🔗 Report share link copied!", Toast.LENGTH_LONG).show()
                                    },
                                    onHelperResolve = { before, after, beforeUrl, afterUrl, notes ->
                                        viewModel.submitHelperVerification(issue.id, before, after, beforeUrl, afterUrl, notes)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // LIST FEED CONTAINER (Alternative View)
                    if (filteredIssues.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "No issues icon",
                                    tint = Color(0xFF388E3C),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Clear and Secure!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "No matching hazard reports found in this area.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(filteredIssues, key = { it.id }) { issue ->
                                IssueItemCard(
                                    issue = issue,
                                    onUpvote = { viewModel.upvoteIssue(issue.id) },
                                    onClick = {
                                        viewModel.selectIssue(issue)
                                        // Switch to map view to display sheet instantly
                                        viewMode = "map"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Verification Comment Dialog
    if (showVerifyDialog && selectedIssueDetail != null) {
        var commentText by remember { mutableStateOf("") }
        var isLegitimate by remember { mutableStateOf(true) }

        Dialog(onDismissRequest = { showVerifyDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Verify Infrastructure Hazard",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Your verification score strengthens civic database reliability. Is this issue still present?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Switch selection for Legitimacy
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(true to "Still Present", false to "Already Resolved").forEach { (state, text) ->
                            val active = isLegitimate == state
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { isLegitimate = state }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        label = { Text("Add Verifier Note") },
                        placeholder = { Text("E.g., Verified pothole is still active in the right lane...") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showVerifyDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val finalComment = if (commentText.isNotBlank()) commentText else (if (isLegitimate) "Confirmed present by community" else "Reported as resolved/not present")
                                viewModel.verifyIssue(
                                    issueId = selectedIssueDetail!!.id,
                                    isLegitimate = isLegitimate,
                                    comment = finalComment,
                                    verificationType = if (isLegitimate) "CONFIRM" else "NOT_PRESENT"
                                )
                                showVerifyDialog = false
                                Toast.makeText(context, "🎉 Verification Submitted! +5 Reputation Points.", Toast.LENGTH_LONG).show()
                            }
                        ) {
                            Text("Submit Verification")
                        }
                    }
                }
            }
        }
    }

    // Modal Add Evidence Dialog
    if (showEvidenceDialog && selectedIssueDetail != null) {
        var evidenceNotes by remember { mutableStateOf("") }
        var evidenceImageUrl by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showEvidenceDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Add Live Community Evidence",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Help your neighbors by providing updated status notes, measurements, or live hazard photos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = evidenceNotes,
                        onValueChange = { evidenceNotes = it },
                        label = { Text("Status Update Notes") },
                        placeholder = { Text("E.g., Pothole is deeper now after the storm, water pooling...") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = evidenceImageUrl,
                        onValueChange = { evidenceImageUrl = it },
                        label = { Text("Evidence Image URL (Optional)") },
                        placeholder = { Text("https://images.unsplash.com/...") },
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showEvidenceDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.addPost(
                                    content = "Added live update on '${selectedIssueDetail!!.title}': $evidenceNotes",
                                    category = "Updates"
                                )
                                viewModel.verifyIssue(
                                    issueId = selectedIssueDetail!!.id,
                                    isLegitimate = true,
                                    comment = evidenceNotes,
                                    verificationType = "EVIDENCE",
                                    evidenceImageUrl = if (evidenceImageUrl.isNotBlank()) evidenceImageUrl else null
                                )
                                showEvidenceDialog = false
                                Toast.makeText(context, "📸 Evidence Logged successfully! +15 Reputation Points.", Toast.LENGTH_LONG).show()
                            },
                            enabled = evidenceNotes.isNotBlank()
                        ) {
                            Text("Upload Evidence")
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 1. HIGH-FIDELITY VECTOR MAP CANVAS
// ==========================================
@Composable
fun InteractiveMapView(
    issues: List<InfrastructureIssue>,
    selectedIssue: InfrastructureIssue?,
    onIssueClick: (InfrastructureIssue) -> Unit,
    searchQuery: String,
    selectedCategory: IssueCategory?,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1.3f) }
    var offset by remember { mutableStateOf(Offset(-50f, -50f)) }

    val isDark = isSystemInDarkTheme()

    // Stylized color configuration matching Google Maps
    val mapBgColor = if (isDark) Color(0xFF1B2230) else Color(0xFFF1F3F4)
    val waterColor = if (isDark) Color(0xFF122C4D) else Color(0xFFC4E0E5)
    val parkColor = if (isDark) Color(0xFF143B21) else Color(0xFFD4EDDA)
    val roadColor = if (isDark) Color(0xFF2C394E) else Color(0xFFFFFFFF)
    val highwayColor = if (isDark) Color(0xFF38495F) else Color(0xFFFFF9C4)
    val textSecondaryColor = if (isDark) Color(0xFF90A4AE) else Color(0xFF78909C)

    val filteredIssues = remember(issues, searchQuery, selectedCategory) {
        issues.filter { issue ->
            val matchesCategory = selectedCategory == null || issue.category == selectedCategory
            val matchesSearch = searchQuery.isEmpty() ||
                    issue.title.contains(searchQuery, ignoreCase = true) ||
                    issue.description.contains(searchQuery, ignoreCase = true) ||
                    issue.locationName.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(mapBgColor)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.7f, 3.5f)
                    val maxOffset = 1800f * scale
                    offset = Offset(
                        x = (offset.x + pan.x).coerceIn(-maxOffset, maxOffset),
                        y = (offset.y + pan.y).coerceIn(-maxOffset, maxOffset)
                    )
                }
            }
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        val virtualWidth = 1200f
        val virtualHeight = 1200f

        // Convert virtual map coordinates to actual screen coordinates (accounting for zoom and pan)
        fun getScreenOffset(lat: Double, lng: Double): Offset {
            val pctX = (lng - MIN_LNG) / (MAX_LNG - MIN_LNG)
            val pctY = (MAX_LAT - lat) / (MAX_LAT - MIN_LAT) // invert for screen-y

            val mapX = (pctX * virtualWidth).toFloat()
            val mapY = (pctY * virtualHeight).toFloat()

            val screenX = (width / 2f) + (mapX - (virtualWidth / 2f)) * scale + offset.x
            val screenY = (height / 2f) + (mapY - (virtualHeight / 2f)) * scale + offset.y
            return Offset(screenX, screenY)
        }

        // Draw Map Elements
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw River
            val riverStart = getScreenOffset(37.792, -122.450)
            val riverEnd = getScreenOffset(37.762, -122.412)
            drawLine(
                color = waterColor,
                start = riverStart,
                end = riverEnd,
                strokeWidth = 90f * scale,
                cap = StrokeCap.Round
            )

            // Draw Parks
            // Central Park
            val park1TopLeft = getScreenOffset(37.781, -122.446)
            val park1BottomRight = getScreenOffset(37.771, -122.436)
            drawRect(
                color = parkColor,
                topLeft = park1TopLeft,
                size = Size(
                    width = park1BottomRight.x - park1TopLeft.x,
                    height = park1BottomRight.y - park1TopLeft.y
                )
            )

            // Pine Grove Park
            val park2TopLeft = getScreenOffset(37.789, -122.426)
            val park2BottomRight = getScreenOffset(37.783, -122.416)
            drawRect(
                color = parkColor,
                topLeft = park2TopLeft,
                size = Size(
                    width = park2BottomRight.x - park2TopLeft.x,
                    height = park2BottomRight.y - park2TopLeft.y
                )
            )

            // Draw Roads
            // Elmwood Blvd (Arterial)
            drawLine(
                color = highwayColor,
                start = getScreenOffset(37.775, -122.455),
                end = getScreenOffset(37.775, -122.410),
                strokeWidth = 14f * scale
            )

            // Broadway Ave (Arterial)
            drawLine(
                color = highwayColor,
                start = getScreenOffset(37.785, -122.455),
                end = getScreenOffset(37.785, -122.410),
                strokeWidth = 14f * scale
            )

            // Local Street: Pine Grove Lane
            drawLine(
                color = roadColor,
                start = getScreenOffset(37.790, -122.418),
                end = getScreenOffset(37.765, -122.418),
                strokeWidth = 10f * scale
            )

            // Local Street: Market Square Alleyway
            drawLine(
                color = roadColor,
                start = getScreenOffset(37.790, -122.446),
                end = getScreenOffset(37.765, -122.446),
                strokeWidth = 10f * scale
            )

            // Local Street: Oak Street
            drawLine(
                color = roadColor,
                start = getScreenOffset(37.790, -122.430),
                end = getScreenOffset(37.765, -122.430),
                strokeWidth = 10f * scale
            )

            // User Pulse Dot (Dynamic GPS Marker)
            val userPos = getScreenOffset(37.7790, -122.4220)
            val pulseRatio = (System.currentTimeMillis() % 1200f) / 1200f
            drawCircle(
                color = Color(0x222196F3),
                radius = 24f * scale * (1f + pulseRatio * 0.6f),
                center = userPos
            )
            drawCircle(
                color = Color(0xFF2196F3),
                radius = 8f * scale,
                center = userPos
            )
            drawCircle(
                color = Color.White,
                radius = 3.5f * scale,
                center = userPos
            )
        }

        // Draw Interactive Overlay Pins (HTML-like coordinates overlay)
        filteredIssues.forEach { issue ->
            val screenPos = getScreenOffset(issue.latitude, issue.longitude)
            val isSelected = selectedIssue?.id == issue.id

            // Determine Marker severity color (Green = Low, Amber = Medium, Red = High/Critical)
            val severityColor = when (issue.severity) {
                SeverityLevel.LOW -> Color(0xFF4CAF50)
                SeverityLevel.MEDIUM -> Color(0xFFFFB300)
                SeverityLevel.HIGH -> Color(0xFFF44336)
                SeverityLevel.CRITICAL -> Color(0xFFD32F2F)
            }

            // Put a composable exactly over the projected coordinate
            Box(
                modifier = Modifier
                    .offset(
                        x = (screenPos.x / LocalDensity.current.density).dp - 90.dp, // Center alignment offset (180.dp / 2)
                        y = (screenPos.y / LocalDensity.current.density).dp - 44.dp  // Tip alignment offset
                    )
                    .width(180.dp)
                    .wrapContentHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onIssueClick(issue) }
                ) {
                    // Google Map Style Custom Badge: Displays Severity + Category
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) severityColor else Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .shadow(if (isSelected) 12.dp else 4.dp, RoundedCornerShape(12.dp))
                            .border(1.5.dp, severityColor, RoundedCornerShape(12.dp))
                            .padding(1.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = getCategoryIcon(issue.category),
                                contentDescription = issue.category.name,
                                tint = if (isSelected) Color.White else severityColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${issue.severity.name} • ${issue.category.name}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color.Black,
                                maxLines = 1
                            )
                        }
                    }

                    // Map pin tail anchor
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(severityColor, shape = CircleShape)
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                }
            }
        }
    }
}

// ==========================================
// 2. GOOGLE MAPS DESIGN INSPIRED BOTTOM SHEET
// ==========================================
@Composable
fun GoogleMapsBottomSheet(
    issue: InfrastructureIssue,
    currentUser: User,
    votes: List<VerificationVote>,
    onDismiss: () -> Unit,
    onVerifyClick: () -> Unit,
    onAddEvidenceClick: () -> Unit,
    onShareClick: () -> Unit,
    onHelperResolve: (android.graphics.Bitmap, android.graphics.Bitmap, String, String, String) -> Unit
) {
    // Dynamic trust score mapping (10% to 100%)
    val trustScore = remember(issue) {
        val base = 45
        val upvotesBonus = (issue.upvotesCount * 4).coerceAtMost(25)
        val verificationsBonus = (issue.verificationsCount * 12).coerceAtMost(30)
        val statusPenalty = if (issue.status == IssueStatus.REJECTED) -35 else 0
        (base + upvotesBonus + verificationsBonus + statusPenalty).coerceIn(10, 100)
    }

    val trustLabel = when {
        trustScore >= 80 -> "🌟 High consensus (Certified Neighbor-Verified)"
        trustScore >= 50 -> "👍 Medium consensus (Under community review)"
        else -> "⚠️ Low consensus (Caution advised)"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .shadow(24.dp)
            .testTag("map_bottom_sheet"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Drag / Close anchor line
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp)
            )

            // Header close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hazard Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_sheet_btn")) {
                    Icon(Icons.Filled.Close, "Close details")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Image view with fallbacks
                val fallbackImg = when (issue.category) {
                    IssueCategory.ROADS -> "https://images.unsplash.com/photo-1515162305285-0293e4767cc2?w=500"
                    IssueCategory.LIGHTING -> "https://images.unsplash.com/photo-1509024644558-2f56ce76c490?w=500"
                    IssueCategory.SANITATION -> "https://images.unsplash.com/photo-1611284446314-60a58ac0deb9?w=500"
                    IssueCategory.WATER -> "https://images.unsplash.com/photo-1508873696983-2df519f0397e?w=500"
                    IssueCategory.POWER -> "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=500"
                    IssueCategory.PARKS -> "https://images.unsplash.com/photo-1448375240586-882707db888b?w=500"
                    IssueCategory.OTHER -> "https://images.unsplash.com/photo-1532372320572-cda25653a26d?w=500"
                    IssueCategory.POTHOLE -> "https://images.unsplash.com/photo-1515162305285-0293e4767cc2?w=500"
                    IssueCategory.GARBAGE -> "https://images.unsplash.com/photo-1611284446314-60a58ac0deb9?w=500"
                    IssueCategory.WATER_LEAKAGE -> "https://images.unsplash.com/photo-1508873696983-2df519f0397e?w=500"
                    IssueCategory.BROKEN_STREETLIGHT -> "https://images.unsplash.com/photo-1509024644558-2f56ce76c490?w=500"
                    IssueCategory.ROAD_DAMAGE -> "https://images.unsplash.com/photo-1515162305285-0293e4767cc2?w=500"
                }

                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(issue.imageUrl ?: fallbackImg)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Hazard snapshot",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Title + metadata
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = issue.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = issue.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusBadge(issue.status)
                        SeverityBadge(issue.severity)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sub-details panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Location Name
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = "Location",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = issue.locationName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Reporter Information
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = "Reporter",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reported by ${issue.reporterName} • ${formatTime(issue.timestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Community Helper System Workshop Panel
                if (currentUser.role == UserRole.CONTRIBUTOR && issue.status != IssueStatus.RESOLVED) {
                    var helperAccepted by remember(issue.id) { mutableStateOf(false) }
                    var helperStep by remember(issue.id) { mutableStateOf(1) } // 1: Accepted, 2: Before Photo, 3: Work Done, 4: After Photo
                    var beforeBitmap by remember(issue.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
                    var afterBitmap by remember(issue.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
                    var repairNotes by remember(issue.id) { mutableStateOf("") }
                    var isSubmitting by remember(issue.id) { mutableStateOf(false) }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().testTag("helper_workshop_card"),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Build,
                                    contentDescription = "Helper Workshop",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "COMMUNITY HELPER WORKSHOP",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }

                            if (!helperAccepted) {
                                Text(
                                    text = "This issue is flagged as suitable for a localized community repair task. You can accept this task and repair it to earn badges and reputation points!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = { helperAccepted = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                    modifier = Modifier.fillMaxWidth().testTag("accept_repair_task_btn")
                                ) {
                                    Icon(Icons.Filled.Handshake, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Accept Repair Task")
                                }
                            } else {
                                // Accepted Repair Steps
                                when (helperStep) {
                                    1 -> {
                                        Text(
                                            text = "Step 1: Document the 'Before' state of the hazard. Take a photo to establish the baseline severity.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        if (beforeBitmap != null) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(120.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color.DarkGray)
                                            ) {
                                                AsyncImage(
                                                    model = beforeBitmap,
                                                    contentDescription = "Before Photo",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Button(
                                                onClick = { helperStep = 2 },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                                modifier = Modifier.fillMaxWidth().testTag("step1_next_btn")
                                            ) {
                                                Text("Proceed to Repair Work")
                                            }
                                        } else {
                                            Button(
                                                onClick = {
                                                    // Programmatically draw a simulated pothole on canvas
                                                    val bitmap = android.graphics.Bitmap.createBitmap(300, 300, android.graphics.Bitmap.Config.ARGB_8888)
                                                    val canvas = android.graphics.Canvas(bitmap)
                                                    val paint = android.graphics.Paint()
                                                    paint.color = android.graphics.Color.GRAY
                                                    canvas.drawRect(0f, 0f, 300f, 300f, paint)
                                                    paint.color = android.graphics.Color.BLACK
                                                    canvas.drawCircle(150f, 150f, 60f, paint) // deep hole
                                                    beforeBitmap = bitmap
                                                },
                                                modifier = Modifier.fillMaxWidth().testTag("capture_before_btn")
                                            ) {
                                                Icon(Icons.Filled.PhotoCamera, null)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Capture Before Photo")
                                            }
                                        }
                                    }
                                    2 -> {
                                        Text(
                                            text = "Step 2: Perform repair work on-site. When pavement is level and all hazards are safely addressed, mark work as done.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Button(
                                            onClick = { helperStep = 3 },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                            modifier = Modifier.fillMaxWidth().testTag("work_completed_btn")
                                        ) {
                                            Icon(Icons.Filled.Hardware, null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("I Have Finished Repair Work!")
                                        }
                                    }
                                    3 -> {
                                        Text(
                                            text = "Step 3: Document the 'After' state of the hazard. Take a photo showing the fully repaired area.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        if (afterBitmap != null) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(120.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color.DarkGray)
                                            ) {
                                                AsyncImage(
                                                    model = afterBitmap,
                                                    contentDescription = "After Photo",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Button(
                                                onClick = { helperStep = 4 },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                                modifier = Modifier.fillMaxWidth().testTag("step3_next_btn")
                                            ) {
                                                Text("Proceed to AI Audit")
                                            }
                                        } else {
                                            Button(
                                                onClick = {
                                                    // Programmatically draw a repaired road patch on canvas
                                                    val bitmap = android.graphics.Bitmap.createBitmap(300, 300, android.graphics.Bitmap.Config.ARGB_8888)
                                                    val canvas = android.graphics.Canvas(bitmap)
                                                    val paint = android.graphics.Paint()
                                                    paint.color = android.graphics.Color.GRAY
                                                    canvas.drawRect(0f, 0f, 300f, 300f, paint)
                                                    paint.color = android.graphics.Color.DKGRAY
                                                    canvas.drawCircle(150f, 150f, 65f, paint) // patched hole
                                                    afterBitmap = bitmap
                                                },
                                                modifier = Modifier.fillMaxWidth().testTag("capture_after_btn")
                                            ) {
                                                Icon(Icons.Filled.PhotoCamera, null)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Capture After Photo")
                                            }
                                        }
                                    }
                                    4 -> {
                                        Text(
                                            text = "Step 4: Describe your work and submit it to the Gemini AI Safety Verification Engine for final analysis.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        OutlinedTextField(
                                            value = repairNotes,
                                            onValueChange = { repairNotes = it },
                                            label = { Text("What repairs did you perform?") },
                                            placeholder = { Text("E.g. Filled pothole with 2 bags of quick-set cold mix...") },
                                            modifier = Modifier.fillMaxWidth().testTag("helper_notes_input")
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        if (isSubmitting) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text("Gemini AI analyzing images...", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            Button(
                                                onClick = {
                                                    if (beforeBitmap != null && afterBitmap != null && repairNotes.isNotBlank()) {
                                                        isSubmitting = true
                                                        onHelperResolve(
                                                            beforeBitmap!!,
                                                            afterBitmap!!,
                                                            "https://images.unsplash.com/photo-1515162305285-0293e4767cc2?w=500", // simulated before url
                                                            "https://images.unsplash.com/photo-1532372320572-cda25653a26d?w=500", // simulated after url
                                                            repairNotes
                                                        )
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                                enabled = repairNotes.isNotBlank(),
                                                modifier = Modifier.fillMaxWidth().testTag("submit_ai_verification_btn")
                                            ) {
                                                Icon(Icons.Filled.AutoAwesome, null)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Run Gemini AI Verification")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Community Verification Panel
                val needed = maxOf(0, issue.verificationThreshold - issue.verificationsCount)
                val progressText = if (needed > 0) "Need $needed more verifications" else "Community Verified!"
                val confidenceVal = issue.confidenceScore ?: "$trustScore%"

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.SafetyCheck,
                                contentDescription = "Security Check",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Community Verification Diagnostics",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("VERIFICATIONS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${issue.verificationsCount} verified",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("CONFIDENCE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = confidenceVal,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF388E3C)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("PROGRESS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = progressText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        // Linear progress bar towards threshold
                        val pct = if (issue.verificationThreshold > 0) (issue.verificationsCount.toFloat() / issue.verificationThreshold).coerceIn(0f, 1f) else 1f
                        LinearProgressIndicator(
                            progress = pct,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                        )
                    }
                }

                if (votes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Recent Community Reports & Evidence",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Display up to 3 recent verification votes/evidence
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        votes.sortedByDescending { it.timestamp }.take(3).forEach { vote ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                val voteIcon = when (vote.verificationType) {
                                    "NOT_PRESENT" -> Icons.Filled.Close
                                    "EVIDENCE" -> Icons.Filled.CameraEnhance
                                    else -> Icons.Filled.Check
                                }
                                val voteColor = when (vote.verificationType) {
                                    "NOT_PRESENT" -> Color.Red
                                    "EVIDENCE" -> MaterialTheme.colorScheme.tertiary
                                    else -> Color(0xFF388E3C)
                                }

                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(voteColor.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = voteIcon,
                                        contentDescription = null,
                                        tint = voteColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = vote.verifierName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black
                                        )
                                        Text(
                                            text = formatTime(vote.timestamp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = vote.comment ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    if (!vote.evidenceImageUrl.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(80.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(vote.evidenceImageUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = "Uploaded Evidence Picture",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons inspired by Google Maps quick interactions row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Verify button
                Button(
                    onClick = onVerifyClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("action_verify"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.SafetyCheck, "Verify", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Verify", style = MaterialTheme.typography.labelLarge)
                }

                // Add Evidence button
                OutlinedButton(
                    onClick = onAddEvidenceClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("action_add_evidence"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.CameraEnhance, "Add Evidence", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Evidence", style = MaterialTheme.typography.labelLarge)
                }

                // Share button
                OutlinedIconButton(
                    onClick = onShareClick,
                    modifier = Modifier.testTag("action_share"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Share, "Share details")
                }
            }
        }
    }
}

// ==========================================
// 3. UTILITY COMPONENT CORRESPONDENCES
// ==========================================
private fun getCategoryIcon(category: IssueCategory): androidx.compose.ui.graphics.vector.ImageVector {
    return when (category) {
        IssueCategory.ROADS -> Icons.Filled.AddRoad
        IssueCategory.LIGHTING -> Icons.Filled.Lightbulb
        IssueCategory.SANITATION -> Icons.Filled.Delete
        IssueCategory.WATER -> Icons.Filled.WaterDrop
        IssueCategory.POWER -> Icons.Filled.FlashOn
        IssueCategory.PARKS -> Icons.Filled.Forest
        IssueCategory.OTHER -> Icons.Filled.Help
        IssueCategory.POTHOLE -> Icons.Filled.Warning
        IssueCategory.GARBAGE -> Icons.Filled.DeleteOutline
        IssueCategory.WATER_LEAKAGE -> Icons.Filled.Water
        IssueCategory.BROKEN_STREETLIGHT -> Icons.Filled.LightbulbCircle
        IssueCategory.ROAD_DAMAGE -> Icons.Filled.Construction
    }
}
