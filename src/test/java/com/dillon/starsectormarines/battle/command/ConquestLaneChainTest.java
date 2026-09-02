package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.world.gen.precinct.LaneRoute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chain reading, asked directly of two hand-written lanes.
 *
 * <p>No map is generated: what is being asked is whether a lane's front is the
 * first place nobody holds and whether a compound lands on the place whose
 * ground it stands on, and a generated map would answer for the generator as
 * well.
 */
class ConquestLaneChainTest {

    /** A straight route from (0,y) to (60,y), links every twenty cells. */
    private static LaneRoute lane(int lane, int y, int... claimWidths) {
        List<LaneRoute.Cell> route = new ArrayList<>();
        for (int x = 0; x <= 60; x++) route.add(new LaneRoute.Cell(x, y));
        List<LaneRoute.Link> links = new ArrayList<>();
        int[] widths = claimWidths.length > 0 ? claimWidths : new int[]{4, 4, 4, 4};
        for (int i = 0; i < widths.length; i++) {
            int x = i * 20;
            int band = i < 3 ? 3 - i : LaneRoute.OBJECTIVE_BAND;
            links.add(new LaneRoute.Link("lane-" + (lane + 1) + "-band-" + band,
                    band, x, y, x, x - widths[i], y - widths[i],
                    x + widths[i], y + widths[i]));
        }
        return new LaneRoute(lane, links, route);
    }

    private static ConquestLaneChain.Compound compound(int zone, int x, int y) {
        return new ConquestLaneChain.Compound(zone, x, y);
    }

    private static IntPredicate held(int... zones) {
        return zone -> {
            for (int z : zones) if (z == zone) return true;
            return false;
        };
    }

    @Test
    @DisplayName("a compound lands on the place whose claimed ground it stands on")
    void compoundsLandOnTheirOwnPlace() {
        ConquestLaneChain chain = ConquestLaneChain.of(List.of(lane(0, 10)),
                List.of(compound(7, 1, 11), compound(8, 21, 9),
                        compound(9, 100, 100)));

        assertArrayEquals(new int[]{7}, chain.links(0).get(0).captureZoneIds());
        assertArrayEquals(new int[]{8}, chain.links(0).get(1).captureZoneIds());
        assertEquals(0, chain.laneOfCompound(7));
        assertEquals(1, chain.linkOfCompound(8));
        assertFalse(chain.isOnChain(9), "a compound off every claim is off the chain");
        assertEquals(-1, chain.laneOfCompound(9));
    }

    @Test
    @DisplayName("the front is the first place the marines do not hold")
    void frontIsTheFirstPlaceNotHeld() {
        ConquestLaneChain chain = ConquestLaneChain.of(List.of(lane(0, 10)),
                List.of(compound(1, 0, 10), compound(2, 20, 10),
                        compound(3, 40, 10), compound(4, 60, 10)));

        assertEquals(0, chain.frontLink(0, held()));
        assertEquals(1, chain.frontLink(0, held(1)));
        assertEquals(2, chain.frontLink(0, held(1, 2)));
        assertEquals(4, chain.frontLink(0, held(1, 2, 3, 4)),
                "a lane held to its end has no front left");
    }

    @Test
    @DisplayName("a place is held only when every compound on it is")
    void aPlaceIsHeldOnlyWhenAllOfItIs() {
        ConquestLaneChain chain = ConquestLaneChain.of(List.of(lane(0, 10)),
                List.of(compound(1, 0, 10), compound(2, 20, 10),
                        compound(3, 21, 11), compound(4, 40, 10)));

        assertEquals(1, chain.frontLink(0, held(1)));
        assertEquals(1, chain.frontLink(0, held(1, 2)),
                "half a strongpoint is not a strongpoint taken");
        assertEquals(2, chain.frontLink(0, held(1, 2, 3)));
    }

    @Test
    @DisplayName("a front moves back when a place behind it is retaken")
    void theFrontMovesBackOnALoss() {
        ConquestLaneChain chain = ConquestLaneChain.of(List.of(lane(0, 10)),
                List.of(compound(1, 0, 10), compound(2, 20, 10),
                        compound(3, 40, 10), compound(4, 60, 10)));

        assertEquals(2, chain.frontLink(0, held(1, 2)));
        assertEquals(0, chain.frontLink(0, held(2)),
                "losing the outermost place is the front coming back to it");
        assertEquals(0.25f, chain.progress(0, held(2)), 0.0001f);
    }

    @Test
    @DisplayName("a place with nothing on it is stepped over rather than blocking the lane")
    void anEmptyRungIsSteppedOver() {
        ConquestLaneChain chain = ConquestLaneChain.of(List.of(lane(0, 10)),
                List.of(compound(1, 0, 10), compound(3, 40, 10)));

        assertFalse(chain.links(0).get(1).hasCompounds());
        assertEquals(2, chain.frontLink(0, held(1)),
                "the empty rung is not a place that can be taken");
        assertEquals(0.5f, chain.progress(0, held(1)), 0.0001f);
        assertEquals(1f, chain.progress(0, held(1, 3)), 0.0001f);
    }

    @Test
    @DisplayName("the objective stands on every lane's chain")
    void theObjectiveIsOnEveryChain() {
        ConquestLaneChain chain = ConquestLaneChain.of(
                List.of(lane(0, 10), lane(1, 30)),
                List.of(compound(1, 0, 10), compound(2, 20, 10),
                        compound(3, 40, 10), compound(4, 60, 10),
                        compound(5, 0, 30), compound(6, 20, 30),
                        compound(7, 40, 30), compound(8, 60, 30)));

        assertArrayEquals(new int[]{4}, chain.links(0).get(3).captureZoneIds());
        assertArrayEquals(new int[]{8}, chain.links(1).get(3).captureZoneIds());
        assertEquals(3, chain.linkIndexOn(1, 8));
        assertEquals(-1, chain.linkIndexOn(1, 4),
                "lane 1 does not hold lane 0's places");
    }

    @Test
    @DisplayName("a place that is not the objective belongs to one lane only")
    void anOrdinaryPlaceIsOnOneChain() {
        // Two lanes whose claim boxes overlap on the outermost rung. Bounds are
        // rectangles and claims are not, so this is a real map's failure mode:
        // one compound on both chains would stop both fronts on it.
        ConquestLaneChain chain = ConquestLaneChain.of(
                List.of(lane(0, 10, 12, 4, 4, 4), lane(1, 16, 12, 4, 4, 4)),
                List.of(compound(1, 0, 11)));

        int onFirst = chain.links(0).get(0).captureZoneIds().length;
        int onSecond = chain.links(1).get(0).captureZoneIds().length;
        assertEquals(1, onFirst + onSecond, "the nearer place takes it, and only it");
        assertEquals(1, onFirst, "the first lane's rung is the nearer of the two");
        assertEquals(0, chain.laneOfCompound(1));
    }

    @Test
    @DisplayName("progress along the route is the nearest recorded cell")
    void routeProgressIsMeasuredOnTheRoute() {
        ConquestLaneChain chain = ConquestLaneChain.of(List.of(lane(0, 10)),
                List.of(compound(1, 0, 10)));

        assertEquals(0, chain.routeIndexNear(0, 0.5f, 10.5f));
        assertEquals(37, chain.routeIndexNear(0, 37.5f, 14f),
                "a squad beside the road is still at its own point along it");
        assertEquals(61, chain.routeLength(0));
        assertEquals(20, chain.links(0).get(1).routeIndex());
    }

    @Test
    @DisplayName("a map with no lanes answers nothing rather than throwing")
    void aMapWithNoLanesIsSilent() {
        ConquestLaneChain chain = ConquestLaneChain.of(List.of(), List.of());
        assertEquals(0, chain.laneCount());
        assertFalse(chain.hasChains());
        assertEquals(-1, chain.laneOfCompound(3));
        assertEquals(0, chain.frontLink(0, held()));
        assertTrue(chain.links(0).isEmpty());
        assertEquals(-1, chain.routeIndexNear(0, 1f, 1f));
    }
}
