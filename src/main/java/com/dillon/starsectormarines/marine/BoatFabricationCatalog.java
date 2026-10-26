package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.air.ShuttleType;

import java.util.ArrayList;
import java.util.List;

/**
 * What it costs to build one ship's boat into an empty berth.
 *
 * <p>The Boat Deck's counterpart to {@link MechFabricationCatalog}, and a
 * plainer one: a boat has no components to install, so a recipe is a pattern
 * and its bill. Every {@link ShuttleType} carries one, including the hulls no
 * ship's bay stocks today — a debug hull whose berths could not be built into
 * would be a room with a dead button on it.
 *
 * <p>The ladder is ordered by hull, so a bigger boat always costs more of
 * everything. The Aeroshuttle sits well above its size for the reason it exists:
 * it is a purpose-built assault lander with six seats and a gun, not a runabout.
 */
public final class BoatFabricationCatalog {

    private static final List<Recipe> RECIPES = List.of(
            recipe(ShuttleType.HERMES, "@openingBoatFabricationCatalogShipSGig", 4100),
            recipe(ShuttleType.KITE, "@openingBoatFabricationCatalogCommonMarket", 4570),
            recipe(ShuttleType.MUDSKIPPER, "@openingBoatFabricationCatalogCommonMarket", 5140),
            recipe(ShuttleType.AEROSHUTTLE, "@openingBoatFabricationCatalogPurposeBuiltLander", 8050),
            recipe(ShuttleType.SHEPHERD, "@openingBoatFabricationCatalogUtilityHull", 8520),
            recipe(ShuttleType.WAYFARER, "@openingBoatFabricationCatalogUtilityHull", 8930),
            recipe(ShuttleType.TARSUS, "@openingBoatFabricationCatalogCivilianFreighter", 11110),
            recipe(ShuttleType.BUFFALO, "@openingBoatFabricationCatalogCivilianFreighter", 11740),
            recipe(ShuttleType.MULE, "@openingBoatFabricationCatalogCivilianFreighter", 12880),
            recipe(ShuttleType.NEBULA, "@openingBoatFabricationCatalogCivilianFreighter", 14700),
            recipe(ShuttleType.VALKYRIE, "@openingBoatFabricationCatalogMilitaryTransport", 20200));

    private BoatFabricationCatalog() {
    }

    /** Every pattern that can be built, cheapest hull first. */
    public static List<Recipe> recipes() { return RECIPES; }

    /** What one pattern costs, or null for a pattern nothing has authored. */
    public static Recipe recipe(ShuttleType pattern) {
        for (Recipe recipe : RECIPES) {
            if (recipe.pattern() == pattern) return recipe;
        }
        return null;
    }

    private static Recipe recipe(ShuttleType pattern, String provenance, int credits) {
        return new Recipe(pattern, new FabricationCost(credits), provenance);
    }

    /**
     * Every part a new boat of {@code pattern} leaves the yard with, as those parts' own bills:
     * her stock fittings and the yard's guns on her hardpoints.
     */
    public static List<FabricationCost> stockParts(ShuttleType pattern) {
        CampaignBoat boat = BoatDeck.stockBoat("stock", "stock", pattern);
        List<FabricationCost> parts = new ArrayList<>();
        for (BoatFittingSlot slot : BoatFittingSlot.values()) {
            BoatFitting fitting = boat.fittingIn(slot);
            if (fitting != null && fitting.bill() != null) parts.add(fitting.bill());
        }
        for (int hardpoint = 0; hardpoint < pattern.hardpoints(); hardpoint++) {
            BoatGun gun = boat.gunOn(hardpoint);
            if (gun != null && gun.bill() != null) parts.add(gun.bill());
        }
        return parts;
    }

    /**
     * One buildable pattern, its authored hull bill, and where the yard's plans came from.
     *
     * <p>Every stock part a new boat carries is a spare with a bill of its own, which comes off
     * her into stores and recycles like any other. So what she costs ({@link #bill}) is never
     * less than those parts together; were it less, building a boat and recycling her parts
     * would make credits out of nothing.
     */
    public record Recipe(ShuttleType pattern, FabricationCost hull, String provenance) {
        public String provenance() { return Strings.resolve(provenance); }

        public Recipe {
            if (pattern == null || hull == null) {
                throw new IllegalArgumentException("a boat recipe needs a pattern and a bill");
            }
        }

        /** What building one costs: the hull price, raised to what her stock parts cost together. */
        public FabricationCost bill() { return covering(hull, stockParts(pattern)); }
    }

    /** {@code bill}, raised to at least what {@code parts} cost together. */
    public static FabricationCost covering(FabricationCost bill, List<FabricationCost> parts) {
        return new FabricationCost((int) Math.max(bill.credits(), FabricationCost.total(parts)));
    }
}
