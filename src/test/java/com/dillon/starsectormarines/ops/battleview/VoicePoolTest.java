package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoicePoolTest {

    enum S { CRY }

    /** Three voices of a second each, and one body may not repeat within half a second. */
    private static final VoicePool.Layering LAYERING = new VoicePool.Layering(3, 1f, 0.5f);

    private static VoicePool<S, String> pool() {
        return new VoicePool<>(S.class, s -> LAYERING);
    }

    private static int ticks(float seconds) {
        return Math.round(seconds / BattleSimulation.TICK_DT);
    }

    @Test void voicesLayerUpToTheSoundsVoiceCount() {
        VoicePool<S, String> pool = pool();
        for (int i = 0; i < 3; i++) {
            assertEquals(VoicePool.Admit.START, pool.offer(S.CRY, 100 + i, i, "", 0), "body " + i + " layers on");
            assertEquals(i + 1, pool.sounding(S.CRY));
        }
        assertEquals(VoicePool.Admit.DROP, pool.offer(S.CRY, 200, 3, "", 0), "every voice is sounding");
        assertEquals(VoicePool.Admit.START, pool.offer(S.CRY, 200, ticks(1f) + 1, "", 0), "a voice has finished");
    }

    @Test void twoVoicesNeverStartOnOneTickAndTheSecondIsHeld() {
        VoicePool<S, String> pool = pool();
        assertEquals(VoicePool.Admit.START, pool.offer(S.CRY, 1, 10, "first", 0));
        assertEquals(VoicePool.Admit.HOLD, pool.offer(S.CRY, 2, 10, "second", 0));
        assertTrue(pool.due(10).isEmpty(), "not on the tick it was held");
        List<VoicePool.Held<S, String>> later = pool.due(10 + VoicePool.HOLD_TICKS);
        assertEquals(1, later.size());
        assertEquals("second", later.get(0).payload());
        assertEquals(VoicePool.Admit.START, pool.offer(S.CRY, 2, 10 + VoicePool.HOLD_TICKS, "second",
                later.get(0).heldFor()));
    }

    @Test void aVoiceHeldItsLimitIsNotHeldAgain() {
        VoicePool<S, String> pool = pool();
        pool.offer(S.CRY, 1, 10, "", 0);
        assertEquals(VoicePool.Admit.HOLD, pool.offer(S.CRY, 2, 10, "", VoicePool.HOLD_TICKS));
        assertTrue(pool.due(10 + 10).isEmpty(), "it waited long enough already, and is let go");
    }

    @Test void oneBodyDoesNotRepeatInsideItsGap() {
        VoicePool<S, String> pool = pool();
        int gap = ticks(0.5f);
        assertEquals(VoicePool.Admit.START, pool.offer(S.CRY, 7, 0, "", 0));
        assertEquals(VoicePool.Admit.DROP, pool.offer(S.CRY, 7, gap - 1, "", 0));
        assertEquals(VoicePool.Admit.START, pool.offer(S.CRY, 8, gap - 1, "", 0), "another body is heard");
        assertEquals(VoicePool.Admit.START, pool.offer(S.CRY, 7, gap + 1, "", 0));
    }

    @Test void layeredVoicesGrowSlowerThanTheirCount() {
        assertEquals(1f, VoicePool.layerGain(1));
        for (int n = 2; n <= 8; n++) {
            float g = VoicePool.layerGain(n), prior = VoicePool.layerGain(n - 1);
            assertTrue(g < prior, "each layer quieter than the last");
            assertTrue(n * g * g > (n - 1) * prior * prior, "a bigger crowd is still louder");
            assertTrue(n * g * g < n, "but far less than n voices at full level");
        }
    }
}
