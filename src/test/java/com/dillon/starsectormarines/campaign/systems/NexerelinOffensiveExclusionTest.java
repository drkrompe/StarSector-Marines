package com.dillon.starsectormarines.campaign.systems;

import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexerelinOffensiveExclusionTest {

    /** Nexerelin's InvasionIntel shape: one step below the offensive base class. */
    static class InvasionIntel extends OffensiveFleetIntel {}

    /** A further subclass, as Nex's defence and expedition intels sit deeper in the chain. */
    static class DeeperOffensive extends InvasionIntel {}

    static class OrdinaryRaid {}

    @Test
    void stubCarriesNexerelinsName() {
        assertEquals(VanillaRaidGarrisonSystem.NEXERELIN_OFFENSIVE_FLEET_INTEL,
                OffensiveFleetIntel.class.getName());
    }

    @Test
    void offensiveBaseAndEverySubclassAreExcluded() {
        assertTrue(VanillaRaidGarrisonSystem.isNexerelinOffensive(OffensiveFleetIntel.class));
        assertTrue(VanillaRaidGarrisonSystem.isNexerelinOffensive(InvasionIntel.class));
        assertTrue(VanillaRaidGarrisonSystem.isNexerelinOffensive(DeeperOffensive.class));
    }

    @Test
    void unrelatedClassesAreNot() {
        assertFalse(VanillaRaidGarrisonSystem.isNexerelinOffensive(OrdinaryRaid.class));
        assertFalse(VanillaRaidGarrisonSystem.isNexerelinOffensive(Object.class));
        assertFalse(VanillaRaidGarrisonSystem.isNexerelinOffensive(null));
    }
}
