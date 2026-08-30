package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.combat.fx.OrdnanceDelivery;
import com.dillon.starsectormarines.battle.combat.fx.OrdnanceRelease;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading an ordnance load as a kind of delivery, and one released round as a picture of it. */
final class AirOrdnanceDeliveryTest {

    private static final float EPS = 1e-4f;

    @Test
    void theThreeShippedLoadsAreThreeDifferentDeliveries() {
        assertEquals(OrdnanceDelivery.SHELL, AirOrdnanceDelivery.of(AirOrdnance.AUTOCANNON));
        assertEquals(OrdnanceDelivery.BEAM, AirOrdnanceDelivery.of(AirOrdnance.BEAM));
        assertEquals(OrdnanceDelivery.BOMB, AirOrdnanceDelivery.of(AirOrdnance.BOMBS));
    }

    @Test
    void anUnnamedLoadFallsBackOnWhetherItRunsOut() {
        // A gun fires for as long as it holds the target; a rack releases what
        // it loaded. That structural split is the only thing a load nobody
        // named can be classified by, and it is the one the model draws.
        AirOrdnance gun = new AirOrdnance(9f, 0,
                OrdnanceFlight.powered(6f, 25f, 520f, 0f), 1.5f, 20f, 1.2f, 20f, 8f, 20);
        AirOrdnance rack = new AirOrdnance(4f, 3,
                OrdnanceFlight.dropped(10f, 1f), 2f, 14f, 3f, 70f, 14f, 140);
        assertEquals(OrdnanceDelivery.SHELL, AirOrdnanceDelivery.of(gun));
        assertEquals(OrdnanceDelivery.BOMB, AirOrdnanceDelivery.of(rack));
    }

    @Test
    void theRoundIsDrawnLeavingAheadOfTheOriginAndArrivingWhereItLanded() {
        AirBody body = new AirBody();
        body.teleport(10f, 20f, 0f);
        // Nose along +X: facingDegrees + 90 is the heading AirSystem aims on.
        double nose = 0d;

        OrdnanceRelease release = AirOrdnanceDelivery.release(
                77L, AirOrdnance.AUTOCANNON, body, nose, 18f, 21f, Faction.DEFENDER,
                AirOrdnance.AUTOCANNON.flight.flightTimeSec());

        assertEquals(77L, release.sourceId());
        assertEquals(OrdnanceDelivery.SHELL, release.delivery());
        assertEquals(10f + AirOrdnanceDelivery.MUZZLE_OFFSET_CELLS, release.fromX(), EPS);
        assertEquals(20f, release.fromY(), EPS);
        assertEquals(18f, release.toX(), EPS);
        assertEquals(21f, release.toY(), EPS);
        assertEquals(AirOrdnance.AUTOCANNON.aoeRadiusCells, release.radiusCells(), EPS);
        assertEquals(Faction.DEFENDER, release.faction());
        assertTrue(release.fromX() > body.x, "the flash belongs at the nose, not mid-hull");
    }
}
