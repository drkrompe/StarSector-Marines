package com.dillon.starsectormarines.battle.world.gen.elevation;

import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Ramp;
import com.dillon.starsectormarines.battle.world.gen.elevation.TerracePlanner.Planned;
import com.dillon.starsectormarines.battle.world.gen.elevation.TerracePlanner.Terrace;
import org.junit.jupiter.api.Test;

import java.util.function.BiPredicate;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

class TerracePlannerTest {

    static final int W = 120, H = 100;
    static final long SEED = 7;
    /** A place's ground in the middle of an open field. */
    static final Terrace PLACE = new Terrace("place", 50, 40, 70, 60, true);

    static IntPredicate cells(BiPredicate<Integer, Integer> at) {
        return i -> at.test(i % W, i / W);
    }

    static Planned plan(IntPredicate road, IntPredicate free) {
        return TerracePlanner.plan(W, H, SEED, PLACE, road, i -> true, i -> free.test(i) && !road.test(i), i -> true);
    }

    static ElevationLayout resolve(Planned planned) {
        return ElevationLayout.resolve(new ElevationPlan(W, H, PlateauPlanner.FACE_WIDTH, PlateauPlanner.SOUTH_FACE,
                .6, SEED, planned.plateaus(), planned.ramps()));
    }

    static void assertOnTheTop(Planned planned) {
        assertTrue(planned.stands(), planned.refusal());
        ElevationLayout layout = resolve(planned);
        for (int y = PLACE.y0(); y <= PLACE.y1(); y++) {
            for (int x = PLACE.x0(); x <= PLACE.x1(); x++) {
                assertEquals(1, layout.tier(x, y), "the place's ground at " + x + "," + y);
                assertEquals(ElevationMap.GROUND, layout.kind(x, y), "no face or ramp at " + x + "," + y);
            }
        }
    }

    @Test void aRoadCrossingTheCliffIsRampedAndThePlaceStandsOnTheTop() {
        IntPredicate road = cells((x, y) -> x >= 59 && x <= 61 && y <= 45);
        Planned planned = plan(road, i -> true);
        assertOnTheTop(planned);
        assertEquals(1, planned.ramps().size());
        Ramp ramp = planned.ramps().get(0);
        assertTrue(ramp.foot().y() < PLACE.y0() && Math.abs(ramp.foot().x() - 60) <= 1,
                "the ramp climbs from the road below the south side: " + ramp);
        assertTrue(ramp.width() >= TerracePlanner.MIN_RAMP_WIDTH);
    }

    @Test void aRoadAlongASideIsTakenOntoTheTopAndRampedWhereItLeaves() {
        // Up the east side a cell outside the starting outline, then off east to the map's edge.
        IntPredicate road = cells((x, y) -> (x >= 73 && x <= 75 && y >= 44 && y <= 56) || (x >= 73 && y >= 44 && y <= 46));
        Planned planned = plan(road, i -> true);
        assertOnTheTop(planned);
        assertEquals(1, planned.ramps().size(), "only where it crosses the outline");
        assertTrue(planned.ramps().get(0).foot().x() > 75, "ramped where it leaves to the east");
        ElevationLayout layout = resolve(planned);
        for (int y = 48; y <= 56; y++) assertEquals(1, layout.tier(74, y), "the road beside the place is on the top");
    }

    @Test void withoutARoadThereIsNoWayUpAndNoTerrace() {
        Planned planned = plan(i -> false, i -> true);
        assertFalse(planned.stands());
        assertTrue(planned.refusal().contains("no road"), planned.refusal());
    }

    /** The place supplied from the south, with {@code open} saying which ground a ramp may cover. */
    static Planned supplied(IntPredicate road, IntPredicate open) {
        Terrace place = new Terrace("place", PLACE.x0(), PLACE.y0(), PLACE.x1(), PLACE.y1(), true,
                TerracePlanner.Edge.SOUTH);
        return TerracePlanner.plan(W, H, SEED, place, road, open, i -> open.test(i) && !road.test(i), i -> true);
    }

    /** The ramps whose foot is below the south side. */
    static long southRamps(Planned planned) {
        return planned.ramps().stream().filter(r -> r.foot().y() < PLACE.y0() - TerracePlanner.MARGIN).count();
    }

    @Test void theSupplySideIsRampedWhereNoRoadCrossesIt() {
        // The only road leaves by the east side; the place is supplied from the south.
        IntPredicate road = cells((x, y) -> x >= 75 && y >= 49 && y <= 51);
        Planned planned = supplied(road, i -> true);
        assertOnTheTop(planned);
        assertEquals(2, planned.ramps().size(), "the road's ramp and the supply side's");
        assertEquals(1, southRamps(planned));
        Ramp ramp = planned.ramps().stream().filter(r -> r.foot().y() < PLACE.y0()).findFirst().orElseThrow();
        assertEquals(60, ramp.foot().x(), 1, "as near the side's middle as the ground allows");
        assertTrue(ramp.width() >= TerracePlanner.MIN_RAMP_WIDTH, "a tank's width");
    }

    @Test void aSupplySideARoadAlreadyCrossesGetsNoSecondRamp() {
        IntPredicate road = cells((x, y) -> x >= 59 && x <= 61 && y <= 45);
        Planned planned = supplied(road, i -> true);
        assertOnTheTop(planned);
        assertEquals(1, planned.ramps().size());
    }

    @Test void aSupplyRampMovesAlongTheSideOffBuiltGround() {
        IntPredicate road = cells((x, y) -> x >= 75 && y >= 49 && y <= 51);
        // Built ground below the south side's cliff, under its middle.
        IntPredicate open = cells((x, y) -> !(x >= 52 && x <= 58 && y < 33));
        Planned planned = supplied(road, open);
        assertOnTheTop(planned);
        Ramp ramp = planned.ramps().stream().filter(r -> r.foot().y() < PLACE.y0()).findFirst().orElseThrow();
        assertTrue(ramp.foot().x() - ramp.width() / 2 > 58, "moved clear of the built ground: " + ramp);
    }

    @Test void aSupplySideWithNoOpenGroundBelowItDoesNotStand() {
        IntPredicate road = cells((x, y) -> x >= 75 && y >= 49 && y <= 51);
        IntPredicate open = cells((x, y) -> y >= 38 || road.test(y * W + x));
        Planned planned = supplied(road, open);
        assertFalse(planned.stands());
        assertTrue(planned.refusal().contains("south side"), planned.refusal());
    }

    @Test void aCliffIsNeverStoodOnBuiltGround() {
        IntPredicate road = cells((x, y) -> x >= 59 && x <= 61 && y <= 45);
        // Built ground all round the place, out past every margin it could be pushed to.
        Planned planned = plan(road, i -> false);
        assertFalse(planned.stands());
        assertTrue(planned.refusal().contains("built ground"), planned.refusal());
    }
}
