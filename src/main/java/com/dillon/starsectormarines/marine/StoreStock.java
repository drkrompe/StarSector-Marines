package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a company store stocks ({@code progression-nouns.md}, Company store). The Kit Ladder alone paces loadout cards
 * and machine patterns: every one whose station the campaign has passed ({@link KitLadder#forSale}), and which the
 * company does not already know or carry, is on sale at every store, whatever the market's size, owner or month.
 * Tier only sets the price ({@link EquipmentStore#price}).
 *
 * <p>Two older rules still narrow the cards. A Restricted card needs a military warrant naming it, and stands only on
 * the licence shelf (standing with the market's faction) of a faction that fields its Restricted kit, the tradition
 * that issues it. A market's story listings ({@link MarketListings}) stand on a shelf of their own, first.
 *
 * <p>Boat parts are not on the Kit Ladder; they keep their monthly rotation ({@link #boatParts}).
 */
final class StoreStock {

    private static final int MAX_BOAT_PART_SLOTS = 2;

    private StoreStock() {}

    static StockPlan cards(String factionId, boolean hasLicenseAccess, Set<String> unavailableCardIds,
                           EquipmentAcquisitionEligibility.Progress progress) {
        return cards(factionId, hasLicenseAccess, unavailableCardIds, progress, List.of());
    }

    /**
     * Every loadout card on a store's shelf: the market's listed cards first, then every card the ladder opens. A
     * listing the company already knows or carries is withdrawn, one whose Restricted kit is no longer warranted is
     * not shown, and a listed card is never offered twice.
     */
    static StockPlan cards(String factionId, boolean hasLicenseAccess, Set<String> unavailableCardIds,
                           EquipmentAcquisitionEligibility.Progress progress, List<String> listedCardIds) {
        Set<String> unavailable = new HashSet<>(unavailableCardIds != null ? unavailableCardIds : Set.of());
        List<String> listed = new ArrayList<>();
        for (String cardId : listedCardIds != null ? listedCardIds : List.<String>of()) {
            LoadoutCard card = LoadoutCard.parse(cardId);
            if (card == null || unavailable.contains(card.id()) || listed.contains(card.id())) continue;
            if (!restrictedKitWarranted(card, progress)) continue;
            listed.add(card.id());
        }
        unavailable.addAll(listed);
        List<String> market = new ArrayList<>();
        List<String> licensed = new ArrayList<>();
        for (LoadoutCard card : LoadoutCards.all()) {
            if (unavailable.contains(card.id())) continue;
            if (card.accessTier() == EquipmentAccessTier.RESTRICTED) {
                if (hasLicenseAccess
                        && EquipmentAcquisitionEligibility.allows(card, FactionEquipmentSource.LICENSE, progress)
                        && LoadoutCardPicker.fieldsEveryRestricted(card, factionId, FactionEquipmentSource.LICENSE)) {
                    licensed.add(card.id());
                }
            } else if (EquipmentAcquisitionEligibility.allows(card, FactionEquipmentSource.MARKET, progress)) {
                market.add(card.id());
            }
        }
        return new StockPlan(listed, market, licensed);
    }

    /** Every machine pattern (chassis, weapon, internal, vehicle) the ladder opens that the company lacks. */
    static List<String> patterns(Set<String> unavailablePatternIds,
                                 EquipmentAcquisitionEligibility.Progress progress) {
        Set<String> unavailable = unavailablePatternIds != null ? unavailablePatternIds : Set.of();
        List<String> offered = new ArrayList<>();
        for (MechPattern pattern : MechPattern.offerable()) {
            if (!unavailable.contains(pattern.id())
                    && EquipmentAcquisitionEligibility.allows(pattern, FactionEquipmentSource.MARKET, progress)) {
                offered.add(pattern.id());
            }
        }
        return List.copyOf(offered);
    }

    /**
     * The boat part slots of an open market, on a monthly rotation: one for a market of size 3 or 4, two from size
     * 5. Drawn through the licence channel when the company holds a licence there, else the ordinary market channel.
     * Boat parts are paced by access tier, not by the Kit Ladder.
     */
    static List<String> boatParts(String factionId, String marketId, int marketSize, long rotation,
                                  boolean hasLicenseAccess, Set<String> unavailablePartIds,
                                  EquipmentAcquisitionEligibility.Progress progress) {
        int slots = Math.max(0, Math.min(MAX_BOAT_PART_SLOTS, (marketSize - 1) / 2));
        if (slots == 0) return List.of();
        return BoatPartPicker.pick(factionId,
                hasLicenseAccess ? FactionEquipmentSource.LICENSE : FactionEquipmentSource.MARKET,
                slots, seed(factionId, marketId, rotation, "boat-part"), unavailablePartIds, progress);
    }

    private static long seed(String factionId, String marketId, long rotation, String channel) {
        long hash = 0xcbf29ce484222325L;
        for (String part : List.of(text(factionId), text(marketId), channel,
                Long.toString(rotation))) {
            for (int index = 0; index < part.length(); index++) {
                hash ^= part.charAt(index);
                hash *= 0x100000001b3L;
            }
            hash ^= 0xff;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static String text(String value) {
        return value != null ? value : "";
    }

    private static boolean restrictedKitWarranted(LoadoutCard card,
                                                  EquipmentAcquisitionEligibility.Progress progress) {
        return card.accessTier() != EquipmentAccessTier.RESTRICTED
                || (progress != null && progress.warrants(card.id()));
    }

    record StockPlan(List<String> listedCardIds, List<String> marketCardIds,
                     List<String> licensedCardIds) {
        StockPlan {
            listedCardIds = List.copyOf(listedCardIds);
            marketCardIds = List.copyOf(marketCardIds);
            licensedCardIds = List.copyOf(licensedCardIds);
        }

        List<String> allCardIds() {
            Set<String> ordered = new LinkedHashSet<>(listedCardIds);
            ordered.addAll(marketCardIds);
            ordered.addAll(licensedCardIds);
            return List.copyOf(ordered);
        }
    }
}
