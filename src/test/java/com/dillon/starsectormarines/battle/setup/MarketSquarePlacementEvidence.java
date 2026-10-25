package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.OpenSquare;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How often the market square set piece ({@code set-piece-blocks.md}) stands on city and colony maps: per
 * map, whether it has a dense quarter (the host measured and rejected for want of ground) and how many mouths
 * each square opened, failing when fewer than half the maps of either kind carry one. Writes
 * {@code build/reports/set-pieces/market-square.md}.
 */
@Tag("generated-space-evidence")
class MarketSquarePlacementEvidence {

    static final int SEEDS = 24;

    @Test
    void marketSquaresStandOnCityAndColonyMaps() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        DiskRegistries.installMapGeneration(root);
        TileRegistryTestInstaller.install(root);
        StringBuilder out = new StringBuilder("# Market squares\n\n| map | seed | dense quarter | mouths |\n|---|---|---|---|\n");
        for (boolean colony : new boolean[]{false, true}) {
            int quarters = 0, squares = 0;
            Map<Integer, Integer> byMouths = new TreeMap<>();
            for (long seed = 1; seed <= SEEDS; seed++) {
                BspCityGenerator gen = new BspCityGenerator();
                MapScale scale = colony ? MapScale.COLONY : MapScale.MEDIUM;
                MapResult map = colony
                        ? gen.generate(scale.width, scale.height, seed, null, TargetProfile.NEUTRAL,
                                BattleSetup.colonyPlanFor(scale, seed))
                        : gen.generate(scale.width, scale.height, seed);
                boolean quarter = gen.getLastCompounds().stream().anyMatch(c -> c.kind == BlockKind.DENSE_QUARTER);
                if (quarter) quarters++;
                StringBuilder mouths = new StringBuilder();
                for (OpenSquare square : map.openSquares) {
                    mouths.append(square.mouths().size()).append(' ');
                    byMouths.merge(square.mouths().size(), 1, Integer::sum);
                }
                if (!map.openSquares.isEmpty()) squares++;
                out.append(String.format("| %s | %d | %s | %s|%n", colony ? "colony" : "city", seed,
                        quarter ? "yes" : "", mouths));
            }
            out.append(String.format("%n%s: a square on %d of %d maps; %d had a dense quarter; squares by mouths %s%n%n",
                    colony ? "Colony" : "City", squares, SEEDS, quarters, byMouths));
            assertTrue(squares * 2 >= SEEDS, (colony ? "colonies" : "city maps") + " with a square: " + squares);
        }
        Path dir = root.resolve("build/reports/set-pieces");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("market-square.md"), out.toString());
        System.out.println(out);
    }
}
