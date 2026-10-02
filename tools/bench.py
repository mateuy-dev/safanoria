#!/usr/bin/env python3
"""Startup benchmark of the native CLI (target for pre-commit hooks: < 100 ms on a real repository).

Usage: python3 tools/bench.py <safanoria binary> [runs]

Cases: `version` (process start only); `dump` over this repository (config, every ticket's
frontmatter, body and schema check); `dump` over a synthetic repository with this repository's
tickets copied 42 times (~500 tickets), created under build/bench/.
"""
import pathlib
import re
import shutil
import statistics
import subprocess
import sys
import time

repo = pathlib.Path(__file__).resolve().parent.parent
binary = sys.argv[1]
runs = int(sys.argv[2]) if len(sys.argv) > 2 else 20

big = repo / "build" / "bench" / "big"
shutil.rmtree(big, ignore_errors=True)
(big / "tickets").mkdir(parents=True)
shutil.copy(repo / "safanoria.yaml", big)
for i in range(1, 43):
    for f in (repo / "tickets").glob("*.md"):
        text = re.sub(r"^id: .*$", f"id: {f.stem}-n{i}", f.read_text(encoding="utf-8"), count=1, flags=re.M)
        (big / "tickets" / f"{f.stem}-n{i}.md").write_text(text, encoding="utf-8")

cases = [
    ("version", [binary, "version"]),
    ("dump, this repository", [binary, "--root", str(repo), "dump"]),
    ("dump, synthetic repository", [binary, "--root", str(big), "dump"]),
]
for name, cmd in cases:
    first = subprocess.run(cmd, capture_output=True, text=True)  # warm-up (file cache)
    if first.returncode != 0:
        sys.exit(f"{name} failed ({first.returncode}): {first.stderr[:500]}")
    times = []
    for _ in range(runs):
        start = time.perf_counter()
        subprocess.run(cmd, capture_output=True)
        times.append((time.perf_counter() - start) * 1000)
    last = first.stdout.strip().splitlines()
    summary = next((l for l in reversed(last) if l.endswith("tickets")), last[-1] if last else "")
    print(f"{name:28} mean {statistics.mean(times):6.1f} ms  min {min(times):6.1f} ms  ({runs} runs; {summary})")
