package io.buildlens.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.buildlens.core.BuildResult;
import io.buildlens.core.model.Build;
import io.buildlens.core.model.BuildStatus;
import io.buildlens.core.model.TaskTimingMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    }
}
