package com.dillon.starsectormarines.testsupport;

import com.dillon.starsectormarines.battle.warship.VanillaData;
import com.dillon.starsectormarines.battle.warship.VanillaWeaponLooks;
import com.dillon.starsectormarines.battle.warship.VanillaWeapons;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lets headless runs read the installed game's own data files — hulls, weapons,
 * projectiles, variants and the weapon table — the way {@link InstalledHullSpecs}
 * does for hull dimensions. Paths are the game's own, relative to
 * {@code starsector-core}.
 */
public final class InstalledVanillaData {

    private InstalledVanillaData() {}

    public static void install(Path starsectorCore) {
        if (starsectorCore == null) return;
        VanillaData.useSource(new VanillaData.Source() {
            @Override public JSONObject json(String path) throws Exception {
                return new JSONObject(sanitize(Files.readString(starsectorCore.resolve(path))));
            }

            @Override public List<Map<String, String>> csv(String path) throws Exception {
                return parseCsv(Files.readString(starsectorCore.resolve(path)));
            }
        });
        VanillaWeaponLooks.clearCache();
        VanillaWeapons.clearCache();
    }

    public static void install() {
        String dir = System.getProperty("starsectorDir");
        if (dir == null || dir.isBlank()) return;
        install(Paths.get(dir, "starsector-core"));
    }

    /**
     * Cleans a game data file the way the game's own loader does: {@code #}
     * comments to the end of the line (outside strings) and a trailing {@code f}
     * on a number, neither of which the JSON library accepts.
     */
    static String sanitize(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean inString = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\\' && i + 1 < text.length()) out.append(text.charAt(++i));
                else if (c == '"') inString = false;
            } else if (c == '"') {
                inString = true;
                out.append(c);
            } else if (c == '#') {
                while (i < text.length() && text.charAt(i) != '\n') i++;
                out.append('\n');
            } else {
                out.append(c);
            }
        }
        return out.toString().replaceAll("(?<=[0-9])f(?=[,\\s}\\]])", "");
    }

    /** A header row and quoted-field CSV; blank lines are skipped. */
    static List<Map<String, String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        List<Map<String, String>> out = new ArrayList<>();
        if (rows.isEmpty()) return out;
        List<String> header = rows.get(0);
        for (int r = 1; r < rows.size(); r++) {
            List<String> values = rows.get(r);
            if (values.stream().allMatch(String::isBlank)) continue;
            Map<String, String> map = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                map.put(header.get(c), c < values.size() ? values.get(c) : "");
            }
            out.add(map);
        }
        return out;
    }
}
