package io.buildlens.core.model;

import com.google.gson.annotations.SerializedName;

/** Per-test-class timing as reported by the build system's test runner. */
public class TestClassResult {
    @SerializedName("className")
    private String className;

    @SerializedName("timeElapsedMs")
    private Long timeElapsedMs;

    @SerializedName("run")
    private int run;

    @SerializedName("failures")
    private int failures;

    @SerializedName("errors")
    private int errors;

    @SerializedName("skipped")
    private int skipped;

    public TestClassResult() {
    }

    public TestClassResult(String className, Long timeElapsedMs, int run,
                           int failures, int errors, int skipped) {
        this.className = className;
        this.timeElapsedMs = timeElapsedMs;
        this.run = run;
        this.failures = failures;
        this.errors = errors;
        this.skipped = skipped;
    }

    public String getClassName() {
        return className;
    }

    public Long getTimeElapsedMs() {
        return timeElapsedMs;
    }

    public int getRun() {
        return run;
    }

    public int getFailures() {
        return failures;
    }

    public int getErrors() {
        return errors;
    }

    public int getSkipped() {
        return skipped;
    }

    /** Simple class name without package, for compact display. */
    public String shortName() {
        int idx = className == null ? -1 : className.lastIndexOf('.');
        return idx < 0 ? className : className.substring(idx + 1);
    }
}
