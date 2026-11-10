package com.dillon.starsectormarines.battle.appearance;

import java.util.Arrays;

/**
 * The derelict security walker's legs and the body they carry, seen from above (`derelict-security-network.md`, The
 * walker's legs): a heavy torso slung between four legs that reach out from a ring of hips and plant their feet.
 * Presentation only, like the Rav rigs: the walker moves as any mech does and nothing in the battle reads its feet back.
 *
 * <p><b>Gait.</b> A gait clock runs on the distance the torso covers, a cycle each stride, so the cadence follows the
 * speed. At a walk ({@link Gait#WALK}) the legs lift one at a time in a four-beat sequence (back, front on one side,
 * then back, front on the other) and three feet always stand; at a run ({@link Gait#RUN}, above
 * {@link #RUN_SPEED}) the diagonal pairs trot, each pair in the air a little longer than half the cycle so the machine
 * leaves the ground between strides. A foot stays planted where it landed until its turn, then swings to land ahead of
 * where the torso will be, so it pushes back under the body through its stance. Standing, the clock stops and a foot
 * dragged out of place by a turn steps back alone.
 *
 * <p><b>Weight.</b> The drawn body is not the torso's path: it sways over the feet that stand (toward their middle, so
 * away from a lifted leg), dips as a leg lifts its share of the weight, bounces twice a stride at the run and leans
 * into its speed ({@link #bodyX()}, {@link #bodyHeight()}). The hips ride on the drawn body. A build with a
 * suspension ({@link Build#suspensionSeconds()}) carries its body on a critically damped spring instead: the body
 * eases into each sway and dip, keeps a little way on when the path stops and settles back, so it never starts or
 * stops in a frame (a crewed body, carried smoothly).
 *
 * <p><b>Reach.</b> Each leg chooses for itself: a wall face within {@link #GRIP_CELLS} along its line, or one splay
 * step out to the side, takes the foot up on it, gripping; in the open the foot stands on the floor at
 * {@link #REST_CELLS}, short of any wall in the way. So in a hall the walker pulls itself along between the walls,
 * and in a chamber it walks. The torso itself keeps to floor cells.
 *
 * <p><b>Crippled</b> (`ruins-guardians.md`, The walker's legs). A leg whose knee is lost ({@link #legsLost}) never
 * lifts again and grips nothing: its foot stays on the floor and is dragged along behind the torso, held within
 * {@link #DRAG_SLACK_CELLS} of where it would rest, and the body sags toward it, carried on three legs. Downed, the
 * torso sinks to {@link #DOWNED_CELLS} below its rest height and no foot steps.
 *
 * <p><b>Builds.</b> The constants here are the walker's ({@link #WALKER}); Tri-Tachyon's light crawler walks the same
 * rig on its own short legs ({@link #CRAWLER}). A rig reads its sizes and gait from its {@link Build}.
 */
public final class DerelictLegRig {

    /** Whether a cell is wall: solid to a foot. */
    public interface Solid {
        boolean at(int x, int y);
    }

    public enum Gait { STAND, WALK, RUN }

    /** Hips sit on a ring this far from the torso's centre, cells. */
    public static final float HIP_CELLS = 0.55f;
    /** Where a foot rests on open floor, cells from the torso's centre. */
    public static final float REST_CELLS = 1.9f;
    /** How far a leg reaches to grip a wall, cells from the torso's centre: further, it stands on the floor. */
    public static final float GRIP_CELLS = 2.6f;
    /** A torso that jumps further than this between frames stands fresh. */
    public static final float JUMP_CELLS = 3.0f;
    /** Standing, a foot further than this from its rest spot steps back. */
    public static final float RESTEP_CELLS = 0.8f;
    /** A standing step's length. */
    public static final float STEP_SECONDS = 0.3f;
    /** Below this the walker stands; above {@link #RUN_SPEED} it runs (cells a second). */
    public static final float MOVING_SPEED = 0.12f;
    public static final float RUN_SPEED = 1.6f;
    /** Back to a walk below this, so the gait does not flicker at the threshold. */
    static final float WALK_SPEED = 1.3f;
    /** Distance a full gait cycle covers, cells. */
    public static final float WALK_STRIDE = 1.5f;
    public static final float RUN_STRIDE = 2.8f;
    /** The share of a cycle a leg spends in the air. */
    static final float WALK_SWING = 0.22f;
    static final float RUN_SWING = 0.56f;
    /** The run's swing share with no spring: each pair down a little longer than it is up, so one always stands. */
    static final float FLAT_RUN_SWING = 0.46f;
    /** How high a running foot lifts with no spring, as a share of its step lift. */
    static final float FLAT_RUN_LIFT = 0.5f;
    /** The derelict walker's run: a heavy machine's flat trot, never leaving the ground (owner: too much jump). */
    static final float WALKER_RUN_SPRING = 0.15f;
    /** Each leg's place in the cycle: the walk's four beats, and the trot's two pairs. */
    private static final float[] WALK_OFFSET = {0.25f, 0f, 0.5f, 0.75f};
    private static final float[] RUN_OFFSET = {0f, 0.5f, 0f, 0.5f};
    /** How far the body sways toward the middle of its standing feet, as a share of the way. */
    static final float SWAY = 0.30f;
    /** How far the body sinks for each leg in the air, cells. */
    static final float DIP_CELLS = 0.07f;
    /** The run's bounce, cells, twice a cycle. */
    static final float RUN_BOUNCE_CELLS = 0.10f;
    /** How far the body leans into its speed, cells per cell a second, at most {@link #LEAN_MAX_CELLS}. */
    static final float LEAN = 0.06f;
    static final float LEAN_MAX_CELLS = 0.20f;
    /** The body's pose settles toward its target over this many seconds. */
    static final float SETTLE_SECONDS = 0.10f;
    /** Speed is read over this many seconds. */
    static final float SPEED_SECONDS = 0.25f;
    /** A foot on a wall's face stands this far off it, cells, so it reads as gripping the face, not inside it. */
    static final float FACE_OFFSET = 0.08f;
    /** A floor foot stops this far short of a wall in its way. */
    static final float TUCK_CELLS = 0.35f;
    /** A leg looking for a wall swings out sideways by this much, once. */
    static final float SPLAY_DEGREES = 20f;
    /** A lost leg's foot is dragged once it trails further than this from where it would rest, cells. */
    public static final float DRAG_SLACK_CELLS = 0.6f;
    /** How far the body sags for each lost leg, cells. */
    public static final float LIMP_SAG_CELLS = 0.12f;
    /** How far the body tips toward a lost leg's hip, cells. */
    public static final float LIMP_TILT_CELLS = 0.18f;
    /** How far a downed torso sinks below its rest height, cells. */
    public static final float DOWNED_CELLS = 0.55f;
    private static final float MARCH = 0.05f;
    /**
     * The walker's body rides the crawler's suspension, a little heavier (owner: the walker is stiff), and yaws a
     * little toward a lifted leg's diagonal. UNTUNED.
     */
    static final float WALKER_SUSPENSION_SECONDS = 0.35f;
    static final float WALKER_TWIST_DEGREES = 3f;
    /** The walker's and the crawler's fastest turn, degrees a second: the machines' own turn rates. */
    public static final float WALKER_TURN_DEGREES = 75f;
    public static final float CRAWLER_TURN_DEGREES = 150f;
    /**
     * The furthest a planted foot may be carried round from its hip's own bearing by the body turning over it,
     * degrees. The hips stand 90 degrees apart, so under 45 no foot ever passes its neighbour's: the body holds its
     * turn until the foot has stepped round (owner: the legs must never get mixed up).
     */
    public static final float MAX_FOOT_DRIFT_DEGREES = 40f;
    /** A standing foot carried round this far steps round, degrees, before the body has to wait for it. */
    static final float RESTEP_DRIFT_DEGREES = 22f;
    /**
     * Turning where it stands, a stepping foot comes down this far round ahead of the body, degrees, toward the heading
     * it is asked for, so the body may turn on over it; the diagonal pair steps together. UNTUNED.
     */
    static final float TURN_LEAD_DEGREES = 35f;
    /** A turn under way keeps its direction while the heading it wants stands this far round or more, degrees. */
    static final float HALF_TURN_KEEP_DEGREES = 135f;
    private static final int LEGS = 4;

    /**
     * A four-legged machine's proportions, cells and seconds: how far out its hips and feet stand, how long and high
     * its legs are, and its gait. {@code scale} sizes the small distances that follow the machine's size (a re-step's
     * slack, how far a foot stands off a wall, a crippled leg's drag); {@code motionScale} sizes how much its body
     * moves on its legs (the dip, bounce and lean, a crippled leg's sag and tilt, how far a downed torso sinks).
     *
     * @param hipCells        hips on a ring this far from the torso's centre
     * @param restCells       where a foot rests on open floor, from the torso's centre
     * @param gripCells       how far a leg reaches to grip a wall
     * @param hipHeightCells  how high the hips stand off the floor
     * @param thighCells      the thigh's length
     * @param shinCells       the shin's length
     * @param wallFootCells   how far up a wall's face a gripping foot stands
     * @param stepLiftCells   how high a stepping foot lifts at the top of its step
     * @param walkStride      distance a walking gait cycle covers
     * @param runStride       distance a running gait cycle covers
     * @param runSpeed        above this the machine runs (cells a second)
     * @param walkSpeed       back to a walk below this
     * @param stepSeconds     a standing step's length
     * @param scale           the small distances' size against the walker's
     * @param motionScale     the body's motion on its legs against the walker's
     * @param twistDegrees    how far the body yaws toward a lifted leg's diagonal at full lift (the walker none)
     * @param jointInsetCells how far a leg part's joint centre sits in from its drawing's end
     * @param suspensionSeconds how long the body's spring takes to settle onto its pose, seconds; zero for none (the
     *                        body then settles by {@link #SETTLE_SECONDS}' lag)
     * @param turnDegreesPerSecond the fastest the body turns, degrees a second: the machine's own turn rate
     *                        ({@code MechLocomotion.turnRateDegrees}), held here too so the drawn body never outruns
     *                        its feet
     * @param runSpring       how much spring the run has, 1 the full trot (each pair a little longer in the air than
     *                        down, so the machine leaves the ground, a bounce twice a stride, feet lifted high) and 0 a
     *                        flat one (a pair always down, no bounce, feet lifted low)
     */
    public record Build(float hipCells, float restCells, float gripCells, float hipHeightCells, float thighCells,
                        float shinCells, float wallFootCells, float stepLiftCells, float walkStride, float runStride,
                        float runSpeed, float walkSpeed, float stepSeconds, float scale, float motionScale,
                        float twistDegrees, float jointInsetCells, float suspensionSeconds,
                        float turnDegreesPerSecond, float runSpring) {

        /**
         * This build at {@code k} times its size (`derelict-watch.md`, Medium and large walkers): the derelict line's
         * rounds walker and hall frame are the walker's rig at their own sizes. Lengths scale with the body, the
         * stride with them; a larger body steps more slowly, by the square root of its size, and turns more slowly,
         * in proportion to its size.
         */
        public Build scaled(float k) {
            if (!(k > 0f)) throw new IllegalArgumentException("A build scales by a positive size");
            float slow = (float) Math.sqrt(k);
            return new Build(hipCells * k, restCells * k, gripCells * k, hipHeightCells * k, thighCells * k,
                    shinCells * k, wallFootCells * k, stepLiftCells * k, walkStride * k, runStride * k,
                    runSpeed * slow, walkSpeed * slow, stepSeconds * slow, scale * k, motionScale * k,
                    twistDegrees, jointInsetCells * k, suspensionSeconds, turnDegreesPerSecond / k, runSpring);
        }
    }

    /** The derelict security walker: a heavy torso on long reaching legs. */
    public static final Build WALKER = new Build(HIP_CELLS, REST_CELLS, GRIP_CELLS, DerelictLegPose.HIP_HEIGHT_CELLS,
            DerelictLegPose.THIGH_CELLS, DerelictLegPose.SHIN_CELLS, DerelictLegPose.WALL_FOOT_HEIGHT_CELLS,
            DerelictLegPose.STEP_LIFT_CELLS, WALK_STRIDE, RUN_STRIDE, RUN_SPEED, WALK_SPEED, STEP_SECONDS, 1f, 1f,
            WALKER_TWIST_DEGREES, 0.16f, WALKER_SUSPENSION_SECONDS, WALKER_TURN_DEGREES, WALKER_RUN_SPRING);

    /**
     * Tri-Tachyon's light crawler (`mech-lines.md`, high-tech light): a small carapace low on four short legs, quick
     * on its feet. UNTUNED. The thigh and shin are its drawn parts' lengths between joint centres
     * (mod/graphics/battle/tt-crawler: 48 and 70 px at 128 px a cell), so a leg is drawn at its own length; the
     * cadence is quicker than the walker's, the feet lift higher for their size and the body bobs and twists on them.
     * Its body rides a suspension (owner: the crew inside must not feel the steps), so it eases through its gait.
     */
    public static final Build CRAWLER = new Build(0.42f, 1.05f, 1.45f, 0.30f, 0.375f, 0.55f, 0.30f, 0.30f,
            0.7f, 1.3f, 1.5f, 1.2f, 0.15f, 0.4f, 0.9f, 7f, 0.14f, 0.45f, CRAWLER_TURN_DEGREES, 1f);
    /** The spring's integration step at most, seconds, so a long frame stays stable. */
    private static final float SPRING_STEP = 1f / 120f;

    private final Build build;

    /** Each leg's direction from the torso's facing, sprite degrees (counter-clockwise from forward). */
    private final float[] hipAngle = {45f, 135f, 225f, 315f};
    /** Diagonal pairs: 45 with 225, 135 with 315. */
    private final int[] pair = {0, 1, 0, 1};
    private final float[] footX = new float[LEGS];
    private final float[] footY = new float[LEGS];
    private final float[] fromX = new float[LEGS];
    private final float[] fromY = new float[LEGS];
    private final boolean[] onWall = new boolean[LEGS];
    /** The body's heading when each foot last came down: how far the body has turned over it since. */
    private final float[] plantedFacing = new float[LEGS];
    /** Swing progress 0..1 while lifted, -1 while planted. */
    private final float[] swing = new float[LEGS];
    private boolean posed;
    private float pathX;
    private float pathY;
    private float facing;
    /** The heading the body is asked to turn to. */
    private float wantFacing;
    /** Which way the body is turning, +1, -1 or 0: kept through a turn near half round, so it never turns back. */
    private float turnSign;
    private float velX;
    private float velY;
    private float phase;
    private Gait gait = Gait.STAND;
    private float offX;
    private float offY;
    private float height;
    private float twist;
    /** The suspended body's rates, per second: its velocity in the world, its rise and its yaw. */
    private float bodyVX;
    private float bodyVY;
    private float heightV;
    private float twistV;
    private final boolean[] lost = new boolean[LEGS];
    private final boolean[] dragging = new boolean[LEGS];
    private boolean downed;

    public DerelictLegRig() {
        this(WALKER);
    }

    /** A rig of {@code build}'s proportions: the derelict walker's ({@link #WALKER}) or the crawler's. */
    public DerelictLegRig(Build build) {
        this.build = build;
        Arrays.fill(swing, -1f);
    }

    /** The proportions this rig stands and steps by. */
    public Build build() { return build; }

    public int legs() { return LEGS; }
    public boolean posed() { return posed; }
    public Gait gait() { return gait; }
    /** The drawn body: the torso's path plus its sway and lean. */
    public float bodyX() { return pathX + offX; }
    public float bodyY() { return pathY + offY; }
    /** The drawn body's rise above its rest height, cells: below zero as it dips. */
    public float bodyHeight() { return height; }
    public float facing() { return facing; }
    /** The drawn body's heading: the path's, yawed toward the diagonal whose leg is lifted. */
    public float bodyFacing() { return facing + twist; }
    public float footX(int leg) { return footX[leg]; }
    public float footY(int leg) { return footY[leg]; }
    /** Whether the leg's foot is planted on a wall's face. */
    public boolean onWall(int leg) { return onWall[leg]; }
    /** How high the foot is in its step, 0 planted to 1 at the top of the arc. */
    public float lift(int leg) {
        if (swing[leg] < 0f) return 0f;
        float arc = (float) Math.sin(Math.PI * swing[leg]);
        return gait == Gait.RUN ? arc * (FLAT_RUN_LIFT + (1f - FLAT_RUN_LIFT) * build.runSpring()) : arc;
    }
    public float hipX(int leg) { return bodyX() + dirX(bodyFacing() + hipAngle[leg]) * build.hipCells(); }
    public float hipY(int leg) { return bodyY() + dirY(bodyFacing() + hipAngle[leg]) * build.hipCells(); }
    /** Whether leg {@code i} is lifted in a step. */
    public boolean stepping(int leg) { return swing[leg] >= 0f; }
    /** Whether leg {@code i}'s knee is lost. */
    public boolean lost(int leg) { return lost[leg]; }
    /** Whether leg {@code i}'s foot was dragged along this frame. */
    public boolean dragging(int leg) { return dragging[leg]; }
    /** Whether the walker is downed, its torso on the floor. */
    public boolean downed() { return downed; }

    /**
     * The battle's leg state: bit {@code i} of {@code lostMask} set for each leg whose knee is lost, and whether the
     * walker is downed. A lost leg lets go of any wall at once.
     */
    public void legsLost(int lostMask, boolean downed) {
        for (int i = 0; i < LEGS; i++) {
            boolean now = (lostMask & (1 << i)) != 0;
            if (now && !lost[i]) {
                swing[i] = -1f;
                onWall[i] = false;
            }
            lost[i] = now;
        }
        this.downed = downed;
    }

    /** Leg {@code i}'s diagonal pair: the pair a run lifts together. */
    public int pair(int leg) { return pair[leg]; }

    /** Moves the torso to {@code (x, y)} facing {@code facingDegrees} and steps the legs over {@code dt} seconds. */
    public void advance(float x, float y, float facingDegrees, float dt, Solid solid) {
        float moved = distance(x, y, pathX, pathY);
        if (posed && moved > JUMP_CELLS) {
            // A lift, a teleport: stand fresh rather than drag the feet across the map.
            posed = false;
        }
        wantFacing = facingDegrees;
        if (!posed) {
            facing = facingDegrees;
        } else {
            facing = turn(facing, facingDegrees, dt);
        }
        if (!posed) {
            pathX = x;
            pathY = y;
            velX = velY = 0f;
            offX = offY = height = twist = 0f;
            bodyVX = bodyVY = heightV = twistV = 0f;
            gait = Gait.STAND;
            Arrays.fill(swing, -1f);
            float[] spot = new float[3];
            for (int i = 0; i < LEGS; i++) {
                rest(i, pathX, pathY, solid, spot);
                footX[i] = spot[0];
                footY[i] = spot[1];
                onWall[i] = spot[2] > 0f;
                plantedFacing[i] = facing;
            }
            posed = true;
            return;
        }
        if (dt > 0f) {
            float k = 1f - (float) Math.exp(-dt / SPEED_SECONDS);
            velX += ((x - pathX) / dt - velX) * k;
            velY += ((y - pathY) / dt - velY) * k;
        }
        float movedX = x - pathX;
        float movedY = y - pathY;
        pathX = x;
        pathY = y;
        float speed = (float) Math.sqrt(velX * velX + velY * velY);
        gait = speed < MOVING_SPEED ? Gait.STAND
                : speed > build.runSpeed() || (gait == Gait.RUN && speed > build.walkSpeed()) ? Gait.RUN : Gait.WALK;
        if (downed) gait = Gait.STAND;
        if (gait == Gait.STAND) stand(dt, solid);
        else stride(moved, speed, solid);
        drag(solid);
        pose(dt, speed, movedX, movedY);
    }

    /**
     * The body's heading this frame, turning from {@code from} toward {@code want}: no faster than the build's turn rate,
     * and never more than {@link #MAX_FOOT_DRIFT_DEGREES} past the heading any planted foot came down at; there the body
     * waits for that foot to step round (standing, it steps at once; walking, in its turn of the stride). A lost leg's
     * foot is dragged and never holds the turn.
     */
    float turn(float from, float want, float dt) {
        float diff = toward(from, want);
        float most = build.turnDegreesPerSecond() * Math.max(0f, dt);
        float step = Math.max(-most, Math.min(most, diff));
        if (step == 0f) return from;
        for (int i = 0; i < LEGS; i++) {
            if (lost[i] || swing[i] >= 0f) continue;
            // How far the body has already turned over this foot since it came down, and how far it may yet.
            float turned = wrap(from - plantedFacing[i]);
            float room = step > 0f ? Math.max(0f, MAX_FOOT_DRIFT_DEGREES - turned)
                    : Math.min(0f, -MAX_FOOT_DRIFT_DEGREES - turned);
            step = step > 0f ? Math.min(step, room) : Math.max(step, room);
        }
        return wrap(from + step);
    }

    /**
     * How far round from {@code from} to {@code want}, signed: the short way, except that a turn already under way
     * keeps its direction while the far heading stands near half round, rather than flip at the half.
     */
    private float toward(float from, float want) {
        float diff = wrap(want - from);
        if (Math.abs(diff) < 0.01f) {
            turnSign = 0f;
            return diff;
        }
        if (turnSign != 0f && Math.signum(diff) != turnSign && Math.abs(diff) > HALF_TURN_KEEP_DEGREES) {
            diff += 360f * turnSign;
        }
        turnSign = Math.signum(diff);
        return diff;
    }

    /** How far leg {@code i}'s foot stands round from its hip's own bearing with the body facing {@code heading}. */
    public float drift(int i, float heading) {
        float dx = footX[i] - pathX;
        float dy = footY[i] - pathY;
        if (dx * dx + dy * dy < 1e-6f) return 0f;
        float bearing = (float) Math.toDegrees(Math.atan2(-dx, dy));
        return wrap(bearing - (heading + hipAngle[i]));
    }

    private static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    /** Moving: the clock runs on distance and each leg swings in its window of the cycle. */
    private void stride(float moved, float speed, Solid solid) {
        boolean run = gait == Gait.RUN;
        float strideCells = run ? build.runStride() : build.walkStride();
        float swingShare = run ? FLAT_RUN_SWING + (RUN_SWING - FLAT_RUN_SWING) * build.runSpring() : WALK_SWING;
        float[] offset = run ? RUN_OFFSET : WALK_OFFSET;
        phase = (phase + moved / strideCells) % 1f;
        float hx = speed > 1e-4f ? velX / speed : 0f;
        float hy = speed > 1e-4f ? velY / speed : 0f;
        float[] spot = new float[3];
        for (int i = 0; i < LEGS; i++) {
            if (lost[i]) continue;
            float local = ((phase - offset[i]) % 1f + 1f) % 1f;
            if (local >= swingShare) {
                if (swing[i] >= 0f) land(i, solid);
                continue;
            }
            if (swing[i] < 0f) {
                fromX[i] = footX[i];
                fromY[i] = footY[i];
                onWall[i] = false;
            }
            float t = local / swingShare;
            swing[i] = t;
            // Land ahead of where the torso will be at touchdown, by half the stance, so the foot passes under it.
            float ahead = strideCells * (1f - t) * swingShare + strideCells * (1f - swingShare) / 2f;
            rest(i, pathX + hx * ahead, pathY + hy * ahead, solid, spot);
            float e = t * t * (3f - 2f * t);
            footX[i] = fromX[i] + (spot[0] - fromX[i]) * e;
            footY[i] = fromY[i] + (spot[1] - fromY[i]) * e;
        }
    }

    /** A foot at the end of its swing comes down where it was going, on the floor or up a wall. */
    private void land(int i, Solid solid) {
        float[] spot = new float[3];
        swing[i] = -1f;
        // The heading it came down at is read from where it stands, so a foot cut short in its step counts true.
        plantedFacing[i] = wrap(facing + drift(i, facing));
        rest(i, pathX, pathY, solid, spot);
        // It keeps where it came down; only whether it holds a wall is read from the ground under it.
        onWall[i] = spot[2] > 0f && distance(spot[0], spot[1], footX[i], footY[i]) < small(RESTEP_CELLS);
    }

    /** Standing: the clock stops; a swinging foot finishes its step, and a foot dragged out of place steps back. */
    private void stand(float dt, Solid solid) {
        float[] spot = new float[3];
        boolean anySwinging = false;
        for (int i = 0; i < LEGS; i++) {
            if (swing[i] < 0f || lost[i]) continue;
            anySwinging = true;
            swing[i] = Math.min(1f, swing[i] + dt / build.stepSeconds());
            // The foot steps to where it rests at the heading it lifted for (ahead of the body in a turn).
            rest(i, pathX, pathY, plantedFacing[i], solid, spot);
            float t = swing[i];
            float e = t * t * (3f - 2f * t);
            footX[i] = fromX[i] + (spot[0] - fromX[i]) * e;
            footY[i] = fromY[i] + (spot[1] - fromY[i]) * e;
            if (t >= 1f) {
                swing[i] = -1f;
                onWall[i] = spot[2] > 0f;
            }
        }
        if (anySwinging || downed) return;
        float lead = facing + Math.max(-TURN_LEAD_DEGREES, Math.min(TURN_LEAD_DEGREES, toward(facing, wantFacing)));
        int worst = -1;
        float worstStrain = small(RESTEP_CELLS);
        for (int i = 0; i < LEGS; i++) {
            if (lost[i]) continue;
            rest(i, pathX, pathY, solid, spot);
            float strain = distance(footX[i], footY[i], spot[0], spot[1]);
            // A foot the turn has left behind steps round before the body has to wait for it.
            if (turning(i, lead)) strain = Math.max(strain, worstStrain + 1f);
            if (strain > worstStrain) {
                worstStrain = strain;
                worst = i;
            }
        }
        if (worst < 0) return;
        liftToward(worst, lead);
        // In a turn the diagonal pair steps together, the other two holding the body.
        for (int i = 0; i < LEGS; i++) {
            if (i != worst && pair[i] == pair[worst] && !lost[i] && turning(i, lead)) liftToward(i, lead);
        }
    }

    /** Whether leg {@code i}'s foot stands far enough round from {@code lead} to step round to it. */
    private boolean turning(int i, float lead) {
        return Math.abs(wrap(lead - plantedFacing[i])) > RESTEP_DRIFT_DEGREES;
    }

    /** Lifts leg {@code i}'s foot to step to its rest at {@code heading}. */
    private void liftToward(int i, float heading) {
        fromX[i] = footX[i];
        fromY[i] = footY[i];
        onWall[i] = false;
        swing[i] = 0f;
        plantedFacing[i] = wrap(heading);
    }

    /**
     * A lost leg's foot stays on the floor where it is until it trails further than {@link #DRAG_SLACK_CELLS} from
     * where it would rest; then it is dragged along, held at that slack.
     */
    private void drag(Solid solid) {
        float[] spot = new float[3];
        for (int i = 0; i < LEGS; i++) {
            dragging[i] = false;
            if (!lost[i]) continue;
            floorRest(i, pathX, pathY, solid, spot);
            float d = distance(footX[i], footY[i], spot[0], spot[1]);
            if (d <= small(DRAG_SLACK_CELLS)) continue;
            float k = (d - small(DRAG_SLACK_CELLS)) / d;
            footX[i] += (spot[0] - footX[i]) * k;
            footY[i] += (spot[1] - footY[i]) * k;
            dragging[i] = true;
        }
    }

    /** The body's weight on its feet: sway over the standing feet, dip for lifted ones, the run's bounce, the lean. */
    private void pose(float dt, float speed, float movedX, float movedY) {
        float sx = 0f;
        float sy = 0f;
        int standing = 0;
        float lifted = 0f;
        float sag = 0f;
        float yaw = 0f;
        float tiltX = 0f;
        float tiltY = 0f;
        for (int i = 0; i < LEGS; i++) {
            if (lost[i]) {
                // Carried on three: the body sags and tips toward the leg that no longer holds it.
                sag += moved(LIMP_SAG_CELLS);
                tiltX += dirX(facing + hipAngle[i]) * moved(LIMP_TILT_CELLS);
                tiltY += dirY(facing + hipAngle[i]) * moved(LIMP_TILT_CELLS);
            } else if (swing[i] < 0f) {
                sx += footX[i];
                sy += footY[i];
                standing++;
            } else {
                lifted += lift(i);
                // One diagonal yaws the body one way, the other the other: a small machine wriggles as it steps.
                yaw += lift(i) * (pair[i] == 0 ? 1f : -1f);
            }
        }
        float tx = 0f;
        float ty = 0f;
        if (standing > 0) {
            tx = (sx / standing - pathX) * SWAY;
            ty = (sy / standing - pathY) * SWAY;
        }
        tx += tiltX;
        ty += tiltY;
        // At a run a pair is always in the air, so its dip is the run's bounce: as springy as the run.
        float dip = gait == Gait.RUN ? DIP_CELLS * build.runSpring() : DIP_CELLS;
        float th = downed ? -moved(DOWNED_CELLS) : -moved(dip) * lifted - sag;
        if (gait != Gait.STAND && speed > 1e-4f) {
            float lean = Math.min(moved(LEAN_MAX_CELLS), moved(LEAN) * speed);
            tx += velX / speed * lean;
            ty += velY / speed * lean;
        }
        if (gait == Gait.RUN) {
            th += moved(RUN_BOUNCE_CELLS) * build.runSpring() * (float) Math.sin(2.0 * Math.PI * 2.0 * phase);
        }
        float tw = build.twistDegrees() * Math.max(-1f, Math.min(1f, yaw));
        if (build.suspensionSeconds() > 0f) {
            suspend(dt, movedX, movedY, tx, ty, th, tw);
            return;
        }
        float k = dt <= 0f ? 1f : 1f - (float) Math.exp(-dt / SETTLE_SECONDS);
        offX += (tx - offX) * k;
        offY += (ty - offY) * k;
        height += (th - height) * k;
        twist += (tw - twist) * k;
    }

    /**
     * The suspended body: a critically damped spring pulls the drawn body toward the path plus its pose
     * ({@code tx, ty}), its height toward {@code th} and its yaw toward {@code tw}, settling within the build's
     * suspension. The body is carried with the path's smoothed velocity, so it rides along without lag at a steady
     * pace, keeps a little way on when the path stops short and eases off when it starts.
     */
    private void suspend(float dt, float movedX, float movedY, float tx, float ty, float th, float tw) {
        if (dt <= 0f) return;
        // Where the body was drawn last frame, in the world: the path has since moved on under it.
        float dx = pathX - movedX + offX;
        float dy = pathY - movedY + offY;
        float w = 4f / build.suspensionSeconds();
        int steps = Math.max(1, (int) Math.ceil(dt / SPRING_STEP));
        float h = dt / steps;
        for (int s = 0; s < steps; s++) {
            bodyVX += (w * w * (pathX + tx - dx) + 2f * w * (velX - bodyVX)) * h;
            bodyVY += (w * w * (pathY + ty - dy) + 2f * w * (velY - bodyVY)) * h;
            dx += bodyVX * h;
            dy += bodyVY * h;
            heightV += (w * w * (th - height) - 2f * w * heightV) * h;
            height += heightV * h;
            twistV += (w * w * (tw - twist) - 2f * w * twistV) * h;
            twist += twistV * h;
        }
        offX = dx - pathX;
        offY = dy - pathY;
    }

    /**
     * Where leg {@code i}'s foot would rest for a torso at {@code (cx, cy)}, as {@code {x, y, onWall}}: up the face of
     * a wall within {@link #GRIP_CELLS} along its line or one splay out, gripping; else on the floor at
     * {@link #REST_CELLS}, short of any wall in between.
     */
    void rest(int i, float cx, float cy, Solid solid, float[] out) {
        rest(i, cx, cy, facing, solid, out);
    }

    /** Where leg {@code i}'s foot would rest for a torso at {@code (cx, cy)} facing {@code heading}. */
    private void rest(int i, float cx, float cy, float heading, Solid solid, float[] out) {
        if (lost[i]) {
            floorRest(i, cx, cy, solid, out);
            return;
        }
        float own = heading + hipAngle[i];
        float side = sideOf(hipAngle[i]);
        for (int k = 0; k <= 1; k++) {
            float angle = own + side * k * SPLAY_DEGREES;
            float hit = wallDistance(cx, cy, angle, build.gripCells(), solid);
            if (hit > 0f) {
                float at = hit - small(FACE_OFFSET);
                out[0] = cx + dirX(angle) * at;
                out[1] = cy + dirY(angle) * at;
                out[2] = 1f;
                return;
            }
        }
        float hit = wallDistance(cx, cy, own, build.restCells(), solid);
        float at = hit > 0f ? Math.max(build.hipCells() + small(MARCH), hit - small(TUCK_CELLS)) : build.restCells();
        out[0] = cx + dirX(own) * at;
        out[1] = cy + dirY(own) * at;
        out[2] = 0f;
    }

    /** Where leg {@code i}'s foot would stand on the floor, never on a wall: a lost leg's, which grips nothing. */
    private void floorRest(int i, float cx, float cy, Solid solid, float[] out) {
        float own = facing + hipAngle[i];
        float hit = wallDistance(cx, cy, own, build.restCells(), solid);
        float at = hit > 0f ? Math.max(build.hipCells() + small(MARCH), hit - small(TUCK_CELLS)) : build.restCells();
        out[0] = cx + dirX(own) * at;
        out[1] = cy + dirY(own) * at;
        out[2] = 0f;
    }

    /** How far from (cx, cy) a ray at {@code angle} meets a wall, within reach; 0 when it meets none. */
    private float wallDistance(float cx, float cy, float angle, float reach, Solid solid) {
        float dx = dirX(angle);
        float dy = dirY(angle);
        for (float d = build.hipCells() + small(MARCH); d <= reach + 1e-4f; d += small(MARCH)) {
            if (solid.at((int) Math.floor(cx + dx * d), (int) Math.floor(cy + dy * d))) return d;
        }
        return 0f;
    }

    /** +1 or -1: which way from its own line a leg swings to splay out sideways, away from the fore-and-aft axis. */
    private static float sideOf(float hipAngle) {
        float a = ((hipAngle % 360f) + 360f) % 360f;
        if (a < 90f || (a >= 180f && a < 270f)) return 1f;
        return -1f;
    }

    /** One of the walker's body motions, sized to this machine. */
    private float moved(float walkerCells) {
        return build.motionScale() * walkerCells;
    }

    /** One of the walker's small distances, sized to this machine. */
    private float small(float walkerCells) {
        return build.scale() * walkerCells;
    }

    private static float distance(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** The unit vector of a sprite angle: 0 is +Y, counter-clockwise positive. */
    static float dirX(float degrees) {
        return (float) -Math.sin(Math.toRadians(degrees));
    }

    static float dirY(float degrees) {
        return (float) Math.cos(Math.toRadians(degrees));
    }
}
