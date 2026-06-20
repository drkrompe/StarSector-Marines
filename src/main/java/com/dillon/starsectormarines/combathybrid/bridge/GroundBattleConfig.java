package com.dillon.starsectormarines.combathybrid.bridge;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.ops.battleview.RenderLayer;
import org.lwjgl.util.vector.Vector2f;

import java.util.EnumSet;
import java.util.List;

/**
 * Host-agnostic description of a ground battle to mirror into a vanilla combat instance.
 * Replaces the spike's static {@code S0BattleProbe.mode()} global + per-class constants: the
 * sim is built once (via {@code BattleSetup.buildMap}) and this record carries everything the
 * bridge adapters need to render and couple it. One config → one {@code CombatBridgeSession}.
 *
 * <p>The {@link #sceneLayers} set is the knob the render-layers thread (S3f–S3j) grows: each
 * story adds a {@link RenderLayer} here rather than editing a hardcoded constant in
 * {@link GroundSceneBackdrop}.
 *
 * @param sim               the externally-owned ground sim (source of truth; never built here)
 * @param gridW             sim grid width in cells (cell→world projection)
 * @param gridH             sim grid height in cells
 * @param worldUnitsPerCell combat world units per sim cell (the scale knob)
 * @param sceneLayers       which render passes {@link GroundSceneBackdrop} draws under the ships
 * @param targetable        sim entities mirrored as invisible vanilla proxies (the targetable tier)
 * @param proxyVariant      hull behind each invisible proxy (sprite hidden)
 * @param damageScale       vanilla-damage → sim-damage divisor (placeholder cross-scale convention)
 */
public record GroundBattleConfig(
        BattleSimulation sim,
        int gridW,
        int gridH,
        float worldUnitsPerCell,
        EnumSet<RenderLayer> sceneLayers,
        List<Entity> targetable,
        String proxyVariant,
        float damageScale) {

    /**
     * The render passes the bridge draws today: terrain + structure + ground units (S3f) +
     * objective / compound capture-state markers (S3g) + parked vehicles & convoys (S3h). Grows as
     * the render-layers thread brings the remaining projection-agnostic passes over.
     */
    public static final EnumSet<RenderLayer> DEFAULT_SCENE_LAYERS =
            EnumSet.of(RenderLayer.GROUND, RenderLayer.DOODADS, RenderLayer.ROOFS, RenderLayer.UNITS,
                    RenderLayer.OBJECTIVES, RenderLayer.COMPOUND,
                    RenderLayer.VEHICLES, RenderLayer.CONVOY);

    /**
     * Vanilla-damage → sim-damage divisor: maps ship-gun damage (hundreds/sec, bursty fighter
     * passes) onto infantry-scale turret HP (50–85) so a turret attrits over several passes. A
     * placeholder for the real cross-scale convention (architecture.md, S3c) — the knob, not the
     * answer.
     */
    public static final float DEFAULT_DAMAGE_SCALE = 0.02f;

    /** Defensive copy of the mutable collections so a config is an immutable snapshot. */
    public GroundBattleConfig {
        sceneLayers = EnumSet.copyOf(sceneLayers);
        targetable = List.copyOf(targetable);
    }

    /**
     * The shared cell→combat-world projection: the grid's center cell maps to the combat world
     * origin, so {@code (cell − grid/2)·worldUnitsPerCell}. Both the proxy mirror (per-frame, hot)
     * and the carrier-engagement waypoint use it, so it lives here next to the grid/scale knobs.
     * Writes into {@code out} (no allocation) for the hot caller.
     */
    public void cellToWorld(int cellX, int cellY, Vector2f out) {
        out.set((cellX - gridW / 2f) * worldUnitsPerCell,
                (cellY - gridH / 2f) * worldUnitsPerCell);
    }
}
