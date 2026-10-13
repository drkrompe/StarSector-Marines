package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.power.ShipOnStation;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.ops.AgreusArcCampaign;
import com.dillon.starsectormarines.ops.Mission;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgreusConstructionSupportTest {
    @Test void onlyCommittedWorkingOwnedTendersContributeSupport() {
        var working = member("shepherd", "replacement-tender", Map.of());
        assertTrue(AgreusConstructionSupport.sources(null).isEmpty());
        assertTrue(AgreusConstructionSupport.sources(List.of()).isEmpty());
        var sources = AgreusConstructionSupport.sources(List.of(working));
        assertEquals(1, sources.size());
        assertEquals("replacement-tender", sources.get(0).memberId());
        assertTrue(sources.get(0).tender());
        for (Map<String, Object> condition : List.of(Map.<String, Object>of("isMothballed", true),
                Map.<String, Object>of("isFighterWing", true), Map.<String, Object>of("getHullFraction", 0f),
                Map.<String, Object>of("getCR", 0f), Map.<String, Object>of("missingStatus", true),
                Map.<String, Object>of("missingTracker", true)))
            assertTrue(AgreusConstructionSupport.sources(List.of(member("shepherd", "unavailable", condition))).isEmpty(), condition.toString());
        assertTrue(AgreusConstructionSupport.sources(List.of(member("shepherd", null, Map.of()))).isEmpty());
        assertTrue(AgreusConstructionSupport.sources(List.of(member("shepherd", "", Map.of()))).isEmpty());
        assertTrue(AgreusConstructionSupport.sources(List.of(member("hermes", "owned-shuttle", Map.of()))).isEmpty());
    }

    @Test void storyUsesNormalSelectedShipPowerAndItsSupplyCostWithoutEmployerOrDebugGifts() {
        Mission mission = Mission.builder(AgreusArcCampaign.offer(new AgreusArcProgress(), "Agreus"))
                .employerPowerIds(List.of(ShipOnStation.EMPLOYER_PREFIX + "shepherd", "mech_support", "orbital_barrage")).build();
        assertTrue(PowerCatalog.resolve(List.of(), mission).isEmpty());
        var powers = PowerCatalog.resolve(List.of(member("shepherd", "morrow", Map.of())), mission);
        assertEquals(1, powers.size());
        var power = assertInstanceOf(ShipOnStation.class, powers.get(0));
        assertEquals(ShipOnStation.ID, power.id);
        assertEquals(ShipOnStation.SUPPLY_COST, power.supplyCost);
        assertEquals(ShipOnStation.SLOT_WEIGHT, power.slotWeight);
        assertEquals(1, power.maxCharges);
        assertFalse(AgreusConstructionSupport.selected(List.of()));
        assertTrue(AgreusConstructionSupport.selected(powers));
        assertNotNull(AgreusConstructionSupport.blocker(powers, List.of(), 100));
        assertNotNull(AgreusConstructionSupport.blocker(powers, List.of("foreign-power"), 100));
        assertNotNull(AgreusConstructionSupport.blocker(powers, List.of(ShipOnStation.ID), ShipOnStation.SUPPLY_COST - 1));
        assertNull(AgreusConstructionSupport.blocker(powers, List.of(ShipOnStation.ID), ShipOnStation.SUPPLY_COST));
        var stock = new ShipOnStation(List.of(new ShipSupportSource("shepherd", "Employer", true, Map.of())));
        assertFalse(AgreusConstructionSupport.selected(List.of(stock)));
        assertNotNull(AgreusConstructionSupport.blocker(List.of(stock), List.of(ShipOnStation.ID), 100));
        var warship = new ShipSupportSource("onslaught", "Owned capital", false, Map.of(), "capital-member");
        assertFalse(AgreusConstructionSupport.ownedTender(warship));
        assertFalse(AgreusConstructionSupport.selected(List.of(new ShipOnStation(List.of(warship)))));
    }

    @Test void launchRejectsSaleLossOrOperationalChangesSinceTheBriefing() {
        var previous = Global.getSector();
        var working = member("shepherd", "owned-tender", Map.of());
        var power = new ShipOnStation(AgreusConstructionSupport.sources(List.of(working)));
        var owned = new ArrayList<>(List.of(working));
        var fleet = stub(CampaignFleetAPI.class, Map.of("getFleetData", stub(FleetDataAPI.class, Map.of("getMembersListCopy", owned))));
        try {
            Global.setSector(stub(SectorAPI.class, Map.of("getPlayerFleet", fleet)));
            assertDoesNotThrow(() -> AgreusConstructionSupport.requireLive(List.of(power), ShipOnStation.SUPPLY_COST));
            assertThrows(IllegalStateException.class, () -> AgreusConstructionSupport.requireLive(List.of(power), ShipOnStation.SUPPLY_COST - 1));
            owned.clear();
            assertThrows(IllegalStateException.class, () -> AgreusConstructionSupport.requireLive(List.of(power), 100));
            owned.add(member("shepherd", "other-tender", Map.of()));
            assertThrows(IllegalStateException.class, () -> AgreusConstructionSupport.requireLive(List.of(power), 100));
            owned.clear(); owned.add(member("shepherd", "owned-tender", Map.of("isMothballed", true)));
            assertThrows(IllegalStateException.class, () -> AgreusConstructionSupport.requireLive(List.of(power), 100));
            owned.clear(); owned.add(member("shepherd", "owned-tender", Map.of("getCR", 0f)));
            assertThrows(IllegalStateException.class, () -> AgreusConstructionSupport.requireLive(List.of(power), 100));
            owned.clear(); owned.add(working);
            assertDoesNotThrow(() -> AgreusConstructionSupport.requireLive(List.of(power), 100));
        } finally { Global.setSector(previous); }
    }

    private static FleetMemberAPI member(String hull, String id, Map<String, Object> condition) {
        var spec = stub(ShipHullSpecAPI.class, Map.of("getHullId", hull, "getBaseHullId", hull,
                "getHullSize", HullSize.FRIGATE, "getBuiltInWeapons", new HashMap<>()));
        var variant = stub(ShipVariantAPI.class, Map.of("getHullSpec", spec, "getFittedWeaponSlots", List.of()));
        var values = new HashMap<String, Object>();
        values.put("getVariant", variant); values.put("getHullSpec", spec); values.put("getShipName", "Working tender");
        values.put("getId", id); values.put("isMothballed", false); values.put("isFighterWing", false); values.putAll(condition);
        return (FleetMemberAPI) Proxy.newProxyInstance(FleetMemberAPI.class.getClassLoader(), new Class<?>[]{FleetMemberAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getStatus" -> Boolean.TRUE.equals(condition.get("missingStatus")) ? null
                            : stub(method.getReturnType(), Map.of("getHullFraction", condition.getOrDefault("getHullFraction", 1f)));
                    case "getRepairTracker" -> Boolean.TRUE.equals(condition.get("missingTracker")) ? null
                            : stub(method.getReturnType(), Map.of("getCR", condition.getOrDefault("getCR", 0.7f)));
                    default -> {
                        if (!values.containsKey(method.getName())) throw new UnsupportedOperationException(method.getName());
                        yield values.get(method.getName());
                    }
                });
    }
    private static <T> T stub(Class<T> type, Map<String, Object> values) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (!values.containsKey(method.getName())) throw new UnsupportedOperationException(method.getName());
            return values.get(method.getName());
        }));
    }
}
