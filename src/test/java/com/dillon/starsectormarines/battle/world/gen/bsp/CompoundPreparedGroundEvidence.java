package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.world.gen.CompoundPreparedGround;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compound prepared ground on production-size Conquest maps (`mapgen-nouns.md`, prepared ground), against the same seeds
 * generated without it: how many compound places get an apron, that each one is a full fighting-position run with the
 * compound's lookout standing in it, and that the authored laws hold (no bag on a doorway or in the vehicle corridor, the generation
 * otherwise unchanged). Writes {@code build/reports/garrison/conquest-compounds.md}.
 */
@Tag("generated-space-evidence")
class CompoundPreparedGroundEvidence {

    private static final long[] SEEDS = {1L, 2L, 3L, 5L, 9L};

    @Test
    void conquestCompoundsOfferPreparedGround() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        DiskRegistries.installMapGeneration(root);
        TileRegistryTestInstaller.install(root);
        TargetProfile forts = TargetProfile.NEUTRAL.withDeclaredDefences(
                new GroundDefences(0, false).withForts(FortKind.BATTERY_FORT, 2));
        StringBuilder out = new StringBuilder("# Conquest compound prepared ground\n\n")
                .append("Per seed and profile: compound places, aprons laid, fighting-position runs with and without ")
                .append("(`-Dbattle.mapgen.compoundPreparedGround=false`), and aprons close enough to a fort's works to be read as its belt.\n\n")
                .append("| seed | profile | compound places | aprons | runs off | runs on | apron run cells | near a fort |\n")
                .append("|---|---|---|---|---|---|---|---|\n");
        int places = 0, aprons = 0;
        boolean was = CompoundPreparedGround.ENABLED;
        try {
            for (TargetProfile profile : new TargetProfile[]{TargetProfile.NEUTRAL, forts}) {
                for (long seed : SEEDS) {
                    CompoundPreparedGround.ENABLED = false;
                    MapResult control = generate(seed, profile);
                    CompoundPreparedGround.ENABLED = true;
                    MapResult laid = generate(seed, profile);
                    FightingPositionIndex off = index(control);
                    FightingPositionIndex on = index(laid);
                    int compounds = 0;
                    for (TacticalNode node : laid.tacticalMap.all()) {
                        if (CompoundPreparedGround.isCompoundKind(node.kind)) compounds++;
                    }
                    int laidHere = 0, nearFort = 0;
                    StringBuilder cells = new StringBuilder();
                    for (TacticalNode picket : laid.tacticalMap.ofKind(TacticalNode.Kind.GUARDPOST)) {
                        if (picket.standPositions().isEmpty() || picket.priorityScore != 50) continue;
                        laidHere++;
                        int run = on.runIdAt(picket.anchorX, picket.anchorY);
                        assertTrue(run >= 0, "a compound lookout stands in the apron's run, seed " + seed);
                        for (TacticalNode.StandPosition stand : picket.standPositions()) {
                            assertEquals(run, on.runIdAt(stand.x(), stand.y()), "every lookout cell is on its apron");
                        }
                        assertTrue(on.runSize(run) >= FightingPositionIndex.MIN_RUN_CELLS);
                        cells.append(on.runSize(run)).append(' ');
                        if (nearFort(laid, picket.anchorX, picket.anchorY)) nearFort++;
                    }
                    assertLaws(control, laid);
                    out.append(String.format("| %d | %s | %d | %d | %d | %d | %s| %d |%n", seed,
                            profile == forts ? "two forts" : "neutral", compounds, laidHere, off.runCount(),
                            on.runCount(), cells, nearFort));
                    assertTrue(on.runCount() >= off.runCount() + laidHere,
                            "every apron is a run of its own, beside every run the map already had");
                    places += compounds;
                    aprons += laidHere;
                }
            }
        } finally {
            CompoundPreparedGround.ENABLED = was;
        }
        out.append("\nIn all: ").append(aprons).append(" aprons on ").append(places).append(" compound places.\n");
        assertTrue(aprons * 2 >= places, "at least half the compound places get prepared ground: " + aprons + "/" + places);
        Path dir = root.resolve("build/reports/garrison");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("conquest-compounds.md"), out.toString());
        System.out.println(out);
    }

    private static MapResult generate(long seed, TargetProfile profile) {
        return new BspCityGenerator().generate(BattleSetup.CONQUEST_GRID_W, BattleSetup.CONQUEST_GRID_H, seed,
                TraversalAxis.SOUTH_TO_NORTH, profile);
    }

    private static FightingPositionIndex index(MapResult map) {
        return FightingPositionIndex.build(map.grid.getWidth(), map.grid.getHeight(), map.doodads, map.grid::isWalkable);
    }

    /** Whether a fort's works stand within the registry's belt reach of this cell. */
    private static boolean nearFort(MapResult map, int x, int y) {
        for (DefensePost post : map.defensePosts) {
            if (post.fort == null) continue;
            if (Math.max(Math.abs(post.anchorX - x), Math.abs(post.anchorY - y)) <= GarrisonableRegistry.FORT_REACH_CELLS) {
                return true;
            }
        }
        return false;
    }

    /**
     * No apron bag sits on a doorway or in the vehicle corridor, and an apron changes no cell's walkability: everything that was a
     * way through still is.
     */
    private static void assertLaws(MapResult control, MapResult laid) {
        NavigationGrid g = laid.grid;
        Set<Long> controlProps = new HashSet<>();
        for (Doodad d : control.doodads) controlProps.add(((long) d.cellX << 32) | d.cellY);
        for (Doodad d : laid.doodads) {
            if (d.definitionId() == null || !d.definitionId().startsWith("doodad.fort-sandbag-")) continue;
            if (controlProps.contains(((long) d.cellX << 32) | d.cellY)) continue;
            assertFalse(g.isDoorway(d.cellX, d.cellY), "no bag in a doorway");
            assertTrue(g.isWalkable(d.cellX, d.cellY), "a bag is stepped over");
            assertFalse(laid.vehicleCorridor != null && laid.vehicleCorridor.contains(d.cellX, d.cellY), "no bag in the vehicle corridor");
        }
        for (int y = 0; y < g.getHeight(); y++) {
            for (int x = 0; x < g.getWidth(); x++) {
                assertEquals(control.grid.isWalkable(x, y), g.isWalkable(x, y), "walkability unchanged at " + x + "," + y);
            }
        }
    }
}
