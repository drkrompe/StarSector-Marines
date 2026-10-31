package com.dillon.starsectormarines.battle.command.allied;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlliedToolShedTest {

    private static final AlliedRequest PAIR = new AlliedRequest(AlliedTool.ARMOUR_PAIR, "lane-1", 50, 50, "uncovered");

    @Test
    void aRequestDrawsOnStockAndIsOnTheRoadUntilDone() {
        AtomicBoolean arrived = new AtomicBoolean();
        AlliedToolShed shed = new AlliedToolShed(sim -> new int[]{0, 0})
                .stock(AlliedTool.ARMOUR_PAIR, (sim, from, r) -> s -> arrived.get(), 1, 1, 0);
        assertTrue(shed.request(null, PAIR, 10));
        assertEquals(0, shed.stock(AlliedTool.ARMOUR_PAIR));
        assertEquals(1, shed.onRoad(AlliedTool.ARMOUR_PAIR, "lane-1"));
        assertEquals(0, shed.onRoad(AlliedTool.ARMOUR_PAIR, "lane-2"));
        assertFalse(shed.request(null, PAIR, 11));
        assertEquals("empty", shed.refusal(null, AlliedTool.ARMOUR_PAIR));
        arrived.set(true);
        shed.advance(null, 12);
        assertEquals(0, shed.onRoad(AlliedTool.ARMOUR_PAIR, "lane-1"));
        assertEquals(1, shed.granted().size());
    }

    @Test
    void stockComesBackOnItsPaceUpToItsCap() {
        AlliedToolShed shed = new AlliedToolShed(sim -> new int[]{0, 0})
                .stock(AlliedTool.ARMOUR_PAIR, (sim, from, r) -> s -> true, 2, 2, 100);
        assertTrue(shed.request(null, PAIR, 0));
        shed.advance(null, 99);
        assertEquals(1, shed.stock(AlliedTool.ARMOUR_PAIR));
        shed.advance(null, 100);
        assertEquals(2, shed.stock(AlliedTool.ARMOUR_PAIR));
        shed.advance(null, 400);
        assertEquals(2, shed.stock(AlliedTool.ARMOUR_PAIR));
    }

    @Test
    void aCutSupplyLineStopsTheRestockClockForAsLongAsItLasts() {
        AtomicBoolean supplied = new AtomicBoolean(true);
        AlliedToolShed shed = new AlliedToolShed(sim -> new int[]{0, 0})
                .stock(AlliedTool.ARMOUR_PAIR, (sim, from, r) -> s -> true, 1, 1, 100)
                .suppliedWhile(AlliedTool.ARMOUR_PAIR, sim -> supplied.get());
        assertTrue(shed.request(null, PAIR, 0));
        for (int tick = 1; tick < 50; tick++) shed.advance(null, tick);
        supplied.set(false);
        for (int tick = 50; tick < 80; tick++) shed.advance(null, tick);
        supplied.set(true);
        for (int tick = 80; tick < 130; tick++) shed.advance(null, tick);
        assertEquals(0, shed.stock(AlliedTool.ARMOUR_PAIR), "thirty ticks cut, thirty ticks late");
        shed.advance(null, 130);
        assertEquals(1, shed.stock(AlliedTool.ARMOUR_PAIR));
    }

    @Test
    void anOverrunDepotAClosedGateOrAFailedMakerSendsNothing() {
        AlliedToolShed overrun = new AlliedToolShed(sim -> null)
                .stock(AlliedTool.ARMOUR_PAIR, (sim, from, r) -> s -> true, 1, 1, 0);
        assertFalse(overrun.request(null, PAIR, 0));
        assertEquals("depot", overrun.refusal(null, AlliedTool.ARMOUR_PAIR));

        AtomicBoolean open = new AtomicBoolean();
        AlliedRequest sortie = new AlliedRequest(AlliedTool.GUNSHIP, "bore-2", 0, 0, "matriarch");
        AlliedToolShed gated = new AlliedToolShed(sim -> new int[]{0, 0})
                .stock(AlliedTool.GUNSHIP, (sim, from, r) -> s -> true, 1, 1, 0)
                .gated(AlliedTool.GUNSHIP, sim -> open.get());
        assertFalse(gated.request(null, sortie, 0));
        open.set(true);
        assertTrue(gated.request(null, sortie, 0));

        AlliedToolShed noGround = new AlliedToolShed(sim -> new int[]{0, 0})
                .stock(AlliedTool.ARMOUR_PAIR, (sim, from, r) -> null, 1, 1, 0);
        assertFalse(noGround.request(null, PAIR, 0));
        assertEquals(1, noGround.stock(AlliedTool.ARMOUR_PAIR));
        assertEquals("none", noGround.refusal(null, AlliedTool.COLUMN));
    }

    @Test
    void aCapThatCountsTheRoadRestocksOnlyOnceOneComesOffIt() {
        AtomicBoolean arrived = new AtomicBoolean();
        AlliedRequest truck = new AlliedRequest(AlliedTool.AMMO_TRUCK, "redoubt 1", 5, 5, "low");
        AlliedToolShed shed = new AlliedToolShed(sim -> new int[]{0, 0})
                .stock(AlliedTool.AMMO_TRUCK, (sim, from, r) -> s -> arrived.get(), 1, 1, 10)
                .capsOnRoad(AlliedTool.AMMO_TRUCK);
        assertTrue(shed.request(null, truck, 0));
        for (int tick = 1; tick <= 100; tick++) shed.advance(null, tick);
        assertEquals(0, shed.stock(AlliedTool.AMMO_TRUCK), "one out of a cap of one: no more however long it is out");
        arrived.set(true);
        for (int tick = 101; tick < 111; tick++) shed.advance(null, tick);
        assertEquals(0, shed.stock(AlliedTool.AMMO_TRUCK), "the clock starts when it comes off the road");
        shed.advance(null, 111);
        assertEquals(1, shed.stock(AlliedTool.AMMO_TRUCK));
    }
}
