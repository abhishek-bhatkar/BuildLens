package io.buildlens.providers.maven;

import io.buildlens.core.model.Category;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps a Maven plugin execution (plugin prefix + goal) onto the common
 * {@link Category} model so reports stay build-system independent.
 */
public final class CategoryMapper {

    private static final Map<String, Category> BY_PREFIX = new HashMap<String, Category>();

    static {
        BY_PREFIX.put("clean", Category.CLEAN);
        BY_PREFIX.put("resources", Category.RESOURCES);
        BY_PREFIX.put("compiler", Category.COMPILE);
        BY_PREFIX.put("antlr", Category.COMPILE);
        BY_PREFIX.put("jaxb", Category.COMPILE);
        BY_PREFIX.put("proto", Category.COMPILE);
        BY_PREFIX.put("exec", Category.OTHER);
        BY_PREFIX.put("surefire", Category.TEST);
        BY_PREFIX.put("failsafe", Category.TEST);
        BY_PREFIX.put("smart-testing", Category.TEST);
        BY_PREFIX.put("jar", Category.PACKAGE);
        BY_PREFIX.put("war", Category.PACKAGE);
        BY_PREFIX.put("ear", Category.PACKAGE);
        BY_PREFIX.put("shade", Category.PACKAGE);
        BY_PREFIX.put("assembly", Category.PACKAGE);
        BY_PREFIX.put("spring-boot", Category.PACKAGE);
        BY_PREFIX.put("source", Category.PACKAGE);
        BY_PREFIX.put("install", Category.INSTALL);
        BY_PREFIX.put("deploy", Category.DEPLOY);
    }

    private CategoryMapper() {
    }

    public static Category map(String pluginPrefix, String goal) {
        Category byPrefix = BY_PREFIX.get(pluginPrefix);
        if (byPrefix != null) {
            return byPrefix;
        }
        if (goal != null) {
            if (goal.contains("compile")) {
                return Category.COMPILE;
            }
            if (goal.equals("test") || goal.equals("integration-test") || goal.equals("verify")) {
                return Category.TEST;
            }
            if (goal.equals("package") || goal.equals("repackage")) {
                return Category.PACKAGE;
            }
            if (goal.equals("deploy")) {
                return Category.DEPLOY;
            }
            if (goal.equals("install")) {
                return Category.INSTALL;
            }
            if (goal.equals("clean")) {
                return Category.CLEAN;
            }
            if (goal.equals("resources") || goal.equals("testResources")) {
                return Category.RESOURCES;
            }
        }
        return Category.OTHER;
    }
}
