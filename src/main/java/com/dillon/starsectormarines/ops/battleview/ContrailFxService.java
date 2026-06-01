package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.render2d.ContrailTrail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns the in-flight contrail trail lifecycle — the <em>state</em> half of the
 * SHOTS contrail effect, split out of the renderer so the render pass is a pure
 * emit (the old {@code renderContrails} {@code Custom} aged trails as a side
 * effect of being called every frame, which meant it had to be emitted
 * unconditionally even with no shots; that smell is gone). This is the
 * {@code [[battle_services_systems]]} rule applied to the render tier: a service
 * owns the mutable state and {@link #tick}s it; {@link #collect} is stateless.
 *
 * <p>Sibling to {@link com.dillon.starsectormarines.battle.combat.fx.ImpactFx} —
 * same shape (a {@code tick}/advance call from {@code BattleScreen.advance}, a
 * collect/render call from the world pass), different state.
 *
 * <p><strong>Carrier-agnostic.</strong> Which shots contrail, what style, and how
 * they curve all come from {@link ShotFx}: {@code fx.contrail()} both selects the
 * shot (non-null) <em>and</em> is the {@code ContrailStyle}, and
 * {@code boostRamp()}/{@code arcHeight()} drive the sample position — the same
 * effect composition the body sweeps key on. No {@code turretKind} cascade; a
 * future arc-and-contrail grenade launcher trails with no edit here.
 */
public final class ContrailFxService {

    /** Active trails keyed by their owning shot (identity). */
    private final Map<ShotEvent, ContrailTrail> live = new IdentityHashMap<>();

    /** Trails whose owning shot has expired but whose samples haven't all aged out. */
    private final List<ContrailTrail> decaying = new ArrayList<>();

    /** Reused identity set for the retire-gone scan — avoids a per-tick alloc. */
    private final Set<ShotEvent> currentShots = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Advance the trail lifecycle: retire trails whose shot vanished, push a fresh
     * sample for each live contrail shot, age every trail, and drop fully-decayed
     * ones. Call once per frame from {@code BattleScreen.advance} <em>after</em>
     * the sim advances (samples read the shots' post-tick positions).
     *
     * <p>{@code dt} is real (wall-clock) time, not sim-scaled — trails keep
     * dissipating while the sim is paused, matching the old render-frame aging.
     */
    public void tick(List<ShotEvent> shots, float dt) {
        // Retire trails whose owning shot is gone this frame → let them decay in place.
        if (!live.isEmpty()) {
            currentShots.clear();
            currentShots.addAll(shots);
            Iterator<Map.Entry<ShotEvent, ContrailTrail>> it = live.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<ShotEvent, ContrailTrail> e = it.next();
                if (!currentShots.contains(e.getKey())) {
                    decaying.add(e.getValue());
                    it.remove();
                }
            }
            currentShots.clear();
        }

        // Push the leading-edge sample for every contrail-bearing shot.
        for (ShotEvent s : shots) {
            ShotFx fx = ShotFx.of(s);
            if (fx.contrail() == null) continue;
            float linearProgress = 1f - clamp01(s.lifetime / Math.max(0.001f, s.lifetimeMax));
            float progress = fx.boostRamp() ? Projectile.applyBoostCurve(linearProgress) : linearProgress;
            float px = s.fromX + (s.toX - s.fromX) * progress;
            float py = s.fromY + (s.toY - s.fromY) * progress;
            float arcH = fx.arcHeight();
            if (arcH > 0f) py += arcH * 4f * progress * (1f - progress);
            ContrailTrail trail = live.get(s);
            if (trail == null) {
                trail = new ContrailTrail(fx.contrail(), 32);
                live.put(s, trail);
            }
            trail.pushSample(px, py);
        }

        if (dt > 0f) {
            for (ContrailTrail t : live.values()) t.advance(dt);
            for (ContrailTrail t : decaying)      t.advance(dt);
        }
        decaying.removeIf(ContrailTrail::isEmpty);
    }

    /**
     * Emit one {@code RIBBON} command per trail — live first, then decaying, so the
     * paint order matches the old single-batch append order. The drain coalesces
     * the consecutive ribbons into one {@link com.dillon.starsectormarines.render2d.RibbonBatch}
     * flush; trails with fewer than two samples append nothing. No GL here.
     */
    public void collect(DrawList out, float alphaMult) {
        for (ContrailTrail t : live.values()) out.addRibbon(RenderLayer.SHOTS, t, alphaMult);
        for (ContrailTrail t : decaying)      out.addRibbon(RenderLayer.SHOTS, t, alphaMult);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
