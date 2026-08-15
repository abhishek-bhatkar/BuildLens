package io.buildlens.core;

import io.buildlens.core.model.Build;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

/**
 * Abstraction over a concrete build system (Maven today, Gradle/npm later).
 *
 * <p>Execution model: the {@code buildlens} launcher runs the user's build
 * command and streams its console output into BuildLens. A provider
 * <b>captures</b> that stream — echoing it through unchanged while parsing
 * it — and normalizes it into the common {@link Build} model. Providers never
 * modify the build, never touch the project and never affect its exit
 * code.</p>
 */
public interface BuildProvider {

    /** Canonical tool name, e.g. "maven". */
    String toolName();

    /** True if this provider handles the requested tool token (e.g. "mvn"). */
    boolean handles(String tool);

    /**
     * Captures one build execution from its output stream.
     *
     * <p>The stream content is echoed line-by-line to {@code console} exactly
     * as received (the user always sees their build's real output), while the
     * provider parses it into the normalized model. Timings are taken from
     * the wall-clock arrival time of each line.</p>
     *
     * @param output    the build's merged console output
     * @param context   invocation metadata (command line, optional exit-code
     *                  hint, optional tool-version file)
     * @param console   passthrough sink for the original output
     */
    BuildResult capture(InputStream output, CaptureContext context, PrintStream console)
            throws IOException;
}
