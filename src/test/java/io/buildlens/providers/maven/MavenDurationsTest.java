package io.buildlens.providers.maven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MavenDurationsTest {

    @Test
    void parsesSeconds() {
        assertEquals(Long.valueOf(1272L), MavenDurations.parseMs("1.272 s"));
        assertEquals(Long.valueOf(973L), MavenDurations.parseMs("0.973 s"));
        assertEquals(Long.valueOf(47200L), MavenDurations.parseMs("47.2 s"));
        assertEquals(Long.valueOf(47200L), MavenDurations.parseMs("47.2"));
    }

    @Test
    void parsesMinutes() {
        assertEquals(Long.valueOf(227000L), MavenDurations.parseMs("03:47 min"));
        assertEquals(Long.valueOf(60000L), MavenDurations.parseMs("01:00 min"));
        assertEquals(Long.valueOf(227000L), MavenDurations.parseMs("03:47"));
    }

    @Test
    void parsesHours() {
        assertEquals(Long.valueOf(3723000L), MavenDurations.parseMs("01:02:03 h"));
        assertEquals(Long.valueOf(3723000L), MavenDurations.parseMs("01:02:03"));
    }

    @Test
    void parsesBracketedReactorSummaryForm() {
        assertEquals(Long.valueOf(117L), MavenDurations.parseBracketedMs("[  0.117 s]"));
        assertEquals(Long.valueOf(980L), MavenDurations.parseBracketedMs("[  0.980 s]"));
        assertEquals(Long.valueOf(1272L), MavenDurations.parseBracketedMs("[ 1.272 s]"));
    }

    @Test
    void rejectsGarbage() {
        assertNull(MavenDurations.parseMs(null));
        assertNull(MavenDurations.parseMs(""));
        assertNull(MavenDurations.parseMs("soon"));
        assertNull(MavenDurations.parseMs("03:47"));
        assertNull(MavenDurations.parseBracketedMs(""));
    }
}
