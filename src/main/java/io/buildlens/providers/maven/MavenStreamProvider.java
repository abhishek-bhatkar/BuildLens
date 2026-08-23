package io.buildlens.providers.maven;

import io.buildlens.core.BuildProvider;
import io.buildlens.core.BuildResult;
import io.buildlens.core.CaptureContext;
import io.buildlens.core.model.Build;
import io.buildlens.core.model.BuildStatus;
import io.buildlens.core.model.Environment;
import io.buildlens.core.model.Module;
import io.buildlens.core.model.Task;
import io.buildlens.core.model.TaskTimingConfidence;
import io.buildlens.core.model.TaskTimingMode;
import io.buildlens.core.model.TaskTimingSource;
import io.buildlens.core.model.TestClassResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Captures a running Maven build from its console output stream and
 * normalizes it into the common model.
 *
 * <p>Timing model: Maven prints a marker line immediately before every plugin
 * execution. In a sequential build, the wall-clock interval between one
 * marker and the next boundary event (another marker, a module banner, or the
 * final status line) is that execution's time — measured data, not inference.
 * Parallel builds are detected and explicitly marked approximate.</p>
 */
public final class MavenStreamProvider implements BuildProvider {

    private static final Set<String> HANDLED_TOKENS =
            new HashSet<String>(Arrays.asList("mvn", "maven"));
    private static final DateTimeFormatter ID_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss");

    @Override
    public String toolName() {
        return "maven";
    }

    @Override
    public boolean handles(String tool) {
        return tool != null && HANDLED_TOKENS.contains(tool.toLowerCase());
    }

    @Override
    public BuildResult capture(InputStream output, CaptureContext context, PrintStream console)
            throws IOException {
        MavenLogParser parser = new MavenLogParser();
        StringBuilder rawLog = new StringBuilder(64 * 1024);

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(output, StandardCharsets.UTF_8), 32 * 1024);
        String line;
        while ((line = reader.readLine()) != null) {
            parser.consume(line, System.nanoTime() - context.getStartNanos());
            console.println(line);
            rawLog.append(line).append('\n');
        }
        long endRelativeNanos = System.nanoTime() - context.getStartNanos();
        ParsedLog parsed = parser.finish(endRelativeNanos);

        ParsedLog versionInfo = readVersionFile(context.getVersionFile());
        long durationMs = Math.round(endRelativeNanos / 1_000_000.0);
        Build build = assemble(context, parsed, versionInfo, durationMs);
        return new BuildResult(build, rawLog.toString());
    }

    /**
     * Reads the version banner the launcher captured concurrently
     * ({@code mvn -version}), tolerating a still-being-written file.
     */
    private static ParsedLog readVersionFile(Path versionFile) {
        if (versionFile == null) {
            return null;
        }
        IOException failure = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                if (!Files.exists(versionFile)) {
                    continue;
                }
                MavenLogParser bannerParser = new MavenLogParser();
                for (String line : Files.readAllLines(versionFile, StandardCharsets.UTF_8)) {
                    bannerParser.consume(line, -1L);
                }
                return bannerParser.finish(-1L);
            } catch (IOException e) {
                failure = e;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        if (failure != null) {
            return null;
        }
        return null;
    }

    private Build assemble(CaptureContext context, ParsedLog parsed, ParsedLog versionInfo,
                           long durationMs) {
        OffsetDateTime startedAt = OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(System.currentTimeMillis() - durationMs),
                ZoneId.systemDefault());

        Build build = new Build();
        build.setBuildId(startedAt.format(ID_FORMAT));
        build.setTimestamp(startedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        build.setCommand(context.getCommand());
        build.setProjectDir(context.getProjectDir());
        build.setTool("maven");
        build.setDurationMs(durationMs);
        build.setReportedDurationMs(parsed.getTotalMs());
        build.setExitCode(context.getExitCode());

        build.setToolVersion(firstNonNull(parsed.getToolVersion(),
                versionInfo == null ? null : versionInfo.getToolVersion()));

        Environment environment = toEnvironment(parsed, versionInfo);
        build.setEnvironment(environment);

        if (parsed.getStatus() != BuildStatus.UNKNOWN) {
            build.setStatus(parsed.getStatus());
        } else if (context.getExitCode() != null) {
            build.setStatus(context.getExitCode() == 0 ? BuildStatus.SUCCESS : BuildStatus.FAILURE);
        } else {
            // The status lines are the source of truth; without them we say so.
            build.setStatus(BuildStatus.UNKNOWN);
        }

        TaskTimingMode timingMode = parsed.getTasks().isEmpty() ? TaskTimingMode.NONE
                : parsed.isParallel() ? TaskTimingMode.APPROXIMATE_PARALLEL
                : TaskTimingMode.SEQUENTIAL_ARRIVAL;
        build.setTaskTimingMode(timingMode);

        build.setFailureReason(parsed.getFailureReason());
        build.setDownloads(parsed.getDownloads());

        for (Task task : parsed.getTasks()) {
            task.setStatus(isFailedGoal(parsed, task) ? "FAILURE" : "SUCCESS");
            if (task.getDurationMs() == null) {
                task.setTimingConfidence(TaskTimingConfidence.UNAVAILABLE);
            } else {
                task.setTimingSource(TaskTimingSource.ARRIVAL_CLOCK);
                task.setTimingConfidence(timingMode.confidence());
            }
            build.getTasks().add(task);
        }

        Map<String, ParsedLog.ModuleFacts> factsByName = parsed.getModules();
        boolean singleModule = factsByName.size() == 1;
        for (Map.Entry<String, ParsedLog.ModuleFacts> entry : factsByName.entrySet()) {
            ParsedLog.ModuleFacts facts = entry.getValue();
            Long moduleDuration = facts.durationMs;
            if (moduleDuration == null && singleModule) {
                moduleDuration = durationMs;
            }
            String status = facts.status != null ? facts.status
                    : singleModule ? build.getStatus().name() : BuildStatus.SUCCESS.name();
            build.getModules().add(new Module(entry.getKey(), moduleDuration,
                    facts.taskCount, status));
        }

        build.getTests().addAll(parsed.getTests());
        if (!parsed.getTests().isEmpty()) {
            int run = 0, failures = 0, errors = 0, skipped = 0;
            for (TestClassResult test : parsed.getTests()) {
                run += test.getRun();
                failures += test.getFailures();
                errors += test.getErrors();
                skipped += test.getSkipped();
            }
            build.setTestTotals(new Build.TestTotals(run, failures, errors, skipped));
        } else if (parsed.sawAggregateTotals()) {
            int[] totals = parsed.getAggregateTotals();
            build.setTestTotals(new Build.TestTotals(totals[0], totals[1], totals[2], totals[3]));
        }

        if (parsed.getTasks().isEmpty()) {
            build.addWarning("No plugin executions were found in the build output "
                    + "(quiet mode or unrecognized format); task-level analysis is unavailable.");
        }
        if (parsed.isParallel()) {
            build.addWarning("Parallel build detected; per-task timings are approximate "
                    + "because output from concurrent modules interleaves.");
        }
        if (parsed.getTotalMs() != null
                && Math.abs(durationMs - parsed.getTotalMs()) > 5000) {
            build.addWarning("Maven's reported total time differs from the measured "
                    + "wall-clock time by more than 5s.");
        }
        if (build.getStatus() == BuildStatus.UNKNOWN) {
            build.addWarning("Build status could not be determined from the output.");
        }
        return build;
    }

    private static Environment toEnvironment(ParsedLog parsed, ParsedLog versionInfo) {
        String javaVersion = firstNonNull(parsed.getJavaVersion(),
                versionInfo == null ? null : versionInfo.getJavaVersion());
        String os = firstNonNull(parsed.getOs(),
                versionInfo == null ? null : versionInfo.getOs());
        String mavenHome = firstNonNull(parsed.getMavenHome(),
                versionInfo == null ? null : versionInfo.getMavenHome());
        String locale = firstNonNull(parsed.getLocale(),
                versionInfo == null ? null : versionInfo.getLocale());
        if (javaVersion == null && os == null && mavenHome == null && locale == null) {
            return null;
        }
        return new Environment(javaVersion, os, mavenHome, locale);
    }

    private static boolean isFailedGoal(ParsedLog parsed, Task task) {
        ParsedLog.FailedGoal failed = parsed.getFailedGoal();
        return failed != null
                && failed.goal.equals(task.getGoal())
                && failed.project.equals(task.getModule());
    }

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }
}
