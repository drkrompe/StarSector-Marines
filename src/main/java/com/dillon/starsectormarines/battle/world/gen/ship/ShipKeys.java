package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.world.gen.GenKey;

import java.util.List;

/**
 * {@link GenKey} declarations for the ship-deck family's own overlays, kept
 * separate from the BSP city/station keys exactly as that holder anticipated.
 * Shared pipeline products such as spawns, buildings, and the tactical map stay
 * on their existing keys, because the stages that publish them are shared.
 */
public final class ShipKeys {

    private ShipKeys() {}

    /** The deck's longitudinal shape, spine rows, and per-frame zones. Produced by {@code HullProfileStage}; read by every later ship stage. */
    public static final GenKey<DeckProfile> DECK_PROFILE = GenKey.of("deckProfile");

    /** The rooms this deck owes, largest first is not assumed. Supplied by the caller; read by {@code RoomPlacementStage}. */
    public static final GenKey<List<RoomRecipe>> ROOM_PROGRAM = GenKey.of("roomProgram");
    /**
     * What this hull keeps in her bays, so the deck that lays the bays can
     * publish what is in them. Travels the same way the room program does,
     * because it is the same kind of fact about the same hull.
     */
    public static final GenKey<ShuttleType> SHIPS_BOATS = GenKey.of("shipsBoats");

    /** Placed compartments and the rooms that could not be fitted. Produced by {@code RoomPlacementStage}. */
    public static final GenKey<DeckGraph> DECK_GRAPH = GenKey.of("deckGraph");
}
