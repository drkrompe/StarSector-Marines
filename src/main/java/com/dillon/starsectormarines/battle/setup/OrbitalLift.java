package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.AirHandling;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.ops.ConquestArrivalConfig;
import com.dillon.starsectormarines.ops.LandingShare;

/**
 * The marines' own arrival policy: they come down from orbit, and the lift is
 * sized so the whole committed force is on the ground inside the mission's
 * {@link LandingShare}.
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
 * <p><b>The lift scales with the seats.</b> Given the committed seats, the
 * seats one sortie carries and the constant round trip, the number of craft
 * that can land all of them inside the window follows directly, and waves
 * overlap because each craft is flying its own cycle: while one pair is on the
 * pad the next is already descending. The berths that needs is the same number
 * — one pair to an arrival area — which is what the landing precinct's program
 * is asked for.
 *
 * @see com.dillon.starsectormarines.battle.air.AirCorridor#descent
 */
public final class OrbitalLift {

    /**
     * Turns the orbital lift off for a control run:
     * {@code -Dbattle.conquest.orbitalLift=false}. Off restores the map-edge
     * arrival and the fixed three pairs, which is the baseline the matrix in
     * {@code conquest-nouns.md} was measured against.
     */
    public static final String PROPERTY = "battle.conquest.orbitalLift";

    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

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
     * How many arrival areas the shipped landing apron already seats.
     *
     * <p>Measured at 560x336 on both canonical fixtures: five, against the three
     * a Conquest asked for. So a lift wanting four pairs needs nothing from the
     * program, and only one wanting more than five is asking for ground the
     * apron does not already own.
     */
    public static final int AREAS_THE_SHIPPED_APRON_SEATS = 5;

    private OrbitalLift() {}

    /** Whether marine arrivals descend from orbit on this run. */
    public static boolean enabled() {
        return ENABLED;
    }

    /**
     * Fills in whatever the mission left to the lift.
     *
     * <p>A stated count wins outright and is returned untouched, because a
     * mission that authors one beachhead means one. Where both are
     * {@link ConquestArrivalConfig#DERIVED} the pairs are sized from the seats
     * and spread one to a drop zone, so the count of zones the map is asked for
     * is the count of pairs that will fly.
     *
     * @param committedSeats the player segment's seats — the segment these
     *                       counts govern. The employer's own pair is authored
     *                       separately and is not sized from here.
     */
    public static ConquestArrivalConfig resolve(ConquestArrivalConfig config,
                                                int committedSeats) {
        if (config == null || !config.derivesItsShape()) return config;
        int pairs = pairsFor(committedSeats, config.landingShare());
        int zones = config.dropZoneCount() != ConquestArrivalConfig.DERIVED
                ? config.dropZoneCount() : pairs;
        int perZone = config.shuttlePairsPerZone() != ConquestArrivalConfig.DERIVED
                ? config.shuttlePairsPerZone()
                : Math.max(1, ceilDiv(pairs, Math.max(1, zones)));
        return config.withDropZoneCount(Math.max(1, zones))
                .withShuttlePairsPerZone(perZone);
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

    /**
     * How many reusable pairs land {@code committedSeats} inside the share.
     *
     * <p>The arithmetic is the honest one: a craft sets its first load down one
     * descent after it launches and every load after that one round trip apart,
     * so a window of {@code w} seconds is
     * {@code 1 + floor((w - descent) / roundTrip)} sorties per craft, and the
     * craft needed is the sorties divided by that. Pairs because a Conquest
     * squad is delivered by two six-seat craft onto the two berths of one area.
     */
    public static int pairsFor(int committedSeats, LandingShare share) {
        LandingShare resolved = share != null ? share : LandingShare.DEFAULT;
        int seatsPerSortie = ShuttleType.AEROSHUTTLE.capacity;
        int sorties = ceilDiv(Math.max(0, committedSeats), seatsPerSortie);
        if (sorties <= 0) return 1;
        return Math.max(1, ceilDiv(craftFor(sorties, resolved), 2));
    }

    /** How many craft it takes to fly {@code sorties} inside the share. */
    public static int craftFor(int sorties, LandingShare share) {
        if (sorties <= 0) return 0;
        return ceilDiv(sorties, sortiesPerCraft(share));
    }

    /**
     * Sorties one craft lands inside the window. At least one: a craft that
     * cannot finish a second round trip still delivers the load it launched
     * with.
     */
    public static int sortiesPerCraft(LandingShare share) {
        LandingShare resolved = share != null ? share : LandingShare.DEFAULT;
        float afterTheFirst = resolved.windowSeconds() - descentSeconds();
        if (afterTheFirst <= 0f) return 1;
        return 1 + (int) Math.floor(afterTheFirst / roundTripSeconds());
    }

    /**
     * The constant round trip in sim-seconds: down, turn round, back up.
     *
     * <p>The turnaround is the pad time the delivery actually owes — one
     * deboard per seat at the craft's own interval — plus the offstage re-arm
     * the cycle already charges. Nothing in it is a distance across the map.
     */
    public static float roundTripSeconds() {
        return 2f * descentSeconds() + turnaroundSeconds();
    }

    public static float turnaroundSeconds() {
        return ShuttleType.AEROSHUTTLE.capacity * ShuttleType.AEROSHUTTLE.deboardInterval
                + ShuttleMission.DEFAULT_REARM_DELAY_SEC;
    }

    /** One leg of the descent, flown at Aeroshuttle handling. */
    public static float descentSeconds() {
        return legSeconds(ShuttleType.AEROSHUTTLE, DESCENT_CELLS);
    }

    /**
     * How long a craft takes to fly {@code cells} from a standstill to a stop:
     * accelerate to its cruise, hold it, brake onto the point. Where the leg is
     * too short to reach cruise the profile is the triangle instead.
     */
    public static float legSeconds(AirHandling flight, float cells) {
        float v = flight.maxSpeed();
        float a = flight.accel();
        float b = flight.brakingAccel();
        if (cells <= 0f || v <= 0f || a <= 0f || b <= 0f) return 0f;
        float toCruise = v * v / (2f * a);
        float toStop = v * v / (2f * b);
        if (toCruise + toStop <= cells) {
            return v / a + v / b + (cells - toCruise - toStop) / v;
        }
        float peak = (float) Math.sqrt(2f * cells * a * b / (a + b));
        return peak / a + peak / b;
    }

    private static int ceilDiv(int value, int by) {
        return by <= 0 ? value : (value + by - 1) / by;
    }
}
