package ru.staschig.guitar.ui

import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import ru.staschig.guitar.lessons.Curriculum
import ru.staschig.guitar.ui.screens.ChordsScreen
import ru.staschig.guitar.ui.screens.CourseScreen
import ru.staschig.guitar.ui.screens.EarScreen
import ru.staschig.guitar.ui.screens.LessonScreen
import ru.staschig.guitar.ui.screens.MetronomeScreen
import ru.staschig.guitar.ui.screens.OnboardingScreen
import ru.staschig.guitar.ui.screens.PieceScreen
import ru.staschig.guitar.ui.screens.PlayListScreen
import ru.staschig.guitar.ui.screens.PlayScreen
import ru.staschig.guitar.ui.screens.ProgressScreen
import ru.staschig.guitar.ui.screens.RhythmScreen
import ru.staschig.guitar.ui.screens.SettingsScreen
import ru.staschig.guitar.ui.screens.TabBrowserScreen
import ru.staschig.guitar.ui.screens.TabDocScreen
import ru.staschig.guitar.ui.screens.TabEditScreen
import ru.staschig.guitar.ui.screens.TabsScreen
import ru.staschig.guitar.ui.screens.TodayScreen
import ru.staschig.guitar.ui.screens.ToolsScreen
import ru.staschig.guitar.ui.screens.TunerScreen
import ru.staschig.guitar.ui.screens.docQuery
import ru.staschig.guitar.ui.screens.isWide
import ru.staschig.guitar.ui.screens.pieceQuery

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("today", "Сегодня", Icons.Filled.Home),
    Tab("course", "Курс", Icons.Filled.School),
    Tab("tools", "Инструменты", Icons.Filled.Apps),
    Tab("progress", "Прогресс", Icons.Filled.QueryStats),
)

fun NavHostController.openPlay(id: String, title: String, query: String) {
    navigate("play?id=${Uri.encode(id)}&title=${Uri.encode(title)}&q=${Uri.encode(query)}")
}

fun NavHostController.openBrowser(url: String, lessonId: String? = null) {
    navigate("browser?url=${Uri.encode(url)}&lesson=${Uri.encode(lessonId ?: "")}")
}

@Composable
fun GuitarApp(store: AppStore) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    val wide = isWide()
    val showNav = tabs.any { it.route == route }
    fun go(tab: Tab) = nav.navigate(tab.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    // Телефон лёжа: вкладки — боковой панелью слева, чтобы не отнимать высоту.
    Row(Modifier.fillMaxSize()) {
        if (wide && showNav) {
            NavigationRail {
                Spacer(Modifier.weight(1f))
                tabs.forEach { tab ->
                    NavigationRailItem(
                        selected = route == tab.route,
                        onClick = { go(tab) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            bottomBar = {
                if (showNav && !wide) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = { go(tab) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            val back: () -> Unit = { nav.popBackStack() }
            NavHost(nav, startDestination = if (store.onboarded) "today" else "onboarding", modifier = Modifier.padding(padding)) {
                composable("onboarding") {
                    OnboardingScreen(store, onDone = {
                        nav.navigate("today") { popUpTo("onboarding") { inclusive = true } }
                    })
                }
                composable("today") {
                    TodayScreen(
                        store = store,
                        onStartLesson = { nav.navigate("lesson/$it") },
                        onOpen = { nav.navigate(it) },
                        onOpenSettings = { nav.navigate("settings") },
                    )
                }
                composable("course") { CourseScreen(store, onStartLesson = { nav.navigate("lesson/$it") }) }
                composable("tools") { ToolsScreen(onOpen = { nav.navigate(it) }) }
                composable(
                    "lesson/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    LessonScreen(
                        store = store,
                        lessonId = entry.arguments?.getString("id").orEmpty(),
                        onBack = { nav.popBackStack() },
                        onOpenUrl = { url, lessonId -> nav.openBrowser(url, lessonId) },
                        onOpenPiece = { nav.navigate("piece/$it") },
                        onOpenDoc = { nav.navigate("tabdoc/$it") },
                        onPlay = { id, title, q -> nav.openPlay(id, title, q) },
                    )
                }
                composable("tabs") {
                    TabsScreen(
                        store = store,
                        onBack = back,
                        onOpenUrl = { nav.openBrowser(it) },
                        onOpenDoc = { nav.navigate("tabdoc/$it") },
                        onOpenPiece = { nav.navigate("piece/$it") },
                        onNewText = { store.library.draft = null; nav.navigate("tabedit") },
                    )
                }
                composable("piece/{asset}") { entry ->
                    val asset = entry.arguments?.getString("asset").orEmpty()
                    PieceScreen(asset, onBack = { nav.popBackStack() }, onPlay = {
                        nav.openPlay(asset, Curriculum.pieceTitle(asset), pieceQuery(asset))
                    })
                }
                composable("tabdoc/{id}") { entry ->
                    TabDocScreen(
                        store = store,
                        id = entry.arguments?.getString("id").orEmpty(),
                        onBack = { nav.popBackStack() },
                        onEdit = { nav.navigate("tabedit?id=$it") },
                        onPlay = { doc -> nav.openPlay("doc_" + doc.id, doc.title, docQuery(doc)) },
                    )
                }
                composable(
                    "tabedit?id={id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { entry ->
                    TabEditScreen(store, entry.arguments?.getString("id")) { savedId ->
                        nav.popBackStack()
                        if (savedId != null && entry.arguments?.getString("id") == null) nav.navigate("tabdoc/$savedId")
                    }
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
                        onOpenDoc = { nav.navigate("tabdoc/$it") },
                        onEditDraft = { nav.navigate("tabedit") },
                    )
                }
                composable("tuner") { TunerScreen(store, onBack = back) }
                composable("playlist") {
                    PlayListScreen(store, onPlay = { id, title, q -> nav.openPlay(id, title, q) }, onBack = { nav.popBackStack() })
                }
                composable(
                    "play?id={id}&title={title}&q={q}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType; defaultValue = "" },
                        navArgument("title") { type = NavType.StringType; defaultValue = "" },
                        navArgument("q") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    val a = entry.arguments
                    PlayScreen(
                        store,
                        id = a?.getString("id").orEmpty(),
                        title = a?.getString("title").orEmpty(),
                        query = a?.getString("q").orEmpty(),
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("metronome") { MetronomeScreen(onBack = back) }
                composable("rhythm") { RhythmScreen(store, onBack = { nav.popBackStack() }) }
                composable("chords") { ChordsScreen(store, onBack = { nav.popBackStack() }) }
                composable("ear") { EarScreen(onBack = { nav.popBackStack() }) }
                composable("progress") { ProgressScreen(store, onOpenSettings = { nav.navigate("settings") }) }
                composable("settings") { SettingsScreen(store, onBack = { nav.popBackStack() }) }
            }
        }
    }
}
