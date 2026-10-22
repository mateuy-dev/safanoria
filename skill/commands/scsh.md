---
name: scsh
description: Create a Safanoria ticket from the text (or this conversation), commit it on the main branch, and start it in Herdr.
argument-hint: <what the ticket is about>
disable-model-invocation: true
---

1. Create a Safanoria ticket with the `safanoria` skill (Create, "From a command"), from: $ARGUMENTS
2. Once it is committed, start it: `safanoria-cli start <id> --herdr`. It opens the worktree in Herdr
   with a Claude session that waits for them.
3. Tell the user the ticket's id and what `start` opened. If `start` refused, say why.
