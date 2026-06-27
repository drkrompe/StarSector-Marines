package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirHandling;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ThrusterFxSystem} — the temporal smoothing that fixes the
 * 0↔100 plume snap, now over the world's {@code THRUSTER_FX} OBJECT column
 * (re-keyed off the retired {@code ComponentStore}). Pure: feed a body + slots +
 * a real {@link EntityWorld}/{@link BattleComponents} harness and assert the
 * smoothed demand ramps toward (rather than jumps to) the {@link ThrusterDemand}
 * target, lazily attaching / dropping the component.
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

    /** The smoothed FX on {@code id}, or null if it carries no THRUSTER_FX yet (has-gated). */
    private static ThrusterFx fxOf(EntityWorld world, BattleComponents c, long id) {
        return world.has(id, c.THRUSTER_FX)
                ? (ThrusterFx) world.getObject(id, c.THRUSTER_FX, BattleComponents.THRUSTER_FX_STATE)
                : null;
    }

    @Test
    void rampsTowardTargetInsteadOfSnapping() {
        EntityWorld world = new EntityWorld();
        BattleComponents c = new BattleComponents(world);
        long id = world.createEntity();   // bare entity; advance lazily attaches THRUSTER_FX
        AirBody body = acceleratingForward();
        EngineSlotData[] slots = mainSlots();

        // Target demand for this body is ~1; a single 1/60s tick should land
        // partway, not at the target — that's the whole point of the lerp.
        float[] after1 = ThrusterFxSystem.advance(id, slots, body, H, world, c, 1f / 60f);
        assertTrue(after1[0] > 0f, "should rise off zero on the first tick");
        assertTrue(after1[0] < 0.9f, "should NOT snap to target in one tick: " + after1[0]);

        // Many ticks → converges on the target.
        for (int i = 0; i < 120; i++) {
            ThrusterFxSystem.advance(id, slots, body, H, world, c, 1f / 60f);
        }
        assertEquals(1f, fxOf(world, c, id).smoothed[0], 0.02f, "should converge on full demand");
    }

    @Test
    void attackIsFasterThanDecay() {
        EntityWorld world = new EntityWorld();
        BattleComponents c = new BattleComponents(world);
        EngineSlotData[] slots = mainSlots();
        float dt = 1f / 60f;

        // Rising from 0 toward demand ~1.
        long rising = world.createEntity();
        float riseDelta = ThrusterFxSystem.advance(rising, slots, acceleratingForward(), H, world, c, dt)[0];

        // Falling: seed at 1, then advance with a parked body (demand 0).
        long falling = world.createEntity();
        ThrusterFx seed = new ThrusterFx(1);
        seed.smoothed[0] = 1f;
        world.addComponent(falling, c.THRUSTER_FX);
        world.setObject(falling, c.THRUSTER_FX, BattleComponents.THRUSTER_FX_STATE, seed);
        AirBody parked = new AirBody();
        ThrusterFxSystem.advance(falling, slots, parked, H, world, c, dt);
        float fallDelta = 1f - fxOf(world, c, falling).smoothed[0];

        assertTrue(riseDelta > fallDelta,
                "attack step (" + riseDelta + ") should exceed decay step (" + fallDelta + ")");
    }

    @Test
    void noEngineSlotsDropsTheComponent() {
        EntityWorld world = new EntityWorld();
        BattleComponents c = new BattleComponents(world);
        long id = world.createEntity();
        world.addComponent(id, c.THRUSTER_FX);
        world.setObject(id, c.THRUSTER_FX, BattleComponents.THRUSTER_FX_STATE, new ThrusterFx(2));

        float[] result = ThrusterFxSystem.advance(id, new EngineSlotData[0], new AirBody(), H, world, c, 0.1f);
        assertNull(result, "no slots → null demand");
        assertFalse(world.has(id, c.THRUSTER_FX), "no slots → component removed");
    }
}
