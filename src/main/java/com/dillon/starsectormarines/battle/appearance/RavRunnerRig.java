package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.unit.SwarmStage;

/**
 * The Rav Bloom Runner's skeleton, advanced once per simulation tick: a hunched four-legged hound
 * whose feet stay planted on the ground, with two jointed spike arms mounted on its shoulders.
 * Presentation only; it reads where the sim put the body and when its strike landed, and nothing
 * in the simulation reads it back.
 *
 * <p>The rig frame is the body's own, in cells, centred on the sim position: x to the right, y
 * forward, z up. Legs are solved in their side-view plane, the front leg as two bones with the
 * elbow back and the hind leg as three with the stifle forward and the hock back. On the move the
 * feet follow a phase gait advanced by distance travelled, so a supporting foot moves back exactly
 * as fast as the body moves forward; stood still or striking, the feet are pinned in the world and
 * a foot that drifts too far from under its root steps back. The spine stretches and gathers with
 * the gallop, and the shoulders and hips sway against each other with the stride.
 *
 * <p>Strikes are one arm at a time. The wind-up runs off the attack cooldown, so the blow lands on
 * the tick the damage is dealt: a stab bows the elbow out away from the body and drives the point
 * straight forward, a swipe turns the arm out wide and cuts across the front. Which arm and which
 * blow alternate deterministically per strike.
 *
 * <p>Ported from the approved art-study skeleton, {@code art-source/rav-redesign/exploration/skeleton.py}.
 */
public final class RavRunnerRig {

    // ------------------------------------------------------------------ anatomy (cells)
    public static final float SPINE = 0.50f;
    static final float SHOULDER_Z = 0.40f;
    static final float HIP_Z = 0.38f;
    // the stance is a little wider than the study's 0.17, so the paws show past the slim flanks
    static final float FRONT_X = 0.20f, FRONT_A = 0.22f, FRONT_B = 0.22f;
    static final float HIND_X = 0.20f, HIND_A = 0.19f, HIND_B = 0.20f, HIND_C = 0.12f;
    static final float ARM_X = 0.12f, ARM_UP = 0.07f, ARM_A = 0.30f, ARM_B = 0.30f, ARM_SPIKE = 0.18f;

    /** Leg order: front left, front right, hind left, hind right. */
    static final int FL = 0, FR = 1, HL = 2, HR = 3;

    // gait: duty factor, stride (cells), per-leg phase offset, foot lift
    private static final float GALLOP_DUTY = 0.30f, GALLOP_STRIDE = 1.05f, GALLOP_LIFT = 0.10f;
    private static final float[] GALLOP_OFFSET = {0.58f, 0.48f, 0.0f, 0.1f};
    private static final float TROT_DUTY = 0.55f, TROT_STRIDE = 0.36f, TROT_LIFT = 0.05f;
    private static final float[] TROT_OFFSET = {0.0f, 0.5f, 0.5f, 0.0f};
    /** Above this speed (cells/s) the Runner gallops, below the lower one it trots. */
    private static final float GALLOP_ABOVE = 1.6f, TROT_BELOW = 1.2f;
    /** Below this speed the Runner stands on planted feet. */
    private static final float STAND_BELOW = 0.15f;
    /** Below this speed a strike plants the feet. */
    private static final float STRIKE_PLANT_BELOW = 0.6f;
    /** A pinned foot further than this from under its root steps back under it. */
    private static final float RESTEP_DISTANCE = 0.22f;
    private static final float LAND_SECONDS = 0.10f;
    private static final float BLEND_SECONDS = 0.25f;

    // strike timing, seconds relative to the damage tick
    static final float STAB_WIND = 0.20f, STAB_DRIVE = 0.08f, STAB_HOLD = 0.08f, STAB_RECOVER = 0.30f;
    static final float SWIPE_WIND = 0.15f, SWIPE_CUT = 0.10f, SWIPE_BACK = 0.30f;
    /** The swipe connects a little past the middle of its cut. */
    static final float SWIPE_HIT = 0.55f * SWIPE_CUT;
    private static final float LUNGE = 0.18f;
    /** A stab gathers the body back this far, cells, while the elbow bows out. */
    private static final float PULL_BACK = 0.08f;
    /**
     * How far the shoulders turn with a blow, degrees: a stab rolls the striking shoulder back
     * and then drives it through; a swipe winds the shoulders away and twists hard across.
     */
    private static final float STAB_TWIST = 12f, SWIPE_WIND_TWIST = 15f, SWIPE_CUT_TWIST = 25f;
    /** The hips turn against the shoulders by this share of the twist. */
    private static final float HIP_COUNTER = 0.35f;
    private static final float RELAX_SECONDS = 0.15f;

    // arm poses: yaw (deg, + turns outward), reach, height, roll (deg, bows the elbow out)
    private static final float G = ARM_A / 0.17f;
    private static final float[] REST = {0f, 0.15f * G, 0.10f * G, 0f};
    private static final float[] COCKED = {0f, 0.05f * G, 0.14f * G, 80f};
    private static final float[] THRUST = {0f, 0.31f * G, -0.08f * G, 25f};
    private static final float[] SWIPE_OUT = {80f, 0.22f * G, 0.07f * G, 0f};
    private static final float[] SWIPE_CUT_POSE = {-30f, 0.30f * G, -0.02f * G, 0f};
    /** Hauling a load: the spike stays in, the elbow still bowed a little out. */
    private static final float[] CARRY = {0f, 0.17f * G, -0.08f * G, 35f};
    /** How fast a carrier turns to keep its load in front of its jaws, degrees a second. */
    private static final float CARRY_TURN = 360f;
    /** A carrier is drawn this far behind its sim position, so its load shows ahead of its jaws. */
    private static final float CARRY_BACK = 0.25f;
    /** The jaws reach this far toward the load, at most; the neck does not stretch further. */
    private static final float LATCH_REACH = 0.10f;
    /** Seconds until the pieces lie flat, the pool has spread, and everything is still. */
    private static final float POOL_CELLS = 1.05f;

    // ------------------------------------------------------------------ pose layout
    /** First point of each leg; front legs have three points, hind legs four. */
    private static final int[] LEG_START_INIT = {0, 3, 6, 10};
    public static final int[] LEG_START = LEG_START_INIT.clone();
    public static final int[] LEG_POINTS = {3, 3, 4, 4};
    /** First point of each arm (left, right): base, elbow, wrist, spike tip. */
    public static final int[] ARM_START = {14, 18};
    public static final int SHOULDER = 22, HIP = 23, NECK = 24, HEAD = 25, MID = 26;
    public static final int POINTS = 27;
    /**
     * How far each point turns with its half of the bending spine (front positive, rear negative,
     * as a share of half the bend): the feet hold their ground and the joints between share it.
     */
    private static final float[] BEND_SHARE = new float[POINTS];
    static {
        float[] front = {1f, 0.5f, 0f};
        float[] hind = {1f, 0.67f, 0.3f, 0f};
        for (int leg = 0; leg < 4; leg++) {
            float[] share = leg < 2 ? front : hind;
            for (int j = 0; j < share.length; j++) {
                BEND_SHARE[LEG_START_INIT[leg] + j] = leg < 2 ? share[j] : -share[j];
            }
        }
        for (int p = 14; p < 22; p++) BEND_SHARE[p] = 1f;
        BEND_SHARE[22] = 1f;     // shoulder
        BEND_SHARE[23] = -1f;    // hip
        BEND_SHARE[24] = 1.2f;   // neck, and the head looks further into the turn
        BEND_SHARE[25] = 1.4f;
    }

    /**
     * What a Runner bursts into: the trunk's two halves, the head, each leg and each spike arm,
     * as the points that move with each piece.
     */
    public static final int PIECE_REAR = 0, PIECE_FRONT = 1, PIECE_HEAD = 2, PIECE_LEG = 3, PIECE_ARM = 7;
    public static final int PIECES = 9;
    private static final int[][] PIECE_POINTS = new int[PIECES][];
    static {
        PIECE_POINTS[PIECE_REAR] = new int[]{MID, HIP};
        PIECE_POINTS[PIECE_FRONT] = new int[]{MID, SHOULDER};
        PIECE_POINTS[PIECE_HEAD] = new int[]{NECK, HEAD};
        for (int leg = 0; leg < 4; leg++) {
            PIECE_POINTS[PIECE_LEG + leg] = new int[LEG_POINTS[leg]];
            for (int i = 0; i < LEG_POINTS[leg]; i++) PIECE_POINTS[PIECE_LEG + leg][i] = LEG_START[leg] + i;
        }
        for (int a = 0; a < 2; a++) {
            PIECE_POINTS[PIECE_ARM + a] = new int[]{ARM_START[a], ARM_START[a] + 1, ARM_START[a] + 2, ARM_START[a] + 3};
        }
    }
    /** The trunk halves stay where they are; a leg rides on its half and an arm on the front, unless it comes away. */
    private static final int[] PIECE_PARENT = {-1, -1, -1, PIECE_FRONT, PIECE_FRONT, PIECE_REAR, PIECE_REAR,
            PIECE_FRONT, PIECE_FRONT};
    /** The limbs, of which one or two come away. */
    private static final int[] LOOSE_CANDIDATES = {3, 4, 5, 6, 7, 8};

    private enum Mode { GALLOP, TROT, PLANT }

    private final float[] pose = new float[POINTS * 3];
    private float bodyX, bodyY, facingDeg;
    private boolean started;
    private float phi;
    private Mode mode = Mode.PLANT;
    private float swayAmp, shx, hpx, run;
    /** Spine bend through a turn, degrees: positive bows the body into a left turn. */
    private float bend;
    /** The shoulders' turn into the current blow, degrees, positive counter-clockwise. */
    private float twist;
    /** Degrees of bend per degree a second of turning, and the most the spine bends. */
    private static final float BEND_PER_YAW_RATE = 0.12f, MAX_BEND = 40f;
    /** How quickly the spine takes the bend up, seconds. */
    private static final float BEND_SECONDS = 0.12f;
    private final float[] feet = new float[12];          // rig frame
    private final float[] pinnedWorld = new float[8];     // world x,y of each pinned foot
    private final float[] pinStart = new float[12];       // rig frame when the pin began
    private final float[] pinLand = new float[4];         // landing y (rig frame, along the body) per foot
    private final float[] pinAge = new float[4];
    private final boolean[] pinned = new boolean[4];
    private final float[] blendFrom = new float[12];
    private float blendAge = -1f;
    private boolean feetValid;

    // strike state
    private final long seed;
    private int strikeCount;
    private boolean winding;
    private float sinceHit = Float.MAX_VALUE;
    private int strikeArm;          // 0 left, 1 right
    private boolean strikeIsStab;
    private float lastCooldown;
    private final float[][] arm = {REST.clone(), REST.clone()};

    // a skitterer's work: what it is doing, how far into the work pose it has eased, and the peck it is in
    public static final int WORK_NONE = 0, WORK_HEAD_DOWN = 1, WORK_FEED = 2, WORK_PULP = 3;
    /** Seconds one peck at the work takes, and how far the shoulders sink into the work. */
    private static final float PECK_SECONDS = 0.7f, WORK_CROUCH = 0.07f;
    /** How far the head swings side to side as it nibbles, cells, and dips toward the ground, cells. */
    private static final float NIBBLE = 0.045f, DIP_DROP = 0.22f;
    private int work;
    private float workAmt, workClock, dip;

    // carrying and dying
    private boolean carrying;
    private float latch;            // 0..1, how far the jaws have closed on the load
    private boolean dead;
    private final RavBurst burst = new RavBurst(PIECE_POINTS, 2, PIECE_PARENT, LOOSE_CANDIDATES, POOL_CELLS);

    public RavRunnerRig(long seed) {
        this(seed, false);
    }

    /**
     * A skitterer's rig: the Runner's skeleton at {@link #WORKER_SCALE} of its size, which it is drawn, strides and
     * latches at, drawn with the skitterer's own bones (`colony-breach-nouns.md`, Skitterer).
     */
    public static RavRunnerRig worker(long seed) {
        return new RavRunnerRig(seed, true);
    }

    /** A skitterer against a Runner, linear: its rig's size, and {@code UnitType.SWARM_WORKER}'s render scale. */
    public static final float WORKER_SCALE = 0.68f;

    private RavRunnerRig(long seed, boolean worker) {
        this.seed = seed;
        this.worker = worker;
        this.scale = worker ? WORKER_SCALE : 1f;
        // Each Runner starts at its own point in the stride, so a pack lopes out of step.
        long h = (seed ^ 0xD1B54A32D192ED03L) * 0xBF58476D1CE4E5B9L;
        this.phi = ((h >>> 40) & 0xFFFF) / 65536f;
    }

    /** The body's growth stage, and its scale against its adult: what it is drawn at and with. */
    private SwarmStage stage = SwarmStage.ADULT;
    private float scale;
    /** Whether this is a skitterer's rig rather than a Runner's. */
    private final boolean worker;

    /** Sets the growth stage; read each tick, so a Matriarch that matures in the field grows with it. */
    public void setStage(SwarmStage stage) {
        this.stage = SwarmStage.of(stage);
        this.scale = this.stage.scale * (worker ? WORKER_SCALE : 1f);
    }

    public SwarmStage stage() { return stage; }

    /** Whether this rig is a skitterer's, drawn with its own bones and no spike arms. */
    public boolean worker() { return worker; }

    /** The body's draw scale against an adult Runner: its stage's ({@link SwarmStage#scale}), and a skitterer's size. */
    public float scale() { return scale; }

    /**
     * Advances the rig one tick.
     *
     * @param posX       sim position
     * @param posY       sim position
     * @param facing     body facing in sprite degrees (0 north, counter-clockwise)
     * @param cooldown   attack cooldown remaining, seconds; jumps up on the tick a blow lands
     * @param engaged    whether the Runner has a target, so a wind-up is worth starting
     * @param dt         tick length, seconds
     */
    public void advance(float posX, float posY, float facing, float cooldown, boolean engaged, float dt) {
        advance(posX, posY, facing, cooldown, engaged, false, 0f, 0f, dt);
    }

    /**
     * Advances the rig one tick, hauling a load when {@code carrying}: the Runner turns to face
     * the body it drags, so it backs toward the bore at a trot, the impaling spike stays in and
     * the jaws latch on beside it.
     *
     * @param loadX world position of the dragged body; read only while carrying
     * @param loadY world position of the dragged body
     */
    public void advance(float posX, float posY, float facing, float cooldown, boolean engaged,
                        boolean carrying, float loadX, float loadY, float dt) {
        if (dead) return;
        if (carrying && started) {
            float want = (float) Math.toDegrees(Math.atan2(-(loadX - posX), loadY - posY));
            float turn = ((want - facingDeg) % 360f + 540f) % 360f - 180f;
            float step = CARRY_TURN * dt;
            facing = facingDeg + Math.max(-step, Math.min(step, turn));
        } else if (this.carrying && !carrying && started) {
            // the load is gone: hand the heading back to the sim gently next tick
            facing = facingDeg + Math.max(-CARRY_TURN * dt, Math.min(CARRY_TURN * dt,
                    ((facing - facingDeg) % 360f + 540f) % 360f - 180f));
        }
        this.carrying = carrying;
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
        float yawRate = SpineBend.yawRate(facingDeg, facing, dt);
        bend = SpineBend.ease(bend, yawRate, BEND_PER_YAW_RATE, MAX_BEND, BEND_SECONDS, dt);
        facingDeg = facing;
        double rad = Math.toRadians(facing);
        float fwdX = (float) -Math.sin(rad), fwdY = (float) Math.cos(rad);
        float along = dx * fwdX + dy * fwdY;
        float speed = moved / dt;

        if (carrying) {
            winding = false;
            sinceHit = Float.MAX_VALUE;
            cooldown = Math.max(cooldown, 1f);
        }
        advanceStrike(cooldown, engaged && !carrying, dt);
        latch += ((carrying ? 1f : 0f) - latch) * Math.min(1f, dt / 0.25f);
        if (carrying) {
            float k = Math.min(1f, dt / 0.35f);
            for (int q = 0; q < 4; q++) arm[strikeArm][q] += (CARRY[q] - arm[strikeArm][q]) * k;
        }
        boolean striking = winding || sinceHit < strikeLength();

        Mode next;
        // a strike plants the feet when the Runner has stopped to deliver it, not while it is still closing
        if (speed < STAND_BELOW || (striking && speed < STRIKE_PLANT_BELOW)) next = Mode.PLANT;
        else if (mode == Mode.GALLOP) next = speed < TROT_BELOW || along < 0f ? Mode.TROT : Mode.GALLOP;
        else next = speed > GALLOP_ABOVE && along > 0f ? Mode.GALLOP : Mode.TROT;

        float stride = next == Mode.GALLOP ? GALLOP_STRIDE : TROT_STRIDE;
        if (next != Mode.PLANT) phi += (along >= 0f ? moved : -moved) / stride;
        float runTarget = next == Mode.GALLOP ? 1f : next == Mode.TROT ? 0.25f : 0f;
        run += (runTarget - run) * Math.min(1f, dt / 0.15f);
        float flex = 0.10f * run * (float) Math.sin(2 * Math.PI * (phi + 0.25f));
        float crouch = crouch(cooldown);
        float lunge = lunge(cooldown);
        twist = twist(cooldown);

        float swayTarget = next == Mode.GALLOP ? 0.025f : next == Mode.TROT ? (along < 0f ? 0.045f : 0.035f) : 0f;
        swayAmp += (swayTarget - swayAmp) * Math.min(1f, dt / 0.15f);
        float s = (float) Math.sin(2 * Math.PI * phi);
        shx = swayAmp * s * (along < 0f ? 1.4f : 1f);     // hauling backward throws the shoulders hardest
        hpx = -swayAmp * s;

        // At work the head goes down and pecks, a seeded rhythm per body so a nest does not work in step.
        workAmt += ((work == WORK_HEAD_DOWN && next == Mode.PLANT ? 1f : 0f) - workAmt) * Math.min(1f, dt / 0.2f);
        workClock = (workClock + dt) % PECK_SECONDS;
        float peck = (workClock / PECK_SECONDS + ((seed * 0x9E3779B97F4A7C15L) >>> 40 & 0xFF) / 256f) % 1f;
        float down = (float) Math.sin(Math.PI * peck);
        dip = workAmt * (0.55f + 0.45f * down * down);
        crouch += WORK_CROUCH * workAmt;

        float shY = lunge + SPINE / 2 * (1 + flex), hpY = lunge - SPINE / 2 * (1 + flex);
        float shZ = SHOULDER_Z - crouch, hpZ = HIP_Z - crouch * 0.6f;
        set(SHOULDER, shx, shY, shZ);
        set(HIP, hpx, hpY, hpZ);

        if (next != mode && next != Mode.PLANT) {
            // leaving a stance, or changing gait: ease the feet from where they were into the stride
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
            float k = smooth(Math.min(1f, blendAge / BLEND_SECONDS));
            for (int i = 0; i < 4; i++) {
                feet[i * 3 + 1] = blendFrom[i * 3 + 1] + (feet[i * 3 + 1] - blendFrom[i * 3 + 1]) * k;
                feet[i * 3 + 2] = feet[i * 3 + 2] + 0.04f * (float) Math.sin(Math.PI * k);
            }
            if (k >= 1f) blendAge = -1f;
        }
        feetValid = true;

        solveLegs(shY, shZ, hpY, hpZ);
        solveArms(shY, shZ);
        set(NECK, shx * 1.2f, shY + 0.12f, shZ + 0.03f);
        float nibble = NIBBLE * workAmt * (float) Math.sin(2 * Math.PI * peck * 0.5f);
        set(HEAD, shx * 1.4f + nibble, shY + 0.25f + 0.03f * s * run - 0.04f * dip, shZ - 0.01f - DIP_DROP * dip);
        set(MID, (shx + hpx) / 2, (shY + hpY) / 2, (shZ + hpZ) / 2);
        bendSpine();
        if (latch > 0.001f && carrying) latchOn(loadX, loadY, fwdX, fwdY);
    }

    private void solveFeet(Mode m, float dt, float fwdX, float fwdY) {
        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            float rootX = (i % 2 == 0 ? -1f : 1f) * (front ? FRONT_X : HIND_X);
            float neutral = front ? SPINE / 2 : -SPINE / 2;    // feet hang off the unflexed frame
            if (m == Mode.PLANT) {
                if (!pinned[i]) {
                    float sx, sy, sz;
                    if (feetValid) {
                        sx = feet[i * 3]; sy = feet[i * 3 + 1]; sz = feet[i * 3 + 2];
                    } else {
                        sx = rootX; sy = neutral; sz = 0f;
                    }
                    pin(i, sx, sy, sz, sz < 0.005f ? sy : neutral + 0.02f, fwdX, fwdY);
                }
                pinAge[i] += dt;
                float k = smooth(Math.min(1f, pinAge[i] / LAND_SECONDS));
                // the landing runs along the body; once down, the foot holds its world spot
                float wx = pinnedWorld[i * 2], wy = pinnedWorld[i * 2 + 1];
                float lx = (wx - bodyX) * fwdY - (wy - bodyY) * fwdX;   // to the right
                float ly = (wx - bodyX) * fwdX + (wy - bodyY) * fwdY;   // forward
                if (k < 1f) {
                    float sy = pinStart[i * 3 + 1];
                    ly = sy + (pinLand[i] - sy) * k;
                    lx = pinStart[i * 3] + (rootX - pinStart[i * 3]) * k;
                }
                float z = pinStart[i * 3 + 2] * (1f - k);
                if (k >= 1f && Math.hypot(lx - rootX, ly - neutral) > RESTEP_DISTANCE) {
                    // turned or shuffled too far off this foot: step it back under its root
                    pin(i, lx, ly, 0f, neutral, fwdX, fwdY);
                    pinStart[i * 3 + 2] = 0.06f;
                }
                feet[i * 3] = lx;
                feet[i * 3 + 1] = ly;
                feet[i * 3 + 2] = z;
            } else {
                boolean gallop = m == Mode.GALLOP;
                float duty = gallop ? GALLOP_DUTY : TROT_DUTY;
                float stride = gallop ? GALLOP_STRIDE : TROT_STRIDE;
                float lift = gallop ? GALLOP_LIFT : TROT_LIFT;
                float u = frac(phi + (gallop ? GALLOP_OFFSET : TROT_OFFSET)[i]);
                float half = duty * stride / 2;
                float fy, fz;
                if (u < duty) {
                    fy = half - (u / duty) * 2 * half;
                    fz = 0f;
                } else {
                    float k = (u - duty) / (1 - duty);
                    fy = -half + 2 * half * smooth(k);
                    fz = lift * (float) Math.sin(Math.PI * k);
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
        // the world spot the foot comes down on
        pinnedWorld[i * 2] = bodyX + rootX * fwdY + landY * fwdX;
        pinnedWorld[i * 2 + 1] = bodyY - rootX * fwdX + landY * fwdY;
    }

    private void solveLegs(float shY, float shZ, float hpY, float hpZ) {
        float[] j = new float[4];
        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            float side = i % 2 == 0 ? -1f : 1f;
            float rootX = side * (front ? FRONT_X : HIND_X) + (front ? shx : hpx);
            float rootY = front ? shY : hpY;
            float rootZ = (front ? shZ : hpZ) - 0.02f;
            float fx = feet[i * 3], fy = feet[i * 3 + 1], fz = feet[i * 3 + 2];
            int p = LEG_START[i];
            set(p, rootX, rootY, rootZ);
            if (front) {
                ik2(rootY, rootZ, fy, fz, FRONT_A, FRONT_B, -1f, j);
                set(p + 1, (rootX + fx) / 2, j[0], j[1]);    // the leg leans from the swaying shoulder to the foot
                set(p + 2, fx, j[2], j[3]);
            } else {
                // the metatarsus stands near-vertical, leaning back
                ik2(rootY, rootZ, fy - 0.03f, fz + HIND_C, HIND_A, HIND_B, 1f, j);
                set(p + 1, rootX + (fx - rootX) * 0.33f, j[0], j[1]);
                set(p + 2, rootX + (fx - rootX) * 0.7f, j[2], j[3]);
                set(p + 3, fx, fy, fz);
            }
        }
    }

    // ------------------------------------------------------------------ strikes

    private void advanceStrike(float cooldown, boolean engaged, float dt) {
        boolean landed = cooldown > lastCooldown + 1e-4f;
        lastCooldown = cooldown;
        if (sinceHit < Float.MAX_VALUE) sinceHit += dt;
        if (landed) {
            if (!winding) chooseStrike();
            winding = false;
            sinceHit = 0f;
        } else if (!winding && engaged && sinceHit >= strikeLength()
                && cooldown <= windLead(nextIsStab())) {
            chooseStrike();
            winding = true;
        } else if (winding && !engaged) {
            winding = false;                       // the target went away: let the arm relax
        }
        if (winding && cooldown <= 0f) {
            // ready but still closing: carry the spike cocked, never a swipe held out wide
            strikeIsStab = true;
        }
        for (int a = 0; a < 2; a++) {
            float[] target = armTarget(a, cooldown);
            if (target == null) {
                float k = Math.min(1f, dt / RELAX_SECONDS);
                for (int q = 0; q < 4; q++) arm[a][q] += (REST[q] - arm[a][q]) * k;
            } else {
                System.arraycopy(target, 0, arm[a], 0, 4);
            }
        }
    }

    private void chooseStrike() {
        strikeArm = strikeCount % 2;
        strikeIsStab = nextIsStab();
        strikeCount++;
    }

    /** Stab or swipe for the next strike: a fixed scramble of this Runner's seed and strike count. */
    private boolean nextIsStab() {
        long h = (seed ^ 0x9E3779B97F4A7C15L) * 0xBF58476D1CE4E5B9L + strikeCount * 0x94D049BB133111EBL;
        h ^= h >>> 29;
        return (h & 4L) == 0L;
    }

    /** Seconds before the damage tick a wind-up starts: the spike is fully driven home as it lands. */
    private static float windLead(boolean stab) {
        return stab ? STAB_WIND + STAB_DRIVE : SWIPE_WIND + SWIPE_HIT;
    }

    private float strikeLength() {
        return strikeIsStab ? STAB_HOLD + STAB_RECOVER : SWIPE_CUT - SWIPE_HIT + SWIPE_BACK;
    }

    /** Seconds relative to the damage tick: negative while winding up, from zero once it landed. */
    private float strikeClock(float cooldown) {
        if (!winding) return sinceHit;
        // ready but out of reach: hold cocked rather than thrust at nothing
        if (cooldown <= 0f) return -STAB_DRIVE;
        return -cooldown;
    }

    /** The scripted pose of arm a this tick, or null when it is free to relax to rest. */
    private float[] armTarget(int a, float cooldown) {
        if (a != strikeArm || (!winding && sinceHit >= strikeLength())) return null;
        float t = strikeClock(cooldown);
        if (strikeIsStab) {
            // bow the elbow out, drive the point home on the damage tick, hold, recover
            if (t < -STAB_DRIVE) return lerp(REST, COCKED, smooth((t + STAB_WIND + STAB_DRIVE) / STAB_WIND));
            if (t < 0f) return lerp(COCKED, THRUST, easeOut((t + STAB_DRIVE) / STAB_DRIVE));
            if (t < STAB_HOLD) return THRUST;
            return lerp(THRUST, REST, smooth((t - STAB_HOLD) / STAB_RECOVER));
        }
        // turn out wide, cut across the front connecting a little past its middle, come back
        if (t < -SWIPE_HIT) return lerp(REST, SWIPE_OUT, smooth((t + SWIPE_WIND + SWIPE_HIT) / SWIPE_WIND));
        float u = t + SWIPE_HIT;                    // seconds into the cut
        if (u < SWIPE_CUT) return lerp(SWIPE_OUT, SWIPE_CUT_POSE, easeOut(u / SWIPE_CUT));
        return lerp(SWIPE_CUT_POSE, REST, smooth((u - SWIPE_CUT) / SWIPE_BACK));
    }

    /** The body sinks onto its haunches as a stab cocks and comes up through the drive. */
    private float crouch(float cooldown) {
        if (!strikeIsStab || (!winding && sinceHit >= strikeLength())) return 0f;
        float t = strikeClock(cooldown);
        if (t < -STAB_DRIVE) return 0.035f * smooth((t + STAB_WIND + STAB_DRIVE) / STAB_WIND);
        return 0.035f * (1f - smooth((t + STAB_DRIVE) / 0.28f));
    }

    /** The body lunges onto a stab's drive and settles back as the arm recovers. */
    private float lunge(float cooldown) {
        if (!strikeIsStab || (!winding && sinceHit >= strikeLength())) return 0f;
        float t = strikeClock(cooldown);
        if (t < -STAB_DRIVE) return -PULL_BACK * smooth((t + STAB_WIND + STAB_DRIVE) / STAB_WIND);
        if (t < STAB_HOLD) {
            return -PULL_BACK + (LUNGE + PULL_BACK) * easeOut((t + STAB_DRIVE) / (STAB_DRIVE + STAB_HOLD));
        }
        return LUNGE * (1f - smooth((t - STAB_HOLD) / STAB_RECOVER));
    }

    /**
     * The shoulders' turn into the blow. Positive turns counter-clockwise, which takes the left
     * shoulder back: a left-arm blow winds positive and drives negative, a right-arm one the reverse.
     */
    private float twist(float cooldown) {
        if (!winding && sinceHit >= strikeLength()) return 0f;
        float t = strikeClock(cooldown);
        float side = strikeArm == 0 ? 1f : -1f;
        if (strikeIsStab) {
            if (t < -STAB_DRIVE) return STAB_TWIST * side * smooth((t + STAB_WIND + STAB_DRIVE) / STAB_WIND);
            if (t < 0f) return STAB_TWIST * side * (1f - 2f * easeOut((t + STAB_DRIVE) / STAB_DRIVE));
            if (t < STAB_HOLD) return -STAB_TWIST * side;
            return -STAB_TWIST * side * (1f - smooth((t - STAB_HOLD) / STAB_RECOVER));
        }
        if (t < -SWIPE_HIT) return SWIPE_WIND_TWIST * side * smooth((t + SWIPE_WIND + SWIPE_HIT) / SWIPE_WIND);
        float u = t + SWIPE_HIT;
        if (u < SWIPE_CUT) {
            float k = easeOut(u / SWIPE_CUT);
            return side * (SWIPE_WIND_TWIST + (-SWIPE_CUT_TWIST - SWIPE_WIND_TWIST) * k);
        }
        return -SWIPE_CUT_TWIST * side * (1f - smooth((u - SWIPE_CUT) / SWIPE_BACK));
    }

    private void solveArms(float shY, float shZ) {
        float[] j = new float[4];
        float[] e = new float[3], w = new float[3], b = new float[3];
        for (int a = 0; a < 2; a++) {
            float side = a == 0 ? -1f : 1f;
            float[] q = arm[a];
            b[0] = side * ARM_X + shx;
            b[1] = shY - 0.02f;
            b[2] = shZ + ARM_UP;
            ik2(0f, 0f, q[1], q[2], ARM_A, ARM_B, -1f, j);
            double yaw = Math.toRadians(q[0]);
            float sy = (float) Math.sin(yaw), cy = (float) Math.cos(yaw);
            e[0] = b[0] + side * j[0] * sy; e[1] = b[1] + j[0] * cy; e[2] = b[2] + j[1];
            w[0] = b[0] + side * j[2] * sy; w[1] = b[1] + j[2] * cy; w[2] = b[2] + j[3];
            if (Math.abs(q[3]) > 0.01f) {
                // turn the elbow about the shoulder-wrist line, whichever way takes it outward
                float[] r1 = rollAbout(b, w, e, q[3]);
                float[] r2 = rollAbout(b, w, e, -q[3]);
                float[] r = side * r1[0] >= side * r2[0] ? r1 : r2;
                e[0] = r[0]; e[1] = r[1]; e[2] = r[2];
            }
            float dx = w[0] - e[0], dy = w[1] - e[1], dz = w[2] - e[2];
            float n = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (n < 1e-6f) n = 1f;
            int p = ARM_START[a];
            set(p, b[0], b[1], b[2]);
            set(p + 1, e[0], e[1], e[2]);
            set(p + 2, w[0], w[1], w[2]);
            set(p + 3, w[0] + ARM_SPIKE * dx / n, w[1] + ARM_SPIKE * dy / n, w[2] + ARM_SPIKE * dz / n);
        }
    }

    /**
     * Bows the spine into the turn about mid-spine: the shoulders and everything mounted on them
     * swing one way, the hips and the tail the other, so the body draws a C toward the inside of
     * the turn while the planted feet stay put and the legs lean.
     */
    private void bendSpine() {
        if (Math.abs(bend) < 0.05f && Math.abs(twist) < 0.05f) return;
        float mx = x(MID), my = y(MID);
        for (int p = 0; p < POINTS; p++) {
            float share = BEND_SHARE[p];
            if (share == 0f) continue;
            // the front half turns with the blow; the rear half turns a little against it
            double a = Math.toRadians(bend / 2 * share
                    + (share > 0f ? twist * share : -HIP_COUNTER * twist * -share));
            float c = (float) Math.cos(a), sn = (float) Math.sin(a);
            float dx = x(p) - mx, dy = y(p) - my;
            pose[p * 3] = mx + dx * c - dy * sn;
            pose[p * 3 + 1] = my + dx * sn + dy * c;
        }
    }

    /** The shoulders' turn into the current blow, degrees, positive counter-clockwise. */
    public float twistDegrees() { return twist; }

    /** Spine bend this tick, degrees; positive bows the body into a left turn. */
    public float bendDegrees() { return bend; }

    /** The jaws close on the load beside the spike, the neck reaching no further than it can. */
    private void latchOn(float loadX, float loadY, float fwdX, float fwdY) {
        float dx = (loadX - bodyX) / scale, dy = (loadY - bodyY) / scale;
        // the body is drawn CARRY_BACK behind the sim position, so the load sits that much further ahead
        float lx = dx * fwdY - dy * fwdX, ly = dx * fwdX + dy * fwdY + CARRY_BACK * latch, lz = 0.18f;
        float hx = x(HEAD), hy = y(HEAD), hz = z(HEAD);
        float d = (float) Math.sqrt((lx - hx) * (lx - hx) + (ly - hy) * (ly - hy) + (lz - hz) * (lz - hz));
        float k = latch * Math.min(1f, LATCH_REACH / Math.max(1e-6f, d));
        set(HEAD, hx + (lx - hx) * k, hy + (ly - hy) * k, hz + (lz - hz) * k);
    }

    // ------------------------------------------------------------------ death

    /**
     * Advances a dead Runner: on its first dead tick it bursts ({@link RavBurst}). The trunk splits at
     * mid-spine and the two halves slump apart; the head and one or two limbs come away, the other limbs
     * staying on their half. Seeded by the rig, so every replay bursts the same way. A body dragged off by
     * another Runner moves with its position.
     */
    public void advanceDead(float posX, float posY, float dt) {
        if (!started) {
            bodyX = posX;
            bodyY = posY;
            started = true;
            solveLegs(SPINE / 2, SHOULDER_Z, -SPINE / 2, HIP_Z);
            solveArms(SPINE / 2, SHOULDER_Z);
        }
        bodyX = posX;
        bodyY = posY;
        if (!dead) {
            dead = true;
            burst.start(pose, pose[MID * 3], pose[MID * 3 + 1], seed);
        }
        burst.advance(dt);
    }

    /**
     * Where point lies now in the rig frame (x right, y forward, z up) as part of the given piece,
     * offset by (addX, addY) in the pose it died in; out gets x, y, z. Only meaningful once dead.
     */
    public void deadPoint(int pieceIndex, int point, float addX, float addY, float[] out) {
        burst.deadPoint(pieceIndex, point, addX, addY, out);
    }

    /** The burst this Runner died in, for the ichor drawing; meaningful once dead. */
    public RavBurst burst() { return burst; }

    /** 0 at the burst, 1 once the pieces have gone limp and lie flat on the ground. */
    public float flat() { return burst.flat(); }

    /** Seconds since the Runner burst. */
    public float sinceDeath() { return burst.sinceDeath(); }

    /** Where piece i lies in the rig frame, and whether it has come to rest (and left a smear). */
    public float pieceX(int i) { return burst.pieceX(i); }
    public float pieceY(int i) { return burst.pieceY(i); }
    public boolean pieceResting(int i) { return burst.pieceResting(i); }

    public int dropCount() { return burst.dropCount(); }

    /** Droplet i in the rig frame, whether it has landed, and the size in cells of its splat. */
    public float dropX(int i) { return burst.dropX(i); }
    public float dropY(int i) { return burst.dropY(i); }
    public float dropZ(int i) { return burst.dropZ(i); }
    public boolean dropLanded(int i) { return burst.dropLanded(i); }
    public float dropSize(int i) { return burst.dropSize(i); }
    public float dropAngle(int i) { return burst.dropAngle(i); }
    public int dropVariant(int i) { return burst.dropVariant(i); }

    /** The pool spreading under the middle: its diameter in cells, its turn and which splat it is. */
    public float poolCells() { return burst.poolCells(); }
    public float poolX() { return burst.poolX(); }
    public float poolY() { return burst.poolY(); }
    public float poolAngle() { return burst.poolAngle(); }
    public int poolVariant() { return burst.poolVariant(); }

    /**
     * What a skitterer is doing with its head ({@code SwarmWorkers.Show}): {@link #WORK_NONE}, {@link #WORK_HEAD_DOWN}
     * over the work, or carrying {@link #WORK_FEED} or {@link #WORK_PULP} in its mandibles. Read each tick; a Runner
     * is never told anything but none.
     */
    public void setWork(int work) {
        this.work = work;
    }

    /** What its mandibles hold: {@link #WORK_FEED}, {@link #WORK_PULP}, or {@link #WORK_NONE}. */
    public int held() {
        return dead || (work != WORK_FEED && work != WORK_PULP) ? WORK_NONE : work;
    }

    /**
     * How far the head is down over the work, 0 (carried) to 1 (in it): the composer draws a head that far down
     * shorter, foreshortened as it pitches toward the ground, and a little darker in the body's shadow.
     */
    public float headDip() { return dead ? 0f : dip; }

    /** How far forward of the sim position the drawn body sits, cells; negative while hauling. */
    public float drawOffset() { return dead ? 0f : -CARRY_BACK * latch; }

    /** Whether the Runner is dead and lying (or falling). */
    public boolean dead() { return dead; }

    // ------------------------------------------------------------------ solvers

    /**
     * Two bones in a vertical plane from (rootY, rootZ) toward (tY, tZ). bend +1 puts the joint
     * forward of the line, -1 behind it. Writes joint y, z, end y, z into out.
     */
    static void ik2(float rootY, float rootZ, float tY, float tZ, float a, float b, float bend, float[] out) {
        float dy = tY - rootY, dz = tZ - rootZ;
        float d = (float) Math.max(Math.abs(a - b) + 1e-4, Math.min(a + b - 1e-4, Math.hypot(dy, dz)));
        double base = Math.atan2(dz, dy);
        double off = Math.acos(Math.max(-1, Math.min(1, (a * a + d * d - b * b) / (2 * a * d))));
        float j1y = rootY + a * (float) Math.cos(base + off), j1z = rootZ + a * (float) Math.sin(base + off);
        float j2y = rootY + a * (float) Math.cos(base - off), j2z = rootZ + a * (float) Math.sin(base - off);
        boolean first = bend * j1y >= bend * j2y;
        out[0] = first ? j1y : j2y;
        out[1] = first ? j1z : j2z;
        out[2] = rootY + d * (float) Math.cos(base);
        out[3] = rootZ + d * (float) Math.sin(base);
    }

    /** Point p turned about the axis a to b by deg (Rodrigues). */
    static float[] rollAbout(float[] a, float[] b, float[] p, float deg) {
        double kx = b[0] - a[0], ky = b[1] - a[1], kz = b[2] - a[2];
        double n = Math.sqrt(kx * kx + ky * ky + kz * kz);
        if (n == 0) n = 1;
        kx /= n; ky /= n; kz /= n;
        double vx = p[0] - a[0], vy = p[1] - a[1], vz = p[2] - a[2];
        double th = Math.toRadians(deg), c = Math.cos(th), s = Math.sin(th);
        double kv = kx * vx + ky * vy + kz * vz;
        double cx = ky * vz - kz * vy, cy = kz * vx - kx * vz, cz = kx * vy - ky * vx;
        return new float[]{
                (float) (a[0] + vx * c + cx * s + kx * kv * (1 - c)),
                (float) (a[1] + vy * c + cy * s + ky * kv * (1 - c)),
                (float) (a[2] + vz * c + cz * s + kz * kv * (1 - c))};
    }

    private void set(int point, float x, float y, float z) {
        pose[point * 3] = x;
        pose[point * 3 + 1] = y;
        pose[point * 3 + 2] = z;
    }

    private static float[] lerp(float[] p, float[] q, float k) {
        float[] r = new float[p.length];
        for (int i = 0; i < p.length; i++) r[i] = p[i] + (q[i] - p[i]) * k;
        return r;
    }

    static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3 - 2 * t);
    }

    static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float u = 1 - t;
        return 1 - u * u * u;
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    // ------------------------------------------------------------------ read side

    /** Whether the rig has been advanced at least once and so holds a pose. */
    public boolean posed() { return started; }
    /** The body position the pose was solved at; the renderer draws from here, not the live column. */
    public float bodyX() { return bodyX; }
    public float bodyY() { return bodyY; }
    /** Facing the pose was solved at, in sprite degrees. */
    public float facingDegrees() { return facingDeg; }
    /** Point i's rig-frame coordinates (x right, y forward, z up, cells). */
    public float x(int point) { return pose[point * 3]; }
    public float y(int point) { return pose[point * 3 + 1]; }
    public float z(int point) { return pose[point * 3 + 2]; }
    /** World position of a pinned foot, for tests: x, y. */
    float pinnedWorldX(int leg) { return pinnedWorld[leg * 2]; }
    float pinnedWorldY(int leg) { return pinnedWorld[leg * 2 + 1]; }
    boolean planted() { return mode == Mode.PLANT; }
    boolean striking() { return winding || sinceHit < strikeLength(); }
    int strikeArm() { return strikeArm; }
    boolean strikeIsStab() { return strikeIsStab; }
}
