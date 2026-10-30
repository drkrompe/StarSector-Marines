package com.dillon.starsectormarines.battle.matchup;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A report row is a pure summary of its fights; built here from hand-made results, no simulation. */
class ArmorReportTest {

    @Test
    void rowCountsOutcomesAndAveragesOnlyDecidedTimeToKill() {
        ArmorReport.Row row = ArmorReport.Row.of("bulwark vs hound", List.of(
                fight("ATTACKERS", 900, 300, side(1, 1, 0.5f, 10, 5, 1, 0, 3f), side(1, 0, 0f, 20, 4, 2, 1, 0f)),
                fight("DEFENDERS", 1500, 300, side(1, 0, 0f, 30, 6, 3, 1, 9f), side(1, 1, 0.25f, 10, 6, 0, 0, 0f)),
                fight("STALEMATE", 5400, 600, side(1, 1, 0.8f, 0, 0, 0, 0, 0f), side(1, 1, 0.9f, 0, 0, 0, 0, 0f))),
                "gifs/x.gif");
        assertEquals(3, row.fights());
        assertEquals(1, row.attackerWins());
        assertEquals(1, row.defenderWins());
        assertEquals(1, row.stalemate());
        assertEquals(0, row.unresolved());
        // (600 + 1200) / 2 ticks over the two decided fights, at 30 ticks a second.
        assertEquals(30f, row.meanTtkSeconds(), 1e-4f);
        assertEquals(2f / 3f, row.attackerLeft(), 1e-4f);
        assertEquals((0.5f + 0f + 0.8f) / 3f, row.attackerIntegrity(), 1e-4f);
        assertEquals(11f / 40f, row.attackerHitRate(), 1e-4f);
        assertEquals(10f / 30f, row.defenderHitRate(), 1e-4f);
        assertEquals(4f / 3f, row.attackerMountsLost(), 1e-4f);
        assertEquals(1f / 3f, row.defenderLegsCrippled(), 1e-4f);
        assertEquals(4f, row.attackerFluxSeconds(), 1e-4f);
        assertEquals("gifs/x.gif", row.gif());
    }

    @Test
    void noDecidedFightReadsAsNoTimeToKill() {
        ArmorReport.Row row = ArmorReport.Row.of("x", List.of(
                fight("UNRESOLVED", 5400, -1, side(1, 1, 1f, 0, 0, 0, 0, 0f), side(1, 1, 1f, 0, 0, 0, 0, 0f))), null);
        assertEquals(-1f, row.meanTtkSeconds());
        assertTrue(ArmorReport.markdownRow(row).contains("| - |"));
        assertEquals("", row.gif());
    }

    @Test
    void weaponsAreRankedByDamageShareWithHitRate() {
        Map<String, ArmorPlay.WeaponStats> weapons = new LinkedHashMap<>();
        weapons.put("mech-chaingun", new ArmorPlay.WeaponStats(100, 25, 150f));
        weapons.put("mech-srm-pod", new ArmorPlay.WeaponStats(20, 10, 350f));
        weapons.put("mech-lrm-artillery", new ArmorPlay.WeaponStats(4, 0, 0f));
        weapons.put("mech-hc-20", new ArmorPlay.WeaponStats(2, 0, 0f));
        assertEquals("mech-srm-pod 70% (h 50%), mech-chaingun 30% (h 25%), mech-lrm-artillery 0% (h 0%)",
                ArmorReport.weaponText(weapons));
        assertEquals("-", ArmorReport.weaponText(Map.of()));
    }

    @Test
    void rowsMergeWeaponsAcrossSeeds() {
        ArmorPlay.SideStats one = new ArmorPlay.SideStats("hound", 1, 1, 100f, 50f, 80f, 10, 5, 0, 0, 0f,
                Map.of("mech-chaingun", new ArmorPlay.WeaponStats(10, 5, 80f)), "", 0, 0);
        ArmorPlay.SideStats two = new ArmorPlay.SideStats("hound", 1, 1, 100f, 50f, 20f, 10, 1, 0, 0, 0f,
                Map.of("mech-chaingun", new ArmorPlay.WeaponStats(10, 1, 20f)), "", 0, 0);
        ArmorReport.Row row = ArmorReport.Row.of("x", List.of(
                fight("STALEMATE", 2000, 10, one, one), fight("STALEMATE", 2000, 10, two, two)), null);
        assertEquals("mech-chaingun 100% (h 30%)", row.attackerWeapons());
    }

    @Test
    void csvRowsHaveTheirHeaderWidth() {
        ArmorPlay.Result r = fight("ATTACKERS", 900, 300, side(2, 1, 0.5f, 10, 5, 1, 0, 3f),
                side(1, 0, 0f, 20, 4, 2, 1, 0f));
        ArmorReport.Row row = ArmorReport.Row.of("a, \"b\"", List.of(r), null);
        assertEquals(columns(ArmorReport.CSV_HEADER), columns(ArmorReport.csvRow(row)));
        assertEquals(columns(ArmorReport.RUNS_HEADER), columns(ArmorReport.runRow(r, "g.gif")));
        assertEquals(columns(ArmorReport.markdownHeader().lines().findFirst().orElseThrow()),
                columns(ArmorReport.markdownRow(row).replace(',', ';')));
    }

    /** Fields in one CSV line (quotes respected), or cells in one markdown line. */
    private static int columns(String line) {
        line = line.strip();
        if (line.startsWith("|")) return line.split("\\|", -1).length;
        int n = 1;
        boolean quoted = false;
        for (char c : line.toCharArray()) {
            if (c == '"') quoted = !quoted;
            else if (c == ',' && !quoted) n++;
        }
        return n;
    }

    private static ArmorPlay.Result fight(String winner, int ticks, int firstDamage,
                                          ArmorPlay.SideStats a, ArmorPlay.SideStats d) {
        return new ArmorPlay.Result("open-ground", a.label(), d.label(), 1L, winner, ticks, firstDamage, 20f, a, d);
    }

    private static ArmorPlay.SideStats side(int start, int left, float integrity, int shots, int hits,
                                            int mounts, int legs, float flux) {
        return new ArmorPlay.SideStats("side", start, left, 100f, 100f * integrity, 50f, shots, hits, mounts, legs,
                flux, Map.of("w", new ArmorPlay.WeaponStats(shots, hits, 50f)), left > 0 ? "hound[50%]" : "", 0, 0);
    }
}
