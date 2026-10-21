package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Place directives (`garrisonables.md`, place directives and locked positions): the generic triggers over a hand-built
 * pulse, the lifecycle (one transition a pulse, activation order, re-arming) and how detachments fill locks.
 */
class PlaceDirectivesTest {

    private static final PlaceTarget YARD = PlaceTarget.region(20, 20, 4);

    /** A pulse with own squads and believed contacts at the given cells, and a flat pressure field. */
    private static PlaceSignals pulse(int tick, float[] own, int[] contacts, float pressure) {
        return PlaceSignals.of(tick, own, contacts, (bx, by) -> bx == 2 && by == 2 ? pressure : 0f, 8, 10, 10);
    }

    private static PlaceDirectives book(PlaceDirective... directives) {
        return new PlaceDirectives(List.of(directives));
    }

    private static PlaceDirective hold(String id, PlaceTrigger when, PlaceTrigger until) {
        return PlaceDirective.hold(id, Faction.ALLY, YARD).when(when).until(until).build();
    }

    @Test
    void stageFlagAndTickTriggersReadOnlyWhatIsDisclosed() {
        PlaceDirectives book = book(
                hold("phase", PlaceTrigger.stageAtLeast("phase", 2), PlaceTrigger.never()),
                hold("beat", PlaceTrigger.flag("reserve"), PlaceTrigger.never()),
                hold("timer", PlaceTrigger.tickAtLeast(300), PlaceTrigger.never()));
        book.evaluate(PlaceSignals.at(100).stage("phase", 1));
        assertEquals(PlaceDirectives.State.DORMANT, book.state("phase"));
        assertEquals(PlaceDirectives.State.DORMANT, book.state("beat"), "an undisclosed flag is down");
        book.evaluate(PlaceSignals.at(200).stage("phase", 2).flag("reserve", false));
        assertEquals(PlaceDirectives.State.ACTIVE, book.state("phase"));
        assertEquals(PlaceDirectives.State.DORMANT, book.state("beat"));
        book.evaluate(PlaceSignals.at(300).raise("reserve"));
        assertEquals(PlaceDirectives.State.ACTIVE, book.state("beat"));
        assertEquals(PlaceDirectives.State.ACTIVE, book.state("timer"));
        // An undisclosed stage is never "at least" anything.
        assertFalse(PlaceTrigger.stageAtLeast("phase", Integer.MIN_VALUE).test(contextOf(PlaceSignals.at(0))));
    }

    @Test
    void beliefTriggersReadThePartysOwnSquadsAndContacts() {
        PlaceSignals empty = pulse(0, new float[0], new int[0], 0f);
        PlaceSignals own = pulse(0, new float[]{19.5f, 21.2f}, new int[0], 0f);
        PlaceSignals hostile = pulse(0, new float[]{60f, 60f}, new int[]{22, 18}, 0f);
        PlaceSignals both = pulse(0, new float[]{20f, 20f}, new int[]{24, 24}, 0f);
        assertEquals(PlaceHold.EMPTY, empty.hold(YARD));
        assertEquals(PlaceHold.OWN, own.hold(YARD));
        assertEquals(PlaceHold.HOSTILE, hostile.hold(YARD));
        assertEquals(PlaceHold.CONTESTED, both.hold(YARD));
        assertEquals(PlaceHold.EMPTY, hostile.hold(PlaceTarget.region(80, 80, 3)), "a hostile elsewhere presses nothing here");
        PlaceTrigger taken = PlaceTrigger.held(PlaceHold.HOSTILE, PlaceHold.CONTESTED);
        assertTrue(taken.test(contextOf(hostile)));
        assertFalse(taken.test(contextOf(own)));
        // Pressure is the most on any block the region covers: (16..24) spans blocks 2..3.
        assertEquals(5f, pulse(0, new float[0], new int[0], 5f).pressure(YARD));
        assertTrue(PlaceTrigger.pressureAtLeast(4f).test(contextOf(pulse(0, new float[0], new int[0], 5f))));
        assertFalse(PlaceTrigger.pressureAtLeast(PlaceTarget.region(70, 70, 2), 4f)
                .test(contextOf(pulse(0, new float[0], new int[0], 5f))));
    }

    @Test
    void lostNeedsThePlaceHeldOnAnEarlierPulse() {
        PlaceDirective retake = PlaceDirective.counterAttack("retake", Faction.DEFENDER, YARD).build();
        PlaceDirectives book = book(retake);
        // Hostiles in a place the party never held: not lost.
        book.evaluate(pulse(10, new float[0], new int[]{20, 20}, 0f));
        assertEquals(PlaceDirectives.State.DORMANT, book.state("retake"));
        book.evaluate(pulse(20, new float[]{20f, 20f}, new int[0], 0f));
        assertEquals(PlaceDirectives.State.DORMANT, book.state("retake"), "held is not lost");
        book.evaluate(pulse(30, new float[]{20f, 20f}, new int[]{21, 21}, 0f));
        assertEquals(PlaceDirectives.State.ACTIVE, book.state("retake"), "contested after holding it is lost");
        assertEquals(List.of(retake), book.active(PlaceDirective.Kind.COUNTER_ATTACK));
        // Held again: released; re-arms from the next pulse, and fires again when lost again.
        book.evaluate(pulse(40, new float[]{20f, 20f}, new int[0], 0f));
        assertEquals(PlaceDirectives.State.RELEASED, book.state("retake"));
        book.evaluate(pulse(50, new float[0], new int[]{20, 20}, 0f));
        assertEquals(PlaceDirectives.State.ACTIVE, book.state("retake"));
        assertEquals(2, book.activations());
        assertEquals(1, book.releases());
    }

    @Test
    void oneTransitionAPulseAndReleasedStaysReleased() {
        PlaceDirectives book = book(hold("once", PlaceTrigger.always(), PlaceTrigger.always()));
        List<PlaceDirectives.Transition> first = book.evaluate(PlaceSignals.at(0));
        assertEquals(1, first.size());
        assertEquals(PlaceDirectives.State.ACTIVE, book.state("once"), "a directive that comes on is held a pulse");
        book.evaluate(PlaceSignals.at(75));
        assertEquals(PlaceDirectives.State.RELEASED, book.state("once"));
        book.evaluate(PlaceSignals.at(150));
        assertEquals(PlaceDirectives.State.RELEASED, book.state("once"), "without re-arming it stays released");
        assertEquals("always", book.log().get(0).trigger());
    }

    @Test
    void activeForCountsFromActivation() {
        PlaceDirectives book = book(hold("hold-a-while", PlaceTrigger.tickAtLeast(100), PlaceTrigger.activeFor(200)));
        book.evaluate(PlaceSignals.at(100));
        book.evaluate(PlaceSignals.at(250));
        assertEquals(PlaceDirectives.State.ACTIVE, book.state("hold-a-while"));
        book.evaluate(PlaceSignals.at(300));
        assertEquals(PlaceDirectives.State.RELEASED, book.state("hold-a-while"));
    }

    @Test
    void combinatorsCompose() {
        PlaceSignals pulse = PlaceSignals.at(5).raise("a").stage("phase", 3);
        PlaceTrigger.Context context = contextOf(pulse);
        assertTrue(PlaceTrigger.anyOf(PlaceTrigger.flag("b"), PlaceTrigger.flag("a")).test(context));
        assertFalse(PlaceTrigger.allOf(PlaceTrigger.flag("b"), PlaceTrigger.flag("a")).test(context));
        assertTrue(PlaceTrigger.allOf(PlaceTrigger.flag("a"), PlaceTrigger.stageAtLeast("phase", 3)).test(context));
        assertTrue(PlaceTrigger.not(PlaceTrigger.flag("b")).test(context));
        assertEquals("any[b, a]", PlaceTrigger.anyOf(PlaceTrigger.flag("b"), PlaceTrigger.flag("a")).toString());
    }

    @Test
    void detachmentsFillLocksOldestFirstAndTheSurplusReinforcesTheNewest() {
        PlaceDirective landing = PlaceDirective.lock("landing", Faction.ALLY, PlaceTarget.region(10, 10, 4))
                .post(10, 8).post(10, 12).until(PlaceTrigger.anyOf(PlaceTrigger.flag("gain:1"), PlaceTrigger.flag("gain:2")))
                .key(0).build();
        PlaceDirective first = PlaceDirective.lock("first", Faction.ALLY, PlaceTarget.region(40, 10, 4))
                .when(PlaceTrigger.flag("gain:1")).post(40, 9).key(1).build();
        PlaceDirective second = PlaceDirective.lock("second", Faction.ALLY, PlaceTarget.region(80, 10, 4))
                .when(PlaceTrigger.flag("gain:2")).key(2).build();
        PlaceDirectives book = book(landing, first, second);
        assertNull(book.lockFor(0), "no lock before the book is evaluated");
        book.evaluate(PlaceSignals.at(0));
        assertEquals(new PlaceDirective.Post(10, 8), book.lockFor(0).post());
        assertEquals(new PlaceDirective.Post(10, 12), book.lockFor(1).post(), "posts cycle by detachment");
        assertEquals(new PlaceDirective.Post(10, 8), book.lockFor(2).post());
        // The second gain is taken first: the landing lets go on the same pulse the gain locks.
        book.evaluate(PlaceSignals.at(75).raise("gain:2"));
        assertEquals(PlaceDirectives.State.RELEASED, book.state("landing"));
        assertEquals(second, book.lockFor(0).directive());
        assertNull(book.lockFor(0).post(), "a lock with no posts stands on its centre or its place");
        book.evaluate(PlaceSignals.at(150).raise("gain:1"));
        assertEquals(List.of(second, first), book.active(PlaceDirective.Kind.LOCK), "kept in the order they locked");
        assertEquals(second, book.lockFor(0).directive());
        assertEquals(first, book.lockFor(1).directive());
        assertEquals(first, book.lockFor(5).directive(), "the surplus reinforces the newest");
        // Latched: a gain lost again does not unlock a lock that names no until.
        book.evaluate(PlaceSignals.at(225));
        assertEquals(List.of(second, first), book.active(PlaceDirective.Kind.LOCK));
        assertThrows(IllegalArgumentException.class, () -> book.lockFor(-1));
    }

    @Test
    void aBookForOnePartyHoldsOnlyItsOwnAndIdsAreUnique() {
        PlaceDirective ally = hold("ally", PlaceTrigger.always(), PlaceTrigger.never());
        PlaceDirective defender = PlaceDirective.counterAttack("defender", Faction.DEFENDER, YARD).build();
        assertEquals(List.of(ally), PlaceDirectives.forParty(List.of(ally, defender), Faction.ALLY).directives());
        assertThrows(IllegalArgumentException.class, () -> book(ally, ally));
    }

    /** A directive's context over a pulse, for testing a trigger alone. */
    private static PlaceTrigger.Context contextOf(PlaceSignals signals) {
        return new PlaceTrigger.Context() {
            @Override public int tick() { return signals.tick(); }
            @Override public int activeSince() { return -1; }
            @Override public PlaceTarget self() { return YARD; }
            @Override public int stage(String name) { return signals.stage(name); }
            @Override public boolean flag(String name) { return signals.flag(name); }
            @Override public PlaceHold hold(PlaceTarget target) { return signals.hold(target); }
            @Override public boolean everOwn() { return false; }
            @Override public float pressure(PlaceTarget target) { return signals.pressure(target); }
        };
    }
}
