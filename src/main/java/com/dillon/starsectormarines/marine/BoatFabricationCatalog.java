package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            recipe(ShuttleType.HERMES, "@openingBoatFabricationCatalogShipSGig", 30, 8, 20),
            recipe(ShuttleType.KITE, "@openingBoatFabricationCatalogCommonMarket", 34, 9, 22),
            recipe(ShuttleType.MUDSKIPPER, "@openingBoatFabricationCatalogCommonMarket", 38, 10, 25),
            recipe(ShuttleType.AEROSHUTTLE, "@openingBoatFabricationCatalogPurposeBuiltLander", 60, 15, 40),
            recipe(ShuttleType.SHEPHERD, "@openingBoatFabricationCatalogUtilityHull", 64, 16, 42),
            recipe(ShuttleType.WAYFARER, "@openingBoatFabricationCatalogUtilityHull", 66, 17, 44),
            recipe(ShuttleType.TARSUS, "@openingBoatFabricationCatalogCivilianFreighter", 82, 21, 55),
            recipe(ShuttleType.BUFFALO, "@openingBoatFabricationCatalogCivilianFreighter", 88, 22, 58),
            recipe(ShuttleType.MULE, "@openingBoatFabricationCatalogCivilianFreighter", 96, 24, 64),
            recipe(ShuttleType.NEBULA, "@openingBoatFabricationCatalogCivilianFreighter", 110, 28, 72),
            recipe(ShuttleType.VALKYRIE, "@openingBoatFabricationCatalogMilitaryTransport", 150, 38, 100));

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

    private static Recipe recipe(ShuttleType pattern, String provenance,
                                 int metals, int machinery, int supplies) {
        return new Recipe(pattern, new FabricationCost(List.of(
                new FabricationCost.Line(Commodities.METALS, metals),
                new FabricationCost.Line(Commodities.HEAVY_MACHINERY, machinery),
                new FabricationCost.Line(Commodities.SUPPLIES, supplies))),
                provenance);
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
     * less, material by material, than those parts together; were it less, building a boat and
     * breaking up her parts would make materials out of nothing.
     */
    public record Recipe(ShuttleType pattern, FabricationCost hull, String provenance) {
        public String provenance() { return Strings.resolve(provenance); }

        public Recipe {
            if (pattern == null || hull == null) {
                throw new IllegalArgumentException("a boat recipe needs a pattern and a bill");
            }
        }

        /** What building one costs: the hull bill, raised on any material her stock parts need more of. */
        public FabricationCost bill() { return covering(hull, stockParts(pattern)); }
    }

    /** {@code bill}, raised line by line to at least what {@code parts} cost together. */
    public static FabricationCost covering(FabricationCost bill, List<FabricationCost> parts) {
        Map<String, Integer> needed = new LinkedHashMap<>();
        for (FabricationCost part : parts) {
            for (FabricationCost.Line line : part.lines()) needed.merge(line.commodityId(), line.quantity(), Integer::sum);
        }
        Map<String, Integer> lines = new LinkedHashMap<>();
        for (FabricationCost.Line line : bill.lines()) lines.merge(line.commodityId(), line.quantity(), Integer::sum);
        needed.forEach((id, quantity) -> lines.merge(id, quantity, Math::max));
        List<FabricationCost.Line> covered = new ArrayList<>();
        lines.forEach((id, quantity) -> covered.add(new FabricationCost.Line(id, quantity)));
        return new FabricationCost(covered);
    }
}
