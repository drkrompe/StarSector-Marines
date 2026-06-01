package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirHandling;
import com.dillon.starsectormarines.battle.component.ComponentStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ThrusterFxSystem} — the temporal smoothing that fixes the
 * 0↔100 plume snap. Pure: feed a body + slots + store, assert the smoothed
 * demand ramps toward (rather than jumps to) the {@link ThrusterDemand} target.
 */
class ThrusterFxSystemTest {

    private static final AirHandling H = new AirHandling() {
        @Override public float maxSpeed()             { return 10f; }
        @Override public float accel()                { return 10f; }
        @Override public float brakingAccel()         { return 10f; }
        @Override public float maxTurnRateDegPerSec() { return 100f; }
        @Override public float lateralDriftDamping()  { return 3f; }
        @Override public float stationDamping()       { return 5f; }
    };

    /** Aft main, exhaust at 180 → thrust +Y forward. */
    private static EngineSlotData[] mainSlots() {
        return new EngineSlotData[]{ new EngineSlotData(0f, -1f, 180f, 4f, 2f, 0f, "MIDLINE") };
    }

    private static AirBody acceleratingForward() {
        AirBody b = new AirBody();
        b.vy = 5f;        // moving forward
        b.ay = 10f;       // accelerating forward → demand saturates
        return b;
    }

    @Test
    void rampsTowardTargetInsteadOfSnapping() {
        ComponentStore<ThrusterFx> store = new ComponentStore<>();
        AirBody body = acceleratingForward();
        EngineSlotData[] slots = mainSlots();

        // Target demand for this body is ~1; a single 1/60s tick should land
        // partway, not at the target — that's the whole point of the lerp.
        float[] after1 = ThrusterFxSystem.advance(1L, slots, body, H, store, 1f / 60f);
        assertTrue(after1[0] > 0f, "should rise off zero on the first tick");
        assertTrue(after1[0] < 0.9f, "should NOT snap to target in one tick: " + after1[0]);

        // Many ticks → converges on the target.
        for (int i = 0; i < 120; i++) {
            ThrusterFxSystem.advance(1L, slots, body, H, store, 1f / 60f);
        }
        assertEquals(1f, store.get(1L).smoothed[0], 0.02f, "should converge on full demand");
    }

    @Test
    void attackIsFasterThanDecay() {
        ComponentStore<ThrusterFx> rising = new ComponentStore<>();
        ComponentStore<ThrusterFx> falling = new ComponentStore<>();
        EngineSlotData[] slots = mainSlots();
        float dt = 1f / 60f;

        // Rising from 0 toward demand ~1.
        float riseDelta = ThrusterFxSystem.advance(1L, slots, acceleratingForward(), H, rising, dt)[0];

        // Falling: seed at 1, then advance with a parked body (demand 0).
        falling.add(1L, new ThrusterFx(1));
        falling.get(1L).smoothed[0] = 1f;
        AirBody parked = new AirBody();
        ThrusterFxSystem.advance(1L, slots, parked, H, falling, dt);
        float fallDelta = 1f - falling.get(1L).smoothed[0];

        assertTrue(riseDelta > fallDelta,
                "attack step (" + riseDelta + ") should exceed decay step (" + fallDelta + ")");
    }

    @Test
    void noEngineSlotsDropsTheComponent() {
        ComponentStore<ThrusterFx> store = new ComponentStore<>();
        store.add(7L, new ThrusterFx(2));
        float[] result = ThrusterFxSystem.advance(7L, new EngineSlotData[0], new AirBody(), H, store, 0.1f);
        assertNull(result, "no slots → null demand");
        assertTrue(!store.has(7L), "no slots → component removed");
    }
}
