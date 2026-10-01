"""Startup benchmark for the native-spike: native binary vs JVM jar, small and large repos."""
import pathlib, re, shutil, statistics, subprocess, sys, time

spike = pathlib.Path(sys.argv[1])          # .../spike
repo = spike.parent
big = spike / "build" / "big"

# 504 tickets: this repo's 12, copied 42 times with new ids.
shutil.rmtree(big, ignore_errors=True)
(big / "tickets").mkdir(parents=True)
shutil.copy(repo / "safanoria.yaml", big)
for i in range(1, 43):
    for f in (repo / "tickets").glob("*.md"):
        text = re.sub(r"^id: .*$", f"id: {f.stem}-n{i}", f.read_text(), count=1, flags=re.M)
        (big / "tickets" / f"{f.stem}-n{i}.md").write_text(text)

# Optional second argument: the native binary (CI passes the one for its OS).
native = sys.argv[2] if len(sys.argv) > 2 else str(spike / "build/bin/linuxX64/releaseExecutable/safanoria.kexe")
jar_path = spike / "build/libs/safanoria-jvm.jar"
jar = ["java", "-jar", str(jar_path)]
cases = [
    ("native --help", [native, "--help"], 20),
    ("native load 12", [native, "load", str(repo)], 20),
    ("native load 504", [native, "load", str(big)], 20),
    ("native schema 12", [native, "schema", str(repo)], 20),
]
if jar_path.exists():
    cases += [
        ("jvm load 12", jar + ["load", str(repo)], 10),
        ("jvm load 504", jar + ["load", str(big)], 10),
    ]
for name, cmd, runs in cases:
    out = subprocess.run(cmd, capture_output=True, text=True)  # warm-up
    times = []
    for _ in range(runs):
        s = time.perf_counter()
        subprocess.run(cmd, capture_output=True)
        times.append((time.perf_counter() - s) * 1000)
    print(f"{name:18} mean {statistics.mean(times):6.1f} ms  min {min(times):6.1f} ms  "
          f"({runs} runs; {out.stdout.strip().splitlines()[0] if out.stdout.strip() else out.stderr[:60]})")
