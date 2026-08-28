package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Data owner for the {@code SYSTEM_FX} component — the appearance capability
 * "this actor's integral system is running" ({@code progression-nouns.md}).
 *
 * <p><b>Presentation, downstream, one direction only.</b> Everything here is
 * authored by {@link SystemFxSystem} out of live simulation state and read by
 * the render and audio tiers. No simulation code reads a column of it, and an
 * activation, a movement step, or a damage resolution that came to depend on
 * one would be the defect this class's whole placement exists to prevent
 * ({@code battle.air.AirAppearance} is the precedent, and {@code SPRITE} is
 * the one this most closely mirrors).
 *
 * <p><b>Keyed on the capability, not the carrier.</b> A treatment is described
 * entirely by what is running — a window with a clock on it, and optionally a
 * screen over an arc with a pool behind it — so a consumer branches on
 * {@link #arcDegrees} being positive rather than on which armour pattern
 * produced it. A future system that raises a screen without being a breach
 * assist gets the treatment for free; one that raises none simply reports a
 * zero arc.
 *
 * <p>Presence follows the wearer's {@code INTEGRAL_SYSTEM} rather than the
 * activation, so starting and ending a treatment is a handful of float writes
 * instead of an archetype move — the {@code MITIGATION} shape. "Nothing is
 * running" is {@link #intensity} {@code 0}, which {@link #isRunning} asks for
 * you; the one thing that outlives it is {@link #breakFlash}, because a screen
 * that shattered has to be seen shattering after it is gone.
 */
public final class SystemFxService {

    private final EntityWorld entityWorld;
    private final BattleComponents components;

    public SystemFxService(EntityWorld entityWorld, BattleComponents components) {
        this.entityWorld = entityWorld;
        this.components = components;
    }

    /** Whether this actor can ever show a running system. Most cannot. */
    public boolean has(long id) {
        return entityWorld.has(id, components.SYSTEM_FX);
    }

    /**
     * How much of the running window is left, in {@code [0, 1]}: {@code 1} on
     * the tick the system is spent and {@code 0} once it has expired. This is
     * the clock the treatment draws — the requirement is that the window be
     * seen closing, not that a number be printed.
     */
    public float intensity(long id) {
        return has(id) ? clamp01(get(id, BattleComponents.SYSTEM_FX_INTENSITY)) : 0f;
    }

    /** True while a running system's treatment should be drawn at all. */
    public boolean isRunning(long id) {
        return intensity(id) > 0f;
    }

    /** The direction the drawn screen faces; meaningless when {@link #arcDegrees} is {@code 0}. */
    public float arcFacingDegrees(long id) {
        return has(id) ? get(id, BattleComponents.SYSTEM_FX_ARC_FACING_DEGREES) : 0f;
    }

    /**
     * Total width of the drawn screen's arc, or {@code 0} when the running
     * system raises no screen. This is the authored arc the damage path is
     * resolving against, unmodified: a treatment drawn wider than the arc it
     * protects would teach the player the wrong rule.
     */
    public float arcDegrees(long id) {
        return isRunning(id) ? Math.max(0f, get(id, BattleComponents.SYSTEM_FX_ARC_DEGREES)) : 0f;
    }

    /**
     * How much of the screen's soak pool is left, in {@code [0, 1]}. A quantity
     * is easier to show honestly than a clock was: the drawn screen simply gets
     * fainter as the pool is spent, so a screen about to break looks like one.
     */
    public float soakFraction(long id) {
        return isRunning(id) ? clamp01(get(id, BattleComponents.SYSTEM_FX_SOAK_FRACTION)) : 0f;
    }

    /**
     * The "it just shattered" mark, in {@code [0, 1]}, falling to {@code 0}
     * shortly after the pool emptied. Deliberately readable when nothing is
     * running: the screen is gone by then, and its going is the event.
     */
    public float breakFlash(long id) {
        return has(id) ? clamp01(get(id, BattleComponents.SYSTEM_FX_BREAK_FLASH)) : 0f;
    }

    /**
     * A wrapping {@code [0, 1)} phase for the treatment's shimmer, authored from
     * simulation time rather than sampled from a wall clock — the same battle
     * state has to draw the same frame, or deterministic visual evidence stops
     * being evidence.
     */
    public float shimmerPhase(long id) {
        return has(id) ? get(id, BattleComponents.SYSTEM_FX_SHIMMER_PHASE) : 0f;
    }

    /** Authors this tick's treatment. Called only by {@link SystemFxSystem}. */
    void write(long id, float intensity, float arcFacingDegrees,
               float arcDegrees, float soakFraction, float shimmerPhase) {
        if (!has(id)) return;
        set(id, BattleComponents.SYSTEM_FX_INTENSITY, clamp01(intensity));
        set(id, BattleComponents.SYSTEM_FX_ARC_FACING_DEGREES, arcFacingDegrees);
        set(id, BattleComponents.SYSTEM_FX_ARC_DEGREES, Math.max(0f, arcDegrees));
        set(id, BattleComponents.SYSTEM_FX_SOAK_FRACTION, clamp01(soakFraction));
        set(id, BattleComponents.SYSTEM_FX_SHIMMER_PHASE, shimmerPhase);
    }

    /** Authors the shatter mark, which is the one column that outlives the running system. */
    void writeBreakFlash(long id, float breakFlash) {
        if (!has(id)) return;
        set(id, BattleComponents.SYSTEM_FX_BREAK_FLASH, clamp01(breakFlash));
    }

    /**
     * Ends the running system's treatment, leaving no residue. The shatter mark
     * is deliberately not cleared here — it is authored separately every tick,
     * and clearing it would erase the one frame it exists for.
     */
    void clear(long id) {
        if (!has(id)) return;
        set(id, BattleComponents.SYSTEM_FX_INTENSITY, 0f);
        set(id, BattleComponents.SYSTEM_FX_ARC_FACING_DEGREES, 0f);
        set(id, BattleComponents.SYSTEM_FX_ARC_DEGREES, 0f);
        set(id, BattleComponents.SYSTEM_FX_SOAK_FRACTION, 0f);
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value) || value <= 0f) return 0f;
        return Math.min(1f, value);
    }

    private float get(long id, int field) {
        return entityWorld.getFloat(id, components.SYSTEM_FX, field);
    }

    private void set(long id, int field, float value) {
        entityWorld.setFloat(id, components.SYSTEM_FX, field, value);
    }
}
