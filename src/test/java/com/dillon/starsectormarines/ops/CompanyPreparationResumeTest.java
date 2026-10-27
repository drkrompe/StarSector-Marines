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
                "getIndustries", List.of(industry), "getFaction", faction, "getSize", 3));
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
            var sabine = new MarineCaptain("Morgan Hale", null, Rank.LIEUTENANT, 0);
            roster.add(sabine); roster.beginCompanyPreparation(sabine);
            assertCaptured(roster, ScreenId.SHIP_TRANSFER);
            roster.setCompanyShip("starter", "Second Wind", 75);
            roster.boatDeck().registerFoundingWrecks("starter");
            roster.boatDeck().reconcile("starter", ShuttleType.HERMES, 1);
            // The company rides up to Relief on Wayline's truck: Morgan and her squad come straight after the ship.
            assertCaptured(roster, ScreenId.BARRACKS);
            var normal = new PreparationGuide.Lesson(1, "MUSTER OUR FIRST SQUAD", "Found the squad.", List.of(), null, null);
            Map<String, Integer> original = CerberusCompanyStart.depot().stock();
            sabine = roster.officerInduction().officer(roster);
            roster.officerInduction().introduce(sabine);
            assertTrue(sabine.train(OfficerSpecialty.TACTICAL_COVER));
            assertEquals(ScreenId.BARRACKS, CompanyPreparationRoute.resolve(ScreenId.FLEET_ARMORY, roster, false),
                    "Squads are raised in Barracks, so the first formation resumes there");
            assertEquals(ScreenId.BARRACKS, CompanyPreparationRoute.resolve(ScreenId.MISSION_SELECT, roster, false));
            assertSame(normal, CompanyPreparationGuide.withDepotAccess(roster, normal, () -> exits[0]++));

            // Close the planet dialog, then reopen from the planet-free company shortcut.
            ui.remove("getCurrentInteractionDialog");
            assertNull(CerberusCompanyStart.accessibleDepot());
            ui.put("getCurrentInteractionDialog", stub(InteractionDialogAPI.class, Map.of("getInteractionTarget", fleet)));
            var recovery = CompanyPreparationGuide.withDepotAccess(roster, normal, () -> exits[0]++);
            assertEquals("RETURN TO DEPOT", recovery.actionLabel());
            assertTrue(recovery.copy().contains("Jangala"));
            assertEquals(List.of("page-nav-return"), recovery.targets());
            recovery.action().run();
            assertSame(planet, course[0]); assertEquals(1, exits[0]);
            assertCaptured(roster, ScreenId.BARRACKS);
            assertEquals(original, CerberusCompanyStart.depot().stock());

            // Reload before paying, then use a fresh planet dialog and a fresh resource adapter.
            roster = reload(saved, roster);
            assertEquals(original, CerberusCompanyStart.depot().stock());
            assertCaptured(roster, ScreenId.BARRACKS);
            ui.put("getCurrentInteractionDialog", stub(InteractionDialogAPI.class, Map.of("getInteractionTarget", planet)));
            assertSame(normal, CompanyPreparationGuide.withDepotAccess(roster, normal, () -> exits[0]++));
            sabine = roster.officerInduction().officer(roster);
            var squad = new SquadFoundingWorkshop(roster, new CampaignSquadFoundingResources()).foundSquad().squad();
            assertNotNull(squad);
            assertCaptured(roster, ScreenId.BARRACKS);
            roster.assignCaptainToSquad(sabine.id(), squad.id());
            assertCaptured(roster, ScreenId.BARRACKS);
            assertTrue(roster.officerInduction().finish(roster));
            for (ScreenId id : ScreenId.values()) assertEquals(id, CompanyPreparationRoute.resolve(id, roster, false));
            assertTrue(roster.boatDeck().flightReady().isEmpty(), "Relief launches without a boat");
            assertTrue(CerberusCompanyStart.depot().credits() > 0, "The Hermes yard credit stays prepaid");

            // After Relief, the first-lift lesson draws them, and leaving the planet still sends us back for them.
            roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
            assertTrue(CampaignLessons.firstLiftPending(roster));
            var lift = new PreparationGuide.Lesson(1, "REPAIR OUR FIRST HERMES", "Confirm the work.", List.of(), null, null);
            ui.put("getCurrentInteractionDialog", stub(InteractionDialogAPI.class, Map.of("getInteractionTarget", fleet)));
            var away = CompanyPreparationGuide.withDepotAccess(roster, lift, () -> exits[0]++);
            assertEquals("RETURN TO DEPOT", away.actionLabel());
            assertTrue(away.copy().contains("Hermes") && away.copy().contains("Jangala"), away.copy());
            assertFalse(new BoatWorkshop(roster.boatDeck(), CampaignFabricationResources.forBoats()).fabricate(0).succeeded());
            roster = reload(saved, roster);
            ui.put("getCurrentInteractionDialog", dialog);
            assertSame(lift, CompanyPreparationGuide.withDepotAccess(roster, lift, () -> exits[0]++));
            var workshop = new BoatWorkshop(roster.boatDeck(), CampaignFabricationResources.forBoats());
            assertTrue(workshop.fabricate(0).succeeded());
            assertFalse(workshop.fabricate(0).succeeded());
            assertEquals(1, roster.boatDeck().flightReady().size());
            assertFalse(CampaignLessons.firstLiftPending(roster));
            assertEquals(0, CerberusCompanyStart.depot().credits());
        } finally { Global.setSector(previous); }
    }

    @Test void establishedAndLegacyOfficerOnlyCompaniesKeepTheirRoutes() {
        var roster = new MarineRoster();
        for (ScreenId id : ScreenId.values()) assertEquals(id, CompanyPreparationRoute.resolve(id, roster, false));
        var sabine = new MarineCaptain("Morgan Hale", null, Rank.LIEUTENANT, 0);
        roster.add(sabine); roster.beginOfficerInduction(sabine);
        for (ScreenId id : ScreenId.values()) assertEquals(id, CompanyPreparationRoute.resolve(id, roster, false));
    }

    /** Saves and restores the sector's persistent data and the roster together, as a game reload does. */
    private static MarineRoster reload(Map<String, Object> saved, MarineRoster roster) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(saved); out.writeObject(roster); }
        saved.clear();
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            ((Map<?, ?>) in.readObject()).forEach((key, value) -> saved.put((String) key, value));
            return (MarineRoster) in.readObject();
        }
    }

    private static void assertCaptured(MarineRoster roster, ScreenId expected) {
        for (ScreenId entry : List.of(ScreenId.MISSION_SELECT, ScreenId.BARRACKS, ScreenId.FLEET_ARMORY,
                ScreenId.SHIP_TRANSFER, ScreenId.BOAT_DECK,
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
