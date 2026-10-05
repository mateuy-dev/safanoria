package dev.mateuy.safanoria.gui.ticket

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mateuy.safanoria.gui.theme.color
import dev.mateuy.safanoria.gui.theme.label

@Composable
fun TicketScreen(viewModel: TicketViewModel, onOpenTicket: (String) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TicketContent(state, onOpenTicket, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketContent(state: TicketViewState, onOpenTicket: (String) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("← Back") } },
                title = {
                    Column {
                        Text(state.title.ifEmpty { state.id }, maxLines = 1)
                        Text(state.id, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                    }
                },
            )
        },
    ) { padding ->
        if (!state.found) {
            Text("No ticket '${state.id}'.", Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        Row(Modifier.padding(padding).fillMaxSize()) {
            SelectionContainer(Modifier.weight(1f).fillMaxHeight()) {
                // Spike: the markdown as text. Rendering it comes next.
                Text(
                    state.body,
                    Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
            VerticalDivider()
            Column(
                Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.status?.let { StatusLine(it.label, it.color) }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.facts.forEach { (name, value) ->
                        Row {
                            Text(name, Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Links("Parent", listOfNotNull(state.parent), onOpenTicket)
                Links("Children", state.children, onOpenTicket)
                Links("Blocked by", state.blockedBy, onOpenTicket)
                Links("Blocks", state.blocks, onOpenTicket)
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Links(title: String, links: List<TicketLink>, onOpenTicket: (String) -> Unit) {
    if (links.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        links.forEach { link ->
            Row(
                // A missing ticket (no title) has nowhere to go.
                Modifier.clickable(enabled = link.title != null) { onOpenTicket(link.id) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(link.status?.color ?: MaterialTheme.colorScheme.outline))
                Column {
                    Text(link.id, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    link.title?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
