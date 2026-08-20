# BuildLens Maven MVP Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make BuildLens's Maven MVP portfolio-ready with explicit timing reliability, local history/comparison, tests, documentation, and CI.

**Architecture:** Preserve the existing provider-to-normalized-model design. Add timing provenance as model metadata, keep persistence local, and place comparison logic behind a focused service that consumes stored normalized builds rather than Maven-specific parser classes.

**Tech Stack:** Java 8, Maven, Gson, JUnit 5, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-20-buildlens-mvp-polish-design.md`

## Global Constraints

- Preserve Java 8 source and target compatibility.
- Keep Gson as the only runtime dependency unless a new dependency is strictly necessary.
- Keep the project Maven-first; do not add Gradle, npm, a web UI, cloud storage, or telemetry.
- Preserve original build output and never modify the user's build command semantics.
- Distinguish `REPORTED`, `MEASURED`, and `INFERRED` durations.
- Never present inferred durations as exact build-tool timings.
- Keep integration tests opt-in through the existing `integration-tests` Maven profile.

---

## File Structure

- `README.md` — public product documentation and verified usage examples.
- `src/main/java/io/buildlens/core/model/*` — normalized timing provenance types and model fields.
- `src/main/java/io/buildlens/providers/maven/*` — Maven-specific provenance assignment.
- `src/main/java/io/buildlens/storage/*` — local report persistence/history lookup.
- `src/main/java/io/buildlens/analysis/*` — build comparison and contributor ranking.
- `src/main/java/io/buildlens/cli/*` — history/report/compare commands and provenance-aware rendering.
- `src/test/java/...` — JUnit tests for each new behavior.
- `.github/workflows/ci.yml` — default build and test workflow.

### Task 1: Inspect and lock the current model boundaries

**Files:**
- Modify only after inspection identifies exact classes.
- Test: existing model/parser tests.

**Interfaces:**
- Consumes: existing `Build`, duration, report, storage, and CLI types.
- Produces: a concrete list of model insertion points for provenance metadata.

- [ ] **Step 1: Locate normalized timing types**

Run:
```bash
find src/main/java/io/buildlens -type f | sort
```

Expected: identify the exact classes representing total, module, phase, and category durations.

- [ ] **Step 2: Locate existing persistence and CLI entry points**

Run:
```bash
grep -R "class .*Storage\|class .*Report\|history\|compare\|public static void main" -n src/main/java
```

Expected: identify the current storage format and command dispatch path.

- [ ] **Step 3: Run the baseline suite**

Run:
```bash
mvn clean test
```

Expected: PASS before changes.

- [ ] **Step 4: Commit no behavior changes**

Do not create a commit unless inspection requires a documentation correction. Record the exact discovered classes in the task implementation notes.

### Task 2: Add timing provenance and confidence

**Files:**
- Create: exact provenance enum file under `src/main/java/io/buildlens/core/model/`.
- Create: exact confidence enum file under `src/main/java/io/buildlens/core/model/`.
- Modify: the normalized timing-bearing model classes identified in Task 1.
- Test: matching model tests under `src/test/java/io/buildlens/core/model/`.

**Interfaces:**
- Produces: `MeasurementSource` with `REPORTED`, `MEASURED`, `INFERRED`.
- Produces: `MeasurementConfidence` with `HIGH`, `MEDIUM`, `LOW`.
- Produces: deterministic source-to-confidence mapping.

- [ ] **Step 1: Write failing source-to-confidence tests**

```java
assertEquals(HIGH, MeasurementSource.REPORTED.defaultConfidence());
assertEquals(MEDIUM, MeasurementSource.MEASURED.defaultConfidence());
assertEquals(LOW, MeasurementSource.INFERRED.defaultConfidence());
```

- [ ] **Step 2: Run the focused test**

Run:
```bash
mvn -Dtest=MeasurementSourceTest test
```

Expected: FAIL because the type does not exist.

- [ ] **Step 3: Implement the enum**

```java
public enum MeasurementSource {
    REPORTED(MeasurementConfidence.HIGH),
    MEASURED(MeasurementConfidence.MEDIUM),
    INFERRED(MeasurementConfidence.LOW);

    private final MeasurementConfidence defaultConfidence;

    MeasurementSource(MeasurementConfidence defaultConfidence) {
        this.defaultConfidence = defaultConfidence;
    }

    public MeasurementConfidence defaultConfidence() {
        return defaultConfidence;
    }
}
```

Create the corresponding confidence enum and add source/confidence fields to the normalized timing model using backward-compatible constructors where existing tests require them.

- [ ] **Step 4: Run focused and full tests**

Run:
```bash
mvn -Dtest=MeasurementSourceTest test && mvn test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/buildlens/core/model src/test/java/io/buildlens/core/model
git commit -m "feat: add timing measurement provenance"
```

### Task 3: Assign Maven timing provenance correctly

**Files:**
- Modify: `src/main/java/io/buildlens/providers/maven/MavenLogParser.java`.
- Modify: `src/main/java/io/buildlens/providers/maven/MavenDurations.java`.
- Modify: `src/main/java/io/buildlens/providers/maven/MavenStreamProvider.java`.
- Test: Maven parser/provider tests.

**Interfaces:**
- Consumes: `MeasurementSource` and normalized timing model from Task 2.
- Produces: Maven-reported durations marked `REPORTED`; local bounded measurements marked `MEASURED`; output-arrival estimates marked `INFERRED`.

- [ ] **Step 1: Write a failing parser fixture assertion**

Use an existing Maven reactor-summary fixture and assert:

```java
assertEquals(MeasurementSource.REPORTED, duration.getSource());
assertEquals(MeasurementConfidence.HIGH, duration.getConfidence());
```

- [ ] **Step 2: Run the focused parser test**

Run:
```bash
mvn -Dtest=MavenLogParserTest test
```

Expected: FAIL before provenance assignment.

- [ ] **Step 3: Mark each timing creation path explicitly**

At every parser/provider timing creation point, pass the source explicitly rather than relying on an implicit default.

- [ ] **Step 4: Add an inferred-timing test**

Feed the stream path that uses output arrival intervals and assert `INFERRED`/`LOW`.

- [ ] **Step 5: Run all Maven provider tests**

Run:
```bash
mvn -Dtest='Maven*Test' test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/buildlens/providers/maven src/test/java/io/buildlens/providers/maven
git commit -m "feat: classify Maven timing reliability"
```

### Task 4: Implement local history lookup

**Files:**
- Modify: exact existing storage classes identified in Task 1.
- Create: focused history repository/service only if no existing abstraction fits.
- Test: storage tests.

**Interfaces:**
- Produces: `List<Build>` or the repository's existing persisted-report type ordered newest first.
- Produces: lookup by stable report/build identifier.
- Produces: latest previous compatible build lookup.

- [ ] **Step 1: Write failing persistence tests**

Cover save/load ordering and ID lookup:

```java
assertEquals(newest.getId(), history.get(0).getId());
assertEquals(build.getId(), repository.findById(build.getId()).get().getId());
```

- [ ] **Step 2: Run focused storage tests**

Run:
```bash
mvn -Dtest='*StorageTest,*HistoryTest' test
```

Expected: FAIL for the new operations.

- [ ] **Step 3: Implement deterministic local history**

Use the repository's existing JSON persistence format. Do not introduce a database. Ignore or clearly report individual corrupt files while continuing to load valid reports.

- [ ] **Step 4: Add missing-ID and corrupt-file tests**

Assert empty lookup for missing IDs and successful loading of valid reports when one unrelated persisted file is malformed.

- [ ] **Step 5: Run tests**

Run:
```bash
mvn -Dtest='*StorageTest,*HistoryTest' test && mvn test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/buildlens/storage src/test/java/io/buildlens/storage
git commit -m "feat: add local build history lookup"
```

### Task 5: Implement build comparison

**Files:**
- Create: focused comparison service/model classes under `src/main/java/io/buildlens/analysis/` if absent.
- Test: `src/test/java/io/buildlens/analysis/`.

**Interfaces:**
- Consumes: two normalized builds/reports.
- Produces: total delta, percentage delta, classification, and ranked contributors.

- [ ] **Step 1: Write failing regression test**

```java
Comparison result = comparisonService.compare(previous, current);
assertEquals(ComparisonStatus.REGRESSION, result.getStatus());
assertEquals(Duration.ofSeconds(38), result.getTotalDelta());
```

- [ ] **Step 2: Write improvement and unchanged tests**

Assert negative/positive/zero delta classification without string-based comparisons.

- [ ] **Step 3: Write zero-baseline test**

Assert no exception and an absent/undefined percentage when previous duration is zero.

- [ ] **Step 4: Implement comparison models and service**

Use absolute duration delta for contributor ranking. Match contributors by stable normalized identity; omit entries unavailable in either comparable side when evidence is insufficient.

- [ ] **Step 5: Run focused tests**

Run:
```bash
mvn -Dtest='*Comparison*Test' test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/buildlens/analysis src/test/java/io/buildlens/analysis
git commit -m "feat: compare build performance"
```

### Task 6: Add CLI history, report, and compare flows

**Files:**
- Modify: existing CLI command dispatch classes identified in Task 1.
- Test: CLI tests.

**Interfaces:**
- Consumes: history repository from Task 4 and comparison service from Task 5.
- Produces commands equivalent to `history`, `report <id>`, and `compare` using existing CLI conventions.

- [ ] **Step 1: Write failing command tests**

Cover:

```text
buildlens history
buildlens report <id>
buildlens compare
```

Assert the no-history and missing-report cases are clear and non-crashing.

- [ ] **Step 2: Run focused CLI tests**

Run:
```bash
mvn -Dtest='*CliTest,*CommandTest' test
```

Expected: FAIL before commands are wired.

- [ ] **Step 3: Implement concise rendering**

Display total duration, delta, status, top contributors, and provenance/confidence where a displayed duration is inferred. Do not label inferred values as Maven-reported.

- [ ] **Step 4: Run full tests**

Run:
```bash
mvn test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/buildlens/cli src/test/java/io/buildlens/cli
git commit -m "feat: add build history and comparison commands"
```

### Task 7: Add GitHub Actions CI

**Files:**
- Create: `.github/workflows/ci.yml`.

**Interfaces:**
- Consumes: Maven wrapper if present; otherwise repository `pom.xml`.
- Produces: repeatable CI verification for pushes and pull requests.

- [ ] **Step 1: Create the workflow**

Use checkout, a JDK that can run Maven while preserving the project's Java 8 target, dependency caching, and:

```bash
mvn -B clean test
```

- [ ] **Step 2: Verify locally**

Run:
```bash
mvn -B clean test
```

Expected: PASS before relying on remote CI.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add Maven test workflow"
```

### Task 8: Write the public README

**Files:**
- Create or modify: `README.md`.

**Interfaces:**
- Consumes: verified commands and actual CLI behavior from Tasks 2-7.
- Produces: clone-to-first-use documentation with no invented examples.

- [ ] **Step 1: Run the packaged CLI manually**

Run the actual packaging command and each documented command. Capture real output before writing examples.

- [ ] **Step 2: Write README sections**

Include exactly:

```text
What is BuildLens?
Why it exists
Features
Installation
Quick start
Example output
How it works
Measurement reliability
Build history and comparison
Architecture
Testing
Limitations
Roadmap
```

- [ ] **Step 3: Verify every command in README**

Run each shell command from a clean checkout or equivalent clean build state.

- [ ] **Step 4: Run final verification**

Run:
```bash
mvn clean test
mvn package
```

If integration fixtures are available and Maven is installed, additionally run:

```bash
mvn -Pintegration-tests test
```

Expected: default suite and package PASS; integration result recorded honestly if environment-dependent.

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "docs: add BuildLens project guide"
```

## Final Verification

- [ ] Confirm `git status` is clean.
- [ ] Run `mvn clean test`.
- [ ] Run `mvn package`.
- [ ] Run each documented CLI command against a real or fixture-backed Maven build.
- [ ] Inspect generated JSON history for provenance persistence.
- [ ] Compare two reports and verify contributor ordering.
- [ ] Confirm inferred durations are visibly marked and never described as exact Maven timings.
- [ ] Confirm GitHub Actions workflow exists and triggers on push and pull request.
