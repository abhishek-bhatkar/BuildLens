package io.buildlens.core.model;

import com.google.gson.annotations.SerializedName;

/**
 * A single unit of build work (e.g. one Maven plugin execution), normalized
 * across build systems. Times are offsets in milliseconds from build start.
 */
public class Task {
    @SerializedName("id")
    private int id;

    @SerializedName("plugin")
    private String plugin;

    @SerializedName("pluginVersion")
    private String pluginVersion;

    @SerializedName("goal")
    private String goal;

    @SerializedName("executionId")
    private String executionId;

    @SerializedName("module")
    private String module;

    @SerializedName("category")
    private Category category;

    @SerializedName("startMs")
    private Long startMs;

    @SerializedName("endMs")
    private Long endMs;

    @SerializedName("durationMs")
    private Long durationMs;

    @SerializedName("status")
    private String status;

    public Task() {
    }

    public Task(int id, String plugin, String pluginVersion, String goal,
                String executionId, String module, Category category) {
        this.id = id;
        this.plugin = plugin;
        this.pluginVersion = pluginVersion;
        this.goal = goal;
        this.executionId = executionId;
        this.module = module;
        this.category = category;
    }

    public int getId() {
        return id;
    }

    public String getPlugin() {
        return plugin;
    }

    public String getPluginVersion() {
        return pluginVersion;
    }

    public String getGoal() {
        return goal;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getModule() {
        return module;
    }

    public Category getCategory() {
        return category;
    }

    public Long getStartMs() {
        return startMs;
    }

    public void setStartMs(Long startMs) {
        this.startMs = startMs;
    }

    public Long getEndMs() {
        return endMs;
    }

    public void setEndMs(Long endMs) {
        this.endMs = endMs;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /** Stable identity used to match tasks across two builds. */
    public String key() {
        return module + "#" + plugin + ":" + goal;
    }

    /** Human-readable label, e.g. "module-a : compile". */
    public String label() {
        String task = (plugin == null ? goal : shortenPlugin(plugin) + ":" + goal);
        return module == null ? task : module + " : " + task;
    }

    private static String shortenPlugin(String plugin) {
        if (plugin.startsWith("maven-") && plugin.endsWith("-plugin")) {
            return plugin.substring("maven-".length(), plugin.length() - "-plugin".length());
        }
        return plugin;
    }
}
