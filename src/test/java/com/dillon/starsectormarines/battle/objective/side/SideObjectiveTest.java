package com.dillon.starsectormarines.battle.objective.side;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Cell;
import com.dillon.starsectormarines.battle.objective.side.SideField.Who;
import com.dillon.starsectormarines.battle.objective.side.SideObjective.Hook;
import com.dillon.starsectormarines.battle.objective.side.SideObjective.Return;
import com.dillon.starsectormarines.battle.objective.side.SideObjective.State;
import com.dillon.starsectormarines.battle.objective.side.SideObjective.Window;
import com.dillon.starsectormarines.battle.objective.side.SideTrigger.Reading;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each trigger, the window and the effect, on a hand-built field of a few units. */
class SideObjectiveTest {
    private static final Ground YARD = Ground.of(List.of(new Cell(10, 0), new Cell(11, 0), new Cell(10, 1), new Cell(11, 1)));

    /** Units by id, at cell centres; leaders are a separate set. */
    private static final class Field implements SideField {
        final Map<Long, float[]> at = new HashMap<>();
        final Map<Long, Boolean> leader = new HashMap<>();

        Field put(long id, int x, int y) { at.put(id, new float[]{x + .5f, y + .5f}); return this; }
        void remove(long id) { at.remove(id); }

        @Override public boolean anyIn(Who who, Ground ground) {
            for (var unit : at.entrySet())
                if ((who == Who.COMBATANT || leader.getOrDefault(unit.getKey(), false))
                        && ground.contains((int) Math.floor(unit.getValue()[0]), (int) Math.floor(unit.getValue()[1]))) return true;
            return false;
        }

        @Override public long nearest(Who who, float x, float y, float reach) {
            long best = 0L;
            double bestD = reach;
            for (var unit : at.entrySet()) {
                double d = Math.hypot(unit.getValue()[0] - x, unit.getValue()[1] - y);
                if (d <= bestD) { bestD = d; best = unit.getKey(); }
            }
            return best;
        }

        @Override public boolean onField(long id) { return at.containsKey(id); }
        @Override public float x(long id) { return at.get(id)[0]; }
        @Override public float y(long id) { return at.get(id)[1]; }
    }

    @Test void reachIsDoneTheTickACompanyUnitStandsInTheArea() {
        var reach = new SideTrigger.Reach(Who.COMBATANT, YARD);
        var field = new Field().put(1, 5, 0);
        assertEquals(Reading.IDLE, reach.judge(field, .1f));
        field.put(1, 11, 1);
        assertEquals(Reading.DONE, reach.judge(field, .1f));
    }

    @Test void holdNeedsAnUnbrokenStayAndLeavingRestartsTheClock() {
        var hold = new SideTrigger.Hold(Who.LEADER, YARD, .3f);
        var field = new Field().put(1, 10, 0);
        assertEquals(Reading.IDLE, hold.judge(field, .1f), "a rifleman is not a leader");
        field.leader.put(1L, true);
        assertEquals(Reading.WORKING, hold.judge(field, .1f));
        assertEquals(Reading.WORKING, hold.judge(field, .1f));
        field.put(1, 3, 3);
        assertEquals(Reading.IDLE, hold.judge(field, .1f));
        assertEquals(0f, hold.worked(), "leaving restarts the clock");
        field.put(1, 10, 0);
        hold.judge(field, .1f);
        hold.judge(field, .1f);
        assertEquals(Reading.DONE, hold.judge(field, .1f));
    }

    @Test void anInteractionDoesNothingUntilAVerbIsPressedAndLapsesWhenTheCompanyLeaves() {
        var interact = new SideTrigger.Interact(Who.COMBATANT, YARD, 5f, new SideTrigger.Hold(Who.COMBATANT, YARD, .2f),
                SideTrigger.Interact.Layout.CARD, List.of("cut"));
        var field = new Field().put(1, 10, 0);
        assertEquals(Reading.IDLE, interact.judge(field, .1f), "standing on the place is not a choice");
        assertFalse(interact.press("sell", field), "a verb the job does not have");
        assertTrue(interact.press("cut", field));
        assertEquals(Reading.WORKING, interact.judge(field, .1f));
        field.put(1, 30, 30);
        assertEquals(Reading.IDLE, interact.judge(field, .1f));
        assertFalse(interact.engaged(), "walking away drops the choice");
        assertFalse(interact.press("cut", field), "too far to choose");
        field.put(1, 10, 0);
        assertEquals(Reading.IDLE, interact.judge(field, .1f), "and coming back asks again");
        interact.press("cut", field);
        interact.judge(field, .1f);
        assertEquals(Reading.DONE, interact.judge(field, .1f));
    }

    @Test void theServiceOffersAJobOnlyWhileTheCompanyIsNearAndEngagesItOnTheChoice() {
        var service = new SideObjectiveService();
        var interact = new SideTrigger.Interact(Who.COMBATANT, YARD, 5f, new SideTrigger.Hold(Who.COMBATANT, YARD, .2f),
                SideTrigger.Interact.Layout.LINES, List.of("respects"));
        service.install(new SideObjective("nod", new Hook("test", "offerer", null), interact, Window.always(), null, null, null));
        var field = new Field().put(1, 40, 40);
        assertTrue(service.offers(field).isEmpty(), "nobody near");
        field.put(1, 10, 3);
        var offers = service.offers(field);
        assertEquals(1, offers.size());
        assertEquals("nod", offers.get(0).key());
        assertEquals("respects", offers.get(0).verbs().get(0).id());
        service.tick(field, .1f, 1, null);
        assertEquals(State.OFFERED, service.job("nod").state(), "no choice, no work");
        service.choose("nod", "respects");
        service.tick(field, .1f, 2, null);
        assertEquals(State.OFFERED, service.job("nod").state(), "chosen, but nobody is on the place yet");
        assertTrue(service.offers(field).isEmpty(), "an engaged job is no longer offered");
        field.put(1, 10, 1);
        service.tick(field, .1f, 3, null);
        assertEquals(State.WORKING, service.job("nod").state());
        service.tick(field, .2f, 4, null);
        assertEquals(State.DONE, service.job("nod").state());
    }

    @Test void carryIsPickedUpCarriedDroppedWhereItsCarrierFellAndDelivered() {
        var carry = new SideTrigger.Carry(Who.COMBATANT, new Cell(1, 1), 1.5f, YARD);
        var field = new Field().put(7, 5, 5);
        assertEquals(Reading.IDLE, carry.judge(field, .1f), "nobody in reach");
        field.put(7, 1, 2);
        assertEquals(Reading.WORKING, carry.judge(field, .1f));
        assertEquals(7L, carry.carrier());
        field.put(7, 5, 1).put(9, 5, 2);
        assertEquals(Reading.WORKING, carry.judge(field, .1f));
        assertEquals(7L, carry.carrier(), "a carried prop is not taken by another unit");
        field.remove(7);
        assertEquals(Reading.IDLE, carry.judge(field, .1f));
        assertEquals(new SideTrigger.Spot(5, 1, List.of()), carry.target(field), "dropped where the carrier last stood");
        assertEquals(Reading.WORKING, carry.judge(field, .1f), "picked up again by the nearest unit in reach");
        assertEquals(9L, carry.carrier());
        field.put(9, 10, 1);
        assertEquals(Reading.DONE, carry.judge(field, .1f));
        assertEquals(null, carry.target(field), "a delivered prop is no longer marked");
    }

    @Test void escortCountsOnlyWhileEscortedAndFailsWhenTheUnitDies() {
        var held = new AtomicBoolean();
        var escort = new SideTrigger.Escort(() -> 4L, held::get, YARD);
        var field = new Field().put(4, 10, 0);
        assertEquals(Reading.IDLE, escort.judge(field, .1f), "a free broker on the apron is not delivered");
        held.set(true);
        field.put(4, 6, 0);
        assertEquals(Reading.WORKING, escort.judge(field, .1f));
        field.put(4, 10, 0);
        assertEquals(Reading.DONE, escort.judge(field, .1f));
        field.remove(4);
        assertEquals(Reading.FAILED, escort.judge(field, .1f));
    }

    @Test void aClosedWindowLapsesTheJobEvenHalfDoneAndAJobDoneOnItsLastOpenTickCounts() {
        var open = new AtomicBoolean(true);
        var field = new Field().put(1, 10, 0);
        var service = new SideObjectiveService();
        var half = job("half", new SideTrigger.Hold(Who.COMBATANT, YARD, 1f), Window.phase(open::get));
        var quick = job("quick", new SideTrigger.Reach(Who.COMBATANT, YARD), Window.phase(open::get));
        service.install(half);
        service.install(quick);
        service.tick(field, .1f, 1, new StoryBeatLog());
        assertEquals(State.WORKING, half.state());
        assertEquals(State.DONE, quick.state(), "judged while the window was open");
        open.set(false);
        service.tick(field, .1f, 2, new StoryBeatLog());
        assertEquals(State.LAPSED, half.state(), "work under way when the window closes pays nothing");
        assertEquals(State.DONE, quick.state(), "a final state sticks");
        assertFalse(Window.clock(1f).open(1f));
        assertTrue(Window.whileState(quick, State::isFinal).open(0f));
    }

    @Test void theEffectFiresOnceAtCompletionAndAGrantFiresItSilently() {
        var fired = new AtomicInteger();
        var field = new Field().put(1, 10, 0);
        var beats = new StoryBeatLog();
        var service = new SideObjectiveService();
        var reach = new SideObjective("reach", new Hook("test", "offerer", Map.of(State.DONE, "test.done")),
                new SideTrigger.Reach(Who.COMBATANT, YARD), Window.always(), sim -> fired.incrementAndGet(), null, null);
        var granted = new SideObjective("granted", new Hook("test", "offerer", Map.of(State.DONE, "test.granted")),
                new SideTrigger.Reach(Who.COMBATANT, Ground.of(List.of(new Cell(40, 40)))), Window.always(),
                sim -> fired.incrementAndGet(), null, null);
        service.install(reach);
        service.install(granted);
        service.grant("granted", null);
        assertEquals(1, fired.get());
        service.tick(field, .1f, 1, beats);
        service.tick(field, .1f, 2, beats);
        assertEquals(2, fired.get(), "once each");
        assertTrue(beats.has("test.done"), "the field line speaks through the beat log");
        assertFalse(beats.has("test.granted"), "a grant is silent");
        assertEquals(List.of(new SideObjectiveService.Transition(1, "reach", State.DONE)), service.transitionsAfter(0));
        assertThrows(IllegalStateException.class, () -> service.grant("granted", null), "a finished job is closed");
    }

    @Test void resultsEndUnfinishedJobsAsLapsedAndCarryTheMarkTheOutcomeEarned() {
        var service = new SideObjectiveService();
        service.install(new SideObjective("done", new Hook("test", "offerer", null),
                new SideTrigger.Reach(Who.COMBATANT, YARD), Window.always(), null, Return.done("test.place.done"), null));
        service.install(new SideObjective("open", new Hook("test", "offerer", null),
                new SideTrigger.Reach(Who.COMBATANT, Ground.of(List.of(new Cell(40, 40)))), Window.always(), null,
                Return.done("test.place.open"), null));
        service.tick(new Field().put(1, 10, 0), .1f, 1, new StoryBeatLog());
        var results = service.results(9);
        assertEquals(State.DONE, results.get(0).outcome());
        assertEquals("test.place.done", results.get(0).mark());
        assertEquals(State.LAPSED, results.get(1).outcome());
        assertFalse(results.get(1).marked(), "only the outcome that earns a mark keeps one");
        assertEquals(new SideObjectiveService.Transition(9, "open", State.LAPSED), service.transitionsAfter(1).get(0));
        assertThrows(IllegalArgumentException.class, () -> Return.done("Not A Mark"));
        assertThrows(IllegalArgumentException.class, () -> Return.done("unnamespaced"));
    }

    @Test void aHiddenJobIsUnknownAndUnjudgedUntilFoundThenComesToLightOnce() {
        var service = new SideObjectiveService();
        var seen = new AtomicBoolean();
        var box = Ground.of(List.of(new Cell(30, 30)));
        var tap = job("tap", new SideTrigger.Reach(Who.COMBATANT, YARD), Window.always())
                .hidden(SideObjective.Discovery.anyOf(field -> seen.get(), SideObjective.Discovery.reach(Who.COMBATANT, box)), "found");
        service.install(tap);
        var beats = new StoryBeatLog();
        var field = new Field().put(1, 10, 0);
        service.tick(field, .1f, 1, beats);
        assertFalse(tap.known());
        assertEquals(State.OFFERED, tap.state(), "a job nobody has found is not judged, though the unit stands in it");
        seen.set(true);
        service.tick(field, .1f, 2, beats);
        assertTrue(tap.known());
        assertTrue(tap.fresh(), "found just now");
        assertEquals(State.DONE, tap.state(), "judged from the tick it is found");
        assertEquals(List.of(new SideObjectiveService.Transition(2, "tap", State.OFFERED, true),
                new SideObjectiveService.Transition(2, "tap", State.DONE)), service.transitionsAfter(0));
        for (int i = 0; i < 200; i++) service.tick(field, .1f, 3 + i, beats);
        assertFalse(tap.fresh(), "new only for a moment");
        assertEquals(2, service.transitionsAfter(0).size(), "found once");
    }

    @Test void aHiddenJobNeverFoundIsMissedNotLapsedAndEarnsNothing() {
        var service = new SideObjectiveService();
        var open = new AtomicBoolean(true);
        service.install(new SideObjective("tap", new Hook("test", "offerer", null), new SideTrigger.Reach(Who.COMBATANT, YARD),
                Window.phase(open::get), null, Return.done("test.tap.cut", 500), null).hidden(field -> false, null));
        service.install(new SideObjective("late", new Hook("test", "offerer", null), new SideTrigger.Reach(Who.COMBATANT, YARD),
                Window.always(), null, null, null).hidden(field -> false, null));
        var field = new Field().put(1, 10, 0);
        service.tick(field, .1f, 1, new StoryBeatLog());
        open.set(false);
        service.tick(field, .1f, 2, new StoryBeatLog());
        assertEquals(State.MISSED, service.job("tap").state(), "its window closed before anyone found it");
        var results = service.results(5);
        for (var result : results) {
            assertEquals(State.MISSED, result.outcome());
            assertFalse(result.reported(), "no debrief line unless the mission authors one");
            assertFalse(result.marked());
            assertEquals(0, result.credits());
        }
    }

    @Test void aJobPaysItsCreditsOnlyWhenDone() {
        var pays = Return.done("test.place.done", 2_000);
        assertEquals(2_000, pays.credits(State.DONE));
        assertEquals(0, pays.credits(State.LAPSED));
        assertEquals(0, pays.credits(State.FAILED));
        assertThrows(IllegalArgumentException.class, () -> new Return(Map.of(), -1));
    }

    @Test void aGroundAnchorsOnItsOwnCellAndOutlinesItsEdge() {
        var square = Ground.of(List.of(new Cell(0, 0), new Cell(1, 0), new Cell(2, 0), new Cell(0, 1), new Cell(1, 1),
                new Cell(2, 1), new Cell(0, 2), new Cell(1, 2), new Cell(2, 2)));
        assertEquals(new Cell(1, 1), square.anchor());
        assertEquals(8, square.outline().size());
        assertTrue(square.contains(2, 2));
        assertFalse(square.contains(3, 2));
    }

    private static SideObjective job(String key, SideTrigger trigger, Window window) {
        return new SideObjective(key, new Hook("test", "offerer", null), trigger, window, null, null, null);
    }
}
