package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ship-versus-ship rules: which of the duel's weapons may fire at a hull, the cone's miss, where a miss comes
 * down, the heading that brings a hardpoint onto a hull, and fitting a skin as its base hull with its own sprite.
 */
class WarshipShipRulesTest {

    @BeforeAll
    static void install() {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }

    private static VanillaWeapon gun(float rangePx, float spread, boolean beam, float dps, boolean missile,
                                     boolean pd) {
        return new VanillaWeapon("t", 100f, "KINETIC", rangePx, 1, 0.1f, 1f, 600f, spread, 60f,
                beam, dps, 0f, missile, pd, 0f, 0f, null, null, null, null, 0f, 0f, null, 0f,
                null, null, null, null, null, null, null);
    }

    private static Map<String, Warship.Mount> mountsByWeapon(Warship ship) {
        Map<String, Warship.Mount> out = new HashMap<>();
        for (Warship.Mount m : ship.mounts()) out.put(m.weapon.id(), m);
        return out;
    }

    @Test
    void pointDefenceAndLancesDoNotFireAtAHullAndMissilesAreNotGuns() {
        assertTrue(WarshipShipRules.mayFireAtHull(gun(600f, 0f, false, 0f, false, false)));
        assertTrue(WarshipShipRules.mayFireAtHull(gun(600f, 0f, true, 200f, false, false)), "an anti-ship beam");
        assertFalse(WarshipShipRules.mayFireAtHull(gun(600f, 0f, false, 0f, false, true)), "point defence");
        assertTrue(WarshipShipRules.mayFireAtHull(gun(600f, 0f, false, 0f, true, false)), "a missile");
        assertFalse(WarshipShipRules.gunAtHull(gun(600f, 0f, false, 0f, true, false)), "but not a gun");
        assertFalse(WarshipShipRules.mayFireAtHull(gun(600f, 0f, true, 1000f, false, false)), "a lance");
        assertFalse(WarshipShipRules.mayFireAtHull(null));
    }

    @Test
    void theDuelsHullsAreTheirSkinsAndCarryTheirOwnGuns() throws Exception {
        Warship wolf = Warship.fit("wolf_hegemony", "wolf_hegemony_Assault", Faction.ALLY, 0f, 0f, 0f);
        assertEquals("wolf", wolf.hullId, "a skin fits as its base hull");
        assertEquals("graphics/ships/wolf/wolf_hegemony.png", wolf.spritePath, "with its own sprite");
        wolf.limitFlux();
        assertTrue(wolf.hasShield(), "a shield works on a ship that is on flux");
        Map<String, Warship.Mount> wolfGuns = mountsByWeapon(wolf);
        assertTrue(WarshipShipRules.mayFireAtHull(wolfGuns.get("pulselaser").weapon));
        assertTrue(WarshipShipRules.mayFireAtHull(wolfGuns.get("ioncannon").weapon));
        assertFalse(WarshipShipRules.mayFireAtHull(wolfGuns.get("pdlaser").weapon));
        assertTrue(WarshipShipRules.mayFireAtHull(wolfGuns.get("sabot").weapon), "the Sabot fires at a hull");
        assertFalse(WarshipShipRules.gunAtHull(wolfGuns.get("sabot").weapon), "but the heading is the guns'");

        Warship hound = Warship.fit("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, 0f, 0f, 0f);
        assertEquals("hound", hound.hullId);
        assertEquals("graphics/ships/hound/hound_pirates.png", hound.spritePath);
        hound.limitFlux();
        assertFalse(hound.hasShield(), "the Hound is unshielded");
        Map<String, Warship.Mount> houndGuns = mountsByWeapon(hound);
        assertTrue(WarshipShipRules.mayFireAtHull(houndGuns.get("chaingun").weapon));
        assertFalse(WarshipShipRules.mayFireAtHull(houndGuns.get("lightdualmg").weapon), "point defence");
        // The hull table's own figures, not authored ones.
        assertEquals(2000f, hound.maxHitPoints, 0.01f);
        assertEquals(400f, hound.armour, 0.01f);
        assertEquals(1500f, wolf.maxHitPoints, 0.01f);
    }

    @Test
    void theConeThrowsARoundEitherSideByUpToItsHalfAngle() {
        VanillaWeapon w = gun(600f, 10f, false, 0f, false, false);
        float edge = (float) (20.0 * Math.tan(Math.toRadians(5.0)));
        assertEquals(-edge, WarshipShipRules.coneMiss(w, 20f, 0f), 1e-4f);
        assertEquals(edge, WarshipShipRules.coneMiss(w, 20f, 0.99999f), 1e-3f);
        assertEquals(0f, WarshipShipRules.coneMiss(w, 20f, 0.5f), 1e-4f);
        assertEquals(0f, WarshipShipRules.coneMiss(gun(600f, 0f, false, 0f, false, false), 20f, 0.9f), 1e-6f,
                "a weapon with no spread flies true");
    }

    @Test
    void aMissComesDownOnAlongItsLineWithinItsRemainingReach() {
        // Fired east from the origin, passing the hull at x=20 with a reach of 30: ten cells left to carry.
        float[] near = WarshipShipRules.strayLanding(0f, 0f, 20f, 0f, 30f, 0f);
        float[] far = WarshipShipRules.strayLanding(0f, 0f, 20f, 0f, 30f, 1f);
        assertEquals(0f, near[1], 1e-5f);
        assertEquals(20f + 10f * WarshipShipRules.MIN_CARRY_SHARE, near[0], 1e-4f);
        assertEquals(30f, far[0], 1e-4f);
        // A round already at the end of its reach still comes down a little beyond the hull, never on it.
        float[] spent = WarshipShipRules.strayLanding(0f, 0f, 30f, 0f, 30f, 0.5f);
        assertEquals(30f + WarshipShipRules.MIN_CARRY_CELLS, spent[0], 1e-4f);
    }

    @Test
    void aHardpointShipTurnsItsNoseOntoTheHull() throws Exception {
        Warship hound = Warship.fit("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, 50f, 50f, 0f);
        // The target is due south of the hull: the chaingun hardpoint bears only with the nose on it.
        float heading = WarshipShipRules.bestHeading(hound, 50f, 20f);
        Warship.Mount chaingun = mountsByWeapon(hound).get("chaingun");
        float mx = Warship.mountX(hound.x, chaingun.slot, heading);
        float my = Warship.mountY(hound.y, chaingun.slot, heading);
        assertTrue(Warship.bears(chaingun, heading, mx, my, 50f, 20f, 10f), "heading " + heading);
    }

    @Test
    void aShipWhoseEngagedBroadsideIsStrippedWearsRoundToItsFreshSide() throws Exception {
        Warship ship = Warship.fit("onslaught", "onslaught_Standard", Faction.ALLY, 0f, 0f, 0f);
        WarshipPlating plating = ship.plating();
        assertTrue(plating != null, "the capital carries an armour grid");
        // A hull to port, abeam: the bearing straight off the nose's counter-clockwise side.
        float tx = -40f;
        float ty = 0f;
        float fresh = WarshipShipRules.bestHeading(ship, tx, ty);
        float toTarget = WarshipFacing.bearing(0f, 0f, tx, ty);
        assertTrue(Math.sin(Math.toRadians(toTarget - fresh)) > 0.5, "with both sides whole it keeps port engaged: " + fresh);
        for (int r = 0; r < plating.rows; r++) {
            for (int c = 0; c < plating.cols; c++) {
                if (plating.isPlated(c, r) && plating.section(c, r) == WarshipPlating.Section.PORT) {
                    for (int k = 0; k < 6; k++) plating.hit(r * plating.cols + c, 2000f, 12f, 0f);
                }
            }
        }
        assertTrue(WarshipShipRules.plateFacing(plating, 90f) < 0.5f, "port is stripped");
        assertEquals(1f, WarshipShipRules.plateFacing(plating, 0f), 1e-6f, "the nose is not a broadside");
        float worn = WarshipShipRules.bestHeading(ship, tx, ty);
        assertTrue(Math.sin(Math.toRadians(toTarget - worn)) < -0.5, "it wears round to fight starboard: " + worn);
    }
}
