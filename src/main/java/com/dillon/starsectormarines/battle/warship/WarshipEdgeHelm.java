package com.dillon.starsectormarines.battle.warship;

/**
 * The map-edge helm: a soft pressure that keeps a warship's fight off the edges of the map. It is a standing rule for
 * every hull frigate-sized and up, in every mission, and it is the only thing that shapes where over the map a fight
 * between ships goes; no mission declares an area for it.
 *
 * <p>Each hull wants a band of clear air between its hull and every edge, its <b>margin</b> ({@link #margin}): the
 * larger of its tightest turning circle and {@link #MARGIN_LENGTHS} of its own length, never more than
 * {@link #MARGIN_MAP_SHARE} of the map's shorter side, so a small map keeps a middle to fight over. Two things read
 * it:
 * <ul>
 *   <li>Stations a ship fighting hulls or screening chooses are kept out of the band ({@link #settle}), so the helm
 *   never sends a ship there on purpose.</li>
 *   <li>Steering ({@link #steer}) bends the course a ship wants away from an edge as it nears it. The pressure starts
 *   at the margin and grows to full at the edge, and it respects momentum: the distance it reads is what is left
 *   once the hull has stopped closing the edge at its own acceleration, so a heavy hull coming in fast turns away
 *   early rather than discovering the edge with its bow. The way toward the edge is taken off first, then the hull
 *   is pushed back in, so a ship running along the band keeps its way along it.</li>
 * </ul>
 * A centre that crosses the edge anyway is held on it ({@link #contain}); that is the backstop, not the behaviour.
 *
 * <p>It is a rule of the hull fight: it applies to a ship its own helm is flying against hulls, never to one holding a
 * station someone chose for it (a mission's, a power's, a remote order's, an escort's place in the screen line), which
 * keeps the place it was given. A ship deliberately crossing an edge is exempt: one flying in across its side's approved edge
 * ({@link Warship#enteringAcrossEdge}) until it has cleared its margin, and one leaving; an escort whose charge is
 * leaving screens it to the margin and follows it out once it has gone. So is everything placed
 * rather than flown: a route or lift run, a low pass or low hover, a berthed hull, a pilot's helm, and every craft of
 * fighter size.
 *
 * <p>{@code -Dbattle.warship.edgePressure=false} is the control: no steering pressure and no settling of stations past
 * the old hard margin of half a hull and {@link WarshipDuelHelm#EDGE_MARGIN_CELLS}.
 *
 * @param width  the map's width, in cells
 * @param height the map's height, in cells
 */
public record WarshipEdgeHelm(float width, float height) {

    static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("battle.warship.edgePressure"));
    /** A hull's margin is at least this many of its own lengths of clear air. */
    static final float MARGIN_LENGTHS = 1.5f;
    /** A hull's margin is never more than this share of the map's shorter side. */
    static final float MARGIN_MAP_SHARE = 0.2f;
    /** Nor less than this many cells. */
    static final float MIN_MARGIN_CELLS = 6f;
    /** How fast the way toward an edge is shed: all of it by a third of the way into the margin. */
    static final float SHED_RATE = 3f;
    /** The speed, as a share of its top speed, a hull is pushed back in at with its hull on the edge. */
    static final float PUSH_SHARE = 0.8f;

    /** The tightest circle the hull flies, in cells: its top speed over its top turn rate. */
    public static float turnRadius(Warship ship) {
        double rate = Math.toRadians(Math.max(1e-3f, ship.maxTurnDegPerSec));
        return (float) (ship.maxSpeed / rate);
    }

    /** The clear air, in cells, the hull wants between itself and every edge. */
    public float margin(Warship ship) {
        float wanted = Math.max(turnRadius(ship), MARGIN_LENGTHS * ship.lengthCells);
        float cap = MARGIN_MAP_SHARE * Math.min(width, height);
        return Math.max(MIN_MARGIN_CELLS, Math.min(cap, wanted));
    }

    /** The clear air, in cells, between the hull and {@code edge}: its centre's distance less half its length. */
    public float clearance(Warship ship, MapEdge edge) {
        return edge.inside(ship.centerX(), ship.centerY(), width, height) - 0.5f * ship.lengthCells;
    }

    /** The least {@link #clearance} over the four edges. */
    public float nearestClearance(Warship ship) {
        float least = Float.MAX_VALUE;
        for (MapEdge e : MapEdge.values()) least = Math.min(least, clearance(ship, e));
        return least;
    }

    /** Whether the edge pressure applies to {@code ship} now (see the class comment for who is exempt). */
    public static boolean applies(Warship ship) {
        return flown(ship) && fightingHulls(ship);
    }

    /**
     * Whether {@code ship} is a hull flown over the map rather than placed or deliberately crossing an edge: the
     * class comment's exemptions, without the hull-fight condition. The backstop ({@link #contain}) holds every such
     * hull on the map, whatever its helm is doing, since only its own edge is a way off it.
     */
    private static boolean flown(Warship ship) {
        return ENABLED && !ship.fighterSize() && !ship.destroyed() && !ship.departing() && !ship.berthed()
                && !ship.enteringAcrossEdge() && !ship.piloted() && !ship.isPassingThrough()
                && ship.lowPass() == null && !ship.lowHovering();
    }

    /**
     * Whether the ship's own helm has it: fighting a hull with its duel helm, or flying a manoeuvre. A ship holding a
     * station its mission or a power gave it, an escort keeping its place in the screen, a remote order: those are
     * places someone chose, and the pressure does not move a ship off them.
     */
    private static boolean fightingHulls(Warship ship) {
        return (ship.hullTarget() != null && ship.duelHelm()) || ship.manoeuvring();
    }

    /**
     * How hard {@code edge} presses on the hull, from zero at its margin to one with its hull on the edge, read from
     * where it will be once it has stopped closing the edge.
     */
    public float pressure(Warship ship, MapEdge edge) {
        float margin = margin(ship);
        float closing = ship.velocityX * edge.outX + ship.velocityY * edge.outY;
        float stopping = closing > 0f ? closing * closing / (2f * Math.max(1e-3f, ship.acceleration)) : 0f;
        float left = clearance(ship, edge) - stopping;
        return Math.max(0f, Math.min(1f, (margin - left) / margin));
    }

    /**
     * The velocity {@code (wantX, wantY)} with every edge's pressure on it: the part toward an edge taken off (all
     * of it a third of the way in, {@link #SHED_RATE}), then a push back in growing to {@link #PUSH_SHARE} of top speed. A ship the pressure
     * does not apply to gets its velocity back unchanged.
     *
     * @return {@code [x, y]}
     */
    public float[] steer(Warship ship, float wantX, float wantY) {
        if (!applies(ship)) return new float[]{wantX, wantY};
        float limit = Math.max(ship.maxSpeed, (float) Math.hypot(wantX, wantY));
        float x = wantX;
        float y = wantY;
        for (MapEdge e : MapEdge.values()) {
            float p = pressure(ship, e);
            if (p <= 0f) continue;
            float toward = x * e.outX + y * e.outY;
            float shed = toward > 0f ? toward * Math.min(1f, SHED_RATE * p) : 0f;
            float push = p * PUSH_SHARE * ship.maxSpeed;
            x -= e.outX * (shed + push);
            y -= e.outY * (shed + push);
        }
        float s = (float) Math.hypot(x, y);
        if (s > limit && s > 0f) {
            x *= limit / s;
            y *= limit / s;
        }
        return new float[]{x, y};
    }

    /**
     * A station for {@code ship} at {@code (x, y)}, kept out of its margin: at least half its length and its margin
     * inside every edge, or on the middle line of a map too narrow for both. With the pressure off it keeps the old
     * hard margin of half a hull and {@link WarshipDuelHelm#EDGE_MARGIN_CELLS}.
     *
     * @return {@code [x, y]}
     */
    public float[] settle(Warship ship, float x, float y) {
        float keep = 0.5f * ship.lengthCells + (ENABLED ? margin(ship) : WarshipDuelHelm.EDGE_MARGIN_CELLS);
        return new float[]{clampAxis(x, width, keep), clampAxis(y, height, keep)};
    }

    /**
     * Holds a flown hull on the map, whether or not the pressure applies to it (an escort keeping its screen place is
     * held too): a centre past an edge is put back on it and its way off cut. A hull leaving across its edge is held
     * off the other three, since its own edge is its only way off.
     */
    public void contain(Warship ship) {
        MapEdge free;
        if (flown(ship)) {
            free = null;
        } else if (ENABLED && ship.leavingAcrossEdge() && ship.exitEdge() != null && !ship.destroyed()
                && !ship.fighterSize()) {
            free = ship.exitEdge();
        } else {
            return;
        }
        float dx = ship.centerX() - ship.x;
        float dy = ship.centerY() - ship.y;
        float cx = ship.centerX();
        float cy = ship.centerY();
        if (cx < 0f && free != MapEdge.WEST) { ship.x = -dx; ship.velocityX = Math.max(0f, ship.velocityX); }
        if (cx > width && free != MapEdge.EAST) { ship.x = width - dx; ship.velocityX = Math.min(0f, ship.velocityX); }
        if (cy < 0f && free != MapEdge.SOUTH) { ship.y = -dy; ship.velocityY = Math.max(0f, ship.velocityY); }
        if (cy > height && free != MapEdge.NORTH) { ship.y = height - dy; ship.velocityY = Math.min(0f, ship.velocityY); }
    }

    private static float clampAxis(float v, float size, float keep) {
        if (size <= 2f * keep) return 0.5f * size;
        return Math.max(keep, Math.min(size - keep, v));
    }
}
