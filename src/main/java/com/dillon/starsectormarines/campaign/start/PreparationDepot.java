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
    // Null on legacy saves: their remaining stock keeps its original unrestricted policy.
    private Map<String, Integer> boatStock;
    private Map<String, Integer> squadStock;
    public enum Purpose { GENERAL, BOAT, SQUAD, PREFLIGHT }

    public PreparationDepot(String marketId) {
        this(marketId, false);
    }
    public PreparationDepot(String marketId, boolean protectedStock) {
        this.marketId = marketId;
        if (protectedStock) { boatStock = new LinkedHashMap<>(); squadStock = new LinkedHashMap<>(); }
        for (var line : BoatFabricationCatalog.recipe(ShuttleType.HERMES).bill().lines())
            (protectedStock ? boatStock : stock).merge(line.commodityId(), line.quantity(), Integer::sum);
        for (var line : SquadFoundingCost.STANDARD.lines())
            (protectedStock ? squadStock : stock).merge(line.commodityId(), line.quantity(), Integer::sum);
    }
    public String marketId() { return marketId; }
    public boolean protectedStock() { return boatStock != null && squadStock != null; }
    public int available(String commodity) { return stock().getOrDefault(commodity, 0); }
    public Map<String, Integer> stock() {
        if (!protectedStock()) return Map.copyOf(stock);
        var total = new LinkedHashMap<>(boatStock);
        squadStock.forEach((id, quantity) -> total.merge(id, quantity, Integer::sum));
        return Map.copyOf(total);
    }
    private Map<String, Integer> stockFor(Purpose purpose) {
        if (!protectedStock()) return stock;
        return switch (purpose) {
            case BOAT -> boatStock;
            case SQUAD -> squadStock;
            case GENERAL, PREFLIGHT -> Map.of();
        };
    }
    public int available(String commodity, Purpose purpose) {
        return purpose == Purpose.PREFLIGHT ? available(commodity) : stockFor(purpose).getOrDefault(commodity, 0);
    }

    /** Caller first validates the whole combined depot/cargo bill. */
    public int draw(String commodity, int requested) {
        return draw(commodity, requested, Purpose.GENERAL);
    }
    public int draw(String commodity, int requested, Purpose purpose) {
        if (purpose == Purpose.PREFLIGHT) return 0;
        var source = stockFor(purpose);
        int drawn = Math.min(Math.max(0, requested), source.getOrDefault(commodity, 0));
        if (drawn > 0) source.put(commodity, source.get(commodity) - drawn);
        return drawn;
    }
}
