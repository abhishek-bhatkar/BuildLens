# BuildLens

**Understand where your build time goes.**

A `BUILD SUCCESS / Total time: 3m 47s` line tells you *how long* your build
took. BuildLens answers the more useful question:

> Why did my build take 3m 47s, where was the time spent, and what should I
> look at to make it faster?

```text
$ buildlens mvn clean package
...original Maven output, unchanged...

BuildLens

BUILD SUCCESS

Total time      3m 47s
Tasks           84
Modules         12
Tests           127 run

Time distribution
────────────────────────────────────────────
Tests           ██████████████████████  1m 47s  47.2%
Compile         ███████████████        1m 31s  40.1%
Packaging       █████                   23s     8.8%
...

Top bottlenecks
────────────────────────────────────────────
1. billing : surefire:test               47.2s
2. billing : compiler:compile            31.8s
...
```

## Install

Requires Java 8+ and Maven 3.9.x on the PATH.

```bash
git clone <this repo> && cd buildlens
mvn -DskipTests package
alias buildlens="$(pwd)/bin/buildlens"    # or add bin/ to your PATH
```

## Usage

| Command | What it does |
| --- | --- |
| `buildlens mvn <args...>` | Runs your Maven build, captures it, prints the analysis summary |
| `buildlens report [id]` | Full report for a captured build (default: latest) |
| `buildlens compare [a b]` | Diff two builds (default: last two) — regression detection |
| `buildlens list` | Show captured build history |
| `buildlens help` / `version` | Usage and version |

```bash
buildlens mvn clean package                  # capture a build
buildlens mvn clean package -DskipTests      # any mvn arguments pass through
buildlens report                             # inspect the latest build
buildlens report 2026-08-15T2217             # id prefixes work
buildlens compare                            # last two builds
```

`buildlens` always exits with **your build's own exit code**. If BuildLens
itself has a problem analyzing the output, it says so and still reports
whatever it captured — it never turns a passing build into a failing one, and
never hides a failing one.

## How it works

```text
buildlens mvn clean package
        │
        ▼
  bin/buildlens ──── runs mvn (unchanged) ────┐
        │                                     │ console output
        │  mvn -version (concurrently,        ▼
        │  zero wall-clock cost)        java -jar buildlens.jar ingest
        │                                     │  ├─ echo output, unmodified
        └────────────────────────────────────►├─ parse + time each line
                                              └─ store report locally
```

* **Execution stays yours.** The launcher runs exactly the command you typed
  and merges its streams into BuildLens. Nothing is injected into your
  build: no pom changes, no plugins, no flags.
* **Timing is measured, not inferred.** Maven prints a marker line before
  every plugin execution. In a sequential build, the wall-clock gap between
  a marker and the next boundary event is that execution's time. Parallel
  builds (`-T`) are detected and their per-task timings are explicitly
  marked *approximate*.
* **Honesty over coverage.** If something can't be measured reliably, it's
  omitted or flagged (e.g. `mvn -q` suppresses markers, so no task analysis
  is fabricated for quiet builds). Module timings come from Maven's own
  reactor summary where available.
* **Local-first.** Reports live under `~/.buildlens/`. Nothing is ever sent
  anywhere.

## Storage format

```text
~/.buildlens/
└── builds/
    ├── 2026-08-15T220103.json    schema-versioned report
    └── 2026-08-15T220103.log     raw build output
```

Filenames sort chronologically; that ordering defines "previous build".
`projectDir` (the build's working directory) pairs reports from the same
project: the default `buildlens compare` only matches builds of one project,
and explicitly crossing projects or commands produces a visible warning.
`BUILDLENS_HOME` overrides the root (also `--home <dir>` on every command).

Schema v1 (fields with no data are omitted):

```json
{
  "schemaVersion": 1,
  "buildId": "2026-08-15T220103",
  "timestamp": "2026-08-15T22:01:03.412+05:30",
  "command": "mvn clean package",
  "projectDir": "/repo/billing",
  "tool": "maven",
  "toolVersion": "3.9.10",
  "environment": { "javaVersion": "1.8.0_322", "os": "mac os x", "...": "..." },
  "durationMs": 227000,
  "reportedDurationMs": 226412,
  "status": "SUCCESS | FAILURE | ABORTED | UNKNOWN",
  "exitCode": 0,
  "taskTimingMode": "SEQUENTIAL_ARRIVAL | APPROXIMATE_PARALLEL | NONE",
  "failureReason": "There are test failures.",
  "warnings": ["..."],
  "modules": [
    { "name": "billing", "durationMs": 72000, "taskCount": 9, "status": "SUCCESS" }
  ],
  "tasks": [
    { "id": 1, "plugin": "surefire", "pluginVersion": "3.2.5", "goal": "test",
      "executionId": "default-test", "module": "billing", "category": "TEST",
      "startMs": 41200, "endMs": 88400, "durationMs": 47200, "status": "SUCCESS" }
  ],
  "tests": [
    { "className": "com.acme.PaymentIT", "timeElapsedMs": 7800,
      "run": 4, "failures": 0, "errors": 0, "skipped": 0 }
  ],
  "testTotals": { "run": 127, "failures": 0, "errors": 0, "skipped": 0 },
  "downloads": 14,
  "analysisMs": 31
}
```

`schemaVersion` gates future migrations; parsers reject or migrate reports
they don't understand.

## Limitations (measured vs. manufactured)

* **Dependency-resolution time** is not shown per-dependency: Maven's
  default output doesn't expose it reliably, and BuildLens does not
  manufacture measurements. Download *counts* are captured.
* **Parallel builds** interleave output, so per-task times there are
  approximations, flagged as such in every report.
* **Quiet mode** (`-q`) suppresses Maven's markers; you get duration and
  status only, with a warning.
* Ctrl-C aborts the pipeline; no report is written for an interrupted build.

## Development

```bash
mvn verify          # unit + golden + flow tests (no network, fast)
mvn package         # builds target/buildlens.jar (shaded)
```

Architecture boundaries (build-system-independent core, pluggable providers):

```text
io.buildlens
├── cli          entry point, commands, exit-code policy
├── core         model (Build/Task/Module), BuildProvider abstraction
├── providers    maven: log parser + stream capture (future: gradle, npm)
├── analysis     aggregation, ranking, build comparison
├── storage      local JSON reports + raw logs
└── report       console renderers
```

The parser is tested against **golden logs captured from real Maven 3.9
builds** (`src/test/resources/golden/`), plus flow tests that stream those
logs through the exact pipeline the launcher uses. `fixtures/` contains
small Maven projects (simple, multi-module, failing, slow) for manual
end-to-end runs:

```bash
cd fixtures/multi-module-project && ../../bin/buildlens mvn clean package
```

## Roadmap

1. ~~Maven capture → normalized model → CLI report~~ (this MVP)
2. Timeline visualization, drill-down
3. Regression detection heuristics across history
4. Browser-based interactive report
5. CI integration (GitHub Actions PR comments)
6. Gradle and npm providers
