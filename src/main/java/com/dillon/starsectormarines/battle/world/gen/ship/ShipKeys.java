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
}
