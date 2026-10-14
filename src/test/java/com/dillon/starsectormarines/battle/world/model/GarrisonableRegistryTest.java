package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.FightingPositionRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hand-built trenches on a 100x40 field with 8-cell blocks: the registry's mapping, not its use. */
class GarrisonableRegistryTest {

    private static final int W = 100;
    private static final int H = 40;
    private static final int BLOCK = 8;

    private final List<Doodad> props = new ArrayList<>();

    private void trench(int fromX, int toX, int y) {
        for (int x = fromX; x <= toX; x++) {
            DoodadDef def = new DoodadDef("doodad.test.trench", "sheet", 0, 0, DoodadCover.HEAVY, 0.3f,
                    1, 1, null, 0, null, FightingPositionRole.TRENCH);
            props.add(new Doodad(x, y, def));
        }
    }

    private FightingPositionIndex index() {
        return FightingPositionIndex.build(W, H, props, (x, y) -> true);
    }

    @Test
    void aTrenchIsOneGarrisonableWithItsCentreRadiusAndBlocks() {
        trench(10, 25, 12);
        GarrisonableRegistry registry = GarrisonableRegistry.build(index(), W, H, BLOCK, new int[0]);

        assertEquals(1, registry.count());
        assertEquals(16, registry.size(0));
        assertEquals(17, registry.centreX(0), "the run cell nearest its centroid (17.5): the lower on a tie");
        assertEquals(12, registry.centreY(0));
        assertEquals(GarrisonableRegistry.MAX_RADIUS_CELLS - 2, registry.radius(0),
                "a circle that reaches the far end, 8 cells away, inside the cap");
        // x 10..25 is blocks 1..3 on row 1 (y 12 / 8); block row width is ceil(100/8) = 13
        assertArrayEquals(new int[]{13 + 1, 13 + 2, 13 + 3}, registry.coveredBlocks(0));
        assertEquals(-1, registry.fortId(0));
        assertEquals(0, registry.idAt(20, 12));
        assertEquals(-1, registry.idAt(20, 13));
    }

    @Test
    void aShortRunGetsTheMinimumRadiusAndALongOneIsCapped() {
        trench(10, 21, 12);
        trench(10, 70, 30);
        GarrisonableRegistry registry = GarrisonableRegistry.build(index(), W, H, BLOCK, new int[0]);

        assertEquals(2, registry.count());
        assertEquals(GarrisonableRegistry.MIN_RADIUS_CELLS + 2, registry.radius(0));
        assertEquals(GarrisonableRegistry.MAX_RADIUS_CELLS, registry.radius(1));
    }

    @Test
    void theBlockLookupWakesOnlyGarrisonablesWithinReach() {
        trench(10, 25, 4);
        trench(70, 85, 36);
        GarrisonableRegistry registry = GarrisonableRegistry.build(index(), W, H, BLOCK, new int[0]);
        assertEquals(2, registry.count());

        assertArrayEquals(new int[]{0}, registry.near(2, 0), "a block holding the first run");
        assertArrayEquals(new int[]{0}, registry.near(3 + GarrisonableRegistry.REACH_BLOCKS, 0),
                "and one within reach of its last covered block");
        assertEquals(0, registry.near(7, 0).length, "beyond reach of every run wakes nothing");
        assertEquals(0, registry.near(2, 4).length, "four block rows from the first run, and the second is further along");
        assertArrayEquals(new int[]{1}, registry.near(10, 4));
        assertEquals(0, registry.near(-1, 0).length, "off the lattice is empty, not an error");
        assertEquals(0, registry.near(0, 40).length);
    }

    @Test
    void reachCanWakeSeveralGarrisonablesInAscendingOrder() {
        trench(10, 25, 12);
        trench(30, 45, 12);
        GarrisonableRegistry registry = GarrisonableRegistry.build(index(), W, H, BLOCK, new int[0]);

        assertArrayEquals(new int[]{0, 1}, registry.near(3, 1));
    }

    @Test
    void aGarrisonableIsAttributedToTheNearestFortInReachElseNone() {
        trench(10, 25, 12);
        trench(70, 85, 30);
        int[] forts = {4, 20, 20, 7, 30, 20};
        GarrisonableRegistry registry = GarrisonableRegistry.build(index(), W, H, BLOCK, forts);

        assertEquals(4, registry.fortId(0), "nearer fort 4 over fort 7");
        assertEquals(-1, registry.fortId(1), "more than the belt reach from any fort");
    }

    @Test
    void theRegistryIsBuiltFromExactlyOneIndexInstance() {
        trench(10, 25, 12);
        FightingPositionIndex first = index();
        GarrisonableRegistry registry = GarrisonableRegistry.build(first, W, H, BLOCK, new int[0]);

        assertTrue(registry.isBuiltFrom(first));
        assertSame(first, registry.source());
        assertFalse(registry.isBuiltFrom(index()), "an equal index rebuilt is a new instance and asks for a new registry");
    }

    @Test
    void aMapWithNoPreparedGroundHasNoGarrisonables() {
        GarrisonableRegistry registry = GarrisonableRegistry.build(index(), W, H, BLOCK, new int[0]);

        assertEquals(0, registry.count());
        assertEquals(0, registry.near(3, 1).length);
    }
}
