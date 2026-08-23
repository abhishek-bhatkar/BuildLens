package io.buildlens.report;

import io.buildlens.analysis.BuildAnalyzer;
import io.buildlens.analysis.BuildSummary;
import io.buildlens.analysis.ComparisonResult;
import io.buildlens.core.model.Build;
import io.buildlens.core.model.BuildStatus;
import io.buildlens.core.model.Environment;
import io.buildlens.core.model.Module;
import io.buildlens.core.model.Task;
import io.buildlens.core.model.TaskTimingMode;
import io.buildlens.core.model.TestClassResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static io.buildlens.report.TextFormat.duration;
import static io.buildlens.report.TextFormat.pad;
import static io.buildlens.report.TextFormat.padLeft;
import static io.buildlens.report.TextFormat.percentOf;
import static io.buildlens.report.TextFormat.repeat;
import static io.buildlens.report.TextFormat.signedDuration;
import static io.buildlens.report.TextFormat.signedPercent;

/** Renders all human-facing console output. Plain ASCII+box drawing only,
 *  so reports survive pipes, logs and CI. */
public final class ConsoleReporter {

    private static final int LABEL_WIDTH = 16;
    private static final int BAR_WIDTH = 22;
    private static final String RULE = repeat('─', 44);

    private final BuildAnalyzer analyzer = new BuildAnalyzer();

    /** Short summary printed right after a captured build (spec §6). */
    public String runSummary(Build build, Path savedTo, long analysisMs) {
        BuildSummary summary = analyzer.summarize(build, 4);
        StringBuilder sb = new StringBuilder(2048);
        sb.append('\n').append(Ansi.apply(Ansi.Style.BOLD, "BuildLens")).append('\n').append('\n');

        sb.append(statusLine(build.getStatus())).append('\n').append('\n');

        sb.append(row("Total time", duration(build.getDurationMs())));
        sb.append(row("Tasks", String.valueOf(build.getTasks().size())));
        if (!build.getModules().isEmpty()) {
            sb.append(row("Modules", String.valueOf(build.getModules().size())));
        }
        if (build.getTestTotals() != null) {
            sb.append(row("Tests", testTotalsLine(build)));
        }
        sb.append('\n');

        if (!summary.getCategoryTotals().isEmpty()) {
            sb.append("Time distribution").append('\n');
            sb.append(RULE).append('\n');
            appendDistribution(sb, summary);
            sb.append('\n');
        }

        if (!summary.getSlowestTasks().isEmpty()) {
            sb.append("Top bottlenecks").append('\n');
            sb.append(RULE).append('\n');
            int rank = 1;
            for (Task task : summary.getSlowestTasks()) {
                sb.append(rank++).append(". ")
                        .append(pad(task.label(), 34))
                        .append(padLeft(duration(task.getDurationMs()), 9))
                        .append('\n');
            }
            sb.append('\n');
        }

        if (build.getFailureReason() != null) {
            sb.append(Ansi.apply(Ansi.Style.RED, "Failure")).append("  ")
                    .append(wrap(build.getFailureReason(), 60)).append('\n').append('\n');
        }

        appendWarnings(sb, build);

        if (savedTo != null) {
            sb.append(Ansi.apply(Ansi.Style.DIM, "Report saved to " + savedTo)).append('\n');
        }
        sb.append('\n');
        sb.append("Run ").append(Ansi.apply(Ansi.Style.CYAN, "buildlens report"))
                .append(" for the full report").append('\n');
        sb.append("Run ").append(Ansi.apply(Ansi.Style.CYAN, "buildlens compare"))
                .append(" to diff against the previous build").append('\n');
        return sb.toString();
    }

    /** Full offline report for `buildlens report` (spec §7 spirit, text form). */
    public String fullReport(Build build) {
        BuildSummary summary = analyzer.summarize(build, 10);
        StringBuilder sb = new StringBuilder(4096);

        sb.append(Ansi.apply(Ansi.Style.BOLD, "BuildLens report")).append(" — ")
                .append(build.getBuildId()).append('\n').append('\n');

        sb.append(section("Overview"));
        sb.append(row("Command", build.getCommand() == null ? "n/a" : build.getCommand()));
        if (build.getProjectDir() != null) {
            sb.append(row("Project", build.getProjectDir()));
        }
        sb.append(row("Tool", toolLine(build)));
        sb.append(row("Java", environmentValue(build, ENV_JAVA_VERSION)));
        sb.append(row("OS", environmentValue(build, ENV_OS)));
        sb.append(row("Started", build.getTimestamp() == null ? "n/a" : build.getTimestamp()));
        sb.append(row("Status", statusText(build.getStatus())));
        sb.append(row("Total time", duration(build.getDurationMs())));
        sb.append(row("Maven reported", build.getReportedDurationMs() == null
                ? "n/a" : duration(build.getReportedDurationMs())));
        sb.append(row("Tasks", build.getTasks().size() + timingModeSuffix(build)));
        if (!build.getModules().isEmpty()) {
            sb.append(row("Modules", String.valueOf(build.getModules().size())));
        }
        if (build.getTestTotals() != null) {
            sb.append(row("Tests", testTotalsLine(build)));
        }
        if (build.getDownloads() > 0) {
            sb.append(row("Downloads", build.getDownloads() + " artifact downloads"));
        }
        sb.append('\n');

        if (!summary.getCategoryTotals().isEmpty()) {
            sb.append(section("Time distribution"));
            appendDistribution(sb, summary);
            sb.append('\n');
        }

        if (!summary.getSlowestTasks().isEmpty()) {
            sb.append(section("Slowest tasks"));
            int rank = 1;
            for (Task task : summary.getSlowestTasks()) {
                sb.append(rank++).append(". ")
                        .append(pad(task.label(), 40))
                        .append(padLeft(duration(task.getDurationMs()), 9))
                        .append("   ")
                        .append(task.getCategory() == null ? "" : task.getCategory().displayName())
                        .append('\n');
            }
            sb.append('\n');
        }

        if (!summary.getModulesByDuration().isEmpty()) {
            long moduleTotal = 0;
            for (Module module : summary.getModulesByDuration()) {
                if (module.getDurationMs() != null) {
                    moduleTotal += module.getDurationMs();
                }
            }
            sb.append(section("Modules"));
            for (Module module : summary.getModulesByDuration()) {
                String share = module.getDurationMs() == null || moduleTotal == 0
                        ? "" : "  " + percentOf(module.getDurationMs(), moduleTotal);
                sb.append(pad(module.getName(), 24))
                        .append(padLeft(module.getDurationMs() == null
                                ? "n/a" : duration(module.getDurationMs()), 9))
                        .append(share)
                        .append('\n');
            }
            sb.append('\n');
        }

        if (!summary.getSlowestTestClasses().isEmpty()) {
            sb.append(section("Slowest test classes"));
            for (TestClassResult test : summary.getSlowestTestClasses()) {
                sb.append(pad(test.shortName(), 28))
                        .append(padLeft(test.getTimeElapsedMs() == null
                                ? "n/a" : duration(test.getTimeElapsedMs()), 9))
                        .append("   ")
                        .append(test.getRun()).append(" tests")
                        .append('\n');
            }
            sb.append('\n');
        }

        appendWarnings(sb, build);
        return sb.toString();
    }

    /** `buildlens compare` output (spec §13). */
    public String compare(ComparisonResult result) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append(Ansi.apply(Ansi.Style.BOLD, "Build comparison")).append('\n').append('\n');

        sb.append(row("Previous", result.getPreviousId() + "   "
                + duration(result.getPreviousDurationMs())));
        sb.append(row("Current", result.getCurrentId() + "   "
                + duration(result.getCurrentDurationMs())));
        sb.append('\n');

        String verdictLine = signedDuration(result.getDurationDeltaMs()) + "  ("
                + signedPercent(result.getDurationDeltaPercent()) + ")";
        switch (result.getVerdict()) {
            case REGRESSION:
                sb.append(row("Regression", Ansi.apply(Ansi.Style.RED, verdictLine)));
                break;
            case IMPROVEMENT:
                sb.append(row("Improvement", Ansi.apply(Ansi.Style.GREEN, verdictLine)));
                break;
            default:
                sb.append(row("Delta", verdictLine)).append("  (within noise threshold)");
                break;
        }
        if (result.getContextWarning() != null) {
            sb.append('\n').append(Ansi.apply(Ansi.Style.YELLOW,
                    "⚠ " + result.getContextWarning())).append('\n');
        }
        sb.append('\n');

        if (!result.getCategoryRows().isEmpty()) {
            sb.append(pad("", 20)).append(padLeft("Previous", 10))
                    .append(padLeft("Current", 10)).append(padLeft("Delta", 10)).append('\n');
            for (ComparisonResult.DeltaRow row : result.getCategoryRows()) {
                sb.append(pad(row.label, 20))
                        .append(padLeft(duration(row.previousMs), 10))
                        .append(padLeft(duration(row.currentMs), 10))
                        .append(padLeft(signedDuration(row.deltaMs), 10))
                        .append('\n');
            }
            sb.append('\n');
        }

        int shown = 0;
        for (ComparisonResult.TaskDelta delta : result.getTaskRegressions()) {
            if (delta.deltaMs < 500 || shown >= 5) {
                break;
            }
            if (shown == 0) {
                sb.append(section("Likely regression"));
            }
            sb.append("  ").append(pad(delta.current.label(), 36))
                    .append(padLeft(signedDuration(delta.deltaMs), 9))
                    .append('\n');
            shown++;
        }
        if (shown > 0) {
            sb.append('\n');
        }

        shown = 0;
        for (ComparisonResult.TaskDelta delta : result.getTaskImprovements()) {
            if (delta.deltaMs > -500 || shown >= 5) {
                break;
            }
            if (shown == 0) {
                sb.append(section("Improved"));
            }
            sb.append("  ").append(pad(delta.current.label(), 36))
                    .append(padLeft(signedDuration(delta.deltaMs), 9))
                    .append('\n');
            shown++;
        }
        if (shown > 0) {
            sb.append('\n');
        }
        return sb.toString();
    }

    /** `buildlens list` output. */
    public String list(List<Build> builds) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append(Ansi.apply(Ansi.Style.BOLD, "BuildLens history")).append('\n').append('\n');
        if (builds.isEmpty()) {
            sb.append("No builds captured yet.").append('\n');
            sb.append("Run: buildlens mvn clean package").append('\n');
            return sb.toString();
        }
        sb.append(pad("ID", 22)).append(pad("Status", 10))
                .append(pad("Time", 9)).append("Command").append('\n');
        for (Build build : builds) {
            sb.append(pad(build.getBuildId(), 22))
                    .append(pad(statusText(build.getStatus()), 10))
                    .append(pad(duration(build.getDurationMs()), 9))
                    .append(build.getCommand() == null ? "" : build.getCommand())
                    .append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------

    private static final java.util.function.Function<Environment, String> ENV_JAVA_VERSION =
            new java.util.function.Function<Environment, String>() {
                @Override
                public String apply(Environment env) {
                    return env.getJavaVersion();
                }
            };

    private static final java.util.function.Function<Environment, String> ENV_OS =
            new java.util.function.Function<Environment, String>() {
                @Override
                public String apply(Environment env) {
                    return env.getOs();
                }
            };

    private static String environmentValue(Build build,
                                           java.util.function.Function<Environment, String> extract) {
        if (build.getEnvironment() == null) {
            return "n/a";
        }
        String value = extract.apply(build.getEnvironment());
        return value == null ? "n/a" : value;
    }

    private static String toolLine(Build build) {
        if (build.getToolVersion() == null) {
            return build.getTool() == null ? "n/a" : build.getTool();
        }
        return build.getTool() + " " + build.getToolVersion();
    }

    private static String timingModeSuffix(Build build) {
        if (build.getTaskTimingMode() == null
                || build.getTaskTimingMode() == TaskTimingMode.NONE) {
            return "";
        }
        return build.getTaskTimingMode() == TaskTimingMode.APPROXIMATE_PARALLEL
                ? " (approximate; parallel build)" : "";
    }

    private static String testTotalsLine(Build build) {
        Build.TestTotals totals = build.getTestTotals();
        StringBuilder sb = new StringBuilder();
        sb.append(totals.getRun()).append(" run");
        if (totals.getFailures() > 0 || totals.getErrors() > 0) {
            sb.append(", ").append(totals.getFailures() + totals.getErrors()).append(" failed");
        }
        if (totals.getSkipped() > 0) {
            sb.append(", ").append(totals.getSkipped()).append(" skipped");
        }
        return sb.toString();
    }

    private static String statusText(BuildStatus status) {
        return status == null ? "UNKNOWN" : status.name();
    }

    private static String statusLine(BuildStatus status) {
        String text = status == null ? "BUILD UNKNOWN" : "BUILD " + status.name();
        if (status == BuildStatus.SUCCESS) {
            return Ansi.apply(Ansi.Style.GREEN, Ansi.apply(Ansi.Style.BOLD, text));
        }
        if (status == BuildStatus.FAILURE || status == BuildStatus.ABORTED) {
            return Ansi.apply(Ansi.Style.RED, Ansi.apply(Ansi.Style.BOLD, text));
        }
        return Ansi.apply(Ansi.Style.BOLD, text);
    }

    private static void appendDistribution(StringBuilder sb, BuildSummary summary) {
        long max = 0;
        for (long value : summary.getCategoryTotals().values()) {
            max = Math.max(max, value);
        }
        for (Map.Entry<io.buildlens.core.model.Category, Long> entry
                : summary.getCategoryTotals().entrySet()) {
            sb.append(pad(entry.getKey().displayName(), LABEL_WIDTH))
                    .append(TextFormat.bar(entry.getValue(), max, BAR_WIDTH))
                    .append("  ")
                    .append(padLeft(duration(entry.getValue()), 8))
                    .append("  ")
                    .append(percentOf(entry.getValue(), summary.getAttributedMs()))
                    .append('\n');
        }
        if (summary.getOverheadMs() > 1000 && summary.getOverheadMs() * 20 > summary.getAttributedMs()) {
            sb.append(pad("Overhead", LABEL_WIDTH))
                    .append(TextFormat.bar(0, max, 0))
                    .append("  ")
                    .append(padLeft(duration(summary.getOverheadMs()), 8))
                    .append("  ").append("not attributed to any task")
                    .append('\n');
        }
    }

    private static void appendWarnings(StringBuilder sb, Build build) {
        if (build.getWarnings().isEmpty()) {
            return;
        }
        for (String warning : build.getWarnings()) {
            sb.append(Ansi.apply(Ansi.Style.YELLOW, "⚠ " + wrap(warning, 70))).append('\n');
        }
        sb.append('\n');
    }

    private static String section(String title) {
        return Ansi.apply(Ansi.Style.BOLD, title) + '\n' + RULE + '\n';
    }

    private static String row(String label, String value) {
        return pad(label, LABEL_WIDTH) + value + '\n';
    }

    private static String wrap(String text, int width) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        int lineLength = 0;
        for (String word : text.split(" ")) {
            if (lineLength > 0 && lineLength + 1 + word.length() > width) {
                sb.append('\n').append(repeat(' ', LABEL_WIDTH));
                lineLength = 0;
            } else if (lineLength > 0) {
                sb.append(' ');
                lineLength++;
            }
            sb.append(word);
            lineLength += word.length();
        }
        return sb.toString();
    }
}
