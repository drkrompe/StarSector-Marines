package com.dillon.starsectormarines.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanetaryAssaultPhaseTest {

    @Test
    void threePhaseSequenceEndsAtMainAssault() {
        PlanetaryAssaultPhase recon = phase(0, 3);
        PlanetaryAssaultPhase softening = phase(1, 3);
        PlanetaryAssaultPhase assault = phase(2, 3);

        assertEquals(MissionType.SABOTAGE, recon.missionType);
        assertEquals("Recon", recon.title);
        assertEquals(MissionType.RAID, softening.missionType);
        assertEquals(MissionType.CONQUEST, assault.missionType);
        assertTrue(assault.isFinal());
    }

    @Test
    void fivePhaseSequenceAddsMopUpAndConsolidation() {
        assertEquals("Main Assault", phase(2, 5).title);
        assertEquals("Mop-up", phase(3, 5).title);
        PlanetaryAssaultPhase finalPhase = phase(4, 5);
        assertEquals("Consolidation", finalPhase.title);
        assertEquals(MissionType.ASSAULT, finalPhase.missionType);
    }

    @Test
    void stagedPayoutSumsToContractTotal() {
        int total = 0;
        for (int i = 0; i < 5; i++) total += phase(i, 5).payout;

        assertEquals(180_000, total);
        assertEquals(27_000, phase(0, 5).payout);
        assertEquals(72_000, phase(4, 5).payout);
    }

    @Test
    void salvageGrowsByPhaseAndNeverExceedsNegotiation() {
        PlanetaryAssaultPhase recon = PlanetaryAssaultPhase.create(0, 4,
                180_000, 80, 50);
        PlanetaryAssaultPhase finalPhase = PlanetaryAssaultPhase.create(3, 4,
                180_000, 80, 50);

        assertEquals(20, recon.salvageBaseline & 0xFF);
        assertEquals(20, recon.salvageNegotiated & 0xFF);
        assertEquals(80, finalPhase.salvageBaseline & 0xFF);
        assertEquals(50, finalPhase.salvageNegotiated & 0xFF);
    }

    @Test
    void rejectsInvalidPhaseShapes() {
        assertNull(PlanetaryAssaultPhase.create(0, 2, 100, 80, 80));
        assertNull(PlanetaryAssaultPhase.create(5, 5, 100, 80, 80));
        assertNull(PlanetaryAssaultPhase.create(0, 6, 100, 80, 80));
    }

    private static PlanetaryAssaultPhase phase(int index, int total) {
        return PlanetaryAssaultPhase.create(index, total, 180_000, 80, 80);
    }
}
