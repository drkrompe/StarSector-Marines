package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.WarshipAirRules.AirTargetClass;
import com.dillon.starsectormarines.battle.warship.WarshipAirRules.WeaponClass;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ship-versus-air weapon rule against the game's own weapon table and the
 * stock Onslaught and Paragon, and the hash-seeded hit rule's determinism.
 */
class WarshipAirRulesTest {

    @BeforeAll
    static void install() {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }

    private static VanillaWeapon gun(float rangePx, float speedPx, float spread, boolean beam) {
        return new VanillaWeapon("t", 100f, "KINETIC", rangePx, 1, 0.1f, 1f, speedPx, spread, 60f,
                beam, 0f, 0f, false, false, 0f, 0f, null, null, null, null, 0f, 0f, null, 0f,
                null, null, null, null, null, null, null);
    }

    private static Map<String, WeaponClass> classesOf(String hull, String variant) throws Exception {
        Warship ship = Warship.fit(hull, variant, Faction.MARINE, 50f, 50f, 0f);
        Map<String, WeaponClass> out = new HashMap<>();
        for (Warship.Mount m : ship.mounts()) {
            WeaponClass c = WarshipAirRules.classify(m);
            WeaponClass before = out.put(m.weapon.id(), c);
            // The same weapon in the same size of slot is always the same class.
            if (before != null) assertEquals(before, c, m.weapon.id());
        }
        return out;
    }

    @Test
    void theOnslaughtsGunsAreSortedByWeaponNotByShip() throws Exception {
        Map<String, WeaponClass> classes = classesOf("onslaught", "onslaught_Standard");
        // Pure point defence: flak and vulcans.
        for (String id : List.of("flak", "dualflak", "vulcan")) {
            assertEquals(WeaponClass.POINT_DEFENCE, classes.get(id), id);
        }
        // Devastator carries PD_ALSO (anti-fighter, area): it may engage anything.
        assertEquals(WeaponClass.FLAK, classes.get("devastator"));
        // Medium and small guns with no hint are judged on size.
        for (String id : List.of("lightac", "heavyac", "hveldriver", "annihilatorpod")) {
            assertEquals(WeaponClass.FLAK, classes.get(id), id);
        }
        // The large assault guns are too slow to track a fighter.
        assertEquals(WeaponClass.ASSAULT, classes.get("mark9"));
        assertEquals(WeaponClass.ASSAULT, classes.get("tpc"));
    }

    @Test
    void theParagonsGunsAreSortedByWeaponNotByShip() throws Exception {
        Map<String, WeaponClass> classes = classesOf("paragon", "paragon_Elite");
        assertEquals(WeaponClass.POINT_DEFENCE, classes.get("pdlaser"));
        assertEquals(WeaponClass.FLAK, classes.get("taclaser"));
        assertEquals(WeaponClass.FLAK, classes.get("gravitonbeam"));
        assertEquals(WeaponClass.FLAK, classes.get("heavyneedler"));
        // STRIKE wins over everything: the Reaper is a torpedo in a small slot.
        assertEquals(WeaponClass.ASSAULT, classes.get("reaper"));
        assertEquals(WeaponClass.ASSAULT, classes.get("tachyonlance"));
        assertEquals(WeaponClass.ASSAULT, classes.get("autopulse"));
    }

    @Test
    void realHintsDecideTheirClassAtAnySize() {
        // swarmer: ANTI_FTR on a missile, even in a large slot.
        assertEquals(WeaponClass.FLAK, WarshipAirRules.classify(VanillaWeapons.hints("swarmer"), "LARGE", false));
        // reaper: STRIKE, even in a small slot.
        assertEquals(WeaponClass.ASSAULT, WarshipAirRules.classify(VanillaWeapons.hints("reaper"), "SMALL", false));
        // pdburst: "PD, ANTI_FTR" is still a point-defence weapon, which never shoots ships.
        assertEquals(WeaponClass.POINT_DEFENCE, WarshipAirRules.classify(VanillaWeapons.hints("pdburst"), "MEDIUM", false));
        // heavyburst: PD, ANTI_FTR in a large slot.
        assertEquals(WeaponClass.POINT_DEFENCE, WarshipAirRules.classify(VanillaWeapons.hints("heavyburst"), "LARGE", false));
        // A weapon the table does not know has no hints and is judged on its slot.
        assertEquals(WeaponClass.FLAK, WarshipAirRules.classify(VanillaWeapons.hints("not_a_weapon"), "MEDIUM", false));
        assertEquals(WeaponClass.ASSAULT, WarshipAirRules.classify(VanillaWeapons.hints("not_a_weapon"), "LARGE", false));
    }

    @Test
    void whichClassMayShootWhichAircraft() {
        assertTrue(WarshipAirRules.mayEngage(WeaponClass.FLAK, AirTargetClass.FIGHTER));
        assertTrue(WarshipAirRules.mayEngage(WeaponClass.FLAK, AirTargetClass.TRANSPORT));
        assertTrue(WarshipAirRules.mayEngage(WeaponClass.POINT_DEFENCE, AirTargetClass.FIGHTER));
        assertFalse(WarshipAirRules.mayEngage(WeaponClass.POINT_DEFENCE, AirTargetClass.TRANSPORT));
        assertFalse(WarshipAirRules.mayEngage(WeaponClass.ASSAULT, AirTargetClass.FIGHTER));
        assertTrue(WarshipAirRules.mayEngage(WeaponClass.ASSAULT, AirTargetClass.TRANSPORT));
    }

    @Test
    void aFighterIsHarderToHitThanATransportAtTheSameCrossing() {
        VanillaWeapon w = gun(900f, 700f, 0f, false);
        float transport = WarshipAirRules.roundHitChance(w, AirTargetClass.TRANSPORT, 10f, 6f);
        float fighter = WarshipAirRules.roundHitChance(w, AirTargetClass.FIGHTER, 10f, 6f);
        assertEquals(WarshipPointDefence.roundHitChance(w, 10f, 6f), transport, 1e-6);
        assertEquals(transport * WarshipAirRules.FIGHTER_EVASION, fighter, 1e-6);
    }

    @Test
    void fasterCrossingAndLongerRangeCostAccuracyAndABeamIgnoresTheCrossing() {
        VanillaWeapon w = gun(900f, 700f, 0f, false);
        float slow = WarshipAirRules.roundHitChance(w, AirTargetClass.FIGHTER, 10f, 2f);
        float fast = WarshipAirRules.roundHitChance(w, AirTargetClass.FIGHTER, 10f, 20f);
        float far = WarshipAirRules.roundHitChance(w, AirTargetClass.FIGHTER, 35f, 2f);
        assertTrue(slow > fast);
        assertTrue(slow > far);
        VanillaWeapon beam = gun(900f, 0f, 0f, true);
        assertEquals(WarshipAirRules.roundHitChance(beam, AirTargetClass.TRANSPORT, 10f, 0f),
                WarshipAirRules.roundHitChance(beam, AirTargetClass.TRANSPORT, 10f, 500f), 1e-6);
    }

    @Test
    void theHashIsPureAndSpreadsEvenly() {
        long seed = WarshipAirRules.roundSeed("WS 018", 7, 42L, Faction.MARINE.ordinal());
        assertEquals(seed, WarshipAirRules.roundSeed("WS 018", 7, 42L, Faction.MARINE.ordinal()));
        assertNotEquals(seed, WarshipAirRules.roundSeed("WS 018", 8, 42L, Faction.MARINE.ordinal()));
        assertNotEquals(seed, WarshipAirRules.roundSeed("WS 019", 7, 42L, Faction.MARINE.ordinal()));
        assertNotEquals(seed, WarshipAirRules.roundSeed("WS 018", 7, 43L, Faction.MARINE.ordinal()));
        assertNotEquals(seed, WarshipAirRules.roundSeed("WS 018", 7, 42L, Faction.DEFENDER.ordinal()));
        assertEquals(WarshipAirRules.unit(seed, 0), WarshipAirRules.unit(seed, 0));
        assertNotEquals(WarshipAirRules.unit(seed, 0), WarshipAirRules.unit(seed, 1));
        double sum = 0;
        int below = 0;
        int n = 20000;
        for (int i = 0; i < n; i++) {
            float u = WarshipAirRules.unit(WarshipAirRules.roundSeed("WS 001", i, 9L, 0), 0);
            assertTrue(u >= 0f && u < 1f);
            sum += u;
            if (u < 0.3f) below++;
        }
        assertEquals(0.5, sum / n, 0.02);
        assertEquals(0.3, below / (double) n, 0.02);
    }

    @Test
    void aProjectileLeadsACraftByItsVelocityTimesTheFlightTime() {
        VanillaWeapon w = gun(900f, 700f, 0f, false);
        float speed = WarshipScale.cells(700f);
        // Craft 20 cells east, moving north at 5 cells/s.
        float[] aim = WarshipAirRules.leadPoint(0f, 0f, 20f, 0f, 0f, 5f, w);
        float t = (float) Math.hypot(aim[0], aim[1]) / speed;
        assertEquals(20f, aim[0], 1e-4);
        assertEquals(5f * t, aim[1], 1e-3);
        assertTrue(aim[1] > 0f);
        float[] still = WarshipAirRules.leadPoint(0f, 0f, 20f, 0f, 0f, 0f, w);
        assertEquals(0f, still[1], 1e-6);
        float[] beam = WarshipAirRules.leadPoint(0f, 0f, 20f, 0f, 0f, 5f, gun(900f, 0f, 0f, true));
        assertEquals(0f, beam[1], 1e-6);
        assertEquals(0f, WarshipAirRules.flightSeconds(gun(900f, 0f, 0f, true), 20f), 1e-6);
    }

    @Test
    void onlyTheCrossComponentOfAVelocityHasToBeLed() {
        assertEquals(0f, WarshipAirRules.crossingSpeed(0f, 0f, 10f, 0f, 7f, 0f), 1e-5);
        assertEquals(7f, WarshipAirRules.crossingSpeed(0f, 0f, 10f, 0f, 0f, 7f), 1e-5);
        assertEquals(7f, WarshipAirRules.crossingSpeed(0f, 0f, 10f, 0f, 0f, -7f), 1e-5);
    }
}
