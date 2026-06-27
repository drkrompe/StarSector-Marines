package com.dillon.starsectormarines.combathybrid.host;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.render2d.GlStateBracket;
import com.dillon.starsectormarines.render2d.SolidQuadBatch;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

/**
 * S0b probe — <b>verified facts 8 + 9 + 12: free camera, input override, and the
 * "own the top visible layer by starving the HUD" result.</b>
 *
 * <p>Drives a detached free camera over the spectator battle:
 * <ul>
 *   <li><b>Camera (fact 8):</b> {@code viewport.setExternalControl(true)} each frame,
 *       then own the visible rectangle directly via {@code viewport.set(llx, lly, w, h)}
 *       — under external control {@code setViewMult} is inert, so we drive w/h from a
 *       world-width zoom state + the screen aspect. WASD pans (polled via {@link Keyboard}),
 *       right-mouse drag pans, scroll zooms.</li>
 *   <li><b>Input override (fact 9):</b> WASD / RMB / scroll are consumed in
 *       {@code processInputPreCoreControls} so nothing leaks to a ship or command
 *       action, and player-ship control is disabled defensively each frame.</li>
 *   <li><b>Overlay (fact 12):</b> a screen-space marker drawn in
 *       {@code renderInUICoords}. Because the battle runs spectator (no player ship,
 *       zero CP) the vanilla HUD has nothing to draw, so this marker is effectively
 *       the topmost visible layer — the "starve, don't cover" workaround in action.</li>
 * </ul>
 *
 * <p>Completion control (suppress auto-end + F10 to end) is handled separately by
 * {@link S0CompletionPlugin}, added alongside this plugin.
 */
@DebugOnly
public class SpectatorCanvasPlugin extends BaseEveryFrameCombatPlugin {

    /** Camera pan per second as a fraction of the on-screen world width (zoom-aware feel). */
    private static final float PAN_FRACTION_PER_SEC = 0.8f;
    /** Per scroll-notch zoom factor. */
    private static final float ZOOM_STEP = 0.12f;
    /** World units across the screen at max zoom-in / max zoom-out. */
    private static final float MIN_VISIBLE_WIDTH = 600f;
    private static final float MAX_VISIBLE_WIDTH = 40000f;
    /** Fallback initial view if no map-derived width is supplied (no-arg constructor). */
    private static final float DEFAULT_VISIBLE_WIDTH = 2200f;

    /**
     * Seconds into the battle before re-attaching the stashed player fleet. Must be
     * &gt;0 so the deploy-skip decision (empty fleet at battle build) is already locked,
     * but well before the player can end the fight — so the post-battle resolution
     * reads a healthy fleet and doesn't register a "defeated" game over.
     */
    private static final float FLEET_RESTORE_DELAY = 0.5f;

    private CombatEngineAPI engine;
    private final Vector2f center = new Vector2f(0f, 0f);
    /**
     * World units spanned by the screen width — our single zoom state. Under
     * {@code setExternalControl(true)}, {@code setViewMult} does NOT recompute the
     * visible rectangle, so we own w/h explicitly via {@code viewport.set(...)} and
     * treat this width (plus the screen aspect) as the source of truth.
     */
    private float visibleWidth;
    private float combatTime;
    private boolean fleetRestored;

    /**
     * @param initialVisibleWidth world units across the screen at battle start. Derive it from
     *     the ground map size ({@code gridW × worldUnitsPerCell × margin}) so the whole plate
     *     frames at <em>any</em> cell density — the free-cam zoom takes over from there. Clamped
     *     to the zoom bounds.
     */
    public SpectatorCanvasPlugin(float initialVisibleWidth) {
        this.visibleWidth = clamp(initialVisibleWidth, MIN_VISIBLE_WIDTH, MAX_VISIBLE_WIDTH);
    }

    /** Fallback framing for callers without a map-size handle. */
    public SpectatorCanvasPlugin() {
        this(DEFAULT_VISIBLE_WIDTH);
    }

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        // Tells PlayerFleetStash the probe battle is live, so its restore script
        // re-attaches the player fleet once we're back on the campaign map.
        PlayerFleetStash.markCombatEntered();
        ViewportAPI vp = engine.getViewport();
        if (vp != null) {
            vp.setExternalControl(true);
        }
    }

    @Override
    public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {
        float worldPerPixel = visibleWidth / Math.max(1, Display.getWidth());
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;

            if (e.isMouseScrollEvent()) {
                // Scroll up (+) = zoom in = fewer world units across the screen.
                float dir = Math.signum(e.getEventValue());
                visibleWidth = clamp(visibleWidth * (1f - dir * ZOOM_STEP),
                        MIN_VISIBLE_WIDTH, MAX_VISIBLE_WIDTH);
                e.consume();
            } else if (e.isRMBDownEvent() || (e.isMouseEvent() && isRmbHeld())) {
                // RMB-drag pan: grab the world and pull it with the cursor.
                center.x -= e.getDX() * worldPerPixel;
                center.y -= e.getDY() * worldPerPixel;
                e.consume();
            } else if (isPanKey(e)) {
                e.consume(); // movement itself is polled in advance(); just stop the leak
            }
        }
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) return;
        ViewportAPI vp = engine.getViewport();
        if (vp == null) return;

        // Re-attach the stashed player fleet a beat in — after the deploy-skip is
        // locked, before the player can end the fight — so the post-battle resolution
        // sees a healthy fleet instead of declaring a "defeated" game over.
        combatTime += amount;
        if (!fleetRestored && combatTime > FLEET_RESTORE_DELAY) {
            PlayerFleetStash.restore();
            fleetRestored = true;
        }

        // Spectator: never let a player ship take WASD; we own the camera.
        if (engine.getCombatUI() != null) {
            engine.getCombatUI().setDisablePlayerShipControlOneFrame(true);
        }

        // Pan speed scales with zoom so it feels constant on screen at any zoom level.
        float pan = visibleWidth * PAN_FRACTION_PER_SEC * amount;
        if (Keyboard.isKeyDown(Keyboard.KEY_W)) center.y += pan;
        if (Keyboard.isKeyDown(Keyboard.KEY_S)) center.y -= pan;
        if (Keyboard.isKeyDown(Keyboard.KEY_A)) center.x -= pan;
        if (Keyboard.isKeyDown(Keyboard.KEY_D)) center.x += pan;

        // Own the viewport rectangle explicitly: setViewMult is inert under external
        // control, so derive visible height from the screen aspect and set() the box.
        float aspect = (float) Display.getHeight() / Math.max(1, Display.getWidth());
        float visibleHeight = visibleWidth * aspect;
        vp.setExternalControl(true);
        vp.set(center.x - visibleWidth * 0.5f, center.y - visibleHeight * 0.5f,
                visibleWidth, visibleHeight);
    }

    @Override
    public void renderInUICoords(ViewportAPI viewport) {
        // Top-left marker proving our overlay is the top visible layer.
        float w = Display.getWidth();
        float h = Display.getHeight();
        float x0 = 12f, y1 = h - 12f, x1 = x0 + 240f, y0 = y1 - 34f;

        SolidQuadBatch batch = new SolidQuadBatch(2);
        batch.appendRect(x0 - 2f, y0 - 2f, x1 + 2f, y1 + 2f, 0.10f, 0.55f, 0.75f, 0.9f); // border
        batch.appendRect(x0, y0, x1, y1, 0.03f, 0.10f, 0.14f, 0.85f);                    // fill
        try (GlStateBracket gl = GlStateBracket.textured2D()) {
            batch.flush();
        }
    }

    private static boolean isPanKey(InputEventAPI e) {
        if (!e.isKeyboardEvent()) return false;
        int v = e.getEventValue();
        return v == Keyboard.KEY_W || v == Keyboard.KEY_A
                || v == Keyboard.KEY_S || v == Keyboard.KEY_D;
    }

    private static boolean isRmbHeld() {
        return org.lwjgl.input.Mouse.isButtonDown(1);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
