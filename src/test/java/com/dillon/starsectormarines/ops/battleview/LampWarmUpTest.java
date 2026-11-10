package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.NightLighting;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LampWarmUpTest {

    @Test
    void aLampIsDarkUntilItStrikesAndSteadyOnceSettled() {
        for (int lamp = 0; lamp < 200; lamp++) {
            float delay = LampWarmUp.delay(lamp);
            assertTrue(delay >= 0 && delay < LampWarmUp.MAX_DELAY_SECONDS);
            if (delay > 0.01f) assertFalse(LampWarmUp.lit(lamp, delay - 0.01f), "dark before it strikes");
            assertTrue(LampWarmUp.lit(lamp, delay + 0.001f), "its first step strikes");
            for (float t = LampWarmUp.settled(lamp); t < LampWarmUp.settled(lamp) + 2f; t += 0.05f) {
                assertTrue(LampWarmUp.lit(lamp, t), "steady once settled");
            }
        }
    }

    @Test
    void lampsStrikeAtDifferentMomentsAndFlicker() {
        boolean flickered = false;
        float earliest = Float.MAX_VALUE, latest = 0f;
        for (int lamp = 0; lamp < 50; lamp++) {
            earliest = Math.min(earliest, LampWarmUp.delay(lamp));
            latest = Math.max(latest, LampWarmUp.delay(lamp));
            for (float t = LampWarmUp.delay(lamp); t < LampWarmUp.settled(lamp); t += 0.02f) {
                if (!LampWarmUp.lit(lamp, t)) flickered = true;
            }
        }
        assertTrue(latest - earliest > LampWarmUp.MAX_DELAY_SECONDS / 2, "a band comes up across the delay");
        assertTrue(flickered, "a striking tube goes dark between steps");
    }

    @Test
    void repoweringKeepsTheLampsSoTheRendererWarmsRatherThanRebakes() {
        NightLighting night = new NightLighting(0f, .8f,
                List.of(new NightLighting.Lamp(1, 1, 3, 1, 1, 1, "a")), Set.of());
        assertSame(night.lamps(), night.withPowered(Set.of("a")).lamps());
    }
}
