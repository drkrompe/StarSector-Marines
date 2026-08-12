package com.dillon.starsectormarines.ops.loot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LootRecoveryModifiersTest {

    @Test
    void expertAndFleetHardwareStack() {
        LootRecoveryModifier modifier = LootRecoveryModifiers.compute(true, 2, 3);

        assertEquals(60, modifier.recoveryBonusPct);
        assertEquals(10, modifier.highValueChancePct);
    }

    @Test
    void fleetHardwareBonusIsCapped() {
        LootRecoveryModifier modifier = LootRecoveryModifiers.compute(false, 4, 10);

        assertEquals(40, modifier.recoveryBonusPct);
        assertEquals(0, modifier.highValueChancePct);
    }
}
