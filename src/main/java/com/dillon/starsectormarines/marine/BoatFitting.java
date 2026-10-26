package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.air.LiftRating;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import java.util.ArrayList;
import java.util.List;

/**
 * One installed subsystem in a ship's boat, and what a yard charges to put it
 * there.
 *
 * <p>A code catalog, the shape {@link
 * com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent} uses:
 * a handful of authored entries with stable ids, resolved by id everywhere a
 * boat is persisted. Moving it to data is an extension rather than a
 * correction — there is nothing here a JSON file would say better while the
 * list is nine long.
 *
 * <p><b>A fitting is installed work, not a spare.</b> Plating is welded on and
 * a drive is built in, so unlike a mech's components there is no finite stock,
 * nothing returns to stores, and the outgoing fitting is scrapped. That is the
 * one place the boat model departs from the mech one, and it is a fact about
 * what a fitting is rather than a shortcut past inventory.
 *
 * @param liftRating the machines a LIFT fitting lets the boat carry;
 *     {@link LiftRating#NONE} for every other fitting and for the standard hold
 * @param bill what the yard consumes out of the fleet's hold, or {@code null}
 *     for the standard fitting included in the boat's fabrication bill
 * @param provenance where the thing comes from, for the player reading the
 *     catalog row
 */
public record BoatFitting(
        String id,
        String displayName,
        BoatFittingSlot slot,
        int tier,
        float hullFactor,
        float speedFactor,
        float accelFactor,
        LiftRating liftRating,
        FabricationCost bill,
        String provenance) {

    public static final BoatFitting STANDARD_PLATING = new BoatFitting(
            "standard_plating", "@openingBoatFittingYardStandardPlating", BoatFittingSlot.PLATING, 1,
            1.00f, 1f, 1f, LiftRating.NONE, null,
            "@openingBoatFittingWhatABoatLeavesTheYardWith");

    public static final BoatFitting REINFORCED_PLATING = new BoatFitting(
            "reinforced_plating", "@openingBoatFittingReinforcedBellyPlate", BoatFittingSlot.PLATING, 2,
            1.35f, 1f, 1f, LiftRating.NONE, cost(10, 0, 20, 0),
            "@openingBoatFittingASecondSkinLaidOverTheBelly");

    public static final BoatFitting ARMOURED_PLATING = new BoatFitting(
            "armoured_plating", "@openingBoatFittingArmouredLanderShell", BoatFittingSlot.PLATING, 3,
            1.75f, 1f, 1f, LiftRating.NONE, cost(20, 5, 40, 0),
            "@openingBoatFittingAssaultLanderPlateLaminateOverTheCrew");

    public static final BoatFitting STANDARD_DRIVE = new BoatFitting(
            "standard_drive", "@openingBoatFittingYardStandardDrive", BoatFittingSlot.DRIVE, 1,
            1f, 1.00f, 1.00f, LiftRating.NONE, null,
            "@openingBoatFittingThePlantTheHullWasRatedFor");

    public static final BoatFitting TUNED_DRIVE = new BoatFitting(
            "tuned_drive", "@openingBoatFittingTunedDrive", BoatFittingSlot.DRIVE, 2,
            1f, 1.20f, 1.20f, LiftRating.NONE, cost(15, 5, 0, 0),
            "@openingBoatFittingARefitCrewSWeekCleanFeed");

    public static final BoatFitting UPRATED_DRIVE = new BoatFitting(
            "uprated_drive", "@openingBoatFittingUpratedDrive", BoatFittingSlot.DRIVE, 3,
            1f, 1.45f, 1.45f, LiftRating.NONE, cost(30, 10, 0, 5),
            "@openingBoatFittingABiggerPlantThanTheHullWas");

    public static final BoatFitting STANDARD_LIFT = new BoatFitting(
            "standard_lift", "@boatFittingStandardHold", BoatFittingSlot.LIFT, 1,
            1f, 1f, 1f, LiftRating.NONE, null,
            "@boatFittingStandardHoldProvenance");

    public static final BoatFitting LIFT_CRADLE = new BoatFitting(
            "lift_cradle", "@boatFittingLiftCradle", BoatFittingSlot.LIFT, 2,
            1f, 1f, 1f, LiftRating.LIGHT, cost(15, 10, 30, 0),
            "@boatFittingLiftCradleProvenance");

    public static final BoatFitting HEAVY_CRADLE = new BoatFitting(
            "heavy_cradle", "@boatFittingHeavyCradle", BoatFittingSlot.LIFT, 3,
            1f, 1f, 1f, LiftRating.MEDIUM, cost(30, 20, 60, 5),
            "@boatFittingHeavyCradleProvenance");

    private static final List<BoatFitting> CATALOG = List.of(
            STANDARD_PLATING, REINFORCED_PLATING, ARMOURED_PLATING,
            STANDARD_DRIVE, TUNED_DRIVE, UPRATED_DRIVE,
            STANDARD_LIFT, LIFT_CRADLE, HEAVY_CRADLE);

    public String displayName() { return Strings.resolve(displayName); }
    public String provenance() { return Strings.resolve(provenance); }

    public BoatFitting {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Boat fitting id is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Boat fitting display name is required");
        }
        if (slot == null) throw new IllegalArgumentException("Boat fitting slot is required");
        if (tier < 1 || tier > 3) {
            throw new IllegalArgumentException("Boat fitting tier must be 1..3: " + tier);
        }
        requirePositiveFactor(hullFactor, "hull");
        requirePositiveFactor(speedFactor, "speed");
        requirePositiveFactor(accelFactor, "acceleration");
        if (liftRating == null) throw new IllegalArgumentException("Boat fitting lift rating is required");
        if (liftRating != LiftRating.NONE && slot != BoatFittingSlot.LIFT) {
            throw new IllegalArgumentException("Only a LIFT fitting can carry a lift rating: " + id);
        }
    }

    /** Whether this is the fitting a boat is built with rather than refitted to. */
    public boolean standard() {
        return bill == null;
    }

    /**
     * Whether a boat of {@code pattern} can take this fitting. Hermes is a
     * repairable courier stopgap; its frame has no upgrade path. A cradle goes
     * only into a bus-class hold, and never into one whose pattern already
     * lifts at least as much — a Valkyrie gains nothing from it.
     */
    public boolean supports(ShuttleType pattern) {
        if (standard()) return true;
        if (pattern == ShuttleType.HERMES) return false;
        if (slot != BoatFittingSlot.LIFT) return true;
        return pattern != null && pattern.busClass()
                && liftRating.ordinal() > pattern.liftRating().ordinal();
    }

    /**
     * Whether this is a cradle: a hold fitting that lets a bus-class boat lift one
     * machine on a sortie. The seats stay, so the boat still ferries squads; one
     * sortie carries one kind or the other.
     */
    public boolean cradle() {
        return liftRating != LiftRating.NONE;
    }

    /** Every authored fitting, in slot then tier order. */
    public static List<BoatFitting> catalog() {
        return CATALOG;
    }

    /** Everything that can go in one slot, cheapest first. */
    public static List<BoatFitting> catalog(BoatFittingSlot slot) {
        List<BoatFitting> found = new ArrayList<>();
        for (BoatFitting fitting : CATALOG) {
            if (fitting.slot() == slot) found.add(fitting);
        }
        return List.copyOf(found);
    }

    /** The fitting a boat leaves the yard with in {@code slot}. */
    public static BoatFitting standard(BoatFittingSlot slot) {
        return switch (slot) {
            case DRIVE -> STANDARD_DRIVE;
            case LIFT -> STANDARD_LIFT;
            case PLATING -> STANDARD_PLATING;
        };
    }

    /** Returns null for an unknown persisted fitting id. */
    public static BoatFitting findById(String id) {
        if (id == null) return null;
        for (BoatFitting fitting : CATALOG) {
            if (fitting.id().equals(id)) return fitting;
        }
        return null;
    }

    /** Resolves a persisted id, rejecting unknown fixture vocabulary. */
    public static BoatFitting requireById(String id) {
        BoatFitting fitting = findById(id);
        if (fitting == null) {
            throw new IllegalArgumentException("Unknown boat fitting id '" + id + "'");
        }
        return fitting;
    }

    /**
     * Legacy-safe resolution for a live boat: an id nobody recognises reads as
     * the standard fitting, so a catalog entry that was renamed leaves the
     * company with a plainer boat rather than an unflyable one.
     */
    public static BoatFitting resolve(String id, BoatFittingSlot slot) {
        BoatFitting fitting = findById(id);
        return fitting != null && fitting.slot() == slot ? fitting : standard(slot);
    }

    private static FabricationCost cost(int supplies, int machinery,
                                        int metals, int rareMetals) {
        List<FabricationCost.Line> lines = new ArrayList<>(4);
        if (supplies > 0) lines.add(new FabricationCost.Line(Commodities.SUPPLIES, supplies));
        if (machinery > 0) {
            lines.add(new FabricationCost.Line(Commodities.HEAVY_MACHINERY, machinery));
        }
        if (metals > 0) lines.add(new FabricationCost.Line(Commodities.METALS, metals));
        if (rareMetals > 0) {
            lines.add(new FabricationCost.Line(Commodities.RARE_METALS, rareMetals));
        }
        return new FabricationCost(lines);
    }

    private static void requirePositiveFactor(float factor, String what) {
        if (!(factor > 0f) || !Float.isFinite(factor)) {
            throw new IllegalArgumentException(
                    "Boat fitting " + what + " factor must be finite and positive: " + factor);
        }
    }
}
