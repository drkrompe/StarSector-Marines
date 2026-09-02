package com.dillon.starsectormarines.campaign.polity;

import java.util.Collection;
import java.util.List;

/**
 * One market's four ground-production facts, and the pure rule for reading a whole
 * polity off a set of them.
 *
 * <p>This is what the live adapter measures a market down to, so the arithmetic that
 * matters — which step a market sits at, and which market is the polity's best — is
 * testable without a sector. The step rule itself belongs to
 * {@link GroundProductionQuality}; this only carries the inputs and takes the best.
 *
 * @param heavyIndustry         the market runs Heavy Industry
 * @param orbitalWorks          the market runs Orbital Works
 * @param suppliesDeficit       the market is short of supplies
 * @param heavyArmamentsDeficit the market is short of hand weapons
 */
public record MarketProductionSignals(boolean heavyIndustry, boolean orbitalWorks,
                                      boolean suppliesDeficit, boolean heavyArmamentsDeficit) {

    public static MarketProductionSignals of(boolean heavyIndustry, boolean orbitalWorks,
                                             boolean suppliesDeficit,
                                             boolean heavyArmamentsDeficit) {
        return new MarketProductionSignals(heavyIndustry, orbitalWorks,
                suppliesDeficit, heavyArmamentsDeficit);
    }

    /** The step this one market sits at. */
    public GroundProductionQuality quality() {
        return GroundProductionQuality.of(heavyIndustry, orbitalWorks,
                suppliesDeficit, heavyArmamentsDeficit);
    }

    /**
     * The best step reached by any market in {@code markets}, the way vanilla reads
     * ship quality off a faction's best producing market rather than off an average.
     * An empty or absent set is {@link GroundProductionQuality#NONE}: a polity with no
     * colonies makes nothing.
     */
    public static GroundProductionQuality bestQuality(
            Collection<MarketProductionSignals> markets) {
        GroundProductionQuality best = GroundProductionQuality.NONE;
        if (markets == null) return best;
        for (MarketProductionSignals market : markets) {
            if (market == null) continue;
            GroundProductionQuality step = market.quality();
            if (step.ordinal() > best.ordinal()) best = step;
        }
        return best;
    }

    /** The stated set as a {@link ProductionSignals} — the pure counterpart of the live adapter. */
    public static ProductionSignals signals(MarketProductionSignals... markets) {
        List<MarketProductionSignals> stated = List.of(markets);
        return () -> bestQuality(stated);
    }
}
