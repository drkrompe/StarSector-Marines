package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a knoll stands and how its place's roads leave it ({@code elevation-nouns.md}, Knolls). */
class KnollGroundTest {

    private static final int W = 200, H = 160;
    private static final GrownTrunkPlan.Profile GROWTH = GrownTrunkPlan.Profile.town();

    private static Landscape.Knoll knoll(String place, List<Direction> gates) {
        return new Landscape.Knoll("knoll", place, 20, 16, gates, true);
    }

    @Test
    void theTopIsMovedInFromTheMapsEdgeRatherThanShrunk() {
        // Twenty cells either side of a seed eight cells in, with twenty of ring and edge beyond: moved in whole.
        assertArrayEquals(new int[]{20, 60}, KnollGround.span(26, 20, 20, 200));
        assertArrayEquals(new int[]{60, 100}, KnollGround.span(80, 20, 20, 200));
        assertNull(KnollGround.span(5, 20, 20, 200), "moved that far, the seed would stand at the top's edge");
        assertNull(KnollGround.span(50, 40, 20, 100), "the top and its ring do not fit the map");
    }

    @Test
    void aKnollKeepsOffAnotherPlacesSeed() {
        Precinct town = Precinct.settlement("town", 100, 80, GROWTH);
        Precinct far = Precinct.settlement("far", 180, 20, GROWTH);
        Precinct near = Precinct.settlement("near", 145, 80, GROWTH);
        KnollGround.Resolved stands = KnollGround.resolve(List.of(knoll("town", List.of(Direction.S))),
                List.of(town, far), W, H).get(0);
        assertTrue(stands.stands(), stands.refusal());
        assertEquals(80, stands.x0());
        assertEquals(120, stands.x1());
        KnollGround.Resolved refused = KnollGround.resolve(List.of(knoll("town", List.of(Direction.S))),
                List.of(town, near), W, H).get(0);
        assertFalse(refused.stands(), "a seed fourteen cells past the ring leaves no room for that place to start");
    }

    @Test
    void aPlaceStandsOnOneKnoll() {
        Precinct town = Precinct.settlement("town", 100, 80, GROWTH);
        List<KnollGround.Resolved> out = KnollGround.resolve(List.of(knoll("town", List.of(Direction.S)),
                new Landscape.Knoll("again", "town", 20, 16, List.of(Direction.N), false)), List.of(town), W, H);
        assertTrue(out.get(0).stands());
        assertFalse(out.get(1).stands());
    }

    @Test
    void theRingKeepsTheTopsEdgeBuiltOnlyInsideItsLane() {
        KnollGround.Resolved k = new KnollGround.Resolved(knoll("town", List.of(Direction.S)), 0, 80, 64, 120, 96, null);
        assertTrue(k.onTop(80, 64) && !k.onRing(80, 64));
        assertTrue(k.onRing(79, 64) && k.onRing(80 - 14, 64) && !k.onGround(80 - 15, 64));
        assertFalse(k.buildable(80 + KnollGround.LANE - 1, 80), "the lane round the top's edge is no one's to build on");
        assertTrue(k.buildable(80 + KnollGround.LANE, 80));
    }

    /** A gate runs straight from the place's outermost street on the seed's line out across the ring. */
    @Test
    void aGateIsAStraightRoadFromTheStreetsOutAcrossTheRing() {
        int[][] owner = new int[W][H];
        for (int[] column : owner) Arrays.fill(column, GrownTrunkPlan.UNOWNED);
        // The place's street across the top a few cells south of its seed, and another place's road beyond the ring.
        for (int x = 85; x <= 115; x++) for (int y = 72; y <= 74; y++) owner[x][y] = 0;
        for (int x = 90; x <= 110; x++) owner[x][40] = 1;
        KnollGround.Resolved k = new KnollGround.Resolved(knoll("town", List.of(Direction.S)), 0, 80, 64, 120, 96, null);
        KnollGround.carveGates(owner, k, 100, 80, W, H);
        int half = KnollGround.GATE_WIDTH / 2;
        for (int y = 64 - 14 - 2; y < 72; y++) {
            for (int x = 100 - half; x <= 100 + half; x++) assertEquals(0, owner[x][y], "the gate's road at " + x + "," + y);
            assertEquals(GrownTrunkPlan.UNOWNED, owner[100 - half - 1][y], "the gate is " + KnollGround.GATE_WIDTH + " across");
        }
        assertEquals(GrownTrunkPlan.UNOWNED, owner[100][64 - 14 - 3], "the gate stops just past the ring");
        assertEquals(GrownTrunkPlan.UNOWNED, owner[100][78], "nothing is carved inward of the street it leaves from");
        assertEquals(1, owner[90][40], "another place's road is left as it is");
    }
}
