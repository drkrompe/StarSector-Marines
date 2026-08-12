package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanetaryAssaultTermsTest {

    @Test
    void firstDeploymentFreezesContractWideTerms() {
        CampaignState state = state(ContractState.OFFERED);

        assertTrue(PlanetaryAssaultTerms.lockForDeployment(state, mission(40, 120)));
        assertEquals(40, state.contractSalvageNegotiated[0] & 0xFF);
        assertEquals(120, state.contractCashMultiplier[0] & 0xFF);
    }

    @Test
    void laterPhaseAcceptsOnlyFrozenTerms() {
        CampaignState state = state(ContractState.IN_PROGRESS);
        state.contractSalvageNegotiated[0] = 40;
        state.contractCashMultiplier[0] = 120;

        assertTrue(PlanetaryAssaultTerms.lockForDeployment(state, mission(40, 120)));
        assertFalse(PlanetaryAssaultTerms.lockForDeployment(state, mission(20, 130)));
        assertFalse(PlanetaryAssaultTerms.negotiationOpen(state, state.contractId[0]));
    }

    @Test
    void rejectsInvalidCurveAndNonAssaultContract() {
        CampaignState state = state(ContractState.OFFERED);
        assertFalse(PlanetaryAssaultTerms.lockForDeployment(state, mission(40, 110)));
        state.contractType[0] = ContractType.STRIKE.toByte();
        assertFalse(PlanetaryAssaultTerms.lockForDeployment(state, mission(40, 120)));
    }

    private static CampaignState state(ContractState contractState) {
        CampaignState state = new CampaignState();
        state.addContract(1L, 2L, -1L, ContractType.PLANETARY_ASSAULT,
                contractState, 1, -1, 20, (byte) 4, -1, 1, -1,
                180_000, 0, (byte) 80, (byte) 80, (byte) 100);
        return state;
    }

    private static Mission mission(int contractNegotiated, int cashMultiplier) {
        return new Mission("contract:1:phase:0:attempt:0", "Recon",
                MissionType.SABOTAGE, MissionSource.GENERATED, 27_000,
                RiskLevel.LOW, "", "", 0.5f, 0.5f,
                FlybyRoster.EMPTY, FlybyRoster.EMPTY, 1, 1,
                "Target", null, null, 1L,
                (byte) 20, (byte) Math.min(20, contractNegotiated),
                (byte) cashMultiplier, (byte) 80, (byte) contractNegotiated,
                Collections.emptyList());
    }
}
