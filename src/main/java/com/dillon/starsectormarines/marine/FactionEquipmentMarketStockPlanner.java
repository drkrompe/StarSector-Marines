package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure monthly selection policy for faction-authored equipment-card stock. */
final class FactionEquipmentMarketStockPlanner {

    private static final int MAX_MARKET_SLOTS = 4;
    private static final int MAX_LICENSE_SLOTS = 2;
    private static final int MAX_BOAT_PART_SLOTS = 2;
    /** Smallest civilian market that carries a mech pattern shelf; military markets always do. */
    static final int MIN_MECH_PATTERN_MARKET_SIZE = 5;

    private FactionEquipmentMarketStockPlanner() {}

    static StockPlan plan(String factionId, String marketId, int marketSize,
                          long rotation, boolean hasLicenseAccess,
                          Set<String> unavailableCardIds,
                          EquipmentAcquisitionEligibility.Progress progress) {
        return plan(factionId, marketId, marketSize, rotation, hasLicenseAccess,
                unavailableCardIds, progress, List.of());
    }

    /**
     * As above, with the market's listed cards on a shelf of their own. Listings come first
     * and are additional: they never displace a rotating slot, and a listed card is left out
     * of the rotating draw so it never appears twice. A listing the company already knows or
     * carries is withdrawn, and one whose Restricted kit is no longer warranted is not shown.
     */
    static StockPlan plan(String factionId, String marketId, int marketSize,
                          long rotation, boolean hasLicenseAccess,
                          Set<String> unavailableCardIds,
                          EquipmentAcquisitionEligibility.Progress progress,
                          List<String> listedCardIds) {
        Set<String> unavailable = new HashSet<>(unavailableCardIds != null
                ? unavailableCardIds : Set.of());
        List<String> listed = new ArrayList<>();
        for (String cardId : listedCardIds != null ? listedCardIds : List.<String>of()) {
            LoadoutCard card = LoadoutCard.parse(cardId);
            if (card == null || unavailable.contains(card.id()) || listed.contains(card.id())) continue;
            if (!restrictedKitWarranted(card, progress)) continue;
            listed.add(card.id());
        }
        unavailable.addAll(listed);
        List<String> market = LoadoutCardPicker.pick(factionId,
                FactionEquipmentSource.MARKET, marketSlots(marketSize),
                seed(factionId, marketId, rotation, "market"), unavailable, progress);
        unavailable.addAll(market);

        List<String> licensed = hasLicenseAccess
                ? LoadoutCardPicker.pick(factionId,
                        FactionEquipmentSource.LICENSE, licenseSlots(marketSize),
                        seed(factionId, marketId, rotation, "license"),
                        unavailable, progress)
                : List.of();
        return new StockPlan(listed, market, licensed);
    }

    /**
     * The one mech pattern card slot of an open market, beside its loadout cards and on the
     * same monthly rotation. Only a market of size {@link #MIN_MECH_PATTERN_MARKET_SIZE} or
     * more, or one hosting a military base or high command, carries the slot. It draws through
     * the licence channel when the company holds a licence there (falling back to the ordinary
     * market channel when the faction licenses nothing), so its tier gate is the one a loadout card on that shelf meets.
     */
    static List<String> mechPattern(String factionId, String marketId, int marketSize,
                                    boolean militaryMarket, long rotation,
                                    boolean hasLicenseAccess, Set<String> unavailablePatternIds,
                                    EquipmentAcquisitionEligibility.Progress progress) {
        if (!carriesMechPattern(marketSize, militaryMarket)) return List.of();
        long seed = seed(factionId, marketId, rotation, "mech-pattern");
        List<String> licensed = hasLicenseAccess
                ? MechPatternPicker.pick(factionId, FactionEquipmentSource.LICENSE, 1, seed,
                        unavailablePatternIds, progress)
                : List.of();
        return !licensed.isEmpty() ? licensed
                : MechPatternPicker.pick(factionId, FactionEquipmentSource.MARKET, 1, seed,
                        unavailablePatternIds, progress);
    }

    /**
     * The boat part slots of an open market, on the same monthly rotation: one for a market of
     * size 3 or 4, two from size 5. Drawn through the licence channel when the company holds a
     * licence there, else the ordinary market channel, as the mech pattern slot is.
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

    static boolean carriesMechPattern(int marketSize, boolean militaryMarket) {
        return militaryMarket || marketSize >= MIN_MECH_PATTERN_MARKET_SIZE;
    }

    private static int marketSlots(int marketSize) {
        return Math.max(1, Math.min(MAX_MARKET_SLOTS, marketSize - 2));
    }

    private static int licenseSlots(int marketSize) {
        if (marketSize < 4) return 0;
        return Math.max(1, Math.min(MAX_LICENSE_SLOTS, marketSize - 4));
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

        StockPlan(List<String> marketCardIds, List<String> licensedCardIds) {
            this(List.of(), marketCardIds, licensedCardIds);
        }

        List<String> allCardIds() {
            Set<String> ordered = new LinkedHashSet<>(listedCardIds);
            ordered.addAll(marketCardIds);
            ordered.addAll(licensedCardIds);
            return List.copyOf(ordered);
        }
    }
}
