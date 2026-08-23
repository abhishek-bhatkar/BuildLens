package io.buildlens.cli;

import io.buildlens.core.BuildResult;
import io.buildlens.core.CaptureContext;
import io.buildlens.core.model.Build;
import io.buildlens.core.model.BuildStatus;
import io.buildlens.providers.maven.MavenStreamProvider;
import io.buildlens.report.ConsoleReporter;
import io.buildlens.storage.BuildStorage;
import io.buildlens.testsupport.GoldenLogs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the exact pipeline the launcher drives:
 * capture stream → persist report → render summary/report/comparison.
 */
class IngestFlowTest {

    @TempDir
    Path tempDir;

    private static final class Console {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final PrintStream stream = utf8(buffer);

        String text() {
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static PrintStream utf8(ByteArrayOutputStream buffer) {
        try {
            return new PrintStream(buffer, true, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 unavailable", e);
        }
    }

    private BuildResult capture(String goldenLog, Console console) throws Exception {
        MavenStreamProvider provider = new MavenStreamProvider();
        CaptureContext context = new CaptureContext("mvn clean package", null, null,
                System.nanoTime());
        return provider.capture(GoldenLogs.stream(goldenLog), context, console.stream);
    }

    /**
     * Streams bytes with a pause after each newline so arrival-time-based task
     * durations become meaningful (the surefire block has the most lines and
     * therefore dominates, as in real builds).
     */
    private BuildResult capturePaced(String goldenLog, Console console) throws Exception {
        MavenStreamProvider provider = new MavenStreamProvider();
        CaptureContext context = new CaptureContext("mvn clean package", null, null,
                System.nanoTime());
        java.io.InputStream paced = new PacedStream(GoldenLogs.bytes(goldenLog), 15);
        return provider.capture(paced, context, console.stream);
    }

    private static final class PacedStream extends java.io.InputStream {
        private final byte[] data;
        private int position;
        private final long delayMs;

        PacedStream(byte[] data, long delayMs) {
            this.data = data;
            this.delayMs = delayMs;
        }

        @Override
        public int read() {
            if (position >= data.length) {
                return -1;
            }
            int b = data[position++];
            if (b == '\n') {
                pause();
            }
            return b;
        }

        /**
         * Delivers at most one line per call. Without this, the default
         * bulk read loops until the buffer is full and would collapse the
         * pacing (real pipes return short reads, which is what production
         * relies on).
         */
        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (position >= data.length) {
                return -1;
            }
            if (length <= 0) {
                return 0;
            }
            int end = position;
            while (end < data.length && data[end] != '\n') {
                end++;
            }
            int lineEnd = Math.min(data.length, end + 1); // include the newline
            int count = Math.min(length, lineEnd - position);
            System.arraycopy(data, position, buffer, offset, count);
            position += count;
            if (position > 0 && data[position - 1] == '\n') {
                pause();
            }
            return count;
        }

        private void pause() {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void captureEchoesOriginalOutputAndProducesAModel() throws Exception {
        Console console = new Console();
        BuildResult result = capture("golden-simple-project.log", console);

        // the user's original build output is preserved verbatim
        String echoed = console.text();
        assertTrue(echoed.contains("[INFO] BUILD SUCCESS"));
        assertTrue(echoed.contains(
                "[INFO] --- surefire:3.2.5:test (default-test) @ simple-project ---"));

        Build build = result.getBuild();
        assertEquals(BuildStatus.SUCCESS, build.getStatus());
        assertEquals(7, build.getTasks().size());
        assertEquals(1, build.getModules().size());
        assertEquals("simple-project", build.getModules().get(0).getName());
        assertNotNull(build.getTestTotals());
        assertEquals(2, build.getTestTotals().getRun());
        assertTrue(build.getDurationMs() > 0);

        // raw output is retained for persistence
        assertTrue(result.getRawOutput().contains("[INFO] BUILD SUCCESS"));
    }

    @Test
    void projectDirFromContextIsCaptured() throws Exception {
        Console console = new Console();
        MavenStreamProvider provider = new MavenStreamProvider();
        CaptureContext context = new CaptureContext("mvn clean package", null, null,
                System.nanoTime(), "/repo/simple-project");
        BuildResult result = provider.capture(GoldenLogs.stream("golden-simple-project.log"),
                context, console.stream);
        assertEquals("/repo/simple-project", result.getBuild().getProjectDir());
    }

    @Test
    void persistenceRoundTripThenSummaryAndReport() throws Exception {
        Console console = new Console();
        BuildResult result = capturePaced("golden-simple-project.log", console);
        BuildStorage storage = new BuildStorage(tempDir);
        Path saved = storage.save(result);

        Console summaryOut = new Console();
        summaryOut.stream.print(new ConsoleReporter()
                .runSummary(result.getBuild(), saved, 3));
        String summary = summaryOut.text();
        assertTrue(summary.contains("BUILD SUCCESS"));
        assertTrue(summary.contains("Total time"));
        assertTrue(summary.contains("Time distribution"));
        assertTrue(summary.contains("Top bottlenecks"));
        assertTrue(summary.contains("simple-project : surefire:test"));

        Build reloaded = storage.load(result.getBuild().getBuildId());
        assertNotNull(reloaded);
        assertEquals(1, reloaded.getSchemaVersion());

        Console reportOut = new Console();
        reportOut.stream.print(new ConsoleReporter().fullReport(reloaded));
        String report = reportOut.text();
        assertTrue(report.contains("BuildLens report"));
        assertTrue(report.contains("Command"));
        assertTrue(report.contains("mvn clean package"));
        assertTrue(report.contains("Slowest tasks"));
        assertTrue(report.contains("Modules"));
        assertTrue(report.contains("Slowest test classes"));
        assertTrue(report.contains("AppTest"));
    }

    @Test
    void failingBuildStillCapturesWhatItCan() throws Exception {
        Console console = new Console();
        BuildResult result = capture("golden-failing-project.log", console);

        Build build = result.getBuild();
        assertEquals(BuildStatus.FAILURE, build.getStatus());
        assertNotNull(build.getFailureReason());
        assertTrue(build.getFailureReason().contains("test failures"));
        assertTrue(build.getTasks().size() >= 5);

        // one task is pinpointed as the failed goal
        boolean failedTaskFound = false;
        for (io.buildlens.core.model.Task task : build.getTasks()) {
            if ("FAILURE".equals(task.getStatus())
                    && "test".equals(task.getGoal())
                    && "failing-project".equals(task.getModule())) {
                failedTaskFound = true;
            }
        }
        assertTrue(failedTaskFound);

        Path saved = new BuildStorage(tempDir).save(result);
        Console summaryOut = new Console();
        summaryOut.stream.print(new ConsoleReporter().runSummary(build, saved, 3));
        String summary = summaryOut.text();
        assertTrue(summary.contains("BUILD FAILURE"));
        assertTrue(summary.contains("Failure"));
    }

    @Test
    void comparisonFlowAfterTwoBuilds() throws Exception {
        Console first = new Console();
        Console second = new Console();
        BuildStorage storage = new BuildStorage(tempDir);
        Build buildA = capture("golden-simple-project.log", first).getBuild();
        Build buildB = capture("golden-multi-module-project.log", second).getBuild();

        Console compareOut = new Console();
        compareOut.stream.print(new ConsoleReporter().compare(
                new io.buildlens.analysis.BuildComparison().compare(buildA, buildB)));
        String comparison = compareOut.text();
        assertTrue(comparison.contains("Build comparison"));
        assertTrue(comparison.contains("Previous"));
        assertTrue(comparison.contains("Current"));

        Console listOut = new Console();
        java.util.List<Build> builds = new java.util.ArrayList<Build>();
        for (String id : storage.listIds()) {
            builds.add(storage.load(id));
        }
        // storage was not written in this test; the listing renders empties too
        listOut.stream.print(new ConsoleReporter().list(builds));
        assertTrue(listOut.text().contains("BuildLens history"));
    }

    @Test
    void quietOrEmptyStreamDegradesToWarnings() throws Exception {
        Console console = new Console();
        MavenStreamProvider provider = new MavenStreamProvider();
        CaptureContext context = new CaptureContext("mvn -q clean package", null, null,
                System.nanoTime());
        java.io.InputStream empty = new java.io.ByteArrayInputStream(
                "almost nothing here\n".getBytes(StandardCharsets.UTF_8));
        BuildResult result = provider.capture(empty, context, console.stream);

        assertEquals(BuildStatus.UNKNOWN, result.getBuild().getStatus());
        assertTrue(result.getBuild().getTasks().isEmpty());
        assertTrue(result.getBuild().getWarnings().size() >= 2);
    }
}
