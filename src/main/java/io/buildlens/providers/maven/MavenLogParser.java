package io.buildlens.providers.maven;

import io.buildlens.core.model.BuildStatus;
import io.buildlens.core.model.Task;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Streaming parser for Maven's default (non-quiet) console output.
 *
 * <p>Two layers are deliberately separated:</p>
 * <ul>
 *   <li><b>Structural facts</b> (tasks, modules, statuses, test results,
 *       reactor summaries) come purely from log text, which makes them
 *       deterministic and golden-testable.</li>
 *   <li><b>Task timings</b> are derived from the wall-clock arrival time of
 *       each output line. Maven prints a marker line immediately before every
 *       plugin execution, so in a sequential build the interval between one
 *       marker and the next boundary event is the execution's wall time. This
 *       is measured data, not inference; parallel builds are explicitly
 *       flagged as approximate.</li>
 * </ul>
 */
public final class MavenLogParser {

    // --- banner (from `mvn -version`; optionally present in build output) ---
    private static final Pattern BANNER_VERSION = Pattern.compile("^Apache Maven (\\S+) \\(.*\\)");
    private static final Pattern BANNER_MAVEN_HOME = Pattern.compile("^Maven home: (.+)$");
    private static final Pattern BANNER_JAVA = Pattern.compile("^Java version: ([^,]+), vendor: .*$");
    private static final Pattern BANNER_LOCALE = Pattern.compile("^Default locale: ([^,]+), platform encoding: .*$");
    private static final Pattern BANNER_OS = Pattern.compile("^OS name: ?\"?([^\",]+)\"?,.*$");

    // --- build structure ---
    private static final Pattern PLUGIN_MARKER =
            Pattern.compile("^\\[INFO\\] --- (.+) @ (\\S+) ---$");
    private static final Pattern MODULE_BANNER =
            Pattern.compile("^\\[INFO\\] -+< ([^>]+) >-+$");
    private static final Pattern BUILDING =
            Pattern.compile("^\\[INFO\\] Building (\\S+) (\\S+)(?:\\s+.*)?$");
    private static final Pattern FROM =
            Pattern.compile("^\\[INFO\\]\\s+from (\\S+)$");
    private static final Pattern REACTOR_ORDER_HEADER =
            Pattern.compile("^\\[INFO\\] Reactor Build Order:$");
    private static final Pattern REACTOR_ORDER_ROW =
            Pattern.compile("^\\[INFO\\] (\\S+)\\s+\\[\\w+\\]$");
    private static final Pattern REACTOR_SUMMARY_HEADER =
            Pattern.compile("^\\[INFO\\] Reactor Summary( for (\\S+) (\\S+):|:)$");
    private static final Pattern REACTOR_SUMMARY_ROW =
            Pattern.compile("^\\[INFO\\] (.+?)\\s*\\.*\\s*(SUCCESS|FAILURE|SKIPPED)\\s+\\[\\s*([\\d.]+)\\s*s\\s*\\]$");
    private static final Pattern BUILD_STATUS =
            Pattern.compile("^\\[(INFO|ERROR)\\] BUILD (SUCCESS|FAILURE)$");
    private static final Pattern TOTAL_TIME =
            Pattern.compile("^\\[INFO\\] Total time:\\s+(.+)$");
    private static final Pattern FINISHED_AT =
            Pattern.compile("^\\[INFO\\] Finished at: (.+)$");

    // --- tests ---
    private static final Pattern TESTS_RUN =
            Pattern.compile("^\\[(INFO|ERROR)\\] Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+)"
                    + "(?:, Time elapsed: ([\\d.]+)\\s*s?)?"
                    + "(?: <<< [A-Z]+!)?"
                    + "(?: -- in (\\S+))?$");

    // --- failure / misc ---
    private static final Pattern FAILED_GOAL =
            Pattern.compile("^\\[ERROR\\] Failed to execute goal (\\S+)(?: \\(([^)]*)\\))? on project (\\S+): (.*)$");
    private static final Pattern DOWNLOADING =
            Pattern.compile("^\\[INFO\\] Downloading from \\S+: \\S+$");
    private static final Pattern MULTITHREADED =
            Pattern.compile("^\\[INFO\\] Using the MultiThreadedBuilder.*$");

    private final ParsedLog result = new ParsedLog();

    /** Index into result.tasks of the task waiting to be terminated, or -1. */
    private int openTask = -1;
    private int taskSeq = 0;

    private boolean inReactorOrder;
    private boolean inReactorSummary;

    /**
     * Feeds one output line with its arrival time, in nanoseconds relative to
     * the build start (the caller records this clock reading as lines arrive).
     */
    public void consume(String line, long arrivalNanos) {
        if (line == null) {
            return;
        }
        consumeBanner(line);

        if (REACTOR_ORDER_HEADER.matcher(line).matches()) {
            inReactorOrder = true;
            return;
        }
        if (REACTOR_SUMMARY_HEADER.matcher(line).matches()) {
            inReactorOrder = false;
            inReactorSummary = true;
            return;
        }

        if (inReactorOrder) {
            Matcher row = REACTOR_ORDER_ROW.matcher(line);
            if (row.matches()) {
                result.module(row.group(1));
                return;
            }
            if (line.trim().equals("[INFO]") || line.trim().isEmpty()) {
                inReactorOrder = false;
                // fall through to normal handling of this (blank) line
            } else {
                return;
            }
        }

        if (inReactorSummary) {
            Matcher row = REACTOR_SUMMARY_ROW.matcher(line);
            if (row.matches()) {
                ParsedLog.ModuleFacts facts = result.module(row.group(1));
                facts.durationMs = MavenDurations.parseMs(row.group(3));
                facts.status = row.group(2);
                return;
            }
            if (line.startsWith("[INFO] ---") || line.trim().equals("[INFO]")) {
                inReactorSummary = false;
                // fall through
            } else {
                return;
            }
        }

        Matcher marker = PLUGIN_MARKER.matcher(line);
        if (marker.matches()) {
            closeOpenTask(arrivalNanos);
            handleMarker(marker.group(1), marker.group(2), arrivalNanos);
            return;
        }

        Matcher moduleBanner = MODULE_BANNER.matcher(line);
        if (moduleBanner.matches()) {
            closeOpenTask(arrivalNanos);
            String ga = moduleBanner.group(1);
            String name = ga.contains(":") ? ga.substring(ga.lastIndexOf(':') + 1) : ga;
            result.module(name).ga = ga;
            return;
        }

        Matcher status = BUILD_STATUS.matcher(line);
        if (status.matches()) {
            closeOpenTask(arrivalNanos);
            result.setStatus(BuildStatus.valueOf(status.group(2)));
            return;
        }

        Matcher building = BUILDING.matcher(line);
        if (building.matches()) {
            result.module(building.group(1));
            return;
        }

        Matcher from = FROM.matcher(line);
        if (from.matches()) {
            // Path of the most recently seen module; Maven prints this
            // directly after the "Building <name>" line.
            String path = from.group(1);
            String lastName = null;
            for (String name : result.getModules().keySet()) {
                lastName = name;
            }
            if (lastName != null) {
                result.module(lastName).path = path;
            }
            return;
        }

        Matcher total = TOTAL_TIME.matcher(line);
        if (total.matches()) {
            result.setTotalMs(MavenDurations.parseMs(total.group(1)));
            return;
        }

        Matcher finished = FINISHED_AT.matcher(line);
        if (finished.matches()) {
            result.setFinishedAt(finished.group(1));
            return;
        }

        Matcher tests = TESTS_RUN.matcher(line);
        if (tests.matches()) {
            int run = Integer.parseInt(tests.group(2));
            int failures = Integer.parseInt(tests.group(3));
            int errors = Integer.parseInt(tests.group(4));
            int skipped = Integer.parseInt(tests.group(5));
            Long elapsed = tests.group(6) == null ? null
                    : MavenDurations.parseMs(tests.group(6));
            String className = tests.group(7);
            if (className != null) {
                result.getTests().add(new io.buildlens.core.model.TestClassResult(
                        className, elapsed, run, failures, errors, skipped));
            } else {
                result.markAggregateTotals(run, failures, errors, skipped);
            }
            return;
        }

        Matcher failedGoal = FAILED_GOAL.matcher(line);
        if (failedGoal.matches()) {
            String ref = failedGoal.group(1);
            String goal = ref.contains(":") ? ref.substring(ref.lastIndexOf(':') + 1) : ref;
            result.setFailedGoal(new ParsedLog.FailedGoal(goal, failedGoal.group(3)));
            if (result.getFailureReason() == null) {
                result.setFailureReason(failedGoal.group(4));
            }
            return;
        }

        if (DOWNLOADING.matcher(line).matches()) {
            result.incrementDownloads();
            return;
        }

        if (MULTITHREADED.matcher(line).matches()) {
            result.setParallel(true);
            return;
        }
    }

    private void consumeBanner(String line) {
        Matcher m = BANNER_VERSION.matcher(line);
        if (m.matches() && result.getToolVersion() == null) {
            result.setToolVersion(m.group(1));
        }
        m = BANNER_MAVEN_HOME.matcher(line);
        if (m.matches() && result.getMavenHome() == null) {
            result.setMavenHome(m.group(1).trim());
        }
        m = BANNER_JAVA.matcher(line);
        if (m.matches() && result.getJavaVersion() == null) {
            result.setJavaVersion(m.group(1).trim());
        }
        m = BANNER_LOCALE.matcher(line);
        if (m.matches() && result.getLocale() == null) {
            result.setLocale(m.group(1).trim());
        }
        m = BANNER_OS.matcher(line);
        if (m.matches() && result.getOs() == null) {
            result.setOs(m.group(1).trim());
        }
    }

    private void handleMarker(String left, String module, long arrivalNanos) {
        String executionId = null;
        Matcher exec = Pattern.compile("^(.*?)\\s*\\(([^)]*)\\)\\s*$").matcher(left);
        if (exec.matches()) {
            executionId = exec.group(2);
            left = exec.group(1);
        }
        String[] parts = left.split(":");
        String prefix;
        String version = null;
        String goal;
        if (parts.length >= 3) {
            prefix = parts[0];
            version = parts[1];
            goal = parts[2];
        } else if (parts.length == 2) {
            prefix = parts[0];
            goal = parts[1];
        } else {
            return;
        }

        Task task = new Task(taskSeq++, prefix, version, goal, executionId, module,
                CategoryMapper.map(prefix, goal));
        task.setStartMs(nanosToMs(arrivalNanos));
        result.getTasks().add(task);
        ParsedLog.ModuleFacts facts = result.module(module);
        facts.taskCount++;
        openTask = result.getTasks().size() - 1;
    }

    private void closeOpenTask(long arrivalNanos) {
        if (openTask >= 0) {
            Task task = result.getTasks().get(openTask);
            task.setEndMs(nanosToMs(arrivalNanos));
            if (task.getStartMs() != null && task.getEndMs() != null) {
                long duration = task.getEndMs() - task.getStartMs();
                task.setDurationMs(Math.max(0L, duration));
            }
            openTask = -1;
        }
    }

    /** Closes any open task at end-of-output and returns the parsed facts. */
    public ParsedLog finish(long endNanos) {
        closeOpenTask(endNanos);
        return result;
    }

    private static Long nanosToMs(long nanos) {
        return nanos < 0 ? null : Long.valueOf(Math.round(nanos / 1_000_000.0));
    }
}
