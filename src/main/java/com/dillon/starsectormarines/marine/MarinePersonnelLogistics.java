package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

/** Transactional bridge between cargo's generic marines and named persistent personnel. */
public final class MarinePersonnelLogistics {

    private MarinePersonnelLogistics() {}

    public static int availableRecruits() {
        return availableRecruits(playerCargo());
    }

    static int availableRecruits(CargoAPI cargo) {
        return cargo != null ? Math.max(0,
                (int) Math.floor(cargo.getCommodityQuantity(Commodities.MARINES))) : 0;
    }

    /** Consumes one cargo marine only after the roster accepts the enlistment. */
    public static MarineSoldier enlist(MarineRoster roster, String squadId) {
        return enlist(roster, squadId, playerCargo());
    }

    static MarineSoldier enlist(MarineRoster roster, String squadId, CargoAPI cargo) {
        if (roster == null || cargo == null
                || cargo.getCommodityQuantity(Commodities.MARINES) < 1f) return null;
        MarineSoldier recruit = roster.recruitToSquad(squadId);
        if (recruit == null) return null;
        cargo.removeCommodity(Commodities.MARINES, 1f);
        return recruit;
    }

    /** Returns one ready reserve marine to the generic cargo pool. */
    public static boolean release(MarineRoster roster, String soldierId) {
        return release(roster, soldierId, playerCargo());
    }

    static boolean release(MarineRoster roster, String soldierId, CargoAPI cargo) {
        if (roster == null || cargo == null || !roster.releaseReserveSoldier(soldierId)) {
            return false;
        }
        cargo.addCommodity(Commodities.MARINES, 1f);
        return true;
    }

    private static CargoAPI playerCargo() {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return null;
        return Global.getSector().getPlayerFleet().getCargo();
    }
}
