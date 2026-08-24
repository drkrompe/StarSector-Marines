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

    /**
     * Fills every true open billet in one squad, drawing ready reserves first and then consuming
     * generic cargo marines. WIA personnel still hold their billets, so this never overmans a
     * temporarily degraded squad.
     */
    public static ReinforcementResult reinforceSquad(MarineRoster roster, String squadId) {
        return reinforceSquad(roster, squadId, playerCargo());
    }

    static ReinforcementResult reinforceSquad(
            MarineRoster roster, String squadId, CargoAPI cargo) {
        if (roster == null || roster.squadById(squadId) == null) {
            return new ReinforcementResult(0, 0);
        }
        int transferred = roster.fillVacanciesFromReserve(squadId);
        int enlisted = 0;
        while (roster.vacancies(roster.squadById(squadId)) > 0
                && enlist(roster, squadId, cargo) != null) {
            enlisted++;
        }
        return new ReinforcementResult(transferred, enlisted);
    }

    /** Bulk-enlists line personnel, bounded by the request and real cargo. */
    public static int enlistLine(MarineRoster roster, int requested) {
        return enlistLine(roster, requested, playerCargo());
    }

    static int enlistLine(MarineRoster roster, int requested, CargoAPI cargo) {
        if (roster == null || cargo == null || requested <= 0) return 0;
        int count = Math.min(requested, availableRecruits(cargo));
        int enlisted = 0;
        while (enlisted < count) {
            MarineSoldier recruit = roster.enlistLineRecruit();
            if (recruit == null) break;
            cargo.removeCommodity(Commodities.MARINES, 1f);
            enlisted++;
        }
        return enlisted;
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

    public record ReinforcementResult(int transferred, int enlisted) {
        public int total() { return transferred + enlisted; }
    }
}
