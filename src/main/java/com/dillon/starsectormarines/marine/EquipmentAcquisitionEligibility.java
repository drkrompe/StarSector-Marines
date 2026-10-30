package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.ContractEligibility;
import com.fs.starfarer.api.Global;

import java.util.Set;

/** Shared access-tier policy for every faction equipment acquisition channel. */
public final class EquipmentAcquisitionEligibility {

    public static final int ADVANCED_RECOVERY_VICTORIES = 5;
    public static final int PRESTIGE_RECOVERY_VICTORIES = 15;

    private EquipmentAcquisitionEligibility() {}

    /**
     * Whether {@code source} may offer {@code card}. A Restricted card answers to a military
     * warrant instead of to reputation or victories: only a warrant naming the card opens it,
     * and then only through licence and patron, the channels a tradition issues through.
     * Through the store's channels a card is on offer once the company has passed its Kit Ladder
     * station ({@link KitLadder}); through patron and recovery, when the channel reaches its
     * scarcest tier.
     */
    public static boolean allows(LoadoutCard card, FactionEquipmentSource source, Progress progress) {
        if (card == null || source == null || progress == null || !card.defined()) return false;
        if (storeChannel(source) && !KitLadder.forSale(card.id(), progress.stations())) return false;
        if (card.accessTier() == EquipmentAccessTier.RESTRICTED) {
            return warrantChannel(source) && progress.warrants(card.id());
        }
        return storeChannel(source) || allows(card.accessTier(), source, progress);
    }

    /**
     * Whether {@code source} may offer a mech pattern: through the store's channels once the company has passed its
     * Kit Ladder station, through patron and recovery when the channel reaches the pattern's tier.
     */
    public static boolean allows(MechPattern pattern, FactionEquipmentSource source, Progress progress) {
        if (pattern == null || source == null || progress == null) return false;
        if (storeChannel(source)) return KitLadder.forSale(pattern.id(), progress.stations());
        return allows(pattern.accessTier(), source, progress);
    }

    /** The company store's channels, the ones the Kit Ladder paces: open market and licence. */
    public static boolean storeChannel(FactionEquipmentSource source) {
        return source == FactionEquipmentSource.MARKET || source == FactionEquipmentSource.LICENSE;
    }

    /**
     * Whether {@code source} may offer one piece of equipment on its own. Restricted
     * equipment never is: a warrant names cards, so it is reached only inside one.
     */
    public static boolean allows(EquipmentTemplateCard template,
                                 FactionEquipmentSource source,
                                 Progress progress) {
        if (template == null) return false;
        return allows(template.accessTier(), source, progress);
    }

    /**
     * Tier-only form, for callers that hold no card identity. It can never admit
     * {@link EquipmentAccessTier#RESTRICTED}: a warrant names cards, not a tier.
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
        MarineRoster roster = rosterScript != null ? rosterScript.roster() : null;
        int victories = roster != null ? roster.armory().victories() : 0;
        int mrbRep = campaignScript != null ? campaignScript.state().playerMrbRep : 0;
        Set<String> warranted = campaignScript != null
                ? MilitaryWarrants.warrantedCardIds(campaignScript.state()) : Set.of();
        Set<UnlockStation> stations = UnlockStation.reached(roster != null ? roster::hasCompletedStory : null,
                campaignScript != null ? campaignScript.state() : null);
        return new Progress(victories, mrbRep, warranted, stations);
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
     * @param warrantedCardIds Restricted loadout cards some held military warrant opens
     * @param stations Kit Ladder stations the campaign has passed ({@link UnlockStation#reached})
     */
    public record Progress(int victories, int mrbRep, Set<String> warrantedCardIds, Set<UnlockStation> stations) {
        /** A new company: no victories, no standing, only the ladder's start. */
        public static final Progress OPENING = new Progress(0, 0, Set.of(), Set.of(UnlockStation.START));

        public Progress {
            victories = Math.max(0, victories);
            warrantedCardIds = warrantedCardIds != null ? Set.copyOf(warrantedCardIds) : Set.of();
            stations = stations != null ? Set.copyOf(stations) : Set.of(UnlockStation.START);
        }

        /**
         * A company past the whole Kit Ladder, for callers that read only victories, standing and warrants (patron
         * rewards, catalog validation, detached fixtures).
         */
        public Progress(int victories, int mrbRep, Set<String> warrantedCardIds) {
            this(victories, mrbRep, warrantedCardIds, UnlockStation.all());
        }

        /** A company past the whole Kit Ladder holding no military warrant. */
        public Progress(int victories, int mrbRep) {
            this(victories, mrbRep, Set.of());
        }

        public boolean warrants(String cardId) {
            return cardId != null && warrantedCardIds.contains(cardId);
        }
    }
}
