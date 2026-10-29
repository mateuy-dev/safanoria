---
name: ssh
description: Start an existing Safanoria ticket in Herdr.
argument-hint: '[--agent claude|codex] <ticket id>'
disable-model-invocation: true
---

Parse an optional `--agent claude|codex` from the invocation separately. Reject missing,
unsupported, or repeated agent values before creating or starting anything. Remove only that
option and its value from the ticket id or creation text; preserve all remaining text verbatim.
Pass the option to `safanoria-cli start` only when supplied; otherwise let the CLI resolve the
project default. Treat text after `--` as literal ticket text, not launch options. In Codex,
use the text supplied with the skill invocation where Claude uses `$ARGUMENTS` below. Quote the parsed ticket id and agent as separate shell arguments.

Start the parsed ticket: `safanoria-cli start <ticket-id> [--agent <agent>] --herdr`. It opens the worktree in Herdr
with a session of the selected agent that waits for them. Without an id, ask which ticket (`safanoria-cli list` shows the
backlog). Tell the user what `start` opened. If it refused, say why.
