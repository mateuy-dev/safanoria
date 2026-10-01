---
id: v1-tooling-native-spike
type: research
title: Can the Safanoria CLI be built with Kotlin/Native?
status: backlog
priority: high
size: S
created: 2026-10-01
updated: 2026-10-01
parent: v1-tooling
---

## Objective

The CLI is planned as a Kotlin/Native binary sharing a KMP `core` with a future Compose Desktop
viewer. Before `cli-core` builds on it, prove the pieces work on linuxX64, macosArm64 and
mingwX64. Time box: size S. If it fails, fall back to a JVM CLI on the same `core` and record why.

## Acceptance Criteria

- [ ] Which YAML library reads frontmatter on all native targets (candidates: kaml,
      snakeyaml-engine-kmp)?
- [ ] Do Clikt (CLI) and Okio (file system) work on all three targets?
- [ ] Is a KMP JSON Schema validator usable (candidate: OptimumCode `json-schema-validator`), or
      are per-file rules written in Kotlin?
- [ ] How does the CLI run `git` and read its output on each target (branch existence, staged
      files)?
- [ ] Measured startup time of a hello-world native binary that loads `safanoria.yaml`
- [ ] Can CI cross-build all three binaries, and on which runners (macOS needs a macOS runner)?

## Plan

## Learnings

## Work Log
