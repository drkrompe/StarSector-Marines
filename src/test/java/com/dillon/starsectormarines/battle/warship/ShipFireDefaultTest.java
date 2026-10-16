package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hostile hulls fight by default; a mission hold keeps AUTO ships off hulls, never an explicit ALL. */
class ShipFireDefaultTest {

    @BeforeAll
    static void install() {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }

    private static Warship wolf(float x, float y) throws Exception {
        return Warship.fit("wolf_hegemony", "wolf_hegemony_Assault", Faction.ALLY, x, y, 0f);
    }

    private static Warship hound(float x, float y) throws Exception {
        return Warship.fit("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, x, y, 180f);
    }

    @Test
    void anArmedShipFightsHullsByDefaultUnlessHeld() throws Exception {
        Warship wolf = wolf(40f, 50f);
        assertEquals(ShipFireOrder.AUTO, wolf.shipFire());
        assertTrue(WarshipShipFire.looksAtHulls(wolf, false));
        assertFalse(WarshipShipFire.looksAtHulls(wolf, true), "a mission hold keeps an AUTO ship off hulls");
        wolf.shipFire(ShipFireOrder.ALL);
        assertTrue(WarshipShipFire.looksAtHulls(wolf, true), "an explicit order is not held");
        wolf.shipFire(ShipFireOrder.OFF);
        assertFalse(WarshipShipFire.looksAtHulls(wolf, false));
    }

    @Test
    void aGunlessHullIsNeverSteeredIntoAFight() throws Exception {
        Warship rig = Warship.fit("crig", "crig_Standard", Faction.DEFENDER, 100f, 50f, 180f);
        assertFalse(WarshipShipFire.armedAgainstHulls(rig));
        assertFalse(WarshipShipFire.looksAtHulls(rig, false));
    }

    @Test
    void theFirstHullFoughtPutsTheShipOnItsDuelFooting() throws Exception {
        Warship wolf = wolf(40f, 50f);
        Warship hound = hound(70f, 50f);
        WarshipShipFire fire = new WarshipShipFire(new ArrayList<>(), new WarshipPointDefence());
        List<Warship> ships = List.of(wolf, hound);

        fire.held(true);
        fire.chooseTarget(wolf, ships);
        assertNull(wolf.hullTarget());
        assertFalse(wolf.onDuelFooting(), "held, the ship stays on the hull table's footing");

        fire.held(false);
        fire.chooseTarget(wolf, ships);
        assertSame(hound, wolf.hullTarget());
        assertTrue(wolf.onDuelFooting());
    }

    @Test
    void aMissionReleasesItsHoldByName() {
        WarshipSystem system = new WarshipSystem();
        HullFireHold hold = system.holdHullFire(HullFireHold.untilReleased("the order"));
        assertFalse(hold.released());
        system.releaseHullFire("someone else's");
        assertFalse(hold.released());
        system.releaseHullFire("the order");
        assertTrue(hold.released());
        assertEquals(List.of(hold), system.hullFireHolds());
    }
}
