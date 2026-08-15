package io.buildlens.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextFormatTest {

    @Test
    void formatsDurationsLikeTheSpec() {
        assertEquals("0.9s", TextFormat.duration(940));
        assertEquals("8.2s", TextFormat.duration(8200));
        assertEquals("31s", TextFormat.duration(31_200));
        assertEquals("59s", TextFormat.duration(59_400));
        assertEquals("1m 00s", TextFormat.duration(60_000));
        assertEquals("3m 47s", TextFormat.duration(227_000));
        assertEquals("1h 02m", TextFormat.duration(3_720_000));
        assertEquals("n/a", TextFormat.duration(-1));
    }

    @Test
    void formatsSignedValues() {
        assertEquals("+38s", TextFormat.signedDuration(38_000));
        assertEquals("-1.2s", TextFormat.signedDuration(-1200));
        assertEquals("+20%", TextFormat.signedPercent(20.0));
        assertEquals("-3%", TextFormat.signedPercent(-3.2));
        assertEquals("0%", TextFormat.signedPercent(0));
    }

    @Test
    void formatsSharesAndBars() {
        assertEquals("47.2%", TextFormat.percentOf(4720, 10000));
        assertEquals("0%", TextFormat.percentOf(10, 0));

        assertEquals("████████", TextFormat.bar(800, 1000, 8));
        assertEquals("█", TextFormat.bar(1, 1000, 8));
        assertEquals("", TextFormat.bar(0, 1000, 8));
        assertEquals(22, TextFormat.bar(500, 500, 22).length());
    }

    @Test
    void padsColumns() {
        assertEquals("abc     ", TextFormat.pad("abc", 8));
        assertEquals("abcdef", TextFormat.pad("abcdef", 3));
        assertEquals("     abc", TextFormat.padLeft("abc", 8));
        assertEquals("────", TextFormat.repeat('─', 4));
    }
}
