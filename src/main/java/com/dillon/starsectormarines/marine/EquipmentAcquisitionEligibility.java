package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.ContractEligibility;
import com.fs.starfarer.api.Global;

import java.util.Collection;
import java.util.Set;

/** Shared access-tier policy for every faction equipment acquisition channel. */
public final class EquipmentAcquisitionEligibility {

    public static final int ADVANCED_RECOVERY_VICTORIES = 5;
    public static final int PRESTIGE_RECOVERY_VICTORIES = 15;

    private EquipmentAcquisitionEligibility() {}

    /**
     * Whether {@code source} may offer {@code card}. A Restricted card answers to a military
     * warrant instead of to reputation or victories: only a warrant naming it opens it, and
     * then only through licence and patron, the channels a tradition issues through.
     */
    public static boolean allows(EquipmentTemplateCard card,
                                 FactionEquipmentSource source,
                                 Progress progress) {
        if (card == null || source == null || progress == null) return false;
        if (card.accessTier() == EquipmentAccessTier.RESTRICTED) {
            return warrantChannel(source) && progress.warrants(card.id());
        }
        return allows(card.accessTier(), source, progress);
    }

    /**
     * Tier-only form, for callers that hold no template identity. It can never admit
     * {@link EquipmentAccessTier#RESTRICTED}: a warrant names templates, not a tier.
     */
    public static boolean allows(EquipmentAccessTier tier, FactionEquipmentSource source,
                                 Progress progress) {
        if (tier == null || source == null || progress == null) return false;
        if (tier == EquipmentAccessTier.RESTRICTED) return false;
        EquipmentAccessTier available = switch (source) {
            case MARKET -> EquipmentAccessTier.COMMON;
            case LICENSE, PATRON -> licensedTier(progress);
            case RECOVERY -> recoveryTier(progress);
        };
        return tier.ordinal() <= available.ordinal();
    }

    /** A bundle is on offer when every template in it is: its scarcest tier gates the rest. */
    public static boolean allowsAll(Collection<String> templateIds, FactionEquipmentSource source,
                                    Progress progress) {
        if (templateIds == null || source == null || progress == null) return false;
        for (String id : templateIds) {
            if (!allows(EquipmentTemplateCatalog.require(id), source, progress)) return false;
        }
        return true;
    }

    /** The channels a tradition issues warranted equipment through. */
    public static boolean warrantChannel(FactionEquipmentSource source) {
        return source == FactionEquipmentSource.LICENSE || source == FactionEquipmentSource.PATRON;
    }

    public static EquipmentAccessTier licensedTier(Progress progress) {
        return reputationTier(progress != null ? progress.mrbRep() : 0);
    }

    public static EquipmentAccessTier recoveryTier(Progress progress) {
        return recoveryTier(progress != null ? progress.victories() : 0);
    }

    /** MRB a tier asks of licence and patron; Restricted is never reached by reputation. */
    public static int requiredMrb(EquipmentAccessTier tier) {
        if (tier == null) return Integer.MAX_VALUE;
        return switch (tier) {
            case COMMON -> Integer.MIN_VALUE;
            case ADVANCED -> ContractEligibility.TIER_2_MRB_REQUIRED;
            case PRESTIGE -> ContractEligibility.TIER_3_MRB_REQUIRED;
            case RESTRICTED -> Integer.MAX_VALUE;
        };
    }

    /** Victories a tier asks of recovery; Restricted is never recovered. */
    public static int requiredRecoveryVictories(EquipmentAccessTier tier) {
        if (tier == null) return Integer.MAX_VALUE;
        return switch (tier) {
            case COMMON -> 0;
            case ADVANCED -> ADVANCED_RECOVERY_VICTORIES;
            case PRESTIGE -> PRESTIGE_RECOVERY_VICTORIES;
            case RESTRICTED -> Integer.MAX_VALUE;
        };
    }

    public static Progress currentProgress() {
        if (Global.getSector() == null) return Progress.OPENING;
        MarineRosterScript rosterScript = MarineRosterScript.getInstance();
        CampaignStateScript campaignScript = CampaignStateScript.getInstance();
        int victories = rosterScript != null ? rosterScript.roster().armory().victories() : 0;
        int mrbRep = campaignScript != null ? campaignScript.state().playerMrbRep : 0;
        Set<String> warranted = campaignScript != null
                ? MilitaryWarrants.warrantedTemplateIds(campaignScript.state()) : Set.of();
        return new Progress(victories, mrbRep, warranted);
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

    /**
     * What the company has done, as acquisition reads it.
     *
     * @param warrantedTemplateIds Restricted templates some held military warrant opens
     */
    public record Progress(int victories, int mrbRep, Set<String> warrantedTemplateIds) {
        public static final Progress OPENING = new Progress(0, 0);

        public Progress {
            victories = Math.max(0, victories);
            warrantedTemplateIds = warrantedTemplateIds != null ? Set.copyOf(warrantedTemplateIds) : Set.of();
        }

        /** A company holding no military warrant. */
        public Progress(int victories, int mrbRep) {
            this(victories, mrbRep, Set.of());
        }

        public boolean warrants(String templateId) {
            return templateId != null && warrantedTemplateIds.contains(templateId);
        }
    }
}
