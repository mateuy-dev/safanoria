package dev.mateuy.safanoria.views

import dev.mateuy.safanoria.core.ReleaseRequest
import dev.mateuy.safanoria.core.Version

/** What [TicketStore.prepareShip] found: what shipping a parent would do, or why it can't. */
sealed interface ShipCheck {
    data class Ready(val form: ShipForm) : ShipCheck

    data class Refused(val reason: String) : ShipCheck
}

/**
 * The question before shipping parent [id] (SPEC §8.1): what goes to [mainBranch] and whether to
 * stamp it. Not stamping ([stamp] false) is the default and always possible.
 */
data class ShipForm(
    val id: String,
    val mainBranch: String,
    /** The `done` children that reach [mainBranch] for the first time. */
    val children: List<String>,
    val stamp: Boolean = false,
    /** The project's components, all chosen to start with. */
    val components: List<ShipComponent> = emptyList(),
) {
    /** Why it can't be shipped as filled in, or null. */
    val problem: String?
        get() {
            if (!stamp) return null
            val chosen = components.filter { it.chosen }
            if (chosen.isEmpty()) return "Choose a component to stamp, or don't stamp"
            for (c in chosen) {
                val v = c.version.trim()
                if (v.isEmpty()) return if (c.external) "'${c.name}' is external: give the version it was released as" else "Give the version of '${c.name}'"
                if (Version.parse(v) == null) return "'$v' is not a version: MAJOR.MINOR.PATCH, e.g. 4.3.0"
            }
            return null
        }

    /** The stamps asked for; a version left as its source says is read from there again. */
    val releases: List<ReleaseRequest>
        get() = if (!stamp) emptyList() else components.filter { it.chosen }.map { c ->
            val v = c.version.trim()
            ReleaseRequest(c.name, v.takeUnless { it == c.fromSource })
        }

    fun toggleStamp(): ShipForm = copy(stamp = !stamp)

    fun toggle(component: String): ShipForm = copy(components = components.map { if (it.name == component) it.copy(chosen = !it.chosen) else it })

    fun version(component: String, version: String): ShipForm = copy(components = components.map { if (it.name == component) it.copy(version = version) else it })

    /** What the confirmation says: that it all ships, unfinished work too, and nothing is pushed. */
    val text: String
        get() = "Merges the branch $id into $mainBranch, bringing ${children.joinToString()} " +
            "and everything else on $id, its own unfinished work too; logs it in the Work Log of $id" +
            (if (stamp) " and stamps resolvedIn" else "") +
            ". Then merges $mainBranch back into $id, where the remaining children go on. Nothing is pushed."
}

/**
 * A component that can be stamped. [version] starts as [fromSource], the version its source reads
 * on the merged branches; null when it has none (external) or can't be read ([note] says why).
 */
data class ShipComponent(
    val name: String,
    val external: Boolean,
    val fromSource: String?,
    val version: String = fromSource.orEmpty(),
    val chosen: Boolean = true,
    val note: String? = null,
)

/** What [TicketStore.ship] did. */
sealed interface ShipOutcome {
    /** [children] reached [mainBranch] in [commit]; [stamped] is per component, as "app 1.2.0: stamped one". */
    data class Shipped(val mainBranch: String, val children: List<String>, val commit: String, val stamped: List<String>, val warnings: List<String>) : ShipOutcome

    data class NotShipped(val reason: String) : ShipOutcome
}

/** How shipping went, as told to the user. */
val ShipOutcome.notice: Notice
    get() = when (this) {
        is ShipOutcome.NotShipped -> Notice("Not shipped: $reason", error = true)
        is ShipOutcome.Shipped -> Notice(
            (
                listOf("Shipped ${children.joinToString()} to $mainBranch (${commit.take(7)}) and merged it back; nothing was pushed") +
                    stamped + warnings.map { "Warning: $it" }
                ).joinToString("\n"),
            error = false,
        )
    }
