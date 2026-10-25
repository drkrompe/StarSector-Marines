package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.gen.IndustrialZone;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * How often the industrial zone district ({@code set-piece-districts.md}) stands on medium city maps and
 * colonies: per map, the service street's length and width, and how many loading bays, service lanes and
 * warehouse docks it published, and on how many maps a freight yard and a factory row stand at all, so a zone
 * that hosts them is seen not to cost the map one. Writes {@code build/reports/set-pieces/industrial-zone.md}. Asserts nothing
 * about the count: it states coverage.
 */
@Tag("generated-space-evidence")
class IndustrialZonePlacementEvidence {

    static final int SEEDS = 24;

    @Test
    void industrialZonesStandOnCityAndColonyMaps() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        DiskRegistries.installMapGeneration(root);
        TileRegistryTestInstaller.install(root);
        StringBuilder out = new StringBuilder("# Industrial zones\n\n"
                + "| map | seed | length | width | bays | lanes | docks | main street |\n|---|---|---|---|---|---|---|---|\n");
        StringBuilder totals = new StringBuilder();
        for (boolean colony : new boolean[]{false, true}) {
            int zones = 0, yards = 0, rows = 0, anyYard = 0, anyRow = 0;
            StringBuilder noYard = new StringBuilder();
            for (long seed = 1; seed <= SEEDS; seed++) {
                MapScale scale = colony ? MapScale.COLONY : MapScale.MEDIUM;
                MapResult map = colony
                        ? new BspCityGenerator().generate(scale.width, scale.height, seed, null, TargetProfile.NEUTRAL,
                                BattleSetup.colonyPlanFor(scale, seed))
                        : new BspCityGenerator().generate(scale.width, scale.height, seed);
                if (!map.industrialZones.isEmpty()) zones++;
                if (!map.loadingBays.isEmpty()) anyYard++;
                else noYard.append(' ').append(seed);
                if (!map.serviceLanes.isEmpty()) anyRow++;
                for (IndustrialZone zone : map.industrialZones) {
                    if (!zone.bays().isEmpty()) yards++;
                    if (!zone.lanes().isEmpty()) rows++;
                    float length = Math.abs(zone.endX() - zone.startX()) + Math.abs(zone.endY() - zone.startY());
                    out.append(String.format(Locale.ROOT, "| %s | %d | %.0f | %d | %d | %d | %d | %s |%n",
                            colony ? "colony" : "city", seed, length, zone.width(), zone.bays().size(),
                            zone.lanes().size(), zone.docks().size(), map.mainStreets.isEmpty() ? "" : "yes"));
                }
            }
            totals.append(String.format(Locale.ROOT,
                    "%s: an industrial zone on %d of %d maps (hosting a freight yard on %d, a factory row on %d);"
                            + " a freight yard anywhere on %d, a factory row anywhere on %d%n",
                    colony ? "Colonies" : "Medium city maps", zones, SEEDS, yards, rows, anyYard, anyRow));
            totals.append("  no freight yard on seeds:").append(noYard).append('\n');
        }
        out.append('\n').append(totals);
        Path dir = root.resolve("build/reports/set-pieces");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("industrial-zone.md"), out.toString());
        System.out.println(out);
    }
}
