package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The collection safety net: a company that keeps winning but misses the ordinary
 * card sources is topped up to a floor of collected cards, in an authored order.
 *
 * <p>It counts and grants whole cards, never single equipment, and never a
 * Restricted card: those come only by warrant. Cards held in cargo count toward
 * the floor without being learned.
 */
final class EquipmentCollectionCurve {

    static final int FIVE_VICTORY_TARGET = 3;
    static final int FIFTEEN_VICTORY_TARGET = 6;
    static final int THIRTY_VICTORY_TARGET = 8;
    static final int FORTY_VICTORY_TARGET = 9;

    /** Low-tier cards, in the order the net hands them out; the Wayline refit sheets stay a choice. */
    static final List<LoadoutCard> FALLBACK_ORDER = List.of(
            new LoadoutCard(LoadoutCard.Kind.WEAPON, SquadEquipmentDoctrines.CONVOY_ESCORT_WEAPONS),
            new LoadoutCard(LoadoutCard.Kind.SHEET, SquadEquipmentDoctrines.OUTLAW_RAIDER_ARMOR),
            new LoadoutCard(LoadoutCard.Kind.SHEET, SquadEquipmentDoctrines.CHURCH_WARDEN_ARMOR),
            new LoadoutCard(LoadoutCard.Kind.WEAPON, SquadEquipmentDoctrines.BUILDERS_GUARD_WEAPONS),
            new LoadoutCard(LoadoutCard.Kind.SHEET, SquadEquipmentDoctrines.FREEPORT_ASSAULT_ARMOR),
            new LoadoutCard(LoadoutCard.Kind.SHEET, SquadEquipmentDoctrines.OUTLAW_LINE_ARMOR),
            new LoadoutCard(LoadoutCard.Kind.WEAPON, SquadEquipmentDoctrines.COUNTER_BATTERY_WEAPONS),
            new LoadoutCard(LoadoutCard.Kind.SHEET, SquadEquipmentDoctrines.LEAGUE_LINE_ARMOR),
            new LoadoutCard(LoadoutCard.Kind.WEAPON, SquadEquipmentDoctrines.CHECKPOINT_WEAPONS),
            new LoadoutCard(LoadoutCard.Kind.SHEET, SquadEquipmentDoctrines.SINDRIAN_SECURITY_ARMOR),
            new LoadoutCard(LoadoutCard.Kind.WEAPON, SquadEquipmentDoctrines.TRENCH_CLEARING_WEAPONS),
            new LoadoutCard(LoadoutCard.Kind.WEAPON, SquadEquipmentDoctrines.LINE_INFANTRY_WEAPONS));

    private EquipmentCollectionCurve() {}

    /** Collected cards beyond the starter pair a company should hold at this many victories. */
    static int minimumCollectedAtVictories(int victories) {
        int wins = Math.max(0, victories);
        if (wins < 2) return 0;
        if (wins < 5) return 1;
        if (wins <= 15) {
            return FIVE_VICTORY_TARGET
                    + (wins - 5) * (FIFTEEN_VICTORY_TARGET - FIVE_VICTORY_TARGET) / 10;
        }
        if (wins <= 30) {
            return FIFTEEN_VICTORY_TARGET
                    + (wins - 15) * (THIRTY_VICTORY_TARGET - FIFTEEN_VICTORY_TARGET) / 15;
        }
        if (wins <= 40) {
            return THIRTY_VICTORY_TARGET
                    + (wins - 30) * (FORTY_VICTORY_TARGET - THIRTY_VICTORY_TARGET) / 10;
        }
        return FORTY_VICTORY_TARGET;
    }

    /** Learns cards from the fallback order until the floor is met; returns those it granted. */
    static List<LoadoutCard> repair(MarineArmory armory, int victories, Set<String> carriedCardIds) {
        if (armory == null) return List.of();
        int target = minimumCollectedAtVictories(victories);
        Set<String> collected = new HashSet<>();
        Set<String> carried = carriedCardIds != null ? carriedCardIds : Set.of();
        for (LoadoutCard card : LoadoutCards.all()) {
            if (armory.knowsLoadoutCard(card.id()) || carried.contains(card.id())) collected.add(card.id());
        }
        List<LoadoutCard> granted = new ArrayList<>();
        for (LoadoutCard card : FALLBACK_ORDER) {
            if (collected.size() >= target) break;
            if (collected.contains(card.id()) || !card.defined()
                    || card.accessTier() == EquipmentAccessTier.RESTRICTED) continue;
            armory.learnLoadoutCard(card);
            collected.add(card.id());
            granted.add(card);
        }
        return List.copyOf(granted);
    }
}
