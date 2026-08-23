package io.buildlens.providers.maven;

import io.buildlens.core.model.BuildStatus;
import io.buildlens.core.model.Task;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parser tests against real captured Maven 3.9 output (golden logs). */
class MavenLogParserTest {

    private static ParsedLog parse(String resourceName) throws IOException {
        MavenLogParser parser = new MavenLogParser();
        BufferedReader reader = new BufferedReader(GoldenLogsHelper.reader(resourceName));
        long clock = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            clock += 100_000_000L; // synthetic 100ms-per-line arrival clock
            parser.consume(line, clock);
        }
        return parser.finish(clock + 100_000_000L);
    }

    @Test
    void parsesSimpleProjectStructure() throws IOException {
        ParsedLog parsed = parse("golden-simple-project.log");

        assertEquals(BuildStatus.SUCCESS, parsed.getStatus());
        assertNotNull(parsed.getTotalMs());
        assertEquals("2026-08-15T23:44:46+05:30", parsed.getFinishedAt());
        assertNull(parsed.getFailureReason());

        List<Task> tasks = parsed.getTasks();
        // clean, resources, compile, testResources, testCompile, surefire:test, jar
        assertEquals(7, tasks.size());
        assertEquals("clean", tasks.get(0).getGoal());
        assertEquals("simple-project", tasks.get(0).getModule());
        assertEquals("compile", tasks.get(2).getGoal());
        assertEquals("test", tasks.get(5).getGoal());
        assertEquals("jar", tasks.get(6).getGoal());

        assertTrue(parsed.getModules().containsKey("simple-project"));
        assertEquals(7, parsed.getModules().get("simple-project").taskCount);
        assertEquals("com.example.fixtures:simple-project",
                parsed.getModules().get("simple-project").ga);
        assertEquals("pom.xml", parsed.getModules().get("simple-project").path);

        assertEquals(1, parsed.getTests().size());
        assertEquals("com.example.simple.AppTest", parsed.getTests().get(0).getClassName());
        assertEquals(2, parsed.getTests().get(0).getRun());
        assertNotNull(parsed.getTests().get(0).getTimeElapsedMs());
    }

    @Test
    void parsesMultiModuleProject() throws IOException {
        ParsedLog parsed = parse("golden-multi-module-project.log");

        assertEquals(BuildStatus.SUCCESS, parsed.getStatus());
        assertEquals(3, parsed.getModules().size());
        assertTrue(parsed.getModules().containsKey("multi-module-project"));
        assertTrue(parsed.getModules().containsKey("module-a"));
        assertTrue(parsed.getModules().containsKey("module-b"));

        // Reactor summary durations come straight from Maven's own output.
        ParsedLog.ModuleFacts moduleA = parsed.getModules().get("module-a");
        assertNotNull(moduleA.durationMs);
        assertEquals("SUCCESS", moduleA.status);
        assertTrue(moduleA.durationMs > 0);
        assertEquals("module-a/pom.xml", moduleA.path);

        assertEquals(1, parsed.getTests().size());
        assertEquals("com.example.a.CalculatorTest", parsed.getTests().get(0).getClassName());
    }

    @Test
    void parsesFailingBuild() throws IOException {
        ParsedLog parsed = parse("golden-failing-project.log");

        assertEquals(BuildStatus.FAILURE, parsed.getStatus());
        assertNotNull(parsed.getFailureReason());
        assertTrue(parsed.getFailureReason().contains("test failures"));

        assertNotNull(parsed.getFailedGoal());
        assertEquals("test", parsed.getFailedGoal().goal);
        assertEquals("failing-project", parsed.getFailedGoal().project);

        // The build still produced tasks before dying at surefire.
        assertTrue(parsed.getTasks().size() >= 5);
        boolean hasSurefireTest = false;
        for (Task task : parsed.getTasks()) {
            if ("surefire".equals(task.getPlugin()) && "test".equals(task.getGoal())) {
                hasSurefireTest = true;
            }
        }
        assertTrue(hasSurefireTest);

        // Failing test class captured with its counters.
        assertEquals(1, parsed.getTests().size());
        assertEquals(1, parsed.getTests().get(0).getFailures());
        assertEquals("com.example.failing.WidgetTest", parsed.getTests().get(0).getClassName());
    }

    @Test
    void parsesSlowProjectTestTimings() throws IOException {
        ParsedLog parsed = parse("golden-slow-project.log");

        assertEquals(BuildStatus.SUCCESS, parsed.getStatus());
        assertEquals(1, parsed.getTests().size());
        Long elapsed = parsed.getTests().get(0).getTimeElapsedMs();
        assertNotNull(elapsed);
        // The fixture sleeps ~3s; Maven reported 3.043 s in the golden log.
        assertTrue(elapsed >= 2900 && elapsed <= 3300,
                "unexpected elapsed time: " + elapsed);
    }

    @Test
    void syntheticLinesCoverRemainingFormats() {
        MavenLogParser parser = new MavenLogParser();
        long clock = 0;

        // banner lines (from `mvn -version` output)
        clock += 1_000_000L;
        parser.consume("Apache Maven 3.9.10 (5f519b97e944483d878815739f519b2eade0a91d)", clock);
        parser.consume("Maven home: /opt/maven", clock);
        parser.consume("Java version: 21.0.1, vendor: Azul Systems, Inc.", clock);
        parser.consume("Default locale: en_US, platform encoding: UTF-8", clock);
        parser.consume("OS name: \"mac os x\", version: \"14.0\", arch: \"aarch64\"", clock);

        // multi-threaded detection
        clock += 1_000_000L;
        parser.consume("[INFO] Using the MultiThreadedBuilder with 8 threads", clock);
        assertTrue(parser.finish(clock).isParallel());

        // downloads
        MavenLogParser p2 = new MavenLogParser();
        clock += 1_000_000L;
        p2.consume("[INFO] Downloading from central: https://repo.example/junit-4.13.2.jar", clock);
        p2.consume("[INFO] Downloading from central: https://repo.example/hamcrest-2.2.jar", clock);
        assertEquals(2, p2.finish(clock).getDownloads());
    }

    @Test
    void timingDeltasUseArrivalClock() throws IOException {
        MavenLogParser parser = new MavenLogParser();
        BufferedReader reader = new BufferedReader(new StringReader(
                "[INFO] --- clean:3.2.0:clean (default-clean) @ app ---\n"
                        + "[INFO] Deleting /tmp/app/target\n"
                        + "[INFO] --- compiler:3.13.0:compile (default-compile) @ app ---\n"
                        + "[INFO] Compiling 3 source files\n"
                        + "[INFO] BUILD SUCCESS\n"));
        long clock = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            clock += 1_000_000_000L; // 1s per line
            parser.consume(line, clock);
        }
        ParsedLog parsed = parser.finish(clock);
        List<Task> tasks = parsed.getTasks();
        assertEquals(2, tasks.size());
        // clean ran from t=1s to t=3s (marker to next marker)
        assertEquals(1000L, tasks.get(0).getStartMs().longValue());
        assertEquals(3000L, tasks.get(0).getEndMs().longValue());
        assertEquals(2000L, tasks.get(0).getDurationMs().longValue());
        // compile ran until the status line arrived at t=5s
        assertEquals(3000L, tasks.get(1).getStartMs().longValue());
        assertEquals(2000L, tasks.get(1).getDurationMs().longValue());
    }

    @Test
    void buildingLineWithDisplayNameDoesNotCreatePhantomModule() throws IOException {
        MavenLogParser parser = new MavenLogParser();
        BufferedReader reader = new BufferedReader(new StringReader(
                "[INFO] -----------------------< io.buildlens:buildlens >-----------------------\n"
                        + "[INFO] Building BuildLens 0.1.0\n"
                        + "[INFO]   from pom.xml\n"
                        + "[INFO] --- clean:3.2.0:clean (default-clean) @ buildlens ---\n"
                        + "[INFO] --- compiler:3.13.0:compile (default-compile) @ buildlens ---\n"
                        + "[INFO] BUILD SUCCESS\n"));
        long clock = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            clock += 1_000_000L;
            parser.consume(line, clock);
        }
        ParsedLog parsed = parser.finish(clock);
        assertEquals(1, parsed.getModules().size());
        assertEquals("buildlens", parsed.getModules().keySet().iterator().next());
        assertEquals(2, parsed.getModules().get("buildlens").taskCount);
    }

    /** Small indirection so the helper stays out of the production packages. */
    private static final class GoldenLogsHelper {
        static java.io.InputStreamReader reader(String name) throws IOException {
            return new java.io.InputStreamReader(
                    io.buildlens.testsupport.GoldenLogs.stream(name), "UTF-8");
        }
    }
}
