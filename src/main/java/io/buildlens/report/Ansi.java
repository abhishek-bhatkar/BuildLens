package io.buildlens.report;

/** Minimal ANSI styling, auto-disabled when not attached to a terminal. */
public final class Ansi {

    public enum Style {
        RESET("\u001b[0m"),
        BOLD("\u001b[1m"),
        DIM("\u001b[2m"),
        GREEN("\u001b[32m"),
        RED("\u001b[31m"),
        YELLOW("\u001b[33m"),
        CYAN("\u001b[36m");

        private final String code;

        Style(String code) {
            this.code = code;
        }
    }

    private static final Boolean ENABLED = detect();

    private Ansi() {
    }

    private static boolean detect() {
        if (System.getenv("NO_COLOR") != null) {
            return false;
        }
        // System.console() is null when stdout is redirected or not a tty,
        // which keeps piped/captured output clean plain text.
        return System.console() != null;
    }

    public static String apply(Style style, String text) {
        if (!ENABLED || text == null) {
            return text;
        }
        return style.code + text + Style.RESET.code;
    }

    public static boolean enabled() {
        return ENABLED;
    }
}
