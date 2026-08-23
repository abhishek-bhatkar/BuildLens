package io.buildlens.core;

import java.nio.file.Path;

/** Metadata about the build execution being captured. */
public final class CaptureContext {

    private final String command;
    private final Integer exitCode;
    private final Path versionFile;
    private final long startNanos;
    private final String projectDir;

    /**
     * @param command     the full command line the user asked to run
     * @param exitCode    the build's exit code when already known, else null
     * @param versionFile optional file containing the tool's version banner
     *                    ({@code mvn -version} output) written by the launcher
     * @param startNanos  {@link System#nanoTime()} taken at build start
     */
    public CaptureContext(String command, Integer exitCode, Path versionFile, long startNanos) {
        this(command, exitCode, versionFile, startNanos, null);
    }

    /**
     * @param projectDir  working directory of the build, used to pair reports
     *                    from the same project during comparison
     */
    public CaptureContext(String command, Integer exitCode, Path versionFile, long startNanos,
                          String projectDir) {
        this.command = command;
        this.exitCode = exitCode;
        this.versionFile = versionFile;
        this.startNanos = startNanos;
        this.projectDir = projectDir;
    }

    public String getCommand() {
        return command;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public Path getVersionFile() {
        return versionFile;
    }

    public long getStartNanos() {
        return startNanos;
    }

    public String getProjectDir() {
        return projectDir;
    }
}
