package com.dillon.starsectormarines.battle.warship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The damage and area a swarm is allowed, however many bodies it is. */
class SwarmBudgetTest {

    @Test
    void anyNumberOfSharesSumsToTheWeaponsDamageAndArea() {
        for (int n : new int[]{1, 2, 7, 30, 160}) {
            float damage = SwarmBudget.shareDamage(1500f, n);
            float radius = SwarmBudget.shareRadius(8f, n);
            assertEquals(1500f, damage * n, 0.01f, "damage for " + n);
            assertEquals(8f * 8f, radius * radius * n, 0.01f, "area for " + n);
        }
    }

    @Test
    void fusingKeepsTheSumsAndNeverGrowsTheCount() {
        float[] damage = new float[100];
        float[] radius = new float[100];
        float totalDamage = 0f;
        float totalArea = 0f;
        for (int i = 0; i < 100; i++) {
            damage[i] = 100f + i;
            radius[i] = 1f + 0.05f * i;
            totalDamage += damage[i];
            totalArea += radius[i] * radius[i];
        }
        for (int cap : new int[]{1, 7, 32, 99, 100, 250}) {
            float[][] fused = SwarmBudget.fuse(damage, radius, cap);
            assertEquals(Math.min(cap, 100), fused[0].length);
            float d = 0f;
            float a = 0f;
            for (int i = 0; i < fused[0].length; i++) {
                d += fused[0][i];
                a += fused[1][i] * fused[1][i];
            }
            assertEquals(totalDamage, d, totalDamage * 1e-5f, "damage at cap " + cap);
            assertEquals(totalArea, a, totalArea * 1e-5f, "area at cap " + cap);
        }
    }

    @Test
    void twoMergedRoundsHaveTheCombinedArea() {
        assertEquals(5f, SwarmBudget.mergeRadius(3f, 4f), 1e-5f);
    }

    @Test
    void theCapsBoundWhatIsPutInTheAirButAlwaysLeaveOne() {
        assertEquals(7, SwarmBudget.allowed(7, 0, 0));
        assertEquals(SwarmBudget.PER_VOLLEY, SwarmBudget.allowed(500, 0, 0));
        assertEquals(10, SwarmBudget.allowed(30, SwarmBudget.PER_SHIP - 10, 0));
        assertEquals(4, SwarmBudget.allowed(30, 0, SwarmBudget.PER_BATTLE - 4));
        assertEquals(1, SwarmBudget.allowed(30, SwarmBudget.PER_SHIP, SwarmBudget.PER_BATTLE));
        assertTrue(SwarmBudget.PER_VOLLEY < SwarmBudget.PER_SHIP && SwarmBudget.PER_SHIP < SwarmBudget.PER_BATTLE);
    }
}
