package io.buildlens.core.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * Build-system-independent representation of one captured build.
 * Serialized to JSON with schemaVersion; see README for the schema contract.
 */
public class Build {
    @SerializedName("schemaVersion")
    private final int schemaVersion = 1;

    @SerializedName("buildId")
    private String buildId;

    @SerializedName("timestamp")
    private String timestamp;

    @SerializedName("command")
    private String command;

    @SerializedName("tool")
    private String tool;

    @SerializedName("toolVersion")
    private String toolVersion;

    @SerializedName("environment")
    private Environment environment;

    @SerializedName("durationMs")
    private long durationMs;

    @SerializedName("mavenReportedMs")
    private Long reportedDurationMs;

    @SerializedName("status")
    private BuildStatus status;

    @SerializedName("exitCode")
    private Integer exitCode;

    @SerializedName("taskTimingMode")
    private TaskTimingMode taskTimingMode;

    @SerializedName("failureReason")
    private String failureReason;

    @SerializedName("warnings")
    private List<String> warnings = new ArrayList<String>();

    @SerializedName("modules")
    private List<Module> modules = new ArrayList<Module>();

    @SerializedName("tasks")
    private List<Task> tasks = new ArrayList<Task>();

    @SerializedName("tests")
    private List<TestClassResult> tests = new ArrayList<TestClassResult>();

    @SerializedName("testTotals")
    private TestTotals testTotals;

    @SerializedName("downloads")
    private int downloads;

    @SerializedName("analysisMs")
    private long analysisMs;

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getBuildId() {
        return buildId;
    }

    public void setBuildId(String buildId) {
        this.buildId = buildId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public String getToolVersion() {
        return toolVersion;
    }

    public void setToolVersion(String toolVersion) {
        this.toolVersion = toolVersion;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public Long getReportedDurationMs() {
        return reportedDurationMs;
    }

    public void setReportedDurationMs(Long reportedDurationMs) {
        this.reportedDurationMs = reportedDurationMs;
    }

    public BuildStatus getStatus() {
        return status;
    }

    public void setStatus(BuildStatus status) {
        this.status = status;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public TaskTimingMode getTaskTimingMode() {
        return taskTimingMode;
    }

    public void setTaskTimingMode(TaskTimingMode taskTimingMode) {
        this.taskTimingMode = taskTimingMode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public List<TestClassResult> getTests() {
        return tests;
    }

    public TestTotals getTestTotals() {
        return testTotals;
    }

    public void setTestTotals(TestTotals testTotals) {
        this.testTotals = testTotals;
    }

    public int getDownloads() {
        return downloads;
    }

    public void setDownloads(int downloads) {
        this.downloads = downloads;
    }

    public long getAnalysisMs() {
        return analysisMs;
    }

    public void setAnalysisMs(long analysisMs) {
        this.analysisMs = analysisMs;
    }

    /** Aggregate test counters for the whole build. */
    public static class TestTotals {
        @SerializedName("run")
        private int run;

        @SerializedName("failures")
        private int failures;

        @SerializedName("errors")
        private int errors;

        @SerializedName("skipped")
        private int skipped;

        public TestTotals() {
        }

        public TestTotals(int run, int failures, int errors, int skipped) {
            this.run = run;
            this.failures = failures;
            this.errors = errors;
            this.skipped = skipped;
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
    }
}
