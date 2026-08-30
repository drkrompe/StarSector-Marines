package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.fx.OrdnanceDelivery;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three deliveries have to be told apart, which is the whole reason the
 * presentation branches on the delivery instead of scaling one effect.
 */
final class OrdnanceFxTest {

    @Test
    void eachDeliveryDrawsItselfDifferently() {
        assertInstanceOf(OrdnanceFx.Streak.class, OrdnanceFx.of(OrdnanceDelivery.SHELL).trace());
        assertInstanceOf(OrdnanceFx.Line.class, OrdnanceFx.of(OrdnanceDelivery.BEAM).trace());
        assertInstanceOf(OrdnanceFx.Falling.class, OrdnanceFx.of(OrdnanceDelivery.BOMB).trace());
    }

    @Test
    void noTwoDeliveriesShareAFireOrImpactCue() {
        Set<String> fire = new HashSet<>();
        Set<String> impact = new HashSet<>();
        for (OrdnanceDelivery delivery : OrdnanceDelivery.values()) {
            OrdnanceFx fx = OrdnanceFx.of(delivery);
            assertNotNull(fx.fireSoundId(), delivery + " has no weapon sound");
            assertNotNull(fx.impactSoundId(), delivery + " has no arrival sound");
            assertTrue(fire.add(fx.fireSoundId()), delivery + " reuses a fire cue");
            assertTrue(impact.add(fx.impactSoundId()), delivery + " reuses an impact cue");
        }
    }

    @Test
    void onlyTheBeamIsHeldAndOnlyTheBombArrivesHeavy() {
        assertTrue(OrdnanceFx.of(OrdnanceDelivery.BEAM).fireLoops());
        assertFalse(OrdnanceFx.of(OrdnanceDelivery.SHELL).fireLoops());
        assertFalse(OrdnanceFx.of(OrdnanceDelivery.BOMB).fireLoops());

        assertTrue(OrdnanceFx.of(OrdnanceDelivery.BOMB).heavyImpact());
        assertFalse(OrdnanceFx.of(OrdnanceDelivery.SHELL).heavyImpact());
        assertFalse(OrdnanceFx.of(OrdnanceDelivery.BEAM).heavyImpact());
    }

    @Test
    void aBeamArrivesAtOnceAndABombIsWatchedComingDown() {
        assertEquals(0f, OrdnanceFx.of(OrdnanceDelivery.BEAM).trace().flightSeconds());
        assertTrue(OrdnanceFx.of(OrdnanceDelivery.BOMB).trace().flightSeconds()
                > OrdnanceFx.of(OrdnanceDelivery.SHELL).trace().flightSeconds(),
                "a bomb falls for longer than a shell flies");
    }

    @Test
    void thinnedCadenceIsDeclaredForTheHighVolumeGuns() {
        // Fourteen rounds a second played as fourteen clips is static. The
        // cannon and the beam both declare a gate; a bomb rack does not need one.
        assertTrue(OrdnanceFx.of(OrdnanceDelivery.SHELL).fireCueMinGap() > 0f);
        assertTrue(OrdnanceFx.of(OrdnanceDelivery.SHELL).impactCueMinGap() > 0f);
        assertTrue(OrdnanceFx.of(OrdnanceDelivery.BEAM).impactCueMinGap() > 0f);
    }

    @Test
    void onlyTheFallingBodyClaimsATexture() {
        Set<String> paths = OrdnanceFx.spritePaths();
        assertEquals(Set.of(OrdnanceFx.BOMB_SPRITE_PATH), paths);
    }

    @Test
    void everyAuthoredCompositionParses() {
        // The compositions are text blocks resolved in a static initializer, so
        // a malformed one is a class-load failure rather than a bad-looking
        // impact. Naming that here makes the failure legible.
        assertNotNull(OrdnanceFx.of(OrdnanceDelivery.SHELL).fx());
        assertNotNull(OrdnanceFx.of(OrdnanceDelivery.BEAM).fx());
    }
}
