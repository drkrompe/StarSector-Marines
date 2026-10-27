package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.Armament.Delivery;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * What a squad or lance can do to a defended position, derived from the equipment its living members actually carry
 * (`phased-assault.md`, "Capabilities and roles"). A pure function of {@link Armament}s and head-counts: no unit or
 * item is named anywhere in it, so a new weapon is classified by what its data says it does to a wall or a nest.
 *
 * <p>Each capability is a {@link Rating}: a <em>rate</em> (how fast the squad does the job, in the capability's own
 * unit) and a <em>reserve</em> (how much of it the squad can do before it is spent, infinite for an unlimited
 * magazine or a cooldown tool). A squad may rate on several capabilities at once and the ratings are not exclusive:
 * the same rack of rockets is anti-structure fire <em>and</em> a distance breacher. The plan spends a squad on one
 * role at a time ({@link AssaultRoles}); the profile only says what it could be spent on. Ammunition left is counted,
 * so a depleted launcher rates as little as it is worth and an empty one as nothing.
 *
 * <p>Fair: a side knows its own kit. Nothing here is read of a hostile.
 *
 * <p>Every threshold is UNTUNED and was chosen to separate the catalog's current classes, not to hit an outcome.
 */
public final class CapabilityProfile {

    /** Shortest reach that counts as suppressing fire. */
    static final float MIN_FIRE_RANGE = 8f;
    /** Shortest reach that counts as fire against a structure: inside this a gun is in the nest's own reach. */
    static final float MIN_STRUCTURE_RANGE = 10f;
    /** Armour penetration at which a round is taken to hurt an armoured nest (the catalog's nests rate 6 to 14). */
    static final float ANTI_STRUCTURE_PENETRATION = 12f;
    /** Structural damage per round at which a round is taken to hurt a structure whatever its penetration. */
    static final int ANTI_STRUCTURE_WALL_DAMAGE = 20;
    /** Shortest reach of fire from well outside a position (the nests reach 24). */
    static final float STANDOFF_MIN_RANGE = 25f;
    /** Shortest reach at which a wall-damaging weapon is a distance breacher rather than a close one. */
    static final float DISTANCE_BREACH_MIN_RANGE = 10f;

    /** A rate and a reserve. Reserve is {@link Float#POSITIVE_INFINITY} when nothing runs out. */
    public record Rating(float rate, float reserve) {
        public static final Rating NONE = new Rating(0f, 0f);

        public boolean any() {
            return rate > 0f;
        }

        Rating plus(Rating other) {
            return new Rating(rate + other.rate, reserve + other.reserve);
        }
    }

    /** A squad that can do nothing: no kit, nobody alive. */
    public static final CapabilityProfile NONE = new CapabilityProfile(new EnumMap<>(AssaultCapability.class), 0f, 0f, 0);

    private final Map<AssaultCapability, Rating> ratings;
    private final float distanceBreachRange;
    private final float structureFireRange;
    private final float screenSeconds;
    private final float screenRadius;
    private final int able;

    private CapabilityProfile(Map<AssaultCapability, Rating> ratings, float distanceBreachRange,
                              float structureFireRange, int able) {
        this(ratings, distanceBreachRange, structureFireRange, able, 0f, 0f);
    }

    private CapabilityProfile(Map<AssaultCapability, Rating> ratings, float distanceBreachRange,
                              float structureFireRange, int able, float screenSeconds, float screenRadius) {
        this.ratings = ratings;
        this.distanceBreachRange = distanceBreachRange;
        this.structureFireRange = structureFireRange;
        this.able = able;
        this.screenSeconds = screenSeconds;
        this.screenRadius = screenRadius;
    }

    /**
     * The profile of a set of arms.
     *
     * @param arms     everything the squad's living members carry that can hurt a position (nulls are skipped)
     * @param walkers  living members who can enter a building: infantry, not machines
     * @param able     living members of any kind
     */
    public static CapabilityProfile of(Collection<Armament> arms, int walkers, int able) {
        EnumMap<AssaultCapability, float[]> sums = new EnumMap<>(AssaultCapability.class);
        float breachRange = 0f;
        float breachBest = 0f;
        float structureRange = 0f;
        float structureBest = 0f;
        float screenSeconds = 0f;
        float screenRadius = 0f;
        for (Armament arm : arms) {
            if (arm == null || !arm.usable()) continue;
            if (arm.delivery() == Delivery.CALLED) {
                add(sums, AssaultCapability.AIR_DIRECTION, 1f, Float.POSITIVE_INFINITY);
                continue;
            }
            if (arm.delivery() == Delivery.SCREEN) {
                add(sums, AssaultCapability.SMOKE_SCREEN, 1f, arm.triggers());
                screenSeconds = Math.max(screenSeconds, arm.coverSeconds());
                screenRadius = Math.max(screenRadius, arm.cloudRadius());
                continue;
            }
            float perSecond = arm.roundsPerTrigger() / arm.cycleSeconds();
            float triggers = arm.triggers();
            boolean arcs = arm.delivery() == Delivery.INDIRECT || arm.delivery() == Delivery.EMPLACED;
            boolean fires = arm.delivery() == Delivery.DIRECT || arcs;
            // A served tube cannot be aimed at a wall cell, so it never rates as a breacher however much wall it does.
            boolean wallAimable = arm.delivery() != Delivery.EMPLACED;
            if (fires && arm.range() >= MIN_FIRE_RANGE) {
                add(sums, AssaultCapability.SUPPRESSION, perSecond, arm.roundsPerTrigger() * triggers);
            }
            boolean structureRound = arm.penetration() >= ANTI_STRUCTURE_PENETRATION
                    || arm.wallDamage() >= ANTI_STRUCTURE_WALL_DAMAGE;
            float structurePerSecond = arm.damagePerRound() * perSecond;
            float structureReserve = arm.damagePerRound() * arm.roundsPerTrigger() * triggers;
            if (arm.delivery() == Delivery.DIRECT && arm.range() >= MIN_STRUCTURE_RANGE && structureRound) {
                add(sums, AssaultCapability.DIRECT_STRUCTURE_FIRE, structurePerSecond, structureReserve);
                if (structurePerSecond > structureBest) {
                    structureBest = structurePerSecond;
                    structureRange = arm.range();
                }
            }
            if (arcs && arm.range() >= STANDOFF_MIN_RANGE && structureRound) {
                add(sums, AssaultCapability.STANDOFF_STRUCTURE_FIRE, structurePerSecond, structureReserve);
                if (structurePerSecond > structureBest) {
                    structureBest = structurePerSecond;
                    structureRange = arm.range();
                }
            }
            if (arm.wallDamage() > 0 && wallAimable) {
                float wallPerSecond = arm.wallDamage() * perSecond;
                float wallReserve = arm.wallDamage() * arm.roundsPerTrigger() * triggers;
                if (fires && arm.range() >= DISTANCE_BREACH_MIN_RANGE) {
                    add(sums, AssaultCapability.DISTANCE_BREACH, wallPerSecond, wallReserve);
                    if (wallPerSecond > breachBest) {
                        breachBest = wallPerSecond;
                        breachRange = arm.range();
                    }
                } else if (arm.delivery() == Delivery.CHARGE) {
                    add(sums, AssaultCapability.CLOSE_BREACH, wallPerSecond, wallReserve);
                }
            }
        }
        if (walkers > 0) add(sums, AssaultCapability.ASSAULT, walkers, Float.POSITIVE_INFINITY);
        if (able > 0) add(sums, AssaultCapability.HOLD, able, Float.POSITIVE_INFINITY);
        EnumMap<AssaultCapability, Rating> ratings = new EnumMap<>(AssaultCapability.class);
        for (Map.Entry<AssaultCapability, float[]> entry : sums.entrySet()) {
            ratings.put(entry.getKey(), new Rating(entry.getValue()[0], entry.getValue()[1]));
        }
        return new CapabilityProfile(ratings, breachRange, structureRange, able, screenSeconds, screenRadius);
    }

    private static void add(Map<AssaultCapability, float[]> sums, AssaultCapability capability, float rate, float reserve) {
        float[] sum = sums.computeIfAbsent(capability, c -> new float[2]);
        sum[0] += rate;
        sum[1] += reserve;
    }

    /**
     * A profile for a body whose kit is not itemised: what an unspecified machine or foot squad is assumed to bring.
     * Used where only the old machine/demolition flags exist (fixtures, a squad with no readable kit); the live
     * derivation is {@link #of}.
     */
    public static CapabilityProfile assumed(boolean machine, boolean demolition, int alive) {
        EnumMap<AssaultCapability, Rating> ratings = new EnumMap<>(AssaultCapability.class);
        if (alive > 0) {
            ratings.put(AssaultCapability.HOLD, new Rating(alive, Float.POSITIVE_INFINITY));
            if (machine) {
                ratings.put(AssaultCapability.SUPPRESSION, new Rating(alive * 5f, Float.POSITIVE_INFINITY));
                ratings.put(AssaultCapability.DIRECT_STRUCTURE_FIRE, new Rating(alive * 30f, Float.POSITIVE_INFINITY));
            } else {
                ratings.put(AssaultCapability.SUPPRESSION, new Rating(alive, Float.POSITIVE_INFINITY));
                ratings.put(AssaultCapability.ASSAULT, new Rating(alive, Float.POSITIVE_INFINITY));
            }
            if (demolition) ratings.put(AssaultCapability.CLOSE_BREACH, new Rating(20f, Float.POSITIVE_INFINITY));
        }
        return new CapabilityProfile(ratings, 0f, 0f, alive);
    }

    public Rating rating(AssaultCapability capability) {
        return ratings.getOrDefault(capability, Rating.NONE);
    }

    public float rate(AssaultCapability capability) {
        return rating(capability).rate();
    }

    public float reserve(AssaultCapability capability) {
        return rating(capability).reserve();
    }

    public boolean has(AssaultCapability capability) {
        return rating(capability).any();
    }

    /** Living members of any kind. */
    public int able() {
        return able;
    }

    /** The reach of the squad's best distance-breaching weapon, or zero when it has none. */
    public float distanceBreachRange() {
        return distanceBreachRange;
    }

    /** Seconds the longest-lasting cloud the squad can stand holds its full radius, or zero when it carries no smoke. */
    public float screenSeconds() {
        return screenSeconds;
    }

    /** The radius of the largest cloud the squad can stand, or zero when it carries no smoke. */
    public float screenRadius() {
        return screenRadius;
    }

    /** The reach of the squad's best anti-structure weapon, direct or arcing, or zero when it has none. */
    public float structureFireRange() {
        return structureFireRange;
    }

    /**
     * Whether the squad's anti-structure fire is arcing fire from beyond a nest's reach rather than flat fire from inside
     * it: the squad is placed as a base of fire out of the nests' reach, where a flat gun would have to stand in it.
     */
    public boolean standoffFirst() {
        return has(AssaultCapability.STANDOFF_STRUCTURE_FIRE)
                && rate(AssaultCapability.STANDOFF_STRUCTURE_FIRE) >= rate(AssaultCapability.DIRECT_STRUCTURE_FIRE);
    }

    /** Whether the squad carries anything that can open a wall, at range or at the foot of it. */
    public boolean canBreach() {
        return has(AssaultCapability.DISTANCE_BREACH) || has(AssaultCapability.CLOSE_BREACH);
    }

    /** Whether the squad can enter and clear a building. */
    public boolean canAssault() {
        return has(AssaultCapability.ASSAULT);
    }

    /** The sum of two profiles: what a company of both can do. Ranges keep the longer reach. */
    public CapabilityProfile plus(CapabilityProfile other) {
        EnumMap<AssaultCapability, Rating> sum = new EnumMap<>(AssaultCapability.class);
        for (AssaultCapability capability : AssaultCapability.values()) {
            Rating a = rating(capability);
            Rating b = other.rating(capability);
            if (a.any() || b.any()) sum.put(capability, a.plus(b));
        }
        return new CapabilityProfile(sum, Math.max(distanceBreachRange, other.distanceBreachRange),
                Math.max(structureFireRange, other.structureFireRange), able + other.able,
                Math.max(screenSeconds, other.screenSeconds),
                Math.max(screenRadius, other.screenRadius));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CapabilityProfile that)) return false;
        return able == that.able && distanceBreachRange == that.distanceBreachRange
                && structureFireRange == that.structureFireRange && screenSeconds == that.screenSeconds && screenRadius == that.screenRadius
                && ratings.equals(that.ratings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ratings, distanceBreachRange, structureFireRange, able, screenSeconds, screenRadius);
    }

    /** One line: each capability held, with its rate. */
    public String describe() {
        StringBuilder out = new StringBuilder();
        for (AssaultCapability capability : AssaultCapability.values()) {
            Rating rating = rating(capability);
            if (!rating.any()) continue;
            if (out.length() > 0) out.append(", ");
            out.append(capability.name().toLowerCase(Locale.ROOT)).append(' ').append(Math.round(rating.rate()));
        }
        return out.length() == 0 ? "nothing" : out.toString();
    }

    @Override
    public String toString() {
        return describe();
    }
}
