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
 * the right, y forward. On the move its legs scuttle, each side swinging about its hip against the other, advanced by
 * distance travelled so it walks rather than paddles. Planting, the legs splay a little and set. Standing, the tail
 * lies almost straight out behind and sways.
 *
 * <p>The lob is the catapult's. Winding up ({@code windup} 0 to 1 over the sim's wind-up) the tail curls slice by slice
 * up over the back, rising toward the camera as it goes, until the sling hangs over the shell with the glob glowing in
 * it, as the master's left frame draws it, and the sac brightens. When the cooldown jumps back up the glob has gone:
 * the tail whips through its arc and past straight, then settles.
 *
 * <p>Dead, it bursts as a Spitter does ({@link RavBurst}): the shell slumps and the tail comes away in one or two
 * lengths, over a pool of blood. (The sim bursts its sac into a last haze; that is the haze's to draw.)
 *
 * <p>Built on the approved G1 design, its parts exported by
 * {@code art-source/rav-redesign/artillery/export_bombard_rig.py}.
 */
public final class RavBombardRig {

    /** Master canvas px per cell; the export and the composer share it. */
    public static final float U_LAYER = 330f;
    /** Canvas row of the body anchor (the sim position) and the centre column. */
    public static final float ANCHOR_ROW = 445f, CENTRE_COL = 1078f;
    /** Canvas centres of the exported boxes: the body, and each side's legs. */
    public static final float BODY_ROW = 440f, LEGS_ROW = 455f, LEGS_L_COL = 939f, LEGS_R_COL = 1217f;
    /** Canvas points each side's legs swing about: its hip, under the shell's flank. */
    public static final float HIP_ROW = 455f, HIP_L_COL = 1000f, HIP_R_COL = 1156f;
    /** The exported tail slices, root to sling. */
    public static final int TAIL_ART_SLICES = 6;
    /** Canvas row of the tail's root and of each slice's seam, root to the sling's hinge. */
    public static final float TAIL_ROOT_ROW = 680f, SLICE_PX = 30f;
    /** Canvas row of the sling sprite's centre, and of the cup where the glob sits. */
    public static final float SLING_ROW = 932f, CUP_ROW = 935f;
    /** The tail's joints: one per slice, then the sling's hinge. */
    public static final int TAIL_SEGMENTS = TAIL_ART_SLICES + 1;

    /** The wound-up curl, each joint's bend off the one before it at full wind-up (degrees, counter-clockwise). */
    static final float[] CURL = {12f, 26f, 30f, 32f, 32f, 32f, 28f};
    /** How far toward the camera a fully curled slice rises: its draw scale over its art. */
    static final float RISE = 0.22f;
    /** The curl standing, and its slow sway, of full curl, and Hz. */
    static final float REST_CURL = 0.08f, SWAY = 0.03f, SWAY_HZ = 0.3f;
    /** The whip: past straight by this much curl, over this long, then settling over this long. */
    static final float WHIP = -0.35f, WHIP_SECONDS = 0.12f, SETTLE_SECONDS = 0.7f;
    private static final float RESET_JUMP = 0.3f;

    /** The scuttle: cells walked per leg cycle, each side's swing, degrees, and how far the body bobs, cells. */
    private static final float STRIDE = 0.55f, LEG_SWING = 7f, BOB = 0.015f;
    private static final float MOVE_BELOW = 0.08f, MOVE_FULL = 0.8f;
    /** Planted, the legs splay out this much wider. */
    static final float SPLAY = 0.06f;
    /** The sac's glow: at rest, its pulse, and what the wind-up adds. */
    private static final float GLOW_REST = 0.25f, GLOW_PULSE = 0.12f, GLOW_WINDUP = 0.75f;

    private float bodyX, bodyY, facingDeg;
    private boolean started;
    private float stride, motion, time;
    private float lastCooldown = Float.NaN;
    private float sinceFling = Float.MAX_VALUE;
    private float curl, flingFrom, windup, plant, legSwing, bob, glow;

    /** The pose points a dead Bombard bursts from: the tail's joints root to sling, then the body. */
    public static final int BODY_POINT = TAIL_SEGMENTS + 1;
    private static final int POINTS = TAIL_SEGMENTS + 2;
    /** What a Bombard bursts into: the body (legs and shell), the near half of the tail, the far half and sling. */
    public static final int PIECE_BODY = 0, PIECE_TAIL = 1;
    /** Tail joints per burst piece. */
    public static final int SLICES_PER_PIECE = 4;
    private static final int[][] PIECE_POINTS = {{0, BODY_POINT}, {0, 1, 2, 3, 4}, {4, 5, 6, 7}};
    private static final int[] PIECE_PARENT = {-1, PIECE_BODY, PIECE_BODY};
    private static final int[] LOOSE_CANDIDATES = {PIECE_TAIL, PIECE_TAIL + 1};
    private static final float POOL_CELLS = 2.2f;
    private static final float TAIL_Z = 0.15f, BODY_Z = 0.45f;

    private final long seed;
    private boolean dead;
    private final float[] tail = new float[TAIL_SEGMENTS];
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

    /** The burst tail piece joint {@code i}'s slice belongs to. */
    public static int tailPieceOf(int slice) {
        return slice < SLICES_PER_PIECE ? PIECE_TAIL : PIECE_TAIL + 1;
    }

    /**
     * Advances the rig one tick.
     *
     * @param facing   body facing in sprite degrees (0 north, counter-clockwise)
     * @param cooldown the attack cooldown, seconds; a fling resets it upward
     * @param windup   how far into its wind-up, 0 to 1; 0 when it is not winding up
     * @param planted  how far planted, 0 walking to 1 set
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
        plant += (Math.max(0f, Math.min(1f, planted)) - plant) * (1f - (float) Math.exp(-dt / 0.3f));
        this.windup = Math.max(0f, Math.min(1f, windup));

        if (!Float.isNaN(lastCooldown) && cooldown > lastCooldown + RESET_JUMP) {
            sinceFling = 0f;
            flingFrom = Math.max(curl, 0.85f);
        } else if (sinceFling < Float.MAX_VALUE) {
            sinceFling += dt;
        }
        lastCooldown = cooldown;

        float rest = REST_CURL + SWAY * (float) Math.sin(2 * Math.PI * SWAY_HZ * time);
        if (sinceFling < WHIP_SECONDS) {
            curl = flingFrom + (WHIP - flingFrom) * RavRunnerRig.easeOut(sinceFling / WHIP_SECONDS);
        } else if (sinceFling < WHIP_SECONDS + SETTLE_SECONDS) {
            curl = WHIP + (rest - WHIP) * smooth((sinceFling - WHIP_SECONDS) / SETTLE_SECONDS);
        } else {
            float target = rest + (1f - rest) * smooth(this.windup);
            curl += (target - curl) * (1f - (float) Math.exp(-dt / 0.08f));
        }
        for (int i = 0; i < TAIL_SEGMENTS; i++) tail[i] = curl * CURL[i];

        float cycle = (float) (2 * Math.PI * stride);
        legSwing = motion * LEG_SWING * (float) Math.sin(cycle);
        bob = motion * BOB * (float) Math.sin(2 * cycle);
        float flash = sinceFling < WHIP_SECONDS ? 1f - sinceFling / WHIP_SECONDS : 0f;
        glow = Math.min(1f, GLOW_REST + GLOW_PULSE * (float) Math.sin(2 * Math.PI * 0.5f * time)
                + GLOW_WINDUP * smooth(this.windup) + flash);
    }

    private static float smooth(float t) {
        return RavRunnerRig.smooth(Math.max(0f, Math.min(1f, t)));
    }

    // ------------------------------------------------------------------ death

    /** Advances a dead Bombard: on its first dead tick it bursts from the pose it died in. Seeded by the rig. */
    public void advanceDead(float posX, float posY, float dt) {
        if (!started) advance(posX, posY, facingDeg, 0f, 0f, 0f, dt);
        bodyX = posX;
        bodyY = posY;
        if (!dead) {
            dead = true;
            solvePose();
            burst.start(pose, 0f, (ANCHOR_ROW - BODY_ROW) / U_LAYER, seed);
        }
        burst.advance(dt);
    }

    /** The body-frame pose (x right, y forward, z up, cells): the tail chained back from its root, then the body. */
    private void solvePose() {
        float slice = SLICE_PX / U_LAYER;
        float x = 0f, y = (ANCHOR_ROW - TAIL_ROOT_ROW) / U_LAYER, a = 0f;
        for (int i = 0; i <= TAIL_SEGMENTS; i++) {
            setPoint(i, x, y, TAIL_Z);
            if (i == TAIL_SEGMENTS) break;
            a += tail[i];
            double r = Math.toRadians(a);
            float len = i < TAIL_ART_SLICES ? slice : (SLING_ROW - TAIL_ROOT_ROW - TAIL_ART_SLICES * SLICE_PX) / U_LAYER;
            x += (float) Math.sin(r) * len;
            y -= (float) Math.cos(r) * len;
        }
        setPoint(BODY_POINT, 0f, (ANCHOR_ROW - BODY_ROW) / U_LAYER, BODY_Z);
    }

    private void setPoint(int point, float x, float y, float z) {
        pose[point * 3] = x;
        pose[point * 3 + 1] = y;
        pose[point * 3 + 2] = z;
    }

    /** Whether the Bombard is dead and burst. */
    public boolean dead() { return dead; }

    /** The burst it died in; meaningful once dead. */
    public RavBurst burst() { return burst; }

    // ------------------------------------------------------------------ read side

    /** Whether the rig has been advanced at least once and so holds a pose. */
    public boolean posed() { return started; }
    public float bodyX() { return bodyX; }
    public float bodyY() { return bodyY; }
    public float facingDegrees() { return facingDeg; }
    /** A tail joint's bend off the one before it (the first off the facing), degrees, counter-clockwise. */
    public float tailAngle(int joint) { return tail[joint]; }
    /** How far the tail is curled over the back: 0 straight out behind, 1 wound up, below 0 whipped past straight. */
    public float curl() { return curl; }
    /** A tail joint's draw scale over its art: curled slices rise toward the camera, the far ones most. */
    public float tailRise(int joint) {
        return 1f + RISE * Math.max(0f, curl) * (joint + 1f) / TAIL_SEGMENTS;
    }
    /** Whether the glob is in the sling: from the start of the wind-up until it is flung. */
    public boolean globLoaded() { return windup > 0f && sinceFling > WHIP_SECONDS + SETTLE_SECONDS; }
    /** How far into the wind-up, 0..1. */
    public float windup() { return windup; }
    /** The left legs' swing about their hip, degrees counter-clockwise; the right legs swing the other way. */
    public float legSwing() { return legSwing; }
    /** How wide the legs splay, over their art: wider as the Bombard plants. */
    public float legSplay() { return 1f + SPLAY * plant; }
    /** The body's bob forward and back as it walks, cells. */
    public float bob() { return bob; }
    /** The sac's glow, 0..1: a slow pulse, brighter through the wind-up, a flash at the fling. */
    public float glow() { return glow; }
}
