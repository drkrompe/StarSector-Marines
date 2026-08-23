package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.impl.campaign.abilities.BaseAbilityPlugin;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Guards the seams between {@code mod/data/campaign/abilities.csv} and the code it
 * names. Every one of these fails silently at runtime — a renamed plugin class, a
 * drifted id, or a missing icon produces an ability that simply never appears, with
 * nothing in our own logs to say why.
 */
class AbilitiesCsvTest {

    private static final Path CSV = Paths.get("mod/data/campaign/abilities.csv");

    @Test
    void theCsvDeclaresExactlyTheCompanyViewAbility() throws IOException {
        List<List<String>> rows = parse(CSV);

        assertEquals(2, rows.size(), "header plus exactly one ability row");
        List<String> header = rows.get(0);
        List<String> row = rows.get(1);
        assertEquals(header.size(), row.size(), "row must have one cell per header column");

        assertEquals(CompanyViewAbility.ABILITY_ID, cell(header, row, "id"));
        assertEquals("TOGGLE", cell(header, row, "type"));
        // Granted explicitly from onGameLoad so new games and existing saves take one
        // path; unlocking at start as well would double-grant.
        assertEquals("FALSE", cell(header, row, "unlockedAtStart"));
        assertEquals("FALSE", cell(header, row, "defaultForAIFleet"));
    }

    @Test
    void thePluginColumnNamesALoadableAbility() throws Exception {
        List<List<String>> rows = parse(CSV);
        String plugin = cell(rows.get(0), rows.get(1), "plugin");

        Class<?> type = Class.forName(plugin);
        assertTrue(BaseAbilityPlugin.class.isAssignableFrom(type),
                plugin + " must extend BaseAbilityPlugin");
        assertEquals(CompanyViewAbility.class, type);
    }

    @Test
    void theIconExists() throws IOException {
        String icon = cell(parse(CSV).get(0), parse(CSV).get(1), "icon");
        assertTrue(Files.isRegularFile(Paths.get("mod").resolve(icon)),
                "icon not found under mod/: " + icon);
    }

    /**
     * Our column set has to match the game's exactly — the loader reads by position as
     * well as by name, so a column added or reordered in a game update silently shifts
     * every value in our row. Skipped when the install is not reachable.
     */
    @Test
    void theHeaderMatchesTheInstalledGame() throws IOException {
        String starsectorDir = System.getProperty("starsectorDir");
        assumeTrue(starsectorDir != null, "starsectorDir not set");
        Path vanilla = Paths.get(starsectorDir, "starsector-core", "data", "campaign",
                "abilities.csv");
        assumeTrue(Files.isRegularFile(vanilla), "vanilla abilities.csv not found");

        assertEquals(parse(vanilla).get(0), parse(CSV).get(0));
    }

    private static String cell(List<String> header, List<String> row, String column) {
        int index = header.indexOf(column);
        assertTrue(index >= 0, "no such column: " + column);
        return row.get(index);
    }

    /** Minimal RFC-4180 reader: enough for quoted cells, which is all these files use. */
    private static List<List<String>> parse(Path path) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.replace(",", "").isBlank()) continue;
            List<String> cells = new ArrayList<>();
            StringBuilder cell = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '"') {
                    quoted = !quoted;
                } else if (c == ',' && !quoted) {
                    cells.add(cell.toString().trim());
                    cell.setLength(0);
                } else {
                    cell.append(c);
                }
            }
            cells.add(cell.toString().trim());
            rows.add(cells);
        }
        return rows;
    }
}
