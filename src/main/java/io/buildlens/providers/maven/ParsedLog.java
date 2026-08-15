package io.buildlens.providers.maven;

import io.buildlens.core.model.BuildStatus;
import io.buildlens.core.model.Task;
import io.buildlens.core.model.TestClassResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Structural facts extracted from one Maven build log. */
public final class ParsedLog {

    private String toolVersion;
    private String javaVersion;
    private String os;
    private String mavenHome;
    private String locale;

    private BuildStatus status = BuildStatus.UNKNOWN;
    private Long totalMs;
    private String finishedAt;
    private String failureReason;
    private FailedGoal failedGoal;
    private boolean parallel;

    /** Module name -> mutable facts, in first-seen order. */
    private final Map<String, ModuleFacts> modules = new LinkedHashMap<String, ModuleFacts>();

    private final List<Task> tasks = new ArrayList<Task>();
    private final List<TestClassResult> tests = new ArrayList<TestClassResult>();
    private final int[] aggregateTotals = new int[4];
    private boolean sawAggregateTotals;
    private int downloads;

    private final List<String> warnings = new ArrayList<String>();

    public static class ModuleFacts {
        public String ga;
        public String path;
        public Long durationMs;
        public String status;
        public int taskCount;
    }

    public static class FailedGoal {
        public final String goal;
        public final String project;

        public FailedGoal(String goal, String project) {
            this.goal = goal;
            this.project = project;
        }
    }

    public String getToolVersion() {
        return toolVersion;
    }

    public void setToolVersion(String toolVersion) {
        this.toolVersion = toolVersion;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public void setJavaVersion(String javaVersion) {
        this.javaVersion = javaVersion;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getMavenHome() {
        return mavenHome;
    }

    public void setMavenHome(String mavenHome) {
        this.mavenHome = mavenHome;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public BuildStatus getStatus() {
        return status;
    }

    public void setStatus(BuildStatus status) {
        this.status = status;
    }

    public Long getTotalMs() {
        return totalMs;
    }

    public void setTotalMs(Long totalMs) {
        this.totalMs = totalMs;
    }

    public String getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(String finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public FailedGoal getFailedGoal() {
        return failedGoal;
    }

    public void setFailedGoal(FailedGoal failedGoal) {
        this.failedGoal = failedGoal;
    }

    public boolean isParallel() {
        return parallel;
    }

    public void setParallel(boolean parallel) {
        this.parallel = parallel;
    }

    public Map<String, ModuleFacts> getModules() {
        return modules;
    }

    public ModuleFacts module(String name) {
        ModuleFacts facts = modules.get(name);
        if (facts == null) {
            facts = new ModuleFacts();
            modules.put(name, facts);
        }
        return facts;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public List<TestClassResult> getTests() {
        return tests;
    }

    public int[] getAggregateTotals() {
        return aggregateTotals;
    }

    public void markAggregateTotals(int run, int failures, int errors, int skipped) {
        this.aggregateTotals[0] = run;
        this.aggregateTotals[1] = failures;
        this.aggregateTotals[2] = errors;
        this.aggregateTotals[3] = skipped;
        this.sawAggregateTotals = true;
    }

    public boolean sawAggregateTotals() {
        return sawAggregateTotals;
    }

    public int getDownloads() {
        return downloads;
    }

    public void incrementDownloads() {
        downloads++;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
