package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The duel helm's range rule, its station on the circle, and the variant's flux fittings for a ship fighting hulls. */
class WarshipDuelHelmTest {

    @BeforeAll
    static void install() {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }

    @Test
    void theOutrangingHullHoldsOffBetweenTheReachesAndTheOutrangedOneCloses() throws Exception {
        Warship wolf = Warship.fit("wolf_hegemony", "wolf_hegemony_Assault", Faction.ALLY, 0f, 0f, 0f);
        Warship hound = Warship.fit("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, 0f, 0f, 0f);
        float wolfReach = WarshipDuelHelm.reach(wolf);
        float houndReach = WarshipDuelHelm.reach(hound);
        // The pulse laser (600) outranges the chaingun (450): the game's own figures decide the shape of the duel.
        assertEquals(WarshipScale.cells(600f), wolfReach, 1e-3f);
        assertEquals(WarshipScale.cells(450f), houndReach, 1e-3f);
        assertTrue(WarshipDuelHelm.holdsOff(wolfReach, houndReach));
        assertFalse(WarshipDuelHelm.holdsOff(houndReach, wolfReach));
        float wolfRange = WarshipDuelHelm.preferredRange(wolfReach, houndReach);
        assertTrue(wolfRange > houndReach && wolfRange < wolfReach, "outside the Hound's reach, inside its own");
        assertEquals(WarshipDuelHelm.CLOSE_SHARE * houndReach, WarshipDuelHelm.preferredRange(houndReach, wolfReach), 1e-4f);
    }

    @Test
    void theStationSitsOnTheRangeCircleTurnedByTheSlideAndInsideTheMap() {
        float[] straight = WarshipDuelHelm.station(10f, 50f, 50f, 50f, 20f, 0f, 2f, 200f, 100f);
        assertArrayEquals(new float[]{30f, 50f}, straight, 1e-4f);
        float[] slid = WarshipDuelHelm.station(10f, 50f, 50f, 50f, 20f, 90f, 2f, 200f, 100f);
        assertEquals(50f, slid[0], 1e-3f);
        assertEquals(30f, slid[1], 1e-3f);
        float[] edge = WarshipDuelHelm.station(10f, 50f, 15f, 50f, 20f, 0f, 2f, 200f, 100f);
        assertEquals(WarshipDuelHelm.EDGE_MARGIN_CELLS + 2f, edge[0], 1e-4f, "clamped inside the map edge");
    }

    @Test
    void capacitorsAndVentsCountOnlyForAShipFightingHulls() throws Exception {
        assertArrayEquals(new int[]{7, 10}, WarshipVariants.fluxFittings("wolf_hegemony", "wolf_hegemony_Assault"));
        Warship wolf = Warship.fit("wolf_hegemony", "wolf_hegemony_Assault", Faction.ALLY, 0f, 0f, 0f);
        wolf.limitFlux();
        assertEquals(2250f, wolf.fluxCapacity(), 1e-3f, "a ship not fighting hulls keeps the hull table's figure");
        wolf.shipFire(ShipFireOrder.ALL);
        assertEquals(2250f + 7 * Warship.FLUX_PER_CAPACITOR, wolf.fluxCapacity(), 1e-3f);
        wolf.shipFire(ShipFireOrder.OFF);
        assertEquals(2250f, wolf.fluxCapacity(), 1e-3f);
    }

    @Test
    void aShipFightingHullsCarriesItsMissilesBaseGameMagazines() throws Exception {
        Warship wolf = Warship.fit("wolf_hegemony", "wolf_hegemony_Assault", Faction.ALLY, 0f, 0f, 0f);
        int sabot = VanillaWeapons.ammo("sabot");
        assertTrue(sabot > 0, "the Sabot has a magazine in the weapon table");
        for (Warship.Mount m : wolf.mounts()) {
            if (m.weapon.id().equals("sabot")) assertEquals(-1, m.ammoRemaining(), "unlimited for ground bombardment");
        }
        wolf.shipFire(ShipFireOrder.ALL);
        int racks = 0;
        for (Warship.Mount m : wolf.mounts()) {
            if (!m.weapon.id().equals("sabot")) continue;
            racks++;
            assertEquals(sabot, m.ammoRemaining());
        }
        assertEquals(2, racks);
    }
}
