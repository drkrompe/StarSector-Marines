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
 * into its speed ({@link #bodyX()}, {@link #bodyHeight()}). The hips ride on the drawn body.
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
    private static final int LEGS = 4;

    /** Each leg's direction from the torso's facing, sprite degrees (counter-clockwise from forward). */
    private final float[] hipAngle = {45f, 135f, 225f, 315f};
    /** Diagonal pairs: 45 with 225, 135 with 315. */
    private final int[] pair = {0, 1, 0, 1};
    private final float[] footX = new float[LEGS];
    private final float[] footY = new float[LEGS];
    private final float[] fromX = new float[LEGS];
    private final float[] fromY = new float[LEGS];
    private final boolean[] onWall = new boolean[LEGS];
    /** Swing progress 0..1 while lifted, -1 while planted. */
    private final float[] swing = new float[LEGS];
    private boolean posed;
    private float pathX;
    private float pathY;
    private float facing;
    private float velX;
    private float velY;
    private float phase;
    private Gait gait = Gait.STAND;
    private float offX;
    private float offY;
    private float height;
    private final boolean[] lost = new boolean[LEGS];
    private final boolean[] dragging = new boolean[LEGS];
    private boolean downed;

    public DerelictLegRig() {
        Arrays.fill(swing, -1f);
    }

    public int legs() { return LEGS; }
    public boolean posed() { return posed; }
    public Gait gait() { return gait; }
    /** The drawn body: the torso's path plus its sway and lean. */
    public float bodyX() { return pathX + offX; }
    public float bodyY() { return pathY + offY; }
    /** The drawn body's rise above its rest height, cells: below zero as it dips. */
    public float bodyHeight() { return height; }
    public float facing() { return facing; }
    public float footX(int leg) { return footX[leg]; }
    public float footY(int leg) { return footY[leg]; }
    /** Whether the leg's foot is planted on a wall's face. */
    public boolean onWall(int leg) { return onWall[leg]; }
    /** How high the foot is in its step, 0 planted to 1 at the top of the arc. */
    public float lift(int leg) {
        return swing[leg] < 0f ? 0f : (float) Math.sin(Math.PI * swing[leg]);
    }
    public float hipX(int leg) { return bodyX() + dirX(facing + hipAngle[leg]) * HIP_CELLS; }
    public float hipY(int leg) { return bodyY() + dirY(facing + hipAngle[leg]) * HIP_CELLS; }
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
        facing = facingDegrees;
        if (!posed) {
            pathX = x;
            pathY = y;
            velX = velY = 0f;
            offX = offY = height = 0f;
            gait = Gait.STAND;
            Arrays.fill(swing, -1f);
            float[] spot = new float[3];
            for (int i = 0; i < LEGS; i++) {
                rest(i, pathX, pathY, solid, spot);
                footX[i] = spot[0];
                footY[i] = spot[1];
                onWall[i] = spot[2] > 0f;
            }
            posed = true;
            return;
        }
        if (dt > 0f) {
            float k = 1f - (float) Math.exp(-dt / SPEED_SECONDS);
            velX += ((x - pathX) / dt - velX) * k;
            velY += ((y - pathY) / dt - velY) * k;
        }
        pathX = x;
        pathY = y;
        float speed = (float) Math.sqrt(velX * velX + velY * velY);
        gait = speed < MOVING_SPEED ? Gait.STAND
                : speed > RUN_SPEED || (gait == Gait.RUN && speed > WALK_SPEED) ? Gait.RUN : Gait.WALK;
        if (downed) gait = Gait.STAND;
        if (gait == Gait.STAND) stand(dt, solid);
        else stride(moved, speed, solid);
        drag(solid);
        pose(dt, speed);
    }

    /** Moving: the clock runs on distance and each leg swings in its window of the cycle. */
    private void stride(float moved, float speed, Solid solid) {
        boolean run = gait == Gait.RUN;
        float strideCells = run ? RUN_STRIDE : WALK_STRIDE;
        float swingShare = run ? RUN_SWING : WALK_SWING;
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
        rest(i, pathX, pathY, solid, spot);
        // It keeps where it came down; only whether it holds a wall is read from the ground under it.
        onWall[i] = spot[2] > 0f && distance(spot[0], spot[1], footX[i], footY[i]) < RESTEP_CELLS;
    }

    /** Standing: the clock stops; a swinging foot finishes its step, and a foot dragged out of place steps back. */
    private void stand(float dt, Solid solid) {
        float[] spot = new float[3];
        boolean anySwinging = false;
        for (int i = 0; i < LEGS; i++) {
            if (swing[i] < 0f || lost[i]) continue;
            anySwinging = true;
            swing[i] = Math.min(1f, swing[i] + dt / STEP_SECONDS);
            rest(i, pathX, pathY, solid, spot);
            float t = swing[i];
            footX[i] = fromX[i] + (spot[0] - fromX[i]) * t;
            footY[i] = fromY[i] + (spot[1] - fromY[i]) * t;
            if (t >= 1f) {
                swing[i] = -1f;
                onWall[i] = spot[2] > 0f;
            }
        }
        if (anySwinging || downed) return;
        int worst = -1;
        float worstStrain = RESTEP_CELLS;
        for (int i = 0; i < LEGS; i++) {
            if (lost[i]) continue;
            rest(i, pathX, pathY, solid, spot);
            float strain = distance(footX[i], footY[i], spot[0], spot[1]);
            if (strain > worstStrain) {
                worstStrain = strain;
                worst = i;
            }
        }
        if (worst < 0) return;
        fromX[worst] = footX[worst];
        fromY[worst] = footY[worst];
        onWall[worst] = false;
        swing[worst] = 0f;
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
            if (d <= DRAG_SLACK_CELLS) continue;
            float k = (d - DRAG_SLACK_CELLS) / d;
            footX[i] += (spot[0] - footX[i]) * k;
            footY[i] += (spot[1] - footY[i]) * k;
            dragging[i] = true;
        }
    }

    /** The body's weight on its feet: sway over the standing feet, dip for lifted ones, the run's bounce, the lean. */
    private void pose(float dt, float speed) {
        float sx = 0f;
        float sy = 0f;
        int standing = 0;
        float lifted = 0f;
        float sag = 0f;
        float tiltX = 0f;
        float tiltY = 0f;
        for (int i = 0; i < LEGS; i++) {
            if (lost[i]) {
                // Carried on three: the body sags and tips toward the leg that no longer holds it.
                sag += LIMP_SAG_CELLS;
                tiltX += dirX(facing + hipAngle[i]) * LIMP_TILT_CELLS;
                tiltY += dirY(facing + hipAngle[i]) * LIMP_TILT_CELLS;
            } else if (swing[i] < 0f) {
                sx += footX[i];
                sy += footY[i];
                standing++;
            } else {
                lifted += lift(i);
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
        float th = downed ? -DOWNED_CELLS : -DIP_CELLS * lifted - sag;
        if (gait != Gait.STAND && speed > 1e-4f) {
            float lean = Math.min(LEAN_MAX_CELLS, LEAN * speed);
            tx += velX / speed * lean;
            ty += velY / speed * lean;
        }
        if (gait == Gait.RUN) th += RUN_BOUNCE_CELLS * (float) Math.sin(2.0 * Math.PI * 2.0 * phase);
        float k = dt <= 0f ? 1f : 1f - (float) Math.exp(-dt / SETTLE_SECONDS);
        offX += (tx - offX) * k;
        offY += (ty - offY) * k;
        height += (th - height) * k;
    }

    /**
     * Where leg {@code i}'s foot would rest for a torso at {@code (cx, cy)}, as {@code {x, y, onWall}}: up the face of
     * a wall within {@link #GRIP_CELLS} along its line or one splay out, gripping; else on the floor at
     * {@link #REST_CELLS}, short of any wall in between.
     */
    void rest(int i, float cx, float cy, Solid solid, float[] out) {
        if (lost[i]) {
            floorRest(i, cx, cy, solid, out);
            return;
        }
        float own = facing + hipAngle[i];
        float side = sideOf(hipAngle[i]);
        for (int k = 0; k <= 1; k++) {
            float angle = own + side * k * SPLAY_DEGREES;
            float hit = wallDistance(cx, cy, angle, GRIP_CELLS, solid);
            if (hit > 0f) {
                float at = hit - FACE_OFFSET;
                out[0] = cx + dirX(angle) * at;
                out[1] = cy + dirY(angle) * at;
                out[2] = 1f;
                return;
            }
        }
        float hit = wallDistance(cx, cy, own, REST_CELLS, solid);
        float at = hit > 0f ? Math.max(HIP_CELLS + MARCH, hit - TUCK_CELLS) : REST_CELLS;
        out[0] = cx + dirX(own) * at;
        out[1] = cy + dirY(own) * at;
        out[2] = 0f;
    }

    /** Where leg {@code i}'s foot would stand on the floor, never on a wall: a lost leg's, which grips nothing. */
    private void floorRest(int i, float cx, float cy, Solid solid, float[] out) {
        float own = facing + hipAngle[i];
        float hit = wallDistance(cx, cy, own, REST_CELLS, solid);
        float at = hit > 0f ? Math.max(HIP_CELLS + MARCH, hit - TUCK_CELLS) : REST_CELLS;
        out[0] = cx + dirX(own) * at;
        out[1] = cy + dirY(own) * at;
        out[2] = 0f;
    }

    /** How far from (cx, cy) a ray at {@code angle} meets a wall, within reach; 0 when it meets none. */
    private static float wallDistance(float cx, float cy, float angle, float reach, Solid solid) {
        float dx = dirX(angle);
        float dy = dirY(angle);
        for (float d = HIP_CELLS + MARCH; d <= reach + 1e-4f; d += MARCH) {
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
