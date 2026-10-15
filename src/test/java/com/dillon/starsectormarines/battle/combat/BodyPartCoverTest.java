package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodyPartCoverTest {

    @Test
    void partsAreStackedUpTheBodyInBandsAsTallAsTheirShares() {
        assertEquals(BodyPart.LEGS, BodyPart.atHeight(0f));
        assertEquals(BodyPart.LEGS, BodyPart.atHeight(0.29f));
        assertEquals(BodyPart.TORSO, BodyPart.atHeight(0.31f));
        assertEquals(BodyPart.TORSO, BodyPart.atHeight(0.69f));
        assertEquals(BodyPart.ARMS, BodyPart.atHeight(0.71f));
        assertEquals(BodyPart.ARMS, BodyPart.atHeight(0.89f));
        assertEquals(BodyPart.HEAD, BodyPart.atHeight(0.91f));
        assertEquals(BodyPart.HEAD, BodyPart.atHeight(1f));
    }

    @Test
    void uniformHeightsLandOnEachPartInProportionToItsShare() {
        int n = 100_000;
        int[] counts = new int[BodyPart.values().length];
        for (int i = 0; i < n; i++) counts[BodyPart.atHeight((i + 0.5f) / n).ordinal()]++;
        float total = 0f;
        for (BodyPart part : BodyPart.values()) {
            assertEquals(part.share, counts[part.ordinal()] / (float) n, 1e-3f, part.name());
            total += part.share;
        }
        assertEquals(1f, total, 1e-6f);
    }

    @Test
    void moreCoverHidesAtLeastAsMuchOfEveryPart() {
        for (BodyPart part : BodyPart.values()) {
            for (int level = 1; level <= 3; level++) {
                assertTrue(CoverProfile.edge(level, part) >= CoverProfile.edge(level - 1, part));
                assertTrue(CoverProfile.occupant(level, part) >= CoverProfile.occupant(level - 1, part));
            }
            assertEquals(0f, CoverProfile.edge(0, part));
            assertEquals(0f, CoverProfile.occupant(0, part));
        }
    }

    @Test
    void cutOffLevelsStopAboutWhatTheFlatBlockAndDamageCutStoppedTogether() {
        for (int level = 1; level <= 3; level++) {
            float block = BallisticResolver.BLOCK_CHANCE_BY_LEVEL[level];
            float oldStopped = 1f - (1f - block) * (1f - DamageResolver.COVER_DAMAGE_REDUCTION[level]);
            assertEquals(oldStopped, CoverProfile.expectedEdgeInterception(level), 0.05f, "level " + level);
        }
    }

    @Test
    void onlyPeopleHaveBodyParts() {
        assertTrue(UnitType.MARINE.hasBodyParts());
        assertTrue(UnitType.MILITIA.hasBodyParts());
        assertTrue(UnitType.CIVILIAN.hasBodyParts());
        assertFalse(UnitType.HEAVY_MECH.hasBodyParts());
        assertFalse(UnitType.TURRET.hasBodyParts());
        assertFalse(UnitType.GROUND_VEHICLE.hasBodyParts());
        assertFalse(UnitType.SWARM_RUNNER.hasBodyParts());
    }
}
