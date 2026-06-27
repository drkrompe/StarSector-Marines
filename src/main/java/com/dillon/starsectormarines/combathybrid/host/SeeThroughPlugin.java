package com.dillon.starsectormarines.combathybrid.host;

import com.dillon.starsectormarines.DebugOnly;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Contextual see-through probe for the (future) ground battle control mode — see
 * {@code roadmap/vanilla-combat-bridge/stories/ground-control-mode.md}. Press <b>X</b>
 * ("x-ray") during a SIM_COUPLED bridge battle to toggle a <b>cursor reveal disk</b>:
 * player ships near the cursor lerp their alpha down so the ground scene drawn on the
 * below-ships layer is legible through them, and lerp back up when the cursor moves away.
 *
 * <p>This is the same lever the sim proxies use to be invisible ({@code setExtraAlphaMult},
 * see {@code SimProxyMirror}), applied <em>partially</em> to the real fleet:
 * <ul>
 *   <li><b>Channel:</b> {@link ShipAPI#setExtraAlphaMult2} (+ {@code setApplyExtraAlphaToEngines}).
 *       Mult2 is the safe channel — the engine drives mult1 for phase/arrival fades, and the
 *       {@code PhaseAnchor} hullmod uses mult2 precisely to avoid stomping mult1.</li>
 *   <li><b>Reveal disk:</b> a constant on-screen radius (derived from {@code visibleWidth /
 *       screenWidth} so the lens feels the same at any zoom); each ship's {@code collisionRadius}
 *       is subtracted from the cursor distance so merely touching a large hull already fades it.</li>
 *   <li><b>Which ships:</b> {@code getOwner()==0} (player side) only — which also excludes the
 *       enemy-side sim proxies for free (fading them <em>up</em> would reveal them).</li>
 * </ul>
 *
 * <p>The hull alpha fades; projectiles/beams/shields/FX do not (separate render entities), but at
 * per-cursor scale that residue is negligible — the occluding element is the hull. {@code X} is
 * consumed pre-core-controls and {@code L} is taken by {@link CarrierDescentPlugin}, so it's safe
 * in the spectator canvas.
 *
 * <p>Session-policy probe, installed by {@link CombatBridgeSession#enterEngine}. Config-free: it
 * needs only the engine + cursor. Reachable only via the dev probe today.
 */
@DebugOnly
public final class SeeThroughPlugin extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(SeeThroughPlugin.class);

    /** "X" for x-ray — toggles see-through mode (stands in for the real ground-control-mode entry). */
    private static final int TOGGLE_KEY = Keyboard.KEY_X;

    /** Reveal-disk radius in screen pixels (constant on-screen lens size, zoom-independent). */
    private static final float REVEAL_SCREEN_RADIUS_PX = 180f;
    /** Alpha a fully-revealed (cursor-centred) hull fades to. */
    private static final float MIN_ALPHA = 0.15f;
    /** Alpha easing rate per second toward the target (higher = snappier). */
    private static final float LERP_RATE = 10f;
    /** Within this of 1.0, a returning ship is snapped to full and released. */
    private static final float SETTLE_EPS = 0.01f;

    private CombatEngineAPI engine;
    private boolean seeThrough;
    /** Player ships we're currently driving, and their eased alpha. Released once back at 1.0. */
    private final Map<ShipAPI, Float> alpha = new HashMap<>();

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
    }

    @Override
    public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            if (e.isKeyDownEvent() && e.getEventValue() == TOGGLE_KEY) {
                e.consume();
                seeThrough = !seeThrough;
                LOG.info("ground-bridge(see-through): " + (seeThrough ? "ON" : "OFF") + ".");
                break;
            }
        }
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) return;
        ViewportAPI vp = engine.getViewport();
        if (vp == null) return;

        // Drop any ship that died or despawned while we were tracking it.
        alpha.keySet().removeIf(s -> !s.isAlive());

        if (seeThrough) {
            driveRevealDisk(vp, amount);
        } else {
            releaseAll(amount);
        }
    }

    /** Mode ON: fade every player ship by its distance to the cursor-anchored reveal disk. */
    private void driveRevealDisk(ViewportAPI vp, float amount) {
        // Cursor → world from the viewport's explicit rectangle (getLLX/getVisibleWidth), NOT
        // convertScreenXToWorldX: under the spectator's setExternalControl camera the convert
        // helpers read the engine's now-inert viewMult/scale, so they drift with zoom. The
        // rectangle getters reflect our own vp.set(...), so this is exact at any zoom. Mouse
        // (0,0) is the bottom-left = the lower-left corner the getters report.
        float screenW = Math.max(1, Display.getWidth());
        float screenH = Math.max(1, Display.getHeight());
        float worldPerPixel = vp.getVisibleWidth() / screenW;
        float cx = vp.getLLX() + Mouse.getX() * worldPerPixel;
        float cy = vp.getLLY() + Mouse.getY() * (vp.getVisibleHeight() / screenH);
        float revealWorldRadius = Math.max(1f, REVEAL_SCREEN_RADIUS_PX * worldPerPixel);

        for (ShipAPI ship : engine.getShips()) {
            if (ship.getOwner() != 0 || !ship.isAlive()) continue;   // player side only; skips proxies
            Vector2f loc = ship.getLocation();
            float dist = (float) Math.hypot(loc.x - cx, loc.y - cy) - ship.getCollisionRadius();
            float t = clamp01(Math.max(0f, dist) / revealWorldRadius);
            float smooth = t * t * (3f - 2f * t);                    // soft falloff, no popping
            float target = MIN_ALPHA + (1f - MIN_ALPHA) * smooth;
            ease(ship, target, amount);
        }
    }

    /** Mode OFF: lerp the ships we touched back to full, releasing each as it settles. */
    private void releaseAll(float amount) {
        for (Iterator<ShipAPI> it = alpha.keySet().iterator(); it.hasNext(); ) {
            ShipAPI ship = it.next();
            float cur = alpha.get(ship) + (1f - alpha.get(ship)) * clamp01(LERP_RATE * amount);
            if (cur >= 1f - SETTLE_EPS) {
                ship.setExtraAlphaMult2(1f);
                it.remove();
            } else {
                ship.setExtraAlphaMult2(cur);
                ship.setApplyExtraAlphaToEngines(true);
                alpha.put(ship, cur);
            }
        }
    }

    /** Ease a ship's tracked alpha toward {@code target} and write it; release when fully clear. */
    private void ease(ShipAPI ship, float target, float amount) {
        float cur = alpha.getOrDefault(ship, 1f);
        cur += (target - cur) * clamp01(LERP_RATE * amount);
        if (target >= 1f && cur >= 1f - SETTLE_EPS) {
            ship.setExtraAlphaMult2(1f);
            alpha.remove(ship);
            return;
        }
        ship.setExtraAlphaMult2(cur);
        ship.setApplyExtraAlphaToEngines(true);
        alpha.put(ship, cur);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
