package ru.staschig.guitar.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.data.TabKind
import ru.staschig.guitar.lessons.Curriculum
import ru.staschig.guitar.play.PlayResult

/** Что можно сыграть с проверкой: встроенные пьесы, упражнения и свои Guitar Pro табы. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayListScreen(store: AppStore, onPlay: (id: String, title: String, query: String) -> Unit, onBack: () -> Unit) {
    val (level, inLevel, need) = PlayResult.level(store.xp)
    val myGp = store.library.docs.filter { it.kind == TabKind.GP }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Играть с проверкой") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
        )
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Уровень $level · ${store.xp} XP", fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(progress = { inLevel.toFloat() / need }, modifier = Modifier.fillMaxWidth())
                        Text(
                            "Ноты едут к линии — играйте их, приложение слушает и засчитывает каждую. " +
                                "Звёзды: ★ 60%, ★★ 80%, ★★★ 95% нот в полном темпе всей пьесы.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item { SectionTitle("Пьесы") }
            items(Curriculum.builtInPieces, key = { "p" + it.asset }) { p ->
                PlayEntry(p.title, "${p.artist} · ${p.level.title}", store.playBest[p.asset]?.stars) {
                    onPlay(p.asset, p.title, pieceQuery(p.asset))
                }
            }
            item { SectionTitle("Упражнения") }
            items(Curriculum.builtInExercises, key = { "e" + it.asset }) { p ->
                PlayEntry(p.title, "${p.artist} · ${p.level.title}", store.playBest[p.asset]?.stars) {
                    onPlay(p.asset, p.title, pieceQuery(p.asset))
                }
            }
            item { SectionTitle("Мои табы Guitar Pro") }
            if (myGp.isEmpty()) {
                item {
                    Text(
                        "Сохраните Guitar Pro таб песни с guitarmaestro.ru (вкладка «Табы») — и его можно будет играть здесь с проверкой.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(myGp, key = { "d" + it.id }) { d ->
                PlayEntry(d.title, d.artist.ifBlank { "мой таб" }, store.playBest["doc_" + d.id]?.stars) {
                    onPlay("doc_" + d.id, d.title, docQuery(d))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun PlayEntry(title: String, subtitle: String, stars: Int?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (stars == null) "☆☆☆" else "★".repeat(stars) + "☆".repeat(3 - stars),
                color = if (stars == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
