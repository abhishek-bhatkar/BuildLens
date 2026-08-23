package io.buildlens.cli;

import io.buildlens.core.BuildResult;
import io.buildlens.core.model.Build;
import io.buildlens.storage.BuildStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Default compare-pair selection, including corrupt-history fallback. */
class CliSelectionTest {

    @TempDir
    Path tempDir;

    private static Build build(String id, long durationMs) {
        Build build = new Build();
        build.setBuildId(id);
        build.setCommand("mvn clean package");
        build.setDurationMs(durationMs);
        return build;
    }

    private static Build build(String id, long durationMs, String projectDir) {
        Build build = build(id, durationMs);
        build.setProjectDir(projectDir);
        return build;
    }

    private BuildStorage storageWithProjects(Object... idAndDir) throws Exception {
        BuildStorage storage = new BuildStorage(tempDir);
        for (int i = 0; i < idAndDir.length; i += 2) {
            String dir = (String) idAndDir[i + 1];
            Build b = dir == null ? build((String) idAndDir[i], 1000)
                    : build((String) idAndDir[i], 1000, dir);
            storage.save(new BuildResult(b, "raw\n"));
        }
        return storage;
    }

    private BuildStorage storageWith(String... ids) throws Exception {
        BuildStorage storage = new BuildStorage(tempDir);
        for (String id : ids) {
            storage.save(new BuildResult(build(id, 1000), "raw\n"));
        }
        return storage;
    }

    private static String[] select(BuildStorage storage) throws Exception {
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errBuffer, true, "UTF-8");
        String[] pair = Main.selectDefaultPair(storage, err);
        err.flush();
        // surface warnings through the return value's closure for assertions
        lastWarnings = new String(errBuffer.toByteArray(), StandardCharsets.UTF_8);
        return pair;
    }

    private static String lastWarnings;

    @Test
    void picksTwoMostRecentReadableBuilds() throws Exception {
        BuildStorage storage = storageWith("b1", "b2", "b3");
        String[] pair = select(storage);
        assertEquals("b3", pair[1]);
        assertEquals("b2", pair[0]);
    }

    @Test
    void walksBackPastCorruptNewestReports() throws Exception {
        BuildStorage storage = storageWith("b1", "b2", "b3", "b4");
        damage("b4");
        damage("b3");
        String[] pair = select(storage);
        assertEquals("b2", pair[1]);
        assertEquals("b1", pair[0]);
        assertTrue(lastWarnings.contains("b4"));
        assertTrue(lastWarnings.contains("b3"));
    }

    @Test
    void allCorruptYieldsNoPair() throws Exception {
        BuildStorage storage = storageWith("b1", "b2");
        damage("b1");
        damage("b2");
        String[] pair = select(storage);
        assertNull(pair[0]);
        assertNull(pair[1]);
    }

    @Test
    void singleReadableBuildHasNoPrevious() throws Exception {
        BuildStorage storage = storageWith("b1", "b2");
        damage("b2");
        String[] pair = select(storage);
        assertEquals("b1", pair[1]);
        assertNull(pair[0]);
    }

    private void damage(String id) throws Exception {
        java.nio.file.Files.write(tempDir.resolve("builds").resolve(id + ".json"),
                "not json at all".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void defaultPairRequiresSameProject() throws Exception {
        // history in chronological order: alpha, beta (different project), alpha
        BuildStorage storage = storageWithProjects(
                "t1-alpha", "/repo/alpha",
                "t2-beta", "/repo/beta",
                "t3-alpha", "/repo/alpha");
        String[] pair = select(storage);
        assertEquals("t3-alpha", pair[1]);
        assertEquals("t1-alpha", pair[0]); // t2-beta is skipped: different project
    }

    @Test
    void noPreviousOfSameProjectYieldsNullPrevious() throws Exception {
        // newest build is the only one of its project
        BuildStorage storage = storageWithProjects(
                "t1-alpha", "/repo/alpha",
                "t2-beta", "/repo/beta");
        String[] pair = select(storage);
        assertEquals("t2-beta", pair[1]);
        assertNull(pair[0]);
    }

    @Test
    void legacyReportsWithoutProjectDirMatchAnything() throws Exception {
        BuildStorage storage = storageWithProjects(
                "t1-legacy", null,
                "t2-alpha", "/repo/alpha");
        String[] pair = select(storage);
        assertEquals("t2-alpha", pair[1]);
        assertEquals("t1-legacy", pair[0]);
    }
}
