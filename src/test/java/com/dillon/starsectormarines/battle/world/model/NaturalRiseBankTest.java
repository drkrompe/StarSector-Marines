package com.dillon.starsectormarines.battle.world.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalRiseBankTest {

    @Test
    void aBankClimbsEvenlyToItsTopAndStepsBackAsItClimbs() {
        List<NaturalRise.Step> bank = NaturalRise.bank(30f);
        assertEquals(30f, bank.get(bank.size() - 1).heightMeters(), 1e-4f);
        assertEquals(NaturalRise.BANK_CELLS, bank.get(bank.size() - 1).insetCells(), 1e-4f);
        for (int k = 1; k < bank.size(); k++) {
            assertTrue(bank.get(k).heightMeters() > bank.get(k - 1).heightMeters());
            assertTrue(bank.get(k).insetCells() > bank.get(k - 1).insetCells());
        }
    }
}
