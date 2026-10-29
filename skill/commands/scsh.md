---
name: scsh
description: Create a Safanoria ticket from the text (or this conversation), commit it on the main branch, and start it in Herdr.
argument-hint: '[--agent claude|codex] <what the ticket is about>'
disable-model-invocation: true
---

Parse an optional `--agent claude|codex` from the invocation separately. Reject missing,
unsupported, or repeated agent values before creating or starting anything. Remove only that
option and its value from the ticket id or creation text; preserve all remaining text verbatim.
Pass the option to `safanoria-cli start` only when supplied; otherwise let the CLI resolve the
project default. Treat text after `--` as literal ticket text, not launch options. In Codex,
use the text supplied with the skill invocation where Claude uses `$ARGUMENTS` below. Quote the parsed ticket id and agent as separate shell arguments.

1. Create a Safanoria ticket with the `safanoria` skill (Create, "From a command"), from the remaining creation text (or this conversation when it points to it). Never include the agent option in the ticket text or title.
2. Once it is committed, start it: `safanoria-cli start <id> [--agent <agent>] --herdr`. It opens the worktree in Herdr
   with a session of the selected agent that waits for them.
3. Tell the user the ticket's id and what `start` opened. If `start` refused, say why.
