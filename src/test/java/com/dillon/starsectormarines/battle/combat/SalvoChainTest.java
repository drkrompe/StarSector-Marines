package com.dillon.starsectormarines.battle.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The autonomous salvo-count rule: one at men, more at armor or behind point defence. */
class SalvoChainTest {

    @Test
    void oneSalvoAtASoftTargetAndTheWholeLoadAtAMechOrBehindPointDefence() {
        assertEquals(1, SalvoChain.autonomousSalvos(3, 3, false, false, false));
        assertEquals(3, SalvoChain.autonomousSalvos(3, 3, true, true, false));
        assertEquals(6, SalvoChain.autonomousSalvos(6, 6, true, true, false), "a deeper rack is a bigger spike");
        assertEquals(3, SalvoChain.autonomousSalvos(3, 3, false, false, true), "saturate the interceptors");
    }

    @Test
    void otherArmorTakesHalfTheLoadAndNothingExceedsWhatIsLoadedOrTheCap() {
        assertEquals(2, SalvoChain.autonomousSalvos(3, 3, true, false, false));
        assertEquals(3, SalvoChain.autonomousSalvos(6, 6, true, false, false));
        assertEquals(1, SalvoChain.autonomousSalvos(1, 6, true, true, true), "one loaded is one");
        assertEquals(0, SalvoChain.autonomousSalvos(0, 6, true, true, true));
        assertEquals(SalvoChain.VEHICLE_AUTONOMOUS_SALVOS,
                SalvoChain.autonomousSalvos(6, SalvoChain.VEHICLE_AUTONOMOUS_SALVOS, true, true, false),
                "a vehicle's magazine is not one rack's buffer");
    }

    @Test
    void aChainedSalvoPaysHalfItsFlux() {
        assertEquals(40f, SalvoChain.fluxCost(40f, false));
        assertEquals(20f, SalvoChain.fluxCost(40f, true));
    }
}
