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
 * screen over an arc — so a consumer branches on {@link #arcDegrees} being
 * positive rather than on which armour pattern produced it. A future system
 * that raises a screen without being a breach assist gets the treatment for
 * free; one that raises none simply reports a zero arc.
 *
 * <p>Presence follows the wearer's {@code INTEGRAL_SYSTEM} rather than the
 * activation, so starting and ending a treatment is four float writes instead
 * of an archetype move — the {@code MITIGATION} shape. "Nothing is running" is
 * {@link #intensity} {@code 0}, which {@link #isRunning} asks for you.
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
     * the clock the treatment draws — the story's requirement is that the
     * window be seen closing, not that a number be printed.
     */
    public float intensity(long id) {
        return has(id) ? clamp01(get(id, BattleComponents.SYSTEM_FX_INTENSITY)) : 0f;
    }

    /** True while a treatment should be drawn at all. */
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

    /** The fraction that screen refuses inside its arc, so a heavier screen may read heavier. */
    public float arcFraction(long id) {
        return isRunning(id) ? clamp01(get(id, BattleComponents.SYSTEM_FX_ARC_FRACTION)) : 0f;
    }

    /** Authors this tick's treatment. Called only by {@link SystemFxSystem}. */
    void write(long id, float intensity, float arcFacingDegrees,
               float arcDegrees, float arcFraction) {
        if (!has(id)) return;
        set(id, BattleComponents.SYSTEM_FX_INTENSITY, clamp01(intensity));
        set(id, BattleComponents.SYSTEM_FX_ARC_FACING_DEGREES, arcFacingDegrees);
        set(id, BattleComponents.SYSTEM_FX_ARC_DEGREES, Math.max(0f, arcDegrees));
        set(id, BattleComponents.SYSTEM_FX_ARC_FRACTION, clamp01(arcFraction));
    }

    /** Ends the treatment now, leaving no residue. Safe on an actor that never had one. */
    void clear(long id) {
        if (!has(id)) return;
        set(id, BattleComponents.SYSTEM_FX_INTENSITY, 0f);
        set(id, BattleComponents.SYSTEM_FX_ARC_FACING_DEGREES, 0f);
        set(id, BattleComponents.SYSTEM_FX_ARC_DEGREES, 0f);
        set(id, BattleComponents.SYSTEM_FX_ARC_FRACTION, 0f);
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
