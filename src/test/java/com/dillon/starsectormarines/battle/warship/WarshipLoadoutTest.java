package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WarshipLoadoutTest {

    private static final String HULL = """
            {"hullId":"test","spriteName":"graphics/ships/test.png",
             "weaponSlots":[
              {"id":"WS 001","type":"BUILT_IN","size":"LARGE","mount":"HARDPOINT","locations":[100,10],"angle":0,"arc":5},
              {"id":"WS 002","type":"BALLISTIC","size":"LARGE","mount":"TURRET","locations":[0,-50],"angle":-80,"arc":220},
              {"id":"WS 003","type":"BALLISTIC","size":"SMALL","mount":"TURRET","locations":[0,50],"angle":80,"arc":220}]}""";

    @AfterEach
    void restore() {
        VanillaData.useSource(null);
        VanillaWeapons.clearCache();
    }

    private static void install() {
        VanillaData.useSource(new VanillaData.Source() {
            @Override public JSONObject json(String path) throws Exception {
                if (path.equals("data/hulls/test.ship")) return new JSONObject(HULL);
                throw new java.io.FileNotFoundException(path);
            }

            @Override public List<Map<String, String>> csv(String path) {
                return List.of(
                        Map.of("id", "tpc", "damage/shot", "250", "type", "ENERGY", "range", "1000",
                                "burst size", "1", "chargedown", "0.2", "proj speed", "1000", "hints", "SYSTEM"),
                        Map.of("id", "devastator", "damage/shot", "150", "type", "HIGH_EXPLOSIVE",
                                "range", "900", "burst size", "12", "burst delay", "0.1", "chargedown", "2",
                                "proj speed", "700", "min spread", "5", "hints", "PD,PD_ALSO"),
                        Map.of("id", "flak", "damage/shot", "200", "type", "FRAGMENTATION", "range", "500",
                                "burst size", "1", "chargedown", "1", "proj speed", "700", "hints", "PD"));
            }
        });
        VanillaWeapons.clearCache();
    }

    /** A variant answering only the calls the adapter makes; anything else fails loudly. */
    private static ShipVariantAPI variant(String hullId, String baseHullId,
                                          Map<String, String> fitted, Map<String, String> builtIns) {
        ShipHullSpecAPI spec = (ShipHullSpecAPI) Proxy.newProxyInstance(
                WarshipLoadoutTest.class.getClassLoader(), new Class<?>[] {ShipHullSpecAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHullId" -> hullId;
                    case "getBaseHullId" -> baseHullId;
                    case "getBuiltInWeapons" -> new HashMap<>(builtIns);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (ShipVariantAPI) Proxy.newProxyInstance(
                WarshipLoadoutTest.class.getClassLoader(), new Class<?>[] {ShipVariantAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHullSpec" -> spec;
                    case "getFittedWeaponSlots" -> fitted.keySet();
                    case "getWeaponId" -> fitted.get((String) args[0]);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static FleetMemberAPI member(ShipVariantAPI variant) {
        return (FleetMemberAPI) Proxy.newProxyInstance(
                WarshipLoadoutTest.class.getClassLoader(), new Class<?>[] {FleetMemberAPI.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getVariant")) return variant;
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void adapterReadsFittedSlotsAndLeavesOthersEmpty() {
        Map<String, String> fitted = new LinkedHashMap<>();
        fitted.put("WS 002", "devastator");
        VariantApiLoadout loadout = new VariantApiLoadout(variant("test", "test", fitted, Map.of()));
        assertEquals("test", loadout.hullId());
        assertEquals("devastator", loadout.weaponInSlot("WS 002"));
        assertNull(loadout.weaponInSlot("WS 003"));
    }

    @Test
    void builtInsFillSlotsTheVariantDoesNotName() {
        Map<String, String> fitted = Map.of("WS 002", "devastator");
        VariantApiLoadout loadout = new VariantApiLoadout(
                variant("test", "test", fitted, Map.of("WS 001", "tpc", "WS 002", "flak")));
        assertEquals("tpc", loadout.weaponInSlot("WS 001"));
        assertEquals("devastator", loadout.weaponInSlot("WS 002"), "a fitted weapon outranks a built-in");
    }

    @Test
    void aSkinFitsOnItsBaseHullsSpec() {
        VariantApiLoadout loadout = new VariantApiLoadout(variant("test_skin", "test", Map.of(), Map.of()));
        assertEquals("test", loadout.hullId());
    }

    @Test
    void aFleetMemberFitsLikeTheStockPathWhenTheyAgree() throws Exception {
        install();
        Map<String, String> fitted = new LinkedHashMap<>();
        fitted.put("WS 001", "tpc");
        fitted.put("WS 002", "devastator");
        fitted.put("WS 003", "flak");
        Warship ship = Warship.fit(member(variant("test", "test", fitted, Map.of())),
                Faction.MARINE, 10f, 10f, 0f);
        assertEquals(3, ship.mounts().size());
        assertEquals("tpc", ship.mounts().get(0).weapon.id());
        assertEquals("devastator", ship.mounts().get(1).weapon.id());
        assertEquals("flak", ship.mounts().get(2).weapon.id());
    }

    @Test
    void anEmptiedSlotMountsNothing() throws Exception {
        install();
        Warship ship = Warship.fit(member(variant("test", "test", Map.of("WS 002", "devastator"), Map.of())),
                Faction.MARINE, 10f, 10f, 0f);
        assertEquals(1, ship.mounts().size());
    }
}
