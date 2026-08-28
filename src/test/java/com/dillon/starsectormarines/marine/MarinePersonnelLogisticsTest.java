package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.campaign.CargoAPI;
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
        float[] quantity = {MarineSquad.CAPACITY + 2f};
        CargoAPI cargo = cargo(quantity);
        MarineRoster roster = new MarineRoster();
        MarineSquad reserve = roster.reserveSquad();

        int firstDraft = MarineSquad.CAPACITY + 1;
        MarinePersonnelLogistics.PersonnelDrawResult first =
                MarinePersonnelLogistics.fillLineShortfall(roster, firstDraft, cargo);
        assertEquals(firstDraft, first.cargoMarinesConsumed());
        assertEquals(1f, quantity[0]);
        assertEquals(firstDraft, roster.lineReadySoldiers().size());
        assertTrue(roster.squadMembers(reserve).isEmpty());

        MarinePersonnelLogistics.PersonnelDrawResult second =
                MarinePersonnelLogistics.fillLineShortfall(roster, 5, cargo);
        assertEquals(1, second.cargoMarinesConsumed());
        assertEquals(0f, quantity[0]);
        assertEquals(MarineSquad.CAPACITY + 2, roster.lineReadySoldiers().size());
        assertEquals(2, roster.squads().stream().filter(squad -> !squad.reserve()).count());
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
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
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
