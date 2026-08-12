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

        assertEquals(MissionType.RAID, strike.missionType);
        assertEquals("Strike", strike.title);
        assertEquals(MissionType.EXTRACTION, escort.missionType);
        assertEquals("Escort", escort.title);
    }

    @Test
    void rejectsContractsWithoutOneShotMissionFlow() {
        assertNull(ContractMissionProfile.from(ContractType.GARRISON));
        assertNull(ContractMissionProfile.from(ContractType.CADRE));
        assertNull(ContractMissionProfile.from(ContractType.PLANETARY_ASSAULT));
        assertNull(ContractMissionProfile.from(ContractType.EXTRACTION));
        assertNull(ContractMissionProfile.from(null));
    }
}
