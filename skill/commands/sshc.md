---
name: sshc
description: Start an existing Safanoria ticket in Herdr, in a session that discusses it first.
argument-hint: <ticket id>
disable-model-invocation: true
---

Start ticket `$ARGUMENTS`: `safanoria-cli start $ARGUMENTS --prompt chat`. It opens the worktree in Herdr
with a Claude session that discusses the ticket with them before changing anything. Without an id, ask which ticket (`safanoria-cli list` shows the
backlog). Tell the user what `start` opened. If it refused, say why.
