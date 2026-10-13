package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.warship.ShipClass.Role;

/**
 * How much each kind of air threat counts for a {@link ShipClass}: a small
 * table of multipliers on the base weights of {@link AirThreatPicture}, not a
 * number per hull. A combat capital is the identity row (the weights were tuned
 * against it), so the table only says how the other classes differ from it.
 *
 * <ul>
 *   <li>A smaller combat hull fears armed fighters more: a frigate counts each
 *       twice over, a destroyer 1.5 times, a cruiser 1.25 times. A capital
 *       fears another capital as much as any hull does and fighters least.</li>
 *   <li>A carrier's weight on hostile fighters is high (twice), whatever its
 *       size: its wing is the reason it is there. A support ship counts them
 *       1.5 times.</li>
 *   <li>The hull's own vanilla shield figures scale the armed-craft weight again ({@link #shieldFactor}):
 *       no shield is the identity, and arc and efficiency take it down to 0.6 at most. Armed craft can
 *       hurt a hull, and a shield is what stands between them and it.</li>
 *   <li>Hostile ships and declared threats count the same for every class.
 *       There is no ground-clutter term in the score, so a carrier's near-zero
 *       weight on ground is already true of every class.</li>
 * </ul>
 *
 * A scaling of how much a threat matters to the posture choice; it multiplies
 * no damage.
 */
public final class AirThreatWeights {

    /** Multipliers on {@link AirThreatPicture}'s base weights, per threat kind. */
    public record Row(float declared, float hostileShip, float armedCraft) {}

    private static final Row IDENTITY = new Row(1f, 1f, 1f);

    private AirThreatWeights() {}

    /** The row for a class. */
    public static Row of(ShipClass shipClass) {
        Row base = bySizeAndRole(shipClass);
        float shield = shieldFactor(shipClass);
        return shield == 1f ? base : new Row(base.declared(), base.hostileShip(), base.armedCraft() * shield);
    }

    private static Row bySizeAndRole(ShipClass shipClass) {
        if (shipClass.role() == Role.CARRIER) return new Row(1f, 1f, 2f);
        if (shipClass.role() == Role.SUPPORT) return new Row(1f, 1f, 1.5f);
        return switch (shipClass.size()) {
            case FRIGATE -> new Row(1f, 1f, 2f);
            case DESTROYER -> new Row(1f, 1f, 1.5f);
            case CRUISER -> new Row(1f, 1f, 1.25f);
            case CAPITAL -> IDENTITY;
        };
    }

    /**
     * How much a hull's own shield scales the weight of armed craft, derived from its vanilla shield figures
     * rather than a table: {@code 1 - }{@link #MAX_RELIEF}{@code  * coverage * absorption}.
     *
     * <ul>
     *   <li>coverage is {@code shield arc / 360}: a front shield covers the arc the table gives it (a
     *       Onslaught's 180 is half), an omni shield its own arc (a Paragon's 360 is all, a Conquest's 90 a
     *       quarter).</li>
     *   <li>absorption is how well the shield soaks what it covers, from {@code shield efficiency} (flux per
     *       damage absorbed, lower is better): {@code min(1.25, 1 / efficiency) / 1.25}. Efficiency 1 is the
     *       base game's ordinary shield and earns 0.8; 0.8 or better is full marks; 1.4 earns about 0.57.</li>
     *   <li>a phase hull has no shield figures. Its cloak is all-round and absorbs everything, but it cannot
     *       be held while the hull fires, so it counts for {@link #PHASE_UPTIME} of a full screen.</li>
     *   <li>no shield is exactly 1, the identity the base weights were tuned on.</li>
     * </ul>
     * The weight is a threat-perception input only; it never changes damage.
     */
    static float shieldFactor(ShipClass shipClass) {
        float screen = switch (shipClass.shield()) {
            case NONE -> 0f;
            case PHASE -> PHASE_UPTIME;
            case FRONT, OMNI -> coverage(shipClass.shieldArc()) * absorption(shipClass.shieldEfficiency());
        };
        return 1f - MAX_RELIEF * screen;
    }

    /** The share of the full sphere an arc covers, in {@code [0, 1]}. */
    static float coverage(float arcDegrees) {
        return Math.max(0f, Math.min(1f, arcDegrees / 360f));
    }

    /** How well a shield soaks what it covers, in {@code (0, 1]}; efficiency is flux per damage absorbed. */
    static float absorption(float efficiency) {
        float e = Math.max(0.01f, efficiency);
        return Math.min(FULL_MARKS_ABSORPTION, 1f / e) / FULL_MARKS_ABSORPTION;
    }

    /** The most a perfect all-round, perfectly efficient screen takes off the armed-craft weight (a Paragon takes this). */
    static final float MAX_RELIEF = 0.4f;
    /** Vanilla's ordinary shield absorbs at efficiency 1; this is the 1/efficiency that earns full marks (efficiency 0.8). */
    static final float FULL_MARKS_ABSORPTION = 1.25f;
    /** Share of a full screen a phase cloak counts for: all-round and total, but exclusive with firing. */
    static final float PHASE_UPTIME = 0.5f;

    /**
     * The weighted threat score: declared strength, plus hostile ships, plus
     * armed craft up to {@link AirThreatPicture#ARMED_CRAFT_CAP} (counted before
     * the class multiplier, so a frigate's ceiling on a swarm is higher too).
     *
     * @param shipWeight the hostile ships' summed base weight
     */
    public static float score(ShipClass shipClass, float declared, float shipWeight, int armedCraft) {
        Row row = of(shipClass);
        float craft = Math.min(AirThreatPicture.ARMED_CRAFT_CAP, armedCraft * AirThreatPicture.ARMED_CRAFT_WEIGHT);
        return declared * row.declared() + shipWeight * row.hostileShip() + craft * row.armedCraft();
    }
}
