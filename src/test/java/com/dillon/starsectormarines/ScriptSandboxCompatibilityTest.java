package com.dillon.starsectormarines;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Protects shipped mod code from APIs rejected by Starsector's script classloader. */
class ScriptSandboxCompatibilityTest {

    private static final List<Path> SHIPPED_SOURCE_ROOTS = List.of(
            Path.of("src/main/java"),
            Path.of("asset-pipeline/src/main/java"));

    private static final List<String> FORBIDDEN_IMPORTS = List.of(
            "java.nio.file.",
            "java.io.File;",
            "java.io.FileInputStream;",
            "java.io.FileOutputStream;",
            "java.io.FileReader;",
            "java.io.FileWriter;",
            "java.io.RandomAccessFile;",
            "java.lang.reflect.");

    @Test
    void shippedSourcesDoNotImportSandboxForbiddenApis() {
        List<String> offenders = new ArrayList<>();
        for (Path root : SHIPPED_SOURCE_ROOTS) {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> inspectImports(root, path, offenders));
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }

        assertTrue(offenders.isEmpty(),
                "shipped mod code must use Starsector APIs instead of sandbox-forbidden "
                        + "file access or reflection; found: " + offenders);
    }

    private static void inspectImports(Path root, Path source, List<String> offenders) {
        try {
            for (String line : Files.readAllLines(source)) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("import ")) continue;
                for (String forbidden : FORBIDDEN_IMPORTS) {
                    if (trimmed.contains(forbidden)) {
                        offenders.add(root.relativize(source) + " imports " + forbidden);
                    }
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
