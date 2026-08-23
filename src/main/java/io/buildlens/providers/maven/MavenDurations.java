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

        boolean hoursUnit = t.endsWith("h");
        boolean minutesUnit = t.endsWith("min");
        boolean secondsUnit = t.endsWith("s") && !minutesUnit;
        String body = stripSuffix(t);

        if (hoursUnit) {
            // 01:02:03 h
            Long ms = hms(body);
            return ms;
        }
        if (minutesUnit) {
            // 03:47 min
            return hms("0:" + body);
        }
        if (secondsUnit) {
            // 1.272 s
            return seconds(body);
        }

        // Unit-less forms: 01:02:03 means hours, 03:47 means minutes,
        // 1.272 means seconds (the reactor summary's bracketed form lands here).
        if (body.matches("\\d{1,2}:\\d{2}:\\d{2}(\\.\\d+)?")) {
            return hms(body);
        }
        if (body.matches("\\d{1,2}:\\d{2}(\\.\\d+)?")) {
            return hms("0:" + body);
        }
        if (body.matches("[\\d.]+")) {
            return seconds(body);
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

    private static String stripSuffix(String t) {
        if (t.endsWith("min")) {
            return t.substring(0, t.length() - 3).trim();
        }
        if (t.endsWith("h") || t.endsWith("s")) {
            return t.substring(0, t.length() - 1).trim();
        }
        return t;
    }

    private static Long seconds(String body) {
        try {
            return (long) Math.round(Double.parseDouble(body) * 1000.0);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long hms(String body) {
        String[] parts = body.split(":");
        if (parts.length != 3) {
            return null;
        }
        try {
            long hours = Long.parseLong(parts[0].trim());
            long minutes = Long.parseLong(parts[1].trim());
            double seconds = Double.parseDouble(parts[2].trim());
            return hours * 3600000L + minutes * 60000L + (long) Math.round(seconds * 1000.0);
        } catch (NumberFormatException e) {
            return null;
        }
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
