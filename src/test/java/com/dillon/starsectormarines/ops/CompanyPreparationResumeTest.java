package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import com.dillon.starsectormarines.campaign.start.FoundingReserve;
import com.dillon.starsectormarines.marine.BoatWorkshop;
import com.dillon.starsectormarines.marine.CampaignFabricationResources;
import com.dillon.starsectormarines.marine.CampaignSquadFoundingResources;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.OfficerSpecialty;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.SquadFoundingWorkshop;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class CompanyPreparationResumeTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void leavingAndReloadingResumeReservedOrdersWithoutDuplicatingStores(boolean protectedStart) throws Exception {
        SectorAPI previous = Global.getSector();
        Map<String, Object> saved = new HashMap<>(Map.of(CerberusCompanyStart.SHIP_KEY, "starter"));
        if (protectedStart) FoundingReserve.issue(saved);
        Map<String, Object> planetMethods = new HashMap<>(Map.of("getName", "Jangala", "getId", "jangala"));
        PlanetAPI planet = stub(PlanetAPI.class, planetMethods);
        var faction = stub(FactionAPI.class, Map.of("isHostileTo", false));
        var industry = stub(Industry.class, Map.of("isFunctional", true,
                "getSpec", stub(IndustrySpecAPI.class, Map.of("hasTag", true))));
        var market = stub(MarketAPI.class, Map.of("getId", "jangala", "getPlanetEntity", planet,
                "getIndustries", List.of(industry), "getFaction", faction));
        planetMethods.put("getMarket", market);
        var cargo = stub(CargoAPI.class, Map.of("getCommodityQuantity", 0f,
                "removeCommodity", (Function<Object[], Object>) a -> { fail("Reserved orders must not spend empty cargo"); return null; }));
        var fleet = stub(CampaignFleetAPI.class, Map.of("getCargo", cargo));
        var dialog = stub(InteractionDialogAPI.class, Map.of("getInteractionTarget", planet));
        Object[] course = {null};
        int[] exits = {0};
        Map<String, Object> ui = new HashMap<>(Map.of("getCurrentInteractionDialog", dialog,
                "layInCourseForNextStep", (Function<Object[], Object>) a -> { course[0] = a[0]; return null; }));
        try {
            Global.setSector(stub(SectorAPI.class, Map.of("getPersistentData", saved, "getPlayerFleet", fleet, "getScripts", List.of(),
                    "getPlayerFaction", faction, "getCampaignUI", stub(CampaignUIAPI.class, ui),
                    "getEconomy", stub(EconomyAPI.class, Map.of("getMarket", market)))));
            CerberusCompanyStart.establishDepot(stub(FleetMemberAPI.class, Map.of("getId", "starter")), market);
            var roster = new MarineRoster();
            var mira = new MarineCaptain("Mira Hale", null, Rank.LIEUTENANT, 0);
            roster.add(mira); roster.beginCompanyPreparation(mira);
            assertCaptured(roster, ScreenId.SHIP_TRANSFER);
            roster.setCompanyShip("starter", "Second Wind", 75);
            roster.boatDeck().registerFoundingWrecks("starter");
            roster.boatDeck().reconcile("starter", ShuttleType.HERMES, 1);
            assertCaptured(roster, ScreenId.BOAT_DECK);
            var normal = new PreparationGuide.Lesson(1, "REPAIR OUR FIRST HERMES", "Confirm the work.", List.of(), null, null);
            var stores = CampaignFabricationResources.forBoats();
            Map<String, Integer> original = CerberusCompanyStart.depot().stock();
            assertSame(normal, CompanyPreparationGuide.withDepotAccess(roster, normal, () -> exits[0]++));

            // Close the planet dialog, then reopen from the planet-free company shortcut.
            ui.remove("getCurrentInteractionDialog");
            assertNull(CerberusCompanyStart.accessibleDepot());
            ui.put("getCurrentInteractionDialog", stub(InteractionDialogAPI.class, Map.of("getInteractionTarget", fleet)));
            assertEquals(0, stores.available("metals"));
            assertFalse(new BoatWorkshop(roster.boatDeck(), stores).fabricate(0).succeeded());
            assertEquals(original, CerberusCompanyStart.depot().stock());
            var recovery = CompanyPreparationGuide.withDepotAccess(roster, normal, () -> exits[0]++);
            assertEquals("RETURN TO DEPOT", recovery.actionLabel());
            assertTrue(recovery.copy().contains("Jangala"));
            assertEquals(List.of("page-nav-return"), recovery.targets());
            recovery.action().run();
            assertSame(planet, course[0]); assertEquals(1, exits[0]);
            assertCaptured(roster, ScreenId.BOAT_DECK);

            // Reload before paying, then use a fresh planet dialog and a fresh resource adapter.
            var bytes = new ByteArrayOutputStream();
            try (var out = new ObjectOutputStream(bytes)) { out.writeObject(saved); out.writeObject(roster); }
            saved.clear();
            try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                ((Map<?, ?>) in.readObject()).forEach((key, value) -> saved.put((String) key, value));
                roster = (MarineRoster) in.readObject();
            }
            assertEquals(original, CerberusCompanyStart.depot().stock());
            assertCaptured(roster, ScreenId.BOAT_DECK);
            ui.put("getCurrentInteractionDialog", stub(InteractionDialogAPI.class, Map.of("getInteractionTarget", planet)));
            assertSame(normal, CompanyPreparationGuide.withDepotAccess(roster, normal, () -> exits[0]++));
            var workshop = new BoatWorkshop(roster.boatDeck(), CampaignFabricationResources.forBoats());
            assertTrue(workshop.fabricate(0).succeeded());
            assertFalse(workshop.fabricate(0).succeeded());
            assertEquals(1, roster.boatDeck().airworthy().size());
            assertEquals(0, CerberusCompanyStart.depot().available("metals"));
            assertEquals(36, CerberusCompanyStart.depot().available("supplies"));
            assertCaptured(roster, ScreenId.BARRACKS);
            mira = roster.officerInduction().officer(roster);
            roster.officerInduction().introduce(mira);
            assertCaptured(roster, ScreenId.BARRACKS);
            assertTrue(mira.train(OfficerSpecialty.TACTICAL_COVER));
            assertEquals(ScreenId.FLEET_ARMORY, CompanyPreparationRoute.resolve(ScreenId.FLEET_ARMORY, roster, false));
            assertEquals(ScreenId.BARRACKS, CompanyPreparationRoute.resolve(ScreenId.MISSION_SELECT, roster, false));
            ui.remove("getCurrentInteractionDialog");
            assertEquals("RETURN TO DEPOT", CompanyPreparationGuide.withDepotAccess(roster, normal, () -> {}).actionLabel());
            ui.put("getCurrentInteractionDialog", dialog);
            var squad = new SquadFoundingWorkshop(roster, new CampaignSquadFoundingResources()).foundSquad().squad();
            assertNotNull(squad);
            assertCaptured(roster, ScreenId.BARRACKS);
            roster.assignCaptainToSquad(mira.id(), squad.id());
            assertCaptured(roster, ScreenId.BARRACKS);
            assertTrue(roster.officerInduction().finish(roster));
            for (ScreenId id : ScreenId.values()) assertEquals(id, CompanyPreparationRoute.resolve(id, roster, false));
            assertTrue(CerberusCompanyStart.depot().stock().values().stream().allMatch(n -> n == 0));
        } finally { Global.setSector(previous); }
    }

    @Test void establishedAndLegacyOfficerOnlyCompaniesKeepTheirRoutes() {
        var roster = new MarineRoster();
        for (ScreenId id : ScreenId.values()) assertEquals(id, CompanyPreparationRoute.resolve(id, roster, false));
        var mira = new MarineCaptain("Mira Hale", null, Rank.LIEUTENANT, 0);
        roster.add(mira); roster.beginOfficerInduction(mira);
        for (ScreenId id : ScreenId.values()) assertEquals(id, CompanyPreparationRoute.resolve(id, roster, false));
    }

    private static void assertCaptured(MarineRoster roster, ScreenId expected) {
        for (ScreenId entry : List.of(ScreenId.MISSION_SELECT, ScreenId.BARRACKS, ScreenId.FLEET_ARMORY,
                ScreenId.FLEET_ARMORY_OVERVIEW, ScreenId.SHIP_TRANSFER, ScreenId.BOAT_DECK,
                ScreenId.MECH_LAB, ScreenId.SHIP_VIEW, ScreenId.BRIEFING, ScreenId.SQUAD_DEPLOYMENT))
            assertEquals(expected, CompanyPreparationRoute.resolve(entry, roster, false), entry.name());
        for (ScreenId untouched : List.of(ScreenId.BATTLE, ScreenId.RESULTS, ScreenId.LOOT,
                ScreenId.DEBUG_MISSIONS, ScreenId.UI_WORKBENCH, ScreenId.COMPANY_HQ))
            assertEquals(untouched, CompanyPreparationRoute.resolve(untouched, roster, false));
        assertEquals(ScreenId.BRIEFING, CompanyPreparationRoute.resolve(ScreenId.BRIEFING, roster, true));
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, Map<String, Object> methods) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("equals")) return proxy == args[0];
            Object result = methods.get(method.getName());
            if (result instanceof Function<?, ?> fn) return ((Function<Object[], Object>) fn).apply(args);
            if (result != null) return result;
            if (method.getReturnType() == boolean.class) return false;
            return null;
        }));
    }
}
