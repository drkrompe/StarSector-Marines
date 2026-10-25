package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.fire.FireFuel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where an AI incendiary is aimed: at fuel that can still catch, never at ground already burnt. */
class IncendiaryAimTest {

    private static final int W = 30, H = 20;

    private static FireField field(int[] fuel) {
        return new FireField(W, H, 7L, idx -> fuel[idx]);
    }

    private static int[] grass() {
        int[] fuel = new int[W * H];
        Arrays.fill(fuel, FireFuel.GRASS.bit);
        return fuel;
    }

    @Test
    void aTargetStandingInFuelIsAimedAtWhereItStands() {
        assertArrayEquals(new int[]{10, 10}, FragGrenadeTactics.fuelCellNear(field(grass()), 10, 10, 1.3f));
    }

    @Test
    void aTargetOnBareGroundIsAimedAtTheNearestFuelInTheBlast() {
        int[] fuel = new int[W * H];
        fuel[10 * W + 11] = FireFuel.GRASS.bit;
        assertArrayEquals(new int[]{11, 10}, FragGrenadeTactics.fuelCellNear(field(fuel), 10, 10, 1.3f));
        assertNull(FragGrenadeTactics.fuelCellNear(field(new int[W * H]), 10, 10, 1.3f), "no fuel, no fire point");
    }

    @Test
    void groundThatHasBurntIsNeverAFirePoint() {
        FireField f = field(grass());
        f.addHeat(15.5f, 10.5f, 1.3f, 2200);
        for (int t = 0; t < 30 * 120; t++) f.tick(t);
        assertFalse(f.isActive(), "the patch burnt out");
        assertTrue(f.isBurnt(15, 10));
        // A Marine standing in the burnt patch: nothing within the blast can catch, so the throw is blast-only.
        assertNull(FragGrenadeTactics.fuelCellNear(f, 15, 10, 1.3f));
        // Reaching further finds grass the fire left standing, and only that.
        int[] cell = FragGrenadeTactics.fuelCellNear(f, 15, 10, 8f);
        assertNotNull(cell);
        assertTrue(f.canBurn(cell[0], cell[1]), "aimed at unburnt fuel: " + Arrays.toString(cell));
    }

    @Test
    void withNothingLeftToCatchTheBlastMustDoTheWorkAlone() {
        assertTrue(FragGrenadeTactics.worthThrowing(true, 1), "one man in fuel: the fire does the rest");
        assertFalse(FragGrenadeTactics.worthThrowing(false, 1), "one man on burnt ground: hold the charge");
        assertTrue(FragGrenadeTactics.worthThrowing(false, 2), "two in the blast: a blast throw");
        assertFalse(FragGrenadeTactics.worthThrowing(false, 0));
    }
}
