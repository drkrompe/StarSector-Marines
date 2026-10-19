package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A turret holding its next round stays laid on the hull it fights, and no turret leaves its slot's arc. */
class WarshipMountLayTest {

    @BeforeAll
    static void install() {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }

    /** A traversing hull gun on a slot with less than a full circle of arc. */
    private static Warship.Mount turret(Warship ship) {
        for (Warship.Mount m : ship.mounts()) {
            if (!m.slot.hardpoint() && m.weapon.turnRateDegPerSec() > 0f && m.slot.arcDegrees() >= 60f
                    && m.slot.arcDegrees() < 300f && WarshipShipRules.gunAtHull(m.weapon)) return m;
        }
        return null;
    }

    private static float offRest(Warship ship, Warship.Mount m) {
        return WarshipFacing.wrap(m.headingDegrees - (ship.facingDegrees + m.slot.angleDegrees()));
    }

    @Test
    void aTurretMeetsABearingOutsideItsArcAtTheStopAndSwingsBackInsideIt() throws Exception {
        Warship ship = Warship.fit("venture", "venture_Greyhook", Faction.DEFENDER, 0f, 0f, 0f);
        Warship.Mount m = turret(ship);
        assertNotNull(m, "a traversing turret on a limited arc");
        float half = m.slot.arcDegrees() * 0.5f;
        float rest = ship.facingDegrees + m.slot.angleDegrees();
        // Dead astern of the slot: outside the arc, so it stops at a stop.
        for (int i = 0; i < 600; i++) WarshipSystem.traverse(0.1f, ship, m, rest + 180f);
        assertEquals(half, Math.abs(offRest(ship, m)), 1e-3f, "held at the stop");
        float stop = Math.signum(offRest(ship, m));
        // To just inside the other stop: it swings back through the arc, never through the dead arc behind it.
        float target = rest - stop * (half - 1f);
        for (int i = 0; i < 600; i++) {
            WarshipSystem.traverse(0.1f, ship, m, target);
            assertTrue(Math.abs(offRest(ship, m)) <= half + 1e-3f, "inside the arc on the way");
        }
        assertEquals(-stop * (half - 1f), offRest(ship, m), 1e-3f);
    }

    @Test
    void aCoolingTurretStaysLaidOnItsHullAndRestsWithoutOne() throws Exception {
        Warship ship = Warship.fit("venture", "venture_Greyhook", Faction.DEFENDER, 0f, 0f, 0f);
        Warship.Mount m = turret(ship);
        assertNotNull(m);
        float bearing = ship.facingDegrees + m.slot.angleDegrees() + m.slot.arcDegrees() * 0.25f;
        float d = 0.5f * WarshipScale.hullReach(m.weapon.rangePx());
        double r = Math.toRadians(bearing + 90f);
        Warship foe = Warship.fit("wolf_hegemony", "wolf_hegemony_Rusk", Faction.ALLY,
                ship.mountX(m) + (float) Math.cos(r) * d, ship.mountY(m) + (float) Math.sin(r) * d, 0f);
        WarshipSystem system = new WarshipSystem();
        WarshipShipFire fire = system.shipFire();
        List<Warship> ships = List.of(ship, foe);
        ship.hullTarget(foe);
        assertFalse(Float.isNaN(WarshipShipFire.layError(ship, m)), "the foe is in this turret's reach and arc");

        m.cooldown = 5f;
        assertFalse(fire.tickMount(0.1f, system, ship, m, ships), "a cooling turret has not spent the tick on a hull");
        assertTrue(m.hullLaid, "but it is laid on the hull it will fire at");
        float[] aim = WarshipShipFire.aimFor(ship.mountX(m), ship.mountY(m), foe, m.weapon);
        assertEquals(0f, WarshipFacing.wrap(m.hullLayBearing
                - AirBody.facingToward(aim[0] - ship.mountX(m),
                aim[1] - ship.mountY(m))), 1e-3f);

        ship.hullTarget(null);
        foe.x += 100_000f;
        fire.tickMount(0.1f, system, ship, m, ships);
        assertFalse(m.hullLaid, "with no hull in reach it rests");
    }
}
