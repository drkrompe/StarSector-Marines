package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirScale;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarshipFittingTest {

    private static final String HULL = """
            {"hullId":"test","spriteName":"graphics/ships/test.png",
             "weaponSlots":[
              {"id":"WS 001","type":"BUILT_IN","size":"LARGE","mount":"HARDPOINT","locations":[100,10],"angle":0,"arc":5},
              {"id":"WS 002","type":"BALLISTIC","size":"LARGE","mount":"TURRET","locations":[0,-50],"angle":-80,"arc":220},
              {"id":"WS 003","type":"BALLISTIC","size":"SMALL","mount":"TURRET","locations":[0,50],"angle":80,"arc":220}]}""";

    private static final String VARIANT = """
            {"hullId":"test","weaponGroups":[
              {"weapons":{"WS 001":"tpc","WS 002":"devastator"}},
              {"weapons":{"WS 003":"flak"}}]}""";

    private static final String TABLE_NOTE = "weapons come through VanillaData";

    @AfterEach
    void restore() {
        VanillaData.useSource(null);
        VanillaWeapons.clearCache();
    }

    private static void install() {
        VanillaData.useSource(new VanillaData.Source() {
            @Override public JSONObject json(String path) throws Exception {
                if (path.equals("data/hulls/test.ship")) return new JSONObject(HULL);
                if (path.equals("data/variants/test/test_Standard.variant")) return new JSONObject(VARIANT);
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

    @Test
    void slotsKeepTheHullsFrameAndConvertToCells() throws Exception {
        List<WarshipSlot> slots = WarshipSlot.parse(new JSONObject(HULL));
        WarshipSlot hardpoint = slots.get(0);
        assertTrue(hardpoint.hardpoint());
        assertEquals(100f * AirScale.METERS_PER_PX, hardpoint.localY(), 1e-5, "forward runs along local Y");
        assertEquals(-10f * AirScale.METERS_PER_PX, hardpoint.localX(), 1e-5, "port is negative local X");
        assertFalse(slots.get(1).hardpoint());
        assertEquals(220f, slots.get(1).arcDegrees());
    }

    @Test
    void aVariantArmsTheSlotsItNames() throws Exception {
        install();
        Warship ship = Warship.fit("test", "test_Standard", Faction.MARINE, 10f, 10f, 0f);
        // The flak slot is point defence only: mounted, but not an area-fire gun.
        assertEquals(3, ship.mounts().size(), TABLE_NOTE);
        assertEquals("tpc", ship.mounts().get(0).weapon.id());
        assertEquals("devastator", ship.mounts().get(1).weapon.id());
        assertEquals("flak", ship.mounts().get(2).weapon.id());
        assertFalse(ship.mounts().get(1).pointDefence());
        assertTrue(ship.mounts().get(2).pointDefence());
    }

    @Test
    void pointDefenceThatAlsoShootsGroundIsKeptAndPureOneIsNot() {
        install();
        assertTrue(VanillaWeapons.get("flak").pointDefenceOnly());
        assertFalse(VanillaWeapons.get("devastator").pointDefenceOnly());
        assertNull(VanillaWeapons.get("nonesuch"));
    }

    @Test
    void reachAndBlastUseTheOneScale() {
        install();
        VanillaWeapon devastator = VanillaWeapons.get("devastator");
        assertEquals(900f * AirScale.METERS_PER_PX, WarshipScale.cells(devastator.rangePx()), 1e-4);
        assertEquals(12, devastator.burstSize());
    }

    @Test
    void aMountRidesTheHullsFacing() throws Exception {
        install();
        Warship ship = Warship.fit("test", "test_Standard", Faction.MARINE, 10f, 10f, 90f);
        Warship.Mount nose = ship.mounts().get(0);
        // Facing 90 degrees turns the nose to world -X, so the forward offset lands there.
        assertEquals(10f - nose.slot.localY(), ship.mountX(nose), 1e-3);
    }
}
