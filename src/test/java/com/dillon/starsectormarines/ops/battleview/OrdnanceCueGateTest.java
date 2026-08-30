package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.fx.OrdnanceDelivery;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Thinning a fourteen-round second down to something you can hear as a gun. */
final class OrdnanceCueGateTest {

    @Test
    void theFirstCueOfAKindAlwaysPlays() {
        OrdnanceCueGate gate = new OrdnanceCueGate();
        assertTrue(gate.allowFire(OrdnanceDelivery.SHELL));
        assertTrue(gate.allowImpact(OrdnanceDelivery.SHELL));
    }

    @Test
    void backToBackRoundsInOneFrameCollapseToOneCue() {
        OrdnanceCueGate gate = new OrdnanceCueGate();
        assertTrue(gate.allowFire(OrdnanceDelivery.SHELL));
        assertFalse(gate.allowFire(OrdnanceDelivery.SHELL));
        assertFalse(gate.allowFire(OrdnanceDelivery.SHELL));
    }

    @Test
    void theCueReturnsOnceItsOwnGapHasElapsed() {
        OrdnanceCueGate gate = new OrdnanceCueGate();
        float gap = OrdnanceFx.of(OrdnanceDelivery.SHELL).fireCueMinGap();
        assertTrue(gate.allowFire(OrdnanceDelivery.SHELL));

        gate.advance(gap * 0.5f);
        assertFalse(gate.allowFire(OrdnanceDelivery.SHELL));
        gate.advance(gap * 0.6f);
        assertTrue(gate.allowFire(OrdnanceDelivery.SHELL));
    }

    @Test
    void fireAndImpactAreSeparateClocks() {
        OrdnanceCueGate gate = new OrdnanceCueGate();
        assertTrue(gate.allowFire(OrdnanceDelivery.SHELL));
        assertTrue(gate.allowImpact(OrdnanceDelivery.SHELL),
                "a round arriving is not the same event as one leaving");
    }

    @Test
    void deliveriesDoNotGateEachOther() {
        OrdnanceCueGate gate = new OrdnanceCueGate();
        assertTrue(gate.allowFire(OrdnanceDelivery.SHELL));
        assertTrue(gate.allowFire(OrdnanceDelivery.BOMB));
    }

    @Test
    void aCannonBurstIsThinnedToARoughlyTenPerSecondCadence() {
        // The simulated load is 14 rounds/sec; what the gate lets through over a
        // second is what the player actually hears.
        OrdnanceCueGate gate = new OrdnanceCueGate();
        int played = 0;
        float dt = 1f / 60f;
        float roundClock = 0f;
        for (int frame = 0; frame < 60; frame++) {
            gate.advance(dt);
            roundClock += dt;
            while (roundClock >= 1f / 14f) {
                roundClock -= 1f / 14f;
                if (gate.allowFire(OrdnanceDelivery.SHELL)) played++;
            }
        }
        assertTrue(played >= 6 && played <= 12,
                "expected a burst rather than static, got " + played + " cues");
    }
}
