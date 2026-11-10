package com.dillon.starsectormarines.battle.derelict;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoforgeTest {

    private static final BuildQueue.Item STRIPPER = BuildQueue.Item.walker(DerelictWalkerFrame.STRIPPER, 10f);
    private static final BuildQueue.Item STAKE = BuildQueue.Item.stake(30f, DerelictWalkerFrame.ROUNDS);
    private static final BuildQueue.Item LINE = BuildQueue.Item.walker(DerelictWalkerFrame.LINE, 20f);

    private static Autoforge forge() {
        return new Autoforge(new BuildQueue(List.of(STRIPPER, STAKE, LINE)));
    }

    @Test
    void theForgePrintsTheQueueInOrderOncePerCycleWhileItHasFeedstock() {
        Autoforge forge = forge();
        List<Feedstock> stores = List.of(new Feedstock(100f));
        assertNull(forge.tick(Autoforge.CYCLE_SECONDS - 1f, stores, i -> true), "nothing before the cycle ends");
        assertSame(STRIPPER, forge.tick(1f, stores, i -> true));
        assertSame(STAKE, forge.tick(Autoforge.CYCLE_SECONDS, stores, i -> true));
        assertSame(LINE, forge.tick(Autoforge.CYCLE_SECONDS, stores, i -> true));
        assertSame(STRIPPER, forge.tick(Autoforge.CYCLE_SECONDS, stores, i -> true), "the program starts again");
        assertEquals(100f - 10f - 30f - 20f - 10f, Feedstock.total(stores), 1e-4f);
    }

    @Test
    void shortOfFeedstockTheNextItemWaitsAndNothingIsTaken() {
        Autoforge forge = forge();
        Feedstock store = new Feedstock(5f);
        assertNull(forge.tick(Autoforge.CYCLE_SECONDS, List.of(store), i -> true));
        assertEquals(5f, store.metal());
        assertSame(STRIPPER, forge.queue().peek(), "the queue does not skip what it cannot pay for");
        store.deposit(5f);
        assertSame(STRIPPER, forge.tick(Autoforge.CYCLE_SECONDS, List.of(store), i -> true));
    }

    @Test
    void anItemWithNowhereToGoIsPassedOverAtNoCost() {
        Autoforge forge = forge();
        List<Feedstock> stores = List.of(new Feedstock(100f));
        forge.tick(Autoforge.CYCLE_SECONDS, stores, i -> true);
        assertSame(LINE, forge.tick(Autoforge.CYCLE_SECONDS, stores, i -> i.kind() != BuildQueue.Kind.STAKE));
        assertEquals(100f - 10f - 20f, Feedstock.total(stores), 1e-4f);
    }

    @Test
    void aForgePutOutPrintsNothingAgain() {
        Autoforge forge = forge();
        forge.putOut();
        assertFalse(forge.lit());
        assertNull(forge.tick(Autoforge.CYCLE_SECONDS * 3, List.of(new Feedstock(100f)), i -> true));
        assertEquals(0, forge.printed());
    }

    @Test
    void theQueueReadsAheadRoundItsEnd() {
        BuildQueue queue = new BuildQueue(List.of(STRIPPER, STAKE, LINE));
        queue.advance();
        queue.advance();
        assertEquals(List.of(LINE, STRIPPER, STAKE, LINE), queue.peek(4));
    }

    @Test
    void feedstockIsDrawnAllOrNothingTheHullsStoreFirst() {
        Feedstock hull = new Feedstock(10f);
        Feedstock stake = new Feedstock(15f);
        assertFalse(Feedstock.draw(List.of(hull, stake), 30f));
        assertEquals(25f, hull.metal() + stake.metal());
        assertTrue(Feedstock.draw(List.of(hull, stake), 18f));
        assertEquals(0f, hull.metal());
        assertEquals(7f, stake.metal(), 1e-4f);
    }

    @Test
    void aForgeLowOnPlateIsBreached() {
        assertFalse(Autoforge.breached(1f));
        assertTrue(Autoforge.breached(Autoforge.BREACH_SHARE - 0.01f));
    }
}
