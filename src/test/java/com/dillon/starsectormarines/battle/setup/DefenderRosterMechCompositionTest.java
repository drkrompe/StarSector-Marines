package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefenderRosterMechCompositionTest {

    @Test
    void lowRiskNeverSpawnsDefenderMechs() {
        for (MissionType type : MissionType.values()) {
            DefenderRoster roster = DefenderRoster.forMission(type, RiskLevel.LOW, true);
            assertEquals(0, roster.mechCount, type + " LOW mech count");
            assertTrue(roster.mechVariants.isEmpty(), type + " LOW mech profiles");
        }
    }

    @Test
    void heavyArmorGateStillDisablesHighRiskMechs() {
        for (MissionType type : MissionType.values()) {
            DefenderRoster roster = DefenderRoster.forMission(type, RiskLevel.HIGH, false);
            assertTrue(roster.mechVariants.isEmpty(), type + " without heavy armor");
        }
    }

    @Test
    void mediumIntroducesOneBulwark() {
        for (MissionType type : MissionType.values()) {
            DefenderRoster roster = DefenderRoster.forMission(type, RiskLevel.MEDIUM, true);
            assertEquals(List.of(MechVariant.BULWARK), roster.mechVariants, type.name());
            assertEquals(roster.mechVariants.size(), roster.mechCount);
        }
    }

    @Test
    void highRiskUsesMissionScaledComplementaryProfiles() {
        List<MechVariant> lance = List.of(
                MechVariant.BULWARK, MechVariant.HOUND, MechVariant.SIROCCO);
        for (MissionType type : MissionType.values()) {
            DefenderRoster roster = DefenderRoster.forMission(type, RiskLevel.HIGH, true);
            if (type == MissionType.SABOTAGE) {
                assertEquals(List.of(MechVariant.HOUND), roster.mechVariants);
            } else if (type == MissionType.CONQUEST) {
                assertEquals(List.of(
                        MechVariant.BULWARK, MechVariant.HOUND, MechVariant.SIROCCO,
                        MechVariant.BULWARK, MechVariant.HOUND, MechVariant.SIROCCO),
                        roster.mechVariants);
            } else {
                assertEquals(lance, roster.mechVariants, type.name());
            }
            assertEquals(roster.mechVariants.size(), roster.mechCount);
        }
    }

    @Test
    void familyDefaultsMatchBattlefieldJobs() {
        assertEquals(MechRole.ARMORED_SUPPORT, MechVariant.BULWARK.defaultRole);
        assertEquals(MechRole.ASSAULT, MechVariant.HOUND.defaultRole);
        assertEquals(MechRole.LR_SUPPORT, MechVariant.SIROCCO.defaultRole);
    }
}
