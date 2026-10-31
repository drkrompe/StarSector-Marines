package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a company store stocks ({@code progression-nouns.md}, Company store). The Kit Ladder alone paces loadout
 * cards, machine patterns and boat parts: every one whose station the campaign has passed ({@link KitLadder#forSale}),
 * and which the company does not already know or carry, is on sale at every store, whatever the market's size, owner
 * or month. Tier only sets the price ({@link EquipmentStore#price}).
 *
 * <p>There are no faction locks: not the market's owner, the company's standing with it, a military warrant or a
 * faction's equipment pool. A Restricted card is sold like any other once its station is passed; its tier is a label
 * and a price. A market's story listings ({@link MarketListings}) stand on a shelf of their own, first: the story is
 * the permission, so a listing does not wait on its card's station, but a card on {@code none} or with no placement
 * is never listed.
 */
final class StoreStock {

    private StoreStock() {}

    static StockPlan cards(Set<String> unavailableCardIds, EquipmentAcquisitionEligibility.Progress progress) {
        return cards(unavailableCardIds, progress, List.of());
    }

    /**
     * Every loadout card on a store's shelf: the market's listed cards first, then every card the ladder opens. A
     * listing the company already knows or carries is withdrawn, one on {@code none} is never shown, and a listed card
     * is never offered twice.
     */
    static StockPlan cards(Set<String> unavailableCardIds, EquipmentAcquisitionEligibility.Progress progress,
                           List<String> listedCardIds) {
        Set<String> unavailable = new HashSet<>(unavailableCardIds != null ? unavailableCardIds : Set.of());
        List<String> listed = new ArrayList<>();
        for (String cardId : listedCardIds != null ? listedCardIds : List.<String>of()) {
            LoadoutCard card = LoadoutCard.parse(cardId);
            if (card == null || unavailable.contains(card.id()) || listed.contains(card.id())) continue;
            if (!card.defined() || KitLadder.station(card.id()).equals(UnlockStation.NONE)) continue;
            listed.add(card.id());
        }
        unavailable.addAll(listed);
        List<String> market = new ArrayList<>();
        for (LoadoutCard card : LoadoutCards.all()) {
            if (!unavailable.contains(card.id())
                    && EquipmentAcquisitionEligibility.allows(card, FactionEquipmentSource.MARKET, progress)) {
                market.add(card.id());
            }
        }
        return new StockPlan(listed, market);
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

    /** Every boat part the ladder opens ({@link BoatParts#ladderId}) that the company does not know. */
    static List<String> boatParts(Set<String> unavailablePartIds, EquipmentAcquisitionEligibility.Progress progress) {
        Set<String> unavailable = unavailablePartIds != null ? unavailablePartIds : Set.of();
        Set<UnlockStation> reached = progress != null ? progress.stations() : Set.of();
        List<String> offered = new ArrayList<>();
        for (String id : BoatParts.offerable()) {
            if (!unavailable.contains(id) && KitLadder.forSale(BoatParts.ladderId(id), reached)) offered.add(id);
        }
        return List.copyOf(offered);
    }

    record StockPlan(List<String> listedCardIds, List<String> marketCardIds) {
        StockPlan {
            listedCardIds = List.copyOf(listedCardIds);
            marketCardIds = List.copyOf(marketCardIds);
        }

        List<String> allCardIds() {
            Set<String> ordered = new LinkedHashSet<>(listedCardIds);
            ordered.addAll(marketCardIds);
            return List.copyOf(ordered);
        }
    }
}
