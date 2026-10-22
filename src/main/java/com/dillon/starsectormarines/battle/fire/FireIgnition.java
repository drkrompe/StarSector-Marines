package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * How fires start (`fire-nouns.md`, Starting fires). A blast whose weapon is
 * incendiary always puts its heat into the ground it covers. Ordinary war lights
 * fires only where the battle's country is dry ({@link FireField#setTinder}): a
 * heavy blast there puts enough heat in to catch grass and dying creep, never a
 * living crown, and a wreck burns as an igniter for a minute. A battle that
 * never says its country is dry, and never fires an incendiary, never burns.
 */
public final class FireIgnition {

    /** Blast radius in cells at or above which an ordinary blast is heavy: shells, not grenades. */
    public static final float HEAVY_RADIUS = 2.5f;
    /** Heat a heavy ordinary blast puts in at its centre. */
    public static final int BLAST_HEAT = 340;
    /** Heat a burning wreck puts into its cell, and a quarter of it round it, every fire step, per cell of its radius. */
    public static final int WRECK_HEAT = 260;
    /** How long a wreck burns as an igniter. */
    public static final int WRECK_TICKS = 30 * 60;

    private FireIgnition() { }

    public static void blast(BattleSimulation sim, PendingDetonation det) {
        if (det.wasAbsorbed()) return;
        float radius = Math.max(1f, det.aoeRadius);
        if (det.heat() > 0) {
            sim.fireField().addHeat(det.endpointX, det.endpointY, radius, det.heat());
            return;
        }
        FireField fire = sim.fireFieldIfPresent();
        if (fire != null && fire.tinder() && det.aoeRadius >= HEAVY_RADIUS) {
            fire.addHeat(det.endpointX, det.endpointY, radius, BLAST_HEAT);
        }
    }

    public static void wreck(BattleSimulation sim, float x, float y, float radius) {
        FireField fire = sim.fireFieldIfPresent();
        if (fire == null || !fire.tinder()) return;
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        long id = ((long) cx << 32) ^ (cy & 0xFFFFFFFFL) ^ 0x57EC000000000000L;
        fire.putIgniter(id, cx, cy, Math.round(WRECK_HEAT * radius), sim.getSimTickIndex() + WRECK_TICKS);
    }
}
