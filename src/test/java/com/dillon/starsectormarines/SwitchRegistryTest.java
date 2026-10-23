package com.dillon.starsectormarines;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every {@code -Dbattle.*} and {@code -Dmarines.*} switch the shipped code reads
 * is listed in {@code roadmap/switches.md} with its kind and its removal
 * condition, and every row there names a switch that still exists.
 *
 * <p>Switches added for an A/B comparison are temporary, and nothing retires
 * them on its own: an audit on 2026-10-23 found about 260 of them, most of
 * them settled comparisons still carrying their losing arm. The registry is
 * where a switch's reason to exist is written down, so this keeps a new one
 * from arriving without one and an old row from outliving its switch.
 *
 * <p>Every {@code "battle.…"} or {@code "marines.…"} string literal under
 * {@code src/main/java} is a property name in this codebase. A name the code
 * composes from a prefix ({@code "battle.mech." + key}, {@code PROPERTY + "." + name})
 * is registered as a row whose name holds a {@code <placeholder>}, which matches
 * its prefix literal.
 */
class SwitchRegistryTest {

    private static final Path SOURCES = Path.of("src/main/java");
    private static final Path REGISTRY = Path.of("roadmap/switches.md");
    private static final Set<String> KINDS = Set.of("experiment", "control", "config", "tuning", "undecided");
    private static final Pattern NAME = Pattern.compile("\"((?:battle|marines)\\.[A-Za-z0-9_.]*[A-Za-z0-9_]\\.?)\"");

    @Test
    void everySwitchIsRegisteredAndEveryRowStillExists() throws IOException {
        Set<String> read = readInSources();
        Map<String, String> rows = registryRows();

        List<String> problems = new ArrayList<>();
        for (String name : read) {
            if (name.endsWith(".")) {
                if (rows.keySet().stream().noneMatch(row -> row.startsWith(name) && row.indexOf('<') > 0)) {
                    problems.add("unregistered prefix: " + name + "<placeholder>");
                }
            } else if (!rows.containsKey(name)) {
                problems.add("unregistered: " + name);
            }
        }
        for (Map.Entry<String, String> row : rows.entrySet()) {
            String name = row.getKey();
            int hole = name.indexOf('<');
            String prefix = hole < 0 ? name : name.substring(0, hole);
            boolean exists = read.contains(prefix)
                    || hole >= 0 && prefix.endsWith(".") && read.contains(prefix.substring(0, prefix.length() - 1));
            if (!exists) problems.add("registered but no longer read: " + name);
            if (row.getValue() != null) problems.add(row.getValue());
        }
        assertEquals(List.of(), problems,
                "update roadmap/switches.md: every switch needs a row with its kind and removal condition");
    }

    private static Set<String> readInSources() throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(SOURCES)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
        Set<String> names = ConcurrentHashMap.newKeySet();
        files.parallelStream().forEach(file -> {
            String text;
            try {
                text = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (text.indexOf("\"battle.") < 0 && text.indexOf("\"marines.") < 0) return;
            Matcher m = NAME.matcher(text);
            while (m.find()) names.add(m.group(1));
        });
        return new TreeSet<>(names);
    }

    /** Row name to a problem with the row, or {@code null} when the row is well formed. */
    private static Map<String, String> registryRows() throws IOException {
        Map<String, String> rows = new LinkedHashMap<>();
        for (String line : Files.readAllLines(REGISTRY, StandardCharsets.UTF_8)) {
            if (!line.startsWith("| `battle.") && !line.startsWith("| `marines.")) continue;
            String[] cells = line.split("\\|");
            String name = cells[1].trim().replace("`", "");
            String kind = cells.length > 2 ? cells[2].trim() : "";
            String condition = cells.length > 4 ? cells[4].trim() : "";
            String problem = null;
            if (!KINDS.contains(kind)) problem = name + ": kind '" + kind + "' is not one of " + new TreeSet<>(KINDS);
            else if (condition.isEmpty()) problem = name + ": no removal condition or reason";
            rows.put(name, problem);
        }
        return rows;
    }
}
