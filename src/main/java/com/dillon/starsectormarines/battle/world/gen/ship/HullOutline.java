package com.dillon.starsectormarines.battle.world.gen.ship;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Turns a hull's collision polygon into the outline a deck is laid out inside.
 *
 * <p>The shape a ship presents to the rest of the game is the shape her decks
 * should have. Every hull publishes that polygon — it is what the game collides
 * against — and it is the one description of a hull's form available while the
 * game is running, since tracing the art needs pixels a mod cannot reach. It is
 * also the better description: art carries glow, banners and overhang that a
 * deck has no floor for, and the collision hull is the ship herself.
 *
 * <p>Bow-forward, port to the left, in the game's own axes: a hull's polygon
 * runs along X and spreads across Y. The two sides are kept apart rather than
 * averaged, because a mirrored deck is exactly the station geometry the
 * longitudinal family exists to avoid.
 *
 * <p>Nothing here reads a file. A polygon arrives from wherever the caller found
 * it, which is what lets the same conversion serve a running game, a test, and
 * an authoring tool.
 */
public final class HullOutline {

    /** Samples taken bow to stern. Enough to keep a fine bow from reading as a wedge. */
    private static final int SAMPLES = 64;

    /**
     * The half-beam percentile that maps to full deck depth; wider points clip.
     *
     * <p>Normalizing against the hull's widest point would be wrong. Many hulls
     * are long and slender with one bulge — a wing root, a sponson, an engine
     * block — and dividing by that peak shrinks the rest of the ship to a sliver
     * too shallow to hold any compartment, leaving a deck that is nearly all
     * dead structure. Scaling against a high percentile maps the hull's typical
     * beam near full depth and lets the genuine outlier clip.
     */
    private static final float BEAM_PERCENTILE = 0.80f;

    private HullOutline() { }

    /**
     * The outline of a hull whose collision polygon is {@code bounds}, as
     * alternating x and y in the game's own units, or null for a polygon too
     * degenerate to lay a deck inside.
     *
     * <p>Returning null rather than a fallback shape is deliberate: a caller
     * that cannot get a real outline should say so and take the synthetic
     * taper knowingly, not receive an invented hull that looks measured.
     */
    public static HullSilhouette fromBounds(float[] bounds, String source) {
        if (bounds == null || bounds.length < 6 || bounds.length % 2 != 0) return null;

        int corners = bounds.length / 2;
        float bow = Float.NEGATIVE_INFINITY;
        float stern = Float.POSITIVE_INFINITY;
        for (int corner = 0; corner < corners; corner++) {
            float along = bounds[corner * 2];
            bow = Math.max(bow, along);
            stern = Math.min(stern, along);
        }
        float length = bow - stern;
        if (!(length > 0f)) return null;

        float[] port = new float[SAMPLES];
        float[] starboard = new float[SAMPLES];
        float widest = 0f;
        for (int sample = 0; sample < SAMPLES; sample++) {
            float frame = bow - length * sample / (SAMPLES - 1);
            List<Float> across = crossings(bounds, corners, frame);
            if (across.isEmpty()) {
                // A frame the polygon does not reach: hold the previous
                // section rather than pinching the hull to nothing.
                if (sample > 0) {
                    port[sample] = port[sample - 1];
                    starboard[sample] = starboard[sample - 1];
                }
                continue;
            }
            float toPort = Float.NEGATIVE_INFINITY;
            float toStarboard = Float.POSITIVE_INFINITY;
            for (float side : across) {
                toPort = Math.max(toPort, side);
                toStarboard = Math.min(toStarboard, side);
            }
            port[sample] = Math.max(0f, toPort);
            starboard[sample] = Math.max(0f, -toStarboard);
            widest = Math.max(widest, toPort - toStarboard);
        }
        if (!(widest > 0f)) return null;

        float reference = typicalHalfBeam(port, starboard);
        for (int sample = 0; sample < SAMPLES; sample++) {
            port[sample] /= reference;
            starboard[sample] /= reference;
        }
        return new HullSilhouette(port, starboard, widest / length, source);
    }

    /** Where the polygon's edges cross one frame, across the beam. */
    private static List<Float> crossings(float[] bounds, int corners, float frame) {
        List<Float> across = new ArrayList<>(4);
        for (int corner = 0; corner < corners; corner++) {
            int next = (corner + 1) % corners;
            float fromAlong = bounds[corner * 2];
            float toAlong = bounds[next * 2];
            if (fromAlong == toAlong) continue;
            if ((fromAlong - frame) * (toAlong - frame) > 0f) continue;
            float fromAcross = bounds[corner * 2 + 1];
            float toAcross = bounds[next * 2 + 1];
            float travel = (frame - fromAlong) / (toAlong - fromAlong);
            across.add(fromAcross + (toAcross - fromAcross) * travel);
        }
        return across;
    }

    /** The {@link #BEAM_PERCENTILE} half-beam over every frame the hull occupies. */
    private static float typicalHalfBeam(float[] port, float[] starboard) {
        float[] halves = new float[port.length];
        int occupied = 0;
        for (int sample = 0; sample < port.length; sample++) {
            float half = Math.max(port[sample], starboard[sample]);
            if (half > 0f) halves[occupied++] = half;
        }
        if (occupied == 0) return 1f;
        float[] present = Arrays.copyOf(halves, occupied);
        Arrays.sort(present);
        int index = Math.min(occupied - 1, Math.round((occupied - 1) * BEAM_PERCENTILE));
        return Math.max(0.0001f, present[index]);
    }
}
