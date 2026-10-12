package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;

/**
 * A hull with one gun, one missile rack and one point-defence mount, fitted from
 * tables written here, so a test of the pilot's hands needs neither the game
 * install nor a battle. Call {@link #install} before {@link #ship} and
 * {@link #restore} after.
 */
public final class PilotShipFixture {

    private static final String HULL = """
            {"hullId":"test","spriteName":"graphics/ships/test.png","width":100,"height":400,
             "center":[50,200],"weaponSlots":[
              {"id":"WS 001","type":"BALLISTIC","size":"LARGE","mount":"TURRET","angle":0,"arc":360,"locations":[0,100]},
              {"id":"WS 002","type":"MISSILE","size":"LARGE","mount":"TURRET","angle":0,"arc":360,"locations":[0,0]},
              {"id":"WS 003","type":"BALLISTIC","size":"SMALL","mount":"TURRET","angle":0,"arc":360,"locations":[0,-100]}]}""";
    private static final String VARIANT = """
            {"hullId":"test","weaponGroups":[{"mode":"LINKED","weapons":
              {"WS 001":"gun_a","WS 002":"msl_a","WS 003":"pd_a"}}]}""";
    private static final String MISSILE_WEAPON = "{\"projectileSpecId\":\"msl_a_proj\"}";
    private static final String MISSILE_PROJECTILE = """
            {"id":"msl_a_proj","specClass":"missile","missileType":"MISSILE","explosionRadius":60,
             "engineSpec":{"turnAcc":1720,"turnRate":240,"acc":2500,"dec":2500}}""";

    private PilotShipFixture() {}

    public static void install() {
        VanillaData.useSource(new VanillaData.Source() {
            @Override public JSONObject json(String path) throws Exception {
                return switch (path) {
                    case "data/hulls/test.ship" -> new JSONObject(HULL);
                    case "data/variants/test/test_Standard.variant" -> new JSONObject(VARIANT);
                    case "data/weapons/msl_a.wpn" -> new JSONObject(MISSILE_WEAPON);
                    case "data/weapons/proj/msl_a_proj.proj" -> new JSONObject(MISSILE_PROJECTILE);
                    default -> throw new FileNotFoundException(path);
                };
            }

            @Override public List<Map<String, String>> csv(String path) {
                if (path.equals("data/hulls/ship_data.csv")) return List.of(Map.of("id", "test"));
                return List.of(
                        Map.of("id", "gun_a", "damage/shot", "100", "type", "KINETIC", "range", "2000",
                                "burst size", "1", "chargedown", "1", "proj speed", "1000", "turn rate", "20",
                                "hints", ""),
                        Map.of("id", "msl_a", "damage/shot", "200", "type", "HIGH_EXPLOSIVE", "range", "2000",
                                "burst size", "1", "chargedown", "1", "proj speed", "500", "turn rate", "20",
                                "hints", ""),
                        Map.of("id", "pd_a", "damage/shot", "10", "type", "KINETIC", "range", "500",
                                "burst size", "1", "chargedown", "0.2", "proj speed", "800", "turn rate", "100",
                                "hints", "PD"));
            }
        });
        VanillaWeapons.clearCache();
    }

    public static void restore() {
        VanillaData.useSource(null);
        VanillaWeapons.clearCache();
    }

    /** A friendly ship at {@code (x, y)} facing north. */
    public static Warship ship(float x, float y) throws Exception {
        return Warship.fit("test", "test_Standard", Faction.MARINE, x, y, 0f);
    }
}
