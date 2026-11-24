package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.core.UiState
import com.example.data.repository.ReportStorageHelper
import com.example.data.repository.TelemedicineAndAppointmentsRepository
import com.example.data.repository.UserAndCareRepository
import com.example.data.repository.authStateFlow
import com.example.notifications.MedicineReminderScheduler
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.AppointmentsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DoctorDetailScreen
import com.example.ui.screens.DoctorsScreen
import com.example.ui.screens.HospitalsMapScreen
import com.example.ui.screens.MedicinesScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ProviderAvailabilityScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.VideoCallScreen
import com.example.ui.viewmodel.AiAssistantViewModel
import com.example.ui.viewmodel.CareHubViewModel
import com.example.ui.viewmodel.DoctorsAndAppointmentsViewModel
import com.example.ui.viewmodel.HospitalsMapViewModel
import com.example.ui.viewmodel.TelemedicineCallViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.reflect.KClass

private data class NavDestinationItem<T : Any>(
    val route: T,
    val routeClass: KClass<T>,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val primaryBottomDestinations = listOf(
    NavDestinationItem(
        route = DashboardRoute,
        routeClass = DashboardRoute::class,
        labelRes = R.string.nav_dashboard,
        selectedIcon = Icons.Rounded.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_dashboard"
    ),
    NavDestinationItem(
        route = AppointmentsRoute,
        routeClass = AppointmentsRoute::class,
        labelRes = R.string.nav_appointments,
        selectedIcon = Icons.Rounded.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth,
        testTag = "nav_appointments"
    ),
    NavDestinationItem(
        route = MedicinesRoute,
        routeClass = MedicinesRoute::class,
        labelRes = R.string.nav_medicines,
        selectedIcon = Icons.Rounded.Medication,
        unselectedIcon = Icons.Outlined.Medication,
        testTag = "nav_medicines"
    ),
    NavDestinationItem(
        route = AiAssistantRoute,
        routeClass = AiAssistantRoute::class,
        labelRes = R.string.nav_ai_assistant,
        selectedIcon = Icons.Rounded.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        testTag = "nav_ai_assistant"
    ),
    NavDestinationItem(
        route = DoctorsRoute,
        routeClass = DoctorsRoute::class,
        labelRes = R.string.nav_doctors,
        selectedIcon = Icons.Rounded.MedicalServices,
        unselectedIcon = Icons.Outlined.MedicalServices,
        testTag = "nav_doctors"
    )
)

private val secondaryCareDestinations = listOf(
    NavDestinationItem(
        route = HospitalsMapRoute,
        routeClass = HospitalsMapRoute::class,
        labelRes = R.string.nav_hospitals,
        selectedIcon = Icons.Rounded.LocalHospital,
        unselectedIcon = Icons.Outlined.LocalHospital,
        testTag = "nav_hospitals"
    ),
    NavDestinationItem(
        route = ReportsRoute,
        routeClass = ReportsRoute::class,
        labelRes = R.string.nav_reports,
        selectedIcon = Icons.Rounded.Description,
        unselectedIcon = Icons.Outlined.Description,
        testTag = "nav_reports"
    ),
    NavDestinationItem(
        route = ProfileRoute,
        routeClass = ProfileRoute::class,
        labelRes = R.string.nav_profile,
        selectedIcon = Icons.Rounded.AccountCircle,
        unselectedIcon = Icons.Outlined.AccountCircle,
        testTag = "nav_profile"
    )
)

@Composable
fun AppRootContent() {
    val currentUser by Firebase.auth
        .authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedCareApp(currentUser = user)
        }
    }
}

@Composable
private fun AuthenticatedCareApp(currentUser: FirebaseUser) {
    val appContext = LocalContext.current.applicationContext
    val databaseId = remember(appContext) { appContext.getString(R.string.firestore_database_id) }
    val reportStorageHelper = remember(appContext) { ReportStorageHelper(appContext) }

    val careHubViewModel: CareHubViewModel = viewModel(
        key = "carehub_${currentUser.uid}",
        factory = viewModelFactory {
            initializer {
                val db = FirebaseFirestore.getInstance(databaseId)
                CareHubViewModel(
                    repository = UserAndCareRepository(db = db),
                    reportStorageHelper = reportStorageHelper,
                    currentUserId = currentUser.uid
                )
            }
        }
    )

    val docsAndApptsViewModel: DoctorsAndAppointmentsViewModel = viewModel(
        key = "docs_${currentUser.uid}",
        factory = viewModelFactory {
            initializer {
                val db = FirebaseFirestore.getInstance(databaseId)
                DoctorsAndAppointmentsViewModel(
                    repository = TelemedicineAndAppointmentsRepository(db = db),
                    currentUserId = currentUser.uid
                )
            }
        }
    )

    val aiAssistantViewModel: AiAssistantViewModel = viewModel(
        key = "ai_${currentUser.uid}",
        factory = viewModelFactory {
            initializer { AiAssistantViewModel() }
        }
    )

    val hospitalsMapViewModel: HospitalsMapViewModel = viewModel(
        key = "map_${currentUser.uid}",
        factory = viewModelFactory {
            initializer { HospitalsMapViewModel() }
        }
    )

    LaunchedEffect(currentUser.uid) {
        careHubViewModel.ensureProfile(currentUser)
    }

    val profileState by careHubViewModel.profileState.collectAsStateWithLifecycle()
    val medicinesState by careHubViewModel.medicinesState.collectAsStateWithLifecycle()
    val notificationsEnabled = (profileState as? UiState.Success)?.data?.notificationsEnabled ?: true
    val patientName = (profileState as? UiState.Success)?.data?.displayName?.takeIf { it.isNotBlank() }
        ?: currentUser.displayName?.takeIf { it.isNotBlank() }
        ?: "Patient"

    LaunchedEffect(medicinesState, notificationsEnabled) {
        val medicines = (medicinesState as? UiState.Success)?.data
        if (medicines != null) {
            MedicineReminderScheduler.syncReminders(
                context = appContext,
                medicines = medicines,
                globalNotificationsEnabled = notificationsEnabled
            )
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val isFullScreenRoute = currentDestination?.hasRoute(DoctorDetailRoute::class) == true ||
        currentDestination?.hasRoute(ProviderAvailabilityRoute::class) == true ||
        currentDestination?.hasRoute(VideoCallRoute::class) == true

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (isWide && !isFullScreenRoute) {
                CareNavigationRail(
                    currentDestination = currentDestination,
                    onNavigate = ::navigateTopLevel
                )
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                contentWindowInsets = if (isFullScreenRoute) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
                topBar = {
                    if (!isFullScreenRoute && !isWide) {
                        CareMobileTopBar(
                            currentDestination = currentDestination,
                            onNavigate = ::navigateTopLevel
                        )
                    }
                },
                bottomBar = {
                    if (!isFullScreenRoute && !isWide) {
                        CareMobileBottomBar(
                            currentDestination = currentDestination,
                            onNavigate = ::navigateTopLevel
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = DashboardRoute
                    ) {
                        composable<DashboardRoute> {
                            DashboardScreen(
                                careHubViewModel = careHubViewModel,
                                docsAndApptsViewModel = docsAndApptsViewModel,
                                onNavigateAppointments = { navigateTopLevel(AppointmentsRoute) },
                                onNavigateMedicines = { navigateTopLevel(MedicinesRoute) },
                                onNavigateAiAssistant = { navigateTopLevel(AiAssistantRoute) },
                                onNavigateDoctors = { navigateTopLevel(DoctorsRoute) },
                                onNavigateHospitals = { navigateTopLevel(HospitalsMapRoute) },
                                onNavigateReports = { navigateTopLevel(ReportsRoute) },
                                onNavigateProfile = { navigateTopLevel(ProfileRoute) },
                                onJoinVideoCall = { roomId ->
                                    navController.navigate(VideoCallRoute(roomId = roomId))
                                }
                            )
                        }

                        composable<AppointmentsRoute> {
                            AppointmentsScreen(
                                patientName = patientName,
                                viewModel = docsAndApptsViewModel,
                                onJoinVideoCall = { roomId ->
                                    navController.navigate(VideoCallRoute(roomId = roomId))
                                }
                            )
                        }

                        composable<MedicinesRoute> {
                            MedicinesScreen(viewModel = careHubViewModel)
                        }

                        composable<AiAssistantRoute> {
                            AiAssistantScreen(
                                viewModel = aiAssistantViewModel,
                                onOpenHospitalsMap = { navigateTopLevel(HospitalsMapRoute) }
                            )
                        }

                        composable<DoctorsRoute> {
                            DoctorsScreen(
                                viewModel = docsAndApptsViewModel,
                                onOpenDoctorDetail = { doctorId ->
                                    navController.navigate(DoctorDetailRoute(doctorId = doctorId))
                                },
                                onOpenProviderAvailability = {
                                    navController.navigate(ProviderAvailabilityRoute)
                                }
                            )
                        }

                        composable<DoctorDetailRoute> { entry ->
                            val args = entry.toRoute<DoctorDetailRoute>()
                            DoctorDetailScreen(
                                doctorId = args.doctorId,
                                patientName = patientName,
                                viewModel = docsAndApptsViewModel,
                                onBack = { navController.popBackStack() },
                                onAppointmentBooked = {
                                    navigateTopLevel(AppointmentsRoute)
                                }
                            )
                        }

                        composable<ProviderAvailabilityRoute> {
                            ProviderAvailabilityScreen(
                                currentUserId = currentUser.uid,
                                defaultDoctorName = patientName,
                                viewModel = docsAndApptsViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<HospitalsMapRoute> {
                            HospitalsMapScreen(viewModel = hospitalsMapViewModel)
                        }

                        composable<ReportsRoute> {
                            ReportsScreen(
                                viewModel = careHubViewModel,
                                reportStorageHelper = reportStorageHelper
                            )
                        }

                        composable<ProfileRoute> {
                            ProfileScreen(
                                viewModel = careHubViewModel,
                                onOpenProviderPortal = {
                                    navController.navigate(ProviderAvailabilityRoute)
                                },
                                onSignedOut = {}
                            )
                        }

                        composable<VideoCallRoute> { entry ->
                            val args = entry.toRoute<VideoCallRoute>()
                            val callViewModel: TelemedicineCallViewModel = viewModel(
                                key = "call_${currentUser.uid}_${args.roomId}",
                                factory = viewModelFactory {
                                    initializer {
                                        val db = FirebaseFirestore.getInstance(databaseId)
                                        TelemedicineCallViewModel(
                                            repository = TelemedicineAndAppointmentsRepository(db = db),
                                            roomId = args.roomId,
                                            currentUserId = currentUser.uid
                                        )
                                    }
                                }
                            )
                            VideoCallScreen(
                                currentUserId = currentUser.uid,
                                currentUserName = patientName,
                                viewModel = callViewModel,
                                onCallExited = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CareMobileTopBar(
    currentDestination: NavDestination?,
    onNavigate: (Any) -> Unit
) {
    val title = when {
        currentDestination?.hasRoute(AppointmentsRoute::class) == true -> "Appointments"
        currentDestination?.hasRoute(MedicinesRoute::class) == true -> "Medicine Reminders"
        currentDestination?.hasRoute(AiAssistantRoute::class) == true -> "AI Health Guide"
        currentDestination?.hasRoute(DoctorsRoute::class) == true -> "Find Doctors"
        currentDestination?.hasRoute(HospitalsMapRoute::class) == true -> "Nearby Hospitals"
        currentDestination?.hasRoute(ReportsRoute::class) == true -> "Medical Reports"
        currentDestination?.hasRoute(ProfileRoute::class) == true -> "Profile & Settings"
        else -> stringResource(R.string.app_name)
    }

    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        actions = {
            secondaryCareDestinations.forEach { item ->
                val selected = currentDestination?.hasRoute(item.routeClass) == true
                IconButton(
                    onClick = { onNavigate(item.route) },
                    modifier = Modifier.testTag("topbar_${item.testTag}")
                ) {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = stringResource(item.labelRes),
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

@Composable
private fun CareMobileBottomBar(
    currentDestination: NavDestination?,
    onNavigate: (Any) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        primaryBottomDestinations.forEach { item ->
            val selected = currentDestination?.hasRoute(item.routeClass) == true
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = stringResource(item.labelRes)
                    )
                },
                label = {
                    Text(
                        text = stringResource(item.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.testTag(item.testTag)
            )
        }
    }
}

@Composable
private fun CareNavigationRail(
    currentDestination: NavDestination?,
    onNavigate: (Any) -> Unit
) {
    val allDestinations = primaryBottomDestinations + secondaryCareDestinations
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            allDestinations.forEach { item ->
                val selected = currentDestination?.hasRoute(item.routeClass) == true
                NavigationRailItem(
                    selected = selected,
                    onClick = { onNavigate(item.route) },
                    icon = {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = stringResource(item.labelRes)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(item.labelRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.testTag("rail_${item.testTag}")
                )
            }
        }
    }
}
