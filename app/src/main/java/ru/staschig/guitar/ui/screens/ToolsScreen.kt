package ru.staschig.guitar.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class ToolItem(val route: String, val icon: ImageVector, val title: String, val subtitle: String)

private val tools = listOf(
    ToolItem("tuner", Icons.Filled.GraphicEq, "Тюнер", "Настроить гитару"),
    ToolItem("metronome", Icons.Filled.Timer, "Метроном", "Темп, разгон, tap"),
    ToolItem("playlist", Icons.Filled.SportsEsports, "Играть с проверкой", "Ноты на ленте, звёзды"),
    ToolItem("tabs", Icons.Filled.LibraryMusic, "Табы и песни", "Найти, сохранить, открыть"),
    ToolItem("chords", Icons.Filled.SwapHoriz, "Смены аккордов", "Минута на пару аккордов"),
    ToolItem("rhythm", Icons.Filled.TouchApp, "Ритм-тест", "Спешите или тянете?"),
    ToolItem("ear", Icons.Filled.Hearing, "Слух", "Узнать аккорд на слух"),
)

/** Все инструменты отдельно от курса — плитками. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Инструменты") })
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tools, key = { it.route }) { t ->
                Card(Modifier.fillMaxWidth().height(132.dp).clickable { onOpen(t.route) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(t.icon, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(t.title, style = MaterialTheme.typography.titleMedium)
                        Text(t.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
