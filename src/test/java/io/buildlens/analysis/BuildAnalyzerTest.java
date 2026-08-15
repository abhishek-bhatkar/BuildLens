package io.buildlens.analysis;

import io.buildlens.core.model.Build;
import io.buildlens.core.model.Category;
import io.buildlens.core.model.Module;
import io.buildlens.core.model.Task;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildAnalyzerTest {

    private static Task task(int id, String module, String plugin, String goal,
                             Category category, long durationMs) {
        Task task = new Task(id, plugin, "3.0", goal, "default", module, category);
        task.setDurationMs(durationMs);
        return task;
    }

    @Test
    void aggregatesCategoriesAndRanksTasks() {
        Build build = new Build();
        build.setDurationMs(10_000);
        List<Task> tasks = new ArrayList<Task>();
        tasks.add(task(1, "app", "surefire", "test", Category.TEST, 4000));
        tasks.add(task(2, "app", "compiler", "compile", Category.COMPILE, 2500));
        tasks.add(task(3, "app", "jar", "jar", Category.PACKAGE, 1500));
        tasks.add(task(4, "app", "clean", "clean", Category.CLEAN, 500));
        tasks.add(task(5, "app", "surefire", "test", Category.TEST, 1000));
        build.getTasks().addAll(tasks);

        Module module = new Module("app", 9500L, 5, "SUCCESS");
        build.getModules().add(module);

        BuildAnalyzer analyzer = new BuildAnalyzer();
        BuildSummary summary = analyzer.summarize(build, 3);

        Map<Category, Long> totals = summary.getCategoryTotals();
        assertEquals(Long.valueOf(5000L), totals.get(Category.TEST));
        assertEquals(Long.valueOf(2500L), totals.get(Category.COMPILE));
        assertEquals(Long.valueOf(1500L), totals.get(Category.PACKAGE));

        // insertion order of a LinkedHashMap follows descending totals
        assertEquals(Category.TEST, new ArrayList<Category>(totals.keySet()).get(0));

        assertEquals(9500L, summary.getAttributedMs());
        assertEquals(500L, summary.getOverheadMs());

        assertEquals(3, summary.getSlowestTasks().size());
        assertEquals("test", summary.getSlowestTasks().get(0).getGoal());
        assertEquals("jar", summary.getSlowestTasks().get(2).getGoal());

        assertEquals(1, summary.getModulesByDuration().size());
    }

    @Test
    void untimedTasksAreIgnoredNotFabricated() {
        Build build = new Build();
        build.setDurationMs(3000);
        Task untimed = new Task(1, "surefire", "3.0", "test", "default", "app", Category.TEST);
        build.getTasks().add(untimed);

        BuildSummary summary = new BuildAnalyzer().summarize(build);
        assertTrue(summary.getCategoryTotals().isEmpty());
        assertEquals(0L, summary.getAttributedMs());
        assertEquals(3000L, summary.getOverheadMs());
        assertTrue(summary.getSlowestTasks().isEmpty());
    }
}
