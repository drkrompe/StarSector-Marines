package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.MechDuelBand.Band;
import com.dillon.starsectormarines.battle.mech.MechDuelBand.Stance;
import com.dillon.starsectormarines.battle.mech.MechDuelBand.Weapon;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The band choice on its own, with weapons written as the numbers the shipped
 * data carries (range, damage x rounds / cooldown, accuracy), so no registry
 * or simulation is involved.
 */
class MechDuelBandTest {

    private static final Weapon CHAINGUN = new Weapon(0f, 30f, 13.5f * 12 / 2f, 0.55f, 0f);
    private static final Weapon SRM = new Weapon(0f, 18f, 49.5f * 4 / 5.5f, 0.55f, 0f);
    private static final Weapon LRM = new Weapon(0f, 40f, 81f * 5 / 9f, 0.55f, 0f);
    private static final Weapon HEAVY_CANNON = new Weapon(0f, 26f, 45f / 2.5f, 0.76f, 0f);

    private static final List<Weapon> HOUND = List.of(CHAINGUN, SRM);
    private static final List<Weapon> WARDEN = List.of(HEAVY_CANNON, LRM, LRM);
    private static final List<Weapon> BULWARK = List.of(CHAINGUN, SRM, SRM);

    /** Anti-armor racks: the contact payload against a Bulwark's plate, at full efficiency. */
    private static final Weapon AT_SRM = new Weapon(0f, 18f, 66f * 4 / 5.5f, 0.55f, 0f, true);
    private static final Weapon AT_LRM = new Weapon(0f, 40f, 95f * 5 / 9f, 0.55f, 0f, true);
    private static final List<Weapon> AT_BULWARK = List.of(CHAINGUN, AT_SRM, AT_SRM);
    private static final List<Weapon> AT_HOUND = List.of(CHAINGUN, AT_SRM);

    @Test
    void outReachingWardenStandsOffBeyondTheHoundsReach() {
        Band band = MechDuelBand.choose(WARDEN, HOUND, MechRole.LR_SUPPORT);
        assertEquals(Stance.STANDOFF, band.stance());
        assertTrue(band.min() > 30f, "outside the chaingun: " + band);
        assertTrue(band.max() < 40f, "inside its own LRM reach: " + band);
    }

    @Test
    void outReachingHoldsWhateverTheRole() {
        for (MechRole role : MechRole.values()) {
            assertEquals(Stance.STANDOFF, MechDuelBand.choose(WARDEN, HOUND, role).stance(), role.name());
        }
    }

    @Test
    void brawlerHoundClosesOnTheLongRangeWarden() {
        Band band = MechDuelBand.choose(HOUND, WARDEN, MechRole.ASSAULT);
        assertEquals(Stance.CLOSE, band.stance());
        assertTrue(band.max() <= 18f, "inside its SRM band: " + band);
    }

    @Test
    void equalBulwarksDuelFromTheOuterBandNotContact() {
        Band band = MechDuelBand.choose(BULWARK, BULWARK, MechRole.ARMORED_SUPPORT);
        assertEquals(Stance.DUEL, band.stance());
        assertTrue(band.min() >= 20f && band.max() <= 30f, band.toString());
    }

    @Test
    void equalBrawlersDoNotCloseOnATie() {
        assertEquals(Stance.DUEL, MechDuelBand.choose(BULWARK, BULWARK, MechRole.ASSAULT).stance());
        assertEquals(Stance.DUEL, MechDuelBand.choose(HOUND, HOUND, MechRole.ASSAULT).stance());
    }

    @Test
    void houndDoesNotWalkIntoABulwarksTwinRacks() {
        // Its own SRM adds less than the Bulwark's two add back.
        assertEquals(Stance.DUEL, MechDuelBand.choose(HOUND, BULWARK, MechRole.ASSAULT).stance());
    }

    @Test
    void longRangeSupportNeverCloses() {
        // Against a target with an equal reach, an LR mech whose short band trades better still holds out.
        List<Weapon> lrmBulwark = List.of(CHAINGUN, SRM, LRM);
        List<Weapon> lrmTarget = List.of(LRM);
        assertEquals(Stance.CLOSE, MechDuelBand.choose(lrmBulwark, lrmTarget, MechRole.BALANCED).stance());
        assertEquals(Stance.DUEL, MechDuelBand.choose(lrmBulwark, lrmTarget, MechRole.LR_SUPPORT).stance());
    }

    @Test
    void aSingleGunHullStandsOffAShorterReachAndDuelsAnEqualOne() {
        Weapon tankGun = new Weapon(0f, 36f, 25f, 0.65f, 0f);
        Band onHound = MechDuelBand.choose(List.of(tankGun), HOUND, 1.15f);
        assertEquals(Stance.STANDOFF, onHound.stance());
        assertTrue(onHound.min() > 30f && onHound.max() < 36f, onHound.toString());
        // One gun has no shorter band to close to, whatever the bias.
        assertEquals(Stance.DUEL, MechDuelBand.choose(List.of(tankGun), List.of(tankGun), 0.5f).stance());
    }

    @Test
    void unarmedTargetIsFoughtFromTheDuelBand() {
        assertEquals(Stance.DUEL, MechDuelBand.choose(HOUND, List.of(), MechRole.ASSAULT).stance());
    }

    @Test
    void nothingToFireMeansNoBand() {
        assertNull(MechDuelBand.choose(List.of(), HOUND, MechRole.BALANCED));
    }

    @Test
    void minimumRangeAndFalloffShapeThePressure() {
        Weapon mortar = new Weapon(10f, 30f, 10f, 1f, 0.5f);
        assertEquals(0f, mortar.pressureAt(5f));
        assertEquals(10f, mortar.pressureAt(10f) / (1f - 0.5f * 10f / 30f), 1e-4f);
        assertEquals(5f, mortar.pressureAt(30f), 1e-4f);
        assertEquals(0f, mortar.pressureAt(31f));
    }

    @Test
    void equalMachinesCloseOnATieWhenTheCloseBandBringsAnAntiArmorRack() {
        Band band = MechDuelBand.choose(AT_BULWARK, AT_BULWARK, MechRole.ARMORED_SUPPORT);
        assertEquals(Stance.CLOSE, band.stance());
        assertTrue(band.max() <= 18f, "inside the rack's reach: " + band);
        assertEquals(Stance.CLOSE, MechDuelBand.choose(AT_HOUND, AT_HOUND, MechRole.ASSAULT).stance());
    }

    @Test
    void anAntiArmorRackDoesNotCloseIntoAWorseTrade() {
        // The Hound's one rack against the Bulwark's two still loses the short band.
        assertEquals(Stance.DUEL, MechDuelBand.choose(AT_HOUND, AT_BULWARK, MechRole.ASSAULT).stance());
    }

    @Test
    void longRangeSupportDoesNotCloseOnAnAntiArmorTie() {
        List<Weapon> lrSupport = List.of(CHAINGUN, AT_SRM, AT_LRM);
        assertEquals(Stance.DUEL, MechDuelBand.choose(lrSupport, lrSupport, MechRole.LR_SUPPORT).stance());
    }
}
