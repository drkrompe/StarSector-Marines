package com.dillon.starsectormarines.battle.evacuation.flier;

import com.dillon.starsectormarines.battle.air.AirBody;

import java.util.List;

/**
 * The Harrier's own steering, which it needs because it flies: the ground flow field is a map of walls and
 * doors, and nothing in it is in a flier's way. Seek a goal, push off packmates, turn at a capped rate, and move
 * along the nose. Arithmetic only, in member order, so the same pack flies the same way every replay, and a pack
 * of twenty costs four hundred distance checks a tick.
 */
public final class HarrierSteering {

    private HarrierSteering() {}

    /**
     * The push away from packmates closer than {@link HarrierRules#SEPARATION_RADIUS}, written to
     * {@code out[0..1]}. Two bodies on exactly the same point are split along a bearing taken from their slots,
     * so the answer never depends on a tie in floating point.
     */
    public static void separation(HarrierFlight self, List<HarrierFlight> pack, float[] out) {
        float sx = 0f, sy = 0f;
        float r = HarrierRules.SEPARATION_RADIUS;
        for (int i = 0, n = pack.size(); i < n; i++) {
            HarrierFlight other = pack.get(i);
            if (other == self) continue;
            float dx = self.body.x - other.body.x;
            float dy = self.body.y - other.body.y;
            float d2 = dx * dx + dy * dy;
            if (d2 >= r * r) continue;
            float d = (float) Math.sqrt(d2);
            float ux, uy;
            if (d < 1e-4f) {
                // A bearing for the pair from both slots (golden angle steps), each taking an opposite end of it.
                double a = (Math.min(self.slot, other.slot) * 31 + Math.max(self.slot, other.slot)) * 2.399963;
                float sign = self.slot < other.slot ? 1f : -1f;
                ux = sign * (float) Math.cos(a);
                uy = sign * (float) Math.sin(a);
            } else {
                ux = dx / d;
                uy = dy / d;
            }
            float push = (r - d) / r;
            sx += ux * push;
            sy += uy * push;
        }
        out[0] = sx * HarrierRules.SEPARATION_WEIGHT;
        out[1] = sy * HarrierRules.SEPARATION_WEIGHT;
    }

    /**
     * One tick of flight toward ({@code goalX}, {@code goalY}) at {@code speed}, bent by {@code pushX/pushY}: the
     * nose turns toward the desired bearing by at most {@code turnDegPerSec * dt}, the speed eases toward
     * {@code speed} at {@link HarrierRules#ACCEL}, and the body moves along its nose. A flier cannot hover or
     * fly sideways, so a goal behind it is reached by turning, which is what makes a pull-up an arc.
     */
    public static void fly(AirBody body, float goalX, float goalY, float pushX, float pushY,
                           float speed, float turnDegPerSec, float dt) {
        body.previousX = body.x;
        body.previousY = body.y;
        float dx = goalX - body.x;
        float dy = goalY - body.y;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        float wx = (d > 1e-4f ? dx / d : 0f) + pushX;
        float wy = (d > 1e-4f ? dy / d : 0f) + pushY;
        float facing = body.facingDegrees;
        if (wx * wx + wy * wy > 1e-8f) {
            float want = AirBody.facingToward(wx, wy);
            float delta = shortest(want - facing);
            float step = turnDegPerSec * dt;
            float turned = Math.max(-step, Math.min(step, delta));
            facing = normalise(facing + turned);
            body.angVelDegPerSec = turned / dt;
        } else {
            body.angVelDegPerSec = 0f;
        }
        body.facingDegrees = facing;
        float current = body.speed();
        float next = current < speed ? Math.min(speed, current + HarrierRules.ACCEL * dt)
                : Math.max(speed, current - HarrierRules.ACCEL * dt);
        double rad = Math.toRadians(facing + 90f);
        float nvx = (float) Math.cos(rad) * next;
        float nvy = (float) Math.sin(rad) * next;
        body.ax = (nvx - body.vx) / dt;
        body.ay = (nvy - body.vy) / dt;
        body.vx = nvx;
        body.vy = nvy;
        body.x += nvx * dt;
        body.y += nvy * dt;
    }

    /** Eases {@code altitude} toward {@code target}, falling at the descent rate and rising at the climb rate. */
    public static float ease(float altitude, float target, float dt) {
        if (altitude > target) return Math.max(target, altitude - HarrierRules.DESCENT_RATE * dt);
        return Math.min(target, altitude + HarrierRules.CLIMB_RATE * dt);
    }

    static float shortest(float deg) {
        float d = deg % 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    static float normalise(float deg) {
        float d = deg % 360f;
        if (d > 180f) d -= 360f;
        if (d <= -180f) d += 360f;
        return d;
    }
}
