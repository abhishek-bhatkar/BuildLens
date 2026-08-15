package io.buildlens.cli;

import io.buildlens.core.BuildProvider;
import io.buildlens.core.BuildResult;
import io.buildlens.core.CaptureContext;
import io.buildlens.core.model.Build;
import io.buildlens.providers.maven.MavenStreamProvider;
import io.buildlens.report.ConsoleReporter;
import io.buildlens.storage.BuildStorage;
import io.buildlens.storage.StorageLocations;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
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
        Path versionFile = null;
        Path home = StorageLocations.defaultRoot();

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
            } else if ("--version-file".equals(arg) && i + 1 < args.length) {
                versionFile = Paths.get(args[++i]);
            } else if ("--home".equals(arg) && i + 1 < args.length) {
                home = Paths.get(args[++i]);
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
                System.nanoTime());
        BuildResult result;
        try {
            result = provider.capture(in, context, out);
        } catch (Exception e) {
            err.print("BuildLens failed while capturing the build stream: ");
            err.println(e);
            return EXIT_BUILDLENS_ERROR;
        }

        BuildStorage storage = new BuildStorage(home);
        Path saved = storage.save(result);
        long totalMs = Math.round((System.nanoTime() - captureStart) / 1_000_000.0);
        long analysisMs = Math.max(0L, totalMs - result.getBuild().getDurationMs());
        out.print(reporter.runSummary(result.getBuild(), saved, analysisMs));
        // The wrapped build's exit status is owned by the launcher, not by us.
        return 0;
    }

    // ------------------------------------------------------------------ report

    private int report(List<String> ids, Path home, PrintStream out, PrintStream err)
            throws BuildStorage.StorageException {
        BuildStorage storage = new BuildStorage(home);
        String id = ids.isEmpty() ? storage.latestId() : storage.resolveId(ids.get(0));
        if (id == null) {
            if (ids.isEmpty()) {
                err.println("No captured builds yet. Run: buildlens mvn clean package");
            } else {
                err.print("No build matches id '");
                err.print(ids.get(0));
                err.println("'. Try: buildlens list");
            }
            return EXIT_NOT_FOUND;
        }
        Build build = storage.load(id);
        if (build == null) {
            err.print("Could not read build ");
            err.println(id);
            return EXIT_NOT_FOUND;
        }
        out.print(reporter.fullReport(build));
        return 0;
    }

    // ------------------------------------------------------------------ compare

    private int compare(List<String> ids, Path home, PrintStream out, PrintStream err)
            throws BuildStorage.StorageException {
        BuildStorage storage = new BuildStorage(home);
        List<String> all = storage.listIds();

        String currentId;
        String previousId;
        if (ids.size() >= 2) {
            previousId = storage.resolveId(ids.get(0));
            currentId = storage.resolveId(ids.get(1));
        } else if (ids.size() == 1) {
            currentId = storage.resolveId(ids.get(0));
            previousId = latestBefore(all, currentId);
        } else {
            currentId = all.isEmpty() ? null : all.get(all.size() - 1);
            previousId = all.size() < 2 ? null : all.get(all.size() - 2);
        }
        if (currentId == null) {
            err.println("No captured builds yet. Run: buildlens mvn clean package");
            return EXIT_NOT_FOUND;
        }
        if (previousId == null) {
            err.println("Only one captured build exists; need at least two to compare.");
            return EXIT_NOT_FOUND;
        }

        Build previous = storage.load(previousId);
        Build current = storage.load(currentId);
        out.print(reporter.compare(new io.buildlens.analysis.BuildComparison()
                .compare(previous, current)));
        return 0;
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
            Build build = storage.load(id);
            if (build != null) {
                builds.add(build);
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
