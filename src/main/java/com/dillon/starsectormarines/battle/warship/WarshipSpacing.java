package com.dillon.starsectormarines.battle.warship;

import java.util.List;

/**
 * Open air between hulls ({@code warship-tactics.md}, spacing and contact): how far apart two hulls' footprints are,
 * the contact rule that keeps any two hulls from passing through each other, and the clearance a ship's helm keeps
 * from its friends.
 *
 * <p>A hull's footprint is its sprite rectangle about its sprite centre ({@link Warship#distanceTo}), turned with its
 * heading: length along the nose, width across it. The gap between two footprints is read by separating axes: the
 * largest separation of the two rectangles' projections onto either hull's own axes. It is positive when they are
 * apart (a lower bound on the true distance, exact when two faces look at each other), and negative when they
 * overlap, by the shallowest depth either would have to move to clear the other.
 *
 * <p><b>Contact</b> ({@link #separate}) is physical and holds for every pair of hulls over the battle, friend or foe:
 * two footprints that overlap are pushed apart along the line between their centres, the smaller hull moving more
 * (a hull its mission holds in place does not move, {@link #yields}), and the speed with which they were closing
 * along that line is taken out. Nothing is damaged; ramming is not
 * modelled. <b>Clearance</b> ({@link #clearStation}, {@link #avoid}) is the helm's: a ship fighting hulls or escorting
 * keeps {@link #clearance} of open air from every friendly hull, by moving its station clear of a friend's and by
 * bending its course away from a friend it would close inside that clearance.
 * {@code -Dbattle.warship.hullContact=false} is the control that restores hulls passing through one another.
 */
public final class WarshipSpacing {

    /** {@code -Dbattle.warship.hullContact=false} turns contact and clearance off. */
    public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("battle.warship.hullContact"));
    /** The clearance a helm keeps from a friendly hull, in cells: the screen line's own gap between hulls. */
    static final float CLEARANCE_CELLS = WarshipEscort.GAP_CELLS;
    /** How far ahead, in seconds, a helm looks for a friend it would close inside its clearance. */
    static final float LOOKAHEAD_SECONDS = 10f;
    /** A station this close to the clearance is left where it is, so a ship settles rather than creeping. */
    static final float STATION_TOLERANCE_CELLS = 0.5f;
    /** Within a formation (an escort's screen), the gap under which a helm bends away from a peer. */
    static final float FORMATION_TOUCH_CELLS = 1f;
    /** Separation passes per tick, so a knot of three hulls settles rather than trading overlaps. */
    static final int CONTACT_PASSES = 3;

    private WarshipSpacing() {}

    /** The gap, in cells, between the footprints of {@code a} and {@code b}; negative when they overlap. */
    public static float gap(Warship a, Warship b) {
        return gap(a.centerX(), a.centerY(), a.facingDegrees, a.lengthCells, a.widthCells,
                b.centerX(), b.centerY(), b.facingDegrees, b.lengthCells, b.widthCells);
    }

    /**
     * The separating-axis gap between two hull rectangles, each given by its centre, its facing (the hull convention,
     * nose along {@code (-sin, cos)}), its length and its width.
     */
    public static float gap(float ax, float ay, float aFacing, float aLength, float aWidth,
                            float bx, float by, float bFacing, float bLength, float bWidth) {
        double ra = Math.toRadians(aFacing);
        double rb = Math.toRadians(bFacing);
        float[] axes = {
                (float) -Math.sin(ra), (float) Math.cos(ra),
                (float) Math.cos(ra), (float) Math.sin(ra),
                (float) -Math.sin(rb), (float) Math.cos(rb),
                (float) Math.cos(rb), (float) Math.sin(rb)};
        float dx = bx - ax;
        float dy = by - ay;
        float best = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            float nx = axes[2 * i];
            float ny = axes[2 * i + 1];
            float d = Math.abs(dx * nx + dy * ny);
            float sep = d - extent(axes[0], axes[1], axes[2], axes[3], aLength, aWidth, nx, ny)
                    - extent(axes[4], axes[5], axes[6], axes[7], bLength, bWidth, nx, ny);
            best = Math.max(best, sep);
        }
        return best;
    }

    /** Half the projection of a rectangle (nose axis {@code (lx, ly)}, beam axis {@code (wx, wy)}) onto {@code n}. */
    private static float extent(float lx, float ly, float wx, float wy, float length, float width, float nx, float ny) {
        return 0.5f * length * Math.abs(lx * nx + ly * ny) + 0.5f * width * Math.abs(wx * nx + wy * ny);
    }

    /**
     * The open air a ship's helm keeps from a friendly hull: the screen line's gap, so an escort in its place beside its
     * charge is already clear and two hunters never close inside it.
     */
    public static float clearance(Warship ship) {
        return CLEARANCE_CELLS;
    }

    /**
     * Whether contact may move a hull: one its own helm is flying (fighting a hull with its duel helm, escorting,
     * leaving, piloted or passing low). A hull a mission holds on its station, route or order does not yield; two
     * such hulls a mission has laid over each other are the mission's to place, and contact leaves them.
     */
    static boolean yields(Warship ship) {
        return (ship.hullTarget() != null && ship.duelHelm()) || WarshipEscort.liveLead(ship) != null
                || ship.departing() || ship.piloted() || ship.lowPass() != null;
    }

    /**
     * Whether a hull is physically in the air over the battle, so it can touch another: not destroyed or gone, not
     * still in the cloud deck, not sitting on (or lifting off) its berth, and not flying a low hover over the ground.
     */
    static boolean inContact(Warship ship) {
        return !ship.destroyed() && !ship.departed() && !ship.descending() && !ship.berthed()
                && !ship.liftRunning() && !ship.lowHovering();
    }

    /** Whether {@code other} is a friendly hull {@code ship}'s helm keeps clear of. */
    private static boolean friend(Warship ship, Warship other) {
        return other != ship && inContact(other) && ship.faction.friendlyTo(other.faction);
    }

    /**
     * Pushes apart every pair of hulls whose footprints overlap, along the line between their centres, the smaller
     * (by footprint area) moving more, and takes out the speed with which they were closing along that line.
     */
    static void separate(List<Warship> ships) {
        if (!ENABLED) return;
        for (int pass = 0; pass < CONTACT_PASSES; pass++) {
            boolean moved = false;
            for (int i = 0; i < ships.size(); i++) {
                Warship a = ships.get(i);
                if (!inContact(a)) continue;
                for (int j = i + 1; j < ships.size(); j++) {
                    Warship b = ships.get(j);
                    if (!inContact(b)) continue;
                    float g = gap(a, b);
                    if (g >= 0f || (!yields(a) && !yields(b))) continue;
                    push(a, b, -g);
                    moved = true;
                }
            }
            if (!moved) return;
        }
    }

    private static void push(Warship a, Warship b, float depth) {
        float dx = b.centerX() - a.centerX();
        float dy = b.centerY() - a.centerY();
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-3f) {
            // Dead on top of each other: part them across the first hull's beam, deterministically.
            double r = Math.toRadians(a.facingDegrees);
            dx = (float) Math.cos(r);
            dy = (float) Math.sin(r);
            len = 1f;
        }
        float nx = dx / len;
        float ny = dy / len;
        boolean ya = yields(a);
        boolean yb = yields(b);
        if (!ya && !yb) return;
        float ma = a.lengthCells * a.widthCells;
        float mb = b.lengthCells * b.widthCells;
        // Between two hulls under their own helms the smaller gives more way; a hull held by its mission gives none.
        float shareA = !yb ? 1f : !ya ? 0f : mb / (ma + mb);
        float shareB = 1f - shareA;
        float step = depth + 0.01f;
        a.x -= nx * step * shareA;
        a.y -= ny * step * shareA;
        b.x += nx * step * shareB;
        b.y += ny * step * shareB;
        // Inelastic along the normal: whatever they were closing at, they now share.
        float closing = (a.velocityX - b.velocityX) * nx + (a.velocityY - b.velocityY) * ny;
        if (closing > 0f) {
            a.velocityX -= nx * closing * shareA;
            a.velocityY -= ny * closing * shareA;
            b.velocityX += nx * closing * shareB;
            b.velocityY += ny * closing * shareB;
        }
    }

    /**
     * {@code (sx, sy)}, a station for {@code ship}'s centre of gravity, moved clear of every friendly hull's position
     * and station by {@link #clearance}, each judged at that hull's own heading. A few passes, so a station between
     * two friends settles between them.
     *
     * @return {@code [x, y]}
     */
    static float[] clearStation(Warship ship, List<Warship> ships, float sx, float sy) {
        if (!ENABLED) return new float[]{sx, sy};
        float ox = ship.centerX() - ship.x;
        float oy = ship.centerY() - ship.y;
        float want = clearance(ship);
        for (int pass = 0; pass < 3; pass++) {
            boolean moved = false;
            for (Warship other : ships) {
                if (!friend(ship, other) || formation(ship, other)) continue;
                float[] at = clearOf(sx + ox, sy + oy, ship, other.centerX(), other.centerY(), other, want);
                if (other.hasStation()) {
                    float sox = other.stationX() + (other.centerX() - other.x);
                    float soy = other.stationY() + (other.centerY() - other.y);
                    at = clearOf(at[0], at[1], ship, sox, soy, other, want);
                }
                if (at[0] != sx + ox || at[1] != sy + oy) {
                    sx = at[0] - ox;
                    sy = at[1] - oy;
                    moved = true;
                }
            }
            if (!moved) break;
        }
        return new float[]{sx, sy};
    }

    /**
     * Whether two friends keep a formation laid out clear by its own geometry: an escort and its charge, or two escorts
     * of one charge, whose screen line already spaces them by {@link WarshipEscort#GAP_CELLS}. Their stations are left
     * as the screen lays them; their courses still bend away from each other ({@link #avoid}).
     */
    private static boolean formation(Warship a, Warship b) {
        Warship la = WarshipEscort.liveLead(a);
        Warship lb = WarshipEscort.liveLead(b);
        return la == b || lb == a || (la != null && la == lb);
    }

    /** {@code (cx, cy)}, a centre for {@code ship}, moved straight away from {@code other} at {@code (ox, oy)} until clear. */
    private static float[] clearOf(float cx, float cy, Warship ship, float ox, float oy, Warship other, float want) {
        float g = gap(cx, cy, ship.facingDegrees, ship.lengthCells, ship.widthCells,
                ox, oy, other.facingDegrees, other.lengthCells, other.widthCells);
        if (g >= want - STATION_TOLERANCE_CELLS) return new float[]{cx, cy};
        float dx = cx - ox;
        float dy = cy - oy;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-3f) {
            // Exactly on the friend: step off across its beam, to the side the ship's serial names.
            double r = Math.toRadians(other.facingDegrees);
            float side = ship.serial() % 2 == 0 ? 1f : -1f;
            dx = side * (float) Math.cos(r);
            dy = side * (float) Math.sin(r);
            len = 1f;
        }
        float need = want - g;
        return new float[]{cx + dx / len * need, cy + dy / len * need};
    }

    /**
     * The velocity {@code ship} wants, {@code (wantX, wantY)} in cells a second, bent away from any friendly hull it
     * is inside its {@link #clearance} of now, or would close inside it within {@link #LOOKAHEAD_SECONDS} on both
     * ships' present velocities. The push is across the line to the friend, stronger the deeper inside the clearance,
     * and never more than the ship's top speed.
     *
     * @return {@code [vx, vy]}
     */
    static float[] avoid(Warship ship, List<Warship> ships, float wantX, float wantY) {
        if (!ENABLED) return new float[]{wantX, wantY};
        float want = clearance(ship);
        float pushX = 0f;
        float pushY = 0f;
        for (Warship other : ships) {
            if (!friend(ship, other)) continue;
            float rx = other.centerX() - ship.centerX();
            float ry = other.centerY() - ship.centerY();
            float vx = other.velocityX - wantX;
            float vy = other.velocityY - wantY;
            float vv = vx * vx + vy * vy;
            float t = vv < 1e-6f ? 0f : Math.max(0f, Math.min(LOOKAHEAD_SECONDS, -(rx * vx + ry * vy) / vv));
            float px = rx + vx * t;
            float py = ry + vy * t;
            float gNow = gap(ship, other);
            float gThen = gap(0f, 0f, ship.facingDegrees, ship.lengthCells, ship.widthCells,
                    px, py, other.facingDegrees, other.lengthCells, other.widthCells);
            float g = Math.min(gNow, gThen);
            // A formation is spaced by its own geometry; within it the helm only refuses an actual touch.
            float keep = formation(ship, other) ? FORMATION_TOUCH_CELLS : want;
            if (g >= keep) continue;
            float depth = Math.min(1f, (keep - g) / keep);
            // Away from where the friend will be at closest approach (or now, for one already too close).
            float ax = gNow < gThen ? -rx : -px;
            float ay = gNow < gThen ? -ry : -py;
            float len = (float) Math.hypot(ax, ay);
            if (len < 1e-3f) {
                double r = Math.toRadians(ship.facingDegrees);
                ax = (float) Math.cos(r);
                ay = (float) Math.sin(r);
                len = 1f;
            }
            // The near-term threat counts more than the far one.
            float urgency = t <= 0f ? 1f : 1f - 0.5f * t / LOOKAHEAD_SECONDS;
            pushX += ax / len * depth * urgency * ship.maxSpeed;
            pushY += ay / len * depth * urgency * ship.maxSpeed;
        }
        if (pushX == 0f && pushY == 0f) return new float[]{wantX, wantY};
        float vx = wantX + pushX;
        float vy = wantY + pushY;
        float speed = (float) Math.hypot(vx, vy);
        if (speed > ship.maxSpeed && speed > 0f) {
            vx *= ship.maxSpeed / speed;
            vy *= ship.maxSpeed / speed;
        }
        return new float[]{vx, vy};
    }
}
