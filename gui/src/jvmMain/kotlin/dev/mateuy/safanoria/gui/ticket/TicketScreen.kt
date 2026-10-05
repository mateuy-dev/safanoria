package dev.mateuy.safanoria.gui.ticket

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.markdownAnnotator
import dev.mateuy.safanoria.gui.theme.WarningColor
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.getTextInNode
import dev.mateuy.safanoria.gui.theme.color
import dev.mateuy.safanoria.gui.theme.label

@Composable
fun TicketScreen(viewModel: TicketViewModel, onOpenTicket: (String) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TicketContent(
        state, onOpenTicket, onBack,
        onAction = { action ->
            when (action) {
                TicketAction.START -> viewModel.start()
                TicketAction.OPEN_TERMINAL -> viewModel.openTerminal()
                TicketAction.FINISH -> viewModel.finish()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketContent(
    state: TicketViewState,
    onOpenTicket: (String) -> Unit,
    onBack: () -> Unit,
    onAction: (TicketAction) -> Unit,
) {
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
        bottomBar = { ActionBar(state, onAction) },
    ) { padding ->
        if (!state.found) {
            Text("No ticket '${state.id}'.", Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        Row(Modifier.padding(padding).fillMaxSize()) {
            SelectionContainer(Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Problems(state.problems)
                    Markdown(state.body, typography = ticketTypography(), annotator = ticketAnnotator)
                }
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

/**
 * Keeps text in angle brackets. The parser reads `<id>` as an HTML tag, even inside inline code,
 * and the renderer draws no HTML, so `attachments/<id>/` would lose its middle. Tickets write
 * placeholders that way and have no HTML to draw.
 */
private val ticketAnnotator = markdownAnnotator { content, child ->
    if (child.type == MarkdownTokenTypes.HTML_TAG) {
        append(child.getTextInNode(content).toString())
        true
    } else {
        false
    }
}

/** Headings sized for a ticket: its sections are `##`, which the default draws as a display title. */
@Composable
private fun ticketTypography(): MarkdownTypography {
    val t = MaterialTheme.typography
    return markdownTypography(
        h1 = t.headlineSmall,
        h2 = t.titleLarge,
        h3 = t.titleMedium,
        h4 = t.titleSmall,
        h5 = t.titleSmall,
        h6 = t.titleSmall,
        text = t.bodyMedium,
        paragraph = t.bodyMedium,
        ordered = t.bodyMedium,
        bullet = t.bodyMedium,
        list = t.bodyMedium,
        quote = t.bodyMedium,
        code = t.bodySmall.copy(fontFamily = FontFamily.Monospace),
        inlineCode = t.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        table = t.bodySmall,
    )
}

/** The bar under the ticket: what can be done with it in its status, and how the last action went. */
@Composable
private fun ActionBar(state: TicketViewState, onAction: (TicketAction) -> Unit) {
    if (state.actions.isEmpty() && state.notice == null) return
    Column {
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.actions.forEach { action ->
                val onClick = { onAction(action) }
                when (action) {
                    TicketAction.START -> Button(onClick, enabled = !state.busy) { Text("Start") }
                    TicketAction.OPEN_TERMINAL -> OutlinedButton(onClick, enabled = !state.busy) { Text("Open terminal") }
                    TicketAction.FINISH -> Button(onClick, enabled = !state.busy) { Text("Finish: set to review") }
                }
            }
            if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            state.notice?.let {
                SelectionContainer(Modifier.weight(1f)) {
                    Text(
                        it.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** What `safanoria validate` reports for the ticket. */
@Composable
private fun Problems(problems: List<TicketProblem>) {
    if (problems.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        problems.forEach { p ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    (if (p.error) "error" else "warning") + (p.line?.let { " · line $it" } ?: ""),
                    Modifier.width(130.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (p.error) MaterialTheme.colorScheme.error else WarningColor,
                )
                Column {
                    Text(p.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(p.code, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onErrorContainer)
                }
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
