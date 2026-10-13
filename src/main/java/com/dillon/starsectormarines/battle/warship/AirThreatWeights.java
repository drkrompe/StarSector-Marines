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
