package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.gen.GenKey;

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

    /** Fore-most column of each athwartships corridor, ascending. Produced by {@code TransverseCorridorStage}; read by the compartment carve. */
    public static final GenKey<int[]> CORRIDOR_FRAMES = GenKey.of("corridorFrames");

    /** Carved compartments and the corridors dividing them. Produced by {@code CompartmentCarveStage}. */
    public static final GenKey<DeckGraph> DECK_GRAPH = GenKey.of("deckGraph");
}
