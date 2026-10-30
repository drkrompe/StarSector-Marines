package com.dillon.starsectormarines.marine;

import java.util.Map;
import java.util.Set;

/**
 * The Kit Ladder: the station after which the company store sells each loadout card and machine pattern
 * ({@code progression-nouns.md}, Kit Ladder). The placements are data ({@code data/marines/kit-ladder.json}, loaded by
 * {@link KitLadderCatalog}), authored by the owner on the Kit Ladder page and written back by
 * {@code tools/kits/apply_ladder.py} ({@code kit-ladder.md}).
 *
 * <p>The ladder gates only the store's channels (open market and licence). {@link UnlockStation#START} placements are
 * what a new company holds ({@link MechPattern#STARTER}, the armory's starter issue); {@link UnlockStation#NONE}
 * placements are never sold, though patrons and recovery may still hand them out by tier. An item with no placement
 * (a submod's card, an id an old save still carries) is not sold.
 */
public final class KitLadder {

    private KitLadder() {}

    /** The station of a loadout card id or mech pattern id; {@link UnlockStation#NONE} for one with no placement. */
    public static UnlockStation station(String id) {
        UnlockStation station = id != null ? KitLadderCatalog.require().placements().get(id) : null;
        return station != null ? station : UnlockStation.NONE;
    }

    /** Whether the store may sell {@code id} to a company that has passed {@code reached}. */
    public static boolean forSale(String id, Set<UnlockStation> reached) {
        UnlockStation station = station(id);
        return !station.equals(UnlockStation.NONE) && reached != null && reached.contains(station);
    }

    /** Every placement, card and pattern ids alike, in authored order. */
    public static Map<String, UnlockStation> stations() {
        return KitLadderCatalog.require().placements();
    }
}
