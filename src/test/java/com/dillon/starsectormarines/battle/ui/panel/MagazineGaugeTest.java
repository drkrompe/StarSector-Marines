package com.dillon.starsectormarines.battle.ui.panel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The counted load the strip under a weapon row draws. */
class MagazineGaugeTest {

    @Test
    void aBurstWeaponIsABeltInBlocksOfItsBurstAndAPacketWeaponShowsShells() {
        MagazineGauge belt = MagazineGauge.of(1, 8, 48, 27, 0f, 4f);
        assertEquals(MagazineGauge.Kind.LINK, belt.kind());
        assertEquals(8, belt.group());
        assertTrue(belt.groupEnd(7));
        assertFalse(belt.groupEnd(8));
        assertFalse(belt.groupEnd(47), "no gap after the last round");
        assertEquals(MagazineGauge.Kind.SHELL, MagazineGauge.of(6, 1, 8, 3, 0f, 3f).kind());
        assertEquals(MagazineGauge.Kind.RIFLE, MagazineGauge.of(1, 1, 4, 1, 0f, 3f).kind());
    }

    @Test
    void roundsLeaveFromTheFirstAndAReloadShowsNoneUntilItEnds() {
        MagazineGauge firing = MagazineGauge.of(1, 1, 4, 1, 0f, 3f);
        assertEquals(MagazineGauge.Round.LIVE, firing.round(0));
        assertEquals(MagazineGauge.Round.SPENT, firing.round(1));
        MagazineGauge reloading = MagazineGauge.of(1, 1, 4, 0, 1.5f, 3f);
        assertTrue(reloading.reloading());
        assertEquals(MagazineGauge.Round.SPENT, reloading.round(0));
        assertEquals(MagazineGauge.Round.SPENT, reloading.round(3));
    }

    @Test
    void theLastQuarterReadsLowButAReloadDoesNot() {
        assertTrue(MagazineGauge.of(1, 1, 4, 1, 0f, 3f).low());
        assertFalse(MagazineGauge.of(1, 1, 4, 2, 0f, 3f).low());
        assertTrue(MagazineGauge.of(1, 8, 48, 12, 0f, 4f).low());
        assertFalse(MagazineGauge.of(1, 1, 4, 0, 1f, 3f).low());
    }

    @Test
    void aRackTooLongForTheRowFoldsIntoSalvosAndAPartSalvoReadsPart() {
        MagazineGauge rack = MagazineGauge.of(Ordnance.MISSILE, 1, 10, 60, 37, 0f, 0f);
        assertEquals(MagazineGauge.Kind.MISSILE, rack.kind());
        assertEquals(10, rack.fold());
        assertEquals(6, rack.icons());
        assertEquals("live", rack.iconState(2));
        assertEquals("part", rack.iconState(3));
        assertEquals("spent", rack.iconState(4));
        assertTrue(rack.fits());
        assertFalse(MagazineGauge.of(Ordnance.MISSILE, 1, 5, 15, 10, 0f, 0f).folded(), "a short rack stays missile by missile");
    }

    @Test
    void theSalvosAHoldHasFiredLightUpBesideTheLiveOnes() {
        MagazineGauge rack = MagazineGauge.of(Ordnance.MISSILE, 1, 5, 15, 5, 0f, 0f).withHeld(10);
        assertEquals("live", rack.iconState(4));
        assertEquals("held", rack.iconState(5));
        assertEquals("held", rack.iconState(14));
        assertEquals("spent", MagazineGauge.of(Ordnance.MISSILE, 1, 5, 15, 5, 0f, 0f).iconState(5));

        MagazineGauge folded = MagazineGauge.of(Ordnance.MISSILE, 1, 10, 60, 30, 0f, 0f).withHeld(20);
        assertEquals("live", folded.iconState(2));
        assertEquals("held", folded.iconState(3));
        assertEquals("held", folded.iconState(4));
        assertEquals("spent", folded.iconState(5));
    }

    @Test
    void aLongBeltFoldsWholeBurstsPerIconUntilItFits() {
        MagazineGauge belt = MagazineGauge.of(Ordnance.BALLISTIC, 1, 5, 200, 131, 0f, 0f);
        assertEquals(0, belt.fold() % 5);
        assertTrue(belt.fits());
        assertEquals((200 + belt.fold() - 1) / belt.fold(), belt.icons());
    }
}
