package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/**
 * Shared state for the marine ops screens — planet, market, texture path, and
 * (eventually) the player's current selections as they click through clients
 * and missions. Threaded into every {@link OpsPanel} via {@link OpsPanel#attach}.
 *
 * <p>Single source of truth means panels don't reach into
 * {@code dialog.getInteractionTarget()} directly, and future screens can read
 * the same state without re-resolving it.
 */
public class MarineOpsContext {

    public final PlanetAPI planet;
    public final MarketAPI market;
    public final String planetTexture;

    public MarineOpsContext(PlanetAPI planet) {
        this.planet = planet;
        MarketAPI m = null;
        String tex = null;
        if (planet != null) {
            m = planet.getMarket();
            if (planet.getSpec() != null) {
                tex = planet.getSpec().getTexture();
            }
        }
        this.market = m;
        this.planetTexture = tex;
    }
}
