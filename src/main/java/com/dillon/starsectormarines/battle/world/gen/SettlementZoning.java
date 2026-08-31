package com.dillon.starsectormarines.battle.world.gen;

/**
 * The selection-layer policy that turns what the campaign knows about a market
 * into how its settlement is built — the third of the bridge's policy classes,
 * beside {@link EconomicZoning} for industry and {@link SurfaceZoning} for
 * terrain, and game-API-free for the same reason.
 *
 * <p>Both answers come off market size, because size is the campaign's own
 * statement of how much place there is. A size-9 world is a city that fills its
 * map; a size-2 waystation is a handful of buildings on a rock, and drawing it
 * as a city is the fiction this exists to stop.
 */
public final class SettlementZoning {

    private SettlementZoning() {}

    /**
     * Largest market size that still reads as an outpost rather than a town.
     * Vanilla sizes run 1..10; 3 and below is a mining claim, a waystation, or
     * a survey post — the places nobody paved a road to.
     */
    public static final int OUTPOST_MAX_SIZE = 3;

    /**
     * How this settlement joins the rest of its world.
     *
     * <p>A small market is an outpost supplied by ship. Everywhere larger grew
     * where people could drive to it, so it takes a road.
     *
     * @param marketSize   vanilla market size; {@code 0} means no market backs
     *                     this battle, which is not an outpost — it is an
     *                     absence of information, and the road is the safe read.
     * @param decivilized  the world has lost its infrastructure. Reserved for
     *                     the ruins case: nothing is guaranteed to reach it.
     */
    public static SettlementLink linkFor(int marketSize, boolean decivilized) {
        if (decivilized) return SettlementLink.NONE;
        if (marketSize > 0 && marketSize <= OUTPOST_MAX_SIZE) return SettlementLink.LANDING;
        return SettlementLink.ROAD;
    }

    /**
     * How densely built the settlement is, on the 0..1 scale
     * {@code GrownTrunkPlan.Profile.of} takes.
     *
     * <p>Linear in size, from a thin outpost to a nearly-solid metropolis. It
     * was a curve first, which put a size-5 market at 0.33 — and a size-5
     * market is a substantial colony rather than a sparse town, so the curve was
     * wrong about the middle of the range, which is where most inhabited
     * markets actually sit.
     *
     * <p>A size-10 world stops short of saturation on purpose: full density
     * leaves no open ground at all, and even a metropolis has ground its roads
     * never reached.
     */
    public static float densityFor(int marketSize) {
        if (marketSize <= 0) return 0f;
        return Math.min(0.90f, 0.15f + 0.75f * (marketSize / 10f));
    }
}
