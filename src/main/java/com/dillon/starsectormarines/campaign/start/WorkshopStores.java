package com.dillon.starsectormarines.campaign.start;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.dillon.starsectormarines.campaign.start.PreparationDepot.Purpose;

import java.util.Map;

/** Draws local prepaid stores and yard credit first, preserving the ship's travelling reserves. */
public final class WorkshopStores {
    private final CargoAPI cargo;
    private final PreparationDepot depot;
    private final Purpose purpose;

    public WorkshopStores(CargoAPI cargo, PreparationDepot depot) {
        this(cargo, depot, Purpose.GENERAL);
    }
    public WorkshopStores(CargoAPI cargo, PreparationDepot depot, Purpose purpose) {
        this.cargo = cargo;
        this.depot = depot;
        this.purpose = purpose;
    }

    public static WorkshopStores current() {
        return current(Purpose.GENERAL);
    }
    public static WorkshopStores current(Purpose purpose) {
        var sector = Global.getSector();
        CargoAPI cargo = sector != null && sector.getPlayerFleet() != null
                ? sector.getPlayerFleet().getCargo() : null;
        return new WorkshopStores(cargo, CerberusCompanyStart.accessibleDepot(), purpose);
    }

    public int available(String commodity) {
        int aboard = cargo == null ? 0 : Math.max(0, (int) Math.floor(cargo.getCommodityQuantity(commodity)));
        return aboard + (depot == null ? 0 : depot.available(commodity, purpose));
    }

    public boolean spend(Map<String, Integer> bill) {
        if (purpose == Purpose.PREFLIGHT) return false;
        if (bill.entrySet().stream().anyMatch(e -> e.getValue() < 0 || available(e.getKey()) < e.getValue()))
            return false;
        for (var line : bill.entrySet()) {
            int local = depot == null ? 0 : depot.draw(line.getKey(), line.getValue(), purpose);
            int aboard = line.getValue() - local;
            if (aboard > 0) cargo.removeCommodity(line.getKey(), aboard);
        }
        return true;
    }

    /** The company's credits plus any yard credit this job may draw. */
    public long credits() {
        long aboard = cargo == null || cargo.getCredits() == null ? 0 : Math.max(0, (long) Math.floor(cargo.getCredits().get()));
        return aboard + (depot == null ? 0 : depot.credits(purpose));
    }

    /** Pays {@code price} from yard credit first, then the purse, or pays nothing. */
    public boolean spendCredits(int price) {
        if (purpose == Purpose.PREFLIGHT || price < 0 || credits() < price) return false;
        int local = depot == null ? 0 : depot.drawCredits(price, purpose);
        if (price - local > 0) cargo.getCredits().subtract(price - local);
        return true;
    }

    /** Recycled credits go to the purse, never into a depot the company may leave behind. */
    public boolean refund(int credits) {
        if (cargo == null || cargo.getCredits() == null) return false;
        if (credits > 0) cargo.getCredits().add(credits);
        return true;
    }
}
