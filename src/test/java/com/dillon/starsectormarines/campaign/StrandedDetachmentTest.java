package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.systems.NamedStationingRepairSystem;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.Status;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrandedDetachmentTest {

    private static final int DAY = 60;

    @Test
    void aStrandedDetachmentIsFoundOnItsOwnMarketOnly() {
        AbsentDefenceResolutionTest.Fixture fixture = overrun();

        assertEquals(List.of(fixture.contractId),
                StrandedDetachment.strandedAt(fixture.state, "jangala"));
        assertTrue(StrandedDetachment.strandedAt(fixture.state, "sindria").isEmpty());
    }

    @Test
    void pickupBringsTheSurvivorsAndCaptainHomeOnce() {
        AbsentDefenceResolutionTest.Fixture fixture = overrun();
        int living = fixture.roster.stationedLivingCount(fixture.contractId);

        assertEquals(living, StrandedDetachment.pickup(fixture.state, fixture.contractId,
                fixture.roster, DAY + 5, "Jangala"));

        assertFalse(fixture.squad.stationed());
        assertEquals(Status.ACTIVE, fixture.captain.status());
        assertFalse(StrandedDetachment.isStranded(fixture.state, 0));
        assertEquals(0, fixture.state.contractMarinesCommitted[0]);
        assertEquals(-1, fixture.state.contractCaptainId[0]);
        assertEquals(ChronicleEventType.DETACHMENT_RECOVERED,
                ChronicleEventType.fromByte(fixture.state.chronicleEventType[1]));
        assertEquals(living, fixture.state.chronicleSurvivorsRescued[1]);

        assertEquals(-1, StrandedDetachment.pickup(fixture.state, fixture.contractId,
                fixture.roster, DAY + 6, "Jangala"));
        assertEquals(2, fixture.state.chronicleCount);
    }

    @Test
    void anExpiredRecoveryListsTheSurvivorsMissingAndInjuresTheCaptain() {
        AbsentDefenceResolutionTest.Fixture fixture = overrun();
        List<MarineSoldier> waiting = fixture.roster.squadMembers(fixture.squad).stream()
                .filter(soldier -> soldier.status() == MarineSoldierStatus.ACTIVE
                        || soldier.status() == MarineSoldierStatus.WIA)
                .toList();
        int living = waiting.size();

        assertEquals(living, StrandedDetachment.expire(fixture.state, 0, fixture.roster,
                DAY + StrandedDetachment.RECOVERY_DAYS, "Jangala"));

        for (MarineSoldier soldier : waiting) {
            assertEquals(MarineSoldierStatus.MIA, soldier.status());
        }
        assertFalse(fixture.squad.stationed());
        assertEquals(Status.INJURED, fixture.captain.status());
        assertFalse(StrandedDetachment.isStranded(fixture.state, 0));
        assertEquals(ChronicleEventType.DETACHMENT_LOST,
                ChronicleEventType.fromByte(fixture.state.chronicleEventType[1]));
    }

    @Test
    void waitingCostsOneMarinePerSquadEachInterval() {
        AbsentDefenceResolutionTest.Fixture fixture = overrun();
        int living = fixture.roster.stationedLivingCount(fixture.contractId);

        assertTrue(StrandedDetachment.attrition(fixture.state, 0, fixture.roster,
                DAY + StrandedDetachment.ATTRITION_INTERVAL_DAYS - 1).isEmpty());
        List<String> lost = StrandedDetachment.attrition(fixture.state, 0, fixture.roster,
                DAY + StrandedDetachment.ATTRITION_INTERVAL_DAYS);

        assertEquals(1, lost.size());
        assertEquals(living - 1, fixture.roster.stationedLivingCount(fixture.contractId));
        assertEquals(living - 1, fixture.state.contractMarinesCommitted[0]);
        // The interval is spent: asking again the same day strikes nobody else.
        assertTrue(StrandedDetachment.attrition(fixture.state, 0, fixture.roster,
                DAY + StrandedDetachment.ATTRITION_INTERVAL_DAYS).isEmpty());
        assertEquals(Status.GARRISONED, fixture.captain.status());
    }

    @Test
    void theSameWaitStrikesTheSamePeople() {
        AbsentDefenceResolutionTest.Fixture first = overrun();
        AbsentDefenceResolutionTest.Fixture second = overrun();
        int day = DAY + StrandedDetachment.ATTRITION_INTERVAL_DAYS;

        assertEquals(StrandedDetachment.attrition(first.state, 0, first.roster, day),
                StrandedDetachment.attrition(second.state, 0, second.roster, day));
    }

    /** The interval landing on the deadline belongs to the expiry, not to attrition. */
    @Test
    void attritionStopsShortOfTheDeadline() {
        AbsentDefenceResolutionTest.Fixture fixture = overrun();

        StrandedDetachment.attrition(fixture.state, 0, fixture.roster,
                DAY + StrandedDetachment.RECOVERY_DAYS + 50);

        assertEquals(StrandedDetachment.RECOVERY_DAYS / StrandedDetachment.ATTRITION_INTERVAL_DAYS - 1,
                fixture.state.contractStrandedIntervals[0]);
    }

    /** The repair pass must not mistake a failed row's held survivors for a stale binding. */
    @Test
    void repairLeavesAStrandedDetachmentBound() {
        AbsentDefenceResolutionTest.Fixture fixture = overrun();

        NamedStationingRepairSystem.repair(fixture.state, fixture.roster);

        assertTrue(fixture.squad.stationed());
        assertEquals(Status.GARRISONED, fixture.captain.status());
        assertTrue(StrandedDetachment.isStranded(fixture.state, 0));
    }

    @Test
    void theSituationBoardCarriesTheRecoveryDeadline() {
        AbsentDefenceResolutionTest.Fixture fixture = overrun();

        List<CompanyClocks.Entry> clocks = CompanyClocks.rows(fixture.state, fixture.roster, DAY);

        assertEquals(1, clocks.size());
        assertEquals(CompanyClocks.Kind.RECOVERY, clocks.get(0).kind);
        assertEquals(DAY + StrandedDetachment.RECOVERY_DAYS, clocks.get(0).deadlineDay);
    }

    private static AbsentDefenceResolutionTest.Fixture overrun() {
        AbsentDefenceResolutionTest.Fixture fixture = new AbsentDefenceResolutionTest.Fixture();
        fixture.state.contractDefenseAttackerStrength[0] = 900f;
        AbsentDefenceResolution.applyLanded(fixture.state, fixture.payload(), fixture.roster,
                DAY, 100f);
        assertTrue(StrandedDetachment.isStranded(fixture.state, 0));
        return fixture;
    }
}
