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
        assertEquals(WarshipScale.hullReach(600f), wolfReach, 1e-3f);
        assertEquals(WarshipScale.hullReach(450f), houndReach, 1e-3f);
        assertTrue(WarshipDuelHelm.holdsOff(wolfReach, houndReach));
        assertFalse(WarshipDuelHelm.holdsOff(houndReach, wolfReach));
        float wolfRange = WarshipDuelHelm.preferredRange(wolfReach, houndReach);
        assertTrue(wolfRange > houndReach && wolfRange < wolfReach, "outside the Hound's reach, inside its own");
        assertEquals(WarshipDuelHelm.CLOSE_SHARE * houndReach, WarshipDuelHelm.preferredRange(houndReach, wolfReach), 1e-4f);
    }

    @Test
    void theStationIsAlongsideTheTargetOnItsBeamAndAheadSoTheLinesConverge() throws Exception {
        Warship hound = Warship.fit("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, 0f, 0f, 0f);
        WarshipEdgeHelm map = new WarshipEdgeHelm(600f, 400f);
        // The target at (300, 200) heading east (bearing 0); the ship north of it takes the north beam.
        float[] north = WarshipDuelHelm.station(290f, 220f, 300f, 200f, 0f, 20f, true, hound, map);
        assertArrayEquals(new float[]{300f + 20f * WarshipDuelHelm.CONVERGE_SHARE, 220f}, north, 1e-3f);
        float[] south = WarshipDuelHelm.station(290f, 180f, 300f, 200f, 0f, 20f, true, hound, map);
        assertArrayEquals(new float[]{300f + 20f * WarshipDuelHelm.CONVERGE_SHARE, 180f}, south, 1e-3f);
        // Dead ahead of the target: the beam the hull prefers, never a pass down its nose.
        float[] ahead = WarshipDuelHelm.station(340f, 200f, 300f, 200f, 0f, 20f, false, hound, map);
        assertEquals(180f, ahead[1], 1e-3f);
        // Near the edge the station is kept out of the hull's edge margin.
        float[] edge = WarshipDuelHelm.station(250f, 395f, 290f, 390f, 0f, 20f, true, hound, map);
        assertEquals(400f - 0.5f * hound.lengthCells - map.margin(hound), edge[1], 1e-3f, "kept off the map edge");
    }

    @Test
    void bigHullsNeverCloseNearerThanTheirLengthsAndAClearance() {
        assertEquals(10f + 8f + WarshipDuelHelm.CLEARANCE_CELLS,
                WarshipDuelHelm.preferredRange(10f, 20f, 10f, 8f), 1e-4f);
        assertEquals(WarshipDuelHelm.CLOSE_SHARE * 60f, WarshipDuelHelm.preferredRange(60f, 70f, 15f, 15f), 1e-4f);
    }

    @Test
    void noShipHoldsOffFartherThanAFewShipLengths() {
        // Guns that reach 600 against 50 would hold off at 325; two hulls 20 cells long stand off at STANDOFF_LENGTHS of their length.
        assertEquals(WarshipDuelHelm.STANDOFF_LENGTHS * 20f, WarshipDuelHelm.preferredRange(600f, 50f, 10f, 10f), 1e-4f);
        // Inside the cap the guns decide.
        assertEquals(0.5f * (60f + 40f), WarshipDuelHelm.preferredRange(60f, 40f, 15f, 15f), 1e-4f);
    }

    @Test
    void anEscortsDuelStationIsHeldOnALeashToItsCharge() {
        assertArrayEquals(new float[]{30f, 10f}, WarshipDuelHelm.leashed(new float[]{30f, 10f}, 10f, 10f, 25f), 1e-4f);
        assertArrayEquals(new float[]{35f, 10f}, WarshipDuelHelm.leashed(new float[]{80f, 10f}, 10f, 10f, 25f), 1e-4f);
    }

    @Test
    void aKnockedOutGunNoLongerSetsTheReach() throws Exception {
        Warship wolf = Warship.fit("wolf_hegemony", "wolf_hegemony_Assault", Faction.ALLY, 0f, 0f, 0f);
        Warship.Mount strongest = WarshipShipRules.strongest(wolf);
        strongest.knockedOut = true;
        Warship.Mount next = WarshipShipRules.strongest(wolf);
        assertTrue(next != strongest);
        float expected = next == null ? 0f : WarshipScale.hullReach(next.weapon.rangePx());
        assertEquals(expected, WarshipDuelHelm.reach(wolf), 1e-4f);
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
