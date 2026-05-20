package com.dillon.starsectormarines.render2d;

/**
 * A single radial light contribution to the lightmap. Mutable plain POJO
 * — the {@link LightAccumulator} owns transient and persistent pools and
 * rewrites their fields in place to keep allocations off the hot path.
 *
 * <p>Position and radius are in world-cell coordinates so light placement
 * survives camera zoom and pan. Color is pre-intensity (typical fire is
 * warm orange ~ (1.0, 0.55, 0.20)); {@link #intensity} is the multiplier
 * that pushes the final contribution toward / past white.
 *
 * <p>{@link #lifetimeMax} of {@code -1} flags a persistent light (managed
 * by id, not by lifetime); for those, {@link #persistentId} is the
 * caller-chosen non-zero key used by
 * {@link LightAccumulator#removePersistent(long)} /
 * {@link LightAccumulator#retainPersistent}.
 */
public final class Light {

    public float x;
    public float y;
    public float radiusCells;
    public float r;
    public float g;
    public float b;
    public float intensity;
    public LightKernel kernel;
    public float lifetimeRemaining;
    public float lifetimeMax;
    public long persistentId;
}
