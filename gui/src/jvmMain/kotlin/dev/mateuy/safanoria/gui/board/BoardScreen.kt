package dev.mateuy.safanoria.gui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mateuy.safanoria.core.Priority
import dev.mateuy.safanoria.core.TicketType
import dev.mateuy.safanoria.core.text
import dev.mateuy.safanoria.gui.theme.color
import dev.mateuy.safanoria.gui.theme.label

@Composable
fun BoardScreen(viewModel: BoardViewModel, onOpenTicket: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BoardContent(state, onRefresh = viewModel::refresh, onOpenTicket = onOpenTicket)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardContent(state: BoardViewState, onRefresh: () -> Unit, onOpenTicket: (String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Board · ${state.ticketCount} tickets") },
                actions = {
                    if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    TextButton(onClick = onRefresh, enabled = !state.loading) { Text("Refresh") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            state.error?.let {
                Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            }
            Row(
                Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.columns.forEach { column -> BoardColumnView(column, onOpenTicket) }
            }
        }
    }
}

@Composable
private fun BoardColumnView(column: BoardColumn, onOpenTicket: (String) -> Unit) {
    Column(
        Modifier.width(300.dp).fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(column.status?.color ?: MaterialTheme.colorScheme.error))
            Text(column.status?.label ?: "Unreadable status", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("${column.cards.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(column.cards, key = { it.id }) { card -> TicketCardView(card, onClick = { onOpenTicket(card.id) }) }
        }
    }
}

@Composable
private fun TicketCardView(card: TicketCard, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                card.id,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(card.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            card.progress?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { if (it.total == 0) 0f else it.done.toFloat() / it.total },
                        modifier = Modifier.weight(1f),
                        drawStopIndicator = {},
                    )
                    Text("${it.done}/${it.total}", style = MaterialTheme.typography.labelSmall)
                }
            }
            val facts = listOfNotNull(
                card.type?.takeIf { it != TicketType.FEATURE }?.text,
                card.priority?.takeIf { it != Priority.MEDIUM }?.text,
                card.size?.text,
                card.parentId?.let { "↑ $it" },
                card.onlyOnBranch?.let { "only on $it" },
            )
            if (facts.isNotEmpty()) {
                Text(
                    facts.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = card.priority?.takeIf { it >= Priority.HIGH }?.color ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (card.openBlockers.isNotEmpty()) {
                Text(
                    "blocked by " + card.openBlockers.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
