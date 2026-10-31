package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.fs.starfarer.api.Global;

import java.util.Set;

/**
 * Shared acquisition policy for every channel that hands out equipment ({@code progression-nouns.md}, Kit Ladder):
 * the company store, patron rewards and high-risk recovery.
 *
 * <p>An item is in exactly one of three states: never sold (on {@code none}, or not placed), held from the start, or
 * unlocked after one named mission (its Kit Ladder station, {@link KitLadder#forSale}). Every channel asks only that;
 * nothing else gates acquisition: not victories, reputation, standing, a warrant, a faction or a tier, which is a
 * label and a price.
 */
public final class EquipmentAcquisitionEligibility {

    private EquipmentAcquisitionEligibility() {}

    /** Whether {@code source} may offer {@code card}: once the campaign has passed its Kit Ladder station. */
    public static boolean allows(LoadoutCard card, FactionEquipmentSource source, Progress progress) {
        if (card == null || source == null || progress == null || !card.defined()) return false;
        return KitLadder.forSale(card.id(), progress.stations());
    }

    /** Whether {@code source} may offer a machine pattern: once the campaign has passed its Kit Ladder station. */
    public static boolean allows(MechPattern pattern, FactionEquipmentSource source, Progress progress) {
        if (pattern == null || source == null || progress == null) return false;
        return KitLadder.forSale(pattern.id(), progress.stations());
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
     * carries Common kit and every other channel every tier but Restricted, which comes only inside a card.
     */
    public static boolean allows(EquipmentAccessTier tier, FactionEquipmentSource source,
                                 Progress progress) {
        if (tier == null || source == null || progress == null) return false;
        if (tier == EquipmentAccessTier.RESTRICTED) return false;
        return source != FactionEquipmentSource.MARKET || tier == EquipmentAccessTier.COMMON;
    }

    /** The channels a faction pool may list Restricted equipment under ({@link FactionEquipmentCatalog} validation). */
    public static boolean warrantChannel(FactionEquipmentSource source) {
        return source == FactionEquipmentSource.LICENSE || source == FactionEquipmentSource.PATRON;
    }

    public static Progress currentProgress() {
        if (Global.getSector() == null) return Progress.OPENING;
        MarineRosterScript rosterScript = MarineRosterScript.getInstance();
        CampaignStateScript campaignScript = CampaignStateScript.getInstance();
        MarineRoster roster = rosterScript != null ? rosterScript.roster() : null;
        return new Progress(UnlockStation.reached(roster != null ? roster::hasCompletedStory : null,
                campaignScript != null ? campaignScript.state() : null));
    }

    /**
     * What the company has done, as acquisition reads it.
     *
     * @param stations Kit Ladder stations the campaign has passed ({@link UnlockStation#reached})
     */
    public record Progress(Set<UnlockStation> stations) {
        /** A new company: only the ladder's start. */
        public static final Progress OPENING = new Progress(Set.of(UnlockStation.START));

        public Progress {
            stations = stations != null ? Set.copyOf(stations) : Set.of(UnlockStation.START);
        }

        /** A company past the whole Kit Ladder, for catalog validation and detached fixtures. */
        public static Progress complete() {
            return new Progress(UnlockStation.all());
        }
    }
}
