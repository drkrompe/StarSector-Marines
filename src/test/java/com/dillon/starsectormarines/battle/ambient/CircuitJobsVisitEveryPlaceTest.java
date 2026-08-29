package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rounds are made of the walk between compartments, not of any one of them.
 *
 * <p>Almost every job aboard is somewhere you go — a bench, a bunk, a lane — and
 * a shift gives each of them one stop at the nearest place offering it. Two jobs
 * are not like that, and given the ordinary treatment they collapse into their
 * opposite: a master-at-arms making rounds walks to the next compartment and
 * stands in it for the rest of the watch, and a technician working a defect list
 * tends the same fault forever.
 */
class CircuitJobsVisitEveryPlaceTest {

    /**
     * A compartment as a shift sees it: an id, a purpose and an extent.
     *
     * <p>A three-cell box rather than a single cell, because a room holds more
     * than one fixture and the whole question here is how many.
     */
    private record Site(int id, RoomPurpose purpose, int centreX, int centreY)
            implements JobSite {
        @Override
        public boolean contains(int cellX, int cellY) {
            return Math.abs(cellX - centreX) <= 1 && Math.abs(cellY - centreY) <= 1;
        }
    }

    /** An armoury to be posted to, and {@code others} compartments to walk round. */
    private static List<JobSite> ship(int others) {
        List<JobSite> sites = new ArrayList<>();
        sites.add(new Site(0, RoomPurpose.ARMORY, 0, 0));
        for (int index = 1; index <= others; index++) {
            sites.add(new Site(index, RoomPurpose.PARTS_CAGE, index * 8, 0));
        }
        return sites;
    }

    /** One issue counter in the armoury, and a rounds point in every compartment. */
    private static List<FixtureTask> work(List<JobSite> sites) {
        List<FixtureTask> tasks = new ArrayList<>();
        tasks.add(FixtureTask.at(0, 0, Affordance.ISSUE, 1, 0));
        for (JobSite site : sites) {
            tasks.add(FixtureTask.at(site.centreX(), site.centreY(), Affordance.ROUNDS,
                    site.centreX(), site.centreY() + 1));
        }
        return tasks;
    }

    private static Shift armoury(int others) {
        List<JobSite> sites = ship(others);
        return Shift.postedAt(CrewRole.ARMOURER, sites.get(0), sites,
                work(sites), new boolean[0], AmbientThreatPolicy.HOSTILE_COMBATANT);
    }

    private static int stopsFor(AmbientTaskRoute route, AmbientActivity activity) {
        int count = 0;
        for (AmbientTaskRoute.Stop stop : route.stops()) {
            if (stop.activity() == activity) count++;
        }
        return count;
    }

    /**
     * A round calls at every compartment the shift reached, in one turn of the
     * rotation — not at the nearest one.
     */
    @Test
    void aRoundCallsAtEveryCompartmentItReaches() {
        AmbientTaskRoute route = armoury(4).member(0);

        assertEquals(5, stopsFor(route, AmbientActivity.INSPECTING),
                "a round that calls at one compartment is somebody standing in it");
    }

    /**
     * And it is bounded. A patrol of every compartment on a capital is a
     * rotation nobody completes, whose walker is permanently in a passage —
     * which is the shape the whole model exists to avoid, reached by being too
     * thorough rather than too idle.
     */
    @Test
    void aRoundIsBoundedRatherThanExhaustive() {
        int stops = stopsFor(armoury(40).member(0), AmbientActivity.INSPECTING);

        assertTrue(stops > 1, "the round collapsed to a single call");
        assertTrue(stops <= 8, "a forty-compartment hull produced a " + stops + "-stop round");
    }

    /** Two hands on the same round are not on the same round. */
    @Test
    void twoWalkersStartAtDifferentPointsOnTheRing() {
        Shift bill = armoury(4);

        assertNotEquals(bill.member(0).stops().get(0).worldX(),
                bill.member(1).stops().get(0).worldX(),
                "both walkers set off from the same compartment in step");
    }

    /**
     * A circuit must not bound a posting. The armoury holds one rounds point
     * like every other compartment, and counting it would cap the room at a
     * single hand for a job that is satisfied anywhere on the ship.
     */
    @Test
    void aCircuitDoesNotCapTheRoomItStartsIn() {
        List<JobSite> sites = ship(4);
        List<FixtureTask> tasks = new ArrayList<>(work(sites));
        tasks.add(FixtureTask.at(1, 0, Affordance.ISSUE, 1, 1));
        tasks.add(FixtureTask.at(0, 1, Affordance.ISSUE, 1, 1));

        Shift bill = Shift.postedAt(CrewRole.ARMOURER, sites.get(0), sites,
                tasks, new boolean[0], AmbientThreatPolicy.HOSTILE_COMBATANT);

        assertEquals(3, bill.capacity(),
                "the armoury's three counters were capped by its one rounds point");
    }

    /**
     * Nor may a circuit be a trade. Rounds and defects are deliberately
     * everywhere — that is what makes them circuits — so reading either as a
     * station would make every compartment aboard a posting and put a watch in
     * each of them.
     */
    @Test
    void noTradeIsACircuit() {
        for (CrewRole role : CrewRole.values()) {
            assertFalse(role.trade() != null && CrewRole.isCircuit(role.trade()),
                    role + " is stationed by a job that is done all over the ship");
        }
    }

    /**
     * A compartment that offers nothing but rounds is nobody's posting, however
     * many of them there are.
     */
    @Test
    void aCompartmentOnTheRoundIsNotAPosting() {
        List<JobSite> sites = ship(1);
        Site pocket = (Site) sites.get(1);

        for (CrewRole role : CrewRole.values()) {
            assertFalse(Shift.basedAt(role, pocket, work(sites), new boolean[0]),
                    role + " was billeted in a room whose only job is being looked into");
        }
    }
}
