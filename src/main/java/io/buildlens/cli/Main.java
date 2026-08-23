package io.buildlens.cli;

import io.buildlens.core.BuildProvider;
import io.buildlens.core.BuildResult;
import io.buildlens.core.CaptureContext;
import io.buildlens.core.model.Build;
import io.buildlens.core.model.BuildStatus;
import io.buildlens.providers.maven.MavenStreamProvider;
import io.buildlens.report.ConsoleReporter;
import io.buildlens.storage.BuildStorage;
import io.buildlens.storage.StorageLocations;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * BuildLens CLI.
 *
 * <p>User-facing entry point is the {@code buildlens} launcher, which runs
 * the build and pipes its output into {@code buildlens ingest}. The other
 * subcommands operate on captured history:</p>
 *
 * <pre>
 *   buildlens mvn clean package    (launcher: run build + capture)
 *   buildlens ingest [flags]       (read a build stream from stdin)
 *   buildlens report [id]
 *   buildlens compare [a b]
 *   buildlens list
 * </pre>
 */
public final class Main {

    static final String VERSION = "0.1.0";

    /** Exit code when BuildLens itself fails (distinct from build failure). */
    static final int EXIT_BUILDLENS_ERROR = 2;
    /** Exit code for user-facing problems (unknown build, nothing to compare). */
    static final int EXIT_NOT_FOUND = 1;

    public static void main(String[] args) {
        PrintStream out = utf8SystemOut();
        Main cli = new Main();
        int code = cli.execute(args, System.in, out, out);
        out.flush();
        System.exit(code);
    }

    private final ConsoleReporter reporter = new ConsoleReporter();

    /** Executes one command; returns the process exit code. Test-friendly. */
    int execute(String[] args, InputStream in, PrintStream out, PrintStream err) {
        if (args == null || args.length == 0) {
            out.print(usage());
            return 0;
        }
        String command = args[0];
        try {
            if ("ingest".equals(command)) {
                return ingest(Arrays.copyOfRange(args, 1, args.length), in, out, err);
            }
            if ("report".equals(command)) {
                return report(positional(args), homeDir(args), out, err);
            }
            if ("compare".equals(command)) {
                return compare(positional(args), homeDir(args), out, err);
            }
            if ("list".equals(command)) {
                return list(homeDir(args), out, err);
            }
            if ("help".equals(command) || "--help".equals(command) || "-h".equals(command)) {
                out.print(usage());
                return 0;
            }
            if ("version".equals(command) || "--version".equals(command)) {
                out.println("buildlens " + VERSION);
                return 0;
            }
            err.print("Unknown command: ");
            err.println(command);
            err.print(usage());
            return EXIT_BUILDLENS_ERROR;
        } catch (BuildStorage.StorageException e) {
            err.print("BuildLens error: ");
            err.println(e.getMessage());
            return EXIT_BUILDLENS_ERROR;
        } catch (Exception e) {
            // Analysis problems must never be confused with build problems.
            err.print("BuildLens internal error: ");
            err.println(e);
            return EXIT_BUILDLENS_ERROR;
        }
    }

    // ------------------------------------------------------------------ ingest

    private int ingest(String[] args, InputStream in, PrintStream out, PrintStream err)
            throws BuildStorage.StorageException {
        String command = null;
        String tool = "mvn";
        Integer exitCode = null;
        Path exitCodeFile = null;
        Path versionFile = null;
        Path home = StorageLocations.defaultRoot();
        String projectDir = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--command".equals(arg) && i + 1 < args.length) {
                command = args[++i];
            } else if ("--tool".equals(arg) && i + 1 < args.length) {
                tool = args[++i];
            } else if ("--exit-code".equals(arg) && i + 1 < args.length) {
                try {
                    exitCode = Integer.valueOf(args[++i]);
                } catch (NumberFormatException e) {
                    exitCode = null;
                }
            } else if ("--exit-code-file".equals(arg) && i + 1 < args.length) {
                exitCodeFile = Paths.get(args[++i]);
            } else if ("--version-file".equals(arg) && i + 1 < args.length) {
                versionFile = Paths.get(args[++i]);
            } else if ("--home".equals(arg) && i + 1 < args.length) {
                home = Paths.get(args[++i]);
            } else if ("--project-dir".equals(arg) && i + 1 < args.length) {
                projectDir = args[++i];
            }
        }
        if (command == null) {
            err.println("ingest requires --command <command line>");
            return EXIT_BUILDLENS_ERROR;
        }

        BuildProvider provider = findProvider(tool);
        if (provider == null) {
            err.print("No BuildLens provider handles tool '");
            err.print(tool);
            err.println("'. Supported: mvn");
            return EXIT_BUILDLENS_ERROR;
        }

        long captureStart = System.nanoTime();
        CaptureContext context = new CaptureContext(command, exitCode, versionFile,
                System.nanoTime(), projectDir);
        BuildResult result;
        try {
            result = provider.capture(in, context, out);
        } catch (Exception e) {
            err.print("BuildLens failed while capturing the build stream: ");
            err.println(e);
            return EXIT_BUILDLENS_ERROR;
        }

        if (exitCode == null && exitCodeFile != null) {
            exitCode = readExitCode(exitCodeFile);
        }
        if (exitCode != null) {
            result.getBuild().setExitCode(exitCode);
            if (result.getBuild().getStatus() == BuildStatus.UNKNOWN) {
                result.getBuild().setStatus(exitCode == 0
                        ? BuildStatus.SUCCESS
                        : BuildStatus.FAILURE);
            }
        }

        long totalMs = Math.round((System.nanoTime() - captureStart) / 1_000_000.0);
        long analysisMs = Math.max(0L, totalMs - result.getBuild().getDurationMs());
        result.getBuild().setAnalysisMs(analysisMs);

        BuildStorage storage = new BuildStorage(home);
        Path saved = storage.save(result);
        out.print(reporter.runSummary(result.getBuild(), saved, analysisMs));
        // The wrapped build's exit status is owned by the launcher, not by us.
        return 0;
    }

    private static Integer readExitCode(Path exitCodeFile) {
        try {
            List<String> lines = Files.readAllLines(exitCodeFile, StandardCharsets.UTF_8);
            return lines.isEmpty() ? null : Integer.valueOf(lines.get(0).trim());
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ report

    private int report(List<String> ids, Path home, PrintStream out, PrintStream err)
            throws BuildStorage.StorageException {
        BuildStorage storage = new BuildStorage(home);
        String id = null;
        Build build = null;
        if (ids.isEmpty()) {
            // Default: latest readable report — corrupt files are skipped.
            List<String> all = storage.listIds();
            for (int i = all.size() - 1; i >= 0 && build == null; i--) {
                build = storage.loadOrNull(all.get(i));
                if (build == null) {
                    warnSkipped(err, all.get(i));
                }
            }
            if (build != null) {
                id = build.getBuildId();
            }
        } else {
            id = storage.resolveId(ids.get(0));
            if (id != null) {
                try {
                    build = storage.load(id); // explicit request: strict
                } catch (BuildStorage.StorageException e) {
                    err.print("Could not show report ");
                    err.print(id);
                    err.print(": ");
                    err.println(e.getMessage());
                    return EXIT_NOT_FOUND;
                }
            }
        }
        if (id == null || build == null) {
            if (ids.isEmpty()) {
                err.println("No captured builds yet. Run: buildlens mvn clean package");
            } else {
                err.print("No build matches id '");
                err.print(ids.get(0));
                err.println("'. Try: buildlens list");
            }
            return EXIT_NOT_FOUND;
        }
        out.print(reporter.fullReport(build));
        return 0;
    }

    private static void warnSkipped(PrintStream err, String id) {
        err.print("Warning: skipping corrupt report ");
        err.println(id);
    }

    // ------------------------------------------------------------------ compare

    private int compare(List<String> ids, Path home, PrintStream out, PrintStream err)
            throws BuildStorage.StorageException {
        BuildStorage storage = new BuildStorage(home);

        String currentId;
        String previousId;
        if (ids.size() >= 2) {
            previousId = storage.resolveId(ids.get(0));
            currentId = storage.resolveId(ids.get(1));
        } else if (ids.size() == 1) {
            currentId = storage.resolveId(ids.get(0));
            previousId = latestBefore(storage.listIds(), currentId);
        } else {
            String[] pair = selectDefaultPair(storage, err);
            previousId = pair[0];
            currentId = pair[1];
        }
        if (currentId == null) {
            err.println("No captured builds yet. Run: buildlens mvn clean package");
            return EXIT_NOT_FOUND;
        }
        if (previousId == null) {
            err.println("No previous build of this project to compare against. "
                    + "Capture at least two builds of the same project, or pass "
                    + "two ids explicitly (buildlens compare <a> <b>).");
            return EXIT_NOT_FOUND;
        }

        Build previous = storage.load(previousId);
        Build current = storage.load(currentId);
        out.print(reporter.compare(new io.buildlens.analysis.BuildComparison()
                .compare(previous, current)));
        return 0;
    }

    /**
     * Default pairing for {@code buildlens compare}: the current build is the
     * most recent readable report; the previous is the most recent readable
     * report from the <b>same project</b> (matching project directory), so
     * histories of different projects are never mixed into one delta.
     * Corrupt files are walked past with a warning. Returns
     * {previousId, currentId}; either may be null when no match exists.
     */
    static String[] selectDefaultPair(BuildStorage storage, PrintStream err)
            throws BuildStorage.StorageException {
        List<String> all = storage.listIds();
        Build current = null;
        String currentId = null;
        int cursor = all.size() - 1;
        while (cursor >= 0 && current == null) {
            String candidate = all.get(cursor--);
            Build build = storage.loadOrNull(candidate);
            if (build == null) {
                warnSkipped(err, candidate);
            } else {
                current = build;
                currentId = candidate;
            }
        }
        String previousId = null;
        while (cursor >= 0 && previousId == null) {
            String candidate = all.get(cursor--);
            Build build = storage.loadOrNull(candidate);
            if (build == null) {
                warnSkipped(err, candidate);
            } else if (sameProject(current, build)) {
                previousId = candidate;
            }
        }
        return new String[]{previousId, currentId};
    }

    /** Null-safe project identity: builds without a recorded project
     *  directory (legacy reports) match anything. */
    private static boolean sameProject(Build a, Build b) {
        if (a == null || b == null) {
            return false;
        }
        String dirA = a.getProjectDir();
        String dirB = b.getProjectDir();
        return dirA == null || dirB == null || dirA.equals(dirB);
    }

    private static String latestBefore(List<String> ids, String currentId) {
        String previous = null;
        if (currentId == null) {
            return null;
        }
        for (String id : ids) {
            if (id.equals(currentId)) {
                break;
            }
            previous = id;
        }
        return previous;
    }

    // ------------------------------------------------------------------ list

    private int list(Path home, PrintStream out, PrintStream err)
            throws BuildStorage.StorageException {
        BuildStorage storage = new BuildStorage(home);
        List<Build> builds = new ArrayList<Build>();
        for (String id : storage.listIds()) {
            Build build = storage.loadOrNull(id);
            if (build != null) {
                builds.add(build);
            } else {
                warnSkipped(err, id);
            }
        }
        out.print(reporter.list(builds));
        return 0;
    }

    // ------------------------------------------------------------------ helpers

    private static BuildProvider findProvider(String tool) {
        List<BuildProvider> providers = new ArrayList<BuildProvider>();
        providers.add(new MavenStreamProvider());
        for (BuildProvider provider : providers) {
            if (provider.handles(tool)) {
                return provider;
            }
        }
        return null;
    }

    static List<String> positional(String[] args) {
        List<String> positional = new ArrayList<String>();
        for (int i = 1; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                positional.add(args[i]);
            } else {
                i++; // skip flag value
            }
        }
        return positional;
    }

    static Path homeDir(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("--home".equals(args[i]) && i + 1 < args.length) {
                return Paths.get(args[i + 1]);
            }
        }
        return StorageLocations.defaultRoot();
    }

    private static String usage() {
        return "BuildLens " + VERSION + " — understand where your build time goes\n"
                + "\n"
                + "Usage:\n"
                + "  buildlens mvn <args...>     Run a Maven build and capture it\n"
                + "  buildlens report [id]       Show a captured build report (default: latest)\n"
                + "  buildlens compare [a b]     Compare two builds (default: last two)\n"
                + "  buildlens list              List captured builds\n"
                + "  buildlens help              Show this help\n"
                + "  buildlens version           Show version\n"
                + "\n"
                + "Options:\n"
                + "  --home <dir>    Storage root (default: $BUILDLENS_HOME or ~/.buildlens)\n"
                + "\n"
                + "Examples:\n"
                + "  buildlens mvn clean package\n"
                + "  buildlens mvn clean package -DskipTests\n"
                + "  buildlens report\n"
                + "  buildlens compare\n";
    }

    private static PrintStream utf8SystemOut() {
        try {
            return new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return System.out;
        }
    }
}
