package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.unit.SwarmStage;

/**
 * The Matriarch's skeleton, advanced once per simulation tick: an elephant's build under a plated body,
 * the legs pillars under it, two scythes from under the crown's rim. Presentation only; it reads where the
 * sim put the body and what its behaviour is doing with its scythes and crown, and nothing in the
 * simulation reads it back.
 *
 * <p>The rig frame is the body's own, in cells, centred on the sim position (the hero layer's row 900):
 * x to the right, y forward. On the move the feet follow a four-beat walk advanced by distance travelled,
 * so a supporting foot holds its spot; stood still or in an action they are planted, set down where they
 * were, and one that drifts too far from under its root (the body turning on the spot) steps back under
 * it, one foot at a time. Each foot carries a load that eases in after it lands, and the shoulders and
 * hips lean toward the side of their pair that carries more, as the Brute's do.
 *
 * <p>An action ({@link #REAP_RIGHT} ...) is posed from its age. A reap cocks the scythe back and out,
 * lashes it forward and inward across the front past the crown, holds and recovers; the shoulders lunge
 * with it and wind against the blade, and the head turns toward it with its jaws open. A crown blow rocks
 * the body back onto its haunches with the crown up, then drives it forward and down. On emergence it
 * roars: the head rises, the jaws gape and the head shakes. Walking, it looks slowly left and right. Faster, the
 * walk lengthens into a run, on longer strides with less of each on the ground; before a run the crown dips and the
 * jaws part.
 *
 * <p>Ported from the approved motion study 4, {@code art-source/rav-redesign/exploration/matriarch_skeleton.py}.
 */
public final class RavMatriarchRig {

    /** Hero layer px per cell at the Matriarch's size; the export and the composer share it. */
    public static final float U_LAYER = 327.4f;
    /** Layer rows of the body anchor (the sim position), the front feet and the hind feet. */
    public static final float ANCHOR_ROW = 900f, SHOULDER_ROW = 860f, HIP_ROW = 1270f;
    static final float SHOULDER_Y = (ANCHOR_ROW - SHOULDER_ROW) / U_LAYER;
    static final float HIP_Y = (ANCHOR_ROW - HIP_ROW) / U_LAYER;
    /** Each foot's rest x, cells: front left, front right, hind left, hind right. */
    static final float[] FOOT_X = {(185f - 512f) / U_LAYER, (850f - 512f) / U_LAYER,
            (255f - 512f) / U_LAYER, (775f - 512f) / U_LAYER};

    /** Actions, in the order of {@code MatriarchActions.Kind}. */
    public static final int NONE = 0, ROAR = 1, REAP_RIGHT = 2, REAP_LEFT = 3, CROWN = 4, RUN_TELL = 5;
    /** Scythe indices. */
    public static final int LEFT = 0, RIGHT = 1;

    // the walk: duty factor, stride and lift (cells), per-leg phase offset (FL, FR, HL, HR)
    private static final float DUTY = 0.74f, STRIDE = 0.75f, LIFT = 0.13f;
    private static final float[] OFFSET = {0.25f, 0.75f, 0.0f, 0.5f};
    private static final float STAND_BELOW = 0.1f;
    private static final float LAND_SECONDS = 0.15f, BLEND_SECONDS = 0.35f, LOAD_SECONDS = 0.08f;
    /** A planted foot this far from under its root steps back under it, over STEP_SECONDS. */
    private static final float RESTEP_DISTANCE = 0.3f, STEP_SECONDS = 0.35f, STEP_LIFT = 0.1f;
    private static final float RUN = 0.6f;
    // the run, blended in by speed: a longer stride on less of the ground, the feet lifted higher
    private static final float RUN_STRIDE = 1.5f, RUN_DUTY = 0.5f, RUN_LIFT = 0.22f, RUN_SWAY = 1f;
    /** Speeds (adult cells a second) the gait is all walk below and all run above, and how fast it changes. */
    private static final float WALK_SPEED = 0.9f, RUN_SPEED = 3f, GAIT_SECONDS = 0.3f;
    // the tell before a run: the crown lowers and the jaws part
    static final float TELL_SECONDS = 0.6f;

    // a reap: wind, lash, hold, recover (seconds)
    static final float S_WIND = 0.30f, S_LASH = 0.13f, S_HOLD = 0.15f, S_BACK = 0.45f;
    static final float LASH_TO = 205f;
    // a crown blow
    static final float WIND = 0.45f, SHOVE = 0.12f, HOLD = 0.30f, BACK = 0.40f;
    static final float ROAR_SECONDS = 1.0f;
    /** The walk's look round: keyframes (seconds, degrees) of one loop; + is toward its right. */
    private static final float[][] LOOK = {{0f, 0f}, {0.6f, 0f}, {1.4f, -14f}, {2.3f, -14f}, {3.3f, 16f},
            {4.2f, 16f}, {5.2f, 0f}, {9f, 0f}};

    private float bodyX, bodyY, facingDeg;
    private boolean started, walking, feetValid;
    /** 0 a walk, 1 a run. */
    private float gait;
    private float phi;
    private final float[] feet = new float[12];
    private final float[] stance = new float[4];
    private final float[] load = {1f, 1f, 1f, 1f};
    private final float[] pinnedWorld = new float[8];
    private final float[] pinStart = new float[12];
    private final float[] pinTarget = new float[8];
    private final float[] pinAge = new float[4];
    private final float[] pinSeconds = new float[4];
    private final boolean[] pinned = new boolean[4];
    private final float[] blendFrom = new float[12];
    private float blendAge = -1f;
    private float lookTime, look;

    private float shoulderX, shoulderY, hipX, hipY;
    private float headDy, headK = 1f, headYaw;
    private boolean gape;
    private final float[] bladeAngle = new float[2], bladeReach = new float[2];

    /** @param seed the body's id, so two Matriarchs do not look round in step */
    public RavMatriarchRig(long seed) {
        lookTime = Math.floorMod(seed * 0x9E3779B97F4A7C15L, 9000L) / 1000f;
    }

    /** The body's growth stage, and its scale against its adult: what it is drawn at and with. */
    private SwarmStage stage = SwarmStage.ADULT;
    private float scale = 1f;

    /** Sets the growth stage; read each tick, so a Matriarch that matures in the field grows with it. */
    public void setStage(SwarmStage stage) {
        this.stage = SwarmStage.of(stage);
        this.scale = this.stage.matriarchScale;
    }

    public SwarmStage stage() { return stage; }

    /** The stage's draw scale against the adult ({@link SwarmStage#scale}). */
    public float scale() { return scale; }

    /**
     * Advances the rig one tick.
     *
     * @param facing body facing in sprite degrees (0 north, counter-clockwise)
     * @param action {@link #NONE}, {@link #ROAR}, {@link #REAP_RIGHT}, {@link #REAP_LEFT}, {@link #CROWN} or
     *               {@link #RUN_TELL}
     * @param age    seconds since the action began
     */
    public void advance(float posX, float posY, float facing, int action, float age, float dt) {
        if (!started) {
            bodyX = posX;
            bodyY = posY;
            facingDeg = facing;
            started = true;
        }
        float dx = posX - bodyX, dy = posY - bodyY;
        float moved = (float) Math.hypot(dx, dy);
        if (moved > 2f) {                         // a teleport: start over where it landed
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
        facingDeg = facing;
        double rad = Math.toRadians(facing);
        float fwdX = (float) -Math.sin(rad), fwdY = (float) Math.cos(rad);
        float along = dx * fwdX + dy * fwdY;
        boolean walk = action == NONE && moved / dt >= STAND_BELOW;
        float gaitTarget = walk
                ? Math.max(0f, Math.min(1f, (moved / dt - WALK_SPEED) / (RUN_SPEED - WALK_SPEED))) : 0f;
        gait += (gaitTarget - gait) * (1f - (float) Math.exp(-dt / GAIT_SECONDS));
        if (walk) phi += along / stride();
        if (walk && !walking && feetValid) {      // leave the plant by blending the feet onto the gait
            System.arraycopy(feet, 0, blendFrom, 0, 12);
            blendAge = 0f;
        }
        if (!walk && walking) for (int i = 0; i < 4; i++) pinned[i] = false;
        walking = walk;
        solveFeet(dt, fwdX, fwdY);
        feetValid = true;

        float rate = 1f - (float) Math.exp(-dt / LOAD_SECONDS);
        for (int i = 0; i < 4; i++) load[i] += ((feet[i * 3 + 2] < 0.01f ? 1f : 0f) - load[i]) * rate;
        float run = walk ? RUN + (RUN_SWAY - RUN) * gait : 0f;
        float pushL = push(2), pushR = push(3);
        float swayF = 0.07f * (load[1] - load[0]) * run;
        float swayH = 0.09f * (load[3] - load[2]) * run + 0.04f * run * (pushL - pushR);
        float surge = 0.05f * run * (pushL + pushR);

        // the action: scythes, lunge, head
        bladeAngle[LEFT] = bladeAngle[RIGHT] = bladeReach[LEFT] = bladeReach[RIGHT] = 0f;
        float lunge = 0f, actionYaw = 0f;
        headDy = 0f;
        headK = 1f;
        gape = false;
        if (action == REAP_RIGHT || action == REAP_LEFT) {
            int side = action == REAP_RIGHT ? RIGHT : LEFT;
            lunge = strike(age, side);
            boolean lashing = age >= S_WIND && age < S_WIND + S_LASH + S_HOLD;
            float turn = age < S_WIND + S_LASH + S_HOLD ? Math.min(1f, age / S_WIND)
                    : Math.max(0f, 1f - (age - S_WIND - S_LASH - S_HOLD) / S_BACK);
            headDy = -20f * Math.min(1f, Math.max(0f, lunge) / 0.16f);
            gape = lashing;
            actionYaw = (side == RIGHT ? 1f : -1f) * 16f * RavRunnerRig.smooth(turn);
        } else if (action == CROWN) {
            lunge = shove(age);
        } else if (action == RUN_TELL) {
            float g = RavRunnerRig.smooth(Math.min(1f, age / (TELL_SECONDS * 0.5f)));
            headDy = 16f * g;
            lunge = -0.08f * g;
            gape = g >= 0.5f;
        } else if (action == ROAR) {
            float g = (float) Math.sin(Math.PI * Math.min(1f, age / ROAR_SECONDS));
            headDy = -30f * g;
            headK = 1f + 0.05f * g;
            gape = 1.6f * g >= 0.5f;
            actionYaw = 7f * g * (float) Math.sin(2 * Math.PI * 3.5f * age);
        }
        // the body winds against a swinging blade
        float twist = 0.12f * clampTwist(bladeAngle[RIGHT]) - 0.12f * clampTwist(bladeAngle[LEFT]);
        shoulderX = swayF + twist;
        shoulderY = SHOULDER_Y + lunge + surge * 0.4f;
        hipX = swayH - 0.3f * twist;
        hipY = HIP_Y + surge;

        // looking round while it walks or stands; an action's own turn of the head replaces it
        lookTime += dt;
        float lookTarget = action == NONE ? lookCurve(lookTime % 9f) : 0f;
        look += (lookTarget - look) * (1f - (float) Math.exp(-dt / 0.3f));
        headYaw = look + actionYaw;
    }

    private static float clampTwist(float angle) {
        return Math.max(-0.2f, Math.min(1f, angle / LASH_TO));
    }

    /** Poses one scythe at {@code age} into its reap; returns the shoulders' lunge, cells. */
    private float strike(float u, int side) {
        float a, reach, lunge;
        if (u < S_WIND) {
            float s = RavRunnerRig.smooth(u / S_WIND);
            a = -22f * s;
            reach = 0.10f * s;
            lunge = -0.06f * s;
        } else if (u < S_WIND + S_LASH) {
            float s = RavRunnerRig.easeOut((u - S_WIND) / S_LASH);
            a = -22f + (LASH_TO + 22f) * s;
            reach = 0.10f + 0.12f * s;
            lunge = -0.06f + 0.22f * s;
        } else if (u < S_WIND + S_LASH + S_HOLD) {
            a = LASH_TO;
            reach = 0.22f;
            lunge = 0.16f;
        } else {
            float s = RavRunnerRig.smooth(Math.min(1f, (u - S_WIND - S_LASH - S_HOLD) / S_BACK));
            a = LASH_TO * (1f - s);
            reach = 0.22f * (1f - s);
            lunge = 0.16f * (1f - s);
        }
        bladeAngle[side] = a;
        bladeReach[side] = reach;
        return lunge;
    }

    /** Poses the crown blow at {@code age}; returns the body's offset forward, cells. */
    private float shove(float u) {
        if (u < WIND) {
            float s = RavRunnerRig.smooth(u / WIND);
            headDy = 60f * s;
            headK = 1f + 0.06f * s;
            return -0.25f * s;
        }
        if (u < WIND + SHOVE) {
            float s = RavRunnerRig.easeOut((u - WIND) / SHOVE);
            headDy = 60f - 150f * s;
            headK = 1.06f - 0.12f * s;
            return -0.25f + 0.55f * s;
        }
        if (u < WIND + SHOVE + HOLD) {
            headDy = -90f;
            headK = 0.94f;
            return 0.30f;
        }
        float s = RavRunnerRig.smooth(Math.min(1f, (u - WIND - SHOVE - HOLD) / BACK));
        headDy = -90f * (1f - s);
        headK = 0.94f + 0.06f * s;
        return 0.30f * (1f - s);
    }

    private static float lookCurve(float t) {
        for (int i = 0; i + 1 < LOOK.length; i++) {
            float t0 = LOOK[i][0], t1 = LOOK[i + 1][0];
            if (t >= t0 && t < t1) {
                return LOOK[i][1] + (LOOK[i + 1][1] - LOOK[i][1]) * RavRunnerRig.smooth((t - t0) / (t1 - t0));
            }
        }
        return 0f;
    }

    /** One cycle's stride, cells: the walk's, lengthening into the run's. */
    private float stride() {
        return STRIDE + (RUN_STRIDE - STRIDE) * gait;
    }

    private float push(int leg) {
        return stance[leg] < 0f ? 0f : RavRunnerRig.smooth((stance[leg] - 0.6f) / 0.4f);
    }

    private void solveFeet(float dt, float fwdX, float fwdY) {
        if (walking) {
            for (int i = 0; i < 4; i++) {
                float u = frac(phi + OFFSET[i]);
                float duty = DUTY + (RUN_DUTY - DUTY) * gait;
                float half = duty * stride() / 2;
                float fy, fz;
                if (u < duty) {
                    fy = half - (u / duty) * 2 * half;
                    fz = 0f;
                    stance[i] = u / duty;
                } else {
                    float k = (u - duty) / (1 - duty);
                    fy = -half + 2 * half * RavRunnerRig.smooth(k);
                    fz = (LIFT + (RUN_LIFT - LIFT) * gait) * (float) Math.sin(Math.PI * k);
                    stance[i] = -1f;
                }
                feet[i * 3] = FOOT_X[i];
                feet[i * 3 + 1] = neutral(i) + fy;
                feet[i * 3 + 2] = fz;
            }
            if (blendAge >= 0f) {
                blendAge += dt;
                float k = RavRunnerRig.smooth(Math.min(1f, blendAge / BLEND_SECONDS));
                for (int i = 0; i < 4; i++) {
                    feet[i * 3 + 1] = blendFrom[i * 3 + 1] + (feet[i * 3 + 1] - blendFrom[i * 3 + 1]) * k;
                    feet[i * 3 + 2] = blendFrom[i * 3 + 2] + (feet[i * 3 + 2] - blendFrom[i * 3 + 2]) * k
                            + 0.06f * (float) Math.sin(Math.PI * k);
                }
                if (k >= 1f) blendAge = -1f;
            }
            return;
        }
        blendAge = -1f;
        boolean stepping = false;
        for (int i = 0; i < 4; i++) {
            if (pinned[i] && pinAge[i] < pinSeconds[i] && pinSeconds[i] == STEP_SECONDS) stepping = true;
        }
        for (int i = 0; i < 4; i++) {
            stance[i] = 0.5f;
            if (!pinned[i]) {                     // a foot sets down where it is
                float sx = feetValid ? feet[i * 3] : FOOT_X[i];
                float sy = feetValid ? feet[i * 3 + 1] : neutral(i);
                float sz = feetValid ? feet[i * 3 + 2] : 0f;
                pin(i, sx, sy, sz, FOOT_X[i], sy, LAND_SECONDS, fwdX, fwdY);
            }
            pinAge[i] += dt;
            float k = RavRunnerRig.smooth(Math.min(1f, pinAge[i] / pinSeconds[i]));
            float wx = pinnedWorld[i * 2], wy = pinnedWorld[i * 2 + 1];
            float lx = (wx - bodyX) * fwdY - (wy - bodyY) * fwdX;
            float ly = (wx - bodyX) * fwdX + (wy - bodyY) * fwdY;
            float z = 0f;
            if (k < 1f) {
                lx = pinStart[i * 3] + (pinTarget[i * 2] - pinStart[i * 3]) * k;
                ly = pinStart[i * 3 + 1] + (pinTarget[i * 2 + 1] - pinStart[i * 3 + 1]) * k;
                z = pinSeconds[i] == STEP_SECONDS ? STEP_LIFT * (float) Math.sin(Math.PI * k)
                        : pinStart[i * 3 + 2] * (1f - k);
            } else if (!stepping && Math.hypot(lx - FOOT_X[i], ly - neutral(i)) > RESTEP_DISTANCE) {
                pin(i, lx, ly, 0f, FOOT_X[i], neutral(i), STEP_SECONDS, fwdX, fwdY);
                stepping = true;
            }
            feet[i * 3] = lx;
            feet[i * 3 + 1] = ly;
            feet[i * 3 + 2] = z;
        }
    }

    private static float neutral(int leg) {
        return leg < 2 ? SHOULDER_Y : HIP_Y;
    }

    private void pin(int i, float sx, float sy, float sz, float tx, float ty, float seconds, float fwdX, float fwdY) {
        pinned[i] = true;
        pinAge[i] = 0f;
        pinSeconds[i] = seconds;
        pinStart[i * 3] = sx;
        pinStart[i * 3 + 1] = sy;
        pinStart[i * 3 + 2] = sz;
        pinTarget[i * 2] = tx;
        pinTarget[i * 2 + 1] = ty;
        pinnedWorld[i * 2] = bodyX + tx * fwdY + ty * fwdX;
        pinnedWorld[i * 2 + 1] = bodyY - tx * fwdX + ty * fwdY;
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
    /** A foot, cells in the body frame: front left, front right, hind left, hind right. */
    public float footX(int leg) { return feet[leg * 3]; }
    public float footY(int leg) { return feet[leg * 3 + 1]; }
    public float footZ(int leg) { return feet[leg * 3 + 2]; }
    /** The shoulders and the hips, cells in the body frame; the body is drawn along the line between. */
    public float shoulderX() { return shoulderX; }
    public float shoulderY() { return shoulderY; }
    public float hipX() { return hipX; }
    public float hipY() { return hipY; }
    /** The head's offset back along the body, in hero layer px (negative: thrust forward). */
    public float headDy() { return headDy; }
    /** The head's scale about the neck. */
    public float headK() { return headK; }
    /** The head turned about the neck, degrees; + is toward the creature's right. */
    public float headYaw() { return headYaw; }
    /** Whether the jaws are open: a reap's lash or a roar. */
    public boolean gaping() { return gape; }
    /** A scythe's swing, degrees from rest: + forward and inward across the front. */
    public float bladeAngle(int side) { return bladeAngle[side]; }
    /** How far a scythe's arm reaches out from its rest, cells. */
    public float bladeReach(int side) { return bladeReach[side]; }
    /** Whether the feet are planted this tick. */
    public boolean planted() { return !walking; }
}
