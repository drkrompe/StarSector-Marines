package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.ops.ConquestArrivalConfig;

/**
 * The marines' own arrival policy: they come down from orbit, and a mission
 * that leaves its lift unstated flies the ferry.
 *
 * <p><b>A marine arrival does not cross the map edge.</b> It appears at a
 * descent point {@link #DESCENT_CELLS} off its berth on the side away from the
 * objective, at altitude, flies down, sets its half-squad on the pad, and climbs
 * back to the same point; the next load is already in orbit behind it. A round
 * trip is therefore the descent, the turnaround on the pad and the climb — a
 * constant measured in cells of descent, never in the size of the map and never
 * in how far inland the standoff put the beachhead. The edge-crossing cadence it
 * replaces was ~460 ticks <em>plus ~5.3 ticks per cell of slide</em>, which is
 * how a beachhead moved 226 cells inland came to cost 1,450 ticks a sortie.
 *
 * <p>This is a marine policy and not a change to the air corridor. Defender
 * shuttles are planetary and keep crossing their own edge; aircraft sorties keep
 * their strips. {@code AirCorridor.descent} is the one thing the air layer
 * learned for it.
 *
 * @see com.dillon.starsectormarines.battle.air.AirCorridor#descent
 */
public final class OrbitalLift {

    /**
     * How far off its berth a marine lift appears, in cells.
     *
     * <p>Far enough that the arrival is a flight the player watches come down —
     * about two and a half seconds under power at {@link ShuttleType#AEROSHUTTLE}
     * handling, which is the whole of the altitude lerp — and near enough that
     * the leg is the descent rather than a transit across somebody's ground. It
     * is the only distance in the round trip, so it is also the dial that says
     * what a sortie costs.
     */
    public static final float DESCENT_CELLS = 18f;

    /**
     * The drop zones the ferry flies: three, one pair to each of a Conquest's
     * three lanes, whatever the force.
     *
     * <p>A lift sized from the committed seats and the landing share was
     * tried and took more compounds but kept fewer than the ferry. It is what
     * a mission that says nothing gets.
     */
    public static final int FERRY_DROP_ZONES = 3;

    private OrbitalLift() {}

    /**
     * Fills in whatever the mission left to the lift.
     *
     * <p>A stated count wins outright and is returned untouched, because a
     * mission that authors one beachhead means one. What a
     * {@link ConquestArrivalConfig#DERIVED} count gets is the
     * {@link #ferryShape}.
     */
    public static ConquestArrivalConfig resolve(ConquestArrivalConfig config) {
        if (config == null || !config.derivesItsShape()) return config;
        return ferryShape(config);
    }

    /** Three drop zones with a pair on each, whatever the force. */
    public static ConquestArrivalConfig ferryShape(ConquestArrivalConfig config) {
        return config.withDropZoneCount(FERRY_DROP_ZONES)
                .withShuttlePairsPerZone(1);
    }

    /**
     * Folds a derived shape onto the arrival areas the finished map actually
     * authored, keeping the pair count exactly.
     *
     * <p>A derived zone count is a request against an estimate — the landing
     * claim is seeded before the town grows over the ground round it — so a map
     * that seated fewer areas than the lift asked for lands the same craft on
     * fewer beachheads rather than refusing to start. A <em>stated</em> count is
     * a mission promise and is left alone, so it still fails loudly where the
     * map cannot keep it.
     */
    public static ConquestArrivalConfig foldOntoAvailableAreas(
            ConquestArrivalConfig config, ConquestArrivalConfig asAuthored,
            int availableAreas) {
        if (config == null || asAuthored == null) return config;
        if (asAuthored.dropZoneCount() != ConquestArrivalConfig.DERIVED) return config;
        if (availableAreas < 1 || config.dropZoneCount() <= availableAreas) return config;
        int pairs = config.playerShuttlePairCount();
        return config.withDropZoneCount(availableAreas)
                .withShuttlePairsPerZone(Math.max(1, ceilDiv(pairs, availableAreas)));
    }

    private static int ceilDiv(int value, int by) {
        return by <= 0 ? value : (value + by - 1) / by;
    }
}
