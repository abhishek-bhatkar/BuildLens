package io.buildlens.testsupport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Shared helpers for golden-log tests. */
public final class GoldenLogs {

    private GoldenLogs() {
    }

    public static Path resource(String name) {
        return Paths.get("src", "test", "resources", "golden", name);
    }

    public static InputStream stream(String name) throws IOException {
        return new ByteArrayInputStream(bytes(name));
    }

    public static byte[] bytes(String name) throws IOException {
        return Files.readAllBytes(resource(name));
    }

    public static String text(String name) throws IOException {
        return new String(bytes(name), StandardCharsets.UTF_8);
    }
}
