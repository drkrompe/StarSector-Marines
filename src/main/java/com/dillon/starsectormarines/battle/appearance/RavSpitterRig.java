package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.unit.SwarmStage;

/**
 * The Spitter's skeleton, advanced once per simulation tick: an upright serpent seen from above, its haunched back
 * reared up out of the front of one thick tail in short slices, and a cobra hood round its head; built from separate parts
 * the way infantry are. Presentation only; it reads where the sim put the body, its attack cooldown and whether it has
 * a target, and nothing in the simulation reads it back.
 *
 * <p>The rig frame is the body's own, in cells, centred on the sim position (the parts canvas's {@link #ANCHOR_ROW}):
 * x to the right, y forward. It rears up out of its tail: the tail leaves the body's centre straight back beneath the
 * haunch, as broad as it at the root ({@link #GIRTH_ROOT}), and lies on the ground behind, tapering to its spike;
 * standing, it rests curled to one side ({@link #REST}). On the move it uncurls and slithers, a wave running down it
 * advanced by distance travelled so it slithers rather than paddles; the chest rolls against it and the head holds
 * steady; turning, the tail lags and bends away from the turn. At rest it sways slowly.
 *
 * <p>Firing is timed off the attack cooldown, as the Runner's strike is, so the glob leaves on the tick the sim lobs
 * it: as the cooldown runs out with a target held it braces (the hood pops open, the chest rears back), and when the
 * cooldown jumps back up the lob has gone: the head lunges, and the hood folds again once it has settled.
 *
 * <p>Dead, it bursts as a Brute does ({@link RavBurst}): the head comes away, the body slumps, and one or two lengths
 * of the tail are thrown, over a pool of blood.
 *
 * <p>Built on the approved round 5 design, its parts exported by
 * {@code art-source/rav-redesign/spitter/export_spitter_rig.py}.
 */
public final class RavSpitterRig {

    /** Parts canvas px per cell; the export and the composer share it. */
    public static final float U_LAYER = 500f;
    /** Canvas row of the body anchor (the sim position) and the centre column the parts mirror about. */
    public static final float ANCHOR_ROW = 600f, CENTRE_COL = 512f;
    /** Canvas row of the neck, about which the head turns. */
    public static final float NECK_ROW = 460f;
    /** Canvas row of each sprite's centre (the export boxes): the back, and the head in either state. */
    public static final float BACK_ROW = 575f, HEAD_ROW = 390f;
    /** The exported tail slices, root to spike. */
    public static final int TAIL_ART_SLICES = 12;
    /** Canvas rows of each exported slice's root, and of the tail's tip: the root to the spike, evenly cut. */
    public static final float[] TAIL_ROWS = new float[TAIL_ART_SLICES + 1];
    static {
        for (int i = 0; i <= TAIL_ART_SLICES; i++) TAIL_ROWS[i] = Math.round(560f + 844f * i / TAIL_ART_SLICES);
    }
    /** The drawn tail: each drawn slice's exported slice, root to spike. */
    public static final int[] TAIL_ART = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    public static final int TAIL_SEGMENTS = TAIL_ART.length;
    /** The tail is drawn at this share of its canvas size. */
    public static final float TAIL_SCALE = 1f;
    /** The tail's girth against its art: as broad as the haunch at the root, tapering to the spike. */
    static final float GIRTH_ROOT = 1f, GIRTH_TIP = 1f;
    /** A drawn slice's length, canvas px. */
    public static final float SLICE_PX = (TAIL_ROWS[1] - TAIL_ROWS[0]) * TAIL_SCALE;
    /** Slices under the body, straight back from its centre; the rest lie on the ground behind. */
    static final int ROOT_SLICES = 1;
    /** The rest pose, each slice's bend off the one above it (degrees): straight back, then curling to one side. */
    static final float[] REST = {-55f, 23f, 23f, 23f, 23f, 23f, 23f, 23f, 23f, 23f, 23f, 23f};

    /** The slither: cells travelled per tail wave, the wave's lag down each slice (cycles), and its bend per slice. */
    private static final float WAVE_CELLS = 1.1f, WAVE_LAG = 0.1f, SWING = 14f;
    private static final float MOVE_BELOW = 0.12f, MOVE_FULL = 1.2f;
    /** The chest's roll against the tail, degrees, and its sideways carry, cells. */
    private static final float ROLL = 4f, CARRY = 0.02f;
    /** At rest: the tail's slow sway, degrees and Hz. */
    private static final float IDLE_SWAY = 4f, IDLE_HZ = 0.35f;
    /** Degrees of tail bend per degree a second of turn, split down the segments, and its limit. */
    private static final float TAIL_LAG = 0.05f, TAIL_BEND_MAX = 22f;

    /** The brace before a lob, seconds of cooldown left when it begins. */
    static final float BRACE_SECONDS = 0.45f;
    /** After the lob: the lunge, the settle, and how long the hood stays open after it. */
    static final float LUNGE_SECONDS = 0.10f, SETTLE_SECONDS = 0.35f, HOOD_HOLD = 0.35f, HOOD_FOLD = 0.30f;
    private static final float RESET_JUMP = 0.3f;
    private static final float REAR = 0.06f, LUNGE = 0.09f, RISE = 0.06f;

    private float bodyX, bodyY, facingDeg;
    private boolean started;
    private float psi, motion, time, turnRate;
    private float lastCooldown = Float.NaN;
    private float sinceLob = Float.MAX_VALUE;
    private float brace;

    private final float[] tail = new float[TAIL_SEGMENTS];
    private float bodyAngle, shiftX, shiftY, headYaw, headScale = 1f, hood;

    /** The pose points a dead Spitter bursts from: the tail's joints root to tip, then the back and the head. */
    public static final int BACK_POINT = TAIL_SEGMENTS + 1, HEAD_POINT = TAIL_SEGMENTS + 2;
    private static final int POINTS = TAIL_SEGMENTS + 3;
    /** Tail slices per burst tail piece. */
    public static final int SLICES_PER_PIECE = 4;
    /** What a Spitter bursts into: the body, the head, and the tail in three lengths. */
    public static final int PIECE_BODY = 0, PIECE_HEAD = 1, PIECE_TAIL = 2;
    private static final int[][] PIECE_POINTS = {{0, BACK_POINT}, {HEAD_POINT}, tailPiece(0), tailPiece(1),
            tailPiece(2)};
    private static final int[] PIECE_PARENT = {-1, -1, PIECE_BODY, PIECE_BODY, PIECE_BODY};
    private static final int[] LOOSE_CANDIDATES = {PIECE_TAIL, PIECE_TAIL + 1, PIECE_TAIL + 2};
    private static final float POOL_CELLS = 1.8f;
    /** Each part's height off the ground as it died, cells: the tail lies on it, the head is reared highest. */
    private static final float TAIL_Z = 0.1f, BACK_Z = 0.35f, HEAD_Z = 0.6f;

    private final long seed;
    private boolean dead;
    private final float[] pose = new float[POINTS * 3];
    private final RavBurst burst = new RavBurst(PIECE_POINTS, 1, PIECE_PARENT, LOOSE_CANDIDATES, POOL_CELLS);

    /** @param seed the body's id, so two Spitters do not sway in step */
    public RavSpitterRig(long seed) {
        this.seed = seed;
        time = Math.floorMod(seed * 0x9E3779B97F4A7C15L, 10000L) / 1000f;
    }

    private static int[] tailPiece(int k) {
        int[] joints = new int[SLICES_PER_PIECE + 1];
        for (int i = 0; i <= SLICES_PER_PIECE; i++) joints[i] = k * SLICES_PER_PIECE + i;
        return joints;
    }

    /** The burst tail piece slice i belongs to. */
    public static int tailPieceOf(int slice) {
        return PIECE_TAIL + Math.min(slice / SLICES_PER_PIECE, PIECE_POINTS.length - PIECE_TAIL - 1);
    }

    private SwarmStage stage = SwarmStage.ADULT;
    private float scale = 1f;

    /** Sets the growth stage; read each tick, so a Spitter that matures in the field grows with it. */
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
     * @param facing    body facing in sprite degrees (0 north, counter-clockwise)
     * @param cooldown  the attack cooldown, seconds; a lob resets it upward
     * @param hasTarget whether the body holds a target, so a running-out cooldown is a lob coming
     */
    public void advance(float posX, float posY, float facing, float cooldown, boolean hasTarget, float dt) {
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
        moved /= scale;
        float turn = ((facing - facingDeg + 540f) % 360f) - 180f;
        turnRate += (turn / dt - turnRate) * (1f - (float) Math.exp(-dt / 0.15f));
        facingDeg = facing;
        psi += moved / WAVE_CELLS;
        float speed = moved / dt;
        float moving = smooth((speed - MOVE_BELOW) / (MOVE_FULL - MOVE_BELOW));
        motion += (moving - motion) * (1f - (float) Math.exp(-dt / 0.2f));
        time += dt;

        // the lob: brace as the cooldown runs out with a target, then lunge when it resets
        if (!Float.isNaN(lastCooldown) && cooldown > lastCooldown + RESET_JUMP) sinceLob = 0f;
        else if (sinceLob < Float.MAX_VALUE) sinceLob += dt;
        lastCooldown = cooldown;
        float braceTarget = hasTarget && motion < 0.5f ? smooth(1f - Math.max(0f, cooldown) / BRACE_SECONDS) : 0f;
        brace += (braceTarget - brace) * (1f - (float) Math.exp(-dt / 0.06f));
        float lunge = 0f;
        if (sinceLob < LUNGE_SECONDS) {
            lunge = RavRunnerRig.easeOut(sinceLob / LUNGE_SECONDS);
            brace = 0f;
        } else if (sinceLob < LUNGE_SECONDS + SETTLE_SECONDS) {
            lunge = 1f - smooth((sinceLob - LUNGE_SECONDS) / SETTLE_SECONDS);
        }
        float hoodTarget = brace;
        if (sinceLob < LUNGE_SECONDS + HOOD_HOLD) hoodTarget = 1f;
        else if (sinceLob < LUNGE_SECONDS + HOOD_HOLD + HOOD_FOLD) {
            hoodTarget = 1f - smooth((sinceLob - LUNGE_SECONDS - HOOD_HOLD) / HOOD_FOLD);
        }
        hood += (hoodTarget - hood) * (1f - (float) Math.exp(-dt / 0.05f));

        // the body: a wave down the tail on the move, a slow sway at rest, a lag through turns
        float bend = Math.max(-TAIL_BEND_MAX, Math.min(TAIL_BEND_MAX, -turnRate * TAIL_LAG));
        float idle = (1f - motion) * IDLE_SWAY * (float) Math.sin(2 * Math.PI * IDLE_HZ * time);
        float still = 1f - Math.max(brace, lunge);        // braced, the tail is planted
        int trailing = TAIL_SEGMENTS - ROOT_SLICES;
        float curl = 1f - motion * still;                 // curled at rest and while firing, uncurled on the move
        for (int i = 0; i < TAIL_SEGMENTS; i++) {
            tail[i] = curl * REST[i];
            if (i < ROOT_SLICES) continue;
            int along = i - ROOT_SLICES;
            float wave = motion * SWING * (float) Math.sin(2 * Math.PI * (psi - along * WAVE_LAG));
            tail[i] += (wave + (bend + idle) / trailing) * still;
        }
        float cycle = (float) (2 * Math.PI * (psi + 0.5f));
        bodyAngle = motion * ROLL * (float) Math.sin(cycle) * still;
        shiftX = motion * CARRY * (float) Math.sin(cycle) * still;
        shiftY = -REAR * brace + LUNGE * lunge;
        headYaw = -bodyAngle * 0.8f;                       // the head holds its line while the chest rolls
        headScale = 1f + RISE * brace + 0.03f * lunge;
    }

    private static float smooth(float t) {
        return RavRunnerRig.smooth(Math.max(0f, Math.min(1f, t)));
    }

    // ------------------------------------------------------------------ death

    /**
     * Advances a dead Spitter: on its first dead tick it bursts as a Brute does ({@link RavBurst}), from the pose it
     * died in, over the same blood. Seeded by the rig, so every replay bursts the same way.
     */
    public void advanceDead(float posX, float posY, float dt) {
        if (!started) advance(posX, posY, facingDeg, 0f, false, dt);   // never drawn alive: stand it up
        bodyX = posX;
        bodyY = posY;
        if (!dead) {
            dead = true;
            solvePose();
            burst.start(pose, 0f, shiftY + (ANCHOR_ROW - BACK_ROW) / U_LAYER, seed);
        }
        burst.advance(dt);
    }

    /** The body-frame pose (x right, y forward, z up, cells): the tail chained back from the centre, the back and head. */
    private void solvePose() {
        float slice = SLICE_PX / U_LAYER;
        float x = 0f, y = 0f, a = 0f;
        for (int i = 0; i <= TAIL_SEGMENTS; i++) {
            setPoint(i, x, y, TAIL_Z);
            if (i == TAIL_SEGMENTS) break;
            a += tail[i];
            double r = Math.toRadians(a);
            x += (float) Math.sin(r) * slice;
            y -= (float) Math.cos(r) * slice;
        }
        setPoint(BACK_POINT, shiftX, shiftY + (ANCHOR_ROW - BACK_ROW) / U_LAYER, BACK_Z);
        setPoint(HEAD_POINT, shiftX, shiftY + (ANCHOR_ROW - HEAD_ROW) / U_LAYER, HEAD_Z);
    }

    private void setPoint(int point, float x, float y, float z) {
        pose[point * 3] = x;
        pose[point * 3 + 1] = y;
        pose[point * 3 + 2] = z;
    }

    /** Whether the Spitter is dead and burst. */
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
    /** The chest's roll off the facing, degrees, counter-clockwise. */
    public float bodyAngle() { return bodyAngle; }
    /** Where the chest is carried off its anchor, cells in the body frame: its sideways carry, its rear and lunge. */
    public float shiftX() { return shiftX; }
    public float shiftY() { return shiftY; }
    /** A tail slice's bend off the one above it (the first off the facing), degrees, counter-clockwise. */
    public float tailAngle(int segment) { return tail[segment]; }
    /** A drawn tail slice's girth against its art. */
    public static float tailGirth(int segment) {
        return GIRTH_ROOT + (GIRTH_TIP - GIRTH_ROOT) * segment / (TAIL_SEGMENTS - 1f);
    }
    /** The head's turn about the neck off the chest, degrees, counter-clockwise. */
    public float headYaw() { return headYaw; }
    /** The head and hood's scale: raised toward the camera as it rears. */
    public float headScale() { return headScale; }
    /** How far the hood has popped open, 0 folded to 1 flared. */
    public float hood() { return hood; }
    /** How far into the brace before a lob, 0..1. */
    public float brace() { return brace; }
}
