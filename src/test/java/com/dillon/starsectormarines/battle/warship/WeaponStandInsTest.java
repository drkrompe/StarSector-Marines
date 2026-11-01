package com.dillon.starsectormarines.battle.warship;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WeaponStandInsTest {

    private static JSONObject wpn(String type, String size, String specClass) {
        return new JSONObject(Map.of("type", type, "size", size, "specClass", specClass));
    }

    private static final JSONObject MISSILE_PROJ = new JSONObject(Map.of("specClass", "missile"));

    @AfterEach
    void restore() {
        VanillaData.useSource(null);
        VanillaWeapons.clearCache();
    }

    @Test
    void aModdedWeaponTakesTheBaseGameWeaponOfItsCategorySizeAndRole() {
        assertEquals("tachyonlance", WeaponStandIns.standInFor(
                Map.of("type", "ENERGY"), wpn("ENERGY", "LARGE", "beam"), null));
        assertEquals("pdlaser", WeaponStandIns.standInFor(
                Map.of("type", "ENERGY", "hints", "PD"), wpn("ENERGY", "SMALL", "beam"), null));
        assertEquals("heavymauler", WeaponStandIns.standInFor(
                Map.of("type", "HIGH_EXPLOSIVE", "hints", "PD_ALSO"), wpn("BALLISTIC", "MEDIUM", "projectile"), null),
                "PD_ALSO is a gun that also shoots missiles, not point defence");
        assertEquals("annihilator", WeaponStandIns.standInFor(
                Map.of("type", "HIGH_EXPLOSIVE", "burst size", "5"), wpn("MISSILE", "SMALL", "projectile"),
                MISSILE_PROJ));
    }

    @Test
    void aMountOfNoFixedCategoryFiresAsWhatItsWeaponIs() {
        assertEquals("harpoonpod", WeaponStandIns.standInFor(
                Map.of("type", "HIGH_EXPLOSIVE"), wpn("HYBRID", "MEDIUM", "projectile"), MISSILE_PROJ));
        assertEquals("phasebeam", WeaponStandIns.standInFor(
                Map.of("type", "ENERGY"), wpn("UNIVERSAL", "MEDIUM", "beam"), null));
        assertNull(WeaponStandIns.standInFor(Map.of("type", "OTHER"), wpn("DECORATIVE", "SMALL", "projectile"), null));
    }

    @Test
    void onlyAWeaponTheBaseGameDoesNotShipIsStoodIn() {
        Map<String, String> lance = Map.of("id", "tachyonlance", "type", "ENERGY", "damage/second", "1000");
        Map<String, String> modded = Map.of("id", "mod_lance", "type", "ENERGY", "damage/second", "1500");
        VanillaData.useSource(new VanillaData.Source() {
            @Override public JSONObject json(String path) throws Exception {
                if (path.equals("data/weapons/mod_lance.wpn")) return wpn("ENERGY", "LARGE", "beam");
                throw new FileNotFoundException(path);
            }

            @Override public List<Map<String, String>> csv(String path) {
                return List.of(lance, modded);
            }

            @Override public List<Map<String, String>> baseCsv(String path) {
                return List.of(lance);
            }
        });
        VanillaWeapons.clearCache();

        assertEquals("tachyonlance", VanillaWeapons.get("mod_lance").id());
        assertEquals(1000f, VanillaWeapons.get("mod_lance").damagePerSecond(), "the stand-in's own numbers");
        assertEquals("tachyonlance", VanillaWeapons.get("tachyonlance").id());
        assertNull(VanillaWeapons.get("nowhere"));
    }
}
