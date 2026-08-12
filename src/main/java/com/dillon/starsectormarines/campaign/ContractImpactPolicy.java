package com.dillon.starsectormarines.campaign;

/** Pure policy for political effects that differ by contract archetype. */
public final class ContractImpactPolicy {

    private ContractImpactPolicy() {}

    public static boolean transfersIndustryStake(ContractType type) {
        return type == ContractType.STRIKE || type == ContractType.PLANETARY_ASSAULT;
    }
}
