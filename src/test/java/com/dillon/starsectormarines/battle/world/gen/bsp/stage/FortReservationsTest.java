package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.FortSitingPolicy;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortSiting;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.precinct.LandingKind;
import com.dillon.starsectormarines.battle.world.gen.precinct.MapPlacement;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.Standoff;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ground a mission reserves for its forts before the town grows: one reservation for every declared fort, kept
 * clear of road growth, and slid off a place the plan programs because a programmed place never gives its ground up.
 */
class FortReservationsTest {

    private static final int W = 560;
    private static final int H = 336;
    private static final int KEEP_X = 480;
    private static final int LANDING_X = 80;
    private static final int MID_Y = 168;
    /** A programmed place standing on the first battery fort's nominal ground. */
    private static final int LANE_X = 320;
    private static final int LANE_Y = MID_Y + 48;

    private static GenContext context(FortSitingPolicy siting, boolean placeOnApproach) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        GenContext ctx = new GenContext(grid, topology, new Random(7L), W, H, 7L);
        ctx.put(BspKeys.MARKET_PROFILE, TargetProfile.NEUTRAL.withDeclaredDefences(new GroundDefences(0, false)
                .withForts(FortKind.BATTERY_FORT, 2).withForts(FortKind.SHIELD_FORT, 1).withSiting(siting)));
        List<Precinct> places = new ArrayList<>(List.of(
                Precinct.garrison("keep", KEEP_X, MID_Y, GrownTrunkPlan.Profile.hamlet(), FortressProgram.garrison()),
                Precinct.landing("landing", LANDING_X, MID_Y, GrownTrunkPlan.Profile.hamlet(),
                        LandingKind.FIELD, LandingKind.FIELD.program())));
        if (placeOnApproach) {
            places.add(Precinct.garrison("lane", LANE_X, LANE_Y, GrownTrunkPlan.Profile.hamlet(),
                    FortressProgram.garrison()));
        }
        ctx.put(BspKeys.PRECINCTS, new PrecinctPlan(places, MapPlacement.WEST, Standoff.FAR));
        return ctx;
    }

    private static Map<FortKind, List<FortSiting.Point>> reserve(GenContext ctx) {
        PrecinctPlan plan = ctx.get(BspKeys.PRECINCTS);
        FortReservations.keepClear(ctx, plan);
        return ctx.get(BspKeys.FORT_TARGETS);
    }

    @Test
    void everyDeclaredFortGetsGroundNotOnlyTheFirst() {
        Map<FortKind, List<FortSiting.Point>> sites = reserve(context(FortSitingPolicy.COVER_THE_APPROACH, false));
        assertEquals(2, sites.get(FortKind.BATTERY_FORT).size());
        assertEquals(1, sites.get(FortKind.SHIELD_FORT).size());
        FortSiting.Point a = sites.get(FortKind.BATTERY_FORT).get(0);
        FortSiting.Point b = sites.get(FortKind.BATTERY_FORT).get(1);
        assertTrue(Math.hypot(a.x() - b.x(), a.y() - b.y()) >= FortSiting.SEPARATION, "forts reserved flush");
    }

    @Test
    void theReservationIsKeptClearOfRoadGrowthAtEveryTarget() {
        GenContext ctx = context(FortSitingPolicy.COVER_THE_APPROACH, false);
        boolean[][] mask = FortReservations.keepClear(ctx, ctx.get(BspKeys.PRECINCTS));
        assertNotNull(mask);
        for (List<FortSiting.Point> targets : ctx.get(BspKeys.FORT_TARGETS).values()) {
            for (FortSiting.Point t : targets) assertTrue(mask[t.x()][t.y()], "no reservation at " + t);
        }
    }

    @Test
    void aTargetOnAProgrammedPlaceSlidesOffItAlongTheApproach() {
        FortSiting.Anchors anchors = FortReservations.anchors(
                context(FortSitingPolicy.COVER_THE_APPROACH, true).get(BspKeys.PRECINCTS), W, H);
        List<FortSiting.Point> nominal = FortSiting.batteryTargets(anchors, 2);
        Map<FortKind, List<FortSiting.Point>> free = reserve(context(FortSitingPolicy.COVER_THE_APPROACH, false));
        Map<FortKind, List<FortSiting.Point>> blocked = reserve(context(FortSitingPolicy.COVER_THE_APPROACH, true));
        List<FortSiting.Point> moved = blocked.get(FortKind.BATTERY_FORT);
        assertEquals(2, moved.size(), "the declared count stands");
        boolean slid = false;
        for (int i = 0; i < 2; i++) {
            if (!moved.get(i).equals(free.get(FortKind.BATTERY_FORT).get(i))) slid = true;
            // Still on the way between the keep and the landing, never behind the one or beyond the other.
            assertTrue(FortSiting.approachCost(anchors, nominal.get(i), moved.get(i).x(), moved.get(i).y())
                    < FortSiting.OFF_APPROACH, "left the approach: " + moved.get(i));
            // And not on the lane place's seed.
            assertTrue(Math.hypot(moved.get(i).x() - LANE_X, moved.get(i).y() - LANE_Y) > 60,
                    "still on the programmed place: " + moved.get(i));
        }
        assertTrue(slid, "a programmed place sat on a target and nothing moved");
    }

    @Test
    void aMissionThatDoesNotOptInReservesNothing() {
        GenContext ctx = context(FortSitingPolicy.LEFTOVER_GROUND, false);
        assertNull(FortReservations.keepClear(ctx, ctx.get(BspKeys.PRECINCTS)));
        assertTrue(ctx.get(BspKeys.FORT_TARGETS).isEmpty());
    }
}
