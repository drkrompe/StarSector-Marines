package com.dillon.starsectormarines.battle.unit;

/**
 * How old a swarm body is (`colony-breach-nouns.md`, Growth stage): whelp,
 * adult or elder. A stage is not a strain: a whelp Runner is a Runner, with the
 * Runner's {@link UnitType}, behaviour and rig. It is a property of one body,
 * issued at spawn and fixed for the battle (the Matriarch alone matures in the
 * field), which is why it lives on the body rather than in the append-only
 * {@link UnitType}.
 *
 * <p>What the stage itself owns is the part every strain shares: how big the
 * body is drawn against the adult, and how much of the director's population
 * target it counts for. A strain's stats by stage are the strain's own table.
 */
public enum SwarmStage {
    WHELP(0.80f, 0.88f, 0.6f),
    ADULT(1.00f, 1.00f, 1.0f),
    ELDER(1.22f, 1.12f, 1.8f);

    /**
     * The largest sim radius any stage of a small strain may have: under half a
     * cell, so one walkable-cell flow field serves every body and an elder
     * squeezes through a door it visibly fills. Only the elder Brute (0.55
     * natural) reaches it; it is drawn and shoved at full size. The Matriarch,
     * multi-cell at every stage, paths for herself and is never capped.
     */
    public static final float SIM_RADIUS_CAP = 0.49f;

    /** Drawn size against the adult, linear, for the small strains. */
    public final float scale;
    /** The Matriarch's: narrower, because she is already several cells long. */
    public final float matriarchScale;
    /** What one body counts for against the director's population target. */
    public final float populationWeight;

    SwarmStage(float scale, float matriarchScale, float populationWeight) {
        this.scale = scale;
        this.matriarchScale = matriarchScale;
        this.populationWeight = populationWeight;
    }

    /** The stage a body without one is: an adult. */
    public static SwarmStage of(SwarmStage stage) {
        return stage != null ? stage : ADULT;
    }

    /** Drawn size against the adult for a body of {@code type}. */
    public float scale(UnitType type) {
        return type == UnitType.SWARM_MATRIARCH ? matriarchScale : scale;
    }

    /**
     * The sim radius of a {@code type} body of this stage: its adult radius
     * scaled, and for a strain that fits a cell, capped under half of one.
     */
    public float simRadius(UnitType type) {
        if (this == ADULT) return type.radius;
        float r = type.radius * scale(type);
        return type.radius <= SIM_RADIUS_CAP ? Math.min(SIM_RADIUS_CAP, r) : r;
    }

    /** The size a {@code type} body of this stage is drawn and shoved at: scaled, never capped. */
    public float drawnRadius(UnitType type) {
        return this == ADULT ? type.radius : type.radius * scale(type);
    }

    /** The next stage up, or this one at elder. */
    public SwarmStage older() {
        return this == WHELP ? ADULT : ELDER;
    }
}
