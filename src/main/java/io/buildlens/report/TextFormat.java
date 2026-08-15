package io.buildlens.report;

/** Terminal text formatting helpers: durations, percentages, bars, padding. */
public final class TextFormat {

    private static final String BAR_BLOCK = "█";
    private static final char SPACE = ' ';

    private TextFormat() {
    }

    /** Human duration: 0.9s, 31s, 3m 47s, 1h 02m. */
    public static String duration(long ms) {
        if (ms < 0) {
            return "n/a";
        }
        if (ms < 10_000) {
            return String.format("%.1fs", ms / 1000.0);
        }
        if (ms < 60_000) {
            return String.format("%ds", Math.round(ms / 1000.0));
        }
        long totalSeconds = Math.round(ms / 1000.0);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%dh %02dm", hours, minutes);
        }
        return String.format("%dm %02ds", minutes, seconds);
    }

    /** Signed duration: +38s, -1.2s. */
    public static String signedDuration(long ms) {
        String sign = ms > 0 ? "+" : "";
        return sign + duration(Math.abs(ms));
    }

    /** Signed percent with no decimals: +20%, -3%. */
    public static String signedPercent(double percent) {
        String sign = percent > 0 ? "+" : "";
        return String.format("%s%.0f%%", sign, percent);
    }

    /** Percent share of a total, one decimal: 47.2%. */
    public static String percentOf(long part, long total) {
        if (total <= 0) {
            return "0%";
        }
        return String.format("%.1f%%", part * 100.0 / total);
    }

    /** Horizontal bar of the given width scaled against maxValue. */
    public static String bar(long value, long maxValue, int width) {
        if (value <= 0 || maxValue <= 0 || width <= 0) {
            return "";
        }
        int blocks = (int) Math.round((double) value / maxValue * width);
        blocks = Math.max(1, Math.min(width, blocks));
        StringBuilder sb = new StringBuilder(blocks);
        for (int i = 0; i < blocks; i++) {
            sb.append(BAR_BLOCK);
        }
        return sb.toString();
    }

    /** Pads to at least width characters. */
    public static String pad(String text, int width) {
        String value = text == null ? "" : text;
        if (value.length() >= width) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value);
        for (int i = value.length(); i < width; i++) {
            sb.append(SPACE);
        }
        return sb.toString();
    }

    /** Fixed-width right-aligned text. */
    public static String padLeft(String text, int width) {
        String value = text == null ? "" : text;
        if (value.length() >= width) {
            return value;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = value.length(); i < width; i++) {
            sb.append(SPACE);
        }
        return sb.append(value).toString();
    }

    public static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
