package io.buildlens.core.model;

/**
 * Coarse, build-system-independent phase categories used for the time
 * distribution view. Providers map their native task/goal names onto these.
 */
public enum Category {
    CLEAN("Clean"),
    RESOURCES("Resources"),
    COMPILE("Compile"),
    TEST("Tests"),
    PACKAGE("Packaging"),
    INSTALL("Install"),
    DEPLOY("Deploy"),
    OTHER("Other");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
