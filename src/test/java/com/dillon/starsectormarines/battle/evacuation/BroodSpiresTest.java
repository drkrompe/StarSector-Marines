package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BroodSpiresTest {

    private static final int GAP = Math.round(BroodSpires.MIN_GAP_SECONDS / BattleSimulation.TICK_DT);

    /** Creep and open ground everywhere inside a 200-cell square. */
    private static final CreepOutposts.Ground ALL = new CreepOutposts.Ground() {
        @Override public boolean creeped(int x, int y) { return x >= 0 && y >= 0 && x < 200 && y < 200; }
        @Override public boolean open(int x, int y) { return creeped(x, y); }
    };

    @Test
    void releasesOnlyUnderCoverWithPreyAndNoPackAloftAfterTheGap() {
        assertTrue(BroodSpires.releases(true, false, GAP, true));
        assertFalse(BroodSpires.releases(false, false, GAP, true), "the flier law");
        assertFalse(BroodSpires.releases(true, true, GAP, true), "a pack of its own still flies");
        assertFalse(BroodSpires.releases(true, false, GAP - 1, true), "inside the gap");
        assertFalse(BroodSpires.releases(true, false, GAP, false), "nothing in reach");
    }

    @Test
    void growsOnCreepAtItsStandoffFromTheBore() {
        int[] cell = BroodSpires.choose(ALL, 100, 100, 0f, List.of(), List.of());
        assertNotNull(cell);
        double d = Math.hypot(cell[0] + 0.5 - 100.5, cell[1] + 0.5 - 100.5);
        assertTrue(d >= BroodSpires.STANDOFF_MIN - 1 && d <= BroodSpires.STANDOFF_MAX + 1, "standoff " + d);
    }

    @Test
    void keepsClearOfTheBoreAndItsColonies() {
        float[] colony = {100.5f + BroodSpires.STANDOFF_MIN, 100.5f};
        int[] cell = BroodSpires.choose(ALL, 100, 100, 0f, List.of(), List.of(colony));
        assertNotNull(cell);
        assertTrue(Math.hypot(cell[0] + 0.5 - colony[0], cell[1] + 0.5 - colony[1]) >= BroodSpires.CLEARANCE);
    }

    @Test
    void neverGrowsNearAnotherSpire() {
        assertNull(BroodSpires.choose(ALL, 100, 100, 0f, List.of(new float[]{120.5f, 100.5f}), List.of()));
        assertNotNull(BroodSpires.choose(ALL, 100, 100, 0f, List.of(new float[]{100.5f - BroodSpires.SPACING
                - BroodSpires.STANDOFF_MAX, 100.5f}), List.of()));
    }

    @Test
    void neverGrowsOffTheCreep() {
        CreepOutposts.Ground bare = new CreepOutposts.Ground() {
            @Override public boolean creeped(int x, int y) { return false; }
            @Override public boolean open(int x, int y) { return true; }
        };
        assertNull(BroodSpires.choose(bare, 100, 100, 0f, List.of(), List.of()));
    }

    @Test
    void itsBroodFillsBackOntoTheHooksOverItsGap() {
        assertEquals(0, BroodSpires.perched(true, GAP * 10, GAP), "none while its pack flies");
        assertEquals(0, BroodSpires.perched(false, 0, GAP), "none the moment it launched");
        assertEquals(BroodSpires.PERCHES / 2, BroodSpires.perched(false, GAP / 2, GAP), "half way through the gap");
        assertEquals(BroodSpires.PERCHES, BroodSpires.perched(false, GAP, GAP), "a full roost is ready to launch");
        assertEquals(BroodSpires.PERCHES, BroodSpires.perched(false, GAP / 2, GAP / 2), "a tended spire fills sooner");
        assertEquals(BroodSpires.PERCHES, BroodSpires.perched(false, Integer.MAX_VALUE / 2, GAP), "never more than its hooks");
    }

    @Test
    void aQuietFullRoostPatrolsAfterItsGap() {
        assertTrue(BroodSpires.patrols(true, false, false, true, false, 100, 100));
        assertFalse(BroodSpires.patrols(false, false, false, true, false, 100, 100), "the flier law");
        assertFalse(BroodSpires.patrols(true, true, false, true, false, 100, 100), "its pack is up");
        assertFalse(BroodSpires.patrols(true, false, true, true, false, 100, 100), "a patrol is up");
        assertFalse(BroodSpires.patrols(true, false, false, false, false, 100, 100), "its roost is filling");
        assertFalse(BroodSpires.patrols(true, false, false, true, true, 100, 100), "prey near is a launch's work");
        assertFalse(BroodSpires.patrols(true, false, false, true, false, 99, 100), "too soon");
    }

    @Test
    void aPatrolHomeShortLeavesTheRoostShort() {
        int tick = 10_000;
        int last = BroodSpires.roostAfterPatrol(Integer.MIN_VALUE / 2, tick, 0, GAP);
        assertEquals(BroodSpires.PERCHES, BroodSpires.perched(false, tick - last, GAP), "home whole: still full");
        last = BroodSpires.roostAfterPatrol(Integer.MIN_VALUE / 2, tick, 2, GAP);
        assertEquals(BroodSpires.PERCHES - 2, BroodSpires.perched(false, tick - last, GAP), "two lost: two hooks bare");
        assertEquals(BroodSpires.PERCHES, BroodSpires.perched(false, tick + GAP - last, GAP), "and it fills back");
        assertFalse(BroodSpires.releases(true, false, tick - last, true), "and holds off its launch meanwhile");
    }

    @Test
    void theSameBoreGrowsItsSpireOnTheSameCell() {
        assertArrayEquals(BroodSpires.choose(ALL, 50, 60, 0.37f, List.of(), List.of()),
                BroodSpires.choose(ALL, 50, 60, 0.37f, List.of(), List.of()));
    }

    @Test
    void aFedSatelliteAtMawRaisesOneSpire() {
        float fed = BroodSpires.FED_BIOMASS;
        assertTrue(BroodSpires.raises(false, BoreTier.MAW, fed, false));
        assertFalse(BroodSpires.raises(false, BoreTier.MAW, fed - 0.5f, false), "not fed enough");
        assertFalse(BroodSpires.raises(false, BoreTier.BORE, fed * 3, false), "fed but not yet maw");
        assertFalse(BroodSpires.raises(true, BoreTier.MAW, fed, false), "a mother's air is her brood");
        assertFalse(BroodSpires.raises(false, BoreTier.MAW, fed, true), "one spire a bore, never regrown");
        assertTrue(BroodSpires.raises(false, BoreTier.MAW, 0f, true, false), "a bore grown for a spire needs no feeding");
        assertFalse(BroodSpires.raises(false, BoreTier.BORE, 0f, true, false), "but it must reach maw");
        assertFalse(BroodSpires.raises(false, BoreTier.MAW, 0f, true, true), "and raises one only");
    }

    @Test
    void aRaisedSpireComesAtTheEndOfItsLead() {
        BroodSpires.Rising r = new BroodSpires.Rising(null, 0, 0, 100, 200);
        assertEquals(0.5f, r.progress(200), 1e-6f);
        assertFalse(r.arrived(299));
        assertTrue(r.arrived(300));
    }
}
