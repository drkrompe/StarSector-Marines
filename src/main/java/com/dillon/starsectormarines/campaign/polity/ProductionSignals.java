package com.dillon.starsectormarines.campaign.polity;

/**
 * Where the polity's {@link GroundProductionQuality} is read from.
 *
 * <p>One method, because the derivation asks one question: what is the best step any
 * of the polity's own markets can reach? The rule that answers it is pure and lives
 * on {@link GroundProductionQuality}; this is only the seam that decides whether the
 * booleans come from a live sector ({@link VanillaProductionSignals}) or from a
 * stated set ({@link MarketProductionSignals#of}).
 */
public interface ProductionSignals {

    /** The best step the polity's markets reach. {@link GroundProductionQuality#NONE} with no markets. */
    GroundProductionQuality bestQuality();
}
