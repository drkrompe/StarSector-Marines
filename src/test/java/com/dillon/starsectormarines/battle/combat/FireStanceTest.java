package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitRegistry;
import com.dillon.starsectormarines.battle.unit.UnitType;
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
    public void stanceForReadsMoveProgress() {
        // moveProgress is a registry-backed SoA column with no local* shadow, so
        // the units must be registered before setMoveProgress routes anywhere.
        UnitRegistry registry = new UnitRegistry();

        Unit stationary = new Unit("s", Faction.MARINE, UnitType.MARINE, 0, 0);
        registry.allocate(stationary);
        stationary.setMoveProgress(0f);
        assertEquals(FireStance.STANCED, FireStance.stanceFor(stationary),
                "moveProgress == 0 → STANCED");

        Unit walking = new Unit("w", Faction.MARINE, UnitType.MARINE, 0, 0);
        registry.allocate(walking);
        walking.setMoveProgress(0.5f);
        assertEquals(FireStance.MOVING, FireStance.stanceFor(walking),
                "moveProgress > 0 → MOVING (strict rule: any lerp = moving)");

        Unit almostThere = new Unit("a", Faction.MARINE, UnitType.MARINE, 0, 0);
        registry.allocate(almostThere);
        almostThere.setMoveProgress(0.95f);
        assertEquals(FireStance.MOVING, FireStance.stanceFor(almostThere),
                "even a near-arrived unit is still MOVING — visually still lerping");
    }
}
