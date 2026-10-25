package com.dillon.starsectormarines.battle.evacuation.flier;

import com.dillon.starsectormarines.battle.air.AirBody;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A Harrier fetching a body home: when its pack sends it, the swoop, the haul and the way back. */
class HarrierCarryTest {

    private static final float DT = 1f / 30f;

    private static HarrierFlight flight(long id, float x, float y) {
        AirBody body = new AirBody();
        body.teleport(x, y, 0f);
        return new HarrierFlight(id, body, HarrierLook.DEFAULT);
    }

    @Test
    void aPackSendsOneCarrierForEveryThreeAndOnlyWithNothingToHunt() {
        assertEquals(0, HarrierCarry.carriersFor(2));
        assertEquals(1, HarrierCarry.carriersFor(3));
        assertEquals(2, HarrierCarry.carriersFor(7));
        assertTrue(HarrierCarry.sends(HarrierPack.Phase.LOITER, false, 0, 7, 1));
        assertFalse(HarrierCarry.sends(HarrierPack.Phase.LOITER, false, 0, 7, 2), "two of seven already out");
        assertFalse(HarrierCarry.sends(HarrierPack.Phase.LOITER, true, 0, 7, 0), "it has prey");
        assertFalse(HarrierCarry.sends(HarrierPack.Phase.DIVE, false, 0, 7, 0), "it is diving");
        assertFalse(HarrierCarry.sends(HarrierPack.Phase.LOITER, false, 2, 7, 0), "still hatching");
    }

    @Test
    void itSwoopsLowOntoTheBodyAndHaulsItLowToTheMouth() {
        HarrierFlight m = flight(1L, 0f, 0f);
        HarrierCarry c = new HarrierCarry(9L);
        boolean reached = false;
        for (int t = 0; t < 300 && !reached; t++) reached = c.swoop(m, 12f, 0f, DT);
        assertTrue(reached, "reached the body");
        assertTrue(m.altitude <= HarrierRules.LOW_ALTITUDE, "low enough to take it");
        c.took();
        assertEquals(HarrierCarry.Phase.HAUL, c.phase());
        for (int t = 0; t < 900; t++) c.haul(m, 12f, 40f, DT);
        assertTrue(m.body.distanceTo(12f, 40f) < 1.5f, "at the mouth, " + m.body.distanceTo(12f, 40f));
        assertEquals(HarrierCarry.HAUL_ALTITUDE, m.altitude, 1e-3f);
    }

    @Test
    void aBodyItCannotReachInTimeIsGivenUp() {
        HarrierFlight m = flight(1L, 0f, 0f);
        HarrierCarry c = new HarrierCarry(9L);
        for (int t = 0; t < Math.round(HarrierCarry.SNATCH_SECONDS / DT) + 1; t++) assertFalse(c.swoop(m, 500f, 0f, DT));
        assertEquals(HarrierCarry.Phase.RETURN, c.phase());
    }

    @Test
    void itFliesBackUpToItsPacksRing() {
        HarrierFlight m = flight(1L, 0f, 0f);
        m.altitude = HarrierCarry.HAUL_ALTITUDE;
        HarrierCarry c = new HarrierCarry(9L);
        c.giveUp();
        boolean back = false;
        for (int t = 0; t < 600 && !back; t++) back = c.rejoin(m, 30f, 0f, 3f, DT);
        assertTrue(back);
        assertTrue(m.body.distanceTo(30f, 0f) <= 3f + HarrierRules.GATHER_MARGIN);
    }

    @Test
    void itsPackLeavesItAloneAndDivesWithoutIt() {
        HarrierPack pack = new HarrierPack(1, 60f, 30f);
        for (int i = 0; i < 4; i++) {
            HarrierFlight f = flight(100 + i, 60f + 4f * (float) Math.cos(i * Math.PI / 2), 30f + 4f * (float) Math.sin(i * Math.PI / 2));
            f.slot = i;
            pack.members.add(f);
        }
        HarrierFlight carrier = pack.members.get(3);
        carrier.carry = new HarrierCarry(77L);
        float cx = carrier.body.x, cy = carrier.body.y;
        List<Long> strikers = new ArrayList<>();
        for (int t = 0; t < 600; t++) {
            pack.prey(900L, 20f, 30f, 0.3f);
            pack.advance(DT, (h, target) -> strikers.add(h));
        }
        assertTrue(pack.dives() > 0, "the rest dived");
        assertFalse(strikers.contains(carrier.id), "the carrier never bit");
        assertEquals(cx, carrier.body.x, 1e-4f, "nor did the pack fly it");
        assertEquals(cy, carrier.body.y, 1e-4f);
    }
}
