package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.command.allied.AlliedCap.Pool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlliedCapTest {

    @Test
    void aSpawnAtTheCapWaitsAndGoesWhenThereIsRoom() {
        AlliedCap cap = new AlliedCap().limit(Pool.INFANTRY, 20);
        assertFalse(cap.mayGo("wave", Pool.INFANTRY, 16, 8, 0), "8 standing and 16 more would pass 20");
        assertEquals(1, cap.deferred(Pool.INFANTRY));
        assertFalse(cap.mayGo("wave", Pool.INFANTRY, 16, 8, 1));
        assertEquals(1, cap.deferred(Pool.INFANTRY), "a wave that waits is counted once");
        assertTrue(cap.mayGo("wave", Pool.INFANTRY, 16, 4, 2), "losses made room: it goes");
        assertEquals(0, cap.waiting(Pool.INFANTRY));
    }

    @Test
    void spawnsGoInTheOrderTheyAsked() {
        AlliedCap cap = new AlliedCap().limit(Pool.MACHINES, 10);
        assertFalse(cap.mayGo("pair-lane-1", Pool.MACHINES, 2, 9, 0));
        assertFalse(cap.mayGo("tank-lane-2", Pool.MACHINES, 1, 9, 0), "the pair asked first");
        assertFalse(cap.mayGo("tank-lane-2", Pool.MACHINES, 1, 8, 1), "room for one only: still behind the pair");
        assertTrue(cap.mayGo("pair-lane-1", Pool.MACHINES, 2, 8, 1));
        assertTrue(cap.mayGo("tank-lane-2", Pool.MACHINES, 1, 9, 2));
    }

    @Test
    void aRequestThatStopsAskingLeavesTheQueue() {
        AlliedCap cap = new AlliedCap().limit(Pool.MACHINES, 4);
        assertFalse(cap.mayGo("gone", Pool.MACHINES, 4, 4, 0));
        assertTrue(cap.mayGo("next", Pool.MACHINES, 1, 3, AlliedCap.EXPIRE_TICKS + 1), "the stale request no longer holds the line");
    }

    @Test
    void aPoolWithNoDeclaredLimitNeverWaits() {
        AlliedCap cap = new AlliedCap().limit(Pool.MACHINES, 1);
        assertTrue(cap.mayGo(null, "wave", Pool.INFANTRY, 500));
        assertFalse(new AlliedCap().declared());
    }
}
