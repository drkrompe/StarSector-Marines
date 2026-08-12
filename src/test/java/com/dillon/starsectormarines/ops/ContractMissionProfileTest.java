package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.ContractType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContractMissionProfileTest {

    @Test
    void mapsSupportedOneShotTypes() {
        ContractMissionProfile strike = ContractMissionProfile.from(ContractType.STRIKE);
        ContractMissionProfile escort = ContractMissionProfile.from(ContractType.ESCORT);
        ContractMissionProfile recovery = ContractMissionProfile.from(ContractType.EXTRACTION);

        assertEquals(MissionType.RAID, strike.missionType);
        assertEquals("Strike", strike.title);
        assertEquals(MissionType.EXTRACTION, escort.missionType);
        assertEquals("Escort", escort.title);
        assertEquals(MissionType.EXTRACTION, recovery.missionType);
        assertEquals("Recovery", recovery.title);
    }

    @Test
    void rejectsContractsWithoutOneShotMissionFlow() {
        assertNull(ContractMissionProfile.from(ContractType.GARRISON));
        assertNull(ContractMissionProfile.from(ContractType.CADRE));
        assertNull(ContractMissionProfile.from(ContractType.PLANETARY_ASSAULT));
        assertNull(ContractMissionProfile.from(null));
    }
}
