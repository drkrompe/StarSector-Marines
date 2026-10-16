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
 * <p><b>Contact</b> ({@link #separate}) is physical and soft, and holds for every pair of hulls of frigate size and up
 * over the battle, friend or foe: two footprints that overlap ease apart under a separating force that respects their
 * momentum, the smaller hull giving more way (a hull its mission holds in place gives none, {@link #yields}).
 * Fighter-sized hulls pass over and under ships freely; a falling wreck is no longer a ship over the battle. Nothing is
 * damaged; ramming is not modelled. <b>Clearance</b> ({@link #clearStation}, {@link #avoid}) is the helm's: a ship fighting hulls or escorting
 * keeps {@link #clearance} of open air from every friendly hull, by moving its station clear of a friend's and by
 * bending its course away from any hull, friend or foe, it would close inside that clearance.
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
    static final float FORMATION_TOUCH_CELLS = 2f;
    /** Separating acceleration, in cells a second squared, for each cell two hulls overlap. */
    static final float CONTACT_STIFFNESS = 1.5f;
    /** The most separating acceleration contact gives, in cells a second squared. */
    static final float CONTACT_MAX_ACCELERATION = 6f;
    /** How fast contact spends the speed two overlapping hulls are still closing at, per second. */
    static final float CONTACT_DAMPING = 4f;
    /** The fastest, as a share of the slower yielding hull's top speed, contact parts two hulls. */
    static final float CONTACT_PARTING_SHARE = 0.5f;
    /** An overlap deeper than this share of the narrower beam is also corrected in position, as a backstop. */
    static final float CONTACT_HARD_SHARE = 0.05f;
    /** The share of its top speed above which a hull is under way and contact may move it, whatever is flying it. */
    static final float UNDER_WAY_SHARE = 0.25f;

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
        return separation(ax, ay, aFacing, aLength, aWidth, bx, by, bFacing, bLength, bWidth)[0];
    }

    /**
     * The separating-axis gap and the axis it is measured on: {@code [gap, nx, ny]}, the unit axis pointing from the
     * first hull toward the second. For two overlapping hulls it is the axis of least overlap, the way out that is
     * shortest; for two apart, the axis that holds them apart.
     */
    static float[] separation(float ax, float ay, float aFacing, float aLength, float aWidth,
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
        float bnx = 1f;
        float bny = 0f;
        for (int i = 0; i < 4; i++) {
            float nx = axes[2 * i];
            float ny = axes[2 * i + 1];
            float along = dx * nx + dy * ny;
            float sep = Math.abs(along) - extent(axes[0], axes[1], axes[2], axes[3], aLength, aWidth, nx, ny)
                    - extent(axes[4], axes[5], axes[6], axes[7], bLength, bWidth, nx, ny);
            if (sep > best) {
                best = sep;
                float sign = along >= 0f ? 1f : -1f;
                bnx = nx * sign;
                bny = ny * sign;
            }
        }
        return new float[]{best, bnx, bny};
    }

    /** {@link #separation} of two hulls where they are now. */
    static float[] separation(Warship a, Warship b) {
        return separation(a.centerX(), a.centerY(), a.facingDegrees, a.lengthCells, a.widthCells,
                b.centerX(), b.centerY(), b.facingDegrees, b.lengthCells, b.widthCells);
    }

    /** Half the projection of a rectangle (nose axis {@code (lx, ly)}, beam axis {@code (wx, wy)}) onto {@code n}. */
    private static float extent(float lx, float ly, float wx, float wy, float length, float width, float nx, float ny) {
        return 0.5f * length * Math.abs(lx * nx + ly * ny) + 0.5f * width * Math.abs(wx * nx + wy * ny);
    }

    /** The least overlap, in cells, evidence forgives as a brush between two hulls of any size. */
    public static final float BRUSH_CELLS = 2f;

    /**
     * How deep two hulls may lie in each other, in cells, before contact has failed: the depth at which the
     * positional backstop takes over ({@link #CONTACT_HARD_SHARE} of the narrower beam), never less than
     * {@link #BRUSH_CELLS}. A hull turning beside another sweeps its corners into it faster than any push can
     * part them; that much the backstop holds, and no more.
     */
    public static float contactTolerance(Warship a, Warship b) {
        return Math.max(BRUSH_CELLS, CONTACT_HARD_SHARE * Math.min(a.widthCells, b.widthCells));
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
     * leaving, piloted or passing low), or any hull under way at more than {@link #UNDER_WAY_SHARE} of its top speed.
     * A hull a mission holds at rest on its station does not yield; two such hulls a mission has laid over each other
     * are the mission's to place, and contact leaves them.
     */
    static boolean yields(Warship ship) {
        // A hull on its lift run climbs on the run's own line; the air it is climbing into gives way to it.
        if (ship.liftRunning()) return false;
        if ((ship.hullTarget() != null && ship.duelHelm()) || WarshipEscort.liveLead(ship) != null
                || ship.departing() || ship.piloted() || ship.lowPass() != null) {
            return true;
        }
        float speed = (float) Math.hypot(ship.velocityX, ship.velocityY);
        return speed > UNDER_WAY_SHARE * ship.maxSpeed;
    }

    /**
     * Whether a hull is physically in the air over the battle, so it can touch another: not destroyed or gone, not
     * still in the cloud deck, not sitting on (or lifting off) its berth, and not flying a low hover over the ground.
     */
    public static boolean inContact(Warship ship) {
        return !ship.fighterSize() && !ship.destroyed() && !ship.departed() && !ship.descending() && !ship.berthed()
                && !ship.liftRunning() && !ship.lowHovering();
    }

    /**
     * Whether a hull is a body other hulls must keep clear of: one {@link #inContact in contact}, or one climbing off
     * its berth on a lift run, which is in the air its escorts are flying through even though nothing moves it.
     */
    static boolean obstructs(Warship ship) {
        return inContact(ship) || (ship.liftRunning() && !ship.fighterSize() && !ship.destroyed() && !ship.departed());
    }

    /** Whether {@code other} is a friendly hull {@code ship}'s helm keeps clear of. */
    private static boolean friend(Warship ship, Warship other) {
        return other != ship && inContact(other) && ship.faction.friendlyTo(other.faction);
    }

    /**
     * Soft contact between every pair of hulls whose footprints overlap: a separating acceleration along the line
     * between their centres, growing with how deep they overlap ({@link #CONTACT_STIFFNESS}, at most
     * {@link #CONTACT_MAX_ACCELERATION}), and a damping of the speed with which they are still closing, shared by
     * footprint area so the smaller hull gives more way. It acts on their velocities, so the hulls carry their momentum
     * and ease apart over a second or two rather than being stopped or moved. Only an overlap deeper than
     * {@link #CONTACT_HARD_SHARE} of the narrower beam is also corrected in position, as a backstop. A hull its mission
     * holds in place gives no way ({@link #yields}); fighter-sized hulls and falling wrecks take no part.
     */
    static void separate(List<Warship> ships, float dt) {
        if (!ENABLED) return;
        for (int i = 0; i < ships.size(); i++) {
            Warship a = ships.get(i);
            if (!obstructs(a)) continue;
            for (int j = i + 1; j < ships.size(); j++) {
                Warship b = ships.get(j);
                if (!obstructs(b)) continue;
                float g = gap(a, b);
                if (g >= 0f || (!yields(a) && !yields(b))) continue;
                push(a, b, -g, dt);
            }
        }
    }

    private static void push(Warship a, Warship b, float depth, float dt) {
        // Along the axis of least overlap: two hulls side by side part across their beams, not along the line
        // between their centres, which for long hulls points half along their length.
        float[] sep = separation(a, b);
        float nx = sep[1];
        float ny = sep[2];
        boolean ya = yields(a);
        boolean yb = yields(b);
        float ma = a.lengthCells * a.widthCells;
        float mb = b.lengthCells * b.widthCells;
        // Between two hulls under their own helms the smaller gives more way; a hull held by its mission gives none.
        float shareA = !yb ? 1f : !ya ? 0f : mb / (ma + mb);
        float shareB = 1f - shareA;
        float push = Math.min(CONTACT_MAX_ACCELERATION, CONTACT_STIFFNESS * depth) * dt;
        float closing = (a.velocityX - b.velocityX) * nx + (a.velocityY - b.velocityY) * ny;
        float damp = closing > 0f ? closing * Math.min(1f, CONTACT_DAMPING * dt) : 0f;
        // Contact parts two hulls gently: never faster than a share of the slower yielding hull's own top speed.
        float slowest = Math.min(ya ? a.maxSpeed : Float.MAX_VALUE, yb ? b.maxSpeed : Float.MAX_VALUE);
        float parting = Math.max(0f, -closing);
        float room = Math.max(0f, CONTACT_PARTING_SHARE * slowest - parting);
        float dv = Math.min(push, room) + damp;
        a.velocityX -= nx * dv * shareA;
        a.velocityY -= ny * dv * shareA;
        b.velocityX += nx * dv * shareB;
        b.velocityY += ny * dv * shareB;
        float hard = CONTACT_HARD_SHARE * Math.min(a.widthCells, b.widthCells);
        if (depth > hard) {
            float step = depth - hard;
            a.x -= nx * step * shareA;
            a.y -= ny * step * shareA;
            b.x += nx * step * shareB;
            b.y += ny * step * shareB;
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
     * The velocity {@code ship} wants, {@code (wantX, wantY)} in cells a second, bent away from any other hull it
     * is inside its {@link #clearance} of now, or would close inside it within {@code horizonSeconds} (at most
     * {@link #LOOKAHEAD_SECONDS} or twice the ship's time to its top speed, whichever is longer; the caller's time to
     * its station) on both ships' present velocities. The push is across the line to the friend, stronger the deeper inside the clearance,
     * and never more than the ship's top speed.
     *
     * @return {@code [vx, vy]}
     */
    static float[] avoid(Warship ship, List<Warship> ships, float wantX, float wantY, float horizonSeconds) {
        // A heavy hull looks as far ahead as it takes to change its way: twice the time to reach its top speed.
        float reaction = ship.acceleration > 0f ? 2f * ship.maxSpeed / ship.acceleration : 0f;
        float reach = Math.max(LOOKAHEAD_SECONDS, reaction);
        float lookahead = Math.max(0f, Math.min(reach, horizonSeconds));
        if (!ENABLED) return new float[]{wantX, wantY};
        float want = clearance(ship);
        float pushX = 0f;
        float pushY = 0f;
        for (Warship other : ships) {
            // The course keeps clear of every hull, friend or foe: contact is the backstop, not the behaviour.
            if (other == ship || !obstructs(other)) continue;
            float rx = other.centerX() - ship.centerX();
            float ry = other.centerY() - ship.centerY();
            float vx = other.velocityX - wantX;
            float vy = other.velocityY - wantY;
            float vv = vx * vx + vy * vy;
            float t = vv < 1e-6f ? 0f : Math.max(0f, Math.min(lookahead, -(rx * vx + ry * vy) / vv));
            float px = rx + vx * t;
            float py = ry + vy * t;
            float[] now = separation(ship, other);
            float[] then = separation(0f, 0f, ship.facingDegrees, ship.lengthCells, ship.widthCells,
                    px, py, other.facingDegrees, other.lengthCells, other.widthCells);
            float gNow = now[0];
            float gThen = then[0];
            float g = Math.min(gNow, gThen);
            // A formation is spaced by its own geometry; within it the helm keeps a narrower gap.
            float keep = formation(ship, other) ? FORMATION_TOUCH_CELLS : want;
            if (g >= keep) continue;
            float depth = Math.min(1f, (keep - g) / keep);
            // The axis between them where they are closest: now, or at closest approach.
            float[] at = gNow < gThen ? now : then;
            float nx = at[1];
            float ny = at[2];
            // First, stop closing: the part of the course that runs at the other hull faster than it is moving
            // away is taken out, so a ship slides along a hull rather than pressing into it.
            float closing = (wantX - other.velocityX) * nx + (wantY - other.velocityY) * ny;
            if (closing > 0f) {
                wantX -= nx * closing * depth;
                wantY -= ny * closing * depth;
            }
            float ax = -nx;
            float ay = -ny;
            float len = 1f;
            // The near-term threat counts more than the far one.
            float urgency = t <= 0f ? 1f : 1f - 0.5f * t / reach;
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
