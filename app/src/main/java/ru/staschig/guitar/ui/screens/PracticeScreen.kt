package ru.staschig.guitar.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Практика") })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Tool(Icons.Filled.SportsEsports, "Играть с проверкой", "Ноты едут по ленте, приложение слушает и ставит звёзды — как в Yousician") { onOpen("playlist") }
            Tool(Icons.Filled.Timer, "Метроном", "Точный метроном, tap tempo, тренажёр скорости") { onOpen("metronome") }
            Tool(Icons.Filled.TouchApp, "Ритм-тест", "Играйте под щелчки — приложение покажет, спешите вы или тянете") { onOpen("rhythm") }
            Tool(Icons.Filled.SwapHoriz, "Смены аккордов", "«Одноминутные смены» с автоподсчётом по микрофону и схемы аккордов") { onOpen("chords") }
            Tool(Icons.Filled.Hearing, "Слух: аккорды", "Мажор/минор, открытые аккорды и септаккорды на слух") { onOpen("ear") }
        }
    }
}

@Composable
private fun Tool(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
