package com.dillon.starsectormarines.render2d;

/**
 * Sample-history ribbon backing a contrail / engine plume / smoke streak.
 * Pure data — no GL, no scene refs — so the same struct works for missile
 * tails, shuttle engine wash, wreck smoke, charge fuses, anything that
 * wants a fading line behind a moving source.
 *
 * <p>Lifecycle per source:
 * <ul>
 *   <li>Every render frame, call {@link #pushSample} with the source's
 *       current world position. The trail gates on {@link ContrailStyle#minSegLenCells}
 *       so successive near-duplicate pushes are dropped.</li>
 *   <li>Call {@link #advance} once per frame with the real-time {@code dt}.
 *       Samples that outlive {@link ContrailStyle#durationSec} are dropped
 *       from the tail end.</li>
 *   <li>When the source dies, stop calling {@link #pushSample} but keep
 *       advancing — the existing samples will age out and {@link #isEmpty}
 *       flips true, signaling the trail can be discarded.</li>
 * </ul>
 *
 * <p>Sample storage is a fixed-capacity ring buffer. Once full, pushing a
 * new sample evicts the oldest — bounds the per-trail memory regardless of
 * how long the source lives. Sized so the duration × max-source-speed fits
 * comfortably; for missile-class projectiles, 32 samples is enough headroom.
 *
 * <p>NOT thread-safe. Single producer (sim/render thread), single consumer
 * (renderer).
 */
public final class ContrailTrail {

    /**
     * One {@code (x, y, ageSec)} sample. Mutable + reused — the ring buffer
     * pre-allocates one instance per slot at construction, and
     * {@link #pushSample} overwrites the next slot's fields in place rather
     * than allocating. Zero steady-state garbage per push.
     */
    public static final class Sample {
        public float x;
        public float y;
        /** Seconds since this sample was pushed. {@link #advance} bumps this; samples with {@code age > style.durationSec} get evicted. */
        public float age;
    }

    public final ContrailStyle style;
    private final Sample[] ring;
    private int head;
    private int count;
    private boolean hasPrev;
    private float prevX;
    private float prevY;

    public ContrailTrail(ContrailStyle style, int capacity) {
        if (capacity < 2) throw new IllegalArgumentException("capacity must be >= 2");
        this.style = style;
        this.ring = new Sample[capacity];
        for (int i = 0; i < capacity; i++) this.ring[i] = new Sample();
        this.head = 0;
        this.count = 0;
        this.hasPrev = false;
    }

    /**
     * Push a new sample at {@code (x, y)} with age=0. Skipped if the last
     * pushed sample was within {@link ContrailStyle#minSegLenCells}. Evicts
     * the oldest sample when the ring is full.
     */
    public void pushSample(float x, float y) {
        if (hasPrev) {
            float dx = x - prevX;
            float dy = y - prevY;
            if (dx * dx + dy * dy < style.minSegLenSqCells) return;
        }
        hasPrev = true;
        prevX = x;
        prevY = y;

        int slot;
        if (count < ring.length) {
            slot = (head + count) % ring.length;
            count++;
        } else {
            slot = head;
            head = (head + 1) % ring.length;
        }
        Sample s = ring[slot];
        s.x = x;
        s.y = y;
        s.age = 0f;
    }

    /**
     * Age every live sample by {@code dtSec} and evict any whose age has
     * exceeded {@link ContrailStyle#durationSec}. Oldest sample sits at
     * {@code get(0)} (index 0 = ring tail), so eviction walks the head
     * pointer forward.
     */
    public void advance(float dtSec) {
        for (int i = 0; i < count; i++) {
            ring[(head + i) % ring.length].age += dtSec;
        }
        while (count > 0 && ring[head].age > style.durationSec) {
            head = (head + 1) % ring.length;
            count--;
        }
    }

    /** Number of live samples. {@code < 2} means no segments to draw. */
    public int size() { return count; }

    public boolean isEmpty() { return count == 0; }

    /**
     * Sample {@code i} in oldest-to-newest order — {@code get(0)} is the
     * tail of the ribbon (oldest, dissipating), {@code get(size()-1)} is
     * the leading edge at the source's current position.
     */
    public Sample get(int i) {
        return ring[(head + i) % ring.length];
    }
}
