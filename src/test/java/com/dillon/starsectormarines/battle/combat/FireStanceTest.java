package com.dillon.starsectormarines.battle.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-enum coverage for {@link FireStance}: the accuracy multipliers + the
 * {@code stanceFor} strict-rule heuristic. Integration with
 * {@link com.dillon.starsectormarines.battle.infantry.InfantryWeapons#fireShot} is covered indirectly by callsites that
 * pass the stance explicitly; this test pins the contract of the values.
 */
public class FireStanceTest {

    @Test
    public void stancedIsFullAccuracy() {
        assertEquals(1.0f, FireStance.STANCED.accuracyMult, 1e-6f);
    }

    @Test
    public void movingHalvesAccuracy() {
        assertEquals(0.5f, FireStance.MOVING.accuracyMult, 1e-6f);
        assertTrue(FireStance.MOVING.accuracyMult < FireStance.STANCED.accuracyMult,
                "MOVING must be a penalty relative to STANCED, not a bonus");
    }

    @Test
    public void stanceForReadsMotionState() {
        // stanceFor takes the caller-resolved motion state (!movement.settled(id))
        // — the by-id read lives at the callsite, this enum just maps it.
        assertEquals(FireStance.STANCED, FireStance.stanceFor(false),
                "not moving → STANCED");
        assertEquals(FireStance.MOVING, FireStance.stanceFor(true),
                "moving → MOVING (strict rule: any motion = moving)");
    }
}
