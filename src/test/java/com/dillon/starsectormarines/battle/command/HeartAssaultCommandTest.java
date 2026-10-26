package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.RescueCommandFacts.KnownBore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which bore the company goes for: its own lane's satellites first, then the nearest mother. */
class HeartAssaultCommandTest {

    /** Advancing north from x=100, with satellites within 30 cells across its own. */
    private final HeartAssaultCommand command = new HeartAssaultCommand(new HeartLane(true, 100, 30));

    @Test
    void satellitesInTheLaneComeBeforeAnyMother() {
        List<KnownBore> bores = List.of(
                new KnownBore("mother-1", true, 100, 40, true),
                new KnownBore("1", false, 20, 90, true),
                new KnownBore("2", false, 115, 70, true));
        assertEquals("2", command.target(bores, 100, 150).key(), "the far lane's nearer satellite is not the company's");
    }

    @Test
    void theNearestLivingMotherOnceTheLaneIsClear() {
        List<KnownBore> bores = List.of(
                new KnownBore("mother-1", true, 60, 20, true),
                new KnownBore("mother-2", true, 110, 25, true),
                new KnownBore("mother-3", true, 105, 22, false),
                new KnownBore("2", false, 115, 70, false),
                new KnownBore("1", false, 20, 90, true));
        assertEquals("mother-2", command.target(bores, 100, 60).key());
        assertNull(command.target(List.of(new KnownBore("mother-1", true, 60, 20, false)), 100, 60));
    }

    @Test
    void aSatelliteNearerAnAlliedLanesLineIsThatLanesEvenInsideTheCompanysBand() {
        // Broad Front seed 5 (2026-10-24): the company's band, 279 +/- 78, reached lane 3's satellite at x=350 beside
        // the mothers; lane 3's line is at 375. With its own satellite down the company went for it and was wiped.
        HeartAssaultCommand company = new HeartAssaultCommand(new HeartLane(true, 279, 78))
                .alongside(List.of(new HeartLane(true, 184, 56), new HeartLane(true, 375, 56)));
        List<KnownBore> bores = List.of(
                new KnownBore("mother-2", true, 355, 261, true),
                new KnownBore("3", false, 260, 219, false),
                new KnownBore("4", false, 350, 218, true));
        assertEquals("mother-2", company.target(bores, 260, 200).key(), "satellite 4 is lane 3's, so the company holds for the lanes");
        assertEquals("3", company.target(List.of(new KnownBore("3", false, 260, 219, true), bores.get(2)), 260, 100).key());
    }

    @Test
    void theCompanyGoesInOnAMotherOnlyWithTheLanesRoundHerAndThenStaysIn() {
        KnownBore mother = new KnownBore("mother-2", true, 362, 287, true);
        assertFalse(HeartAssaultCommand.goesIn(mother, HeartAssaultCommand.ALLIES_TO_PUSH - 1, null), "alone, it waits");
        assertTrue(HeartAssaultCommand.goesIn(mother, HeartAssaultCommand.ALLIES_TO_PUSH, null));
        assertTrue(HeartAssaultCommand.goesIn(mother, 0, "mother-2"), "gone in, a thinning there does not call it back");
        assertFalse(HeartAssaultCommand.goesIn(mother, 0, "mother-1"), "a new mother is gone in on afresh");
    }

    @Test
    void theSatelliteTheCompanyHelpsIsTheNearestStandingWhicheverLaneItIsAndNeverAMother() {
        KnownBore mother = new KnownBore("mother-2", true, 290, 60, true);
        KnownBore far = new KnownBore("1", false, 65, 223, true);
        KnownBore near = new KnownBore("5", false, 522, 192, true);
        assertEquals(near, HeartAssaultCommand.stranded(List.of(mother, far, near), 400f, 150f));
        assertEquals(far, HeartAssaultCommand.stranded(List.of(mother, far, new KnownBore("5", false, 522, 192, false)), 400f, 150f));
        assertNull(HeartAssaultCommand.stranded(List.of(mother), 280f, 60f));
    }

    @Test
    void aSquadFarFromWhereTheCompanyHoldsWithdrawsAndOneThereHolds() {
        assertTrue(HeartAssaultCommand.stillFallingBack(273f, 194f, 265, 30), "170 cells forward, it breaks off");
        assertFalse(HeartAssaultCommand.stillFallingBack(270f, 36f, 265, 30));
    }
}
