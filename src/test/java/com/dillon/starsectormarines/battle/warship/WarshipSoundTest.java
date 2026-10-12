package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarshipSoundTest {

    private static final String HULL = """
            {"hullId":"snd","spriteName":"graphics/ships/test.png",
             "weaponSlots":[
              {"id":"WS 001","type":"BALLISTIC","size":"LARGE","mount":"TURRET","locations":[0,0],"angle":0,"arc":360}]}""";

    private static final String VARIANT_GUN = """
            {"hullId":"snd","weaponGroups":[{"weapons":{"WS 001":"gun"}}]}""";
    private static final String VARIANT_LANCE = """
            {"hullId":"snd","weaponGroups":[{"weapons":{"WS 001":"lance"}}]}""";

    @AfterEach
    void restore() {
        VanillaData.useSource(null);
        VanillaWeapons.clearCache();
    }

    private static void install(String variant) {
        VanillaData.useSource(new VanillaData.Source() {
            @Override public JSONObject json(String path) throws Exception {
                return switch (path) {
                    case "data/hulls/snd.ship" -> new JSONObject(HULL);
                    case "data/variants/snd/snd_Standard.variant" -> new JSONObject(variant);
                    case "data/weapons/gun.wpn" -> new JSONObject(
                            "{\"specClass\":\"projectile\",\"projectileSpecId\":\"gun_shot\","
                                    + "\"fireSoundTwo\":\"gun_fire_two\"}");
                    case "data/weapons/proj/gun_shot.proj" -> new JSONObject(
                            "{\"specClass\":\"projectile\",\"behaviorSpec\":{\"behavior\":\"PROXIMITY_FUSE\","
                                    + "\"explosionSpec\":{\"radius\":55,\"sound\":\"gun_explosion\"}}}");
                    case "data/weapons/lance.wpn" -> new JSONObject(
                            "{\"specClass\":\"beam\",\"width\":40,\"fireSoundOne\":\"lance_start\","
                                    + "\"fireSoundTwo\":\"lance_loop\"}");
                    default -> throw new java.io.FileNotFoundException(path);
                };
            }

            @Override public List<Map<String, String>> csv(String path) {
                return List.of(
                        Map.of("id", "gun", "damage/shot", "150", "type", "HIGH_EXPLOSIVE", "range", "1500",
                                "burst size", "6", "burst delay", "0.1", "chargedown", "5",
                                "proj speed", "1500", "turn rate", "60", "hints", ""),
                        Map.of("id", "lance", "damage/shot", "0", "damage/second", "1000", "type", "ENERGY",
                                "range", "1500", "chargedown", "1", "turn rate", "10", "hints", ""));
            }
        });
        VanillaWeapons.clearCache();
    }

    @Test
    void firstSoundNamedIsTheFireSoundAndTheSecondIsTheFallback() {
        install(VARIANT_GUN);
        VanillaWeapon gun = VanillaWeapons.get("gun");
        assertEquals("gun_fire_two", gun.fireSoundId(), "a gun that names only fireSoundTwo is heard by it");
        assertEquals("gun_explosion", gun.impactSoundId(), "the proximity-fuse blast names the landing sound");
        VanillaWeapon lance = VanillaWeapons.get("lance");
        assertEquals("lance_start", lance.fireSoundId(), "fireSoundOne wins when both are named");
        assertNull(lance.impactSoundId(), "a beam has no projectile and so no landing sound");
    }

    @Test
    void theGamesOwnWeaponsResolveTheirSoundIds() {
        InstalledVanillaData.install();
        if (VanillaWeapons.get("devastator") == null) return; // no install on this machine
        VanillaWeapon devastator = VanillaWeapons.get("devastator");
        assertEquals("devastator_fire", devastator.fireSoundId());
        assertEquals("devastator_explosion", devastator.impactSoundId());
        assertEquals("heavy_burst_laser_fire", VanillaWeapons.get("heavyburst").fireSoundId());
        assertEquals("tachyon_lance_fire", VanillaWeapons.get("tachyonlance").fireSoundId());
    }

    private static SceneWorld world(String variant, float targetX) throws Exception {
        install(variant);
        SceneWorld world = SceneBuilder.openGround(120, 60).missionCompletion(false)
                .unit("target", Faction.DEFENDER, UnitType.MARINE, (int) targetX, 30)
                .unit("distant", Faction.MARINE, UnitType.MARINE, 2, 2)
                .build();
        BattleSimulation sim = world.sim();
        Warship ship = Warship.fit("snd", "snd_Standard", Faction.MARINE, 20f, 30f, 0f);
        ship.order(new WarshipOrder.FireOnArea(targetX, 30f, 10f, false));
        sim.addWarship(ship);
        return world;
    }

    private static List<WarshipSoundCue> play(SceneWorld world, int ticks) {
        BattleSimulation sim = world.sim();
        List<WarshipSoundCue> heard = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            sim.advance(BattleSimulation.TICK_DT * 1.001f);
            heard.addAll(sim.warships().cuesThisFrame());
        }
        return heard;
    }

    @Test
    void aBurstIsHeardAsAThinnedReportAndItsShellsLandWithTheirOwnSound() throws Exception {
        List<WarshipSoundCue> heard = play(world(VARIANT_GUN, 70f), 300);
        List<WarshipSoundCue> fires = heard.stream().filter(c -> c.kind() == WarshipSoundCue.Kind.FIRE).toList();
        List<WarshipSoundCue> impacts = heard.stream().filter(c -> c.kind() == WarshipSoundCue.Kind.IMPACT).toList();
        assertFalse(fires.isEmpty(), "firing asks for a sound");
        assertTrue(fires.stream().allMatch(c -> c.soundId().equals("gun_fire_two")));
        assertTrue(fires.size() < 6, "a six-round burst is thinned, not a cue per round: " + fires.size());
        assertFalse(impacts.isEmpty(), "landing asks for a sound");
        assertTrue(impacts.stream().allMatch(c -> c.soundId().equals("gun_explosion")));
        assertTrue(impacts.stream().allMatch(c -> Math.abs(c.x() - 70f) < 12f), "heard where it lands");
        assertTrue(fires.stream().allMatch(c -> Math.abs(c.x() - 20f) < 3f), "heard at the muzzle");
    }

    @Test
    void aLanceSoundsOnceWhenItLightsAndNotWhileItTelegraphs() throws Exception {
        SceneWorld world = world(VARIANT_LANCE, 70f);
        List<WarshipSoundCue> early = play(world, 30); // one second: still telegraphing
        assertTrue(early.isEmpty(), "a telegraphing lance is silent: " + early);
        List<WarshipSoundCue> heard = play(world, 300);
        List<WarshipSoundCue> lit = heard.stream().filter(c -> c.kind() == WarshipSoundCue.Kind.BEAM_LIT).toList();
        assertEquals(1, lit.size(), "one cue for one sweep: " + heard);
        assertEquals("lance_start", lit.get(0).soundId());
    }

    @Test
    void aBurningLanceAsksForItsLoopEveryTickAndOnlyThen() throws Exception {
        SceneWorld world = world(VARIANT_LANCE, 70f);
        assertTrue(play(world, 30).isEmpty(), "silent while telegraphing");
        BattleSimulation sim = world.sim();
        int burningTicks = 0;
        int loopTicks = 0;
        int cuesAfterOut = 0;
        for (int t = 0; t < 400; t++) {
            sim.advance(BattleSimulation.TICK_DT * 1.001f);
            boolean burning = sim.lanceSweeps().active().stream().anyMatch(a -> a.isBurning());
            List<WarshipSoundCue> loops = sim.warships().cuesThisFrame().stream()
                    .filter(c -> c.kind() == WarshipSoundCue.Kind.BEAM_BURNING).toList();
            if (burning) {
                burningTicks++;
                if (!loops.isEmpty()) {
                    loopTicks++;
                    assertEquals("lance_loop", loops.get(0).soundId());
                }
            } else {
                cuesAfterOut += loops.size();
            }
        }
        assertTrue(burningTicks > 10, "the lance burned: " + burningTicks);
        assertEquals(burningTicks, loopTicks, "a loop cue on every burning tick");
        assertEquals(0, cuesAfterOut, "no loop cue outside the burn");
    }
}
