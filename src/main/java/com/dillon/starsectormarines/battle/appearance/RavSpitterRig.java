package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.unit.SwarmStage;

/**
 * The Spitter's skeleton, advanced once per simulation tick: an upright serpent seen from above, its chest reared over
 * a tail coiled beneath it in short slices, two arms each ending in a single hooked talon, and a cobra hood round its
 * head; built from separate parts the way infantry are. Presentation only; it reads where the sim put the body, its attack cooldown and whether it has a target, and
 * nothing in the simulation reads it back.
 *
 * <p>The rig frame is the body's own, in cells, centred on the sim position (the parts canvas's {@link #ANCHOR_ROW}): x to the right, y forward. It stands on its tail: the tail curls out sideways under the reared chest, sweeps
 * back across beneath it and trails out behind ({@link #COIL}). On the move the coil loosens and a wave runs down it,
 * advanced by distance travelled so it slithers rather than paddles, the chest rolls against it and the head holds
 * steady; turning, the tail lags and bends away from the turn. At rest the coil stills to a slow sway and the
 * arms breathe. The arms are their own parts about their shoulders, so a slash can swing one alone.
 *
 * <p>Firing is timed off the attack cooldown, as the Runner's strike is, so the glob leaves on the tick the sim lobs
 * it: as the cooldown runs out with a target held it braces (the hood pops open, the chest rears back, the arms
 * raise forward), and when the cooldown jumps back up the lob has gone: the head lunges, the arms sweep back,
 * and the hood folds again once it has settled.
 *
 * <p>Built on the approved round 5 design, its parts exported by
 * {@code art-source/rav-redesign/spitter/export_spitter_rig.py}.
 */
public final class RavSpitterRig {

    /** Parts canvas px per cell; the export and the composer share it. */
    public static final float U_LAYER = 760f;
    /** Canvas row of the body anchor (the sim position) and the centre column the parts mirror about. */
    public static final float ANCHOR_ROW = 620f, CENTRE_COL = 512f;
    /** Canvas px of the neck, about which the head turns, and of the left shoulder socket; the right mirrors it. */
    public static final float NECK_ROW = 300f, SHOULDER_COL = 320f, SHOULDER_ROW = 370f;
    public static final int TAIL_SEGMENTS = 12;
    /** Canvas rows of each tail slice's root, and of the tail's tip: the root to the spike, evenly cut. */
    public static final float[] TAIL_ROWS = new float[TAIL_SEGMENTS + 1];
    static {
        for (int i = 0; i <= TAIL_SEGMENTS; i++) TAIL_ROWS[i] = Math.round(480f + 1023f * i / TAIL_SEGMENTS);
    }
    /** Canvas row under the chest where the coiled tail's root is drawn, hidden beneath the back. */
    public static final float COIL_ROOT_ROW = 540f;
    /**
     * The coil at rest, each slice's bend off the one above it (degrees, counter-clockwise): from under the chest
     * it shows at one hip, sweeps back across behind the haunch and leaves the tip trailing out behind.
     */
    static final float[] COIL = {0f, 25f, 25f, 20f, -15f, -25f, -30f, -30f, -20f, 0f, 10f, 10f};
    /** How much of the coil is let out at full glide. */
    private static final float COIL_LOOSEN = 0.45f;

    /** Arm indices. */
    public static final int LEFT = 0, RIGHT = 1;

    /** The slither: cells travelled per tail wave, the wave's lag down each slice (cycles), and its bend per slice. */
    private static final float WAVE_CELLS = 1.1f, WAVE_LAG = 0.1f, SWING = 14f;
    private static final float MOVE_BELOW = 0.12f, MOVE_FULL = 1.2f;
    /** The chest's roll against the tail, degrees, and its sideways carry, cells. */
    private static final float ROLL = 4f, CARRY = 0.02f;
    /** At rest: the tail's slow sway (degrees, Hz) and the arms' breath. */
    private static final float IDLE_SWAY = 4f, IDLE_HZ = 0.35f, CLAW_BREATH = 3f;
    /** Degrees of tail bend per degree a second of turn, split down the segments, and its limit. */
    private static final float TAIL_LAG = 0.05f, TAIL_BEND_MAX = 22f;

    /** The brace before a lob, seconds of cooldown left when it begins. */
    static final float BRACE_SECONDS = 0.45f;
    /** After the lob: the lunge, the settle, and how long the hood stays open after it. */
    static final float LUNGE_SECONDS = 0.10f, SETTLE_SECONDS = 0.35f, HOOD_HOLD = 0.35f, HOOD_FOLD = 0.30f;
    private static final float RESET_JUMP = 0.3f;
    private static final float REAR = 0.06f, LUNGE = 0.09f, RISE = 0.06f, SPREAD = 20f, FLICK = 14f;

    private float bodyX, bodyY, facingDeg;
    private boolean started;
    private float psi, motion, time, turnRate;
    private float lastCooldown = Float.NaN;
    private float sinceLob = Float.MAX_VALUE;
    private float brace;

    private final float[] tail = new float[TAIL_SEGMENTS];
    private final float[] claw = new float[2];
    private float bodyAngle, shiftX, shiftY, headYaw, headScale = 1f, hood;

    /** @param seed the body's id, so two Spitters do not sway in step */
    public RavSpitterRig(long seed) {
        time = Math.floorMod(seed * 0x9E3779B97F4A7C15L, 10000L) / 1000f;
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
        float still = 1f - Math.max(brace, lunge);        // braced, the tail is planted straight
        for (int i = 0; i < TAIL_SEGMENTS; i++) {
            float wave = motion * SWING * (float) Math.sin(2 * Math.PI * (psi - i * WAVE_LAG));
            float coil = COIL[i] * (1f - COIL_LOOSEN * motion * still);
            tail[i] = coil + (wave + idle / TAIL_SEGMENTS) * still + bend / TAIL_SEGMENTS;
        }
        float cycle = (float) (2 * Math.PI * (psi + 0.5f));
        bodyAngle = motion * ROLL * (float) Math.sin(cycle) * still;
        shiftX = motion * CARRY * (float) Math.sin(cycle) * still;
        shiftY = -REAR * brace + LUNGE * lunge;
        headYaw = -bodyAngle * 0.8f;                       // the head holds its line while the chest rolls
        headScale = 1f + RISE * brace + 0.03f * lunge;
        float breath = CLAW_BREATH * (float) Math.sin(2 * Math.PI * 0.5f * time + 1.3f);
        for (int side = 0; side < 2; side++) {
            float stride = motion * 6f * (float) Math.sin(cycle + (side == LEFT ? 0f : (float) Math.PI));
            claw[side] = breath * (1f - motion) + stride - SPREAD * brace + FLICK * lunge;
        }
    }

    private static float smooth(float t) {
        return RavRunnerRig.smooth(Math.max(0f, Math.min(1f, t)));
    }

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
    /** A tail slice's bend off the one above it (the first off the chest), degrees, counter-clockwise. */
    public float tailAngle(int segment) { return tail[segment]; }
    /** An arm's swing about its shoulder off its rest, degrees; + sweeps it back along the body, - raises it forward. */
    public float clawAngle(int side) { return claw[side]; }
    /** The head's turn about the neck off the chest, degrees, counter-clockwise. */
    public float headYaw() { return headYaw; }
    /** The head and hood's scale: raised toward the camera as it rears. */
    public float headScale() { return headScale; }
    /** How far the hood has popped open, 0 folded to 1 flared. */
    public float hood() { return hood; }
    /** How far into the brace before a lob, 0..1. */
    public float brace() { return brace; }
}
