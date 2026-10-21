package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.appearance.WorkMotion;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A running track is a loop of markers inside one room, and a lap is every one
 * of them in turn.
 *
 * <p>Given one stop like a bench, a runner jogs to the nearest marker and stands
 * on it; given a circuit's one stop per room, the same. Before this rule the
 * frigate gym's two "lap" places were ordinary exercise stands on the loop, and
 * the people on them curled dumbbells.
 */
class LapsAreRunRoundTheTrackTest {

    private record Room(int id, RoomPurpose purpose, int centreX, int centreY) implements JobSite {
        @Override public boolean contains(int cellX, int cellY) {
            return Math.abs(cellX - centreX) <= 20 && Math.abs(cellY - centreY) <= 20;
        }
    }

    private static final Room GYM = new Room(3, RoomPurpose.GYMNASIUM, 5, 5);
    private static final Room BAY = new Room(4, RoomPurpose.VEHICLE_BAY, 60, 5);

    /** Six markers round a ten-by-ten ring, published in running order, each faced at the next. */
    private static final int[][] RING = {{0, 0}, {5, 0}, {10, 0}, {10, 10}, {5, 10}, {0, 10}};

    private static List<FixtureTask> track() {
        List<FixtureTask> work = new ArrayList<>();
        for (int i = 0; i < RING.length; i++) {
            int[] next = RING[(i + 1) % RING.length];
            work.add(FixtureTask.at(RING[i][0], RING[i][1], Affordance.LAP, next[0], next[1]));
        }
        work.add(FixtureTask.at(5, 5, Affordance.EXERCISE, 5, 6));
        return work;
    }

    /** The cells a member's route stops at for the lap, in route order. */
    private static List<int[]> lapOf(AmbientTaskRoute route) {
        List<int[]> lap = new ArrayList<>();
        for (AmbientTaskRoute.Stop stop : route.stops()) {
            if (JobBoard.affordance(stop.pointGroup()) != Affordance.LAP) continue;
            lap.add(new int[]{(int) stop.worldX(), (int) stop.worldY()});
        }
        return lap;
    }

    @Test void aLapIsEveryMarkerInRunningOrderAndBackToTheStart() {
        Shift shift = Shift.of(CrewRole.MARINE, List.of(GYM), track(), new boolean[0],
                AmbientThreatPolicy.HOSTILE_COMBATANT);
        for (int member = 0; member < RING.length; member++) {
            AmbientTaskRoute route = shift.member(member);
            List<int[]> lap = lapOf(route);
            assertEquals(RING.length + 1, lap.size(), "one marker short of a lap");
            int first = indexOf(lap.get(0));
            for (int step = 0; step <= RING.length; step++) {
                int[] want = RING[(first + step) % RING.length];
                assertEquals(want[0], lap.get(step)[0], "member " + member + " step " + step);
                assertEquals(want[1], lap.get(step)[1], "member " + member + " step " + step);
            }
            // Consecutive in the route, and each a specific marker rather than any free one.
            int at = -1;
            for (int i = 0; i < route.stops().size(); i++) {
                AmbientTaskRoute.Stop stop = route.stops().get(i);
                if (JobBoard.affordance(stop.pointGroup()) != Affordance.LAP) continue;
                if (at >= 0) assertEquals(at + 1, i, "the lap was broken by another job");
                at = i;
                assertTrue(stop.specificPoint(), "a lap marker taken as any free marker");
                assertTrue(stop.dwellSeconds() < 1f, "a marker held somebody for " + stop.dwellSeconds() + "s");
                assertEquals(AmbientActivity.EXERCISING, stop.activity());
            }
        }
        assertNotEquals(indexOf(lapOf(shift.member(0)).get(0)), indexOf(lapOf(shift.member(1)).get(0)),
                "two runners start on the same marker");
    }

    @Test void machinesDealtThroughTheRotationDoNotSplitALap() {
        List<FixtureTask> work = new ArrayList<>(track());
        for (int berth = 0; berth < 8; berth++) work.add(FixtureTask.servingBerth(50 + berth * 2, 5, berth, 51 + berth * 2, 5));
        boolean[] berthed = new boolean[8];
        Arrays.fill(berthed, true);
        Shift shift = Shift.of(CrewRole.MECH_TECH, List.of(BAY, GYM), work, berthed,
                AmbientThreatPolicy.HOSTILE_COMBATANT);
        for (int member = 0; member < 4; member++) {
            List<AmbientTaskRoute.Stop> stops = shift.member(member).stops();
            int first = -1, last = -1, laps = 0;
            for (int i = 0; i < stops.size(); i++) {
                if (JobBoard.affordance(stops.get(i).pointGroup()) != Affordance.LAP) continue;
                if (first < 0) first = i;
                last = i;
                laps++;
            }
            assertEquals(RING.length + 1, laps);
            assertEquals(laps, last - first + 1, "member " + member + ": a machine dealt into the lap");
        }
    }

    @Test void aLapIsJoggedAndNothingOnTheTrackIsLifted() {
        assertEquals(WorkMotion.JOG, WorkMotions.forJob(Affordance.LAP, "ship-gym-track.track"));
        assertEquals(WorkMotion.JOG, WorkMotions.between(Affordance.LAP, Affordance.LAP));
        assertEquals(WorkMotion.NONE, WorkMotions.between(Affordance.EXERCISE, Affordance.LAP),
                "the walk onto the track is a walk");
        assertEquals(0f, WorkMotion.JOG.mountCells(), "a runner on the deck drawn ahead onto a machine");
    }

    private static int indexOf(int[] cell) {
        for (int i = 0; i < RING.length; i++) if (RING[i][0] == cell[0] && RING[i][1] == cell[1]) return i;
        throw new AssertionError("not a marker: " + cell[0] + "," + cell[1]);
    }
}
