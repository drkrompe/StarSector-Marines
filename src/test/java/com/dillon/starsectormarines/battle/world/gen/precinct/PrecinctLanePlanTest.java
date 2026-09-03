package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a plan puts its lane places, and what it says when it cannot.
 *
 * <p>Asked of the derivation directly rather than of a generated map: this is
 * about seeding, and a whole map generation would answer for the fill as well.
 * What the finished map does with them is {@code ConquestOnPrecinctsTest}'s
 * question.
 */
class PrecinctLanePlanTest {

    private static final TargetProfile DEFENDED_TOWN = new TargetProfile(
            5, 7, 2, 1, "independent",
            EnumSet.of(EconomicFunction.HABITATION, EconomicFunction.SPACEPORT),
            SurfacePalette.ROCK, SettlementLink.ROAD);

    /**
     * The whole ladder stands up on the map the model was measured at, one rung
     * per band on every lane, and every rung inside its own lane.
     */
    @Test
    void everyRungStandsInItsOwnLane() {
        PrecinctPlan plan = conquestLike(PrecinctPlan.Lanes.derived(),
                MapScale.CONQUEST.width, MapScale.CONQUEST.height, 4096L);
        List<Precinct> lanes = lanePlaces(plan);
        assertEquals(9, lanes.size(),
                "three lanes of three rungs, and " + plan.unplacedLanePlaces()
                        + " could not be seated");
        assertTrue(plan.unplacedLanePlaces().isEmpty());

        // Objective placement EAST against attacker WEST, so forward is x and
        // the lanes are cut across y.
        int extent = MapScale.CONQUEST.height;
        for (Precinct place : lanes) {
            int lane = laneOf(place);
            int start = LaneGeometry.startInclusive(lane - 1, 3, extent);
            int end = LaneGeometry.endInclusive(lane - 1, 3, extent);
            assertTrue(place.seedY() >= start && place.seedY() <= end,
                    place.name() + " seeded at y=" + place.seedY()
                            + ", outside its own lane " + start + ".." + end);
        }
    }

    /**
     * A rung stands between the force and the thing it came for, in the order
     * the ladder names — band 3 nearest the attacker, band 1 abutting the
     * objective.
     */
    @Test
    void theRungsRunFromTheAttackerToTheObjective() {
        PrecinctPlan plan = conquestLike(PrecinctPlan.Lanes.derived(),
                MapScale.CONQUEST.width, MapScale.CONQUEST.height, 4096L);
        Precinct objective = plan.objective();
        assertNotNull(objective);
        int attackerForward = MapPlacement.WEST.centre(
                MapScale.CONQUEST.width, MapScale.CONQUEST.height)[0];

        for (int lane = 1; lane <= 3; lane++) {
            int outer = forwardOf(plan, "lane-" + lane + "-band-3");
            int middle = forwardOf(plan, "lane-" + lane + "-band-2");
            int inner = forwardOf(plan, "lane-" + lane + "-band-1");
            assertTrue(attackerForward < outer && outer < middle && middle < inner
                            && inner < objective.seedX(),
                    "lane " + lane + " runs " + attackerForward + " -> " + outer + " -> "
                            + middle + " -> " + inner + " -> " + objective.seedX()
                            + ", which is not a ladder from the beachhead to the fortress");
        }
    }

    /** A lane place is a walled garrison precinct in the full sense, and holds no keep. */
    @Test
    void aLanePlaceIsAWalledGarrisonWithoutAKeep() {
        PrecinctPlan plan = conquestLike(PrecinctPlan.Lanes.derived(),
                MapScale.CONQUEST.width, MapScale.CONQUEST.height, 4096L);
        for (Precinct place : lanePlaces(plan)) {
            assertTrue(place.isProgrammed(), place.name() + " packs nothing");
            assertEquals(Precinct.Boundary.WALLED, place.boundary());
            assertNotNull(place.fortification());
            assertEquals(0, place.program().countOf(RoomPurpose.KEEP_THRONE),
                    place.name() + " carries a keep, so the map has two");
            assertEquals(0, place.program().airfields());
        }
        assertEquals(plan.objective(), plan.precincts().stream()
                        .filter(Precinct::isProgrammed).findFirst().orElseThrow(),
                "a lane place displaced the fortress as the objective");
    }

    /**
     * A map with no room for a rung says which one, rather than coming out as a
     * shorter ladder nobody asked for.
     */
    @Test
    void aMapTooSmallDropsRungsAndNamesThem() {
        PrecinctPlan plan = conquestLike(PrecinctPlan.Lanes.derived(),
                MapScale.SMALL.width, MapScale.SMALL.height, 4096L);
        assertFalse(plan.unplacedLanePlaces().isEmpty(),
                "a 112x64 map seated all nine rungs, which means the separation "
                        + "rule is not being applied");
        assertEquals(9, lanePlaces(plan).size() + plan.unplacedLanePlaces().size(),
                "every rung is either a place or a name, never neither");
    }

    /** A mission that states its own ladder gets it; an unstated lane derives. */
    @Test
    void aStatedLadderReachesTheMap() {
        List<LaneResistance> stated = new ArrayList<>();
        stated.add(LaneResistance.stated(List.of(new LaneResistance.Rung(
                1, Fortification.Strength.CITADEL, LaneResistance.Kind.STRONGPOINT))));
        PrecinctPlan plan = conquestLike(new PrecinctPlan.Lanes(3, stated),
                MapScale.CONQUEST.width, MapScale.CONQUEST.height, 4096L);

        List<Precinct> lane1 = new ArrayList<>();
        for (Precinct place : lanePlaces(plan)) {
            if (laneOf(place) == 1) lane1.add(place);
        }
        assertEquals(1, lane1.size(), "a stated one-rung lane grew extra rungs");
        assertEquals(Fortification.CITADEL, lane1.get(0).fortification());
        assertEquals(3, lanePlaces(plan).stream()
                        .filter(place -> laneOf(place) == 2).count(),
                "an unstated lane derives its own three rungs");
    }

    /** A lane count of one is a lane count of one, not the default. */
    @Test
    void theStatedCountIsTheCount() {
        PrecinctPlan plan = conquestLike(PrecinctPlan.Lanes.of(1),
                MapScale.CONQUEST.width, MapScale.CONQUEST.height, 4096L);
        assertEquals(3, lanePlaces(plan).size());
        for (Precinct place : lanePlaces(plan)) assertEquals(1, laneOf(place));
    }

    /** A plan that states no lanes lays none, which is every mission but Conquest. */
    @Test
    void aPlanWithNoLanesLaysNone() {
        PrecinctPlan plan = PrecinctPlan.derive(DEFENDED_TOWN,
                PrecinctPlan.Sprawl.BALANCED, Fortification.Demand.UNSTATED,
                MapPlacement.EAST, MapPlacement.WEST,
                MapScale.CONQUEST.width, MapScale.CONQUEST.height, new Random(4096L));
        assertNull(plan.lanes());
        assertTrue(lanePlaces(plan).isEmpty());
        assertTrue(plan.unplacedLanePlaces().isEmpty());
    }

    /**
     * A stated zig-zag seeds its places at its own waypoints, in path order.
     *
     * <p>Not on them to the cell: a rung is jittered inside its lane so a
     * neighbour crowding it has somewhere to stand. What the path decides is
     * where in the lane it stands, so the assertion is that each rung is beside
     * its own waypoint and that the bend survives — the middle rung of a
     * three-point zig-zag is on the other side of the lane from its neighbours.
     */
    @Test
    void aStatedZigZagSeedsItsPlacesAtItsWaypoints() {
        int width = MapScale.CONQUEST.width;
        int height = MapScale.CONQUEST.height;
        // Lane 1 of three, so the strip is the production one: out east along
        // the low side, up across the strip, back down toward the keep. The
        // other two lanes derive, which is also what a mission stating one
        // lane's route and leaving the rest alone gets.
        LanePath zigzag = LanePath.ofCells(width, height, 200, 45, 300, 100, 400, 45);
        PrecinctPlan plan = conquestLike(
                new PrecinctPlan.Lanes(3, List.of(), List.of(zigzag)),
                width, height, 4096L);

        assertTrue(plan.unplacedLanePlaces().isEmpty(),
                "the stated path dropped " + plan.unplacedLanePlaces());
        // Within the jitter a rung is allowed inside its own lane — a fifth of
        // a 82-cell strip here — because a rung crowded out of its waypoint
        // still has to have somewhere to stand.
        int jitter = 20;
        int[][] expected = {{200, 45}, {300, 100}, {400, 45}};
        String[] bands = {"lane-1-band-3", "lane-1-band-2", "lane-1-band-1"};
        for (int i = 0; i < bands.length; i++) {
            Precinct place = named(plan, bands[i]);
            assertEquals(expected[i][0], place.seedX(),
                    bands[i] + " stands off its waypoint's forward position");
            assertTrue(Math.abs(place.seedY() - expected[i][1]) <= jitter,
                    bands[i] + " seeded at y=" + place.seedY() + ", nowhere near its "
                            + "waypoint at y=" + expected[i][1]);
        }
        assertTrue(named(plan, "lane-1-band-2").seedY()
                        > named(plan, "lane-1-band-3").seedY() + 15,
                "the bend was flattened out of the stated path");
    }

    /**
     * A rung may stand closer to its own ladder than to a settlement.
     *
     * <p>The two separations are different numbers for different questions, and
     * they only stay different while the two lists of seeds do. A rung joins the
     * map's list of taken ground the moment it is seated — the town and the
     * hamlets are placed afterwards and must keep clear of it — and testing the
     * next rung against that merged list finds it there at
     * {@link PrecinctPlan#MIN_SEED_SEPARATION}, so
     * {@link PrecinctPlan#LANE_SEED_SEPARATION} never applies to anything.
     *
     * <p>The path states its waypoints forty cells apart, which is a ladder in
     * lane terms and two places on top of each other in settlement terms. All
     * three rungs stand, no two of them are sixty cells apart, and every one of
     * them is still sixty from the fortress and the town.
     */
    @Test
    void aRungStandsCloserToItsLadderThanToASettlement() {
        int width = MapScale.CONQUEST.width;
        int height = MapScale.CONQUEST.height;
        // Lane 1 of three, up its own strip's centreline, with the rungs closer
        // together than two settlements are ever allowed to be.
        LanePath tight = LanePath.ofCells(width, height, 200, 70, 240, 70, 280, 70);
        PrecinctPlan plan = conquestLike(
                new PrecinctPlan.Lanes(3, List.of(), List.of(tight)), width, height, 4096L);

        List<Precinct> ladder = new ArrayList<>();
        for (Precinct place : lanePlaces(plan)) {
            if (laneOf(place) == 1) ladder.add(place);
        }
        assertEquals(3, ladder.size(), "the tight ladder dropped rungs: "
                + plan.unplacedLanePlaces());

        for (int i = 1; i < ladder.size(); i++) {
            double gap = distance(ladder.get(i - 1), ladder.get(i));
            assertTrue(gap < PrecinctPlan.MIN_SEED_SEPARATION,
                    ladder.get(i).name() + " stands " + Math.round(gap) + " cells from "
                            + ladder.get(i - 1).name() + ", which is no closer than two "
                            + "settlements may stand — the lane separation did not apply");
            assertTrue(gap >= PrecinctPlan.LANE_SEED_SEPARATION,
                    ladder.get(i).name() + " stands " + Math.round(gap) + " cells from "
                            + ladder.get(i - 1).name() + ", inside its own ladder's "
                            + PrecinctPlan.LANE_SEED_SEPARATION);
        }

        for (Precinct place : plan.precincts()) {
            if (place.name().startsWith("lane-")) continue;
            for (Precinct rung : ladder) {
                assertTrue(distance(place, rung) >= PrecinctPlan.MIN_SEED_SEPARATION,
                        rung.name() + " stands " + Math.round(distance(place, rung))
                                + " cells from " + place.name() + ", which is a pocket "
                                + "inside it rather than a place on the way to it");
            }
        }
    }

    /**
     * A waypoint stated inside somewhere else slides along its own path, and the
     * plan says so.
     *
     * <p>The middle waypoint is put on the fortress's own doorstep, which is the
     * one place a lane place may not stand. It gives way toward the objective —
     * a rung that has to move should move to the front rather than back into the
     * force behind it — and the move is named, because a route quietly changed
     * is a route nobody wrote.
     */
    @Test
    void aStatedWaypointInsideAnotherPlaceIsMovedAndReported() {
        int width = MapScale.CONQUEST.width;
        int height = MapScale.CONQUEST.height;
        PrecinctPlan reference = conquestLike(PrecinctPlan.Lanes.of(1),
                width, height, 4096L);
        Precinct objective = reference.objective();
        assertNotNull(objective);

        LanePath onTopOfTheKeep = LanePath.ofCells(width, height,
                200, objective.seedY(),
                objective.seedX() - 10, objective.seedY(),
                objective.seedX() - 5, objective.seedY());
        PrecinctPlan plan = conquestLike(
                PrecinctPlan.Lanes.along(List.of(onTopOfTheKeep)), width, height, 4096L);

        assertFalse(plan.movedLaneWaypoints().isEmpty(),
                "a waypoint on the fortress was seeded where it was asked for");
        assertTrue(plan.movedLaneWaypoints().get(0).startsWith("lane-1 waypoint "),
                "the report names the lane and the waypoint: "
                        + plan.movedLaneWaypoints());
        assertEquals(reference.objective().seedX(), plan.objective().seedX(),
                "moving a waypoint moved the fortress");
    }

    /** A derived plan states no moves, because nothing had to give way. */
    @Test
    void aDerivedPlanMovesNoWaypoints() {
        PrecinctPlan plan = conquestLike(PrecinctPlan.Lanes.derived(),
                MapScale.CONQUEST.width, MapScale.CONQUEST.height, 4096L);
        assertTrue(plan.movedLaneWaypoints().isEmpty(),
                "a derived path had to be fitted: " + plan.movedLaneWaypoints());
    }

    /**
     * Two derived lanes on one map are not parallel lines.
     *
     * <p>What the meandering derivation is for. A straight ladder puts every
     * rung of a lane on one lateral, so the reading is simply whether a lane's
     * rungs differ from each other laterally at all.
     */
    @Test
    void aDerivedLaneBends() {
        PrecinctPlan plan = conquestLike(PrecinctPlan.Lanes.derived(),
                MapScale.CONQUEST.width, MapScale.CONQUEST.height, 4096L);
        boolean bent = false;
        for (int lane = 1; lane <= 3; lane++) {
            int outer = named(plan, "lane-" + lane + "-band-3").seedY();
            int inner = named(plan, "lane-" + lane + "-band-1").seedY();
            if (Math.abs(outer - inner) > 8) bent = true;
        }
        assertTrue(bent, "not one of three derived lanes bends across its own strip");
    }

    /** The same seed lays the same ladder, which is what a replayed fixture needs. */
    @Test
    void laneSeedingIsDeterministicInTheSeed() {
        PrecinctPlan first = conquestLike(PrecinctPlan.Lanes.derived(),
                MapScale.CONQUEST.width, MapScale.CONQUEST.height, 91L);
        PrecinctPlan second = conquestLike(PrecinctPlan.Lanes.derived(),
                MapScale.CONQUEST.width, MapScale.CONQUEST.height, 91L);
        assertEquals(seedList(first), seedList(second));
    }

    /** Every place by name and cell — what determinism means here. */
    private static List<String> seedList(PrecinctPlan plan) {
        List<String> out = new ArrayList<>();
        for (Precinct precinct : plan.precincts()) {
            out.add(precinct.name() + "@" + precinct.seedX() + "," + precinct.seedY());
        }
        return out;
    }

    private static PrecinctPlan conquestLike(PrecinctPlan.Lanes lanes, int width, int height,
                                             long seed) {
        return PrecinctPlan.derive(DEFENDED_TOWN, PrecinctPlan.Sprawl.BALANCED,
                Fortification.Demand.UNSTATED, MapPlacement.EAST, MapPlacement.WEST,
                lanes, width, height, new Random(seed));
    }

    private static List<Precinct> lanePlaces(PrecinctPlan plan) {
        List<Precinct> out = new ArrayList<>();
        for (Precinct precinct : plan.precincts()) {
            if (precinct.name().startsWith("lane-")) out.add(precinct);
        }
        return out;
    }

    /** Cells between two seeds. */
    private static double distance(Precinct a, Precinct b) {
        double dx = a.seedX() - b.seedX();
        double dy = a.seedY() - b.seedY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static int laneOf(Precinct place) {
        return Integer.parseInt(place.name().split("-")[1]);
    }

    private static Precinct named(PrecinctPlan plan, String name) {
        for (Precinct precinct : plan.precincts()) {
            if (precinct.name().equals(name)) return precinct;
        }
        throw new AssertionError("no place called " + name + " in " + plan.precincts());
    }

    private static int forwardOf(PrecinctPlan plan, String name) {
        for (Precinct precinct : plan.precincts()) {
            if (precinct.name().equals(name)) return precinct.seedX();
        }
        throw new AssertionError("no place called " + name + " in " + plan.precincts());
    }
}
