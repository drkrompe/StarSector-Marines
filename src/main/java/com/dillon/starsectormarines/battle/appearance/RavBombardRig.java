package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.unit.SwarmStage;

/**
 * The Bombard's skeleton (`bombard.md`), advanced once per simulation tick: a lumbering crustacean seen from above, its
 * walking legs on either side of a split shell over an amber sac, and a segmented tail laid out behind it that ends in
 * a bony sling. Built from separate parts cut from the picked master. Presentation only; it reads where the sim put the
 * body, its attack cooldown, and how far into its plant and its wind-up the Bombard is, and nothing in the simulation
 * reads it back.
 *
 * <p>The rig frame is the body's own, in cells, centred on the sim position (the master's {@link #ANCHOR_ROW}): x to
 * the right, y forward, z up. Each walking leg keeps its claw planted where it last stepped and turns about its hip
 * to reach it, so the legs push back as the body passes over them; a leg pulled too far from where it rests lifts and
 * steps ahead, in two alternating sets of three, crab-fashion. The same rule steps the legs round as it turns on the
 * spot. The feeler claws beside the head twitch. Standing, the tail lies almost flat out behind and sways.
 *
 * <p>Planting is a dig: through the sim's plant the legs stamp outward into the creep, side against side, the body
 * dipping at each stroke and sinking lower, and it settles with a slight bounce once set. Uprooting is the same in
 * reverse: the legs heave free and the body rises, overshooting a little as it stands. The body's height, its lurch
 * and its rock are springs, so every change of weight settles rather than snaps.
 *
 * <p>The lob is the catapult's. Winding up ({@code windup} 0 to 1 over the sim's wind-up) the tail arches up off the
 * ground and over the back, rising toward the camera and leaning to one side as the master's left frame draws it, until
 * the sling hangs over the shell with the glob glowing in it; the body crouches back on its hind legs and, held at full
 * draw, trembles, while the sac brightens. When the cooldown jumps back up the glob has gone: the tail whips down flat
 * behind and its end lashes from side to side as it settles; the body lurches forward and rocks, the legs brace, and
 * the sac, emptied, flares and goes dull, filling again through the reload.
 *
 * <p>Dead, it falls apart its own way: the sac ruptures in a last amber flash, the shell cracks open down its seam and
 * its halves part, the legs buckle out flat and the body drops between them, and the tail collapses to the ground and
 * flops to one side, over a pool of blood ({@link RavBurst}, which here only spills it). The haze the sim bursts from
 * the sac is the haze's to draw.
 *
 * <p>Built on the approved G1 design, its parts exported by
 * {@code art-source/rav-redesign/artillery/export_bombard_rig.py}.
 */
public final class RavBombardRig {

    /** Master canvas px per cell; the export and the composer share it. */
    public static final float U_LAYER = 330f;
    /** Canvas row of the body anchor (the sim position) and the centre column. */
    public static final float ANCHOR_ROW = 445f, CENTRE_COL = 1078f;
    /** Canvas centre of the exported body box. */
    public static final float BODY_ROW = 440f;
    /** Canvas columns of the shell halves' centres, cut down the seam for the death. */
    public static final float SHELL_L_COL = 1006f, SHELL_R_COL = 1150f;
    /** The legs, as {@code export_bombard_rig.py} cut them: four a side, left then right, front to back; 0 the feeler. */
    public static final int LEGS = 8, LEGS_PER_SIDE = 4;
    /** Each leg's box centre, its hip (where it leaves the shell) and its foot (the claw tip), canvas px. */
    public static final float[] LEG_COL = {959f, 882f, 890f, 914f, 1198f, 1274f, 1267f, 1242f};
    public static final float[] LEG_ROW = {281f, 434f, 530f, 620f, 280f, 434f, 531f, 620f};
    public static final float[] HIP_COL = {957f, 935f, 938f, 952f, 1199f, 1221f, 1218f, 1204f};
    public static final float[] HIP_ROW = {296f, 435f, 505f, 579f, 295f, 434f, 504f, 579f};
    static final float[] FOOT_COL = {938f, 809f, 823f, 854f, 1219f, 1347f, 1332f, 1300f};
    static final float[] FOOT_ROW = {217f, 491f, 604f, 711f, 217f, 491f, 604f, 711f};
    /** The two sets that step in turn, -1 for a feeler: a leg lifts only while no leg of the other set is in the air. */
    static final int[] LEG_SET = {-1, 0, 1, 0, -1, 1, 0, 1};
    /** The exported tail slices, root to sling. */
    public static final int TAIL_ART_SLICES = 6;
    /** Canvas row of the tail's root and of each slice's seam, root to the sling's hinge. */
    public static final float TAIL_ROOT_ROW = 680f, SLICE_PX = 30f;
    /** Canvas row of the sling sprite's centre, and of the cup where the glob sits. */
    public static final float SLING_ROW = 932f, CUP_ROW = 935f;
    /** The tail's joints: one per slice, then the sling's hinge. */
    public static final int TAIL_SEGMENTS = TAIL_ART_SLICES + 1;

    /** The wound-up arch: each joint's lift off the one before it at full wind-up, degrees, root to sling. */
    static final float[] PITCH = {30f, 45f, 40f, 35f, 30f, 20f, 10f};
    /** At full wind-up the arch leans to one side, as the master's tail lies along the shell's flank: degrees. */
    static final float[] LEAN = {8f, 5f, 2f, 0f, 0f, 0f, 0f};
    /** How much larger a slice draws per cell it stands off the ground, toward the camera. */
    static final float PERSPECTIVE = 1.3f;
    /** The curl standing, and the tail's slow sway, degrees per joint, and Hz. */
    static final float REST_CURL = 0.04f, SWAY_DEG = 1.5f, SWAY_HZ = 0.3f;
    /** The whip: down flat over this long, then the tail's end lashing this far side to side as it settles. */
    static final float WHIP_SECONDS = 0.12f, SETTLE_SECONDS = 0.9f, LASH_DEG = 9f, LASH_HZ = 2.6f;
    private static final float RESET_JUMP = 0.3f;

    /** The scuttle: cells walked per bob cycle and how far the body bobs, cells. */
    private static final float STRIDE = 0.55f, BOB = 0.015f;
    /**
     * A step: how far (cells) a planted claw may be pulled from where it rests before the leg lifts, how far past its
     * rest it lands, as a share of the pull, and how long it is in the air.
     */
    static final float STEP_REACH = 0.13f, STEP_LEAD = 0.8f, STEP_SECONDS = 0.13f;
    /** A lifted leg draws this much larger at the top of its step; a reaching leg stretches between these. */
    static final float STEP_LIFT = 0.08f, REACH_MIN = 0.88f, REACH_MAX = 1.15f;
    /** The feeler claws' twitch, degrees and Hz. */
    static final float FEELER_DEG = 6f, FEELER_HZ = 0.7f;
    private static final float MOVE_BELOW = 0.08f, MOVE_FULL = 0.8f;
    /** Planted, the legs splay out this much wider and the body sits this much lower, as a share of its size. */
    static final float SPLAY = 0.18f, SINK = 0.1f;
    /** The dig: stamps per side over a plant or an uproot, how far a stamp throws a side's legs out, and dips the body. */
    static final float DIG_STAMPS = 3f, DIG_SPLAY = 0.15f, DIG_DIP = 0.05f, DIG_SWING = 9f;
    /** At full draw the body crouches back this far (cells) and this much lower, and trembles past {@link #TREMBLE_FROM}. */
    static final float CROUCH_BACK = 0.04f, CROUCH_SINK = 0.02f, TREMBLE_FROM = 0.8f, TREMBLE = 0.006f;
    /** The throw's kick: the body's forward speed (cells/s), its press down (share/s), its rock (deg/s), the brace. */
    static final float KICK_LURCH = 1.4f, KICK_PRESS = 0.9f, KICK_ROCK = 60f, BRACE = 0.06f, BRACE_SECONDS = 0.6f;
    /** The springs the body's height, lurch and rock settle on: stiffness (1/s^2) and damping (1/s). */
    private static final float SPRING_K = 140f, SPRING_C = 8f;
    /**
     * The sac's glow: at rest, its pulse, what the wind-up adds, the most the swell reaches, and the throw's flash.
     * Capped so the charcoal shell's plates and the split still read over it at full draw (owner: tone it down).
     */
    private static final float GLOW_REST = 0.25f, GLOW_PULSE = 0.10f, GLOW_WINDUP = 0.40f, GLOW_PEAK = 0.62f,
            GLOW_FLASH = 0.8f;
    /** Emptied by the throw the sac goes this dull and refills over this long; the shell narrows this much with it. */
    static final float EMPTIED = 0.85f, REFILL_SECONDS = 5f, SQUEEZE = 0.03f;

    /** Dead: the rupture's flash fades over this; the shell cracks this far apart (cells each side) and turns out. */
    static final float RUPTURE_SECONDS = 0.6f, CRACK_AT = 0.06f, CRACK_SECONDS = 0.35f, CRACK_CELLS = 0.05f,
            CRACK_DEG = 5f;
    /** Dead: the body drops this much, the legs buckle this much wider and splay this far each, degrees. */
    static final float SLUMP = 0.07f, BUCKLE = 0.16f, BUCKLE_DEG = 7f;
    /** Dead: the tail falls over this long and lies bent to one side by these, degrees per joint. */
    static final float COLLAPSE_SECONDS = 0.45f;
    static final float[] LIMP = {6f, 8f, 9f, 9f, 8f, 7f, 5f};

    private float bodyX, bodyY, facingDeg;
    private boolean started;
    private float stride, motion, time;
    private float lastCooldown = Float.NaN;
    private float sinceFling = Float.MAX_VALUE;
    private float curl, flingFrom, windup, plant, plantRaw = Float.NaN, bob, glow;
    private float digSplayL, digSplayR, digSwing;
    private float emptied;
    /** The springs: the body's height (share of its size), its lurch forward (cells) and its rock (degrees). */
    private float lift, liftV, lurch, lurchV, rock, rockV;
    private float tremble;

    private static final int POINTS = TAIL_SEGMENTS + 2;
    /** The pose point a dead Bombard spills from: the body. */
    public static final int BODY_POINT = TAIL_SEGMENTS + 1;
    private static final int[][] PIECE_POINTS = {{0, BODY_POINT}, {0, 1, 2, 3, 4}, {4, 5, 6, 7}};
    private static final int[] PIECE_PARENT = {-1, 0, 0};
    private static final int[] LOOSE_CANDIDATES = {1, 2};
    private static final float POOL_CELLS = 2.2f;
    private static final float BODY_Z = 0.45f;

    private final long seed;
    private boolean dead;
    private float sinceDeath, curlAtDeath, flopSide;
    private final float[] lashAtDeath = new float[TAIL_SEGMENTS];
    private final float[] deadLegSwing = new float[2];
    /** Each leg's claw in the world (cells), where it steps from, how far through its step, and its pose. */
    private final float[] footX = new float[LEGS], footY = new float[LEGS];
    private final float[] fromX = new float[LEGS], fromY = new float[LEGS];
    private final float[] stepT = new float[LEGS];
    private final boolean[] stepping = new boolean[LEGS];
    private final float[] legAngle = new float[LEGS], legReach = new float[LEGS], legLift = new float[LEGS];
    private final float[] restX = new float[LEGS], restY = new float[LEGS];
    private boolean feetSet;
    /** Each joint's yaw off the facing (degrees) and the tail's points root to sling, x y z in the rig frame. */
    private final float[] yaw = new float[TAIL_SEGMENTS];
    private final float[] lash = new float[TAIL_SEGMENTS];
    private final float[] tail = new float[(TAIL_SEGMENTS + 1) * 3];
    private final float[] pose = new float[POINTS * 3];
    private final RavBurst burst = new RavBurst(PIECE_POINTS, 1, PIECE_PARENT, LOOSE_CANDIDATES, POOL_CELLS);

    private SwarmStage stage = SwarmStage.ADULT;
    private float scale = 1f;

    /** @param seed the body's id, so two Bombards do not sway in step */
    public RavBombardRig(long seed) {
        this.seed = seed;
        time = Math.floorMod(seed * 0x9E3779B97F4A7C15L, 10000L) / 1000f;
    }

    /** Sets the growth stage; read each tick. */
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
     * @param facing   body facing in sprite degrees (0 north, counter-clockwise)
     * @param cooldown the attack cooldown, seconds; a fling resets it upward
     * @param windup   how far into its wind-up, 0 to 1; 0 when it is not winding up
     * @param planted  how far planted, 0 walking to 1 set: rising through a plant, falling through an uproot
     */
    public void advance(float posX, float posY, float facing, float cooldown, float windup, float planted, float dt) {
        if (!started) {
            bodyX = posX;
            bodyY = posY;
            facingDeg = facing;
            started = true;
        }
        float moved = (float) Math.hypot(posX - bodyX, posY - bodyY);
        if (moved > 2f) moved = 0f;               // a teleport
        bodyX = posX;
        bodyY = posY;
        facingDeg = facing;
        moved /= scale;
        stride += moved / STRIDE;
        float moving = smooth((moved / dt - MOVE_BELOW) / (MOVE_FULL - MOVE_BELOW));
        motion += (moving - motion) * (1f - (float) Math.exp(-dt / 0.2f));
        time += dt;
        this.windup = clamp01(windup);

        float raw = clamp01(planted);
        if (Float.isNaN(plantRaw)) plantRaw = raw;
        boolean digging = raw > 0f && raw < 1f && raw != plantRaw;
        plantRaw = raw;
        plant += (raw - plant) * (1f - (float) Math.exp(-dt / 0.3f));
        // a stamp each side in turn, swelling through the middle of the dig, the same strokes either way
        float envelope = digging ? (float) Math.sin(Math.PI * raw) : 0f;
        double stroke = 2 * Math.PI * DIG_STAMPS * raw;
        float stampL = Math.max(0f, (float) Math.sin(stroke)), stampR = Math.max(0f, (float) -Math.sin(stroke));
        digSplayL = ease(digSplayL, DIG_SPLAY * stampL * envelope, dt, 0.06f);
        digSplayR = ease(digSplayR, DIG_SPLAY * stampR * envelope, dt, 0.06f);
        digSwing = ease(digSwing, DIG_SWING * (stampL - stampR) * envelope, dt, 0.06f);

        boolean flung = false;
        if (!Float.isNaN(lastCooldown) && cooldown > lastCooldown + RESET_JUMP) {
            sinceFling = 0f;
            flingFrom = Math.max(curl, 0.85f);
            flung = true;
        } else if (sinceFling < Float.MAX_VALUE) {
            sinceFling += dt;
        }
        lastCooldown = cooldown;

        float sway = (float) Math.sin(2 * Math.PI * SWAY_HZ * time);
        if (sinceFling < WHIP_SECONDS) {
            curl = flingFrom * (1f - RavRunnerRig.easeOut(sinceFling / WHIP_SECONDS));
        } else if (sinceFling < WHIP_SECONDS + SETTLE_SECONDS) {
            curl = REST_CURL * smooth((sinceFling - WHIP_SECONDS) / SETTLE_SECONDS);
        } else {
            float target = REST_CURL + (1f - REST_CURL) * smooth(this.windup);
            curl += (target - curl) * (1f - (float) Math.exp(-dt / 0.08f));
        }
        // after the whip the tail's end lashes side to side, more toward the sling, dying away
        float lashing = 0f;
        if (sinceFling >= WHIP_SECONDS && sinceFling < WHIP_SECONDS + SETTLE_SECONDS) {
            float s = (sinceFling - WHIP_SECONDS) / SETTLE_SECONDS;
            lashing = LASH_DEG * (1f - s) * (1f - s) * (float) Math.sin(2 * Math.PI * LASH_HZ * (sinceFling - WHIP_SECONDS));
        }
        for (int i = 0; i < TAIL_SEGMENTS; i++) {
            lash[i] = SWAY_DEG * sway + lashing * (i + 1f) / TAIL_SEGMENTS;
        }
        solveTail(curl, lash);

        // the body's weight, on springs: sinking as it plants, dipping at each stamp, crouching back at full draw
        float drawn = smooth(this.windup);
        float liftTarget = -SINK * plant - DIG_DIP * (stampL + stampR) * envelope - CROUCH_SINK * drawn;
        float lurchTarget = -CROUCH_BACK * drawn;
        if (flung) {
            // the arm's swing kicks the body forward, presses it down and rocks it to one side
            lurchV += KICK_LURCH;
            liftV -= KICK_PRESS;
            rockV += ((seed & 1L) == 0L ? 1f : -1f) * KICK_ROCK;
            emptied = 1f;
        }
        liftV += (SPRING_K * (liftTarget - lift) - SPRING_C * liftV) * dt;
        lift += liftV * dt;
        lurchV += (SPRING_K * (lurchTarget - lurch) - SPRING_C * lurchV) * dt;
        lurch += lurchV * dt;
        rockV += (SPRING_K * -rock - SPRING_C * rockV) * dt;
        rock += rockV * dt;
        tremble = drawn > TREMBLE_FROM && sinceFling > WHIP_SECONDS + SETTLE_SECONDS
                ? TREMBLE * (drawn - TREMBLE_FROM) / (1f - TREMBLE_FROM) * (float) Math.sin(2 * Math.PI * 11f * time)
                : 0f;
        if (sinceFling > WHIP_SECONDS) emptied = Math.max(0f, emptied - dt / REFILL_SECONDS);

        float cycle = (float) (2 * Math.PI * stride);
        bob = motion * BOB * (float) Math.sin(2 * cycle);
        stepLegs(dt);
        float flash = sinceFling < WHIP_SECONDS ? GLOW_FLASH * (1f - sinceFling / WHIP_SECONDS) : 0f;
        float full = Math.min(GLOW_PEAK,
                GLOW_REST + GLOW_PULSE * (float) Math.sin(2 * Math.PI * 0.5f * time) + GLOW_WINDUP * drawn);
        glow = Math.min(1f, Math.max(flash, full * (1f - EMPTIED * smooth(emptied))));
    }

    /**
     * The walking legs: a planted claw stays where it is in the world while the body moves and turns over it; one
     * pulled more than {@link #STEP_REACH} from its rest lifts, if no leg of the other set is in the air, and lands
     * past its rest by {@link #STEP_LEAD} of the pull. Each leg is then turned about its hip and stretched to reach its
     * claw. Feet hold still through the dig of a plant or an uproot; set, it steps round as it turns.
     */
    private void stepLegs(float dt) {
        double rad = Math.toRadians(facingDeg);
        float fx = (float) -Math.sin(rad), fy = (float) Math.cos(rad);
        for (int i = 0; i < LEGS; i++) {
            restWorld(i, fx, fy);
        }
        if (!feetSet) {
            System.arraycopy(restX, 0, footX, 0, LEGS);
            System.arraycopy(restY, 0, footY, 0, LEGS);
            feetSet = true;
        }
        boolean inAir0 = false, inAir1 = false;
        for (int i = 0; i < LEGS; i++) {
            if (!stepping[i]) continue;
            if (LEG_SET[i] == 0) inAir0 = true;
            else inAir1 = true;
        }
        // the claws hold still while they dig in or heave free; set, it turns on them as it walks on them
        boolean dug = plantRaw > 0f && plantRaw < 1f;
        for (int i = 0; i < LEGS; i++) {
            if (LEG_SET[i] < 0) continue;
            if (stepping[i]) {
                // aimed at where the rest is now, led on by the pull it lifted with, so a moving body's step lands ahead
                float toX = restX[i] + (restX[i] - fromX[i]) * STEP_LEAD * 0.5f;
                float toY = restY[i] + (restY[i] - fromY[i]) * STEP_LEAD * 0.5f;
                stepT[i] = Math.min(1f, stepT[i] + dt / STEP_SECONDS);
                float e = smooth(stepT[i]);
                footX[i] = fromX[i] + (toX - fromX[i]) * e;
                footY[i] = fromY[i] + (toY - fromY[i]) * e;
                if (stepT[i] >= 1f) stepping[i] = false;
                continue;
            }
            float pull = (float) Math.hypot(footX[i] - restX[i], footY[i] - restY[i]) / scale;
            boolean otherInAir = LEG_SET[i] == 0 ? inAir1 : inAir0;
            if (!dug && pull > STEP_REACH && !otherInAir) {
                stepping[i] = true;
                stepT[i] = 0f;
                fromX[i] = footX[i];
                fromY[i] = footY[i];
                if (LEG_SET[i] == 0) inAir0 = true;
                else inAir1 = true;
            }
        }
        for (int i = 0; i < LEGS; i++) {
            if (LEG_SET[i] < 0) {
                legAngle[i] = FEELER_DEG * (float) Math.sin(2 * Math.PI * FEELER_HZ * time + i);
                legReach[i] = 1f;
                legLift[i] = 0f;
                continue;
            }
            float hx = (HIP_COL[i] - CENTRE_COL) / U_LAYER, hy = (ANCHOR_ROW - HIP_ROW[i]) / U_LAYER;
            float rx = (FOOT_COL[i] - CENTRE_COL) / U_LAYER - hx, ry = (ANCHOR_ROW - FOOT_ROW[i]) / U_LAYER - hy;
            float dx = (footX[i] - bodyX) / scale, dy = (footY[i] - bodyY) / scale;
            float bx = dx * fy - dy * fx - hx, by = dx * fx + dy * fy - hy;
            double turn = Math.atan2(by, bx) - Math.atan2(ry, rx);
            legAngle[i] = (float) Math.toDegrees(Math.IEEEremainder(turn, 2 * Math.PI));
            float reach = (float) (Math.hypot(bx, by) / Math.hypot(rx, ry));
            legReach[i] = Math.max(REACH_MIN, Math.min(REACH_MAX, reach));
            legLift[i] = stepping[i] ? (float) Math.sin(Math.PI * stepT[i]) : 0f;
        }
    }

    /** Where leg {@code i}'s claw rests in the world, the body facing ({@code fx}, {@code fy}). */
    private void restWorld(int i, float fx, float fy) {
        float x = (FOOT_COL[i] - CENTRE_COL) / U_LAYER * scale, y = (ANCHOR_ROW - FOOT_ROW[i]) / U_LAYER * scale;
        restX[i] = bodyX + x * fy + y * fx;
        restY[i] = bodyY - x * fx + y * fy;
    }

    /**
     * Lays the tail out from its root under the shell's rear: each joint lifts {@code curl} of its {@link #PITCH} off
     * the one before, leans {@code curl} of its {@link #LEAN}, and turns by {@code extraYaw}; the points are where the
     * slices' seams lie, the last the sling's centre.
     */
    private void solveTail(float curl, float[] extraYaw) {
        float x = 0f, y = (ANCHOR_ROW - TAIL_ROOT_ROW) / U_LAYER, z = 0f, pitch = 0f, turn = 0f;
        setTail(0, x, y, z);
        for (int i = 0; i < TAIL_SEGMENTS; i++) {
            pitch += PITCH[i] * curl;
            turn += LEAN[i] * curl + extraYaw[i];
            yaw[i] = turn;
            float len = (i < TAIL_ART_SLICES ? SLICE_PX : SLING_ROW - TAIL_ROOT_ROW - TAIL_ART_SLICES * SLICE_PX)
                    / U_LAYER * (1f + PERSPECTIVE * z);
            double p = Math.toRadians(pitch), w = Math.toRadians(turn);
            float back = len * (float) Math.cos(p);          // laid back along the ground; below 0 it reaches forward
            x += back * (float) Math.sin(w);
            y -= back * (float) Math.cos(w);
            z = Math.max(0f, z + len * (float) Math.sin(p));
            setTail(i + 1, x, y, z);
        }
    }

    private void setTail(int point, float x, float y, float z) {
        tail[point * 3] = x;
        tail[point * 3 + 1] = y;
        tail[point * 3 + 2] = z;
    }

    private static float ease(float value, float target, float dt, float seconds) {
        return value + (target - value) * (1f - (float) Math.exp(-dt / seconds));
    }

    private static float clamp01(float t) {
        return Math.max(0f, Math.min(1f, t));
    }

    private static float smooth(float t) {
        return RavRunnerRig.smooth(clamp01(t));
    }

    // ------------------------------------------------------------------ death

    /** Advances a dead Bombard: on its first dead tick it falls apart from the pose it died in. Seeded by the rig. */
    public void advanceDead(float posX, float posY, float dt) {
        if (!started) advance(posX, posY, facingDeg, 0f, 0f, 0f, dt);
        bodyX = posX;
        bodyY = posY;
        if (!dead) {
            dead = true;
            sinceDeath = 0f;
            curlAtDeath = curl;
            System.arraycopy(lash, 0, lashAtDeath, 0, TAIL_SEGMENTS);
            long h = seed * 0x9E3779B97F4A7C15L;
            flopSide = (h & 2L) == 0L ? 1f : -1f;
            deadLegSwing[0] = BUCKLE_DEG * (0.5f + ((h >>> 8) & 0xFF) / 510f);
            deadLegSwing[1] = -BUCKLE_DEG * (0.5f + ((h >>> 16) & 0xFF) / 510f);
            emptied = 0f;
            solvePose();
            burst.start(pose, 0f, (ANCHOR_ROW - BODY_ROW) / U_LAYER, seed);
        }
        sinceDeath += dt;
        // the tail falls like a dead weight, then lies limp to one side with a little bounce as it lands
        float t = clamp01(sinceDeath / COLLAPSE_SECONDS);
        float fall = t * t;
        float bounce = sinceDeath > COLLAPSE_SECONDS
                ? 0.15f * (float) Math.exp(-(sinceDeath - COLLAPSE_SECONDS) / 0.12f)
                * (float) Math.sin(2 * Math.PI * (sinceDeath - COLLAPSE_SECONDS) / 0.25f) : 0f;
        curl = curlAtDeath * (1f - fall);
        for (int i = 0; i < TAIL_SEGMENTS; i++) {
            lash[i] = lashAtDeath[i] * (1f - fall) + flopSide * LIMP[i] * (fall + bounce);
        }
        solveTail(curl, lash);
        liftV += (SPRING_K * (-SLUMP - lift) - SPRING_C * liftV) * dt;
        lift += liftV * dt;
        lurch = ease(lurch, 0f, dt, 0.1f);
        rock = ease(rock, 0f, dt, 0.1f);
        tremble = 0f;
        bob = 0f;
        glow = (float) Math.exp(-sinceDeath / RUPTURE_SECONDS * 3f);
        burst.advance(dt);
    }

    /** The body-frame pose (x right, y forward, z up, cells): the tail's points, then the body. */
    private void solvePose() {
        for (int i = 0; i <= TAIL_SEGMENTS; i++) {
            System.arraycopy(tail, i * 3, pose, i * 3, 3);
        }
        pose[BODY_POINT * 3] = 0f;
        pose[BODY_POINT * 3 + 1] = (ANCHOR_ROW - BODY_ROW) / U_LAYER;
        pose[BODY_POINT * 3 + 2] = BODY_Z;
    }

    /** Whether the Bombard is dead and fallen apart. */
    public boolean dead() { return dead; }

    /** The blood it spills, dead; meaningful once dead. */
    public RavBurst burst() { return burst; }

    /** Seconds since it died. */
    public float sinceDeath() { return sinceDeath; }

    /** How far the shell has cracked open down its seam, 0 whole to 1 parted; 0 alive. */
    public float crack() {
        return dead ? RavRunnerRig.easeOut((sinceDeath - CRACK_AT) / CRACK_SECONDS) : 0f;
    }

    /** How far each shell half has moved out from the seam, cells, and turned out, degrees. */
    public float crackCells() { return CRACK_CELLS * crack(); }
    public float crackDegrees() { return CRACK_DEG * crack(); }

    // ------------------------------------------------------------------ read side

    /** Whether the rig has been advanced at least once and so holds a pose. */
    public boolean posed() { return started; }
    public float bodyX() { return bodyX; }
    public float bodyY() { return bodyY; }
    public float facingDegrees() { return facingDeg; }
    /** How far the tail is arched over the back: 0 laid flat out behind, 1 wound up. */
    public float curl() { return curl; }
    /** A tail point's place in the rig frame, cells: 0 the root, then each slice's far seam, the last the sling's centre. */
    public float tailX(int point) { return tail[point * 3]; }
    public float tailY(int point) { return tail[point * 3 + 1]; }
    public float tailZ(int point) { return tail[point * 3 + 2]; }
    /** A tail joint's heading off the facing, degrees counter-clockwise: the way its slice lies, seen from above. */
    public float tailYaw(int joint) { return yaw[joint]; }
    /** The draw scale of something standing {@code z} cells off the ground: nearer the camera, larger. */
    public static float rise(float z) { return 1f + PERSPECTIVE * z; }
    /** Whether the glob is in the sling: from the start of the wind-up until it is flung. */
    public boolean globLoaded() { return !dead && windup > 0f && sinceFling > WHIP_SECONDS + SETTLE_SECONDS; }
    /** How far into the wind-up, 0..1. */
    public float windup() { return windup; }
    /** Leg {@code i}'s turn about its hip off its art, degrees counter-clockwise: reaching for its claw, dug or buckled. */
    public float legAngle(int i) {
        boolean left = i < LEGS_PER_SIDE;
        float side = dead ? deadLegSwing[left ? 0 : 1] * smooth(sinceDeath / 0.3f) : left ? digSwing : -digSwing;
        return legAngle[i] + side;
    }
    /** How far leg {@code i} stretches to its claw, over its art. */
    public float legReach(int i) { return legReach[i]; }
    /** How high leg {@code i} is lifted in its step, 0 planted to 1 at the top. */
    public float legLift(int i) { return legLift[i]; }
    /** The draw scale a lifted leg takes, nearer the camera. */
    public static float liftScale(float lift) { return 1f + STEP_LIFT * lift; }
    /** How wide one side's legs splay, over their art: wider as the Bombard plants, at each stamp, and braced. */
    public float legSplay(boolean left) {
        float brace = sinceFling < BRACE_SECONDS ? BRACE * (1f - sinceFling / BRACE_SECONDS) : 0f;
        float buckle = dead ? BUCKLE * RavRunnerRig.easeOut(sinceDeath / 0.3f) : 0f;
        return 1f + SPLAY * plant + (left ? digSplayL : digSplayR) + brace + buckle;
    }
    /** How far the body is shifted forward, cells: the walk's bob, the crouch, the throw's lurch, the tremble. */
    public float shift() { return bob + lurch + tremble; }
    /** The body's turn off the facing, degrees: the rock after a throw. */
    public float rock() { return rock; }
    /** The shell's draw scale: lower as it plants, crouches and slumps, pressed down by the throw. */
    public float lift() { return 1f + lift; }
    /** The shell's width over its art: narrower while the sac is emptied. */
    public float squeeze() { return 1f - SQUEEZE * smooth(emptied); }
    /** The sac's glow, 0..1: a slow pulse, brighter through the wind-up, a flash at the fling then dull; dead, it ruptures. */
    public float glow() { return glow; }
}
