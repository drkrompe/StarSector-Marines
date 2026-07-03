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

    /** Death event for entity id {@code id} — cell + pose are irrelevant to dispatcher mechanics. */
    private static DeathEvent death(long id) {
        return new DeathEvent(id, 0, 0, -1);
    }

    @Test
    public void publishDoesNotInvokeHandlersUntilDrain() {
        DeathDispatcher dispatcher = new DeathDispatcher();
        List<Long> seen = new ArrayList<>();
        dispatcher.subscribe(e -> seen.add(e.unitId()));

        dispatcher.publish(death(1L));
        assertTrue(seen.isEmpty(), "publish must buffer — handlers fire only on drain");

        dispatcher.drain();
        assertEquals(List.of(1L), seen, "drain fans the buffered event to the handler");
    }

    @Test
    public void drainFansEachEventToEverySubscriberInOrder() {
        DeathDispatcher dispatcher = new DeathDispatcher();
        List<String> log = new ArrayList<>();
        dispatcher.subscribe(e -> log.add("h1:" + e.unitId()));
        dispatcher.subscribe(e -> log.add("h2:" + e.unitId()));

        dispatcher.publish(death(1L));
        dispatcher.publish(death(2L));
        dispatcher.drain();

        // Outer loop is publish order, inner loop is subscribe order.
        assertEquals(List.of("h1:1", "h2:1", "h1:2", "h2:2"), log);
    }

    @Test
    public void drainClearsTheBufferSoEventsFireExactlyOnce() {
        DeathDispatcher dispatcher = new DeathDispatcher();
        List<Long> seen = new ArrayList<>();
        dispatcher.subscribe(e -> seen.add(e.unitId()));

        dispatcher.publish(death(1L));
        dispatcher.drain();
        dispatcher.drain(); // second drain has nothing buffered

        assertEquals(1, seen.size(), "an event must not be re-delivered on a subsequent drain");
    }

    @Test
    public void reentrantPublishDuringDrainIsFannedOutInTheSameDrain() {
        DeathDispatcher dispatcher = new DeathDispatcher();
        List<Long> seen = new ArrayList<>();
        long hubId = 1L, droneId = 2L;
        // Handler A models a cascade: seeing the hub die, it kills a drone —
        // publishing a fresh DeathEvent while drain() is still fanning out.
        dispatcher.subscribe(e -> {
            if (e.unitId() == hubId) {
                dispatcher.publish(death(droneId));
            }
        });
        // Handler B records every event it is handed.
        dispatcher.subscribe(e -> seen.add(e.unitId()));

        dispatcher.publish(death(hubId));
        dispatcher.drain();

        // The drone death, published mid-drain, is fanned out in the same drain
        // (next wave) — exactly once, after the hub. No drop, no double-fire.
        assertEquals(List.of(hubId, droneId), seen);
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
