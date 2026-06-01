package com.dillon.starsectormarines.battle.unit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for {@link DeathDispatcher} — the buffered death-event
 * mailbox. Covers the publish/drain split (publish does not invoke handlers),
 * fan-out to every subscriber, publish-then-subscribe ordering, buffer-clear
 * after drain, and the empty-drain no-op.
 */
public class DeathDispatcherTest {

    private static Unit unit(String id) {
        return new Unit(id, Faction.MARINE, UnitType.MARINE, 0, 0);
    }

    @Test
    public void publishDoesNotInvokeHandlersUntilDrain() {
        DeathDispatcher dispatcher = new DeathDispatcher();
        List<Unit> seen = new ArrayList<>();
        dispatcher.subscribe(e -> seen.add(e.unit()));

        Unit u = unit("a");
        dispatcher.publish(new DeathEvent(u));
        assertTrue(seen.isEmpty(), "publish must buffer — handlers fire only on drain");

        dispatcher.drain();
        assertEquals(List.of(u), seen, "drain fans the buffered event to the handler");
    }

    @Test
    public void drainFansEachEventToEverySubscriberInOrder() {
        DeathDispatcher dispatcher = new DeathDispatcher();
        List<String> log = new ArrayList<>();
        dispatcher.subscribe(e -> log.add("h1:" + e.unit().id));
        dispatcher.subscribe(e -> log.add("h2:" + e.unit().id));

        dispatcher.publish(new DeathEvent(unit("a")));
        dispatcher.publish(new DeathEvent(unit("b")));
        dispatcher.drain();

        // Outer loop is publish order, inner loop is subscribe order.
        assertEquals(List.of("h1:a", "h2:a", "h1:b", "h2:b"), log);
    }

    @Test
    public void drainClearsTheBufferSoEventsFireExactlyOnce() {
        DeathDispatcher dispatcher = new DeathDispatcher();
        List<Unit> seen = new ArrayList<>();
        dispatcher.subscribe(e -> seen.add(e.unit()));

        dispatcher.publish(new DeathEvent(unit("a")));
        dispatcher.drain();
        dispatcher.drain(); // second drain has nothing buffered

        assertEquals(1, seen.size(), "an event must not be re-delivered on a subsequent drain");
    }

    @Test
    public void drainOnEmptyBufferIsANoOp() {
        DeathDispatcher dispatcher = new DeathDispatcher();
        int[] calls = {0};
        dispatcher.subscribe(e -> calls[0]++);
        dispatcher.drain();
        assertEquals(0, calls[0], "no events buffered → no handler invocations");
    }
}
