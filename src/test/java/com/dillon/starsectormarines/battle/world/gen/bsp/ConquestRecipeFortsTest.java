package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.GenRecipe;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A mission that declares forts gets the stage that stands them from every ground recipe. The axis-only Conquest recipe
 * (an axis and no precinct plan) used to carry no fort stage, so a declared fort there was silently dropped. Whether a
 * dense city map has ground to stand one on is the stage's own question (it logs a shortfall); that the stage runs at all
 * is this one.
 */
class ConquestRecipeFortsTest {

    private static boolean carriesFortStage(GenRecipe recipe) {
        for (GenStage stage : recipe.stages()) if (stage instanceof FortStage) return true;
        return false;
    }

    @Test
    void theAxisOnlyConquestRecipeCarriesTheFortStage() {
        assertTrue(carriesFortStage(new BspCityGenerator().recipeFor(
                TraversalAxis.WEST_TO_EAST, TargetProfile.NEUTRAL, null)));
    }
}
