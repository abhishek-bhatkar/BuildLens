# BuildLens Maven MVP Polish Design

**Goal:** Make the existing Maven-only BuildLens MVP accurate, explainable, testable, and portfolio-ready without expanding into Gradle or a web UI.

## Scope

### Included
- Root README with product positioning, installation, usage, architecture, examples, limitations, and roadmap.
- Explicit measurement provenance and confidence for normalized timings.
- Local build history and previous-versus-current comparison.
- Unit, fixture, and integration-test coverage for the new behavior.
- GitHub Actions CI for Java 8 compatibility and the default test suite.

### Excluded
- Gradle support.
- npm support.
- Web UI.
- Cloud storage or telemetry.
- AI-generated recommendations.

## Architecture

Build providers remain responsible for translating tool-specific output into the common `Build` model. Timing data must carry provenance so BuildLens can distinguish a duration directly reported by Maven from one measured by BuildLens or inferred from log timing.

Persisted build reports remain local and are used by a comparison service. The comparison service compares a selected current build with the most recent compatible previous build, computes absolute and percentage deltas, and ranks regressions by absolute slowdown.

## Measurement Model

Every reported duration has:

- `source`: `REPORTED`, `MEASURED`, or `INFERRED`.
- `confidence`: `HIGH`, `MEDIUM`, or `LOW`.

Mapping:

| Source | Meaning | Default confidence |
|---|---|---|
| REPORTED | Build tool explicitly reported the duration | HIGH |
| MEASURED | BuildLens measured a bounded local interval | MEDIUM |
| INFERRED | BuildLens estimated duration from log/output signals | LOW |

The CLI must never present `INFERRED` durations as exact build-tool timings. If output contains estimates, it must identify them as estimates or display the measurement source/confidence.

## Build History

A successful or failed captured build is persisted locally as the normalized report format already used by the repository. Each report has a stable build identifier, timestamp, command metadata, status, total duration, and available module/category timing data.

History commands provide a concise list of recent reports. Report commands load one stored report by identifier.

## Comparison

The comparison flow compares the current report against the latest previous compatible report for the same project/build context.

Comparison output includes:

- current total duration;
- previous total duration;
- absolute delta;
- percentage delta when previous duration is non-zero;
- classification: improvement, regression, or unchanged;
- ranked contributors by absolute slowdown;
- measurement confidence/source where timing quality affects interpretation.

If no previous compatible report exists, the CLI exits successfully with a clear message rather than fabricating a baseline.

## Error Handling

- Unsupported build commands fail with a clear supported-tool message.
- Missing report IDs produce a clear not-found message.
- Corrupt history files are isolated and reported without corrupting other history.
- Zero-duration previous baselines never cause division-by-zero errors.
- Missing module/category data is omitted from contributor rankings rather than treated as zero-quality evidence.

## Testing

Use JUnit 5 and existing fixture patterns.

Required coverage:

1. Provenance/confidence mapping.
2. Exact versus inferred timing display behavior.
3. Comparison regression, improvement, unchanged, and no-baseline cases.
4. Percentage calculation with zero baseline.
5. Contributor ranking.
6. Maven parser fixtures remain deterministic.
7. Integration tests remain opt-in via the existing Maven profile.

## CI

GitHub Actions runs on pushes and pull requests. The workflow must execute the default Maven verification on a supported JDK that preserves Java 8 bytecode compatibility. Integration tests remain excluded from the default CI workflow unless explicitly enabled later.

## Success Criteria

A new developer can clone the repository, run the documented build command, execute BuildLens against a Maven build, inspect stored history, compare builds, and understand which numbers are exact versus estimated without reading internal source code.
