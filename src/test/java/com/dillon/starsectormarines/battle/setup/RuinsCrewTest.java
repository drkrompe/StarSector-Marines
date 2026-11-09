package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsCrewTest {

    @Test void aCrewSpendsItsBudgetWithoutGoingOver() {
        RuinsCrew crew = RuinsCrew.spend(20f, RiskLevel.LOW, 0.2f, List.of(), 4);
        float spent = BattleForceScore.defenders(crew.bulk(), crew.elites(), crew.heavies());
        assertTrue(spent <= 20f, "spent " + spent);
        assertTrue(20f - spent < BattleForceScore.REGULAR_INFANTRY, "a whole soldier left unbought: " + spent);
        assertEquals(Math.round(crew.infantry() * 0.2f), crew.elites());
    }

    @Test void heaviesComeOutOfTheBudgetFirst() {
        RuinsCrew without = RuinsCrew.spend(40f, RiskLevel.HIGH, 0f, List.of(), 5);
        RuinsCrew with = RuinsCrew.spend(40f, RiskLevel.HIGH, 0f, List.of(MechVariant.HOUND), 5);
        assertEquals(without.infantry() - Math.round(BattleForceScore.mech(MechVariant.HOUND)), with.infantry());
        assertThrows(IllegalArgumentException.class,
                () -> RuinsCrew.spend(10f, RiskLevel.HIGH, 0f, List.of(MechVariant.BULWARK), 5));
    }

    @Test void theDigIsPostedMoreHeavilyThanTheCampAndPatrolsTakeTheRest() {
        RuinsCrew crew = RuinsCrew.spend(30f, RiskLevel.MEDIUM, 0.25f, List.of(), 5);
        assertTrue(crew.digPost() >= crew.campPost());
        assertTrue(crew.campPost() >= RuinsCrew.MIN_POST);
        assertEquals(crew.infantry(), crew.digPost() + crew.campPost() + crew.patrolInfantry());
        assertTrue(crew.patrolInfantry() > 0);
    }

    @Test void aTinyCrewPostsWhatItHas() {
        RuinsCrew crew = RuinsCrew.spend(3f, RiskLevel.LOW, 0f, List.of(), 4);
        assertEquals(3, crew.infantry());
        assertEquals(2, crew.digPost());
        assertEquals(1, crew.campPost());
        assertEquals(0, crew.patrolInfantry());
    }

    @Test void theCampKeepsItsDistanceFromTheLandingButNeverPassesHalfway() {
        assertArrayEquals(new int[]{100 - 1 - 30, 0}, RuinsCrew.camp(straight(100)),
                "a long route: the camp's share of the way");
        assertArrayEquals(new int[]{50 - 1 - RuinsCrew.CAMP_MIN_CELLS_FROM_LANDING, 0}, RuinsCrew.camp(straight(50)),
                "a mid route: the least distance");
        assertArrayEquals(new int[]{20 - 1 - 9, 0}, RuinsCrew.camp(straight(20)), "a short route: halfway");
    }

    /** A straight route of {@code n} cells from the vault at x=0 to the landing at x=n-1. */
    private static int[] straight(int n) {
        int[] route = new int[n * 2];
        for (int i = 0; i < n; i++) route[i * 2] = i;
        return route;
    }

    @Test void alongMeasuresFromTheLandingEndOfTheVaultRoute() {
        int[] route = {0, 0, 1, 0, 2, 0, 3, 0, 4, 0};
        assertArrayEquals(new int[]{4, 0}, RuinsCrew.along(route, 0f));
        assertArrayEquals(new int[]{0, 0}, RuinsCrew.along(route, 1f));
        assertArrayEquals(new int[]{3, 0}, RuinsCrew.along(route, 0.3f));
    }
}
