package io.buildlens.core.model;

import com.google.gson.annotations.SerializedName;

/** A reactor module (Maven) or subproject, with the time the build system
 *  itself measured for it when available. */
public class Module {
    @SerializedName("name")
    private String name;

    @SerializedName("durationMs")
    private Long durationMs;

    @SerializedName("taskCount")
    private int taskCount;

    @SerializedName("status")
    private String status;

    public Module() {
    }

    public Module(String name, Long durationMs, int taskCount, String status) {
        this.name = name;
        this.durationMs = durationMs;
        this.taskCount = taskCount;
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public int getTaskCount() {
        return taskCount;
    }

    public void setTaskCount(int taskCount) {
        this.taskCount = taskCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
