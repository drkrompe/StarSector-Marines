package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.impl.campaign.ids.Commodities;

/**
 * Installed tools waste less: a workshop job spends a fifth fewer supplies and heavy machinery, rounded
 * up, without changing recipes or any other material. Machinery counts because the Mech Lab spends no
 * supplies; it is what wears out on a bench there.
 */
public final class WorkshopTooling {
    private WorkshopTooling() { }
    public static FabricationCost quote(FabricationCost base, boolean equipped) {
        if (base == null || !equipped) return base;
        return new FabricationCost(base.lines().stream().map(line ->
                Commodities.SUPPLIES.equals(line.commodityId()) || Commodities.HEAVY_MACHINERY.equals(line.commodityId())
                        ? new FabricationCost.Line(line.commodityId(),
                        Math.max(1, (int) ((line.quantity() * 4L + 4) / 5))) : line).toList());
    }
}
