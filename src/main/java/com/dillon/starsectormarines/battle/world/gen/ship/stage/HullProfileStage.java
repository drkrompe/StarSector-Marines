package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckProfile;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckZone;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipKeys;

/**
 * Step 1 (ship) — establish the deck's longitudinal shape and publish it.
 *
 * <p>This stage carves nothing. The hull profile is a <em>fact</em> about which
 * cells the hull encloses; the deck starts solid and later stages carve inside
 * that envelope. Keeping shape and carving separate is what lets the spine,
 * compartment, and bulkhead stages each ask one authority where the hull is.
 *
 * <p>The silhouette is deliberately asymmetric fore to aft. The bow taper is
 * longer and runs to a point; the stern taper is shorter and stays blunt, which
 * is both how ships read and the property that keeps this family from
 * collapsing into the mirrored geometry the station layouts already cover.
 * Port and starboard depths are drawn independently, so the hull is not
 * mirrored across the spine either.
 */
public final class HullProfileStage implements GenStage {

    /** Fraction of the deck's length spent tapering out from the bow. */
    private static final float BOW_TAPER = 0.30f;
    /** Fraction of the deck's length spent tapering in to the stern — deliberately shorter than the bow. */
    private static final float STERN_TAPER = 0.18f;
    /** Extra interior rows the bow retains at its narrowest frame. */
    private static final int BOW_MIN_DEPTH = 0;
    /** Extra interior rows the stern retains at its narrowest frame; a blunt stern, unlike the pointed bow. */
    private static final int STERN_MIN_DEPTH = 2;
    /** Frames per hull-jitter section. Low frequency keeps the plating irregular rather than noisy. */
    private static final int JITTER_SECTION = 5;
    /** Maximum rows one section's jitter may pull a side in by. */
    private static final int JITTER_RANGE = 2;

    private final int spineWidth;

    public HullProfileStage(int spineWidth) {
        if (spineWidth < 1) throw new IllegalArgumentException("spine width must be positive");
        this.spineWidth = spineWidth;
    }

    @Override
    public void run(GenContext ctx) {
        int frames = ctx.width;
        int spineTop = (ctx.height - spineWidth) / 2;
        int spineBottom = spineTop + spineWidth - 1;

        // One row of hull plating is reserved at each map edge so the deck is
        // always enclosed and FinalizeStage never tags a boundary cell walkable.
        int maxPortDepth = spineTop - 1;
        int maxStarboardDepth = ctx.height - 2 - spineBottom;
        if (maxPortDepth < 0 || maxStarboardDepth < 0) {
            throw new IllegalStateException("deck height " + ctx.height
                    + " cannot enclose a " + spineWidth + "-wide spine with hull plating");
        }

        int[] portJitter = sectionJitter(ctx, frames);
        int[] starboardJitter = sectionJitter(ctx, frames);

        int[] top = new int[frames];
        int[] bottom = new int[frames];
        DeckZone[] zone = new DeckZone[frames];

        for (int f = 0; f < frames; f++) {
            Taper taper = taperAt(f, frames);
            int port = depth(taper, maxPortDepth, portJitter[f]);
            int starboard = depth(taper, maxStarboardDepth, starboardJitter[f]);
            top[f] = spineTop - port;
            bottom[f] = spineBottom + starboard;
            zone[f] = zoneAt(f, frames);
        }

        ctx.put(ShipKeys.DECK_PROFILE, new DeckProfile(spineTop, spineBottom, top, bottom, zone));
    }

    /**
     * A frame's share of full beam plus the depth its end retains. The two
     * travel together because taper alone cannot say which end a frame is on —
     * it falls to zero at the bow and the stern alike, and those ends are
     * deliberately shaped differently.
     */
    private record Taper(float fraction, int retained) {}

    /** Smoothstepped so the plating curves instead of reading as two straight bevels. */
    private static Taper taperAt(int frame, int frames) {
        float t = frames == 1 ? 0.5f : (float) frame / (frames - 1);
        if (t < BOW_TAPER) return new Taper(smoothstep(t / BOW_TAPER), BOW_MIN_DEPTH);
        if (t > 1f - STERN_TAPER) return new Taper(smoothstep((1f - t) / STERN_TAPER), STERN_MIN_DEPTH);
        return new Taper(1f, 0);
    }

    /**
     * Interpolates between the end's retained depth and full beam, then pulls
     * the side in by this section's jitter. The lower clamp keeps jitter from
     * eroding a shaped end — a blunt stern stays blunt — while still letting
     * the broad midships plating wander.
     */
    private static int depth(Taper taper, int maxDepth, int jitter) {
        int retained = Math.min(taper.retained(), maxDepth);
        int scaled = Math.round(retained + (maxDepth - retained) * taper.fraction());
        int lower = taper.fraction() >= 1f ? Math.max(0, maxDepth - JITTER_RANGE) : retained;
        return Math.max(lower, Math.min(maxDepth, scaled - jitter));
    }

    private static DeckZone zoneAt(int frame, int frames) {
        float t = frames == 1 ? 0.5f : (float) frame / (frames - 1);
        if (t < 1f / 3f) return DeckZone.FORE;
        if (t < 2f / 3f) return DeckZone.MIDSHIPS;
        return DeckZone.AFT;
    }

    /**
     * One jitter value per frame, drawn once per {@link #JITTER_SECTION}-frame
     * run so neighbouring frames agree. Every frame consumes exactly one draw
     * decision from the section it belongs to, keeping the stream deterministic
     * regardless of deck length.
     */
    private static int[] sectionJitter(GenContext ctx, int frames) {
        int sections = (frames + JITTER_SECTION - 1) / JITTER_SECTION;
        int[] perSection = new int[sections];
        for (int s = 0; s < sections; s++) {
            perSection[s] = ctx.rng.nextInt(JITTER_RANGE + 1);
        }
        int[] perFrame = new int[frames];
        for (int f = 0; f < frames; f++) {
            perFrame[f] = perSection[f / JITTER_SECTION];
        }
        return perFrame;
    }

    private static float smoothstep(float x) {
        float c = Math.max(0f, Math.min(1f, x));
        return c * c * (3f - 2f * c);
    }
}
