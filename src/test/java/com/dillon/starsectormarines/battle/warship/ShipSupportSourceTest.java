package com.dillon.starsectormarines.battle.warship;

import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipSupportSourceTest {

    @BeforeEach
    void installWeapons() {
        VanillaData.useSource(new VanillaData.Source() {
            @Override public JSONObject json(String path) throws Exception {
                throw new java.io.FileNotFoundException(path);
            }

            @Override public List<Map<String, String>> csv(String path) {
                return List.of(
                        Map.of("id", "tpc", "damage/shot", "250", "type", "ENERGY", "range", "1000",
                                "burst size", "1", "chargedown", "0.2", "proj speed", "1000", "hints", "SYSTEM"),
                        Map.of("id", "flak", "damage/shot", "200", "type", "FRAGMENTATION", "range", "500",
                                "burst size", "1", "chargedown", "1", "proj speed", "700", "hints", "PD"));
            }
        });
        VanillaWeapons.clearCache();
    }

    @AfterEach
    void restore() {
        VanillaData.useSource(null);
        VanillaWeapons.clearCache();
    }

    private static ShipVariantAPI variant(String baseHullId, Map<String, String> fitted, Map<String, String> builtIns) {
        ShipHullSpecAPI spec = (ShipHullSpecAPI) Proxy.newProxyInstance(
                ShipSupportSourceTest.class.getClassLoader(), new Class<?>[] {ShipHullSpecAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHullId", "getBaseHullId" -> baseHullId;
                    case "getBuiltInWeapons" -> new HashMap<>(builtIns);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (ShipVariantAPI) Proxy.newProxyInstance(
                ShipSupportSourceTest.class.getClassLoader(), new Class<?>[] {ShipVariantAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHullSpec" -> spec;
                    case "getFittedWeaponSlots" -> fitted.keySet();
                    case "getWeaponId" -> fitted.get((String) args[0]);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void theFrozenLoadoutRoundTripsEverySlotWeaponIncludingBuiltIns() {
        Map<String, String> fitted = new LinkedHashMap<>();
        fitted.put("WS 002", "tpc");
        fitted.put("WS 003", "flak");
        ShipSupportSource frozen = ShipSupportSource.ofVariant(
                variant("hull", fitted, Map.of("WS 001", "tpc", "WS 002", "flak")), "ISS Test", false);

        assertEquals("hull", frozen.hullId());
        assertEquals("ISS Test", frozen.shipName());
        assertEquals("tpc", frozen.weaponInSlot("WS 001"), "a built-in fills a slot the variant leaves");
        assertEquals("tpc", frozen.weaponInSlot("WS 002"), "a fitted weapon outranks a built-in");
        assertEquals("flak", frozen.weaponInSlot("WS 003"));
        assertNull(frozen.weaponInSlot("WS 004"));
    }

    @Test
    void theFrozenMapDoesNotFollowTheSourceMapAfterwards() {
        Map<String, String> live = new LinkedHashMap<>(Map.of("WS 001", "tpc"));
        ShipSupportSource frozen = new ShipSupportSource("hull", "X", false, live);
        live.put("WS 001", "flak");
        assertEquals("tpc", frozen.weaponInSlot("WS 001"));
        assertThrows(UnsupportedOperationException.class, () -> frozen.weapons().put("WS 009", "tpc"));
    }

    @Test
    void cruisersAndCapitalsWithAGroundGunAreWarships() {
        Map<String, String> guns = Map.of("WS 001", "tpc");
        for (HullSize size : List.of(HullSize.CRUISER, HullSize.CAPITAL_SHIP)) {
            ShipSupportSource source = ShipSupportSource.of("hull", size, "N", guns);
            assertNotNull(source, size.name());
            assertFalse(source.tender());
        }
    }

    @Test
    void smallerHullsStayWhatTheyAre() {
        Map<String, String> guns = Map.of("WS 001", "tpc");
        for (HullSize size : List.of(HullSize.FRIGATE, HullSize.DESTROYER, HullSize.FIGHTER)) {
            assertNull(ShipSupportSource.of("hull", size, "N", guns), size.name());
        }
    }

    @Test
    void aCapitalWithOnlyPointDefenceOrNothingHasNoGunForTheGround() {
        assertNull(ShipSupportSource.of("hull", HullSize.CAPITAL_SHIP, "N", Map.of("WS 001", "flak")));
        assertNull(ShipSupportSource.of("hull", HullSize.CAPITAL_SHIP, "N", Map.of("WS 001", "no_such_weapon")));
        assertNull(ShipSupportSource.of("hull", HullSize.CAPITAL_SHIP, "N", Map.of()));
        assertTrue(ShipSupportSource.hasGroundGun(Map.of("WS 001", "flak", "WS 002", "tpc")));
    }

    @Test
    void theShepherdIsATenderAtAnySizeWithNoGuns() {
        ShipSupportSource tender = ShipSupportSource.of("shepherd", HullSize.FRIGATE, "Fold", Map.of("WS 001", "tpc"));
        assertNotNull(tender);
        assertTrue(tender.tender());
        assertTrue(tender.weapons().isEmpty(), "a tender's weapons are its drones");
    }

    @Test
    void aSourceNeedsAHull() {
        assertThrows(IllegalArgumentException.class, () -> new ShipSupportSource("", "X", false, Map.of()));
        assertEquals("hull", new ShipSupportSource("hull", " ", false, Map.of()).shipName());
    }
}
