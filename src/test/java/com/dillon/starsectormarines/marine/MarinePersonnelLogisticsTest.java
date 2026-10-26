package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.dillon.starsectormarines.ops.BarracksViewModel;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarinePersonnelLogisticsTest {

    @Test
    void barracksBulkReplacementsStopAtAvailableCargoAndKeepWoundedBillets() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(2 * MarineSquad.CAPACITY);
        var squads = roster.squads().stream().filter(s -> !s.reserve()).toList();
        String wounded = squads.get(1).memberIds().get(1);
        roster.applySoldierOutcome(Map.of(squads.get(0).memberIds().get(0), MarineSoldierStatus.KIA,
                squads.get(1).memberIds().get(0), MarineSoldierStatus.KIA,
                squads.get(1).memberIds().get(2), MarineSoldierStatus.KIA,
                wounded, MarineSoldierStatus.WIA), 20f, 3f);
        var reserve = roster.recruitToSquad(roster.reserveSquad().id());
        float[] quantity = {1f};
        CargoAPI cargo = cargo(quantity);
        var model = new BarracksViewModel(new Reactor(), roster, () -> 20d,
                EquipmentIssueResources.UNLIMITED, () -> (int) quantity[0],
                id -> MarinePersonnelLogistics.reinforceSquad(roster, id, cargo)).command();
        squads.forEach(s -> model.toggle(s.id()));
        model.reinforceSelected();
        assertEquals(0f, quantity[0]);
        assertTrue(squads.get(0).memberIds().contains(reserve.id()));
        assertEquals(0, roster.vacancies(squads.get(0)));
        assertEquals(1, roster.vacancies(squads.get(1)));
        assertEquals(MarineSoldierStatus.WIA, roster.soldierById(wounded).status());
        assertTrue(squads.get(1).memberIds().contains(wounded));
    }

    @Test
    void drawingPersonnelConsumesOneCargoMarineAndReturningReserveRestoresIt() {
        float[] quantity = {2f};
        CargoAPI cargo = cargo(quantity);
        MarineRoster roster = new MarineRoster();
        MarineSquad reserve = roster.reserveSquad();

        MarineSoldier replacement = MarinePersonnelLogistics.drawCargoMarineIntoSquad(
                roster, reserve.id(), cargo);

        assertNotNull(replacement);
        assertEquals(1f, quantity[0]);
        assertEquals(1, roster.soldiers().size());
        assertTrue(MarinePersonnelLogistics.returnReserveToCargo(
                roster, replacement.id(), cargo));
        assertEquals(2f, quantity[0]);
        assertEquals(0, roster.soldiers().size());
    }

    @Test
    void failedPersonnelDrawAndInvalidReturnDoNotMutateCargo() {
        float[] quantity = {0f};
        CargoAPI cargo = cargo(quantity);
        MarineRoster roster = new MarineRoster();
        MarineSquad reserve = roster.reserveSquad();

        assertEquals(null, MarinePersonnelLogistics.drawCargoMarineIntoSquad(
                roster, reserve.id(), cargo));
        assertFalse(MarinePersonnelLogistics.returnReserveToCargo(
                roster, "missing", cargo));
        assertEquals(0f, quantity[0]);
    }

    @Test
    void lineShortfallDrawsDirectlyFromCargoAndStopsAtAvailableQuantity() {
        float[] quantity = {3f};
        CargoAPI cargo = cargo(quantity);
        MarineRoster roster = new MarineRoster();
        MarineSquad reserve = roster.reserveSquad();
        roster.createSquad();

        MarinePersonnelLogistics.PersonnelDrawResult result =
                MarinePersonnelLogistics.fillLineShortfall(roster, 5, cargo);
        assertEquals(3, result.cargoMarinesConsumed());
        assertEquals(0f, quantity[0]);
        assertEquals(3, roster.lineReadySoldiers().size());
        assertTrue(roster.squadMembers(reserve).isEmpty());
    }

    @Test
    void aWipedSquadRefillsWithoutFoundingAnother() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        MarineSquad line = roster.squads().stream().filter(squad -> !squad.reserve()).findFirst().orElseThrow();
        Map<String, MarineSoldierStatus> losses = new LinkedHashMap<>();
        for (String id : line.memberIds()) losses.put(id, MarineSoldierStatus.KIA);
        roster.applySoldierOutcome(losses, 20f, 3f);
        float[] quantity = {MarineSquad.CAPACITY + 5f};

        MarinePersonnelLogistics.PersonnelDrawResult result = MarinePersonnelLogistics.fillLineShortfall(
                roster, MarineSquad.CAPACITY + 5, cargo(quantity));

        assertEquals(MarineSquad.CAPACITY, result.cargoMarinesConsumed());
        assertEquals(0, roster.vacancies(line));
        assertEquals(1, roster.squads().stream().filter(squad -> !squad.reserve()).count(),
                "a refill never founds a squad");
        assertEquals(5f, quantity[0]);
    }

    @Test
    void oneClickReinforcementUsesReserveThenCargoAndRebuildsCurrentBillets() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        MarineSquad line = roster.squads().get(0);
        Map<String, MarineSoldierStatus> losses = new LinkedHashMap<>();
        losses.put(line.memberIds().get(0), MarineSoldierStatus.KIA);
        losses.put(line.memberIds().get(4), MarineSoldierStatus.MIA);
        roster.applySoldierOutcome(losses, 20f, 3f);
        MarineSoldier reserve = roster.recruitToSquad(roster.reserveSquad().id());
        float[] quantity = {1f};

        MarinePersonnelLogistics.PersonnelDrawResult result =
                MarinePersonnelLogistics.reinforceSquad(roster, line.id(), cargo(quantity));

        assertEquals(1, result.reservesAssigned());
        assertEquals(1, result.cargoMarinesConsumed());
        assertEquals(0, roster.vacancies(line));
        assertEquals(0f, quantity[0]);
        assertTrue(roster.teamMemberIds(line, 2).contains(reserve.id()),
                "replacement personnel occupy current fire-team billets");
        assertFalse(roster.manningMemberIds(line).contains(line.memberIds().get(0)));
    }

    @Test
    void lineShortfallUsesNamedReservesBeforeConsumingCargo() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY - 3);
        MarineSquad reserve = roster.reserveSquad();
        MarineSoldier namedReserve = roster.recruitToSquad(reserve.id());
        float[] quantity = {2f};

        MarinePersonnelLogistics.PersonnelDrawResult result =
                MarinePersonnelLogistics.fillLineShortfall(roster, 3, cargo(quantity));

        assertEquals(1, result.reservesAssigned());
        assertEquals(2, result.cargoMarinesConsumed());
        assertEquals(0f, quantity[0]);
        assertTrue(roster.lineReadySoldiers().contains(namedReserve));
        assertTrue(roster.squadMembers(reserve).isEmpty());
    }

    private static CargoAPI cargo(float[] quantity) {
        return (CargoAPI) Proxy.newProxyInstance(CargoAPI.class.getClassLoader(),
                new Class<?>[]{CargoAPI.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getCommodityQuantity": return quantity[0];
                        case "removeCommodity": quantity[0] -= ((Number) args[1]).floatValue(); return null;
                        case "addCommodity": quantity[0] += ((Number) args[1]).floatValue(); return null;
                        default:
                            Class<?> type = method.getReturnType();
                            if (type == boolean.class) return false;
                            if (type == int.class) return 0;
                            if (type == float.class) return 0f;
                            if (type == double.class) return 0d;
                            if (type == long.class) return 0L;
                            return null;
                    }
                });
    }
}
