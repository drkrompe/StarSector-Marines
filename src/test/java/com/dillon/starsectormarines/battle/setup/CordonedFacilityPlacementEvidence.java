package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.gen.CordonedFacility;
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
 * How often the cordoned facility district ({@code set-piece-districts.md}) stands on medium city maps and
 * colonies: per map, its character, its size, its gatehouses, and how many of its buildings were carved with a
 * front door. Writes {@code build/reports/set-pieces/cordoned-facility.md}. Asserts nothing about the count: it
 * states coverage.
 */
@Tag("generated-space-evidence")
class CordonedFacilityPlacementEvidence {

    static final int SEEDS = 24;

    @Test
    void cordonedFacilitiesStandOnCityAndColonyMaps() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        DiskRegistries.installMapGeneration(root);
        TileRegistryTestInstaller.install(root);
        StringBuilder out = new StringBuilder("# Cordoned facilities\n\n"
                + "| map | seed | character | bounds | size | gatehouses | buildings | doors | courtyard |\n"
                + "|---|---|---|---|---|---|---|---|---|\n");
        StringBuilder totals = new StringBuilder();
        for (boolean colony : new boolean[]{false, true}) {
            int facilities = 0;
            for (long seed = 1; seed <= SEEDS; seed++) {
                MapScale scale = colony ? MapScale.COLONY : MapScale.MEDIUM;
                MapResult map = colony
                        ? new BspCityGenerator().generate(scale.width, scale.height, seed, null, TargetProfile.NEUTRAL,
                                BattleSetup.colonyPlanFor(scale, seed))
                        : new BspCityGenerator().generate(scale.width, scale.height, seed);
                if (!map.cordonedFacilities.isEmpty()) facilities++;
                for (CordonedFacility f : map.cordonedFacilities) {
                    int doors = 0;
                    for (CordonedFacility.Building building : f.buildings()) if (building.doorX() >= 0) doors++;
                    out.append(String.format(Locale.ROOT, "| %s | %d | %s | %d,%d..%d,%d | %dx%d | %d | %d | %d | %s |%n",
                            colony ? "colony" : "city", seed, f.character(), f.left(), f.top(), f.right(), f.bottom(),
                            f.right() - f.left() + 1, f.bottom() - f.top() + 1, f.gatehouses().size(),
                            f.buildings().size(), doors, f.courtyardLeft() >= 0 ? "yes" : ""));
                }
            }
            totals.append(String.format(Locale.ROOT, "%s: a cordoned facility on %d of %d maps%n",
                    colony ? "Colonies" : "Medium city maps", facilities, SEEDS));
        }
        out.append('\n').append(totals);
        Path dir = root.resolve("build/reports/set-pieces");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("cordoned-facility.md"), out.toString());
        System.out.println(out);
    }
}
