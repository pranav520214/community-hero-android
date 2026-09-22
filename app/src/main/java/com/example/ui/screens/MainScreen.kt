package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.CivicViewModel
import com.example.data.model.UserRole

sealed class Screen(val route: String, val title: String, val activeIcon: ImageVector, val inactiveIcon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    object Verify : Screen("verify", "Verify", Icons.Filled.SafetyCheck, Icons.Outlined.SafetyCheck)
    object Report : Screen("report", "Report", Icons.Filled.AddAlert, Icons.Outlined.AddAlert)
    object Community : Screen("community", "Community", Icons.Filled.Forum, Icons.Outlined.Forum)
    object Profile : Screen("profile", "Profile", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle)
    object WorkerDashboard : Screen("worker_dashboard", "Tasks", Icons.Filled.Build, Icons.Outlined.Build)
    object OfficerDashboard : Screen("officer_dashboard", "Executive", Icons.Filled.BarChart, Icons.Outlined.BarChart)
}

@Composable
fun MainScreen(viewModel: CivicViewModel) {
    val authState by viewModel.authUiState.collectAsState()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    AnimatedContent(
        targetState = authState,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "auth_gate_transition"
    ) { state ->
        when (state) {
            is AuthUiState.Authenticated -> {
                val currentUser by viewModel.currentUser.collectAsState()

                LaunchedEffect(currentUser) {
                    if (currentUser?.role != UserRole.WORKER && currentUser?.role != UserRole.OFFICER && currentUser?.role != UserRole.ADMINISTRATOR && currentScreen == Screen.WorkerDashboard) {
                        currentScreen = Screen.Home
                    }
                    if (currentUser?.role != UserRole.OFFICER && currentUser?.role != UserRole.ADMINISTRATOR && currentScreen == Screen.OfficerDashboard) {
                        currentScreen = Screen.Home
                    }
                }

                // Main Authenticated Scaffold holding navigation state
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier
                                .navigationBarsPadding() // Safely offsets system gesture bars
                                .testTag("bottom_nav_bar")
                        ) {
                            val items = remember(currentUser) {
                                when (currentUser?.role) {
                                    UserRole.WORKER -> {
                                        listOf(
                                            Screen.Home,
                                            Screen.WorkerDashboard,
                                            Screen.Verify,
                                            Screen.Report,
                                            Screen.Community,
                                            Screen.Profile
                                        )
                                    }
                                    UserRole.OFFICER, UserRole.ADMINISTRATOR -> {
                                        listOf(
                                            Screen.Home,
                                            Screen.OfficerDashboard,
                                            Screen.WorkerDashboard,
                                            Screen.Verify,
                                            Screen.Report,
                                            Screen.Community,
                                            Screen.Profile
                                        )
                                    }
                                    else -> {
                                        listOf(
                                            Screen.Home,
                                            Screen.Verify,
                                            Screen.Report,
                                            Screen.Community,
                                            Screen.Profile
                                        )
                                    }
                                }
                            }

                            items.forEach { screen ->
                                val isSelected = currentScreen.route == screen.route
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { currentScreen = screen },
                                    label = { Text(screen.title) },
                                    icon = {
                                        Icon(
                                            imageVector = if (isSelected) screen.activeIcon else screen.inactiveIcon,
                                            contentDescription = screen.title
                                        )
                                    },
                                    modifier = Modifier.testTag("nav_item_${screen.route}")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Safe transitions between bottom tabs
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                slideInHorizontally { width -> width / 3 } + fadeIn() togetherWith
                                        slideOutHorizontally { width -> -width / 3 } + fadeOut()
                            },
                            label = "tab_nav_transition"
                        ) { targetScreen ->
                            when (targetScreen) {
                                is Screen.Home -> HomeScreen(
                                    viewModel = viewModel,
                                    onNavigateToReport = { currentScreen = Screen.Report }
                                )
                                is Screen.Verify -> VerifyScreen(viewModel = viewModel)
                                is Screen.Report -> ReportScreen(
                                    viewModel = viewModel,
                                    onReportSuccess = { currentScreen = Screen.Home }
                                )
                                is Screen.Community -> CommunityScreen(viewModel = viewModel)
                                is Screen.Profile -> ProfileScreen(viewModel = viewModel)
                                is Screen.WorkerDashboard -> WorkerDashboardScreen(viewModel = viewModel)
                                is Screen.OfficerDashboard -> OfficerDashboardScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
            else -> {
                // If unauthenticated or loading or error, display elegant onboarding login screen
                AuthScreen(viewModel = viewModel)
            }
        }
    }
}
