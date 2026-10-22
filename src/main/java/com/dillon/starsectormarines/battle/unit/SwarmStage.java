package com.dillon.starsectormarines.battle.unit;

/**
 * How old a swarm body is (`colony-breach-nouns.md`, Growth stage): whelp,
 * adult or elder. A stage is not a strain: a whelp Runner is a Runner, with the
 * Runner's {@link UnitType}, behaviour and rig. It is a property of one body,
 * issued at spawn and fixed for the battle, which is why it lives on the body
 * rather than in the append-only {@link UnitType}.
 *
 * <p>What the stage itself owns is the part every strain shares: how big the
 * body is drawn against the adult, and how much of the director's population
 * target it counts for. A strain's stats by stage are the strain's own table.
 */
public enum SwarmStage {
    WHELP(0.80f, 0.6f),
    ADULT(1.00f, 1.0f),
    ELDER(1.22f, 1.8f);

    /**
     * The largest sim radius any stage of a small strain may have: under half a
     * cell, so one walkable-cell flow field serves every body and an elder
     * squeezes through a door it visibly fills. Only the elder Brute (0.55
     * natural) reaches it; it is drawn and shoved at full size.
     */
    public static final float SIM_RADIUS_CAP = 0.49f;

    /** Drawn size against the adult, linear. */
    public final float scale;
    /** What one body counts for against the director's population target. */
    public final float populationWeight;

    SwarmStage(float scale, float populationWeight) {
        this.scale = scale;
        this.populationWeight = populationWeight;
    }

    /** The stage a body without one is: an adult. */
    public static SwarmStage of(SwarmStage stage) {
        return stage != null ? stage : ADULT;
    }

    /** The sim radius of a body of this stage whose adult is {@code adultRadius}: scaled, capped. */
    public float simRadius(float adultRadius) {
        float r = adultRadius * scale;
        return this == ADULT ? adultRadius : Math.min(SIM_RADIUS_CAP, r);
    }

    /** The size the body is drawn and shoved at: scaled, never capped. */
    public float drawnRadius(float adultRadius) {
        return adultRadius * scale;
    }

    /** The next stage up, or this one at elder. */
    public SwarmStage older() {
        return this == WHELP ? ADULT : ELDER;
    }
}
