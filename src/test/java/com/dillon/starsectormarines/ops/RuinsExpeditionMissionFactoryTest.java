package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.ruins.RuinsExpeditionMissionKey;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.ops.detachment.MissionForceEnvelope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsExpeditionMissionFactoryTest {

    @Test
    void anExpeditionHasNoContractAndNoEmployer() {
        Mission mission = RuinsExpeditionMissionFactory.create(3, "ossuary_market", "Ossuary", RuinsGrade.EXTENSIVE);

        assertEquals(MissionSource.RUINS_EXPEDITION, mission.source);
        assertEquals(MissionType.RUINS, mission.type);
        assertEquals(RuinsGrade.EXTENSIVE, mission.ruinsGrade);
        assertEquals(new RuinsExpeditionMissionKey(3, "ossuary_market"), RuinsExpeditionMissionKey.parse(mission.id));
        assertEquals(-1L, mission.contractId);
        assertEquals(0, mission.employerShuttles);
        assertTrue(MissionForceEnvelope.allowsUnderstrength(mission), "the player's own expedition may go understrength");
    }

    @Test
    void largerRuinsNeverHoldAWeakerOccupant() {
        OperationTier previous = null;
        for (RuinsGrade grade : RuinsGrade.values()) {
            OperationTier tier = RuinsExpeditionMissionFactory.tierFor(grade);
            assertTrue(tier.compareTo(MissionType.RUINS.tierFloor) >= 0, grade + " below the family's floor");
            if (previous != null) assertTrue(tier.compareTo(previous) >= 0, grade + " weaker than the grade below");
            previous = tier;
        }
    }

    @Test
    void nothingToExploreBuildsNothing() {
        assertNull(RuinsExpeditionMissionFactory.create(0, "m", "Ossuary", RuinsGrade.VAST));
        assertNull(RuinsExpeditionMissionFactory.create(1, null, "Ossuary", RuinsGrade.VAST));
        assertNull(RuinsExpeditionMissionFactory.create(1, "m", "Ossuary", null));
    }
}
