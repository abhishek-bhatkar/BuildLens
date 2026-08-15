# BuildLens Specification

**Assumption:** BuildLens is a developer tool for understanding and improving build performance.

## 1. Vision

**BuildLens makes build performance understandable.**

Instead of showing only:

```text
BUILD SUCCESS
Total time: 3m 47s
```

BuildLens should answer:

> **Why did my build take 3m 47s, where was the time spent, and what can I do about it?**

The tool should transform raw build execution into an understandable model of:

```text
Build
 │
 ├── dependency resolution
 ├── compilation
 ├── test execution
 ├── packaging
 └── other tasks
```

The long-term goal is to become a **build observability and optimization tool**, rather than another build-time dashboard.

---

## 2. Problem

Build systems become slow for reasons that are difficult to identify.

For example:

```text
BUILD: 4m 12s

Compile             1m 31s
Tests               1m 47s
Dependency resolve    31s
Packaging             23s
Other                 00s
```

This tells us **where** time was spent, but not necessarily **why**.

BuildLens should help answer:

- Which task consumed the most time?
- Which tests are slow?
- Which modules cause the most work?
- Which dependencies take longest to resolve?
- Which modules are unnecessarily rebuilt?
- Did the build become slower than previous builds?
- What changed between two builds?
- Where are the biggest optimization opportunities?
- Is the bottleneck CPU, I/O, network, dependency resolution, compilation, or tests?

---

## 3. Target Users

### Primary

Software developers working with:

- Maven
- Gradle
- npm
- potentially other build systems later

### Secondary

- Senior engineers
- Build/release engineers
- Platform engineers
- DevOps engineers
- Engineering teams maintaining large monorepos

### Not initially targeted

- CI/CD platform replacement
- General project management
- Generic observability
- Cloud build infrastructure

---

## 4. Core Product Principle

BuildLens should follow:

> **Observe → Explain → Compare → Recommend**

Not merely:

> **Measure**

Example:

```text
Observe

Build: 3m 47s
        ↓
Explain

Tests consume 61%
        ↓
Compare

+38s vs previous build
        ↓
Recommend

12 integration tests dominate execution time
```

---

## 5. MVP

The first version should focus on **Maven**.

Do not attempt Maven + Gradle + npm + Docker simultaneously.

### MVP command

```bash
buildlens mvn clean package
```

BuildLens should:

1. Execute the supplied Maven build.
2. Capture build execution information.
3. Preserve the original Maven output.
4. Parse build phases/tasks.
5. Measure execution duration.
6. Produce a structured build report.
7. Show the largest time consumers.
8. Store the result locally.
9. Allow comparison with a previous build.

---

## 6. User Experience

### CLI

Example:

```bash
$ buildlens mvn clean package
```

Output:

```text
BuildLens

Analyzing Maven build...

BUILD SUCCESS

Total time       3m 47s
Tasks            84
Modules          12

Time distribution
────────────────────────────────────────

Tests            ████████████████  1m 47s
Compile          █████████████     1m 31s
Dependencies     █████              31s
Packaging        ███                23s

Top bottlenecks
────────────────────────────────────────

1. integrationTest       47.2s
2. test                   31.8s
3. compileJava            24.1s
4. dependency resolution  21.4s

Run:

  buildlens report

to inspect the build.
```

---

## 7. Interactive Report

BuildLens should eventually provide a browser-based report.

Example:

```text
┌────────────────────────────────────────────────┐
│ BuildLens                                      │
│ Build #42                     3m 47s            │
├────────────────────────────────────────────────┤
│                                                │
│  Build Performance                             │
│                                                │
│  Tests       █████████████████  47%            │
│  Compile     █████████████      40%            │
│  Dependency  ███                8%             │
│  Package     ██                 5%              │
│                                                │
├────────────────────────────────────────────────┤
│ Top Bottlenecks                                │
│                                                │
│ integrationTest                    47.2s        │
│ test                               31.8s        │
│ compile                            24.1s        │
└────────────────────────────────────────────────┘
```

The UI is not the product by itself.

The UI is a visualization layer over **build execution data**.

---

## 8. Build Data Model

BuildLens should normalize build execution into a common model.

Conceptually:

```text
Build
 ├── metadata
 ├── duration
 ├── status
 ├── modules[]
 ├── tasks[]
 ├── dependencies[]
 └── measurements[]
```

### Build

```text
Build
- id
- timestamp
- command
- duration
- status
- project
- tool
- toolVersion
```

### Task

```text
Task
- id
- name
- module
- startTime
- endTime
- duration
- status
- type
```

### Module

```text
Module
- id
- name
- path
- duration
- taskCount
```

The internal model should be **build-system independent** even though MVP only supports Maven.

This makes future Gradle support possible without redesigning the core.

---

## 9. Maven Integration

Maven should be treated as a first-class build provider.

Initial architecture:

```text
             BuildLens
                 │
          Build Provider
                 │
              Maven
                 │
        mvn clean package
                 │
          Build Events
                 │
        Normalized Model
                 │
        ┌────────┴────────┐
        ▼                 ▼
      CLI              Report
```

Do not tightly couple the entire application to Maven output parsing.

Use an abstraction such as:

```text
BuildProvider
      │
      └── MavenBuildProvider
```

Future:

```text
BuildProvider
 ├── MavenBuildProvider
 ├── GradleBuildProvider
 └── NpmBuildProvider
```

---

## 10. Build Execution

BuildLens should execute the requested build as a child process.

Example:

```bash
mvn clean package
```

BuildLens should capture:

- stdout
- stderr
- exit code
- start time
- end time
- environment metadata where safe
- Maven version
- Java version
- operating system

It must **not silently alter the user's build**.

---

## 11. Build Isolation

The first version should avoid invasive instrumentation.

BuildLens should initially behave as:

```text
User command
     │
     ▼
BuildLens
     │
     ▼
Maven
     │
     ▼
Application build
```

It should not require:

- modifying `pom.xml`
- adding BuildLens dependencies
- modifying source code
- installing Maven plugins
- changing the project's build configuration

This makes adoption simple.

---

## 12. Build History

BuildLens should persist build reports locally.

Example:

```text
~/.buildlens/
    builds/
        2026-08-15T2201.json
        2026-08-15T2217.json
        2026-08-15T2242.json
```

The storage format should be documented and versioned.

Example:

```json
{
  "schemaVersion": 1,
  "buildId": "...",
  "tool": "maven",
  "durationMs": 227000,
  "status": "SUCCESS"
}
```

The schema must be designed so future versions can migrate old reports.

---

## 13. Build Comparison

One of BuildLens's most important features.

Command:

```bash
buildlens compare
```

Example:

```text
Build comparison

Previous       3m 09s
Current        3m 47s

Regression     +38s  (+20%)

                    Previous    Current     Delta

Compile             1m 08s      1m 31s      +23s
Tests               1m 31s      1m 47s      +16s
Dependencies          29s         31s       +2s
Packaging              21s         23s       +2s
```

Then:

```text
Likely regression

compile
 └── module: billing
      └── +21.3s

tests
 └── integrationTest
      └── +14.8s
```

This is more valuable than showing two charts side by side.

---

## 14. Slowest Tasks

BuildLens should rank tasks.

Example:

```text
TOP 10 SLOWEST TASKS

1. integrationTest       47.2s
2. test                   31.8s
3. compile                24.1s
4. dependency resolution  21.4s
5. package                15.2s
```

Users should be able to drill down:

```text
integrationTest
      │
      ├── UserIntegrationTest     8.2s
      ├── PaymentIntegrationTest  7.8s
      ├── OrderIntegrationTest   6.1s
      └── ...
```

Where the underlying build system exposes enough information to support this accurately.

---

## 15. Module Analysis

For multi-module Maven projects:

```text
Project
│
├── api
├── common
├── user
├── payment
├── order
└── web
```

BuildLens should show:

```text
Module             Time       %

payment             72s      32%
web                 61s      27%
user                43s      19%
order               29s      13%
common              22s       9%
```

This provides immediate value for enterprise Java repositories.

---

## 16. Dependency Analysis

MVP should collect basic dependency-resolution information where reliably available.

Example:

```text
Dependency resolution

spring-core             2.4s
hibernate-core          1.8s
jackson-databind        1.2s
azure-storage            0.9s
```

Eventually:

```text
Why is dependency resolution slow?

12 remote requests
7 cache misses
3 metadata lookups
2 artifact downloads
```

This should only be implemented when accurate data can be obtained.

**BuildLens must not manufacture measurements from inference.**

---

## 17. Build Timeline

A timeline should become a central visualization.

```text
0s        30s       60s       90s       120s
│─────────│─────────│─────────│─────────│

compile   ███████████████████
test                     █████████████████
package                              ███████
```

For parallel builds:

```text
Module A   ███████████████
Module B   █████████
Module C        █████████████
Module D                  ███████
```

This should expose parallelism and idle periods where reliable data exists.

---

## 18. Recommendations

Recommendations should be **evidence-based**.

Bad:

> "You should enable caching."

Good:

```text
Potential optimization

Test execution accounts for 61% of build time.

Evidence:
- 127 tests
- 14 tests account for 43% of test duration
- integrationTest consumes 47.2s

Suggested investigation:
Review the 14 slowest integration tests.
```

BuildLens should distinguish:

```text
Measured
Derived
Potential
Recommendation
```

Never present speculation as fact.

---

## 19. CI Support

Not part of the first MVP.

Future:

```text
GitHub Actions
      │
      ▼
BuildLens
      │
      ▼
Build report
      │
      ▼
PR comment
```

Example:

```text
BuildLens

Build time: 4m 12s
Regression: +31s

⚠ Build became 14% slower.

Main regression:
payment module +24s
```

This could eventually become a major differentiator.

---

## 20. Architecture

Recommended architecture:

```text
                  CLI
                   │
                   ▼
             Build Orchestrator
                   │
          ┌────────┴─────────┐
          ▼                  ▼
   Build Provider       Build Storage
          │                  │
       Maven             JSON/DB
          │
          ▼
     Build Events
          │
          ▼
    Normalized Model
          │
     ┌────┴─────┐
     ▼          ▼
 Analysis     Reports
     │          │
     └────┬─────┘
          ▼
       CLI/UI
```

Core boundaries:

```text
cli
core
providers
analysis
storage
report
```

The exact package structure should follow the existing repository architecture.

---

## 21. Technology Direction

Do not introduce technologies merely for resume keywords.

Prefer a small stack.

For a Java implementation:

```text
Java
 ├── Core analysis
 ├── Process execution
 ├── Parsing
 └── Storage

CLI
 └── lightweight CLI framework or minimal argument parser

Web report
 └── only if justified by the existing project architecture
```

The important engineering signal is the architecture and functionality, not the number of dependencies.

---

## 22. Testing

BuildLens must have strong tests because it analyzes developer infrastructure.

### Unit tests

Test:

- command parsing
- Maven output parsing
- duration calculation
- task extraction
- module extraction
- aggregation
- comparison
- ranking
- report generation

### Integration tests

Run BuildLens against small fixture Maven projects:

```text
fixtures/
├── simple-project
├── multi-module-project
├── failing-project
└── slow-project
```

### Golden/snapshot tests

Given known build output:

```text
input.log
    ↓
BuildLens parser
    ↓
expected Build model
```

This is particularly important because build output parsing is fragile.

---

## 23. Failure Handling

BuildLens must preserve the distinction between:

### Build failure

```text
Maven exit code: 1
```

and:

### BuildLens failure

```text
Could not parse build event.
```

If BuildLens cannot understand part of the build, it should still provide whatever reliable information it captured.

Example:

```text
BUILD FAILED

BuildLens successfully captured:

Duration       2m 14s
Modules        8
Tasks          42

⚠ Some task details could not be parsed.
```

Never fail the user's build because BuildLens analysis failed.

---

## 24. Privacy

BuildLens should be local-first.

Build information can contain:

- project names
- filesystem paths
- dependency names
- environment details
- command arguments

Therefore:

**MVP sends nothing externally.**

No mandatory telemetry.

No cloud account.

No source-code upload.

---

## 25. Performance

BuildLens must not become the reason builds are significantly slower.

Target:

```text
Normal build:       180s
BuildLens:          ≤185s
```

Target overhead:

**<5% for normal builds**, where practical.

The measurement itself must be lightweight.

---

## 26. Non-Goals

MVP will **not**:

- replace Maven
- modify project build configuration
- automatically optimize builds
- automatically edit source code
- upload source code
- provide a cloud dashboard
- support every build system
- provide generic APM
- claim optimizations without evidence

---

## 27. Roadmap

### Phase 1 — Build visibility

```text
Maven
 ↓
Build capture
 ↓
Normalized model
 ↓
CLI report
```

### Phase 2 — Analysis

```text
Task analysis
Module analysis
Timeline
Slowest tasks
```

### Phase 3 — History

```text
Build history
     ↓
Comparison
     ↓
Regression detection
```

### Phase 4 — Interactive UI

```text
CLI
 ↓
Web report
 ↓
Timeline
 ↓
Module graph
 ↓
Drill-down
```

### Phase 5 — CI

```text
GitHub Actions
 ↓
BuildLens
 ↓
PR performance report
```

### Phase 6 — Multiple build systems

```text
BuildLens
├── Maven
├── Gradle
├── npm
└── ...
```

---

## 28. GitHub Signalling Strategy

BuildLens should deliberately demonstrate:

### Backend engineering

- process management
- parsing
- concurrency
- data modeling
- persistence

### Java expertise

- JVM/process execution
- Maven
- multi-module builds
- library design
- testing

### Systems understanding

- build lifecycle
- dependency graphs
- parallel execution
- performance analysis

### Product thinking

The project should answer a clear question:

> **"Why is my build slow?"**

rather than becoming a collection of unrelated metrics.

---

## 29. Definition of Done — MVP

BuildLens MVP is complete when a user can run:

```bash
buildlens mvn clean package
```

and receive:

```text
✓ Build executed
✓ Build duration measured
✓ Build status detected
✓ Maven phases/tasks identified
✓ Modules identified where applicable
✓ Slowest tasks identified
✓ Performance summary generated
✓ Build result persisted
✓ Previous build comparison available
✓ BuildLens itself adds minimal overhead
```

And:

```bash
buildlens compare
```

can identify meaningful duration differences between two captured builds.

---

## 30. Long-Term Product

The eventual experience should be:

```text
                 BUILD LENS

                       │
                       ▼
                 Your Build
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
       Timeline     Modules      Tasks
          │            │            │
          └────────────┼────────────┘
                       ▼
                  Analysis
                       │
             ┌─────────┴─────────┐
             ▼                   ▼
          Regression         Bottleneck
             │                   │
             └─────────┬─────────┘
                       ▼
                  Explanation
                       │
                       ▼
                 Recommendation
```

**Core positioning:**

> **BuildLens — understand where your build time goes.**

The key differentiator should be **explanation**, not merely measurement.

