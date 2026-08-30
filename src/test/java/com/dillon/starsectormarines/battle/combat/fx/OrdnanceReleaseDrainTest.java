package com.dillon.starsectormarines.battle.combat.fx;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The per-frame drain rounds are published into. */
final class OrdnanceReleaseDrainTest {

    @Test
    void releasesArePublishedAndClearedAtTheTopOfEachFrame() {
        EffectsService effects = new EffectsService(new Random(1L));
        effects.spawnOrdnanceRelease(new OrdnanceRelease(
                7L, OrdnanceDelivery.SHELL, 1f, 2f, 3f, 4f, 1.3f, Faction.DEFENDER));

        assertEquals(1, effects.getOrdnanceReleasesThisFrame().size());

        // A paused host keeps calling beginFrame; a queue that never cleared
        // would replay the same burst forever.
        effects.beginFrame();
        assertTrue(effects.getOrdnanceReleasesThisFrame().isEmpty());
    }
}
