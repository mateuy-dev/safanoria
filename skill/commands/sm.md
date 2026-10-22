---
name: sm
description: Merge this worktree's Safanoria ticket into its target (safanoria-cli merge), ending the review first.
disable-model-invocation: true
---

The user has reviewed this worktree's ticket and wants it merged ("Merge it" in the `safanoria`
skill):

1. In review mode (the SessionStart context says so), `safanoria-cli review --end` first. If it
   lists `// REVIEW: …` comments, stop: tell the user, and merge only once they say so. Commit the
   user's other edits.
2. `safanoria-cli merge <id>`. It removes this worktree, so it is the last thing to do. If it
   refuses, fix that on the branch if it's mechanical (commit, merge the target in) and run it
   again; otherwise tell the user why.
