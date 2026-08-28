package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckProfile;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckZone;
import com.dillon.starsectormarines.battle.world.gen.ship.HullSilhouette;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/**
 * Step 1 (ship) — establish the deck's longitudinal shape and publish it.
 *
 * <p>This stage carves nothing. The hull profile is a <em>fact</em> about which
 * cells the hull encloses; the deck starts solid and later stages carve inside
 * that envelope. Keeping shape and carving separate is what lets the spine,
 * corridor, and compartment stages each ask one authority where the hull is.
 *
 * <p>Given a {@link HullSilhouette} the stage traces it, so a deck inherits a
 * real hull's proportions and its port/starboard asymmetry. Without one it falls
 * back to a synthetic taper — a long pointed bow against a short blunt stern —
 * which keeps the family usable for hulls that have no outline on hand. Either
 * way the spine is guaranteed to be enclosed at every frame and the plating is
 * never breached.
 */
public final class HullProfileStage implements GenStage {

    /** Fraction of length spent tapering out from the bow, when no silhouette is supplied. */
    private static final float BOW_TAPER = 0.30f;
    /** Fraction of length spent tapering in to the stern — deliberately shorter than the bow. */
    private static final float STERN_TAPER = 0.18f;
    /** Extra interior rows the stern retains at its narrowest frame; a blunt stern, unlike the pointed bow. */
    private static final int STERN_MIN_DEPTH = 2;

    private final int spineWidth;
    private final HullSilhouette silhouette;

    /** Synthetic-taper profile, for decks with no hull outline available. */
    public HullProfileStage(int spineWidth) {
        this(spineWidth, null);
    }

    /** Traces {@code silhouette} when non-null, otherwise falls back to the synthetic taper. */
    public HullProfileStage(int spineWidth, HullSilhouette silhouette) {
        if (spineWidth < 1) throw new IllegalArgumentException("spine width must be positive");
        this.spineWidth = spineWidth;
        this.silhouette = silhouette;
    }

    @Override
    public void run(GenContext ctx) {
        int frames = ctx.width;
        int spineTop = (ctx.height - spineWidth) / 2;
        int spineBottom = spineTop + spineWidth - 1;

        // One row of hull plating is reserved at each map edge so the deck stays
        // enclosed and FinalizeStage never tags a boundary cell walkable.
        int maxPortDepth = spineTop - 1;
        int maxStarboardDepth = ctx.height - 2 - spineBottom;
        if (maxPortDepth < 0 || maxStarboardDepth < 0) {
            throw new IllegalStateException("deck height " + ctx.height
                    + " cannot enclose a " + spineWidth + "-wide spine with hull plating");
        }

        int[] top = new int[frames];
        int[] bottom = new int[frames];
        DeckZone[] zone = new DeckZone[frames];

        for (int f = 0; f < frames; f++) {
            float t = frames == 1 ? 0.5f : (float) f / (frames - 1);
            int port;
            int starboard;
            if (silhouette != null) {
                port = Math.round(silhouette.portAt(t) * maxPortDepth);
                starboard = Math.round(silhouette.starboardAt(t) * maxStarboardDepth);
            } else {
                Taper taper = taperAt(t);
                port = syntheticDepth(taper, maxPortDepth);
                starboard = syntheticDepth(taper, maxStarboardDepth);
            }
            top[f] = spineTop - clamp(port, maxPortDepth);
            bottom[f] = spineBottom + clamp(starboard, maxStarboardDepth);
            zone[f] = zoneAt(t);
        }

        // Everything outside the hull is not deck. Said here because this stage
        // is the authority on where the hull is, and left unsaid every cell the
        // ship does not occupy defaults to indoor floor — which paints a
        // rectangle of decking around her and hides the vessel it belongs to.
        for (int f = 0; f < frames; f++) {
            for (int y = 0; y < ctx.height; y++) {
                if (y >= top[f] && y <= bottom[f]) continue;
                ctx.topology.setGroundKind(f, y, GroundKind.VOID);
            }
        }

        ctx.put(ShipKeys.DECK_PROFILE, new DeckProfile(spineTop, spineBottom, top, bottom, zone));
    }

    private static int clamp(int depth, int maxDepth) {
        return Math.max(0, Math.min(maxDepth, depth));
    }

    /**
     * A frame's share of full beam plus the depth its end retains. The two travel
     * together because taper alone cannot say which end a frame is on — it falls
     * to zero at bow and stern alike, and those ends are shaped differently.
     */
    private record Taper(float fraction, int retained) {}

    private static Taper taperAt(float t) {
        if (t < BOW_TAPER) return new Taper(smoothstep(t / BOW_TAPER), 0);
        if (t > 1f - STERN_TAPER) return new Taper(smoothstep((1f - t) / STERN_TAPER), STERN_MIN_DEPTH);
        return new Taper(1f, 0);
    }

    private static int syntheticDepth(Taper taper, int maxDepth) {
        int retained = Math.min(taper.retained(), maxDepth);
        return Math.round(retained + (maxDepth - retained) * taper.fraction());
    }

    private static DeckZone zoneAt(float t) {
        if (t < 1f / 3f) return DeckZone.FORE;
        if (t < 2f / 3f) return DeckZone.MIDSHIPS;
        return DeckZone.AFT;
    }

    private static float smoothstep(float x) {
        float c = Math.max(0f, Math.min(1f, x));
        return c * c * (3f - 2f * c);
    }
}
