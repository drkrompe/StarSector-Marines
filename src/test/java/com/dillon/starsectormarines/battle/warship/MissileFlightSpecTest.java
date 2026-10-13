package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.warship.MissileFlightSpec.Guidance;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** What a missile's own spec says about how it flies and what it carries. */
class MissileFlightSpecTest {

    @AfterEach
    void restore() {
        VanillaData.useSource(null);
        VanillaWeapons.clearCache();
    }

    private static JSONObject json(String text) throws Exception {
        return new JSONObject(text);
    }

    private static final String REAPER_PROJ = """
            {"id":"reaper_torp","specClass":"missile","missileType":"ROCKET",
             "sprite":"graphics/missiles/missile_torpedo_compact.png",
             "explosionColor":[255,100,100,255],"explosionRadius":350,
             "explosionSpec":{"duration":0.1,"radius":175,"coreRadius":75},
             "armingTime":0.5,
             "engineSpec":{"turnAcc":0,"turnRate":0,"acc":500,"dec":0},
             "engineSlots":[{"id":"ES1","style":"CUSTOM","width":10.0,"length":80.0,"angle":180.0,
               "styleSpec":{"engineColor":[255,100,100,255],"contrailDuration":2,"contrailWidthMult":1.5,
                            "contrailColor":[255,100,100,50],"type":"GLOW"}}]}""";

    private static final String ANNIHILATOR_PROJ = """
            {"id":"annihilator_rocket","specClass":"missile","missileType":"ROCKET",
             "explosionColor":[255,165,0,255],"explosionRadius":75,
             "engineSpec":{"turnAcc":0,"turnRate":0,"acc":400,"dec":0}}""";

    private static final String LOCUST_PROJ = """
            {"id":"locust","specClass":"missile","missileType":"MISSILE",
             "explosionRadius":60,
             "engineSpec":{"turnAcc":1720,"turnRate":240,"acc":2500,"dec":2500}}""";

    private static final Map<String, String> REAPER_ROW = Map.of("damage/shot", "4000", "type", "HIGH_EXPLOSIVE",
            "proj speed", "400", "launch speed", "100", "flight time", "3.5", "proj hitpoints", "500", "range", "1200");

    @Test
    void aRocketWhoseTurnRateIsZeroIsUnguided() throws Exception {
        MissileFlightSpec spec = MissileFlightSpec.read(
                Map.of("proj speed", "400", "flight time", "3.75"), json(ANNIHILATOR_PROJ), null);
        assertEquals(Guidance.UNGUIDED, spec.guidance());
        assertEquals(400f, spec.accelerationPx(), 0f);
    }

    @Test
    void aMissileThatTurnsHomes() throws Exception {
        MissileFlightSpec spec = MissileFlightSpec.read(
                Map.of("proj speed", "400", "hints", "ANTI_FTR"), json(LOCUST_PROJ), null);
        assertEquals(Guidance.HOMING, spec.guidance());
        assertEquals(240f, spec.turnRateDegPerSec(), 0f);
    }

    @Test
    void theGuidedPoorHintMakesAHomingMissilePoorlyGuided() throws Exception {
        MissileFlightSpec spec = MissileFlightSpec.read(
                Map.of("proj speed", "300", "hints", "GUIDED_POOR"), json(LOCUST_PROJ), null);
        assertEquals(Guidance.POORLY_GUIDED, spec.guidance());
    }

    @Test
    void aTorpedoCarriesItsArmingTimeAndEngineLook() throws Exception {
        MissileFlightSpec spec = MissileFlightSpec.read(REAPER_ROW, json(REAPER_PROJ), null);
        assertEquals(0.5f, spec.armingSeconds(), 0f);
        assertEquals(500f, spec.hitpoints(), 0f);
        assertEquals(100f, spec.launchSpeedPx(), 0f);
        assertEquals(3.5f, spec.burnSeconds(), 0f);
        assertEquals(80f, spec.flameLengthPx(), 0f);
        assertTrue(spec.glowTrail());
        assertNull(spec.mirv());
    }

    @Test
    void notAMissileHasNoFlight() throws Exception {
        assertNull(MissileFlightSpec.read(REAPER_ROW, json("{\"specClass\":\"projectile\"}"), null));
        assertNull(MissileFlightSpec.read(REAPER_ROW, null, null));
    }

    @Test
    void theDamageRadiusIsTheExplosionSpecAndTheFireballIsTheVisualOne() throws Exception {
        VanillaWeapon reaper = VanillaWeapon.of("reaper", REAPER_ROW, null, json(REAPER_PROJ));
        assertEquals(350f, reaper.explosionRadiusPx(), 0f);
        assertEquals(175f, reaper.damageRadiusPx(), 0f);
        assertEquals(75f, reaper.coreRadiusPx(), 0f);
        assertTrue(reaper.damageRadiusPx() < reaper.explosionRadiusPx());
    }

    @Test
    void aRoundThatDeclaresNoFootprintFallsBackToItsVisualSize() throws Exception {
        VanillaWeapon rocket = VanillaWeapon.of("annihilator", Map.of("damage/shot", "200"), null, json(ANNIHILATOR_PROJ));
        assertEquals(75f, rocket.damageRadiusPx(), 0f);
        assertEquals(75f, rocket.explosionRadiusPx(), 0f);
    }

    @Test
    void aMultiWarheadMissileCarriesItsWarheadsNotTheCarriersRow() throws Exception {
        JSONObject carrier = json("""
                {"id":"type_1_mirv","specClass":"missile","missileType":"MIRV","explosionRadius":125,
                 "engineSpec":{"turnAcc":600,"turnRate":150,"acc":250,"dec":200},
                 "behaviorSpec":{"behavior":"MIRV","splitRange":1000,"minTimeToSplit":2,"canSplitEarly":true,
                   "numShots":7,"damage":500,"damageType":"HIGH_EXPLOSIVE","spreadSpeed":200,
                   "projectileSpec":"mirv_warhead"}}""");
        JSONObject warhead = json("""
                {"id":"mirv_warhead","specClass":"missile","explosionRadius":75,
                 "explosionColor":[255,165,0,255],"engineSpec":{"maxSpeed":400}}""");
        VanillaWeapon hurricane = VanillaWeapon.of("hurricane",
                Map.of("damage/shot", "500", "type", "HIGH_EXPLOSIVE", "proj speed", "250"), null, carrier, warhead);
        MissileFlightSpec.Mirv mirv = hurricane.flight().mirv();
        assertNotNull(mirv);
        assertEquals(Guidance.TWO_STAGE, hurricane.flight().guidance());
        assertEquals(7, mirv.count());
        assertEquals(500f, mirv.damage(), 0f);
        assertEquals(3500f, mirv.totalDamage(), 0f);
        assertEquals(1000f, mirv.splitRangePx(), 0f);
        assertEquals(400f, mirv.warheadSpeedPx(), 0f);
        assertEquals(75f, mirv.damageRadiusPx(), 0f);
    }

    @Test
    void aWarheadWithNoFootprintStillHasAPit() throws Exception {
        JSONObject carrier = json("""
                {"specClass":"missile","missileType":"MIRV","explosionRadius":125,
                 "engineSpec":{"turnRate":150,"acc":100},
                 "behaviorSpec":{"behavior":"MIRV","numShots":5,"damage":200,"damageType":"KINETIC",
                   "spreadSpeed":1000,"projectileSpec":"sabot_warhead2"}}""");
        JSONObject penetrator = json("{\"specClass\":\"projectile\"}");
        MissileFlightSpec.Mirv mirv = MissileFlightSpec.read(Map.of("proj speed", "150"), carrier, penetrator).mirv();
        assertEquals(1000f, mirv.warheadSpeedPx(), 0f);
        assertEquals(MissileFlightSpec.MIN_WARHEAD_RADIUS_PX, mirv.damageRadiusPx(), 0f);
    }

    @Test
    void theInstalledMultiWarheadMissilesCarryTheirTruePayloads() {
        InstalledVanillaData.install();
        VanillaWeapon hurricane = VanillaWeapons.get("hurricane");
        assumeTrue(hurricane != null, "the game install is not available");
        assertEquals(7, hurricane.flight().mirv().count());
        assertEquals(500f, hurricane.flight().mirv().damage(), 0f);
        VanillaWeapon sabot = VanillaWeapons.get("sabot");
        assertEquals(5, sabot.flight().mirv().count());
        assertEquals(200f, sabot.flight().mirv().damage(), 0f);
        assertEquals("KINETIC", sabot.flight().mirv().damageType());
        VanillaWeapon hydra = VanillaWeapons.get("hydra");
        assertEquals(6, hydra.flight().mirv().count());
        assertEquals(500f, hydra.flight().mirv().damage(), 0f);
        assertEquals("ENERGY", hydra.flight().mirv().damageType());
    }

    @Test
    void theInstalledRocketsAndHomingMissilesAreToldApart() {
        InstalledVanillaData.install();
        VanillaWeapon annihilator = VanillaWeapons.get("annihilatorpod");
        assumeTrue(annihilator != null, "the game install is not available");
        assertEquals(Guidance.UNGUIDED, annihilator.flight().guidance());
        assertEquals(Guidance.HOMING, VanillaWeapons.get("locust").flight().guidance());
        assertEquals(Guidance.HOMING, VanillaWeapons.get("harpoon").flight().guidance());
        assertEquals(0.5f, VanillaWeapons.get("reaper").flight().armingSeconds(), 0f);
        VanillaWeapon reaper = VanillaWeapons.get("reaper");
        assertEquals(175f, reaper.damageRadiusPx(), 0f);
        assertEquals(350f, reaper.explosionRadiusPx(), 0f);
    }

    @Test
    void theInstalledMultiWarheadMissilesSayWhetherTheyMaySplitEarlyAndWhetherTheyFlank() {
        InstalledVanillaData.install();
        VanillaWeapon hurricane = VanillaWeapons.get("hurricane");
        assumeTrue(hurricane != null, "the game install is not available");
        assertTrue(hurricane.flight().mirv().canSplitEarly());
        assertTrue(VanillaWeapons.get("sabot").flight().mirv().canSplitEarly());
        assertTrue(!VanillaWeapons.get("hydra").flight().mirv().canSplitEarly());
        assertTrue(VanillaWeapons.get("hydra").flight().mirv().flanks());
        assertEquals(1050f, VanillaWeapons.get("hydra").flight().mirv().flankApproachPx(), 0f);
        assertTrue(!hurricane.flight().mirv().flanks());
    }

    @Test
    void theInstalledDragonfireStandsOffAndPaintsBeforeItsBeam() {
        InstalledVanillaData.install();
        VanillaWeapon dragon = VanillaWeapons.get("dragon");
        assumeTrue(dragon != null, "the game install is not available");
        MissileFlightSpec.Standoff standoff = dragon.flight().standoff();
        assertNotNull(standoff);
        assertEquals(700f, standoff.standoffPx(), 0f);
        assertEquals(3f, standoff.paintSeconds(), 0f);
        assertEquals(1f, standoff.fireSeconds(), 0f);
        assertEquals("dragon_payload", standoff.payloadWeaponId());
        assertNull(VanillaWeapons.get("harpoon").flight().standoff());
    }
}
