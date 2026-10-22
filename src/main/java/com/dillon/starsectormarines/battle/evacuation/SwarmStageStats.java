package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * A strain's stats by growth stage (`colony-breach-nouns.md`, Growth stage).
 * The adult row is the strain as it shipped before stages; whelp and elder are
 * starting values. Health, plate, damage and reach rise with stage and speed
 * falls. Size is not here: it is {@link SwarmStage#scale} on every strain.
 *
 * <p>Read once, at spawn, by {@link SwarmStrains}: a stage is fixed for the
 * battle, so the body carries its numbers in its ordinary stat columns and no
 * behaviour reads this table per tick, except the Brute's charge, whose speed
 * and mass are not stat columns.
 */
public final class SwarmStageStats {

    /**
     * One body's kit at one stage: health, plate (points and rating, zero for
     * none), its blow's damage, its reach in cells and its walking speed in
     * cells a second.
     */
    public record Stats(float hp, float armor, float armorRating, float damage, float reach, float speed) { }

    private static final Stats[] RUNNER = {
            new Stats(12f, 0f, 0f, 3f, 1.3f, 3.6f),
            new Stats(20f, 0f, 0f, 5f, 1.5f, 3.2f),
            // Light plate: an SMG's spray slides off the pack leader, rifles do not.
            new Stats(34f, 6f, 4f, 8f, 1.8f, 2.9f),
    };
    private static final Stats[] BRUTE = {
            new Stats(26f, 12f, 8f, 9f, 1.5f, 2.0f),
            new Stats(40f, SwarmStrains.BRUTE_ARMOR, SwarmStrains.BRUTE_ARMOR_RATING, 14f, 1.7f, 1.8f),
            new Stats(64f, 28f, 12f, 20f, 2.0f, 1.6f),
    };
    /** The acid's penetration against an airframe does not change with stage: an elder is not armoured air's answer. */
    private static final Stats[] SPITTER = {
            new Stats(8f, 0f, 0f, 5f, 11f, 2.8f),
            new Stats(12f, 0f, 0f, 7f, 14f, 2.6f),
            new Stats(20f, 0f, 0f, 10f, 16f, 2.4f),
    };

    /** A Brute's charge speed by stage, cells a second, and its mass in tonnes (scale cubed against the adult's 3). */
    private static final float[] BRUTE_CHARGE = {4.3f, 4.0f, 3.7f};
    private static final float[] BRUTE_MASS = {1.5f, 3.0f, 5.4f};

    /** The chance a Runner's landed blow impales, and the share of its own pace it drags a load at. */
    private static final float[] IMPALE_CHANCE = {0f, SwarmHarvest.IMPALE_CHANCE, 1f / 12f};
    private static final float[] DRAG_PACE = {0.3f, SwarmHarvest.CARRIER_SPEED, 0.5f};

    private SwarmStageStats() {}

    /** {@code type}'s kit at {@code stage}. */
    public static Stats of(UnitType type, SwarmStage stage) {
        Stats[] table = switch (type) {
            case SWARM_RUNNER -> RUNNER;
            case SWARM_BRUTE -> BRUTE;
            case SWARM_SPITTER -> SPITTER;
            default -> throw new IllegalArgumentException(type + " is not a swarm strain");
        };
        return table[SwarmStage.of(stage).ordinal()];
    }

    /** A Brute's charge speed at {@code stage}, cells a second. */
    public static float bruteChargeSpeed(SwarmStage stage) {
        return BRUTE_CHARGE[SwarmStage.of(stage).ordinal()];
    }

    /** A Brute's mass at {@code stage}, tonnes: what a hull it runs into exchanges momentum with. */
    public static float bruteMass(SwarmStage stage) {
        return BRUTE_MASS[SwarmStage.of(stage).ordinal()];
    }

    /**
     * A Brute's momentum at charge speed against the adult's: about 0.54 for a
     * whelp, 1 for an adult and 1.67 for an elder. The wall ram, the infantry
     * throw and knockdown scale with it.
     */
    public static float bruteMomentumRatio(SwarmStage stage) {
        return bruteMass(stage) * bruteChargeSpeed(stage)
                / (bruteMass(SwarmStage.ADULT) * bruteChargeSpeed(SwarmStage.ADULT));
    }

    /** The chance a Runner of {@code stage} impales with a landed blow: a whelp never does, an elder most often. */
    public static float impaleChance(SwarmStage stage) {
        return IMPALE_CHANCE[SwarmStage.of(stage).ordinal()];
    }

    /** The share of its own pace a Runner of {@code stage} drags a load at. */
    public static float dragPace(SwarmStage stage) {
        return DRAG_PACE[SwarmStage.of(stage).ordinal()];
    }
}
