package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.AirbaseLot;
import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A ward that has room for an airbase gets one.
 *
 * <p>It did not, and the way it failed is the point. The ward asked for the
 * whole installation at one hardcoded size, pinned to one end of itself, and
 * returned nothing when either the size or that end did not work — no error, no
 * fallback, just a garrison with no air arm. It fitted on the one map size the
 * generation tests run at, so nothing ever said otherwise.
 *
 * <p>Asked of the placement rather than of a generated city: the question is
 * entirely about this decision, and a city is a slow way to pose it.
 */
class WardAirbasePlacementTest {

    private static final TraversalAxis AXIS = TraversalAxis.SOUTH_TO_NORTH;

    /** A ward with room for the full installation and the buildings beside it. */
    private static int[] wardWithRoomForAField() {
        AirbaseLot.Facing facing = AirbaseLot.Facing.of(AXIS);
        int spanX = AirbaseLot.reservedSpanX(AirbaseLot.Size.FIELD, facing);
        int spanY = AirbaseLot.reservedSpanY(AirbaseLot.Size.FIELD, facing);
        int needed = FortressProgram.ward().buildingGround();
        // Wide enough for the lot plus the buildings' ground at this depth.
        int width = spanX + ceilDiv(needed, spanY) + 4;
        return new int[]{ 10, 10, 10 + width - 1, 10 + spanY - 1 };
    }

    private static int ceilDiv(int a, int b) { return (a + b - 1) / b; }

    @Test
    void aClearWardTakesTheWholeInstallation() {
        FortressWardStage.WardAirbase base =
                FortressWardStage.airbaseLot(wardWithRoomForAField(), AXIS, null, null);

        assertNotNull(base, "a ward with room for the field got no airbase at all");
        assertEquals(AirbaseLot.Size.FIELD, base.size(),
                "the ward settled for less than it had room for");
    }

    /**
     * A road across the preferred end costs the base that end, not the base.
     *
     * <p>This is the case that was silently losing the airfield: the lot is
     * pinned to the end away from the citadel, and the ward keeps exactly one
     * road that the base may not sever. Preferring an end is right; giving up
     * when it is taken is not.
     */
    @Test
    void aRoadAcrossOnePreferredEndDoesNotCostTheWardItsAirbase() {
        int[] ward = wardWithRoomForAField();
        boolean[][] roads = new boolean[ward[2] + 40][ward[3] + 40];
        // A road down the low end, where the lot is placed when no citadel
        // pulls it the other way.
        int spanX = AirbaseLot.reservedSpanX(AirbaseLot.Size.FIELD,
                AirbaseLot.Facing.of(AXIS));
        for (int y = ward[1]; y <= ward[3]; y++) roads[ward[0] + spanX / 2][y] = true;

        FortressWardStage.WardAirbase base =
                FortressWardStage.airbaseLot(ward, AXIS, null, roads);

        assertNotNull(base, "a road across one end of the ward lost the airbase entirely");
        assertEquals(AirbaseLot.Size.FIELD, base.size(),
                "the ward shrank its base to dodge a road instead of moving to its other end");
        for (int x = base.rect()[0]; x <= base.rect()[2]; x++)
            for (int y = base.rect()[1]; y <= base.rect()[3]; y++)
                assertTrue(!roads[x][y],
                        "the airbase was placed across the road it may not sever");
    }

    /**
     * The lot never lands on the keep.
     *
     * <p>The keep is the one thing in the ward the stage does not demolish, so
     * a lot laid across it repaves ground whose building, capture objective and
     * garrison node all survive the clearing — which is a bare roof and a
     * capture marker standing on the apron with nothing under them. Measured
     * over forty-eight generated Conquest maps this happened on eleven of them.
     *
     * <p>Asked of the ward's own decision, because that is where the mistake
     * is. The lot itself is blameless: handed a rectangle, it lays a base in it.
     *
     * <p>The keep here reaches into both end positions. A narrow one in the
     * middle proves nothing — the lot is already pinned to an end and clears it
     * by accident — and a fixture that passes with the rule taken out is a test
     * of the fixture rather than of the rule.
     */
    @Test
    void theLotIsNeverLaidOverTheKeep() {
        int[] ward = wardWithRoomForAField();
        int spanX = AirbaseLot.reservedSpanX(AirbaseLot.Size.FIELD,
                AirbaseLot.Facing.of(AXIS));
        // Reaching half a lot's width into each end, so a full-size base is
        // over the keep whichever end it is pinned to. The ladder still has the
        // smaller sizes to come down to.
        Compound keep = keepAt(ward[0] + spanX / 2, ward[1],
                ward[2] - spanX / 2, ward[3]);

        FortressWardStage.WardAirbase base =
                FortressWardStage.airbaseLot(ward, AXIS, keep, null);

        assertNotNull(base, "a ward with a keep across the middle of it lost the airbase entirely");
        int[] lot = base.rect();
        assertTrue(lot[2] < keep.left || lot[0] > keep.right
                        || lot[3] < keep.top || lot[1] > keep.bottom,
                "the airbase reservation was laid over the keep: lot "
                        + lot[0] + "," + lot[1] + ".." + lot[2] + "," + lot[3]
                        + " keep " + keep.left + "," + keep.top
                        + ".." + keep.right + "," + keep.bottom);
    }

    /**
     * A keep that leaves nowhere clear costs the base, not the keep.
     *
     * <p>The bargain the ward already makes for its road: a facility that would
     * take ground the mission depends on is not sited at all. Three of those
     * forty-eight maps lose their air arm this way, which is the price of the
     * other eleven being honest.
     */
    @Test
    void aKeepAcrossTheWholeWardCostsTheAirbase() {
        int[] ward = wardWithRoomForAField();
        Compound keep = keepAt(ward[0], ward[1], ward[2], ward[3]);

        assertNull(FortressWardStage.airbaseLot(ward, AXIS, keep, null),
                "the ward built an airbase on top of a keep filling the whole ward");
    }

    private static Compound keepAt(int left, int top, int right, int bottom) {
        BlockLeaf leaf = new BlockLeaf(left, top, right, bottom, false);
        return new Compound(BlockKind.MILITARY_BASE, leaf, List.of(leaf),
                new IdentityHashMap<>(), BiomeKind.FORTRESS_DISTRICT);
    }
}
