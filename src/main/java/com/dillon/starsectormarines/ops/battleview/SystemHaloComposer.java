package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.SystemFxService;

import java.util.function.Consumer;

/**
 * Draws what a running integral system looks like: the wearer's own head and
 * body layers a second time, behind them, a little larger and shifted the way
 * the screen faces, in a shimmering blue. The result is a rim of light hugging
 * the marine's silhouette rather than a shape floating in front of it
 * ({@code progression-nouns.md}).
 *
 * <p><b>No new art, on purpose.</b> Everything drawn here is the actor's
 * existing layer stack replayed through a tinted emitter, so a suit that gets
 * new armour art gets a halo shaped like it on the same day.
 *
 * <p><b>The arc has to survive.</b> A uniform glow would hide the one property
 * that makes flanking worth doing, so the halo is not a ring: the enlarged copy
 * is emitted several times, each shifted a fixed distance along a bearing
 * sampled across the authored arc, and the union of those shifted silhouettes
 * is a rim that only protrudes in the directions the screen actually covers. A
 * 90-degree screen is a crescent across the front; a 200-degree one wraps most
 * of the way round and leaves a notch behind. One shift alone cannot express a
 * narrow arc at all — an offset silhouette always protrudes over more than half
 * the circle — which is why the sweep exists rather than a single displaced
 * copy.
 *
 * <p><b>What is left is drawn, not counted.</b> The screen is a soak pool now,
 * so the rim simply dims as the pool is spent and a screen about to break looks
 * like one. Breaking gets its own moment: the same silhouette, whole and
 * symmetric, thrown outward and gone in half a second, because a shield beaten
 * down by massed fire is the outcome the whole mechanic exists to produce.
 *
 * <p><b>Keyed on the capability, not the carrier.</b> Everything comes off the
 * {@code SYSTEM_FX} appearance columns ({@link SystemFxService}); this class
 * cannot tell which armour pattern is in front of it and must not learn. A
 * running system that raises no screen reports a zero arc and gets an even,
 * undirected rim — there is no direction to claim, and implying one would be a
 * lie about a capability that has no facing.
 */
final class SystemHaloComposer {

    /** How much larger the halo copy is drawn, as a fraction of the actor's own size. */
    private static final float RIM_SCALE = 0.11f;
    /** How far each copy is shifted off the actor, in shoulder-widths. Sets the rim's thickness. */
    private static final float SPREAD_SHOULDERS = 0.17f;
    /** Degrees of arc per emitted copy. Constant density, so a wide screen is not a coarser one. */
    private static final float DEGREES_PER_COPY = 20f;
    private static final int MIN_COPIES = 3;
    private static final int MAX_COPIES = 14;
    /** Copies used for a system that raises no screen, spread evenly all the way round. */
    private static final int RING_COPIES = 10;

    /** Per-copy alpha at the reference copy count, before pool and shimmer scaling. */
    private static final float RIM_ALPHA = 0.85f;
    private static final int REFERENCE_COPIES = 5;
    /** Alpha of the undirected rim a screenless running system gets. Present, but not a claim. */
    private static final float RUNNING_ONLY_ALPHA = 0.55f;

    /** How far the shatter throws the silhouette outward, in shoulder-widths, by the time it is gone. */
    private static final float BREAK_THROW_SHOULDERS = 0.55f;
    private static final float BREAK_SCALE = 0.45f;
    private static final float BREAK_ALPHA = 0.85f;
    private static final int BREAK_COPIES = 12;

    private static final float SCREEN_R = 0.34f;
    private static final float SCREEN_G = 0.72f;
    private static final float SCREEN_B = 1.00f;
    /** The shatter reads hotter than the screen it was: a break is not a dimmer screen. */
    private static final float BREAK_R = 0.86f;
    private static final float BREAK_G = 0.96f;
    private static final float BREAK_B = 1.00f;

    /** How deep the shimmer cuts. A sheen over the rim, never a strobe. */
    private static final float SHIMMER_DEPTH = 0.30f;

    private final Capture capture = new Capture();

    /**
     * Emits one actor's halo, if it has one, under whatever the caller is about
     * to draw for that actor. {@code composition} is the actor's own layer
     * emission, replayed here through a capturing emitter — the caller passes it
     * rather than this class rebuilding a second, drifting copy of how a marine
     * is put together.
     */
    void emit(DrawList out, SystemFxService fx, long unit,
              LayeredSpriteCache bodyLayer, LayeredSpriteCache headLayer,
              float actorX, float actorY, float shoulderPx, float alphaMult,
              Consumer<LayeredUnitComposer.SpriteEmitter> composition) {
        float intensity = fx.intensity(unit);
        float breakFlash = fx.breakFlash(unit);
        if (intensity <= 0f && breakFlash <= 0f) return;

        capture.reset(bodyLayer, headLayer);
        composition.accept(capture);
        if (capture.count == 0) return;

        if (intensity > 0f) {
            emitRim(out, fx, unit, actorX, actorY, shoulderPx, alphaMult, intensity);
        }
        if (breakFlash > 0f) {
            emitShatter(out, actorX, actorY, shoulderPx, alphaMult, breakFlash);
        }
    }

    private void emitRim(DrawList out, SystemFxService fx, long unit,
                         float actorX, float actorY, float shoulderPx, float alphaMult,
                         float intensity) {
        float arc = fx.arcDegrees(unit);
        boolean directed = arc > 0f;
        int copies = directed
                ? Math.max(MIN_COPIES, Math.min(MAX_COPIES, Math.round(arc / DEGREES_PER_COPY)))
                : RING_COPIES;
        // A screen fades with what is left in its pool; a system with no screen
        // has no pool to spend, so its rim rides the window instead.
        float strength = directed ? 0.30f + 0.70f * fx.soakFraction(unit) : intensity;
        float base = (directed ? RIM_ALPHA : RUNNING_ONLY_ALPHA)
                * REFERENCE_COPIES / (float) copies * strength;
        float phase = fx.shimmerPhase(unit);
        float spread = shoulderPx * SPREAD_SHOULDERS;
        float scale = 1f + RIM_SCALE;
        float facing = fx.arcFacingDegrees(unit);

        for (int i = 0; i < copies; i++) {
            float along = copies == 1 ? 0.5f : i / (float) (copies - 1);
            float bearing = directed
                    ? facing + (along - 0.5f) * arc
                    : facing + 360f * i / copies;
            // The sheen travels around the rim rather than pulsing the whole
            // thing at once, which is what makes it read as a surface.
            float shimmer = 1f + SHIMMER_DEPTH
                    * (float) Math.sin(2 * Math.PI * (phase + along));
            replay(out, actorX, actorY, scale, offsetX(bearing, spread),
                    offsetY(bearing, spread),
                    SCREEN_R, SCREEN_G, SCREEN_B, base * shimmer * alphaMult);
        }
    }

    /**
     * The shatter: the wearer's whole silhouette thrown outward in every
     * direction and gone. Symmetric on purpose — the arc stopped mattering the
     * moment the screen stopped existing, and a directed break would read as a
     * screen that is still there.
     */
    private void emitShatter(DrawList out, float actorX, float actorY, float shoulderPx,
                             float alphaMult, float breakFlash) {
        float spent = 1f - breakFlash;
        float throwPx = shoulderPx * (SPREAD_SHOULDERS + BREAK_THROW_SHOULDERS * spent);
        float scale = 1f + RIM_SCALE + BREAK_SCALE * spent;
        float alpha = BREAK_ALPHA * breakFlash * breakFlash / BREAK_COPIES * REFERENCE_COPIES;
        for (int i = 0; i < BREAK_COPIES; i++) {
            float bearing = 360f * i / BREAK_COPIES;
            replay(out, actorX, actorY, scale, offsetX(bearing, throwPx),
                    offsetY(bearing, throwPx),
                    BREAK_R, BREAK_G, BREAK_B, alpha * alphaMult);
        }
    }

    /** Re-emits the captured layers scaled about the actor's own pivot and shifted. */
    private void replay(DrawList out, float actorX, float actorY, float scale,
                        float shiftX, float shiftY,
                        float red, float green, float blue, float alpha) {
        if (alpha <= 0f) return;
        for (int i = 0; i < capture.count; i++) {
            LayeredSpriteCache layer = capture.layers[i];
            float centerX = actorX + (capture.centerX[i] - actorX) * scale + shiftX;
            float centerY = actorY + (capture.centerY[i] - actorY) * scale + shiftY;
            // Additive: a tinted normal draw of dark armour art is a shadow
            // however hard it is tinted, and a screen is light the suit is
            // putting out rather than a silhouette behind it.
            out.addAdditiveSprite(RenderLayer.UNITS, layer.sprite, centerX, centerY,
                    capture.width[i] * scale, capture.height[i] * scale,
                    capture.angle[i], red, green, blue, Math.min(1f, alpha));
        }
    }

    /**
     * Screen-space offset along a simulation bearing. {@code AirBody.facingToward}
     * measures zero at north and increases counter-clockwise, so this is the one
     * place that conversion lives.
     */
    private static float offsetX(float bearingDegrees, float distance) {
        return -distance * (float) Math.sin(Math.toRadians(bearingDegrees));
    }

    private static float offsetY(float bearingDegrees, float distance) {
        return distance * (float) Math.cos(Math.toRadians(bearingDegrees));
    }

    /**
     * Keeps the head and body placements out of one composition pass so they can
     * be replayed a dozen times without recomposing the actor a dozen times.
     * Reused across units and frames; nothing here allocates after the first
     * call.
     */
    private static final class Capture implements LayeredUnitComposer.SpriteEmitter {
        private static final int CAPACITY = 4;

        private final LayeredSpriteCache[] layers = new LayeredSpriteCache[CAPACITY];
        private final float[] centerX = new float[CAPACITY];
        private final float[] centerY = new float[CAPACITY];
        private final float[] width = new float[CAPACITY];
        private final float[] height = new float[CAPACITY];
        private final float[] angle = new float[CAPACITY];
        private int count;
        private LayeredSpriteCache bodyLayer;
        private LayeredSpriteCache headLayer;

        void reset(LayeredSpriteCache body, LayeredSpriteCache head) {
            this.bodyLayer = body;
            this.headLayer = head;
            this.count = 0;
        }

        @Override
        public void add(LayeredSpriteCache layer, float cx, float cy,
                        float w, float h, float angleDegrees,
                        float red, float green, float blue, float alpha) {
            if (layer == null || layer.sprite == null) return;
            if (layer != bodyLayer && layer != headLayer) return;
            if (count >= CAPACITY) return;
            layers[count] = layer;
            centerX[count] = cx;
            centerY[count] = cy;
            width[count] = w;
            height[count] = h;
            angle[count] = angleDegrees;
            count++;
        }
    }
}
