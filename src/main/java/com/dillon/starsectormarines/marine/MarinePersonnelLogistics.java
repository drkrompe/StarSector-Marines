package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.refit.ShipRefits;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

/** Transactional bridge between fleet cargo personnel and named persistent billets. */
public final class MarinePersonnelLogistics {

    private MarinePersonnelLogistics() {}

    public static int availableCargoMarines() {
        return availableCargoMarines(playerCargo());
    }

    static int availableCargoMarines(CargoAPI cargo) {
        return cargo != null ? Math.max(0,
                (int) Math.floor(cargo.getCommodityQuantity(Commodities.MARINES))) : 0;
    }

    /** Fills one squad billet and then removes the supplying marine from fleet cargo. */
    public static MarineSoldier drawCargoMarineIntoSquad(MarineRoster roster, String squadId) {
        return drawCargoMarineIntoSquad(roster, squadId, playerCargo(), intakeAboard());
    }

    static MarineSoldier drawCargoMarineIntoSquad(
            MarineRoster roster, String squadId, CargoAPI cargo) {
        return drawCargoMarineIntoSquad(roster, squadId, cargo, RecruitIntake.NEUTRAL);
    }

    static MarineSoldier drawCargoMarineIntoSquad(
            MarineRoster roster, String squadId, CargoAPI cargo, RecruitIntake intake) {
        if (roster == null || cargo == null
                || cargo.getCommodityQuantity(Commodities.MARINES) < 1f) return null;
        MarineSoldier replacement = roster.recruitToSquad(squadId, intake);
        if (replacement == null) return null;
        cargo.removeCommodity(Commodities.MARINES, 1f);
        return replacement;
    }

    /**
     * Fills every true open billet in one squad, drawing ready reserves first and then consuming
     * generic cargo marines. WIA personnel still hold their billets, so this never overmans a
     * temporarily degraded squad.
     */
    public static PersonnelDrawResult reinforceSquad(MarineRoster roster, String squadId) {
        return reinforceSquad(roster, squadId, playerCargo(), intakeAboard());
    }

    static PersonnelDrawResult reinforceSquad(
            MarineRoster roster, String squadId, CargoAPI cargo) {
        return reinforceSquad(roster, squadId, cargo, RecruitIntake.NEUTRAL);
    }

    static PersonnelDrawResult reinforceSquad(
            MarineRoster roster, String squadId, CargoAPI cargo, RecruitIntake intake) {
        if (roster == null || roster.squadById(squadId) == null) {
            return new PersonnelDrawResult(0, 0);
        }
        int reservesAssigned = roster.fillVacanciesFromReserve(squadId);
        int cargoMarinesConsumed = 0;
        while (roster.vacancies(roster.squadById(squadId)) > 0
                && drawCargoMarineIntoSquad(roster, squadId, cargo, intake) != null) {
            cargoMarinesConsumed++;
        }
        return new PersonnelDrawResult(reservesAssigned, cargoMarinesConsumed);
    }

    /**
     * Fills a company-ready shortfall directly from named reserves and then fleet cargo.
     * Cargo is removed only after the roster accepts each corresponding billet holder.
     */
    public static PersonnelDrawResult fillLineShortfall(MarineRoster roster, int requested) {
        return fillLineShortfall(roster, requested, playerCargo(), intakeAboard());
    }

    static PersonnelDrawResult fillLineShortfall(
            MarineRoster roster, int requested, CargoAPI cargo) {
        return fillLineShortfall(roster, requested, cargo, RecruitIntake.NEUTRAL);
    }

    static PersonnelDrawResult fillLineShortfall(
            MarineRoster roster, int requested, CargoAPI cargo, RecruitIntake intake) {
        if (roster == null || requested <= 0) return new PersonnelDrawResult(0, 0);
        int reservesAssigned = 0;
        while (reservesAssigned < requested && roster.assignReadyReserveToLine()) {
            reservesAssigned++;
        }

        int cargoMarinesConsumed = 0;
        int cargoLimit = Math.min(requested - reservesAssigned, availableCargoMarines(cargo));
        while (cargoMarinesConsumed < cargoLimit) {
            MarineSoldier replacement = roster.createLineReplacement(intake);
            if (replacement == null) break;
            cargo.removeCommodity(Commodities.MARINES, 1f);
            cargoMarinesConsumed++;
        }
        return new PersonnelDrawResult(reservesAssigned, cargoMarinesConsumed);
    }

    /** Returns one ready reserve marine to fleet cargo. */
    public static boolean returnReserveToCargo(MarineRoster roster, String soldierId) {
        return returnReserveToCargo(roster, soldierId, playerCargo());
    }

    static boolean returnReserveToCargo(
            MarineRoster roster, String soldierId, CargoAPI cargo) {
        if (roster == null || cargo == null || !roster.releaseReserveSoldier(soldierId)) {
            return false;
        }
        cargo.addCommodity(Commodities.MARINES, 1f);
        return true;
    }

    /** The current company home's intake: the founding hull's lounge, neutral anywhere else. */
    private static RecruitIntake intakeAboard() {
        return ShipRefits.aboard().recruitIntake();
    }

    private static CargoAPI playerCargo() {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return null;
        return Global.getSector().getPlayerFleet().getCargo();
    }

    public record PersonnelDrawResult(int reservesAssigned, int cargoMarinesConsumed) {
        public int total() { return reservesAssigned + cargoMarinesConsumed; }
    }
}
