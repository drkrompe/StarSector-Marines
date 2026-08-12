package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlanetaryAssaultMissionKeyTest {

    @Test
    void roundTripsContractPhaseAndAttempt() {
        String encoded = PlanetaryAssaultMissionKey.encode(42L, 3, 2);
        PlanetaryAssaultMissionKey parsed = PlanetaryAssaultMissionKey.parse(encoded);

        assertEquals("contract:42:phase:3:attempt:2", encoded);
        assertEquals(42L, parsed.contractId);
        assertEquals(3, parsed.phaseIndex);
        assertEquals(2, parsed.attempt);
    }

    @Test
    void rejectsMalformedAndNegativeKeys() {
        assertNull(PlanetaryAssaultMissionKey.parse("contract:42"));
        assertNull(PlanetaryAssaultMissionKey.parse("contract:x:phase:0:attempt:0"));
        assertNull(PlanetaryAssaultMissionKey.parse("contract:42:phase:-1:attempt:0"));
        assertNull(PlanetaryAssaultMissionKey.parse(null));
    }
}
