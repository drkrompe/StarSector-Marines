package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.ContractType;

/** Pure mapping from supported one-shot contracts to their battle mission shape. */
public final class ContractMissionProfile {

    public final MissionType missionType;
    public final String title;

    private ContractMissionProfile(MissionType missionType, String title) {
        this.missionType = missionType;
        this.title = title;
    }

    public static ContractMissionProfile from(ContractType type) {
        if (type == ContractType.STRIKE) {
            return new ContractMissionProfile(MissionType.RAID, "Strike");
        }
        if (type == ContractType.ESCORT) {
            return new ContractMissionProfile(MissionType.EXTRACTION, "Escort");
        }
        if (type == ContractType.EXTRACTION) {
            return new ContractMissionProfile(MissionType.EXTRACTION, "Recovery");
        }
        return null;
    }
}
