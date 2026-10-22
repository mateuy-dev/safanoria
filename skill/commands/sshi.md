---
name: sshi
description: Start an existing Safanoria ticket in Herdr, in a session that implements it.
argument-hint: <ticket id>
disable-model-invocation: true
---

Start ticket `$ARGUMENTS`: `safanoria-cli start $ARGUMENTS --prompt implement`. It opens the worktree in Herdr
with a Claude session that works the ticket to completion and finishes it. Without an id, ask which ticket (`safanoria-cli list` shows the
backlog). Tell the user what `start` opened. If it refused, say why.
