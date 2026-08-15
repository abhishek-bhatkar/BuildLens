package io.buildlens.storage;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Resolves the local BuildLens storage root (local-first; nothing leaves
 *  this machine, per spec §24). */
public final class StorageLocations {

    public static final String HOME_ENV = "BUILDLENS_HOME";

    private StorageLocations() {
    }

    /** Default storage root: {@code $BUILDLENS_HOME} or {@code ~/.buildlens}. */
    public static Path defaultRoot() {
        String override = System.getenv(HOME_ENV);
        if (override != null && !override.trim().isEmpty()) {
            return Paths.get(override.trim());
        }
        return Paths.get(System.getProperty("user.home"), ".buildlens");
    }

    public static Path buildsDir(Path root) {
        return root.resolve("builds");
    }
}
