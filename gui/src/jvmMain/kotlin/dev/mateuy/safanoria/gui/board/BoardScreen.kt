package dev.mateuy.safanoria.gui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mateuy.safanoria.core.Priority
import dev.mateuy.safanoria.core.Status
import dev.mateuy.safanoria.core.TicketType
import dev.mateuy.safanoria.core.text
import dev.mateuy.safanoria.gui.theme.color
import dev.mateuy.safanoria.gui.theme.label

@Composable
fun BoardScreen(viewModel: BoardViewModel, onOpenTicket: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BoardContent(state, onRefresh = viewModel::refresh, onToggleColumn = viewModel::toggleColumn, onOpenTicket = onOpenTicket)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardContent(
    state: BoardViewState,
    onRefresh: () -> Unit,
    onToggleColumn: (Status?) -> Unit,
    onOpenTicket: (String) -> Unit,
) {
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
                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Open columns share the width; collapsed ones are a strip.
                state.columns.forEach { column ->
                    val onToggle = { onToggleColumn(column.status) }
                    if (column.collapsed) CollapsedColumnView(column, onToggle)
                    else BoardColumnView(column, onToggle, onOpenTicket, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BoardColumnView(column: BoardColumn, onToggle: () -> Unit, onOpenTicket: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(column)
            Text(
                column.status.columnLabel,
                Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

/** A collapsed column: a strip with the count and the name written downwards. Click to open it. */
@Composable
private fun CollapsedColumnView(column: BoardColumn, onToggle: () -> Unit) {
    Column(
        Modifier.width(44.dp).fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onToggle)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusDot(column)
        Text("${column.cards.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            column.status.columnLabel,
            Modifier.vertical().rotate(90f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusDot(column: BoardColumn) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(column.status?.color ?: MaterialTheme.colorScheme.error))
}

private val Status?.columnLabel: String get() = this?.label ?: "Unreadable status"

/** Takes the space of the content turned a quarter, so a following `rotate(90f)` fits its layout. */
private fun Modifier.vertical() = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(placeable.height, placeable.width) {
        placeable.place(x = -(placeable.width - placeable.height) / 2, y = -(placeable.height - placeable.width) / 2)
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
