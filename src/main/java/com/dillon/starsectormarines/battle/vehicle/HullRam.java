package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.evacuation.SwarmStageStats;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * A moving hull running into something on the other side ({@code convoy-nouns.md}, ramming): the two exchange
 * one impulse as point masses, and each is hurt by the velocity change it could not shed. Pure.
 *
 * <p>The impulse is {@code (1 + e) * closing * m1 * m2 / (m1 + m2)}, so the lighter of the two takes most of
 * the velocity change: a gun tank through a rifleman barely notices him, while a pickup into a Brute is
 * brought up short and the Brute is only rocked. A body is hurt by
 * {@code DAMAGE * sqrt(mass) * (dv - SAFE_DV)}: nothing below {@link #SAFE_DV}, and a heavier body, which has
 * more of itself to absorb the blow, takes more of it in points but less of its own health. The square root is
 * a choice, not physics: health in this game grows far slower than mass (a marine is a tenth of a tonne and
 * 25 health, a Brute three tonnes and 60), so a damage linear in mass would let a crawling truck kill a Brute.
 *
 * <p>Only the striker's own motion closes the gap ({@link #closing}): a body running into a parked hull is not
 * hurt by it, and a Brute's charge into a hull is the charge's exchange ({@code BruteChargeSystem}), not this.
 * Between two machines, hulls and mechs alike, both drives count ({@link #machineClosing}). Mechs are weighed in
 * the same tonnes as hulls, so a mech against a tank resolves by weight like any other pair.
 */
public final class HullRam {

    /** How much a ram bounces: low, a body is mostly carried off the bow rather than flung. */
    public static final float RESTITUTION = .1f;
    /** Cells a second of velocity change a body takes without harm: a hull nudging at a crawl hurts nobody. */
    public static final float SAFE_DV = 1f;
    /** Damage per square-root tonne per cell a second past {@link #SAFE_DV}. */
    public static final float DAMAGE = 27f;
    /** A hull's bulk is blunt: plate turns some of it, as it turns a heavy club. */
    public static final float PENETRATION = 8f;
    /** A body thrown this hard goes down; a hull slowed this hard stalls for a moment. */
    public static final float KNOCKDOWN_DV = 1.5f;
    public static final float STALL_DV = 1f;
    public static final float KNOCKDOWN_SECONDS = .6f;
    public static final float STALL_SECONDS = .5f;

    /** A rifleman with his kit, tonnes. */
    static final float INFANTRY_TONNES = .1f;
    /** A mech's tonnes per unit of its variant's relative mass (the figure a Brute's charge uses). */
    static final float MECH_TONNES = 10f;
    /** A Runner at each stage: a leaner, lighter thing than a man until it is an elder. */
    private static final float[] RUNNER_TONNES = {.05f, .08f, .14f};
    /** An adult Matriarch, tonnes, scaled with her stage's durability: a tank's weight, the heaviest thing on the field. */
    static final float MATRIARCH_TONNES = 40f;
    private static final float[] MATRIARCH_SCALE = {.7f, 1f, 1.45f};

    private HullRam() {}

    /** What one ram does: the impulse, each side's velocity change, and what each is hurt. */
    public record Exchange(float impulse, float hullDv, float bodyDv, float hullDamage, float bodyDamage) {
        public static final Exchange NONE = new Exchange(0f, 0f, 0f, 0f, 0f);
    }

    /** The exchange between a hull of {@code hullMass} and a body of {@code bodyMass} closing at {@code closing}. */
    public static Exchange exchange(float closing, float hullMass, float bodyMass) {
        if (!(closing > 0f) || !(hullMass > 0f) || !(bodyMass > 0f)) return Exchange.NONE;
        float j = impulse(closing, hullMass, bodyMass);
        return new Exchange(j, j / hullMass, j / bodyMass, damage(j, hullMass), damage(j, bodyMass));
    }

    /** The impulse two point masses exchange closing at {@code closing}, bouncing by {@link #RESTITUTION}. */
    public static float impulse(float closing, float m1, float m2) {
        return (1f + RESTITUTION) * closing * m1 * m2 / (m1 + m2);
    }

    /** What a body of {@code mass} takes from {@code impulse}: nothing until its velocity change passes {@link #SAFE_DV}. */
    public static float damage(float impulse, float mass) {
        return DAMAGE * (float) Math.sqrt(mass) * Math.max(0f, impulse / mass - SAFE_DV);
    }

    /**
     * The closing speed a ram is struck at: the hull's speed toward the body, less what the body is moving away
     * at, and never more than the hull's own. A body running at the hull adds nothing; a hull standing still
     * rams nothing.
     */
    public static float closing(float hullTowardBody, float bodyAwayFromHull) {
        if (!(hullTowardBody > 0f)) return 0f;
        return Math.max(0f, Math.min(hullTowardBody, hullTowardBody - bodyAwayFromHull));
    }

    /**
     * The closing speed between two machines, a hull or a mech each: both drive into the blow, so the striker's
     * speed toward the other and the other's toward it both count. Never negative.
     */
    public static float machineClosing(float towardOther, float otherAway) {
        return Math.max(0f, towardOther - otherAway);
    }

    /**
     * The share of an overlap the body takes when a hull pushes it, the rest coming back onto the hull: a man
     * goes almost all the way, a mech meets an APC halfway, and a Matriarch barely moves for a pickup.
     */
    public static float shoveShare(float hullMass, float bodyMass) {
        return hullMass / (hullMass + bodyMass);
    }

    /** A body's mass in tonnes, against {@link VehicleType#massTonnes}. {@code mechRelativeMass} is read only for a mech. */
    public static float bodyTonnes(UnitType type, SwarmStage stage, float mechRelativeMass) {
        if (type.isMech()) return MECH_TONNES * mechRelativeMass;
        int s = SwarmStage.of(stage).ordinal();
        return switch (type) {
            case SWARM_RUNNER -> RUNNER_TONNES[s];
            case SWARM_BRUTE -> SwarmStageStats.bruteMass(stage);
            case SWARM_MATRIARCH -> MATRIARCH_TONNES * MATRIARCH_SCALE[s];
            default -> INFANTRY_TONNES;
        };
    }
}
