package com.dillon.starsectormarines.battle.appearance;

import java.util.SplittableRandom;

/**
 * A Rav Bloom body bursting on death, for any rig that is drawn bone by bone ({@link RavRunnerRig},
 * {@link RavBruteRig}). The rig names its pieces as groups of its pose points: the trunk pieces first,
 * which slump apart slowly, then the rest. A non-trunk piece either stays on the trunk piece it is
 * attached to or comes away: the ones with no parent always come away (a head), and one or two of the
 * candidates (the limbs) do too. Every piece that comes away is thrown out from the middle and back (the
 * blow that killed it came from ahead), spins, bounces once and lies flat where it lands. Ichor sprays
 * out in droplets that land as splats round a pool spreading under the middle.
 *
 * <p>All in the rig frame (x right, y forward, z up, cells). Seeded, so every replay bursts the same
 * way. Presentation only.
 */
public final class RavBurst {

    /** Gravity on the pieces and ichor, cells/s^2, and how hard the burst throws them back. */
    private static final float GRAVITY = 12f;
    private static final float BURST_BACK = 0.8f;
    /** The pieces go limp and lie flat over this; the pool spreads over the next. */
    private static final float FLATTEN_SECONDS = 0.45f;
    private static final float POOL_SECONDS = 0.6f;
    /** After this nothing moves any more. */
    public static final float SETTLE_SECONDS = 2.5f;
    private static final int DROPS = 9;
    /** Fields of a piece's or a droplet's state. */
    private static final int P_X = 0, P_Y = 1, P_Z = 2, P_VX = 3, P_VY = 4, P_VZ = 5, P_SPIN = 6, P_SPIN_RATE = 7,
            P_PIVOT_X = 8, P_PIVOT_Y = 9, P_PIVOT_Z = 10, P_BOUNCED = 11, P_RESTING = 12, P_SIZE = 13, P_VARIANT = 14,
            P_FIELDS = 15;

    private final int[][] groups;
    private final int trunkPieces;
    private final int[] parent;
    private final int[] candidates;
    private final float poolSize;

    private float[] alivePose;
    private boolean started;
    private float sinceDeath;
    private final float[][] piece;
    /** The piece each piece moves with: itself when it came away, its trunk piece when it stayed on. */
    private final int[] carriedBy;
    private final float[][] drops = new float[DROPS][P_FIELDS];
    private float midX, midY;
    private float poolAngle;
    private int poolVariant;

    /**
     * @param groups      each piece's pose points, trunk pieces first
     * @param trunkPieces how many of the first pieces are the trunk
     * @param parent      per piece, the trunk piece it stays on unless it comes away, or -1 for one that always
     *                    comes away (and for the trunk pieces themselves)
     * @param candidates  the pieces of which one or two come away
     * @param poolCells   how wide the pool under the middle spreads, cells
     */
    public RavBurst(int[][] groups, int trunkPieces, int[] parent, int[] candidates, float poolCells) {
        this.groups = groups;
        this.trunkPieces = trunkPieces;
        this.parent = parent;
        this.candidates = candidates;
        this.poolSize = poolCells;
        this.piece = new float[groups.length][P_FIELDS];
        this.carriedBy = new int[groups.length];
    }

    /**
     * Sets every piece and droplet flying from {@code pose}, the rig's pose as it died (copied), bursting
     * from {@code (midX, midY)}.
     */
    public void start(float[] pose, float midX, float midY, long seed) {
        alivePose = pose.clone();
        this.midX = midX;
        this.midY = midY;
        started = true;
        sinceDeath = 0f;
        SplittableRandom rng = new SplittableRandom(seed * 0x9E3779B97F4A7C15L + 17L);
        int loose = 1 + rng.nextInt(2);
        for (int i = 0; i < groups.length; i++) carriedBy[i] = parent[i] < 0 ? i : parent[i];
        for (int n = 0; n < loose; n++) carriedBy[candidates[rng.nextInt(candidates.length)]] = -1;
        for (int i = 0; i < groups.length; i++) if (carriedBy[i] == -1) carriedBy[i] = i;
        for (int i = 0; i < groups.length; i++) {
            float[] q = piece[i];
            int[] points = groups[i];
            float px = 0f, py = 0f, pz = 0f;
            for (int point : points) {
                px += alivePose[point * 3];
                py += alivePose[point * 3 + 1];
                pz += alivePose[point * 3 + 2];
            }
            px /= points.length;
            py /= points.length;
            pz /= points.length;
            q[P_PIVOT_X] = px;
            q[P_PIVOT_Y] = py;
            q[P_PIVOT_Z] = pz;
            q[P_X] = px;
            q[P_Y] = py;
            q[P_Z] = pz;
            boolean trunk = i < trunkPieces;
            float ox = px - midX, oy = py - midY;
            float on = (float) Math.hypot(ox, oy);
            double a = rng.nextDouble(0, Math.PI * 2);
            float dirX = on > 0.02f ? ox / on : (float) Math.cos(a);
            float dirY = on > 0.02f ? oy / on : (float) Math.sin(a);
            // thrown out from the middle, and back: the blow that killed it came from ahead
            dirX += (float) rng.nextDouble(-0.45, 0.45);
            dirY += (float) rng.nextDouble(-0.45, 0.45) - BURST_BACK;
            float dn = Math.max(1e-4f, (float) Math.hypot(dirX, dirY));
            float speed = trunk ? (float) rng.nextDouble(0.15, 0.35) : (float) rng.nextDouble(0.6, 1.2);
            q[P_VX] = dirX / dn * speed;
            q[P_VY] = dirY / dn * speed;
            q[P_VZ] = trunk ? (float) rng.nextDouble(0.4, 0.7) : (float) rng.nextDouble(0.9, 1.4);
            float spin = trunk ? (float) rng.nextDouble(20, 80) : (float) rng.nextDouble(150, 420);
            q[P_SPIN_RATE] = rng.nextBoolean() ? spin : -spin;
        }
        for (float[] d : drops) {
            double a = rng.nextDouble(0, Math.PI * 2);
            float speed = (float) rng.nextDouble(0.5, 1.8);
            d[P_X] = midX + (float) rng.nextDouble(-0.08, 0.08);
            d[P_Y] = midY + (float) rng.nextDouble(-0.15, 0.15);
            d[P_Z] = 0.3f;
            d[P_VX] = (float) Math.cos(a) * speed;
            d[P_VY] = (float) Math.sin(a) * speed - BURST_BACK * 0.6f * speed;
            d[P_VZ] = (float) rng.nextDouble(0.6, 2.4);
            d[P_SIZE] = (float) rng.nextDouble(0.08, 0.18);
            d[P_SPIN] = (float) rng.nextDouble(0, 360);
            d[P_VARIANT] = rng.nextInt(4);
        }
        poolAngle = (float) rng.nextDouble(0, 360);
        poolVariant = rng.nextInt(4);
    }

    /** Whether {@link #start} has run. */
    public boolean started() { return started; }

    /** Moves every piece and droplet one tick, until everything has settled. */
    public void advance(float dt) {
        if (!started || settled()) return;
        sinceDeath += dt;
        for (int i = 0; i < groups.length; i++) {
            float[] q = piece[i];
            if (q[P_RESTING] > 0f || carriedBy[i] != i) continue;
            fly(q, dt, i < trunkPieces ? 0.25f : 0.35f);
            q[P_SPIN] += q[P_SPIN_RATE] * dt;
        }
        for (float[] d : drops) {
            if (d[P_RESTING] > 0f) continue;
            d[P_VZ] -= GRAVITY * dt;
            d[P_X] += d[P_VX] * dt;
            d[P_Y] += d[P_VY] * dt;
            d[P_Z] += d[P_VZ] * dt;
            if (d[P_Z] <= 0f) {
                d[P_Z] = 0f;
                d[P_RESTING] = 1f;
            }
        }
    }

    /** One ballistic step for a piece; it bounces once, losing most of its speed, then rests. */
    private static void fly(float[] q, float dt, float restitution) {
        q[P_VZ] -= GRAVITY * dt;
        q[P_X] += q[P_VX] * dt;
        q[P_Y] += q[P_VY] * dt;
        q[P_Z] += q[P_VZ] * dt;
        if (q[P_Z] > 0f || q[P_VZ] > 0f) return;
        q[P_Z] = 0f;
        if (q[P_BOUNCED] == 0f && -q[P_VZ] > 0.6f) {
            q[P_BOUNCED] = 1f;
            q[P_VZ] = -q[P_VZ] * restitution;
            q[P_VX] *= 0.45f;
            q[P_VY] *= 0.45f;
            q[P_SPIN_RATE] *= 0.4f;
        } else {
            q[P_RESTING] = 1f;
        }
    }

    /**
     * Where {@code point} lies now in the rig frame as part of the given piece, offset by (addX, addY) in the
     * pose it died in; out gets x, y, z.
     */
    public void deadPoint(int pieceIndex, int point, float addX, float addY, float[] out) {
        float[] q = piece[carriedBy[pieceIndex]];
        float lx = alivePose[point * 3] + addX - q[P_PIVOT_X];
        float ly = alivePose[point * 3 + 1] + addY - q[P_PIVOT_Y];
        float lz = (alivePose[point * 3 + 2] - q[P_PIVOT_Z]) * (1f - flat());
        double r = Math.toRadians(q[P_SPIN]);
        float c = (float) Math.cos(r), sn = (float) Math.sin(r);
        out[0] = q[P_X] + lx * c - ly * sn;
        out[1] = q[P_Y] + lx * sn + ly * c;
        out[2] = Math.max(0f, q[P_Z] + lz);
    }

    /** The turn a piece has taken since the burst, degrees counter-clockwise. */
    public float pieceSpin(int i) { return piece[carriedBy[i]][P_SPIN]; }

    /** 0 at the burst, 1 once the pieces have gone limp and lie flat on the ground. */
    public float flat() { return RavRunnerRig.smooth(sinceDeath / FLATTEN_SECONDS); }

    /** Seconds since the burst. */
    public float sinceDeath() { return sinceDeath; }

    /** Whether everything has come to rest and nothing will move again: a corpse that can be baked. */
    public boolean settled() { return started && sinceDeath > SETTLE_SECONDS; }

    public int pieceCount() { return groups.length; }

    /** Whether piece i is part of the trunk. */
    public boolean trunk(int i) { return i < trunkPieces; }

    /** Where piece i lies in the rig frame, and whether it came away and has come to rest. */
    public float pieceX(int i) { return piece[i][P_X]; }
    public float pieceY(int i) { return piece[i][P_Y]; }
    public boolean pieceResting(int i) { return carriedBy[i] == i && piece[i][P_RESTING] > 0f; }

    public int dropCount() { return drops.length; }

    /** Droplet i in the rig frame, whether it has landed, and the size in cells of its splat. */
    public float dropX(int i) { return drops[i][P_X]; }
    public float dropY(int i) { return drops[i][P_Y]; }
    public float dropZ(int i) { return drops[i][P_Z]; }
    public boolean dropLanded(int i) { return drops[i][P_RESTING] > 0f; }
    public float dropSize(int i) { return drops[i][P_SIZE]; }
    public float dropAngle(int i) { return drops[i][P_SPIN]; }
    public int dropVariant(int i) { return (int) drops[i][P_VARIANT]; }

    /** The pool spreading under the middle: its diameter in cells, its turn and which splat it is. */
    public float poolCells() { return poolSize * RavRunnerRig.easeOut(sinceDeath / POOL_SECONDS); }
    public float poolX() { return midX; }
    public float poolY() { return midY; }
    public float poolAngle() { return poolAngle; }
    public int poolVariant() { return poolVariant; }
}
