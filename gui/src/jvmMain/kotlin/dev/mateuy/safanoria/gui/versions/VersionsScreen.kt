package dev.mateuy.safanoria.gui.versions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import dev.mateuy.safanoria.core.BoardGroup
import dev.mateuy.safanoria.core.TicketType
import dev.mateuy.safanoria.core.text
import dev.mateuy.safanoria.gui.theme.color

@Composable
fun VersionsScreen(viewModel: VersionsViewModel, onOpenTicket: (String) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    VersionsContent(state, onOpenTicket, onBack, onRefresh = viewModel::refresh, onSelectComponent = viewModel::selectComponent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionsContent(
    state: VersionsViewState,
    onOpenTicket: (String) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectComponent: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("← Back") } },
                title = { Text("Versions") },
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
            // With one component there is nothing to choose.
            if (state.components.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.components.forEach { component ->
                        FilterChip(selected = component == state.component, onClick = { onSelectComponent(component) }, label = { Text(component) })
                    }
                }
            }
            if (state.releases.isEmpty() && !state.loading && state.error == null) {
                Text(
                    if (state.component == null) "The project has no components." else "No ticket is done in a version yet.",
                    Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.releases.forEach { group ->
                    item(key = group.version ?: "") { VersionGroupView(group, onOpenTicket) }
                }
            }
        }
    }
}

@Composable
private fun VersionGroupView(group: VersionGroup, onOpenTicket: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 8.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background((if (group.version == null) BoardGroup.TO_RELEASE else BoardGroup.RELEASED).color))
            Text(group.version ?: "Unreleased", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${group.count}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        group.tickets.forEach { ticket ->
            TicketRow(ticket, nested = false, onOpenTicket)
            ticket.children.forEach { TicketRow(it, nested = true, onOpenTicket) }
        }
    }
}

@Composable
private fun TicketRow(ticket: VersionTicket, nested: Boolean, onOpenTicket: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpenTicket(ticket.id) }
            .padding(start = if (nested) 54.dp else 30.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(ticket.title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val facts = listOfNotNull(ticket.type?.takeIf { it != TicketType.FEATURE }?.text, ticket.parentId?.let { "↑ $it" })
        if (facts.isNotEmpty()) {
            Text(facts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(
            ticket.id,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
