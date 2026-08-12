package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractImpactPolicyTest {

    @Test
    void onlyTerritorialContractsTransferIndustryStake() {
        assertTrue(ContractImpactPolicy.transfersIndustryStake(ContractType.STRIKE));
        assertTrue(ContractImpactPolicy.transfersIndustryStake(ContractType.PLANETARY_ASSAULT));
        assertFalse(ContractImpactPolicy.transfersIndustryStake(ContractType.ESCORT));
        assertFalse(ContractImpactPolicy.transfersIndustryStake(ContractType.GARRISON));
        assertFalse(ContractImpactPolicy.transfersIndustryStake(ContractType.CADRE));
        assertFalse(ContractImpactPolicy.transfersIndustryStake(ContractType.EXTRACTION));
        assertFalse(ContractImpactPolicy.transfersIndustryStake(null));
    }
}
