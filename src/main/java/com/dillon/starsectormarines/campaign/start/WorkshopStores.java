package com.dillon.starsectormarines.campaign.start;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;

import java.util.Map;

/** Draws local prepaid stores first, preserving the ship's travelling reserves. */
public final class WorkshopStores {
    private final CargoAPI cargo;
    private final PreparationDepot depot;

    public WorkshopStores(CargoAPI cargo, PreparationDepot depot) {
        this.cargo = cargo;
        this.depot = depot;
    }

    public static WorkshopStores current() {
        var sector = Global.getSector();
        CargoAPI cargo = sector != null && sector.getPlayerFleet() != null
                ? sector.getPlayerFleet().getCargo() : null;
        return new WorkshopStores(cargo, CerberusCompanyStart.accessibleDepot());
    }

    public int available(String commodity) {
        int aboard = cargo == null ? 0 : Math.max(0, (int) Math.floor(cargo.getCommodityQuantity(commodity)));
        return aboard + (depot == null ? 0 : depot.available(commodity));
    }

    public boolean spend(Map<String, Integer> bill) {
        if (bill.entrySet().stream().anyMatch(e -> e.getValue() < 0 || available(e.getKey()) < e.getValue()))
            return false;
        for (var line : bill.entrySet()) {
            int local = depot == null ? 0 : depot.draw(line.getKey(), line.getValue());
            int aboard = line.getValue() - local;
            if (aboard > 0) cargo.removeCommodity(line.getKey(), aboard);
        }
        return true;
    }
}
