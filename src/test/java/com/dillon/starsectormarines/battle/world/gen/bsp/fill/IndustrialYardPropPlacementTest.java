package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class IndustrialYardPropPlacementTest {
    @Test
    void isolatedCratePreviewPreservesGrassAndExistingSurfaceOverrides() {
        GenContext ctx = ground();
        IndustrialYardCoverFixture.placeCrates(ctx.grid, ctx.topology, 1, 1);
        assertGroundPreserved(ctx);
        assertFalse(ctx.grid.isWalkable(1, 1));
        assertTrue(ctx.grid.isSeeThrough(1, 1));
        assertTrue(ctx.topology.isFixture(1, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"crate-stack", "drum-cluster", "cable-reel", "generator", "dumpster",
            "pipe-bundle", "pallet-stack", "scrap-pile", "fluid-tank"})
    void yardEquipmentPreservesSupportingGroundInBothPlacementModes(String equipment) {
        for (boolean tactical : new boolean[]{false, true}) {
            GenContext ctx = ground();
            var prop = IndustrialYardFiller.placeProp(ctx, "doodad.industrial-" + equipment, 1, 1, tactical);
            assertGroundPreserved(ctx);
            assertEquals(1, ctx.doodads.size());
            assertSame(prop, ctx.doodads.get(0));
            assertEquals(!tactical, ctx.grid.isWalkable(1, 1));
            assertEquals(tactical, ctx.topology.isFixture(1, 1));
            if (tactical) assertEquals(!equipment.equals("fluid-tank"), ctx.grid.isSeeThrough(1, 1));
        }
    }

    private static GenContext ground() {
        NavigationGrid grid = new NavigationGrid(3, 3);
        CellTopology topology = new CellTopology(3, 3);
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, GroundKind.GRASS);
        }
        topology.setSurface(1, 1, topology.surfaceIndex("floors.stone"));
        topology.setTag(1, 1, CellTopology.Tag.GROUND_DAMAGED, true);
        return new GenContext(grid, topology, new Random(42), 3, 3, 42);
    }

    private static void assertGroundPreserved(GenContext ctx) {
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) {
            assertEquals(GroundKind.GRASS, ctx.topology.getGroundKind(x, y));
            if (x != 1 || y != 1) assertEquals(0, ctx.topology.getSurface(x, y));
        }
        assertEquals("floors.stone", ctx.topology.getSurfaceId(1, 1));
        assertTrue(ctx.topology.hasTag(1, 1, CellTopology.Tag.GROUND_DAMAGED));
    }
}
