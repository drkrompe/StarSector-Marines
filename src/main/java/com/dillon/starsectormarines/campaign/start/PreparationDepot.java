package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.BoatFabricationCatalog;
import com.dillon.starsectormarines.marine.SquadFoundingCost;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** A finite prepaid consignment, held at one planet rather than in the ship's hold. */
public final class PreparationDepot implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String marketId;
    private final Map<String, Integer> stock = new LinkedHashMap<>();

    public PreparationDepot(String marketId) {
        this.marketId = marketId;
        for (var line : BoatFabricationCatalog.recipe(ShuttleType.HERMES).bill().lines())
            stock.merge(line.commodityId(), line.quantity(), Integer::sum);
        for (var line : SquadFoundingCost.STANDARD.lines())
            stock.merge(line.commodityId(), line.quantity(), Integer::sum);
    }
    public String marketId() { return marketId; }
    public int available(String commodity) { return stock.getOrDefault(commodity, 0); }
    public Map<String, Integer> stock() { return Map.copyOf(stock); }

    /** Caller first validates the whole combined depot/cargo bill. */
    public int draw(String commodity, int requested) {
        int drawn = Math.min(Math.max(0, requested), available(commodity));
        stock.put(commodity, available(commodity) - drawn);
        return drawn;
    }
}
