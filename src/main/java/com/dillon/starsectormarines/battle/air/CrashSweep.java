package com.dillon.starsectormarines.battle.air;

/** Distance-spaced contact samples, independent of tick size and particle emission. */
public final class CrashSweep {
    private final float spacing;
    private float x, y, remainder;

    public CrashSweep(float x, float y, float radius) {
        this.x = x;
        this.y = y;
        this.spacing = Math.max(.5f, radius * .5f);
    }

    @FunctionalInterface
    public interface Contact { void at(float x, float y); }

    public void advance(float nextX, float nextY, Contact contact) {
        float dx = nextX - x, dy = nextY - y;
        float length = (float) Math.hypot(dx, dy);
        if (length > 0f) {
            float distance = spacing - remainder;
            while (distance <= length + .00001f) {
                float t = Math.min(1f, distance / length);
                contact.at(x + dx * t, y + dy * t);
                distance += spacing;
            }
            remainder = Math.max(0f, length - (distance - spacing));
        }
        x = nextX;
        y = nextY;
    }
}
