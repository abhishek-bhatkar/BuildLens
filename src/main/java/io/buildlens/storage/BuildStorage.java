package io.buildlens.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.buildlens.core.BuildResult;
import io.buildlens.core.model.Build;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Local persistence of build reports (spec §12).
 *
 * <p>Layout:</p>
 * <pre>
 * ~/.buildlens/
 *   builds/
 *     2026-08-15T220103.json    -- schema-versioned report
 *     2026-08-15T220103.log     -- raw build output
 * </pre>
 *
 * <p>Filenames sort chronologically, which is how "previous build" is
 * defined. The JSON carries {@code schemaVersion} so future versions can
 * migrate old reports.</p>
 */
public final class BuildStorage {

    /** Thrown when a requested build cannot be found or read. */
    public static class StorageException extends Exception {
        public StorageException(String message) {
            super(message);
        }

        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path buildsDir;

    public BuildStorage(Path root) {
        this.buildsDir = StorageLocations.buildsDir(root);
    }

    /** Persists the report and its raw output; returns the JSON path. */
    public Path save(BuildResult result) throws StorageException {
        try {
            Files.createDirectories(buildsDir);
        } catch (IOException e) {
            throw new StorageException("Could not create " + buildsDir, e);
        }

        Build build = result.getBuild();
        String id = build.getBuildId();
        Path jsonFile = buildsDir.resolve(id + ".json");
        for (int seq = 2; Files.exists(jsonFile); seq++) {
            jsonFile = buildsDir.resolve(id + "-" + seq + ".json");
        }
        build.setBuildId(fileNameBase(jsonFile));

        try {
            Writer writer = Files.newBufferedWriter(jsonFile, StandardCharsets.UTF_8);
            try {
                GSON.toJson(build, writer);
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            throw new StorageException("Could not write " + jsonFile, e);
        }

        try {
            Files.write(buildsDir.resolve(fileNameBase(jsonFile) + ".log"),
                    result.getRawOutput().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // Losing the raw log is not worth failing the report for.
        }
        return jsonFile;
    }

    /** All build ids, oldest first. */
    public List<String> listIds() throws StorageException {
        try {
            if (!Files.isDirectory(buildsDir)) {
                return Collections.emptyList();
            }
            List<String> ids = new ArrayList<String>();
            for (Path file : Files.newDirectoryStream(buildsDir, "*.json")) {
                String name = file.getFileName().toString();
                ids.add(name.substring(0, name.length() - ".json".length()));
            }
            Collections.sort(ids);
            return ids;
        } catch (IOException e) {
            throw new StorageException("Could not list " + buildsDir, e);
        }
    }

    /** Most recent build id, or null if none stored yet. */
    public String latestId() throws StorageException {
        List<String> ids = listIds();
        return ids.isEmpty() ? null : ids.get(ids.size() - 1);
    }

    /**
     * Loads a build by exact id or unambiguous prefix.
     *
     * @throws StorageException when the report is corrupt or unreadable, so a
     *         single damaged file surfaces as a controlled error instead of
     *         an unchecked parse failure
     */
    public Build load(String idOrPrefix) throws StorageException {
        String id = resolveId(idOrPrefix);
        if (id == null) {
            return null;
        }
        Path file = buildsDir.resolve(id + ".json");
        try {
            return GSON.fromJson(Files.newBufferedReader(file, StandardCharsets.UTF_8), Build.class);
        } catch (IOException e) {
            throw new StorageException("Could not read " + file, e);
        } catch (RuntimeException e) {
            throw new StorageException("Report " + id + " is corrupt or not a valid "
                    + "BuildLens report (" + file.getFileName() + ")", e);
        }
    }

    /**
     * Lenient variant for history operations: returns null when the report is
     * corrupt or unreadable so listing and comparison can skip it instead of
     * aborting.
     */
    public Build loadOrNull(String id) {
        try {
            return load(id);
        } catch (StorageException e) {
            return null;
        }
    }

    /** Resolves an exact or prefix id; null when nothing matches. */
    public String resolveId(String idOrPrefix) throws StorageException {
        if (idOrPrefix == null) {
            return null;
        }
        List<String> ids = listIds();
        for (String id : ids) {
            if (id.equals(idOrPrefix)) {
                return id;
            }
        }
        String match = null;
        for (String id : ids) {
            if (id.startsWith(idOrPrefix)) {
                if (match != null) {
                    throw new StorageException("Ambiguous build id prefix: " + idOrPrefix);
                }
                match = id;
            }
        }
        return match;
    }

    private static String fileNameBase(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".json".length());
    }
}
