package com.dillon.starsectormarines.battle.infantry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The medic's energy pool as arithmetic: regeneration, spending, and the start gate. */
public class MedicEnergyTest {

    private static final float MAX = 30f;
    private static final float MIN_START = 7.5f;
    private static final float FLOOR = 0.05f;
    /** A 25-health marine with 5 armor. */
    private static final float WHOLE = 30f;

    @Test
    public void regenerationFillsToTheCapAndNoFurther() {
        assertEquals(0.2f, MedicEnergy.regenerate(0f, MAX, 0.2f, 1f), 1e-6f);
        assertEquals(MAX, MedicEnergy.regenerate(29.9f, MAX, 0.2f, 1f), 1e-6f);
        assertEquals(MAX, MedicEnergy.regenerate(MAX, MAX, 0.2f, 1f), 1e-6f);
    }

    @Test
    public void spendingPaysPerPointAndNeverGoesBelowEmpty() {
        assertEquals(20f, MedicEnergy.spend(30f, 5f, 2f), 1e-6f);
        assertEquals(0f, MedicEnergy.spend(3f, 5f, 1f), 1e-6f);
        assertEquals(4f, MedicEnergy.affordable(30f, 4f, 1f), 1e-6f, "a full want when the pool covers it");
        assertEquals(1.5f, MedicEnergy.affordable(3f, 4f, 2f), 1e-6f, "only what the pool pays for");
        assertEquals(0f, MedicEnergy.affordable(0f, 4f, 1f), 1e-6f);
        assertTrue(MedicEnergy.empty(0f));
        assertFalse(MedicEnergy.empty(0.01f));
    }

    @Test
    public void belowTheGateNothingStartsAndAtItATreatmentDoes() {
        float missing = 20f;
        assertFalse(MedicEnergy.mayStart(MIN_START - 0.5f, missing, WHOLE, 1f, MIN_START, FLOOR));
        assertTrue(MedicEnergy.mayStart(MIN_START, missing, WHOLE, 1f, MIN_START, FLOOR));
        assertTrue(MedicEnergy.mayStart(MAX, missing, WHOLE, 1f, MIN_START, FLOOR));
    }

    @Test
    public void aScratchBelowTheWoundFloorIsIgnoredHoweverFullThePool() {
        assertFalse(MedicEnergy.mayStart(MAX, 1f, WHOLE, 1f, MIN_START, FLOOR));
        assertFalse(MedicEnergy.mayStart(MAX, 0f, WHOLE, 1f, MIN_START, FLOOR));
    }

    @Test
    public void aNearlyWholePatientIsToppedOffWhenThePoolCoversTheWholeWound() {
        float missing = 3f;
        assertEquals(3f, MedicEnergy.startGate(missing, 1f, MIN_START), 1e-6f);
        assertTrue(MedicEnergy.mayStart(3f, missing, WHOLE, 1f, MIN_START, FLOOR),
                "a pool below the minimum still makes a small wound whole");
        assertFalse(MedicEnergy.mayStart(2f, missing, WHOLE, 1f, MIN_START, FLOOR),
                "but not one it cannot cover");
    }
}
