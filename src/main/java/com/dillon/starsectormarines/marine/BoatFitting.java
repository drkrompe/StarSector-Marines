package com.dillon.starsectormarines.marine;

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
 * list is six long.
 *
 * <p><b>A fitting is installed work, not a spare.</b> Plating is welded on and
 * a drive is built in, so unlike a mech's components there is no finite stock,
 * nothing returns to stores, and the outgoing fitting is scrapped. That is the
 * one place the boat model departs from the mech one, and it is a fact about
 * what a fitting is rather than a shortcut past inventory.
 *
 * @param bill what the yard consumes out of the fleet's hold, or {@code null}
 *     for the standard fitting every boat is built with — a boat is never a
 *     thing the company has to buy before it can leave the ship
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
        FabricationCost bill,
        String provenance) {

    public static final BoatFitting STANDARD_PLATING = new BoatFitting(
            "standard_plating", "Yard-standard plating", BoatFittingSlot.PLATING, 1,
            1.00f, 1f, 1f, null,
            "What a boat leaves the yard with: enough hull to hold pressure and "
                    + "take the knocks a crowded bay hands out. It is not armour "
                    + "and was never sold as any.");

    public static final BoatFitting REINFORCED_PLATING = new BoatFitting(
            "reinforced_plating", "Reinforced belly plate", BoatFittingSlot.PLATING, 2,
            1.35f, 1f, 1f, cost(10, 0, 20, 0),
            "A second skin laid over the belly and the cheeks, which is where a "
                    + "lander is hit on the way down. Every yard in the sector cuts "
                    + "these out of surplus plate; none of them will warrant the seams.");

    public static final BoatFitting ARMOURED_PLATING = new BoatFitting(
            "armoured_plating", "Armoured lander shell", BoatFittingSlot.PLATING, 3,
            1.75f, 1f, 1f, cost(20, 5, 40, 0),
            "Assault-lander plate, laminate over the crew spaces and the tanks. It "
                    + "costs the boat nothing but mass, and mass is the one thing a "
                    + "landing craft has least of — which is why it is bought by "
                    + "companies that expect to be shot at and nobody else.");

    public static final BoatFitting STANDARD_DRIVE = new BoatFitting(
            "standard_drive", "Yard-standard drive", BoatFittingSlot.DRIVE, 1,
            1f, 1.00f, 1.00f, null,
            "The plant the hull was rated for. It lifts a full hold off the deck "
                    + "and puts it down again, at the pace the deck was built around.");

    public static final BoatFitting TUNED_DRIVE = new BoatFitting(
            "tuned_drive", "Tuned drive", BoatFittingSlot.DRIVE, 2,
            1f, 1.20f, 1.20f, cost(15, 5, 0, 0),
            "A refit crew's week: clean feed lines, re-cut nozzles, and the "
                    + "governor moved off the yard's cautious setting. Nothing "
                    + "exotic, and every hour of it shows on the descent.");

    public static final BoatFitting UPRATED_DRIVE = new BoatFitting(
            "uprated_drive", "Uprated drive", BoatFittingSlot.DRIVE, 3,
            1f, 1.45f, 1.45f, cost(30, 10, 0, 5),
            "A bigger plant than the hull was drawn for, on mounts rebuilt to take "
                    + "it. It makes the boat quick enough to be somewhere else by "
                    + "the time the guns have settled, and it is not kind to the "
                    + "airframe.");

    private static final List<BoatFitting> CATALOG = List.of(
            STANDARD_PLATING, REINFORCED_PLATING, ARMOURED_PLATING,
            STANDARD_DRIVE, TUNED_DRIVE, UPRATED_DRIVE);

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
    }

    /** Whether this is the fitting a boat is built with rather than refitted to. */
    public boolean standard() {
        return bill == null;
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
        return slot == BoatFittingSlot.DRIVE ? STANDARD_DRIVE : STANDARD_PLATING;
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
