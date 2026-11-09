package com.dillon.starsectormarines.battle.appearance;

import java.util.Arrays;

/**
 * The derelict security walker's legs, seen from above (`derelict-security-network.md`, slice 9): a low torso slung
 * between four legs that reach out from a ring of hips and plant their feet. Presentation only, like the Rav
 * rigs: the walker moves as any mech does and nothing in the battle reads its feet back.
 *
 * <p><b>Stepping.</b> Each foot stays planted where it landed while the torso moves over it. When the feet of a step
 * group have strayed past {@link #RESTEP_CELLS} from where they would rest, that group lifts and swings to new spots
 * over {@link #STEP_SECONDS}; the others hold. The legs step in diagonal pairs (front-left with back-right), so
 * the torso always stands on two feet.
 *
 * <p><b>Reaching to walls.</b> With {@link #wallReach()} a leg swings out sideways from its own line, up to
 * {@link #SPLAY_STEPS} steps of {@link #SPLAY_DEGREES}, to the first wall face within {@link #MAX_REACH_CELLS} and
 * plants its foot up on it, so in a hall the walker pulls itself along between the walls; with no wall in reach the
 * foot plants on the floor at {@link #REST_CELLS}. Without it a foot stays on the floor, tucked short of a wall in its
 * way. The torso itself keeps to floor cells either way.
 */
public final class DerelictLegRig {

    /** Whether a cell is wall: solid to a foot. */
    public interface Solid {
        boolean at(int x, int y);
    }

    /** Hips sit on a ring this far from the torso's centre, cells. */
    public static final float HIP_CELLS = 0.55f;
    /** Where a foot rests on open floor, cells from the torso's centre. */
    public static final float REST_CELLS = 1.9f;
    /** How far a leg reaches for a wall, cells from the torso's centre. */
    public static final float MAX_REACH_CELLS = 3.0f;
    /** A foot further than this from its rest spot steps. */
    public static final float RESTEP_CELLS = 0.8f;
    public static final float STEP_SECONDS = 0.28f;
    /** A foot on a wall's face stands this far off it, cells, so it reads as gripping the face, not inside it. */
    static final float FACE_OFFSET = 0.08f;
    /** A floor foot stops this far short of a wall in its way. */
    static final float TUCK_CELLS = 0.35f;
    /** A reaching leg swings out sideways in steps of this many degrees, up to {@link #SPLAY_STEPS}, to find a wall. */
    static final float SPLAY_DEGREES = 15f;
    static final int SPLAY_STEPS = 2;
    private static final float MARCH = 0.05f;

    private final int legs;
    private final boolean wallReach;
    /** Each leg's direction from the torso's facing, sprite degrees (counter-clockwise from forward). */
    private final float[] hipAngle;
    private final int[] group;
    private final int groups;
    private final float[] footX;
    private final float[] footY;
    private final float[] fromX;
    private final float[] fromY;
    private final float[] toX;
    private final float[] toY;
    private final boolean[] toWall;
    private final boolean[] onWall;
    /** Swing progress 0..1 while lifted, -1 while planted. */
    private final float[] swing;
    private boolean posed;
    private float bodyX;
    private float bodyY;
    private float facing;

    /**
     * @param wallReach whether legs reach out to plant on walls
     */
    public DerelictLegRig(boolean wallReach) {
        this.legs = 4;
        this.wallReach = wallReach;
        hipAngle = new float[] {45f, 135f, 225f, 315f};
        // Front-left (45) with back-right (225), then back-left (135) with front-right (315).
        group = new int[] {0, 1, 0, 1};
        groups = 2;
        footX = new float[legs];
        footY = new float[legs];
        fromX = new float[legs];
        fromY = new float[legs];
        toX = new float[legs];
        toY = new float[legs];
        toWall = new boolean[legs];
        onWall = new boolean[legs];
        swing = new float[legs];
        Arrays.fill(swing, -1f);
    }

    public int legs() { return legs; }
    public boolean wallReach() { return wallReach; }
    public boolean posed() { return posed; }
    public float bodyX() { return bodyX; }
    public float bodyY() { return bodyY; }
    public float facing() { return facing; }
    public float footX(int leg) { return footX[leg]; }
    public float footY(int leg) { return footY[leg]; }
    /** Whether the leg's foot is planted on a wall's face. */
    public boolean onWall(int leg) { return onWall[leg]; }
    /** How high the foot is in its step, 0 planted to 1 at the top of the arc. */
    public float lift(int leg) {
        return swing[leg] < 0f ? 0f : (float) Math.sin(Math.PI * swing[leg]);
    }
    public float hipX(int leg) { return bodyX + dirX(facing + hipAngle[leg]) * HIP_CELLS; }
    public float hipY(int leg) { return bodyY + dirY(facing + hipAngle[leg]) * HIP_CELLS; }

    /** Moves the torso to {@code (x, y)} facing {@code facingDegrees} and steps the legs over {@code dt} seconds. */
    public void advance(float x, float y, float facingDegrees, float dt, Solid solid) {
        // A torso that jumped further than a leg reaches (a lift, a teleport) stands fresh rather than dragging its feet.
        if (posed && (x - bodyX) * (x - bodyX) + (y - bodyY) * (y - bodyY) > MAX_REACH_CELLS * MAX_REACH_CELLS) {
            posed = false;
            Arrays.fill(swing, -1f);
        }
        bodyX = x;
        bodyY = y;
        facing = facingDegrees;
        float[] spot = new float[3];
        if (!posed) {
            for (int i = 0; i < legs; i++) {
                rest(i, solid, spot);
                footX[i] = spot[0];
                footY[i] = spot[1];
                onWall[i] = spot[2] > 0f;
            }
            posed = true;
            return;
        }
        boolean stepping = false;
        for (int i = 0; i < legs; i++) {
            if (swing[i] < 0f) continue;
            swing[i] = Math.min(1f, swing[i] + dt / STEP_SECONDS);
            float t = swing[i];
            footX[i] = fromX[i] + (toX[i] - fromX[i]) * t;
            footY[i] = fromY[i] + (toY[i] - fromY[i]) * t;
            if (t >= 1f) {
                swing[i] = -1f;
                onWall[i] = toWall[i];
            } else {
                stepping = true;
            }
        }
        if (stepping) return;
        int worst = -1;
        float worstStrain = RESTEP_CELLS;
        for (int g = 0; g < groups; g++) {
            float strain = 0f;
            for (int i = 0; i < legs; i++) {
                if (group[i] != g) continue;
                rest(i, solid, spot);
                strain = Math.max(strain, distance(footX[i], footY[i], spot[0], spot[1]));
            }
            if (strain > worstStrain) {
                worstStrain = strain;
                worst = g;
            }
        }
        if (worst < 0) return;
        for (int i = 0; i < legs; i++) {
            if (group[i] != worst) continue;
            rest(i, solid, spot);
            fromX[i] = footX[i];
            fromY[i] = footY[i];
            toX[i] = spot[0];
            toY[i] = spot[1];
            toWall[i] = spot[2] > 0f;
            onWall[i] = false;
            swing[i] = 0f;
        }
    }

    /**
     * Where leg {@code i}'s foot would rest now, as {@code {x, y, onWall}}: on the first wall face along its line within
     * reach when it reaches for walls, else on the floor at {@link #REST_CELLS}, short of any wall in between.
     */
    void rest(int i, Solid solid, float[] out) {
        float own = facing + hipAngle[i];
        if (wallReach) {
            // Swing the leg out toward the walker's side, a SPLAY step at a time, until it finds a wall face in reach.
            float side = sideOf(hipAngle[i]);
            for (int k = 0; k <= SPLAY_STEPS; k++) {
                float angle = own + side * k * SPLAY_DEGREES;
                float hit = wallDistance(angle, MAX_REACH_CELLS, solid);
                if (hit > 0f) {
                    float at = hit - FACE_OFFSET;
                    out[0] = bodyX + dirX(angle) * at;
                    out[1] = bodyY + dirY(angle) * at;
                    out[2] = 1f;
                    return;
                }
            }
        }
        // On the floor: at rest, or tucked in short of a wall in the way.
        float hit = wallDistance(own, REST_CELLS, solid);
        float at = hit > 0f ? Math.max(HIP_CELLS + MARCH, hit - TUCK_CELLS) : REST_CELLS;
        out[0] = bodyX + dirX(own) * at;
        out[1] = bodyY + dirY(own) * at;
        out[2] = 0f;
    }

    /** How far from the torso's centre a ray at {@code angle} meets a wall, within reach; 0 when it meets none. */
    private float wallDistance(float angle, float reach, Solid solid) {
        float dx = dirX(angle);
        float dy = dirY(angle);
        for (float d = HIP_CELLS + MARCH; d <= reach + 1e-4f; d += MARCH) {
            if (solid.at((int) Math.floor(bodyX + dx * d), (int) Math.floor(bodyY + dy * d))) return d;
        }
        return 0f;
    }

    /** +1 or -1: which way from its own line a leg swings to splay out sideways, away from the fore-and-aft axis. */
    private static float sideOf(float hipAngle) {
        float a = ((hipAngle % 360f) + 360f) % 360f;
        if (a < 90f || (a >= 180f && a < 270f)) return 1f;
        return -1f;
    }

    /** Whether leg {@code i} is lifted in a step. */
    public boolean stepping(int leg) {
        return swing[leg] >= 0f;
    }

    /** Leg {@code i}'s step group: legs of one group lift together. */
    public int group(int leg) {
        return group[leg];
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
