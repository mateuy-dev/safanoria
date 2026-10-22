---
name: sre
description: End the review of this worktree's Safanoria ticket and act on the user's edits and REVIEW comments.
disable-model-invocation: true
---

The user has finished reviewing ("Finish review" in the `safanoria` skill): `safanoria-cli review
--end`, then act on what it lists. Act on each `// REVIEW: …` comment in the code and remove it once
done; commit the user's own edits, with a Work Log `review` entry if one is a decision. Run the
tests and commit. The ticket stays in `review`, unless the comments send it back to work (then
`reopen`, as the skill says).
