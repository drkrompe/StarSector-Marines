package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A called ship comes out of the clouds a short glide from its station, not at the far edge. */
class ShipStationingCallInTest {

    private static final float LENGTH = 10f;

    @Test
    void aDeepStationIsReachedByAShortGlideFromTheCompanysSide() {
        ShipStationing.Placement p = ShipStationing.callIn(Faction.MARINE, TraversalAxis.WEST_TO_EAST,
                200, 100, LENGTH, 120f, 50f);
        float glide = (float) Math.hypot(120f - p.x(), 50f - p.y());
        assertEquals(ShipStationing.CALL_SHORT_CELLS + LENGTH / 2f, glide, 1e-3f);
        assertTrue(p.x() < 120f, "it comes in from the company's side of its station");
        assertEquals(-90f, p.facingDegrees(), 1e-3f, "facing east, onto its station");
        assertEquals(120f, p.stationX(), 1e-6f);
        assertEquals(50f, p.stationY(), 1e-6f);
    }

    @Test
    void aStationNearTheEdgeKeepsTheEdgeStart() {
        ShipStationing.Placement edge = ShipStationing.place(Faction.MARINE, TraversalAxis.WEST_TO_EAST, 200, 100, LENGTH);
        ShipStationing.Placement p = ShipStationing.callIn(Faction.MARINE, TraversalAxis.WEST_TO_EAST,
                200, 100, LENGTH, edge.x() + 8f, 50f);
        assertEquals(edge.x(), p.x(), 1e-6f);
        assertEquals(edge.y(), p.y(), 1e-6f);
    }

    @Test
    void anOffAxisStationIsFacedAndTheStartStaysOnTheMap() {
        ShipStationing.Placement p = ShipStationing.callIn(Faction.MARINE, TraversalAxis.WEST_TO_EAST,
                200, 100, LENGTH, 60f, 98f);
        assertTrue(p.y() <= 100f - LENGTH / 2f && p.y() >= LENGTH / 2f, "kept a half hull inside the map");
        float toStation = (float) Math.toDegrees(Math.atan2(98f - p.y(), 60f - p.x())) - 90f;
        assertEquals(toStation, p.facingDegrees(), 1e-3f, "nose on its station");
    }

    @Test
    void aDefendersShipComesFromItsOwnEdge() {
        ShipStationing.Placement p = ShipStationing.callIn(Faction.DEFENDER, TraversalAxis.WEST_TO_EAST,
                200, 100, LENGTH, 80f, 50f);
        assertTrue(p.x() > 80f, "east of its station, from the defender's side");
    }
}
