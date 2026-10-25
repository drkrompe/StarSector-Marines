package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * What a body's per-quad effects are this frame (law 27): the light from the battle's event lights falling on it, and
 * its wear: charred and glowing with embers where the ground under it burns or has burnt. A pure function of the simulation's state and the lights the screen already keeps, so nothing here
 * can move a battle. Stamped onto a {@link DrawList} before a body is emitted and cleared after.
 */
final class BodyEffects {
    /** Char on a body lying in ground that has burnt out: soot that stays. */
    static final float BURNT_CHAR = 0.7f;
    /** Char and embers on a body in ground still burning. */
    static final float BURNING_CHAR = 0.55f;
    static final float BURNING_EMBERS = 0.9f;
    /** Embers on someone still standing in a burning cell: a moment's catch, not soot. */
    static final float LIVE_EMBERS = 0.6f;

    private final float[] light = new float[4];
    private GroundLightService lights;

    /** The event lights bodies are lit by; null lights nobody. */
    void useLights(GroundLightService lights) {
        this.lights = lights;
    }

    /** Stamps the light on a body at cell {@code x, y}. */
    void stampLight(DrawList out, float x, float y) {
        if (lights == null) {
            out.setLight(0f, 0f, 0f, 0f);
            return;
        }
        lights.lightAt(x, y, light);
        // Cell space and screen space run the same way up (BattleCamera), so the angle carries over as it is.
        out.setLight(light[0], light[1], light[2], light[3]);
    }

    /** Stamps a standing body's wear: embers while the cell under it burns. */
    void stampLiveWear(DrawList out, BattleSimulation sim, float x, float y) {
        FireField fire = sim.fireFieldIfPresent();
        boolean burning = fire != null && fire.isBurning((int) x, (int) y);
        out.setWear(0f, burning ? LIVE_EMBERS : 0f);
    }

    /** Stamps a body's wear as it lies: charred by the ground's fire, with embers while it still burns. */
    void stampDeadWear(DrawList out, BattleSimulation sim, float x, float y) {
        FireField fire = sim.fireFieldIfPresent();
        int cx = (int) x, cy = (int) y;
        float charred = 0f, embers = 0f;
        if (fire != null) {
            if (fire.isBurning(cx, cy)) {
                charred = BURNING_CHAR;
                embers = BURNING_EMBERS;
            } else if (fire.isBurnt(cx, cy)) {
                charred = BURNT_CHAR;
            }
        }
        out.setWear(charred, embers);
    }
}
