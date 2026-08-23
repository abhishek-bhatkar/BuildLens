package io.buildlens.analysis;

import io.buildlens.core.model.Build;
import io.buildlens.core.model.Category;
import io.buildlens.core.model.Module;
import io.buildlens.core.model.Task;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildComparisonTest {

    private static Build build(String id, long durationMs, long compileMs, long testMs,
                               long moduleAMs, long moduleBMs) {
        Build build = new Build();
        build.setBuildId(id);
        build.setDurationMs(durationMs);
        List<Task> tasks = new ArrayList<Task>();
        tasks.add(task("module-a", "compiler", "compile", Category.COMPILE, compileMs));
        tasks.add(task("module-a", "surefire", "test", Category.TEST, testMs));
        build.getTasks().addAll(tasks);
        build.getModules().add(new Module("module-a", moduleAMs, 2, "SUCCESS"));
        build.getModules().add(new Module("module-b", moduleBMs, 0, "SUCCESS"));
        return build;
    }

    private static Task task(String module, String plugin, String goal, Category category, long ms) {
        Task task = new Task(1, plugin, "3.0", goal, "default", module, category);
        task.setDurationMs(ms);
        return task;
    }

    @Test
    void detectsRegressionWithDeltas() {
        Build previous = build("prev", 189_000, 68_000, 91_000, 80_000, 61_000);
        Build current = build("cur", 227_000, 91_000, 107_000, 101_000, 63_000);

        ComparisonResult result = new BuildComparison().compare(previous, current);

        assertEquals(ComparisonResult.Verdict.REGRESSION, result.getVerdict());
        assertEquals(38_000L, result.getDurationDeltaMs());
        assertTrue(result.getDurationDeltaPercent() > 20.0 && result.getDurationDeltaPercent() < 20.2);

        assertEquals(2, result.getCategoryRows().size());
        ComparisonResult.DeltaRow compile = findRow(result.getCategoryRows(), "Compile");
        assertEquals(23_000L, compile.deltaMs);

        // module-a regressed by 21s, module-b by 2s — largest first
        assertEquals("module-a", result.getModuleDeltas().get(0).label);
        assertEquals(21_000L, result.getModuleDeltas().get(0).deltaMs);

        assertEquals(2, result.getTaskRegressions().size());
        assertEquals(23_000L, result.getTaskRegressions().get(0).deltaMs);
        assertTrue(result.getTaskImprovements().isEmpty());
    }

    @Test
    void smallDeltasCountAsUnchanged() {
        Build previous = build("prev", 60_000, 20_000, 30_000, 25_000, 25_000);
        Build current = build("cur", 60_500, 20_100, 30_200, 25_100, 25_200);
        assertEquals(ComparisonResult.Verdict.UNCHANGED,
                new BuildComparison().compare(previous, current).getVerdict());
    }

    @Test
    void improvementsAreSurfaced() {
        Build previous = build("prev", 100_000, 50_000, 40_000, 60_000, 30_000);
        Build current = build("cur", 70_000, 30_000, 30_000, 40_000, 20_000);
        ComparisonResult result = new BuildComparison().compare(previous, current);
        assertEquals(ComparisonResult.Verdict.IMPROVEMENT, result.getVerdict());
        assertEquals(2, result.getTaskImprovements().size());
        assertEquals(-20_000L, result.getTaskImprovements().get(0).deltaMs);
    }

    @Test
    void differentProjectsProduceAContextWarning() {
        Build previous = build("prev", 100_000, 50_000, 40_000, 60_000, 30_000);
        previous.setProjectDir("/repo/alpha");
        Build current = build("cur", 110_000, 55_000, 40_000, 65_000, 30_000);
        current.setProjectDir("/repo/beta");

        String warning = new BuildComparison().compare(previous, current).getContextWarning();
        assertTrue(warning != null && warning.contains("different projects"));
        assertTrue(warning.contains("/repo/alpha"));
        assertTrue(warning.contains("/repo/beta"));
    }

    @Test
    void differentCommandsProduceAContextWarning() {
        Build previous = build("prev", 100_000, 50_000, 40_000, 60_000, 30_000);
        previous.setCommand("mvn clean package");
        previous.setProjectDir("/repo/alpha");
        Build current = build("cur", 70_000, 50_000, 10_000, 60_000, 30_000);
        current.setCommand("mvn clean package -DskipTests");
        current.setProjectDir("/repo/alpha");

        String warning = new BuildComparison().compare(previous, current).getContextWarning();
        assertTrue(warning != null && warning.contains("Commands differ"));
    }

    @Test
    void sameContextHasNoWarning() {
        Build previous = build("prev", 100_000, 50_000, 40_000, 60_000, 30_000);
        previous.setProjectDir("/repo/alpha");
        Build current = build("cur", 110_000, 55_000, 40_000, 65_000, 30_000);
        current.setProjectDir("/repo/alpha");
        assertNull(new BuildComparison().compare(previous, current).getContextWarning());
    }

    private static ComparisonResult.DeltaRow findRow(List<ComparisonResult.DeltaRow> rows,
                                                     String label) {
        for (ComparisonResult.DeltaRow row : rows) {
            if (row.label.equals(label)) {
                return row;
            }
        }
        throw new AssertionError("missing row: " + label);
    }
}
