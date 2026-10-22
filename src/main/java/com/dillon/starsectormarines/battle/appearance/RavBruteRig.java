package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.unit.SwarmStage;

/**
 * The Rav Bloom Brute's skeleton, advanced once per simulation tick: a heavy pig-and-bull build whose
 * short legs stand under the body, so from above the hooves step at the body's edge and the body itself
 * carries the motion. Presentation only; it reads where the sim put the body, its charge, and when its
 * blow landed, and nothing in the simulation reads it back.
 *
 * <p>The rig frame is the body's own, in cells, centred on the sim position (the hero layer's row 760):
 * x to the right, y forward, z up. Legs are solved in their side-view plane, the forelimb as two bones
 * with the elbow back and the hind limb as three (stifle forward, hock back). On the move the hooves
 * follow a phase gait advanced by distance travelled, so a supporting hoof holds its spot on the ground:
 * a four-beat walk when slow, a trot at its walking speed (legs this short cannot walk at 1.8 cells/s),
 * a gallop on a charge. Stood still, in its charge tell, stunned or bracing a blow, the hooves are planted
 * and one that drifts too far steps back under its root.
 *
 * <p>What the camera overhead cannot see, the legs swinging under the barrel, is implied by what it can:
 * each hoof carries a load that eases in after it lands; the shoulders and the hips shift toward whichever
 * side of their pair carries more weight and away from a hind hoof driving off; the hips surge forward on
 * the push-off. The head drops for the charge (from the tell to the pull-up).
 *
 * <p>A blow is the whole body's. In the wind-up the weight goes back onto the hind legs, the shoulders rise
 * and the front hooves leave the ground; then it drives forward through the shoulders, the front hooves stamp
 * down ahead of where they stood and the head drives with them, and it settles back, the front hooves
 * stepping in under it. Stunned, it stands splay-footed with its head hung low, shaking it slowly from side to
 * side, and its shoulders wander.
 *
 * <p>Dead, it bursts as a Runner does ({@link RavBurst}): the body splits across between the shoulders and the
 * hips and the halves slump apart, the head and one or two legs come away and the rest stay on their half,
 * over a pool of ichor.
 *
 * <p>Ported from the approved motion study, {@code art-source/rav-redesign/exploration/brute_skeleton.py}.
 */
public final class RavBruteRig {

    // ------------------------------------------------------------------ anatomy (cells)
    /** Hero layer px per cell at the Brute's size; the export and the composer share it. */
    public static final float U_LAYER = 776.4f;
    /** Layer rows of the body anchor (the sim position), the shoulders and the hips. */
    public static final float ANCHOR_ROW = 760f, SHOULDER_ROW = 790f, HIP_ROW = 1158f;
    static final float SHOULDER_Y = (ANCHOR_ROW - SHOULDER_ROW) / U_LAYER;
    static final float HIP_Y = (ANCHOR_ROW - HIP_ROW) / U_LAYER;
    /** Leg planes: each hoof's rest x. */
    static final float FRONT_X = (512f - 186f) / U_LAYER, HIND_X = (512f - 256f) / U_LAYER;
    static final float SHOULDER_Z = 0.30f, HIP_Z = 0.29f;
    static final float FRONT_A = 0.15f, FRONT_B = 0.15f;
    static final float HIND_A = 0.12f, HIND_B = 0.12f, HIND_C = 0.075f;

    /** Leg order: front left, front right, hind left, hind right. */
    static final int FL = 0, FR = 1, HL = 2, HR = 3;

    /** Charge phases as {@code BruteChargeSystem} reports them. */
    public static final int IDLE = 0, TELL = 1, RUN = 2, PULL_UP = 3;

    // gaits: duty factor, stride (cells), lift (cells), per-leg phase offset (FL, FR, HL, HR)
    private enum Mode {
        WALK(0.72f, 0.36f, 0.05f, new float[]{0.25f, 0.75f, 0.0f, 0.5f}, 0.4f),
        TROT(0.52f, 0.46f, 0.055f, new float[]{0.0f, 0.5f, 0.5f, 0.0f}, 0.7f),
        GALLOP(0.36f, 0.90f, 0.07f, new float[]{0.5f, 0.62f, 0.0f, 0.12f}, 1.0f),
        PLANT(1f, 1f, 0f, new float[4], 0f);

        final float duty, stride, lift, run;
        final float[] offset;

        Mode(float duty, float stride, float lift, float[] offset, float run) {
            this.duty = duty;
            this.stride = stride;
            this.lift = lift;
            this.offset = offset;
            this.run = run;
        }
    }

    /** Below this speed (cells/s) the Brute stands; below the next it walks rather than trots. */
    private static final float STAND_BELOW = 0.15f, WALK_BELOW = 1.2f;
    private static final float RESTEP_DISTANCE = 0.16f;
    private static final float LAND_SECONDS = 0.12f;
    private static final float BLEND_SECONDS = 0.25f;
    private static final float LOAD_SECONDS = 0.06f;

    // the head, in hero layer px (dy + is back toward the body) and scale about the neck
    private static final float CHARGE_DY = -45f, CHARGE_K = 0.93f, CHARGE_EASE = 0.35f;
    /** A blow: rear back over WIND, slam over SLAM, hold, then recover over BACK. */
    static final float WIND = 0.32f, SLAM = 0.07f, HOLD = 0.25f, BACK = 0.35f;
    /**
     * The body in a blow, cells: how far it rocks back in the wind-up and drives forward in the slam, how
     * high the shoulders rise, and how high the front hooves lift.
     */
    static final float LUNGE_BACK = 0.07f, LUNGE = 0.15f, REAR = 0.09f, FRONT_LIFT = 0.08f;
    /** Stunned: the head's slow shake (degrees, cycles a second) and the shoulders' wander (cells, cycles). */
    static final float DAZE_YAW = 14f, DAZE_HZ = 1.1f, DAZE_SWAY = 0.03f, DAZE_SWAY_HZ = 0.6f;

    // ------------------------------------------------------------------ pose layout
    /** First point of each leg; front legs have three points (root, elbow, hoof), hind legs four. */
    public static final int[] LEG_START = {0, 3, 6, 10};
    public static final int[] LEG_POINTS = {3, 3, 4, 4};
    public static final int SHOULDER = 14, HIP = 15;
    /** The head's centre (where it pivots when it comes away) and the middle of the back (where it splits). */
    public static final int HEAD = 16, MID = 17;
    /**
     * Where the spine meets itself in the middle of the back, as the front half and as the rear half carry it:
     * the same point while the back is straight, apart across the outside of a bend.
     */
    public static final int SEAM_FRONT = 18, SEAM_REAR = 19;
    static final int POINTS = 20;
    /**
     * The spine's give through a turn: degrees of bend per degree a second of turning, the most it bends,
     * and how quickly it takes the bend up. Stiffer and slower than a Runner's: a heavier back.
     */
    static final float BEND_PER_YAW_RATE = 0.1f, MAX_BEND = 18f, BEND_SECONDS = 0.2f;
    /** The back bends about a hinge this far out from the spine, cells: the outer edge of the body at the seam. */
    static final float HINGE_CELLS = 0.38f;
    /** Layer row of the head sprite's centre, ahead of the shoulders. */
    static final float HEAD_ROW = 224f;

    /** What a Brute bursts into: the body's rear and front halves, the head, and each leg. */
    public static final int PIECE_REAR = 0, PIECE_FRONT = 1, PIECE_HEAD = 2, PIECE_LEG = 3;
    private static final int[][] PIECE_POINTS = {
            {MID, HIP}, {MID, SHOULDER}, {HEAD},
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8, 9}, {10, 11, 12, 13}};
    private static final int[] PIECE_PARENT = {-1, -1, -1, PIECE_FRONT, PIECE_FRONT, PIECE_REAR, PIECE_REAR};
    private static final int[] LOOSE_CANDIDATES = {3, 4, 5, 6};
    /** The Brute's pool, cells across: it is a bigger body than a Runner. */
    private static final float POOL_CELLS = 1.6f;

    private final float[] pose = new float[POINTS * 3];
    private float bodyX, bodyY, facingDeg;
    private boolean started;
    private float phi;
    private Mode mode = Mode.PLANT;
    private final float[] feet = new float[12];
    private final float[] stance = new float[4];          // stance progress 0..1, or -1 in the air
    private final float[] load = {1f, 1f, 1f, 1f};
    private final float[] pinnedWorld = new float[8];
    private final float[] pinStart = new float[12];
    private final float[] pinLand = new float[4];
    private final float[] pinAge = new float[4];
    private final boolean[] pinned = new boolean[4];
    private final float[] blendFrom = new float[12];
    private float blendAge = -1f;
    private boolean feetValid;

    private float charge;                                  // 0..1, how far the head is down for a charge
    private float lastCooldown;
    private float sinceBlow = Float.MAX_VALUE;
    private float headDy, headK = 1f, crouch;
    private float lunge, rear, frontLift;
    private boolean frontsLifted;
    private float stunTime = -1f, headYaw, daze;
    /** Spine bend, degrees; positive bows the body into a left turn. */
    private float bend;

    private final long seed;
    private boolean dead;
    private final RavBurst burst = new RavBurst(PIECE_POINTS, 2, PIECE_PARENT, LOOSE_CANDIDATES, POOL_CELLS);

    public RavBruteRig(long seed) {
        this.seed = seed;
    }

    /** The body's growth stage, and its scale against its adult: what it is drawn at and with. */
    private SwarmStage stage = SwarmStage.ADULT;
    private float scale = 1f;

    /** Sets the growth stage; read each tick, so a Matriarch that matures in the field grows with it. */
    public void setStage(SwarmStage stage) {
        this.stage = SwarmStage.of(stage);
        this.scale = this.stage.scale;
    }

    public SwarmStage stage() { return stage; }

    /** The stage's draw scale against the adult ({@link SwarmStage#scale}). */
    public float scale() { return scale; }

    /**
     * Advances the rig one tick.
     *
     * @param posX        sim position
     * @param posY        sim position
     * @param facing      body facing in sprite degrees (0 north, counter-clockwise)
     * @param cooldown    blow cooldown remaining, seconds; jumps up on the tick a blow lands
     * @param chargePhase {@link #IDLE}, {@link #TELL}, {@link #RUN} or {@link #PULL_UP}
     * @param stunned     whether the Brute can't act
     * @param dt          tick length, seconds
     */
    public void advance(float posX, float posY, float facing, float cooldown, int chargePhase,
                        boolean stunned, float dt) {
        if (!started) {
            bodyX = posX;
            bodyY = posY;
            facingDeg = facing;
            lastCooldown = cooldown;
            started = true;
        }
        float dx = posX - bodyX, dy = posY - bodyY;
        float moved = (float) Math.hypot(dx, dy);
        if (moved > 1.5f) {                      // a teleport: start over where it landed
            feetValid = false;
            moved = 0f;
            dx = dy = 0f;
        }
        bodyX = posX;
        bodyY = posY;
        // The skeleton walks in its adult's units: a body drawn smaller covers more of its own strides.
        dx /= scale;
        dy /= scale;
        moved /= scale;
        float yawRate = (((facing - facingDeg) % 360f + 540f) % 360f - 180f) / dt;
        float bendTarget = Math.max(-MAX_BEND, Math.min(MAX_BEND, yawRate * BEND_PER_YAW_RATE));
        bend += (bendTarget - bend) * Math.min(1f, dt / BEND_SECONDS);
        facingDeg = facing;
        double rad = Math.toRadians(facing);
        float fwdX = (float) -Math.sin(rad), fwdY = (float) Math.cos(rad);
        float along = dx * fwdX + dy * fwdY;
        float speed = moved / dt;

        advanceHead(cooldown, chargePhase, dt);
        advanceDaze(stunned, dt);
        boolean bracing = sinceBlow < SLAM + HOLD || rearing(cooldown);

        Mode next;
        if (stunned || chargePhase == TELL || bracing || speed < STAND_BELOW) next = Mode.PLANT;
        else if (chargePhase == RUN) next = Mode.GALLOP;
        else next = speed < WALK_BELOW ? Mode.WALK : Mode.TROT;
        if (next != Mode.PLANT) phi += (along >= 0f ? moved : -moved) / next.stride;

        if (next != mode && next != Mode.PLANT) {
            if (feetValid) {
                System.arraycopy(feet, 0, blendFrom, 0, 12);
                blendAge = 0f;
            }
            for (int i = 0; i < 4; i++) pinned[i] = false;
        }
        mode = next;
        solveFeet(next, dt, fwdX, fwdY);
        if (blendAge >= 0f) {
            blendAge += dt;
            float k = RavRunnerRig.smooth(Math.min(1f, blendAge / BLEND_SECONDS));
            for (int i = 0; i < 4; i++) {
                feet[i * 3 + 1] = blendFrom[i * 3 + 1] + (feet[i * 3 + 1] - blendFrom[i * 3 + 1]) * k;
                feet[i * 3 + 2] = feet[i * 3 + 2] + 0.03f * (float) Math.sin(Math.PI * k);
            }
            if (k >= 1f) blendAge = -1f;
        }
        feetValid = true;

        // load: the weight a hoof carries, eased in after it lands so the shift reads as a transfer
        float rate = 1f - (float) Math.exp(-dt / LOAD_SECONDS);
        for (int i = 0; i < 4; i++) {
            float target = feet[i * 3 + 2] < 0.004f ? 1f : 0f;
            load[i] += (target - load[i]) * rate;
        }
        float run = next.run;
        float pushL = push(HL), pushR = push(HR);
        float swayF = 0.035f * (load[FR] - load[FL]) * run
                + daze * DAZE_SWAY * (float) Math.sin(2 * Math.PI * DAZE_SWAY_HZ * Math.max(0f, stunTime));
        float swayH = 0.045f * (load[HR] - load[HL]) * run + 0.02f * run * (pushL - pushR);
        float surge = 0.03f * run * (pushL + pushR) - 0.012f * run * (load[FL] + load[FR] - 1f);
        float riseF = 0.012f * run * (1f - 0.5f * (load[FL] + load[FR]));
        float riseH = 0.012f * run * (1f - 0.5f * (load[HL] + load[HR]));
        set(SHOULDER, swayF, SHOULDER_Y + surge * 0.4f + lunge, SHOULDER_Z - crouch + riseF + rear);
        set(HIP, swayH, HIP_Y + surge + lunge, HIP_Z - crouch * 1.6f + riseH - rear * 0.3f);
        set(MID, (x(SHOULDER) + x(HIP)) / 2, (y(SHOULDER) + y(HIP)) / 2, (z(SHOULDER) + z(HIP)) / 2);
        set(HEAD, x(SHOULDER), y(SHOULDER) + (SHOULDER_ROW - HEAD_ROW) / U_LAYER, z(SHOULDER));
        bendSpine();
        solveLegs();
    }

    /**
     * Bows the back into the turn: the forequarters and the head swing one way about the middle of the back
     * and the hindquarters the other, so the body draws a shallow C toward the inside of the turn while the
     * planted hooves stay put and the legs lean. The halves hinge at the outer edge of the body, where they
     * stay joined; on the inside they press into each other.
     */
    private void bendSpine() {
        float mx = x(MID), my = y(MID);
        set(SEAM_FRONT, mx, my, z(MID));
        set(SEAM_REAR, mx, my, z(MID));
        if (Math.abs(bend) < 0.05f) return;
        float ax = x(SHOULDER) - x(HIP), ay = y(SHOULDER) - y(HIP);
        float an = Math.max(1e-4f, (float) Math.hypot(ax, ay));
        float out = Math.signum(bend) * HINGE_CELLS;    // the outside of the turn: right, bending left
        float hx = mx + ay / an * out, hy = my - ax / an * out;
        turn(SHOULDER, hx, hy, bend / 2);
        turn(HEAD, hx, hy, bend / 2);
        turn(SEAM_FRONT, hx, hy, bend / 2);
        turn(HIP, hx, hy, -bend / 2);
        turn(SEAM_REAR, hx, hy, -bend / 2);
        set(MID, (x(SEAM_FRONT) + x(SEAM_REAR)) / 2, (y(SEAM_FRONT) + y(SEAM_REAR)) / 2, z(MID));
    }

    /** Turns point p about (hx, hy) by degrees counter-clockwise. */
    private void turn(int p, float hx, float hy, float degrees) {
        double a = Math.toRadians(degrees);
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float dx = x(p) - hx, dy = y(p) - hy;
        pose[p * 3] = hx + dx * c - dy * s;
        pose[p * 3 + 1] = hy + dx * s + dy * c;
    }

    private float push(int leg) {
        return stance[leg] < 0f ? 0f : RavRunnerRig.smooth((stance[leg] - 0.6f) / 0.4f);
    }

    private boolean rearing(float cooldown) {
        return cooldown > 0f && cooldown < WIND && sinceBlow > SLAM + HOLD;
    }

    private void advanceHead(float cooldown, int chargePhase, float dt) {
        if (cooldown > lastCooldown + 1e-4f) sinceBlow = 0f;       // a blow landed this tick
        else if (sinceBlow < Float.MAX_VALUE / 2) sinceBlow += dt;
        lastCooldown = cooldown;

        boolean headDown = chargePhase == TELL || chargePhase == RUN;
        charge += ((headDown ? 1f : 0f) - charge) * Math.min(1f, dt / (CHARGE_EASE / 3f));
        float dy = CHARGE_DY * charge, k = 1f + (CHARGE_K - 1f) * charge, c = 0f;
        float l = 0f, r = 0f, f = 0f;
        frontsLifted = false;
        if (sinceBlow < SLAM) {                                    // the drive: from reared to stamped down
            float s = RavRunnerRig.easeOut(sinceBlow / SLAM);
            dy = 40f - 110f * s;
            k = 1.08f - 0.13f * s;
            c = 0.03f - 0.05f * s;
            l = -LUNGE_BACK + (LUNGE + LUNGE_BACK) * s;
            r = REAR - (REAR + 0.03f) * s;
            f = FRONT_LIFT * (1f - s);
            frontsLifted = true;
        } else if (sinceBlow < SLAM + HOLD) {
            dy = -70f;
            k = 0.95f;
            c = -0.02f;
            l = LUNGE;
            r = -0.03f;
        } else if (sinceBlow < SLAM + HOLD + BACK) {
            float s = RavRunnerRig.smooth((sinceBlow - SLAM - HOLD) / BACK);
            dy = dy + (-70f - dy) * (1f - s);
            k = k + (0.95f - k) * (1f - s);
            c = -0.02f * (1f - s);
            l = LUNGE * (1f - s);
            r = -0.03f * (1f - s);
        } else if (rearing(cooldown)) {                            // the wind-up: weight back, forequarters up
            float s = RavRunnerRig.smooth(1f - cooldown / WIND);
            dy = 40f * s;
            k = 1f + 0.08f * s;
            c = 0.03f * s;
            l = -LUNGE_BACK * s;
            r = REAR * s;
            f = FRONT_LIFT * s;
            frontsLifted = true;
        }
        headDy = dy;
        headK = k;
        crouch = c;
        lunge = l;
        rear = r;
        frontLift = f;
    }

    /** Stunned: the head hangs and shakes slowly, the shoulders wander; it eases in and out. */
    private void advanceDaze(boolean stunned, float dt) {
        if (stunned) stunTime = stunTime < 0f ? 0f : stunTime + dt;
        else if (daze <= 0f) stunTime = -1f;
        else stunTime += dt;
        daze += ((stunned ? 1f : 0f) - daze) * Math.min(1f, dt / 0.15f);
        if (daze < 1e-3f && !stunned) daze = 0f;
        float t = Math.max(0f, stunTime);
        headYaw = daze * DAZE_YAW * (float) Math.sin(2 * Math.PI * DAZE_HZ * t);
        headDy += daze * (-30f - headDy);
        headK += daze * (0.95f - headK);
        crouch += daze * 0.025f;
    }

    private void solveFeet(Mode m, float dt, float fwdX, float fwdY) {
        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            float rootX = (i % 2 == 0 ? -1f : 1f) * (front ? FRONT_X : HIND_X);
            float neutral = front ? SHOULDER_Y : HIP_Y;
            if (m == Mode.PLANT && front && frontsLifted) {
                // In a blow the forefeet are the body's: lifted with the shoulders, stamped down ahead.
                stance[i] = -1f;
                pinned[i] = false;
                feet[i * 3] = rootX;
                feet[i * 3 + 1] = neutral + lunge + 0.03f;
                feet[i * 3 + 2] = frontLift;
            } else if (m == Mode.PLANT) {
                stance[i] = 0.5f;
                if (!pinned[i]) {
                    float sx, sy, sz;
                    if (feetValid) {
                        sx = feet[i * 3]; sy = feet[i * 3 + 1]; sz = feet[i * 3 + 2];
                    } else {
                        sx = rootX; sy = neutral; sz = 0f;
                    }
                    pin(i, sx, sy, sz, sz < 0.005f ? sy : neutral + 0.03f, fwdX, fwdY);
                }
                pinAge[i] += dt;
                float k = RavRunnerRig.smooth(Math.min(1f, pinAge[i] / LAND_SECONDS));
                float wx = pinnedWorld[i * 2], wy = pinnedWorld[i * 2 + 1];
                float lx = (wx - bodyX) * fwdY - (wy - bodyY) * fwdX;
                float ly = (wx - bodyX) * fwdX + (wy - bodyY) * fwdY;
                if (k < 1f) {
                    float sy = pinStart[i * 3 + 1];
                    ly = sy + (pinLand[i] - sy) * k;
                    lx = pinStart[i * 3] + (rootX - pinStart[i * 3]) * k;
                }
                float z = pinStart[i * 3 + 2] * (1f - k);
                if (k >= 1f && Math.hypot(lx - rootX, ly - neutral) > RESTEP_DISTANCE) {
                    pin(i, lx, ly, 0f, neutral, fwdX, fwdY);
                    pinStart[i * 3 + 2] = 0.05f;
                }
                feet[i * 3] = lx;
                feet[i * 3 + 1] = ly;
                feet[i * 3 + 2] = z;
            } else {
                float u = frac(phi + m.offset[i]);
                float half = m.duty * m.stride / 2;
                float fy, fz;
                if (u < m.duty) {
                    fy = half - (u / m.duty) * 2 * half;
                    fz = 0f;
                    stance[i] = u / m.duty;
                } else {
                    float k = (u - m.duty) / (1 - m.duty);
                    fy = -half + 2 * half * RavRunnerRig.smooth(k);
                    fz = m.lift * (float) Math.sin(Math.PI * k);
                    stance[i] = -1f;
                }
                feet[i * 3] = rootX;
                feet[i * 3 + 1] = neutral + fy;
                feet[i * 3 + 2] = fz;
            }
        }
    }

    private void pin(int i, float sx, float sy, float sz, float landY, float fwdX, float fwdY) {
        float rootX = (i % 2 == 0 ? -1f : 1f) * (i < 2 ? FRONT_X : HIND_X);
        pinned[i] = true;
        pinAge[i] = 0f;
        pinStart[i * 3] = sx;
        pinStart[i * 3 + 1] = sy;
        pinStart[i * 3 + 2] = sz;
        pinLand[i] = landY;
        pinnedWorld[i * 2] = bodyX + rootX * fwdY + landY * fwdX;
        pinnedWorld[i * 2 + 1] = bodyY - rootX * fwdX + landY * fwdY;
    }

    private void solveLegs() {
        float[] j = new float[4];
        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            int root = front ? SHOULDER : HIP;
            float rootX = feet[i * 3] + x(root);
            float rootY = y(root), rootZ = z(root) - 0.02f;
            float fx = feet[i * 3], fy = feet[i * 3 + 1], fz = feet[i * 3 + 2];
            int p = LEG_START[i];
            set(p, rootX, rootY, rootZ);
            if (front) {
                RavRunnerRig.ik2(rootY, rootZ, fy, fz, FRONT_A, FRONT_B, -1f, j);
                set(p + 1, rootX, j[0], j[1]);
                set(p + 2, fx, j[2], j[3]);
            } else {
                RavRunnerRig.ik2(rootY, rootZ, fy - 0.02f, fz + HIND_C, HIND_A, HIND_B, 1f, j);
                set(p + 1, rootX, j[0], j[1]);
                set(p + 2, rootX, j[2], j[3]);
                set(p + 3, fx, fy, fz);
            }
        }
    }

    private void set(int point, float x, float y, float z) {
        pose[point * 3] = x;
        pose[point * 3 + 1] = y;
        pose[point * 3 + 2] = z;
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    // ------------------------------------------------------------------ death

    /**
     * Advances a dead Brute: on its first dead tick it bursts from the pose it died in. Seeded by the rig, so
     * every replay bursts the same way.
     */
    public void advanceDead(float posX, float posY, float dt) {
        if (!started) advance(posX, posY, facingDeg, 0f, IDLE, false, dt);   // never drawn alive: stand it up
        bodyX = posX;
        bodyY = posY;
        if (!dead) {
            dead = true;
            burst.start(pose, x(MID), y(MID), seed);
        }
        burst.advance(dt);
    }

    /** Whether the Brute is dead and burst. */
    public boolean dead() { return dead; }

    /** The burst it died in; meaningful once dead. */
    public RavBurst burst() { return burst; }

    // ------------------------------------------------------------------ read side

    /** Whether the rig has been advanced at least once and so holds a pose. */
    public boolean posed() { return started; }
    /** The body position the pose was solved at; the renderer draws from here, not the live column. */
    public float bodyX() { return bodyX; }
    public float bodyY() { return bodyY; }
    /** Facing the pose was solved at, in sprite degrees. */
    public float facingDegrees() { return facingDeg; }
    public float x(int point) { return pose[point * 3]; }
    public float y(int point) { return pose[point * 3 + 1]; }
    public float z(int point) { return pose[point * 3 + 2]; }
    /** The head's offset back along the body, in hero layer px (negative: thrust forward and down). */
    public float headDy() { return headDy; }
    /** The head's scale about the neck: above one reared toward the camera, below it driven down. */
    public float headK() { return headK; }
    /** How far the forequarters are reared up, cells; negative when driven down. */
    public float rear() { return rear; }

    /** Spine bend this tick, degrees; positive bows the body into a left turn. */
    public float bendDegrees() { return bend; }
    /** The head turned about the neck, degrees, counter-clockwise: a stunned Brute shaking it. */
    public float headYaw() { return headYaw; }
    /** Whether the hooves are planted this tick. */
    public boolean planted() { return mode == Mode.PLANT; }
}
