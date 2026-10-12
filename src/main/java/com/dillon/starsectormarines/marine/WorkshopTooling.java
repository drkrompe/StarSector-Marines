package com.dillon.starsectormarines.marine;

/** Installed tools save consumables without changing recipes or other material inputs. */
public final class WorkshopTooling {
    private WorkshopTooling() { }
    public static FabricationCost quote(FabricationCost base, boolean equipped) {
        if (base == null || !equipped) return base;
        return new FabricationCost(base.lines().stream().map(line ->
                "supplies".equals(line.commodityId())
                        ? new FabricationCost.Line(line.commodityId(),
                        Math.max(1, (int) ((line.quantity() * 4L + 4) / 5))) : line).toList());
    }
}
