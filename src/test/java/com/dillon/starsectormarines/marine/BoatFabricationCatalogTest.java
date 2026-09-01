package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the yard charges, and that it charges it for every hull it can be asked about. */
class BoatFabricationCatalogTest {

    /**
     * A berth the workshop cannot price is a room with a dead button on it, and
     * which patterns a hull's bays hold is decided elsewhere and moves.
     */
    @Test
    void everyPatternCanBeBuilt() {
        for (ShuttleType pattern : ShuttleType.values()) {
            assertNotNull(BoatFabricationCatalog.recipe(pattern),
                    pattern + " has no bill, so a bay stocked with it could not be rebuilt");
        }
        Set<ShuttleType> priced = new HashSet<>();
        for (BoatFabricationCatalog.Recipe recipe : BoatFabricationCatalog.recipes()) {
            assertTrue(priced.add(recipe.pattern()),
                    recipe.pattern() + " is priced twice and the two can disagree");
        }
        assertEquals(ShuttleType.values().length, priced.size());
    }

    /** A bigger boat always costs more of everything; a ladder that crosses is a bug. */
    @Test
    void theLadderRisesWithTheHull() {
        int previousTotal = 0;
        for (BoatFabricationCatalog.Recipe recipe : BoatFabricationCatalog.recipes()) {
            int total = 0;
            for (FabricationCost.Line line : recipe.bill().lines()) total += line.quantity();
            assertTrue(total > previousTotal,
                    recipe.pattern() + " costs no more than the hull below it");
            previousTotal = total;
        }
    }
}
