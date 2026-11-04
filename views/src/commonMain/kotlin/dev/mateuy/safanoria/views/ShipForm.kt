package dev.mateuy.safanoria.views

/** What [TicketStore.prepareShip] found: what shipping a parent would do, or why it can't. */
sealed interface ShipCheck {
    data class Ready(val form: ShipForm) : ShipCheck

    data class Refused(val reason: String) : ShipCheck
}

/**
 * The question before shipping parent [id] (SPEC §8.1): what goes to [mainBranch]. It doesn't
 * stamp: the shipped children are stamped by the next release, like any other ticket (§9).
 */
data class ShipForm(
    val id: String,
    val mainBranch: String,
    /** The `done` children that reach [mainBranch] for the first time. */
    val children: List<String>,
) {
    /** What the confirmation says: that it all ships, unfinished work too, and nothing is pushed. */
    val text: String
        get() = "Merges the branch $id into $mainBranch, bringing ${children.joinToString()} " +
            "and everything else on $id, its own unfinished work too; logs it in the Work Log of $id. " +
            "Then merges $mainBranch back into $id, where the remaining children go on. " +
            "The next release stamps them. Nothing is pushed."
}

/** What [TicketStore.ship] did. */
sealed interface ShipOutcome {
    /** [children] reached [mainBranch] in [commit]. */
    data class Shipped(val mainBranch: String, val children: List<String>, val commit: String) : ShipOutcome

    data class NotShipped(val reason: String) : ShipOutcome
}

/** How shipping went, as told to the user. */
val ShipOutcome.notice: Notice
    get() = when (this) {
        is ShipOutcome.NotShipped -> Notice("Not shipped: $reason", error = true)
        is ShipOutcome.Shipped -> Notice(
            "Shipped ${children.joinToString()} to $mainBranch (${commit.take(7)}) and merged it back; nothing was pushed",
            error = false,
        )
    }
