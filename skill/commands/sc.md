---
name: sc
description: Create a Safanoria ticket from the text (or this conversation) and commit it on the session branch by default (top-level on main only when requested or outside a ticket session).
argument-hint: <what the ticket is about>
disable-model-invocation: true
---

Create a Safanoria ticket with the `safanoria` skill (Create, "From a command"), from: $ARGUMENTS

Then tell the user the ticket's id. Don't start it.
