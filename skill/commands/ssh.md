---
name: ssh
description: Start an existing Safanoria ticket in Herdr.
argument-hint: <ticket id>
disable-model-invocation: true
---

Start ticket `$ARGUMENTS`: `safanoria-cli start $ARGUMENTS --herdr`. It opens the worktree in Herdr
with a Claude session that waits for them. Without an id, ask which ticket (`safanoria-cli list` shows the
backlog). Tell the user what `start` opened. If it refused, say why.
