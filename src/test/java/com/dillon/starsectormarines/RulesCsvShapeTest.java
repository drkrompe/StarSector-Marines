package com.dillon.starsectormarines;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shapes in {@code mod/data/campaign/rules.csv} that Starsector's rule loader rejects at startup, which takes the whole
 * game down before the main menu, with or without any other mod installed.
 *
 * <p>A {@code score:N} must end a condition line, never stand on a line of its own: a bare score line is parsed as a
 * condition with no tokens and throws an {@code IndexOutOfBoundsException} while the rules load.
 */
class RulesCsvShapeTest {

    private static final Path RULES = Path.of("mod/data/campaign/rules.csv");
    private static final Pattern BARE_SCORE = Pattern.compile("\\s*score:\\s*-?\\d+(\\.\\d+)?\\s*");

    @Test
    void noConditionIsABareScoreLine() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (List<String> row : rows(Files.readString(RULES, StandardCharsets.UTF_8))) {
            if (row.size() < 3 || row.get(0).startsWith("#")) continue;
            for (String line : row.get(2).split("\n")) {
                if (BARE_SCORE.matcher(line).matches()) offenders.add(row.get(0));
            }
        }
        assertEquals(List.of(), offenders, "put score:N at the end of a condition line instead");
    }

    @Test
    void theParserSeesQuotedNewlinesAsOneField() {
        List<List<String>> rows = rows("id,trigger,conditions\na,T,\"x\nscore:1\"\n");
        assertEquals(2, rows.size());
        assertTrue(rows.get(1).get(2).contains("\n"));
    }

    /** RFC 4180 rows: quoted fields may hold commas, newlines and doubled quotes. */
    static List<List<String>> rows(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else if (c != '\r') {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else if (c != '\r') {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
