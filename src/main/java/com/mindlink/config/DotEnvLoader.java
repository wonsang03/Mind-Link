package com.mindlink.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * IDE 등에서 working directory 가 달라도 프로젝트 루트 .env 를 읽도록 한다.
 * {@code spring.config.import=optional:file:.env} 보조용.
 */
public final class DotEnvLoader {

    private DotEnvLoader() {}

    public static void load() {
        for (Path path : candidatePaths()) {
            if (Files.isRegularFile(path)) {
                loadFile(path);
                return;
            }
        }
    }

    private static List<Path> candidatePaths() {
        List<Path> out = new ArrayList<>();
        Path cwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        out.add(cwd.resolve(".env"));
        Path parent = cwd.getParent();
        if (parent != null) {
            out.add(parent.resolve(".env"));
        }
        return out;
    }

    private static void loadFile(Path path) {
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                if (!key.isEmpty() && System.getProperty(key) == null) {
                    System.setProperty(key, value);
                }
            }
        } catch (IOException ignored) {
            // optional file
        }
    }
}
