package com.dillon.starsectormarines.ops.loot;

import com.dillon.starsectormarines.ops.MissionOutcome;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;

/** Immutable facts that determine one recovery roll. */
public final class LootRollRequest {

    public final String missionId;
    public final MissionType missionType;
    public final RiskLevel risk;
    public final String targetFactionId;
    public final String targetIndustryId;
    public final int payout;
    public final int entitlement;

    public LootRollRequest(String missionId, MissionType missionType, RiskLevel risk,
                           String targetFactionId, String targetIndustryId,
                           int payout, int entitlement) {
        this.missionId = missionId;
        this.missionType = missionType;
        this.risk = risk;
        this.targetFactionId = targetFactionId;
        this.targetIndustryId = targetIndustryId;
        this.payout = Math.max(0, payout);
        this.entitlement = Math.max(0, entitlement);
    }

    public static LootRollRequest from(MissionOutcome outcome) {
        return new LootRollRequest(outcome.missionId, outcome.missionType, outcome.risk,
                outcome.targetFactionId, outcome.targetIndustryId,
                outcome.payoutBase, outcome.salvageEntitlement);
    }
}
