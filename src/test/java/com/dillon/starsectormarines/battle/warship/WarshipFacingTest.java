package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarshipFacingTest {

    private static final String HULL = """
            {"hullId":"test","spriteName":"graphics/ships/test.png",
             "weaponSlots":[
              {"id":"WS 001","type":"BUILT_IN","size":"LARGE","mount":"HARDPOINT","locations":[0,0],"angle":0,"arc":5},
              {"id":"WS 002","type":"BALLISTIC","size":"LARGE","mount":"TURRET","locations":[0,0],"angle":-80,"arc":220}]}""";

    private static final String VARIANT = """
            {"hullId":"test","weaponGroups":[{"weapons":{"WS 001":"tpc","WS 002":"devastator"}}]}""";

    @AfterEach
    void restore() {
        VanillaData.useSource(null);
        VanillaWeapons.clearCache();
    }

    private static Warship ship(float facing) throws Exception {
        VanillaData.useSource(new VanillaData.Source() {
            @Override public JSONObject json(String path) throws Exception {
                if (path.equals("data/hulls/test.ship")) return new JSONObject(HULL);
                if (path.equals("data/variants/test/test_Standard.variant")) return new JSONObject(VARIANT);
                throw new FileNotFoundException(path);
            }

            @Override public List<Map<String, String>> csv(String path) {
                if (path.equals("data/hulls/ship_data.csv")) {
                    return List.of(Map.of("id", "test", "max turn rate", "6", "turn acceleration", "3"));
                }
                return List.of(
                        Map.of("id", "tpc", "damage/shot", "250", "type", "ENERGY", "range", "1000",
                                "burst size", "1", "chargedown", "0.2", "proj speed", "1000", "hints", "SYSTEM"),
                        Map.of("id", "devastator", "damage/shot", "150", "type", "HIGH_EXPLOSIVE",
                                "range", "1000", "burst size", "4", "burst delay", "0.1", "chargedown", "2",
                                "proj speed", "700", "turn rate", "12", "hints", ""));
            }
        });
        VanillaWeapons.clearCache();
        return Warship.fit("test", "test_Standard", Faction.MARINE, 0f, 0f, facing);
    }

    @Test
    void theHullTurnsAtItsOwnTableRate() throws Exception {
        Warship ship = ship(0f);
        assertEquals(6f, ship.maxTurnDegPerSec, 0.001f);
        assertEquals(3f, ship.turnAccelDegPerSec2, 0.001f);
    }

    @Test
    void aWeaponReadsItsOwnTraverseRate() throws Exception {
        Warship ship = ship(0f);
        float turret = ship.mounts().stream()
                .filter(m -> m.weapon.id().equals("devastator")).findFirst().orElseThrow()
                .weapon.turnRateDegPerSec();
        assertEquals(12f, turret, 0.001f);
    }

    @Test
    void theFixedHeavyGunPullsTheNoseOntoTheTarget() throws Exception {
        Warship ship = ship(0f);
        float reach = WarshipScale.cells(1000f) * 0.5f;
        // Due east is a bearing of -90; only the nose-fixed gun is picky about it.
        float heading = WarshipFacing.bestHeading(ship, reach, 0f);
        assertEquals(-90f, heading, 5f);
    }

    @Test
    void aTargetNothingReachesLeavesTheHeadingAlone() throws Exception {
        Warship ship = ship(30f);
        float far = WarshipScale.cells(1000f) * 3f;
        assertEquals(30f, WarshipFacing.bestHeading(ship, far, 0f), 0.001f);
    }

    @Test
    void whenTwoHeadingsArmEquallyTheSmallerTurnWins() throws Exception {
        Warship ship = ship(-85f);
        float reach = WarshipScale.cells(1000f) * 0.5f;
        float heading = WarshipFacing.bestHeading(ship, reach, 0f);
        assertTrue(Math.abs(WarshipFacing.wrap(heading - -85f)) <= 10f, "turned to " + heading);
    }
}
