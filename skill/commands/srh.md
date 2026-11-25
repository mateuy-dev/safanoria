---
name: srh
description: Refine an existing backlog or ready ticket in Herdr on the configured main branch.
argument-hint: '[--agent claude|codex] <ticket id>'
disable-model-invocation: true
---

Parse an optional `--agent claude|codex` from the invocation before taking action. Reject missing, unsupported or repeated agent values. Remove only the option and its value. Treat everything after `--` as literal input. Preserve the remaining text verbatim.

Use the remaining argument as the ticket id. If omitted, ask which backlog or ready ticket to refine.

Run `safanoria-cli refine <id> [--agent <agent>]`, quoting arguments separately. Omit the option when unspecified so the CLI resolves the project default. Report the ticket id and what opened, or the reason launch failed.
