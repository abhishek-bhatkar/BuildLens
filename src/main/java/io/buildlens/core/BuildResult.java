package io.buildlens.core;

import io.buildlens.core.model.Build;

/** A captured build plus its raw output, kept for local persistence. */
public final class BuildResult {
    private final Build build;
    private final String rawOutput;

    public BuildResult(Build build, String rawOutput) {
        this.build = build;
        this.rawOutput = rawOutput;
    }

    public Build getBuild() {
        return build;
    }

    public String getRawOutput() {
        return rawOutput;
    }
}
