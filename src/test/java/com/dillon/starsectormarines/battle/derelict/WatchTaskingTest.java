package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.derelict.FlockCommandPort.Contact;
import com.dillon.starsectormarines.battle.derelict.WatchTasking.Kind;
import com.dillon.starsectormarines.battle.derelict.WatchTasking.Task;
import com.dillon.starsectormarines.battle.derelict.WatchTasking.Watcher;
import com.dillon.starsectormarines.battle.derelict.WatchTasking.Worker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class WatchTaskingTest {

    /** Three line frames out from a bay at (0, 0), and a siege frame farther off. */
    private static final List<Watcher> WATCH = List.of(
            new Watcher(1, DerelictWalkerFrame.LINE, 2, 0), new Watcher(2, DerelictWalkerFrame.LINE, 10, 0),
            new Watcher(3, DerelictWalkerFrame.LINE, 20, 0), new Watcher(4, DerelictWalkerFrame.SIEGE, 30, 0));

    @Test
    void theBayKeepsItsGuardAndEveryOtherWalkerAnswersTheNearestContactOnTheClaim() {
        Map<Integer, Task> tasks = WatchTasking.assign(WATCH, 0, 0,
                List.of(new Contact(70, 12, 5, 0), new Contact(71, 40, 0, 0)), new int[] {50, 50}, List.of());
        assertEquals(Kind.GUARD, tasks.get(4).kind(), "a siege frame keeps the bay before a nearer line frame");
        assertEquals(Kind.ANSWER, tasks.get(1).kind());
        assertEquals(70L, tasks.get(1).subject());
        assertEquals(70L, tasks.get(3).subject(), "each to the contact nearest it: (12, 5) before (40, 0)");
        assertEquals(WatchTasking.BAY_GUARD, tasks.values().stream().filter(t -> t.kind() == Kind.GUARD).count());
    }

    @Test
    void withNoContactTheWatchPushesTowardTheLoudestFightAndWithNeitherItEscortsTheWorkers() {
        Map<Integer, Task> push = WatchTasking.assign(WATCH, 0, 0, List.of(), new int[] {50, 50}, List.of());
        assertEquals(Kind.PUSH, push.get(2).kind());
        assertEquals(50, push.get(2).x());

        Map<Integer, Task> escort = WatchTasking.assign(WATCH, 0, 0, List.of(), null,
                List.of(new Worker(90, 21, 3)));
        assertEquals(Kind.ESCORT, escort.get(3).kind(), "the free walker nearest the worker escorts it");
        assertEquals(90L, escort.get(3).subject());
        assertEquals(Kind.GUARD, escort.get(1).kind(), "a walker with nothing to escort stands with the guard");
    }

    @Test
    void theLoudestFightIsTheBusiestSquareOfFireOnTheClaim() {
        List<float[]> shots = List.of(new float[] {1, 1}, new float[] {3, 3}, new float[] {50, 50},
                new float[] {51, 50}, new float[] {52, 50}, new float[] {200, 200}, new float[] {201, 200},
                new float[] {202, 200}, new float[] {203, 200});
        int[] at = WatchTasking.loudest(shots, (x, y) -> x < 100);
        assertArrayEquals(new int[] {51, 50}, at, "the busier square off the claim does not count");
        assertNull(WatchTasking.loudest(shots, (x, y) -> false));
    }

    @Test
    void aRangingFlockHoldsEachPlaceInTurnAndTwoFlocksStartApart() {
        List<int[]> places = List.of(new int[] {0, 0}, new int[] {10, 0}, new int[] {20, 0});
        assertArrayEquals(new int[] {0, 0}, WatchTasking.rangePoint(places, 0, 0f));
        assertArrayEquals(new int[] {10, 0}, WatchTasking.rangePoint(places, 0, WatchTasking.RANGE_SECONDS));
        assertArrayEquals(new int[] {10, 0}, WatchTasking.rangePoint(places, 1, 0f));
        assertNull(WatchTasking.rangePoint(List.of(), 0, 0f));
    }

    @Test
    void aWalkerGoesNoFartherThanTheWatchReachOffTheHull() {
        List<FallenHullSite.Rect> hull = List.of(new FallenHullSite.Rect(0, 0, 9, 9));
        assertArrayEquals(new int[] {20, 5}, WatchTasking.clamp(hull, 30, new int[] {20, 5}), "within reach");
        assertArrayEquals(new int[] {39, 5}, WatchTasking.clamp(hull, 30, new int[] {109, 5}),
                "on the line toward it, the reach out from the plates");
        Map<Integer, Task> tasks = WatchTasking.assign(WATCH, 0, 0, List.of(new Contact(70, 109, 5, 0)), null,
                List.of(), cell -> WatchTasking.clamp(hull, 30, cell));
        assertEquals(39, tasks.get(1).x(), "it answers the contact as far as the reach");
        assertEquals(70L, tasks.get(1).subject());
    }

    @Test
    void anAwakeHullKeepsItsZoneAwakeWhileTheForgeStandsAndASleepingOneStaysAsItWas() {
        SecurityNetwork awake = new SecurityNetwork().add(new SecurityZone(FallenHull.HULL_ZONE, 0, 0, 10));
        SecurityNetwork asleep = new SecurityNetwork().add(new SecurityZone(FallenHull.HULL_ZONE, 0, 0, 10));
        for (int tick = 0; tick <= SecurityZone.SETTLE_TICKS * 2; tick += DerelictSecurityDirector.PULSE_TICKS) {
            FallenHull.rouse(awake, true, tick);
            FallenHull.rouse(asleep, false, tick);
            awake.settle(tick);
            asleep.settle(tick);
        }
        assertEquals(SecurityZone.State.AWAKE, awake.zone(FallenHull.HULL_ZONE).state());
        assertEquals(SecurityZone.State.DORMANT, asleep.zone(FallenHull.HULL_ZONE).state());
        awake.zone(FallenHull.HULL_ZONE).loseNode();
        awake.zone(FallenHull.HULL_ZONE).settle(SecurityZone.SETTLE_TICKS * 5);
        FallenHull.rouse(awake, true, SecurityZone.SETTLE_TICKS * 5);
        assertEquals(SecurityZone.State.ALERT, awake.zone(FallenHull.HULL_ZONE).state(),
                "with the forge gone the hull is roused no more and settles");
    }

    @Test
    void aHullHuntingItsTownGuardsEveryBayEscortsItsWorkersAndSendsTheRestAfterTheTownByTurns() {
        // Bays at (0, 0) and (100, 0); five line frames; the town's people at (40, 30); two street places.
        List<Watcher> watch = List.of(new Watcher(1, DerelictWalkerFrame.LINE, 1, 0),
                new Watcher(2, DerelictWalkerFrame.LINE, 99, 0), new Watcher(3, DerelictWalkerFrame.LINE, 50, 0),
                new Watcher(4, DerelictWalkerFrame.LINE, 52, 0), new Watcher(5, DerelictWalkerFrame.LINE, 54, 0),
                new Watcher(6, DerelictWalkerFrame.LINE, 20, 20));
        WatchTasking.Town town = new WatchTasking.Town(List.of(new int[] {0, 0}, new int[] {100, 0}),
                List.of(new int[] {40, 30}), List.of(new int[] {60, 60}, new int[] {80, 80}), 0f);
        Map<Integer, Task> tasks = WatchTasking.hunt(watch, town, List.of(), null, List.of(new Worker(90, 21, 21)),
                cell -> cell);
        assertEquals(Kind.GUARD, tasks.get(1).kind(), "the mouth's guard");
        assertEquals(Kind.GUARD, tasks.get(2).kind(), "the far section's guard");
        assertEquals(100, tasks.get(2).x());
        assertEquals(Kind.ESCORT, tasks.get(6).kind(), "the stripper out has its escort");
        assertEquals(Kind.HUNT, tasks.get(3).kind(), "by turns: one after the town's people");
        assertArrayEquals(new int[] {40, 30}, new int[] {tasks.get(3).x(), tasks.get(3).y()});
        assertEquals(Kind.SWEEP, tasks.get(4).kind(), "the next sweeps the streets");
        assertEquals(Kind.HUNT, tasks.get(5).kind());

        Map<Integer, Task> contact = WatchTasking.hunt(watch, town, List.of(new Contact(70, 45, 5, 0)), null,
                List.of(), cell -> cell);
        assertEquals(Kind.ANSWER, contact.get(3).kind(), "contact on the claim is answered before the hunt");

        Map<Integer, Task> fight = WatchTasking.hunt(watch, town, List.of(), new int[] {70, 10}, List.of(),
                cell -> cell);
        assertEquals(Kind.PUSH, fight.get(4).kind(), "a sweeper goes to the loudest fight instead");
    }

    @Test
    void sweepersHoldEachStreetPlaceInTurnAndStartApart() {
        List<int[]> streets = List.of(new int[] {0, 0}, new int[] {10, 0}, new int[] {20, 0});
        int[] first = WatchTasking.sweepPoint(streets, 1, 0f);
        assertArrayEquals(first, WatchTasking.sweepPoint(streets, 1, WatchTasking.SWEEP_SECONDS - 1f), "held");
        assertFalse(first == WatchTasking.sweepPoint(streets, 1, WatchTasking.SWEEP_SECONDS), "then the next");
        assertFalse(first == WatchTasking.sweepPoint(streets, 2, 0f), "two walkers start apart");
        assertNull(WatchTasking.sweepPoint(List.of(), 1, 0f));
    }
}
