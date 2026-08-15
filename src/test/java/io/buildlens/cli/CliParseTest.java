package io.buildlens.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Command-line argument parsing (spec §22: "command parsing"). */
class CliParseTest {

    @Test
    void positionalArgsSkipFlagValues() {
        List<String> positional = Main.positional(
                new String[]{"report", "2026-08-15T22", "--home", "/tmp/x"});
        assertEquals(1, positional.size());
        assertEquals("2026-08-15T22", positional.get(0));
    }

    @Test
    void noPositionalArgsYieldsEmptyList() {
        assertTrue(Main.positional(new String[]{"list", "--home", "/tmp/x"}).isEmpty());
    }

    @Test
    void homeDirReadsFlagValue() {
        Path home = Main.homeDir(new String[]{"report", "--home", "/tmp/buildlens-home"});
        assertEquals(Paths.get("/tmp/buildlens-home"), home);
    }

    @Test
    void homeDirDefaultsToSystemLocation() {
        // No --home flag: falls back to $BUILDLENS_HOME or ~/.buildlens.
        Path home = Main.homeDir(new String[]{"list"});
        assertTrue(home.toString().endsWith(".buildlens")
                || System.getenv("BUILDLENS_HOME") != null);
    }
}
