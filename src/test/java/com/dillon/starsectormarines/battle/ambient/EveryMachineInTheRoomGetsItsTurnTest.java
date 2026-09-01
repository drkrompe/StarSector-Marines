package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * A room's benches are interchangeable; the machines standing in it are not.
 *
 * <p>An ordinary job is somewhere you go, and one stop at it is right: a bench
 * is as good as the bench beside it, which is what a claim group means. Work on
 * a berth is not that. Six aircraft on an apron are six turnarounds, and an
 * hour spent on one of them is an hour the other five did not get — so a
 * technician given one servicing stop takes the nearest stand and works it for
 * the whole battle, and the claim service answers every later request with the
 * claim already held, so even the walk back is to the machine they never left.
 *
 * <p>Measured before this rule existed, on a conquest airfield with three
 * technicians and six stands: berths 2, 3 and 5 went three hundred seconds
 * without a single visit, while two of the field's three sheds sat on a full
 * turnaround they never worked off and never flew again.
 */
class EveryMachineInTheRoomGetsItsTurnTest {

    /** One bay, wide enough to hold every stand the tests park in it. */
    private record Bay(int id, RoomPurpose purpose, int centreX, int centreY)
            implements JobSite {
        @Override
        public boolean contains(int cellX, int cellY) {
            return Math.abs(cellX - centreX) <= 50 && Math.abs(cellY - centreY) <= 50;
        }
    }

    private static final Bay BAY = new Bay(4, RoomPurpose.VEHICLE_BAY, 0, 0);

    /**
     * A bay of {@code machines} stands, each worked from both flanks, plus the
     * one board the bay's state is read off.
     */
    private static List<FixtureTask> bayOf(int machines) {
        List<FixtureTask> work = new ArrayList<>();
        for (int berth = 0; berth < machines; berth++) {
            int x = berth * 4;
            work.add(FixtureTask.servingBerth(x - 1, 0, berth, x, 0));
            work.add(FixtureTask.servingBerth(x + 1, 0, berth, x, 0));
        }
        work.add(FixtureTask.at(0, 3, Affordance.READOUT, 0, 4));
        return work;
    }

    private static Shift shift(int machines) {
        boolean[] berthed = new boolean[machines];
        for (int berth = 0; berth < machines; berth++) berthed[berth] = true;
        return Shift.of(CrewRole.MECH_TECH, List.of(BAY), bayOf(machines), berthed,
                AmbientThreatPolicy.HOSTILE_COMBATANT);
    }

    private static List<AmbientTaskRoute.Stop> servicing(AmbientTaskRoute route) {
        List<AmbientTaskRoute.Stop> stops = new ArrayList<>();
        for (AmbientTaskRoute.Stop stop : route.stops()) {
            if (stop.pointGroup().contains(Affordance.SERVICE.name().toLowerCase())) {
                stops.add(stop);
            }
        }
        return stops;
    }

    /** One turn of the rotation calls at every machine, not at the nearest one. */
    @Test
    void aTechnicianComesRoundToEveryMachine() {
        List<AmbientTaskRoute.Stop> stops = servicing(shift(6).member(0));

        assertEquals(6, stops.size(),
                "a technician with one servicing stop works one machine for the whole battle");
    }

    /**
     * Both flanks of one stand are two places to stand and one turnaround.
     * Emitting both would spend half a rotation walking round the same hull.
     */
    @Test
    void bothFlanksOfOneMachineAreOneStop() {
        assertEquals(1, servicing(shift(1).member(0)).size(),
                "one machine worked from two sides produced a two-stop round of itself");
    }

    /**
     * The stops name different machines, which is the half of this the claim
     * service reads. Filed under one group they would be interchangeable, and
     * an actor already holding one of them is handed it straight back.
     */
    @Test
    void eachMachineIsItsOwnClaimGroup() {
        Set<String> groups = new LinkedHashSet<>();
        for (AmbientTaskRoute.Stop stop : servicing(shift(6).member(0))) {
            groups.add(stop.pointGroup());
        }

        assertEquals(6, groups.size(),
                "six machines were offered as one interchangeable place to work");
    }

    /**
     * And only machines. A bench really is interchangeable with the bench
     * beside it, so work that stands on its own keeps its single stop.
     */
    @Test
    void benchWorkIsStillOneStop() {
        int boards = 0;
        for (AmbientTaskRoute.Stop stop : shift(6).member(0).stops()) {
            if (stop.activity() == CrewRole.activityFor(Affordance.READOUT)) boards++;
        }

        assertEquals(1, boards, "the bay's one board became a round of itself");
    }

    /** Two technicians in the same bay do not make for the same machine first. */
    @Test
    void twoTechniciansStartOnDifferentMachines() {
        Shift bill = shift(6);

        assertNotEquals(servicing(bill.member(0)).get(0).pointGroup(),
                servicing(bill.member(1)).get(0).pointGroup(),
                "both technicians set off for the same stand");
    }
}
