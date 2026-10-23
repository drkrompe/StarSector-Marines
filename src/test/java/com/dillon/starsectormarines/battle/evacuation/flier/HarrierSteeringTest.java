package com.dillon.starsectormarines.battle.evacuation.flier;

import com.dillon.starsectormarines.battle.air.AirBody;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A flier's own steering: a capped turn, a push off packmates, and no two bodies on one point. */
class HarrierSteeringTest {

    private static HarrierFlight at(long id, int slot, float x, float y) {
        AirBody body = new AirBody();
        body.teleport(x, y, 0f);
        HarrierFlight f = new HarrierFlight(id, body, HarrierLook.DEFAULT);
        f.slot = slot;
        return f;
    }

    @Test
    void aGoalBehindIsReachedByTurningAtTheCappedRate() {
        AirBody body = new AirBody();
        body.teleport(0f, 0f, 0f);                 // nose north
        HarrierSteering.fly(body, 0f, -10f, 0f, 0f, 5f, 180f, 0.1f);
        assertEquals(18f, Math.abs(body.facingDegrees), 1e-3f);
    }

    @Test
    void packmatesOnOnePointArePushedApartInDifferentDirections() {
        HarrierFlight a = at(1, 0, 5f, 5f);
        HarrierFlight b = at(2, 1, 5f, 5f);
        List<HarrierFlight> pack = List.of(a, b);
        float[] pa = new float[2], pb = new float[2];
        HarrierSteering.separation(a, pack, pa);
        HarrierSteering.separation(b, pack, pb);
        assertTrue(Math.hypot(pa[0], pa[1]) > 0.5);
        assertTrue(pa[0] * pb[0] + pa[1] * pb[1] < 0f, "pushed opposite ways");
    }

    @Test
    void aPackOfTwentyOnItsRingKeepsItsSpacing() {
        HarrierPack pack = new HarrierPack(1, 40f, 40f);
        List<HarrierFlight> members = new ArrayList<>();
        for (int i = 0; i < 20; i++) members.add(at(10 + i, i, 40f + (i % 5) * 0.2f, 40f + (i / 5) * 0.2f));
        pack.members.addAll(members);
        for (int t = 0; t < 300; t++) pack.advance(1f / 30f, (h, target) -> { });
        float closest = Float.MAX_VALUE;
        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                AirBody p = members.get(i).body, q = members.get(j).body;
                closest = Math.min(closest, (float) Math.hypot(p.x - q.x, p.y - q.y));
            }
        }
        assertTrue(closest > 0.5f, "closest pair " + closest);
    }
}
