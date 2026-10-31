package com.dillon.starsectormarines.marine;

import java.util.Map;
import java.util.Set;

/**
 * The Kit Ladder: the station after which the company store sells each loadout card, machine pattern and boat part
 * ({@code progression-nouns.md}, Kit Ladder). The placements are data ({@code data/marines/kit-ladder.json}, loaded by
 * {@link KitLadderCatalog}), authored by the owner on the Kit Ladder page and written back by
 * {@code tools/kits/apply_ladder.py} ({@code kit-ladder.md}).
 *
 * <p>The ladder gates every channel that hands out a card or pattern: the store, patron rewards and recovery
 * ({@link EquipmentAcquisitionEligibility}). A {@link UnlockStation#START} card or pattern is what a new company holds
 * ({@link MechPattern#STARTER}, the armory's starter issue); a boat part there is on sale from the founding, since the
 * boat starter set ({@link BoatParts#starterIds}) is never placed. {@link UnlockStation#NONE} placements are never
 * handed out by any channel. An item with no placement (a submod's card, an id an old save still carries) is not
 * either. Every machine pattern and sellable boat part must be placed ({@link KitLadderCatalog}); a loadout card may
 * be left out.
 */
public final class KitLadder {

    private KitLadder() {}

    /**
     * The station of a loadout card id, mech pattern id or boat part ladder id ({@link BoatParts#ladderId});
     * {@link UnlockStation#NONE} for one with no placement.
     */
    public static UnlockStation station(String id) {
        UnlockStation station = id != null ? KitLadderCatalog.require().placements().get(id) : null;
        return station != null ? station : UnlockStation.NONE;
    }

    /** Whether {@code id} may be handed out to a company that has passed {@code reached}. */
    public static boolean forSale(String id, Set<UnlockStation> reached) {
        UnlockStation station = station(id);
        return !station.equals(UnlockStation.NONE) && reached != null && reached.contains(station);
    }

    /** Every placement, card, pattern and boat part ids alike, in authored order. */
    public static Map<String, UnlockStation> stations() {
        return KitLadderCatalog.require().placements();
    }
}
