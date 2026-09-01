package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

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
            recipe(ShuttleType.HERMES, "SHIP'S GIG", 30, 8, 20),
            recipe(ShuttleType.KITE, "COMMON MARKET", 34, 9, 22),
            recipe(ShuttleType.MUDSKIPPER, "COMMON MARKET", 38, 10, 25),
            recipe(ShuttleType.AEROSHUTTLE, "PURPOSE-BUILT LANDER", 60, 15, 40),
            recipe(ShuttleType.SHEPHERD, "UTILITY HULL", 64, 16, 42),
            recipe(ShuttleType.WAYFARER, "UTILITY HULL", 66, 17, 44),
            recipe(ShuttleType.TARSUS, "CIVILIAN FREIGHTER", 82, 21, 55),
            recipe(ShuttleType.BUFFALO, "CIVILIAN FREIGHTER", 88, 22, 58),
            recipe(ShuttleType.MULE, "CIVILIAN FREIGHTER", 96, 24, 64),
            recipe(ShuttleType.NEBULA, "CIVILIAN FREIGHTER", 110, 28, 72),
            recipe(ShuttleType.VALKYRIE, "MILITARY TRANSPORT", 150, 38, 100));

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

    /** One buildable pattern, its material bill, and where the yard's plans came from. */
    public record Recipe(ShuttleType pattern, FabricationCost bill, String provenance) {
        public Recipe {
            if (pattern == null || bill == null) {
                throw new IllegalArgumentException("a boat recipe needs a pattern and a bill");
            }
        }
    }
}
