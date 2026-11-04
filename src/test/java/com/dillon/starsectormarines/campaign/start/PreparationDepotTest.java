package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.BoatFabricationCatalog;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.SquadFoundingCost;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.MutableValue;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** The retired depot only has to be read from older saves and settled once; see {@link PreparationDepot}. */
class PreparationDepotTest {
    private static final int HERMES = BoatFabricationCatalog.recipe(ShuttleType.HERMES).bill().credits();
    private static final int SQUAD = SquadFoundingCost.STANDARD.credits();

    @Test void aWholeDepotBecomesBothFreeOrdersAndNothingElse() {
        for (boolean protectedStock : new boolean[] {true, false}) {
            var settled = new PreparationDepot("jangala", protectedStock).settle();
            assertTrue(settled.freeSquad());
            assertTrue(settled.freeBoat());
            assertEquals(0, settled.credits());
            assertEquals(Map.of(), settled.goods());
        }
    }

    @Test void aDepotLeftWithOnlyTheHermesCreditBecomesOnlyTheFreeBoat() throws Exception {
        var depot = new PreparationDepot("jangala", true);
        setField(depot, "squadCredits", 0);
        field(depot, "squadStock").put(Commodities.MARINES, 0);
        var settled = depot.settle();
        assertFalse(settled.freeSquad());
        assertTrue(settled.freeBoat());
        assertEquals(0, settled.credits());
        assertEquals(Map.of(), settled.goods());
    }

    @Test void whatIsLeftOfAnAllotmentComesAboardRatherThanBeingLost() throws Exception {
        var depot = new PreparationDepot("jangala", false);
        setField(depot, "credits", HERMES - 1);
        setField(depot, "squadCredits", 100);
        field(depot, "stock").put(Commodities.MARINES, MarineSquad.CAPACITY - 5);
        var settled = depot.settle();
        assertFalse(settled.freeSquad());
        assertFalse(settled.freeBoat());
        assertEquals(HERMES - 1 + 100, settled.credits());
        assertEquals(Map.of(Commodities.MARINES, MarineSquad.CAPACITY - 5), settled.goods());
    }

    @Test void aLegacyUnprotectedDepotTurnsItsBoatMaterialsAndSquadStoresIntoCredit() throws Exception {
        PreparationDepot legacy = new PreparationDepot("jangala", false);
        setField(legacy, "credits", null);
        setField(legacy, "squadCredits", null);
        Map<String, Integer> stock = field(legacy, "stock");
        stock.put(Commodities.SUPPLIES, 36);
        stock.put(Commodities.FOOD, 24);
        stock.put(Commodities.METALS, 10);
        stock.put(Commodities.HEAVY_MACHINERY, 2);
        stock.put(Commodities.RARE_METALS, 1);

        var settled = roundTrip(legacy).settle();

        assertTrue(settled.freeSquad(), "its stores convert to exactly the squad's price, and its marines stay marines");
        assertFalse(settled.freeBoat());
        assertEquals(10 * 30 + 2 * 150 + 200, settled.credits(), "stores convert at the base prices");
        assertEquals(Map.of(), settled.goods());
    }

    @Test void aLegacyProtectedDepotKeepsTheBoatsAndTheSquadsCreditApart() throws Exception {
        PreparationDepot legacy = new PreparationDepot("jangala", true);
        setField(legacy, "credits", null);
        setField(legacy, "squadCredits", null);
        int supplies = HERMES / 100 + 5;
        field(legacy, "boatStock").put(Commodities.SUPPLIES, supplies);
        Map<String, Integer> squadStock = field(legacy, "squadStock");
        squadStock.put(Commodities.SUPPLIES, 36);
        squadStock.put(Commodities.FOOD, 24);

        var settled = roundTrip(legacy).settle();

        assertTrue(settled.freeSquad());
        assertTrue(settled.freeBoat());
        assertEquals(supplies * 100 - HERMES, settled.credits(), "the boat's surplus, and none of the squad's");
    }

    @Test void loadingSettlesAnOldDepotOnceAndKeepsItsPort() {
        var saved = new HashMap<String, Object>(Map.of(CerberusCompanyStart.SHIP_KEY, "starter",
                "sm_preparation_depot_issued", true, CerberusCompanyStart.START_PORT_KEY, "elsewhere"));
        var depot = new PreparationDepot("jangala", true);
        saved.put("sm_preparation_depot", depot);
        var purse = new MutableValue(473);
        var added = new HashMap<String, Float>();
        var roster = roster();
        withSector(saved, purse, added, () -> {
            CerberusCompanyStart.settleOldDepot(roster);
            CerberusCompanyStart.settleOldDepot(roster);
        });
        assertTrue(roster.firstSquadFree());
        assertTrue(roster.boatDeck().firstBoatFree());
        assertEquals(473f, purse.get());
        assertEquals(Map.of(), added);
        assertFalse(saved.containsKey("sm_preparation_depot"));
        assertEquals("jangala", saved.get(CerberusCompanyStart.START_PORT_KEY));

        roster.spendFirstSquad();
        withSector(saved, purse, added, () -> CerberusCompanyStart.settleOldDepot(roster));
        assertFalse(roster.firstSquadFree(), "a spent order does not come back on the next load");
    }

    @Test void loadingAStartStillShortOfItsConversionMakesItsFirstOrdersFree() {
        var saved = new HashMap<String, Object>(Map.of(CerberusCompanyStart.SHIP_KEY, "starter"));
        var roster = roster();
        roster.spendFirstSquad();
        roster.boatDeck().spendFirstBoat();
        withSector(saved, new MutableValue(473), new HashMap<>(), () -> CerberusCompanyStart.settleOldDepot(roster));
        assertTrue(roster.firstSquadFree());
        assertTrue(roster.boatDeck().firstBoatFree());
    }

    @Test void loadingACompanyPastItsDepotLeavesItPaying() {
        var saved = new HashMap<String, Object>(Map.of(CerberusCompanyStart.SHIP_KEY, "starter",
                "sm_preparation_depot_issued", true));
        var roster = roster();
        roster.spendFirstSquad();
        roster.boatDeck().spendFirstBoat();
        withSector(saved, new MutableValue(473), new HashMap<>(), () -> CerberusCompanyStart.settleOldDepot(roster));
        assertFalse(roster.firstSquadFree());
        assertFalse(roster.boatDeck().firstBoatFree());
    }

    private static MarineRoster roster() {
        var roster = new MarineRoster();
        var morgan = new MarineCaptain("Morgan Hale", null, Rank.LIEUTENANT, 0);
        roster.add(morgan);
        roster.beginCompanyPreparation(morgan);
        return roster;
    }

    private static void withSector(Map<String, Object> saved, MutableValue purse, Map<String, Float> added, Runnable body) {
        var cargo = stub(CargoAPI.class, Map.of("getCredits", purse,
                "addCommodity", (Function<Object[], Object>) a -> {
                    added.merge((String) a[0], ((Number) a[1]).floatValue(), Float::sum);
                    return null;
                }));
        var fleet = stub(CampaignFleetAPI.class, Map.of("getCargo", cargo,
                "getFleetData", stub(FleetDataAPI.class, Map.of("getMembersListCopy", List.of()))));
        SectorAPI previous = Global.getSector();
        try {
            Global.setSector(stub(SectorAPI.class, Map.of("getPersistentData", saved, "getPlayerFleet", fleet,
                    "getEconomy", stub(EconomyAPI.class, Map.of()))));
            body.run();
        } finally { Global.setSector(previous); }
    }

    @SuppressWarnings("unchecked")
    private static <S> S stub(Class<S> type, Map<String, ?> methods) {
        return (S) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            Object value = methods.get(method.getName());
            if (value instanceof Function<?, ?> function)
                return ((Function<Object[], Object>) function).apply(args);
            if (value != null) return value;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == float.class) return 0f;
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> field(PreparationDepot depot, String name) throws Exception {
        Field field = PreparationDepot.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<String, Integer>) field.get(depot);
    }
    private static void setField(PreparationDepot depot, String name, Object value) throws Exception {
        Field field = PreparationDepot.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(depot, value);
    }
    private static PreparationDepot roundTrip(PreparationDepot depot) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(depot); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (PreparationDepot) in.readObject();
        }
    }
}
