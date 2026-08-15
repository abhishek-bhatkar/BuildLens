package io.buildlens.providers.maven;

/**
 * Parsing and formatting of Maven's duration notations.
 *
 * <p>Maven prints durations such as {@code 1.272 s}, {@code 03:47 min} or
 * {@code 01:02:03 h}; the per-module reactor summary uses {@code [  0.980 s]}.</p>
 */
public final class MavenDurations {

    private MavenDurations() {
    }

    /**
     * Parses a Maven duration string into milliseconds.
     *
     * @return milliseconds, or null if the text is not a recognized duration
     */
    public static Long parseMs(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.isEmpty()) {
            return null;
        }

        // 01:02:03 h / 01:02:03
        String h = stripUnit(t, "h");
        if (h != null && h.contains(":")) {
            String[] parts = h.split(":");
            if (parts.length == 3) {
                Long ms = hms(parts[0], parts[1], parts[2]);
                if (ms != null) {
                    return ms;
                }
            }
            return null;
        }

        // 03:47 min / 03:47
        String min = stripUnit(t, "min");
        if (min != null && min.contains(":")) {
            String[] parts = min.split(":");
            if (parts.length == 2) {
                Long ms = hms("0", parts[0], parts[1]);
                if (ms != null) {
                    return ms;
                }
            }
            return null;
        }

        // 1.272 s / 1.272
        String s = stripUnit(t, "s");
        if (s != null && !s.contains(":")) {
            try {
                return (long) Math.round(Double.parseDouble(s) * 1000.0);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    /** Parses the bracketed reactor-summary form, e.g. "[  0.980 s]". */
    public static Long parseBracketedMs(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.startsWith("[") && t.endsWith("]")) {
            return parseMs(t.substring(1, t.length() - 1));
        }
        return parseMs(t);
    }

    private static String stripUnit(String t, String unit) {
        if (t.endsWith(unit)) {
            String stripped = t.substring(0, t.length() - unit.length()).trim();
            return stripped.isEmpty() ? null : stripped;
        }
        // Unit-less variant is still accepted for the h/m/s shapes.
        return t.matches("[\\d:.]+") ? t : null;
    }

    private static Long hms(String h, String m, String s) {
        try {
            long hours = Long.parseLong(h.trim());
            long minutes = Long.parseLong(m.trim());
            double seconds = Double.parseDouble(s.trim());
            return hours * 3600000L + minutes * 60000L + (long) Math.round(seconds * 1000.0);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
