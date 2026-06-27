package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Frozen-golden guard for the moddable-tilesets Phase 2 filler-params extraction.
 * Pins {@code NatureZoneFiller}'s data tunables (ground weights, plant/rock
 * chances, overlay pools) to the values it hardcoded pre-migration — so the
 * data-driven {@code FillerParams} reproduces the same seeded generation
 * (weights summing to 100 give the former {@code 0.80f}/{@code 0.95f} thresholds;
 * pool order + size drive the same {@code rng.nextInt} picks).
 */
public class FillerParamsParityTest {

    private static final List<String> PLANT_POOL = List.of(
            "nature.shrub-1", "nature.shrub-2", "nature.tuft-1", "nature.tuft-2", "nature.shrub-3");
    private static final List<String> ROCK_POOL_SMALL = List.of(
            "nature.rock-small-1", "nature.rock-small-2", "nature.rock-small-3",
            "nature.rock-medium-1", "nature.rock-medium-2");
    private static final List<String> ROCK_POOL_BEACH = List.of(
            "nature.rock-small-1", "nature.rock-small-2", "nature.rock-small-3",
            "nature.rock-medium-1", "nature.rock-medium-2",
            "nature.rock-large-1", "nature.rock-large-2", "nature.rock-large-3");

    private static GenMappingRegistry loadMapping() throws Exception {
        GenMappingRegistry reg = new GenMappingRegistry();
        for (String p : GenMappingRegistry.BUILTIN_MAPPINGS) {
            reg.ingest(new JSONObject(Files.readString(Paths.get("mod", p.split("/")))));
        }
        return reg;
    }

    @Test
    void grasslandParamsAreFrozen() throws Exception {
        FillerParams p = loadMapping().fillerParams(BlockKind.NATURE_GRASSLAND);
        assertNotNull(p, "NATURE_GRASSLAND params");
        assertGround(p, new GroundKind[]{GroundKind.GRASS, GroundKind.DIRT, GroundKind.SAND}, new int[]{80, 15, 5});
        assertEquals(0.25f, p.plantChance);
        assertEquals(0.12f, p.rockChance);
        assertEquals(PLANT_POOL, p.plantPool());
        assertEquals(ROCK_POOL_SMALL, p.rockPool());
    }

    @Test
    void wetlandParamsAreFrozen() throws Exception {
        FillerParams p = loadMapping().fillerParams(BlockKind.NATURE_WETLAND);
        assertNotNull(p, "NATURE_WETLAND params");
        // Wetland's base is a carve (water puddles), not a weighted pool — no groundPool.
        assertEquals(0, p.groundEntries(), "wetland has no ground pool");
        assertEquals(0.25f, p.plantChance);
        assertEquals(0.12f, p.rockChance);
        assertEquals(PLANT_POOL, p.plantPool());
        assertEquals(ROCK_POOL_SMALL, p.rockPool());
    }

    @Test
    void beachParamsAreFrozen() throws Exception {
        FillerParams p = loadMapping().fillerParams(BlockKind.NATURE_BEACH);
        assertNotNull(p, "NATURE_BEACH params");
        assertGround(p, new GroundKind[]{GroundKind.SAND, GroundKind.DIRT}, new int[]{95, 5});
        assertEquals(0.25f, p.plantChance);
        assertEquals(0.18f, p.rockChance);
        assertEquals(PLANT_POOL, p.plantPool());
        assertEquals(ROCK_POOL_BEACH, p.rockPool());
    }

    private static void assertGround(FillerParams p, GroundKind[] kinds, int[] weights) {
        assertEquals(kinds.length, p.groundEntries(), "ground entry count");
        for (int i = 0; i < kinds.length; i++) {
            assertEquals(kinds[i], p.groundKind(i), "ground kind " + i);
            assertEquals(weights[i], p.groundWeight(i), "ground weight " + i);
        }
    }
}
