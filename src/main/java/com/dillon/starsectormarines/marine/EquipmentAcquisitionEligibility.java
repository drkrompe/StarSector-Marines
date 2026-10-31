package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.fs.starfarer.api.Global;

import java.util.Set;

/**
 * Shared acquisition policy for every channel that hands out equipment ({@code progression-nouns.md}, Kit Ladder):
 * the company store (open market and licence), patron rewards and high-risk recovery.
 *
 * <p>A loadout card or machine pattern is handed out by any channel only once the campaign has passed its Kit Ladder
 * station ({@link KitLadder#forSale}); an item on {@code none} or with no placement is handed out by none. The store
 * and patrons ask nothing more; recovery still asks its victories of an Advanced, Prestige or Restricted item
 * ({@link #requiredRecoveryVictories}). No faction lock gates a card: not reputation (MRB, a contract matter for
 * {@code ContractEligibility}), standing, a military warrant or the Restricted tier, which is a label and a price.
 */
public final class EquipmentAcquisitionEligibility {

    public static final int ADVANCED_RECOVERY_VICTORIES = 5;
    public static final int PRESTIGE_RECOVERY_VICTORIES = 15;

    private EquipmentAcquisitionEligibility() {}

    /**
     * Whether {@code source} may offer {@code card}: once the company has passed its Kit Ladder station, through
     * every channel. Recovery also asks the card's scarcest tier of the company's victories, a Restricted card's as
     * a Prestige card's.
     */
    public static boolean allows(LoadoutCard card, FactionEquipmentSource source, Progress progress) {
        if (card == null || source == null || progress == null || !card.defined()) return false;
        if (!KitLadder.forSale(card.id(), progress.stations())) return false;
        return source != FactionEquipmentSource.RECOVERY || allowsRecovery(card.accessTier(), progress);
    }

    /**
     * Whether {@code source} may offer a mech pattern: once the company has passed its Kit Ladder station, through
     * every channel; recovery also asks the pattern's tier of the company's victories.
     */
    public static boolean allows(MechPattern pattern, FactionEquipmentSource source, Progress progress) {
        if (pattern == null || source == null || progress == null) return false;
        if (!KitLadder.forSale(pattern.id(), progress.stations())) return false;
        return source != FactionEquipmentSource.RECOVERY || allowsRecovery(pattern.accessTier(), progress);
    }

    /** Whether the company's victories reach {@code tier} through recovery; Restricted counts as Prestige. */
    private static boolean allowsRecovery(EquipmentAccessTier tier, Progress progress) {
        EquipmentAccessTier asked = tier == EquipmentAccessTier.RESTRICTED ? EquipmentAccessTier.PRESTIGE : tier;
        return asked != null && asked.ordinal() <= recoveryTier(progress).ordinal();
    }

    /** The company store's channels: open market and licence. */
    public static boolean storeChannel(FactionEquipmentSource source) {
        return source == FactionEquipmentSource.MARKET || source == FactionEquipmentSource.LICENSE;
    }

    /**
     * Whether {@code source} may offer one piece of equipment on its own. Restricted
     * equipment never is: it reaches the company only inside a card.
     */
    public static boolean allows(EquipmentTemplateCard template,
                                 FactionEquipmentSource source,
                                 Progress progress) {
        if (template == null) return false;
        return allows(template.accessTier(), source, progress);
    }

    /**
     * The tier half of the rule, for callers that hold no card identity (a single equipment template): the open market
     * carries Common kit, licence and patron every tier but Restricted, recovery what the company's victories reach
     * ({@link #recoveryTier}). It never admits {@link EquipmentAccessTier#RESTRICTED}, which comes only inside a card.
     */
    public static boolean allows(EquipmentAccessTier tier, FactionEquipmentSource source,
                                 Progress progress) {
        if (tier == null || source == null || progress == null) return false;
        if (tier == EquipmentAccessTier.RESTRICTED) return false;
        EquipmentAccessTier available = switch (source) {
            case MARKET -> EquipmentAccessTier.COMMON;
            case LICENSE, PATRON -> EquipmentAccessTier.PRESTIGE;
            case RECOVERY -> recoveryTier(progress);
        };
        return tier.ordinal() <= available.ordinal();
    }

    /** The channels a faction pool may list Restricted equipment under ({@link FactionEquipmentCatalog} validation). */
    public static boolean warrantChannel(FactionEquipmentSource source) {
        return source == FactionEquipmentSource.LICENSE || source == FactionEquipmentSource.PATRON;
    }

    public static EquipmentAccessTier recoveryTier(Progress progress) {
        return recoveryTier(progress != null ? progress.victories() : 0);
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
        Set<UnlockStation> stations = UnlockStation.reached(roster != null ? roster::hasCompletedStory : null,
                campaignScript != null ? campaignScript.state() : null);
        return new Progress(victories, stations);
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
     * @param stations Kit Ladder stations the campaign has passed ({@link UnlockStation#reached})
     */
    public record Progress(int victories, Set<UnlockStation> stations) {
        /** A new company: no victories, only the ladder's start. */
        public static final Progress OPENING = new Progress(0, Set.of(UnlockStation.START));

        public Progress {
            victories = Math.max(0, victories);
            stations = stations != null ? Set.copyOf(stations) : Set.of(UnlockStation.START);
        }

        /** A company past the whole Kit Ladder, for catalog validation and detached fixtures. */
        public Progress(int victories) {
            this(victories, UnlockStation.all());
        }
    }
}
