package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.warship.MissileWindow.Kind;
import com.dillon.starsectormarines.battle.warship.MissileWindow.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissileOpeningsTest {

    @Test
    void aShipSpendsAPairOrAQuarterOfWhatIsLeft() {
        assertEquals(2, MissileOpenings.budget(8));
        assertEquals(2, MissileOpenings.budget(1));
        assertEquals(3, MissileOpenings.budget(12));
        assertEquals(8, MissileOpenings.budget(30));
        assertEquals(MissileOpenings.PAIR, MissileOpenings.budget(-1));
    }

    @Test
    void theBudgetStopsAShipAndTheReserveHoldsItsHeavyRacksForAKill() {
        assertTrue(MissileOpenings.mayLaunch(Role.FINISHER, false, 0, 2, 6));
        assertTrue(MissileOpenings.mayLaunch(Role.FINISHER, false, 1, 2, 5));
        assertFalse(MissileOpenings.mayLaunch(Role.FINISHER, false, 2, 2, 4));
        // Down to its reserve, a finisher or a torpedo waits for an overload or a worn hull.
        assertFalse(MissileOpenings.mayLaunch(Role.TORPEDO, false, 0, 2, MissileOpenings.RESERVE));
        assertTrue(MissileOpenings.mayLaunch(Role.TORPEDO, true, 0, 2, MissileOpenings.RESERVE));
        assertFalse(MissileOpenings.mayLaunch(Role.TORPEDO, true, 2, 2, MissileOpenings.RESERVE));
        // A Sabot keeps no reserve, and a bottomless rack has none to keep.
        assertTrue(MissileOpenings.mayLaunch(Role.ANTI_SHIELD, false, 0, 2, 0));
        assertTrue(MissileOpenings.mayLaunch(Role.FINISHER, false, 0, 2, -1));
    }

    @Test
    void aForcedOrDecisiveWindowOpensANewOpeningAndASalvoJoins() {
        assertTrue(MissileOpenings.joins(true, false, Kind.SHIELD_DOWN));
        assertTrue(MissileOpenings.joins(true, false, Kind.SALVO));
        assertFalse(MissileOpenings.joins(true, false, Kind.OVERLOAD));
        assertFalse(MissileOpenings.joins(false, false, Kind.SHIELD_DOWN));
        assertFalse(MissileOpenings.joins(false, false, Kind.WORN));
        assertTrue(MissileOpenings.joins(false, true, Kind.WORN));
        assertTrue(MissileOpenings.joins(false, false, Kind.FLANK));
        assertTrue(MissileOpenings.forced(Kind.SHIELD_DOWN) && !MissileOpenings.forced(Kind.FLANK));
        assertTrue(MissileOpenings.decisive(Kind.WORN) && MissileOpenings.decisive(Kind.OVERLOAD));
    }

    @Test
    void aForcedOpeningLastsWhileTheTargetIsOpenAndAStandingOneForItsSpan() {
        assertTrue(MissileOpenings.lasts(true, 100f, true));
        assertFalse(MissileOpenings.lasts(true, 0.1f, false));
        assertTrue(MissileOpenings.lasts(false, MissileOpenings.STANDING_SECONDS - 0.1f, false));
        assertFalse(MissileOpenings.lasts(false, MissileOpenings.STANDING_SECONDS, true));
    }
}
