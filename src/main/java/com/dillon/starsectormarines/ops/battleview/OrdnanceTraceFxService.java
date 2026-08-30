package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.fx.OrdnanceRelease;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Owns the in-flight lifecycle of delivered rounds — the <em>state</em> half of
 * the gun run, split out of the renderer so the render pass is a pure emit.
 * Sibling of {@link ContrailFxService}: an {@link #advance} call from
 * {@code BattleScreen.advance}, a stateless {@link #collect} from the world
 * pass, and no simulation state anywhere.
 *
 * <p><b>Arrival is an event, not an assumption.</b> The simulation resolves a
 * delivery the instant it is released, but a bomb visibly falls for a third of
 * a second and a shell for a tenth, so the crater has to wait for the picture
 * to catch up. Rounds that reach the ground during an {@link #advance} land in
 * {@link #arrivalsThisFrame()}, which is what the host drains for impact
 * effects and the impact cue — the same shape as the simulation's own
 * expired-shot drain, and the reason an explosion never precedes the round
 * that made it.
 *
 * <p>The live list is capped. A rotary cannon at fourteen rounds a second with
 * three aircraft over the map is still nowhere near it; the cap is there so a
 * host that stops advancing cannot accumulate without bound.
 */
public final class OrdnanceTraceFxService {

    /** Ceiling on simultaneously drawn rounds. Oldest are dropped first. */
    static final int MAX_LIVE = 256;

    /** Line width in px for a falling body drawn without its texture. */
    private static final float FALLING_STREAK_WIDTH_PX = 2.4f;

    static final class Live {
        final OrdnanceRelease release;
        final OrdnanceFx fx;
        /**
         * How long this round is actually in the air. The simulation's own
         * number when it has one, the composition's otherwise — a bomb's fall
         * is timed by the physics that drops it, not by a constant in a
         * picture, or the body lands a second before it explodes.
         */
        final float flightSeconds;
        float age;
        boolean arrived;

        Live(OrdnanceRelease release, OrdnanceFx fx) {
            this.release = release;
            this.fx = fx;
            this.flightSeconds = release.flightTimeSec() > 0f
                    ? release.flightTimeSec() : fx.trace().flightSeconds();
        }

        /** Seconds this round occupies the screen, fade included. */
        float lifetimeSeconds() {
            return flightSeconds + fx.trace().fadeSeconds();
        }
    }

    private final BattleSprites sprites;
    private final List<Live> live = new ArrayList<>();
    private final List<OrdnanceRelease> arrivals = new ArrayList<>();
    private final List<OrdnanceRelease> arrivalsView = Collections.unmodifiableList(arrivals);

    public OrdnanceTraceFxService(BattleSprites sprites) {
        this.sprites = sprites;
    }

    /** Starts drawing one released round. */
    public void spawn(OrdnanceRelease release) {
        if (release == null) return;
        if (live.size() >= MAX_LIVE) live.remove(0);
        live.add(new Live(release, OrdnanceFx.of(release.delivery())));
    }

    /**
     * Ages every drawn round, publishes the ones that reached the ground into
     * {@link #arrivalsThisFrame()}, and drops the ones that have finished
     * fading. Call once per frame, after the round's own {@link #spawn}, so a
     * beam — which arrives the instant it is released — still publishes its
     * arrival on the frame it was fired.
     */
    public void advance(float dt) {
        arrivals.clear();
        float step = Math.max(0f, dt);
        for (int i = live.size() - 1; i >= 0; i--) {
            Live t = live.get(i);
            t.age += step;
            if (!t.arrived && t.age >= t.flightSeconds) {
                t.arrived = true;
                arrivals.add(t.release);
            }
            if (t.age >= t.lifetimeSeconds()) live.remove(i);
        }
    }

    /** Rounds that reached the ground during the last {@link #advance}. */
    public List<OrdnanceRelease> arrivalsThisFrame() {
        return arrivalsView;
    }

    /** Rounds currently drawn. */
    public int liveCount() {
        return live.size();
    }

    /** Drops everything drawn — for a host tearing a battle down. */
    public void clear() {
        live.clear();
        arrivals.clear();
    }

    /**
     * Emits one command per drawn round into {@link RenderLayer#SHOTS}. No GL
     * and no state change: called every frame, recomputed every frame.
     */
    public void collect(BattleCamera camera, DrawList out, float alphaMult) {
        if (live.isEmpty()) return;
        float cellPx = camera.cellPxSize();
        for (int i = 0, n = live.size(); i < n; i++) {
            Live t = live.get(i);
            OrdnanceFx.Trace trace = t.fx.trace();
            if (trace instanceof OrdnanceFx.Streak streak) {
                collectStreak(t, streak, camera, out, alphaMult);
            } else if (trace instanceof OrdnanceFx.Line line) {
                collectLine(t, line, camera, out, alphaMult);
            } else if (trace instanceof OrdnanceFx.Falling falling) {
                collectFalling(t, falling, camera, out, alphaMult, cellPx);
            }
        }
    }

    private void collectStreak(Live t, OrdnanceFx.Streak streak, BattleCamera camera,
                               DrawList out, float alphaMult) {
        float progress = progress(t, t.flightSeconds);
        float headX = lerp(t.release.fromX(), t.release.toX(), progress);
        float headY = lerp(t.release.fromY(), t.release.toY(), progress);
        float travelled = distance(t.release.fromX(), t.release.fromY(), headX, headY);
        float tail = Math.min(streak.lengthCells(), travelled);
        if (tail <= 1e-5f) return;
        float total = distance(t.release.fromX(), t.release.fromY(),
                t.release.toX(), t.release.toY());
        float back = total <= 1e-5f ? 0f : tail / total;
        float tailX = headX - (t.release.toX() - t.release.fromX()) * back;
        float tailY = headY - (t.release.toY() - t.release.fromY()) * back;
        addLine(out, camera, tailX, tailY, headX, headY, streak.widthPx(),
                streak.color(), fade(t) * alphaMult);
    }

    private void collectLine(Live t, OrdnanceFx.Line line, BattleCamera camera,
                             DrawList out, float alphaMult) {
        addLine(out, camera, t.release.fromX(), t.release.fromY(),
                t.release.toX(), t.release.toY(), line.widthPx(),
                line.color(), fade(t) * alphaMult);
    }

    private void collectFalling(Live t, OrdnanceFx.Falling falling, BattleCamera camera,
                                DrawList out, float alphaMult, float cellPx) {
        if (t.arrived) return;
        float progress = progress(t, t.flightSeconds);
        float x = lerp(t.release.fromX(), t.release.toX(), progress);
        float y = lerp(t.release.fromY(), t.release.toY(), progress);
        ShuttleSpriteCache body = sprites == null ? null
                : sprites.projectileSprite(falling.spritePath());
        if (body == null) {
            // The bomb body is a vanilla texture, and a host without the
            // install has none. Draw the fall as a dark streak rather than as
            // nothing: a delivery that is invisible on the way down is exactly
            // the fault this effect exists to fix.
            addLine(out, camera, t.release.fromX(), t.release.fromY(), x, y,
                    FALLING_STREAK_WIDTH_PX, Color.DARK_GRAY, 0.55f * alphaMult);
            return;
        }
        float px = falling.visualCells() * cellPx;
        out.addSprite(RenderLayer.SHOTS, body.sprite,
                camera.cellToScreenX(x), camera.cellToScreenY(y),
                px * body.aspect, px,
                bearingDegrees(t.release.fromX(), t.release.fromY(),
                        t.release.toX(), t.release.toY()),
                1f, 1f, 1f, alphaMult);
    }

    private static void addLine(DrawList out, BattleCamera camera,
                                float x0, float y0, float x1, float y1,
                                float widthPx, Color color, float alpha) {
        out.addLine(RenderLayer.SHOTS,
                camera.cellToScreenX(x0), camera.cellToScreenY(y0),
                camera.cellToScreenX(x1), camera.cellToScreenY(y1),
                widthPx,
                color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                alpha);
    }

    /** Flight fraction, clamped; a zero-flight trace is arrived from the start. */
    static float progress(Live t, float flightSeconds) {
        if (flightSeconds <= 0f) return 1f;
        return Math.max(0f, Math.min(1f, t.age / flightSeconds));
    }

    /** Full brightness while travelling, then a linear fade over the tail. */
    static float fade(Live t) {
        float flight = t.flightSeconds;
        float tail = t.fx.trace().fadeSeconds();
        if (t.age <= flight || tail <= 0f) return 1f;
        return Math.max(0f, 1f - (t.age - flight) / tail);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float distance(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float bearingDegrees(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        if (dx == 0f && dy == 0f) return 0f;
        return (float) Math.toDegrees(Math.atan2(dy, dx)) - 90f;
    }
}
