package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.gen.MainStreet;
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
 * How often the main street district ({@code set-piece-districts.md}) stands on medium city maps and colonies:
 * per map, the street's length and width, whether its plaza and hall door were laid. Writes
 * {@code build/reports/set-pieces/main-street.md}. Asserts nothing about the count: it states coverage.
 */
@Tag("generated-space-evidence")
class MainStreetPlacementEvidence {

    static final int SEEDS = 24;

    @Test
    void mainStreetsStandOnCityAndColonyMaps() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        DiskRegistries.installMapGeneration(root);
        TileRegistryTestInstaller.install(root);
        StringBuilder out = new StringBuilder("# Main streets\n\n| map | seed | length | width | plaza | hall door |\n|---|---|---|---|---|---|\n");
        StringBuilder totals = new StringBuilder();
        for (boolean colony : new boolean[]{false, true}) {
            int streets = 0;
            for (long seed = 1; seed <= SEEDS; seed++) {
                MapScale scale = colony ? MapScale.COLONY : MapScale.MEDIUM;
                MapResult map = colony
                        ? new BspCityGenerator().generate(scale.width, scale.height, seed, null, TargetProfile.NEUTRAL,
                                BattleSetup.colonyPlanFor(scale, seed))
                        : new BspCityGenerator().generate(scale.width, scale.height, seed);
                if (!map.mainStreets.isEmpty()) streets++;
                for (MainStreet street : map.mainStreets) {
                    float length = Math.abs(street.endX() - street.startX()) + Math.abs(street.endY() - street.startY());
                    out.append(String.format(Locale.ROOT, "| %s | %d | %.0f | %d | %s | %s |%n", colony ? "colony" : "city",
                            seed, length, street.width(), street.plazaLeft() >= 0 ? "yes" : "",
                            street.landmarkDoorX() >= 0 ? street.landmarkDoorX() + "," + street.landmarkDoorY() : ""));
                }
            }
            totals.append(String.format(Locale.ROOT, "%s: a main street on %d of %d maps%n",
                    colony ? "Colonies" : "Medium city maps", streets, SEEDS));
        }
        out.append('\n').append(totals);
        Path dir = root.resolve("build/reports/set-pieces");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("main-street.md"), out.toString());
        System.out.println(out);
    }
}
