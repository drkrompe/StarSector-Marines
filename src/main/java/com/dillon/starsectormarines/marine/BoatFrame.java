package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;

/**
 * What a boat's frame can carry, and what her parts weigh against it.
 *
 * <p>The boat's counterpart to {@link MechFrameBudget}. Every part above the
 * yard standard has a weight, and the frame has a rating: a Hermes fitted to
 * lift a Bulwark, with the drive that keeps her fast under it, has that much
 * less for armour, shield, flux and guns. Yard-standard parts weigh nothing,
 * and so does a hardpoint's own yard gun in its top position. Every gun counts:
 * a pod's two 2x1 guns weigh what both of them do.
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
        for (BoatFittingSlot slot : BoatFittingSlot.values()) total += fittingWeight(boat.fittingIn(slot));
        for (int index = 0; index < boat.pattern().hardpoints(); index++) total += gunsWeight(boat, index, boat.gunsAt(index));
        return total;
    }

    /** What the boat would weigh with {@code fitting} in its slot instead of what is there. */
    public static int weightWith(CampaignBoat boat, BoatFitting fitting) {
        return weight(boat) - fittingWeight(boat.fittingIn(fitting.slot())) + fitting.weight();
    }

    /**
     * What the boat would weigh with {@code gun} put in {@code position} of hardpoint {@code index},
     * and whatever that takes off gone ({@link BoatHardpoint#placed}).
     */
    public static int weightWithGun(CampaignBoat boat, int index, int position, BoatGun gun) {
        return weightWithGuns(boat, index, BoatHardpoint.placed(boat.gunsAt(index), position, gun));
    }

    /** What the boat would weigh with hardpoint {@code index} holding {@code pair}, top then bottom. */
    public static int weightWithGuns(CampaignBoat boat, int index, BoatGun[] pair) {
        return weight(boat) - gunsWeight(boat, index, boat.gunsAt(index)) + gunsWeight(boat, index, pair);
    }

    /**
     * Whether a refit to {@code after} is allowed from {@code before}: within the
     * rating, or at least no heavier than she already is, so a boat over the
     * rating can always be lightened.
     */
    public static boolean admits(CampaignBoat boat, int before, int after) {
        return after <= rating(boat.pattern()) || after <= before;
    }

    private static int fittingWeight(BoatFitting fitting) {
        return fitting == null ? 0 : fitting.weight();
    }

    /** A hardpoint's guns: the yard's own gun is free on top only, so a second one beside it weighs. */
    private static int gunsWeight(CampaignBoat boat, int index, BoatGun[] pair) {
        BoatGun top = pair[BoatHardpoint.TOP], bottom = pair[BoatHardpoint.BOTTOM];
        int total = top == null || top.standardOn(boat.pattern(), index) ? 0 : top.weight();
        return total + (bottom == null ? 0 : bottom.weight());
    }
}
