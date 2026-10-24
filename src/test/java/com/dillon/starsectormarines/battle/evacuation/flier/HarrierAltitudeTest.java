package com.dillon.starsectormarines.battle.evacuation.flier;

import com.dillon.starsectormarines.battle.air.AirBody;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The drawn height a Harrier's shadow is laid by glides through a whole dive rather than jumping. */
class HarrierAltitudeTest {

    private static final float DT = 1f / 30f;

    @Test
    void theShadowOffsetMovesOnlyALittleEachTickThroughADive() {
        HarrierPack pack = new HarrierPack(1, 60f, 30f);
        for (int i = 0; i < 6; i++) {
            AirBody body = new AirBody();
            double a = 2 * Math.PI * i / 6;
            body.teleport(60f + (float) Math.cos(a) * 4f, 30f + (float) Math.sin(a) * 4f, 0f);
            HarrierFlight f = new HarrierFlight(100 + i, body, HarrierLook.DEFAULT);
            f.slot = i;
            pack.members.add(f);
        }
        float[] last = new float[6];
        for (int i = 0; i < 6; i++) last[i] = HarrierAltitude.shadowHeightCells(pack.members.get(i));
        float worst = 0f, lowest = 1f;
        for (int t = 0; t < 600; t++) {
            pack.prey(900L, 20f, 30f, 0.3f);
            pack.advance(DT, (h, target) -> { });
            for (int i = 0; i < 6; i++) {
                HarrierFlight m = pack.members.get(i);
                float h = HarrierAltitude.shadowHeightCells(m);
                worst = Math.max(worst, Math.abs(h - last[i]));
                lowest = Math.min(lowest, m.shownAltitude);
                last[i] = h;
            }
        }
        assertTrue(pack.dives() >= 1, "the pack dived");
        assertTrue(lowest < 0.4f, "the drawn height came down with the dive: " + lowest);
        assertTrue(worst <= 0.2f, "the shadow moved " + worst + " cells of height in one tick");
    }
}
