package com.dillon.starsectormarines.battle.weapons;

import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-enum coverage for {@link FireStance}: the accuracy multipliers + the
 * {@code stanceFor} strict-rule heuristic. Integration with
 * {@link InfantryWeapons#fireShot} is covered indirectly by callsites that
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
    public void stanceForReadsMoveProgress() {
        Unit stationary = new Unit("s", Faction.MARINE, UnitType.MARINE, 0, 0);
        stationary.moveProgress = 0f;
        assertEquals(FireStance.STANCED, FireStance.stanceFor(stationary),
                "moveProgress == 0 → STANCED");

        Unit walking = new Unit("w", Faction.MARINE, UnitType.MARINE, 0, 0);
        walking.moveProgress = 0.5f;
        assertEquals(FireStance.MOVING, FireStance.stanceFor(walking),
                "moveProgress > 0 → MOVING (strict rule: any lerp = moving)");

        Unit almostThere = new Unit("a", Faction.MARINE, UnitType.MARINE, 0, 0);
        almostThere.moveProgress = 0.95f;
        assertEquals(FireStance.MOVING, FireStance.stanceFor(almostThere),
                "even a near-arrived unit is still MOVING — visually still lerping");
    }
}
