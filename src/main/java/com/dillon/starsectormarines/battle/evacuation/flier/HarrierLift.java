package com.dillon.starsectormarines.battle.evacuation.flier;

/**
 * A marine a Harrier's strike snatched off its feet and threw: carried up off the ground along a short arc
 * from where it stood to where it lands, turning over as it goes, and hurt by the ground when it comes down.
 * Rare and seeded ({@link #rolls}). The sim moves the body along {@link #x()}/{@link #y()}; {@link #height()}
 * and {@link #spin()} are what it is drawn with.
 */
public final class HarrierLift {

    public final long harrier, body;
    final float x0, y0, x1, y1;
    final float seconds;
    private final float spinDegrees;
    private float t;

    HarrierLift(long harrier, long body, float x0, float y0, float x1, float y1) {
        this.harrier = harrier;
        this.body = body;
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        this.seconds = HarrierRules.LIFT_SECONDS;
        long h = (harrier * 31L + body) * 0x9E3779B97F4A7C15L;
        float share = (float) ((h >>> 40) % 1000) / 1000f;
        this.spinDegrees = ((h & 1L) == 0L ? 1f : -1f) * (300f + 240f * share);
    }

    /**
     * Whether {@code harrier}'s strike on {@code body} at {@code tick} lifts it, at {@code chance}: a hash of the
     * three, so a replay lifts the same bodies and a body struck twice is not lifted twice by the same roll.
     */
    static boolean rolls(long harrier, long body, long tick, float chance) {
        long h = ((harrier * 31L + body) * 31L + tick) * 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        return (float) ((h >>> 8) & 0xFFFFFF) / 0x1000000 < chance;
    }

    void advance(float dt) { t = Math.min(seconds, t + dt); }

    /** 0 when lifted, 1 on the ground again. */
    public float progress() { return seconds > 0f ? t / seconds : 1f; }

    public boolean landed() { return t >= seconds; }

    public float x() { return x0 + (x1 - x0) * progress(); }

    public float y() { return y0 + (y1 - y0) * progress(); }

    /** Cells off the ground: a throw's arc, up quickly and down again, highest halfway. */
    public float height() {
        float p = progress();
        return 4f * HarrierRules.LIFT_PEAK_CELLS * p * (1f - p);
    }

    /** Degrees it has turned over in the air. */
    public float spin() { return spinDegrees * progress(); }
}
