package io.buildlens.providers.maven;

import io.buildlens.core.model.Category;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CategoryMapperTest {

    @Test
    void mapsCoreLifecyclePlugins() {
        assertEquals(Category.CLEAN, CategoryMapper.map("clean", "clean"));
        assertEquals(Category.RESOURCES, CategoryMapper.map("resources", "resources"));
        assertEquals(Category.RESOURCES, CategoryMapper.map("resources", "testResources"));
        assertEquals(Category.COMPILE, CategoryMapper.map("compiler", "compile"));
        assertEquals(Category.COMPILE, CategoryMapper.map("compiler", "testCompile"));
        assertEquals(Category.TEST, CategoryMapper.map("surefire", "test"));
        assertEquals(Category.TEST, CategoryMapper.map("failsafe", "integration-test"));
        assertEquals(Category.TEST, CategoryMapper.map("failsafe", "verify"));
        assertEquals(Category.PACKAGE, CategoryMapper.map("jar", "jar"));
        assertEquals(Category.PACKAGE, CategoryMapper.map("shade", "shade"));
        assertEquals(Category.PACKAGE, CategoryMapper.map("spring-boot", "repackage"));
        assertEquals(Category.INSTALL, CategoryMapper.map("install", "install"));
        assertEquals(Category.DEPLOY, CategoryMapper.map("deploy", "deploy"));
    }

    @Test
    void fallsBackToGoalSemanticsForUnknownPlugins() {
        assertEquals(Category.COMPILE, CategoryMapper.map("some-codegen-plugin", "compile"));
        assertEquals(Category.TEST, CategoryMapper.map("custom-test-plugin", "test"));
        assertEquals(Category.PACKAGE, CategoryMapper.map("custom-pack-plugin", "package"));
        assertEquals(Category.OTHER, CategoryMapper.map("enforcer", "enforce"));
        assertEquals(Category.OTHER, CategoryMapper.map("help", "evaluate"));
        assertEquals(Category.OTHER, CategoryMapper.map(null, null));
    }
}
