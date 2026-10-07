package com.ritmo.treinos.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.ritmo.treinos.BuildConfig
import kotlinx.coroutines.launch

@Composable fun RitmoApp(vm: RitmoViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    RitmoTheme(settings.theme) {
        val data by vm.data.collectAsStateWithLifecycle()
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route ?: "home"
        val snackbar = remember { SnackbarHostState() }
        val context = LocalContext.current
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.installUpdate(afterPermission = true) }
        val installerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
        val view = androidx.compose.ui.platform.LocalView.current
        val isDark = settings.theme == "dark" || (settings.theme == "system" && androidx.compose.foundation.isSystemInDarkTheme())
        SideEffect { (context as? android.app.Activity)?.window?.let { window ->
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        } }
        LaunchedEffect(vm) { vm.events.collect { event -> when(event) {
            is UiEvent.Message -> launch { snackbar.showSnackbar(event.text) }
            is UiEvent.Install -> runCatching {
                if (event.needsPermission) permissionLauncher.launch(event.intent) else installerLauncher.launch(event.intent)
            }.onFailure { vm.installLaunchFailed() }
            is UiEvent.Navigate -> {
                val editingExercise = nav.currentDestination?.route?.startsWith("edit-exercise") == true
                val editingWorkout = nav.currentDestination?.route?.startsWith("edit-workout") == true
                if (editingExercise || editingWorkout) nav.popBackStack()
                val returnToWorkout = editingExercise && nav.currentDestination?.route?.startsWith("edit-workout") == true && event.route.startsWith("exercise/")
                if (event.home) nav.navigate("home") { popUpTo(nav.graph.id) { inclusive = true }; launchSingleTop = true }
                if (!returnToWorkout && !(event.home && event.route == "home")) nav.navigate(event.route) { launchSingleTop = true }
            }
        } } }
        val topRoutes = listOf("home", "workouts", "exercises", "history", "progress")
        val labels = listOf("Início", "Treinos", "Exercícios", "Histórico", "Progresso")
        val icons = listOf(Icons.Default.Home, Icons.Default.FitnessCenter, Icons.AutoMirrored.Filled.FormatListBulleted, Icons.Default.History, Icons.AutoMirrored.Filled.ShowChart)
        val info by vm.update.collectAsStateWithLifecycle()
        val dismissed by vm.dismissedCode.collectAsStateWithLifecycle()
        val mandatory = info?.mandatory(BuildConfig.VERSION_CODE) == true
        val showUpdate = info?.available(BuildConfig.VERSION_CODE) == true && (mandatory || dismissed != info?.versionCode)
        if (mandatory) BackHandler { }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { if (route in topRoutes) NavigationBar {
                topRoutes.forEachIndexed { i, destination -> NavigationBarItem(selected = route == destination, enabled = data.loaded, onClick = { nav.navigate(destination) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } }, icon = { Icon(icons[i], labels[i]) }, label = { Text(labels[i], style = MaterialTheme.typography.labelSmall) }) }
            } }
        ) { padding ->
            if (!data.loaded) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
            else NavHost(nav, "home", Modifier.fillMaxSize().padding(padding)) {
                composable("home") { HomeScreen(data, vm, { nav.navigate(it) }) }
                composable("workouts") { WorkoutsScreen(data, vm, { nav.navigate(it) }) }
                composable("exercises") { ExercisesScreen(data, { nav.navigate(it) }, { vm.deleteExercise(it) }) }
                composable("history") { HistoryScreen(data, { nav.navigate(it) }) }
                composable("progress") { ProgressScreen(data, { nav.navigate(it) }) }
                composable("settings") { SettingsScreen(vm) { nav.popBackStack() } }
                composable("edit-exercise?exerciseId={exerciseId}", arguments = listOf(navArgument("exerciseId") { type = NavType.LongType; defaultValue = 0L })) { e -> ExerciseEditor(data, e.arguments?.getLong("exerciseId") ?: 0, vm) { nav.popBackStack() } }
                composable("edit-workout?workoutId={workoutId}", arguments = listOf(navArgument("workoutId") { type = NavType.LongType; defaultValue = 0L })) { e -> WorkoutEditor(data, e.arguments?.getLong("workoutId") ?: 0, vm, { nav.popBackStack() }, { nav.navigate(it) }) }
                composable("exercise/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e -> ExerciseScreen(data, e.arguments!!.getLong("id"), vm, { nav.popBackStack() }, { nav.navigate(it) }) }
                composable("session/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e -> SessionScreen(data, e.arguments!!.getLong("id"), vm, { nav.popBackStack() }, { nav.navigate(it) }) }
                composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e -> WorkoutHistoryDetail(data, e.arguments!!.getLong("id")) { if (!nav.popBackStack()) nav.navigate("home") } }
            }
        }
        if (showUpdate && info != null) {
            val update = info!!
            val download by vm.apkDownload.collectAsStateWithLifecycle()
            val error by vm.updateError.collectAsStateWithLifecycle()
            val installError by vm.installError.collectAsStateWithLifecycle()
            UpdateDialog(update, mandatory, download, error, installError,
                { vm.downloadUpdate() }, { vm.cancelDownload() }, { vm.installUpdate() },
                { vm.dismissedCode.value = update.versionCode }, { vm.checkUpdate() })
        }
    }
}

@Composable fun Page(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
