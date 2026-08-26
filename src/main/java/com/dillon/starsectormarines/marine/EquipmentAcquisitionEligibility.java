package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.ContractEligibility;
import com.fs.starfarer.api.Global;

/** Shared access-tier policy for every faction equipment acquisition channel. */
public final class EquipmentAcquisitionEligibility {

    public static final int ADVANCED_RECOVERY_VICTORIES = 5;
    public static final int PRESTIGE_RECOVERY_VICTORIES = 15;

    private EquipmentAcquisitionEligibility() {}

    public static boolean allows(EquipmentTemplateCard card,
                                 FactionEquipmentSource source,
                                 Progress progress) {
        if (card == null || source == null || progress == null) return false;
        EquipmentAccessTier available = switch (source) {
            case MARKET -> EquipmentAccessTier.COMMON;
            case LICENSE, PATRON -> licensedTier(progress);
            case RECOVERY -> recoveryTier(progress);
        };
        return card.accessTier().ordinal() <= available.ordinal();
    }

    public static EquipmentAccessTier licensedTier(Progress progress) {
        return reputationTier(progress != null ? progress.mrbRep() : 0);
    }

    public static EquipmentAccessTier recoveryTier(Progress progress) {
        return recoveryTier(progress != null ? progress.victories() : 0);
    }

    public static int requiredMrb(EquipmentAccessTier tier) {
        if (tier == null) return Integer.MAX_VALUE;
        return switch (tier) {
            case COMMON -> Integer.MIN_VALUE;
            case ADVANCED -> ContractEligibility.TIER_2_MRB_REQUIRED;
            case PRESTIGE -> ContractEligibility.TIER_3_MRB_REQUIRED;
        };
    }

    public static int requiredRecoveryVictories(EquipmentAccessTier tier) {
        if (tier == null) return Integer.MAX_VALUE;
        return switch (tier) {
            case COMMON -> 0;
            case ADVANCED -> ADVANCED_RECOVERY_VICTORIES;
            case PRESTIGE -> PRESTIGE_RECOVERY_VICTORIES;
        };
    }

    public static Progress currentProgress() {
        if (Global.getSector() == null) return Progress.OPENING;
        MarineRosterScript rosterScript = MarineRosterScript.getInstance();
        CampaignStateScript campaignScript = CampaignStateScript.getInstance();
        int victories = rosterScript != null ? rosterScript.roster().armory().victories() : 0;
        int mrbRep = campaignScript != null ? campaignScript.state().playerMrbRep : 0;
        return new Progress(victories, mrbRep);
    }

    private static EquipmentAccessTier reputationTier(int mrbRep) {
        if (mrbRep >= ContractEligibility.TIER_3_MRB_REQUIRED) {
            return EquipmentAccessTier.PRESTIGE;
        }
        if (mrbRep >= ContractEligibility.TIER_2_MRB_REQUIRED) {
            return EquipmentAccessTier.ADVANCED;
        }
        return EquipmentAccessTier.COMMON;
    }

    private static EquipmentAccessTier recoveryTier(int victories) {
        if (victories >= PRESTIGE_RECOVERY_VICTORIES) {
            return EquipmentAccessTier.PRESTIGE;
        }
        if (victories >= ADVANCED_RECOVERY_VICTORIES) {
            return EquipmentAccessTier.ADVANCED;
        }
        return EquipmentAccessTier.COMMON;
    }

    public record Progress(int victories, int mrbRep) {
        public static final Progress OPENING = new Progress(0, 0);

        public Progress {
            victories = Math.max(0, victories);
        }
    }
}
