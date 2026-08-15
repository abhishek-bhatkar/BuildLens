package io.buildlens.core.model;

import com.google.gson.annotations.SerializedName;

/** Environment metadata captured from the build tool's own banner output. */
public class Environment {
    @SerializedName("javaVersion")
    private String javaVersion;

    @SerializedName("os")
    private String os;

    @SerializedName("toolHome")
    private String toolHome;

    @SerializedName("locale")
    private String locale;

    public Environment() {
    }

    public Environment(String javaVersion, String os, String toolHome, String locale) {
        this.javaVersion = javaVersion;
        this.os = os;
        this.toolHome = toolHome;
        this.locale = locale;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public String getOs() {
        return os;
    }

    public String getToolHome() {
        return toolHome;
    }

    public String getLocale() {
        return locale;
    }
}
