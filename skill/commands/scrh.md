---
name: scrh
description: Create a backlog ticket on the configured main branch and refine it in Herdr.
argument-hint: '[--agent claude|codex] <what the ticket is about>'
disable-model-invocation: true
---

Parse an optional `--agent claude|codex` from the invocation before taking action. Reject missing, unsupported or repeated agent values. Remove only the option and its value. Treat everything after `--` as literal input. Preserve the remaining text verbatim.

Check that this session is inside Herdr and that the configured main branch has an existing checkout before creating the ticket. Follow the Safanoria skill's Create flow for command invocations, explicitly creating a top-level ticket on `mainBranch` with `safanoria-cli new … --on <mainBranch>`. Use the remaining text or the referenced conversation for the Objective. Once committed, use the resulting ticket id for the launch.

Run `safanoria-cli refine <id> [--agent <agent>]`, quoting arguments separately. Omit the option when unspecified so the CLI resolves the project default. Report the ticket id and what opened, or the reason launch failed.
