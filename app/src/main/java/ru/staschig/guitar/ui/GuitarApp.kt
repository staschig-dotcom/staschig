package ru.staschig.guitar.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.ui.screens.LessonScreen
import ru.staschig.guitar.ui.screens.LessonsScreen
import ru.staschig.guitar.ui.screens.MetronomeScreen
import ru.staschig.guitar.ui.screens.ProgressScreen
import ru.staschig.guitar.ui.screens.SettingsScreen
import ru.staschig.guitar.ui.screens.TabBrowserScreen
import ru.staschig.guitar.ui.screens.TabsScreen
import ru.staschig.guitar.ui.screens.TunerScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("lessons", "Уроки", Icons.Filled.School),
    Tab("tabs", "Табы", Icons.Filled.LibraryMusic),
    Tab("tuner", "Тюнер", Icons.Filled.GraphicEq),
    Tab("metronome", "Метроном", Icons.Filled.Timer),
    Tab("progress", "Прогресс", Icons.Filled.QueryStats),
)

fun NavHostController.openBrowser(url: String, lessonId: String? = null) {
    navigate("browser?url=${Uri.encode(url)}&lesson=${Uri.encode(lessonId ?: "")}")
}

@Composable
fun GuitarApp(store: AppStore) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "lessons", modifier = Modifier.padding(padding)) {
            composable("lessons") {
                LessonsScreen(
                    store = store,
                    onOpenLesson = { nav.navigate("lesson/$it") },
                    onOpenSettings = { nav.navigate("settings") },
                )
            }
            composable(
                "lesson/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                LessonScreen(
                    store = store,
                    lessonId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenUrl = { url, lessonId -> nav.openBrowser(url, lessonId) },
                )
            }
            composable("tabs") {
                TabsScreen(store = store, onOpenUrl = { nav.openBrowser(it) })
            }
            composable(
                "browser?url={url}&lesson={lesson}",
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType; defaultValue = "" },
                    navArgument("lesson") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                TabBrowserScreen(
                    store = store,
                    startUrl = entry.arguments?.getString("url").orEmpty(),
                    lessonId = entry.arguments?.getString("lesson")?.ifEmpty { null },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("tuner") { TunerScreen(store) }
            composable("metronome") { MetronomeScreen() }
            composable("progress") { ProgressScreen(store) }
            composable("settings") { SettingsScreen(store, onBack = { nav.popBackStack() }) }
        }
    }
}
