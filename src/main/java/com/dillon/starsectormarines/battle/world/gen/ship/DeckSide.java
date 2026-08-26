package com.dillon.starsectormarines.battle.world.gen.ship;

/**
 * Which side of the spine a compartment sits on. The deck is drawn bow-left, so
 * facing forward puts {@link #PORT} at smaller y and {@link #STARBOARD} at
 * larger y.
 *
 * <p>Side is an authored fact on the deck graph, not something a consumer
 * recovers by comparing a cell's y against the spine.
 */
public enum DeckSide {
    /** Above the spine in cell coordinates. */
    PORT,
    /** Below the spine in cell coordinates. */
    STARBOARD
}
