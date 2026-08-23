package io.buildlens.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.buildlens.core.BuildResult;
import io.buildlens.core.model.Build;
import io.buildlens.core.model.BuildStatus;
import io.buildlens.core.model.Category;
import io.buildlens.core.model.Task;
import io.buildlens.core.model.TaskTimingConfidence;
import io.buildlens.core.model.TaskTimingMode;
import io.buildlens.core.model.TaskTimingSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildStorageTest {

    @TempDir
    Path tempDir;

    private static BuildResult result(String buildId, String command, long durationMs) {
        Build build = new Build();
        build.setBuildId(buildId);
        build.setTimestamp("2026-08-15T22:01:03+05:30");
        build.setCommand(command);
        build.setTool("maven");
        build.setDurationMs(durationMs);
        build.setStatus(BuildStatus.SUCCESS);
        build.setTaskTimingMode(TaskTimingMode.SEQUENTIAL_ARRIVAL);
        Task task = new Task(0, "maven-clean-plugin", "3.2.0", "clean",
                "default-clean", "app", Category.CLEAN);
        task.setStartMs(0L);
        task.setEndMs(1200L);
        task.setDurationMs(1200L);
        task.setTimingSource(TaskTimingSource.ARRIVAL_CLOCK);
        task.setTimingConfidence(TaskTimingConfidence.HIGH);
        build.getTasks().add(task);
        return new BuildResult(build, "raw output line\n");
    }

    @Test
    void savesAndReloadsWithSchemaVersion() throws Exception {
        BuildStorage storage = new BuildStorage(tempDir);
        Path saved = storage.save(result("2026-08-15T220103", "mvn clean package", 227_000));

        assertTrue(saved.toString().endsWith("2026-08-15T220103.json"));
        assertTrue(java.nio.file.Files.exists(tempDir.resolve(
                "builds/2026-08-15T220103.log")));

        Build loaded = storage.load("2026-08-15T220103");
        assertNotNull(loaded);
        assertEquals(1, loaded.getSchemaVersion());
        assertEquals("mvn clean package", loaded.getCommand());
        assertEquals(227_000L, loaded.getDurationMs());
        assertEquals(BuildStatus.SUCCESS, loaded.getStatus());
    }

    @Test
    void listsChronologicallyAndResolvesPrefixes() throws Exception {
        BuildStorage storage = new BuildStorage(tempDir);
        storage.save(result("2026-08-15T220103", "mvn clean package", 1000));
        storage.save(result("2026-08-15T221744", "mvn clean package", 2000));
        storage.save(result("2026-08-15T224201", "mvn clean package", 3000));

        List<String> ids = storage.listIds();
        assertEquals(3, ids.size());
        assertEquals("2026-08-15T220103", ids.get(0));
        assertEquals("2026-08-15T224201", storage.latestId());

        assertEquals("2026-08-15T221744", storage.resolveId("2026-08-15T2217"));
        assertNull(storage.resolveId("1970"));
    }

    @Test
    void collidingIdsGetSequenceSuffix() throws Exception {
        BuildStorage storage = new BuildStorage(tempDir);
        storage.save(result("2026-08-15T220103", "mvn clean package", 1000));
        storage.save(result("2026-08-15T220103", "mvn clean package", 2000));

        List<String> ids = storage.listIds();
        assertEquals(2, ids.size());
        assertTrue(ids.contains("2026-08-15T220103"));
        assertTrue(ids.contains("2026-08-15T220103-2"));
    }

    @Test
    void ambiguousPrefixIsRejected() throws Exception {
        BuildStorage storage = new BuildStorage(tempDir);
        storage.save(result("2026-08-15T220103", "mvn clean package", 1000));
        storage.save(result("2026-08-15T220199", "mvn clean package", 2000));
        try {
            storage.resolveId("2026-08-15T22");
            throw new AssertionError("expected ambiguity failure");
        } catch (BuildStorage.StorageException expected) {
            assertTrue(expected.getMessage().contains("Ambiguous"));
        }
    }

    @Test
    void corruptReportsAreIsolatedNotFatal() throws Exception {
        BuildStorage storage = new BuildStorage(tempDir);
        storage.save(result("2026-08-15T220103", "mvn clean package", 1000));
        storage.save(result("2026-08-15T221744", "mvn clean package", 2000));
        // damage the newest report the way a truncated write would
        java.nio.file.Files.write(
                tempDir.resolve("builds/2026-08-15T221744.json"),
                "{ not valid json".getBytes("UTF-8"));

        try {
            storage.load("2026-08-15T221744");
            throw new AssertionError("expected corrupt-report failure");
        } catch (BuildStorage.StorageException e) {
            assertTrue(e.getMessage().contains("corrupt"));
        }

        // lenient reads let history operations skip the damaged file
        assertNull(storage.loadOrNull("2026-08-15T221744"));
        assertEquals("mvn clean package", storage.loadOrNull("2026-08-15T220103").getCommand());
        // id listing is filename-based and unaffected
        assertEquals(2, storage.listIds().size());
    }

    @Test
    void jsonShapeMatchesDocumentedSchema() throws Exception {
        BuildStorage storage = new BuildStorage(tempDir);
        Path saved = storage.save(result("2026-08-15T220103", "mvn clean package", 227_000));
        String json = new String(java.nio.file.Files.readAllBytes(saved), "UTF-8");

        Gson gson = new GsonBuilder().create();
        com.google.gson.JsonObject tree =
                gson.fromJson(json, com.google.gson.JsonObject.class);
        assertEquals(1, tree.get("schemaVersion").getAsInt());
        assertEquals("maven", tree.get("tool").getAsString());
        assertEquals("SUCCESS", tree.get("status").getAsString());
        assertEquals(227_000, tree.get("durationMs").getAsLong());

        com.google.gson.JsonObject task =
                tree.getAsJsonArray("tasks").get(0).getAsJsonObject();
        assertEquals("ARRIVAL_CLOCK", task.get("timingSource").getAsString());
        assertEquals("HIGH", task.get("timingConfidence").getAsString());
    }

    @Test
    void timingProvenanceSurvivesRoundTrip() throws Exception {
        BuildStorage storage = new BuildStorage(tempDir);
        storage.save(result("2026-08-15T220103", "mvn clean package", 227_000));

        Task loaded = storage.load("2026-08-15T220103").getTasks().get(0);
        assertEquals(TaskTimingSource.ARRIVAL_CLOCK, loaded.getTimingSource());
        assertEquals(TaskTimingConfidence.HIGH, loaded.getTimingConfidence());
        assertFalse(loaded.hasApproximateDuration());
        // legacy reports written before provenance existed load with nulls
        java.nio.file.Files.write(tempDir.resolve("builds/legacy.json"),
                ("{\"schemaVersion\":1,\"buildId\":\"legacy\",\"tasks\":["
                        + "{\"id\":0,\"durationMs\":500}]}").getBytes("UTF-8"));
        Task legacy = storage.load("legacy").getTasks().get(0);
        assertNull(legacy.getTimingSource());
        assertNull(legacy.getTimingConfidence());
    }
}
