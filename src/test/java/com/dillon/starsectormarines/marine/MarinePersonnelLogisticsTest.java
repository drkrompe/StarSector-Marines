package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.campaign.CargoAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarinePersonnelLogisticsTest {

    @Test
    void enlistmentConsumesOneCargoMarineAndDemobilizationReturnsIt() {
        float[] quantity = {2f};
        CargoAPI cargo = cargo(quantity);
        MarineRoster roster = new MarineRoster();
        MarineSquad reserve = roster.reserveSquad();

        MarineSoldier enlisted = MarinePersonnelLogistics.enlist(roster, reserve.id(), cargo);

        assertNotNull(enlisted);
        assertEquals(1f, quantity[0]);
        assertEquals(1, roster.soldiers().size());
        assertTrue(MarinePersonnelLogistics.release(roster, enlisted.id(), cargo));
        assertEquals(2f, quantity[0]);
        assertEquals(0, roster.soldiers().size());
    }

    @Test
    void failedEnlistmentAndInvalidReleaseDoNotMutateCargo() {
        float[] quantity = {0f};
        CargoAPI cargo = cargo(quantity);
        MarineRoster roster = new MarineRoster();
        MarineSquad reserve = roster.reserveSquad();

        assertEquals(null, MarinePersonnelLogistics.enlist(roster, reserve.id(), cargo));
        assertFalse(MarinePersonnelLogistics.release(roster, "missing", cargo));
        assertEquals(0f, quantity[0]);
    }

    @Test
    void bulkEnlistmentFillsLineFireteamsAndStopsAtAvailableCargo() {
        float[] quantity = {8f};
        CargoAPI cargo = cargo(quantity);
        MarineRoster roster = new MarineRoster();
        MarineSquad reserve = roster.reserveSquad();

        assertEquals(7, MarinePersonnelLogistics.enlistLine(roster, 7, cargo));
        assertEquals(1f, quantity[0]);
        assertEquals(7, roster.lineReadySoldiers().size());
        assertTrue(roster.squadMembers(reserve).isEmpty());

        assertEquals(1, MarinePersonnelLogistics.enlistLine(roster, 5, cargo));
        assertEquals(0f, quantity[0]);
        assertEquals(8, roster.lineReadySoldiers().size());
        assertEquals(2, roster.squads().stream().filter(squad -> !squad.reserve()).count());
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
