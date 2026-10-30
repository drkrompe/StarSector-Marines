package com.dillon.starsectormarines.battle.vehicle;

/**
 * A body running into a hull, as a rigid-body contact ({@code colony-breach-nouns.md}, Brute): the striker is a point
 * mass, the hull a rectangle about its centre of gravity, and the two exchange one impulse at the point where
 * the striker meets the hull's footprint, along that face's normal. Momentum is conserved and the bounce is
 * set by a restitution, so how far the hull goes, and how much it turns, follows from where it was struck,
 * how heavy it is against the striker, and how it was already moving; the rest of the striker's momentum
 * comes back to the striker as a rebound. What the hull then does with its velocity, sliding against its
 * tires or tracks, is {@link HullSlide}'s.
 *
 * <p>Frame: cells, x right, y up; facing 0 is +y, counter-clockwise positive; angular velocity in radians a
 * second, counter-clockwise positive. Pure.
 */
public final class HullImpact {

    /**
     * The exchange. {@code hullVx/Vy/Omega} are the hull's changes in velocity and spin; {@code strikerVx/Vy}
     * the striker's change in velocity; {@code impulse} the impulse's size (mass x cells a second), zero when
     * the two were already parting; {@code contactX/Y} where on the ground they met.
     */
    public record Result(float hullVx, float hullVy, float hullOmega, float strikerVx, float strikerVy,
                         float impulse, float contactX, float contactY) {
        static final Result NONE = new Result(0f, 0f, 0f, 0f, 0f, 0f, Float.NaN, Float.NaN);
    }

    private HullImpact() {}

    /**
     * @param hx, hy            hull centre of gravity
     * @param facing            hull facing, degrees
     * @param halfLength, halfWidth hull footprint half extents, cells
     * @param hullMass          hull mass, in the striker's units
     * @param hvx, hvy, homega  hull velocity and spin before the hit
     * @param sx, sy            striker position
     * @param svx, svy          striker velocity
     * @param strikerMass       striker mass
     * @param restitution       0 for a dead stop together, 1 for a perfect bounce
     */
    public static Result strike(float hx, float hy, float facing, float halfLength, float halfWidth, float hullMass,
                                float hvx, float hvy, float homega,
                                float sx, float sy, float svx, float svy, float strikerMass, float restitution) {
        double rad = Math.toRadians(facing);
        float fx = (float) -Math.sin(rad), fy = (float) Math.cos(rad);   // forward
        float rx = fy, ry = -fx;                                         // right
        float dx = sx - hx, dy = sy - hy;
        float lf = dx * fx + dy * fy, lr = dx * rx + dy * ry;          // striker in the hull frame
        float cf = clamp(lf, halfLength), cr = clamp(lr, halfWidth);
        float nf, nr;
        if (cf != lf || cr != lr) {                                     // outside: the nearest point of the footprint
            nf = lf - cf;
            nr = lr - cr;
        } else if (halfLength - Math.abs(lf) < halfWidth - Math.abs(lr)) { // inside: out through the nearer face
            cf = Math.copySign(halfLength, lf);
            nf = Math.signum(lf) == 0f ? 1f : Math.signum(lf);
            nr = 0f;
        } else {
            cr = Math.copySign(halfWidth, lr);
            nr = Math.signum(lr) == 0f ? 1f : Math.signum(lr);
            nf = 0f;
        }
        float nl = (float) Math.hypot(nf, nr);
        if (nl < 1e-6f) return Result.NONE;
        nf /= nl;
        nr /= nl;
        float nx = nf * fx + nr * rx, ny = nf * fy + nr * ry;           // outward from the hull, toward the striker
        float px = cf * fx + cr * rx, py = cf * fy + cr * ry;           // contact, from the centre of gravity

        float inertia = hullMass * (4f * halfLength * halfLength + 4f * halfWidth * halfWidth) / 12f;
        float cvx = hvx - homega * py, cvy = hvy + homega * px;         // hull velocity at the contact
        float vn = (svx - cvx) * nx + (svy - cvy) * ny;
        if (vn >= 0f) return Result.NONE;                               // already parting
        float rn = px * ny - py * nx;
        float j = -(1f + restitution) * vn / (1f / strikerMass + 1f / hullMass + rn * rn / inertia);
        // The striker takes +j along the normal; the hull -j at the contact.
        return new Result(-j * nx / hullMass, -j * ny / hullMass, -j * rn / inertia,
                j * nx / strikerMass, j * ny / strikerMass, j, hx + px, hy + py);
    }

    /**
     * A blast at {@code (bx, by)} pushing the hull with an impulse of {@code impulse} (mass x cells a second): it
     * acts at the point of the footprint nearest the blast, away from the blast, so a burst off a hull's corner
     * turns it as well as pushing it. A burst inside the footprint (a hit on the deck) pushes from where it landed
     * toward the centre and does not turn it; one dead on the centre pushes along {@code (fallbackX, fallbackY)},
     * a unit vector. There is no striker, so the striker's fields are zero.
     */
    public static Result blast(float hx, float hy, float facing, float halfLength, float halfWidth, float hullMass,
                               float bx, float by, float impulse, float fallbackX, float fallbackY) {
        if (!(impulse > 0f)) return Result.NONE;
        double rad = Math.toRadians(facing);
        float fx = (float) -Math.sin(rad), fy = (float) Math.cos(rad);
        float rx = fy, ry = -fx;
        float dx = bx - hx, dy = by - hy;
        float lf = dx * fx + dy * fy, lr = dx * rx + dy * ry;
        float cf = clamp(lf, halfLength), cr = clamp(lr, halfWidth);
        float px = cf * fx + cr * rx, py = cf * fy + cr * ry;           // where it acts, from the centre of gravity
        float nx, ny;
        if (cf != lf || cr != lr) {                                     // outside: from the blast to the footprint
            nx = px - dx;
            ny = py - dy;
        } else {                                                        // on the deck: toward the centre, no turn
            nx = -dx;
            ny = -dy;
            px = 0f;
            py = 0f;
        }
        float nl = (float) Math.hypot(nx, ny);
        if (nl < 1e-5f) {
            nx = fallbackX;
            ny = fallbackY;
        } else {
            nx /= nl;
            ny /= nl;
        }
        float inertia = hullMass * (4f * halfLength * halfLength + 4f * halfWidth * halfWidth) / 12f;
        float torque = (px * ny - py * nx) * impulse;
        return new Result(impulse * nx / hullMass, impulse * ny / hullMass, torque / inertia,
                0f, 0f, impulse, hx + px, hy + py);
    }

    /** How far {@code (px, py)} stands from the hull's footprint, cells; zero inside it. */
    public static float surfaceDistance(float hx, float hy, float facing, float halfLength, float halfWidth,
                                        float px, float py) {
        double rad = Math.toRadians(facing);
        float fx = (float) -Math.sin(rad), fy = (float) Math.cos(rad);
        float dx = px - hx, dy = py - hy;
        float lf = Math.abs(dx * fx + dy * fy) - halfLength;
        float lr = Math.abs(dx * fy - dy * fx) - halfWidth;
        return (float) Math.hypot(Math.max(0f, lf), Math.max(0f, lr));
    }

    private static float clamp(float v, float half) {
        return Math.max(-half, Math.min(half, v));
    }
}
