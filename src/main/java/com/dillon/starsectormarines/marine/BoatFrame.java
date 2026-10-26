package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;

/**
 * What a boat's frame can carry, and what her parts weigh against it.
 *
 * <p>The boat's counterpart to {@link MechFrameBudget}. Every part above the
 * yard standard has a weight, and the frame has a rating: a Hermes fitted to
 * lift a Bulwark, with the drive that keeps her fast under it, has that much
 * less for armour, shield, flux and guns. Yard-standard parts weigh nothing,
 * and so does a hardpoint's own yard gun.
 */
public final class BoatFrame {

    /** The Hermes frame's rating, which every pattern shares until another is authored. */
    public static final int RATING = 10;

    private BoatFrame() { }

    /** How much weight a {@code pattern} frame carries. */
    public static int rating(ShuttleType pattern) {
        return RATING;
    }

    /** What a boat's fitted parts weigh. */
    public static int weight(CampaignBoat boat) {
        int total = 0;
        for (BoatFittingSlot slot : BoatFittingSlot.values()) total += boat.fittingIn(slot).weight();
        for (int index = 0; index < boat.pattern().hardpoints(); index++) total += gunWeight(boat, index, boat.gunOn(index));
        return total;
    }

    /** What the boat would weigh with {@code fitting} in its slot instead of what is there. */
    public static int weightWith(CampaignBoat boat, BoatFitting fitting) {
        return weight(boat) - boat.fittingIn(fitting.slot()).weight() + fitting.weight();
    }

    /** What the boat would weigh with {@code gun} on hardpoint {@code index} instead of what is there. */
    public static int weightWithGun(CampaignBoat boat, int index, BoatGun gun) {
        return weight(boat) - gunWeight(boat, index, boat.gunOn(index)) + gunWeight(boat, index, gun);
    }

    /**
     * Whether a refit to {@code after} is allowed from {@code before}: within the
     * rating, or at least no heavier than she already is, so a boat over the
     * rating can always be lightened.
     */
    public static boolean admits(CampaignBoat boat, int before, int after) {
        return after <= rating(boat.pattern()) || after <= before;
    }

    private static int gunWeight(CampaignBoat boat, int index, BoatGun gun) {
        return gun == null || gun.standardOn(boat.pattern(), index) ? 0 : gun.weight();
    }
}
