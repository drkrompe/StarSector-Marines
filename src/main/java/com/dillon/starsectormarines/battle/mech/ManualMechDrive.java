package com.dillon.starsectormarines.battle.mech;

/** Retained manual chassis velocity; terrain and session lifecycle own its cancellation. */
public final class ManualMechDrive {
    private float velocityX;
    private float velocityY;

    public float velocityX() { return velocityX; }
    public float velocityY() { return velocityY; }
    public void clear() { velocityX = 0f; velocityY = 0f; }

    /** Exact exponential velocity response and its integrated displacement for constant input. */
    public Step advance(float axisX, float axisY, float speed, float relativeMass, float dt) {
        if (!Float.isFinite(axisX) || !Float.isFinite(axisY) || !Float.isFinite(speed)
                || !Float.isFinite(relativeMass) || relativeMass <= 0f
                || !Float.isFinite(dt) || dt < 0f) {
            throw new IllegalArgumentException("Manual drive requires finite input, positive mass and nonnegative time");
        }
        if (dt == 0f) return new Step(0f, 0f);
        float maxSpeed = Math.max(0f, speed);
        double divisor = Math.max(1d, Math.hypot(axisX, axisY));
        float targetX = (float) (axisX / divisor * maxSpeed);
        float targetY = (float) (axisY / divisor * maxSpeed);
        // Neutral input and reversal arrest old motion faster than ordinary acceleration.
        boolean braking = (targetX == 0f && targetY == 0f)
                || targetX * velocityX + targetY * velocityY < 0f;
        float rate = (braking ? 7f : 5f) / relativeMass;
        float response = (float) -Math.expm1(-rate * dt);
        float oldX = velocityX, oldY = velocityY;
        velocityX += (targetX - velocityX) * response;
        velocityY += (targetY - velocityY) * response;
        // A live speed reduction must cap the existing momentum as well as desired motion.
        float length = (float) Math.hypot(velocityX, velocityY);
        if (length > maxSpeed) {
            velocityX *= maxSpeed / length;
            velocityY *= maxSpeed / length;
            oldX = velocityX;
            oldY = velocityY;
        }
        float dx = targetX * dt + (oldX - targetX) * response / rate;
        float dy = targetY * dt + (oldY - targetY) * response / rate;
        float distance = (float) Math.hypot(dx, dy), budget = maxSpeed * dt;
        if (distance > budget) { dx *= budget / distance; dy *= budget / distance; }
        if (targetX == 0f && targetY == 0f && Math.hypot(velocityX, velocityY) < .001f) clear();
        return new Step(dx, dy);
    }

    /** Contact arrests each clipped component immediately; legal slide keeps its tangent. */
    public void acceptMotion(Step requested, float dx, float dy, float dt) {
        if (dt <= 0f) return;
        if (Math.abs(dx - requested.dx()) > 1e-6f) velocityX = 0f;
        if (Math.abs(dy - requested.dy()) > 1e-6f) velocityY = 0f;
    }

    public record Step(float dx, float dy) { }
}
