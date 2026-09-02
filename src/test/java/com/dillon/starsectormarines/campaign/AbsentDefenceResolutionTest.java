package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.AbsentDefenceGrade.Outcome;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbsentDefenceResolutionTest {

    private static final long EVENT_KEY = 77L;
    private static final int DAY = 60;
    private static final int SEATS = 6;

    @Test
    void aRaidLargelyRepelledLeavesTheContractActiveAndTheDetachmentThinner() {
        Fixture fixture = new Fixture();

        // 100 attacker against 300 defender is 0.25 effectiveness — vanilla's
        // "largely repelled by the ground defences" band.
        Outcome outcome = AbsentDefenceResolution.applyLanded(
                fixture.state, fixture.payload(), fixture.roster, DAY, 300f);

        assertEquals(Outcome.HELD, outcome);
        assertEquals(ContractState.ACTIVE,
                ContractState.fromByte(fixture.state.contractState[0]));
        assertEquals(SEATS - 1, fixture.roster.stationedActiveCount(fixture.contractId));
        // Committed strength counts the wounded, whose disposition the roll decides.
        assertEquals(fixture.roster.stationedLivingCount(fixture.contractId),
                fixture.state.contractMarinesCommitted[0]);
        assertTrue(fixture.squad.stationed());
        assertEquals(Status.GARRISONED, fixture.captain.status());
    }

    @Test
    void aSuccessfulRaidOverrunsTheGarrisonAndFailsTheAssignment() {
        Fixture fixture = new Fixture();
        fixture.state.contractDefenseAttackerStrength[0] = 900f;

        Outcome outcome = AbsentDefenceResolution.applyLanded(
                fixture.state, fixture.payload(), fixture.roster, DAY, 100f);

        assertEquals(Outcome.OVERRUN, outcome);
        assertEquals(ContractState.FAILED,
                ContractState.fromByte(fixture.state.contractState[0]));
        assertEquals(0, fixture.state.contractMarinesCommitted[0]);
        assertEquals(-1, fixture.state.contractCaptainId[0]);
        assertFalse(fixture.squad.stationed());
        assertEquals(Status.ACTIVE, fixture.captain.status());
        assertEquals(1, fixture.state.repContractsFailed[fixture.state.repIndex(1L)]);
    }

    @Test
    void aRaidThatNeverLandedCostsTheDetachmentNothing() {
        Fixture fixture = new Fixture();

        Outcome outcome = AbsentDefenceResolution.applyHeld(
                fixture.state, fixture.payload(), fixture.roster, DAY);

        assertEquals(Outcome.HELD, outcome);
        assertEquals(ContractState.ACTIVE,
                ContractState.fromByte(fixture.state.contractState[0]));
        assertEquals(SEATS, fixture.roster.stationedActiveCount(fixture.contractId));
        assertEquals(SEATS, fixture.state.contractMarinesCommitted[0]);
        assertEquals(0, fixture.state.playerMrbRep);
    }

    /** An unestimated attacker still landed on somebody: the middle band, not a free pass. */
    @Test
    void anUnestimatedAttackerSettlesInTheUncertainBand() {
        Fixture fixture = new Fixture();
        fixture.state.contractDefenseAttackerStrength[0] = 0f;

        Outcome outcome = AbsentDefenceResolution.applyLanded(
                fixture.state, fixture.payload(), fixture.roster, DAY, 4_000f);

        assertEquals(Outcome.HELD_WITH_LOSSES, outcome);
        assertEquals(SEATS - 2, fixture.roster.stationedActiveCount(fixture.contractId));
    }

    @Test
    void aSettledDefenceCannotBeSettledTwice() {
        Fixture fixture = new Fixture();
        GarrisonDefensePayload payload = fixture.payload();

        assertEquals(Outcome.HELD, AbsentDefenceResolution.applyLanded(
                fixture.state, payload, fixture.roster, DAY, 300f));
        int survivors = fixture.roster.stationedActiveCount(fixture.contractId);
        int committed = fixture.state.contractMarinesCommitted[0];

        assertNull(AbsentDefenceResolution.applyLanded(
                fixture.state, payload, fixture.roster, DAY, 300f));
        assertNull(AbsentDefenceResolution.applyHeld(
                fixture.state, payload, fixture.roster, DAY));

        assertEquals(survivors, fixture.roster.stationedActiveCount(fixture.contractId));
        assertEquals(committed, fixture.state.contractMarinesCommitted[0]);
    }

    /** A count-only assignment has no marines to name; the count carries the losses. */
    @Test
    void aLegacyCountOnlyAssignmentLosesTheGradedCount() {
        CampaignState state = new CampaignState();
        long contractId = state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                ContractState.IN_PROGRESS, 10, 500, -1, (byte) 0,
                -1, state.marketRegistry.intern("jangala"), -1, 0, 1_000,
                (byte) 25, (byte) 25, (byte) 100);
        state.contractMarinesCommitted[0] = 12;
        arm(state, 100f);

        Outcome outcome = AbsentDefenceResolution.applyLanded(
                state, GarrisonDefensePayload.from(state, contractId), null, DAY, 300f);

        assertEquals(Outcome.HELD, outcome);
        assertEquals(10, state.contractMarinesCommitted[0]);
        assertEquals(ContractState.ACTIVE, ContractState.fromByte(state.contractState[0]));
    }

    private static void arm(CampaignState state, float attackerStrength) {
        state.contractDefenseEventKey[0] = EVENT_KEY;
        state.contractDefenseTriggeredTick[0] = 40;
        state.contractDefenseTriggerType[0] = GarrisonDefenseTriggerType.VANILLA_RAID.toByte();
        state.contractDefenseAttackerHouseId[0] = -1L;
        state.contractDefenseAttackerFactionId[0] = state.factionRegistry.intern("pirates");
        state.contractDefenseAttackerStrength[0] = attackerStrength;
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final MarineRoster roster = new MarineRoster();
        final MarineCaptain captain =
                new MarineCaptain("Garrison Lead", null, Rank.LIEUTENANT, 0f);
        final MarineSquad squad;
        final long contractId;

        Fixture() {
            roster.add(captain);
            roster.ensureActiveSoldiers(SEATS);
            squad = roster.squads().get(0);
            int captainSlot = state.captainRegistry.intern(captain.id());
            contractId = state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                    ContractState.IN_PROGRESS, 10, 500, -1, (byte) 0,
                    captainSlot, state.marketRegistry.intern("jangala"), -1, 0, 1_000,
                    (byte) 25, (byte) 25, (byte) 100);
            state.contractMarinesCommitted[0] = SEATS;
            arm(state, 100f);
            assertTrue(roster.bindStationing(contractId, captain.id(), List.of(squad.id())));
            captain.setStatus(Status.GARRISONED);
        }

        GarrisonDefensePayload payload() {
            return GarrisonDefensePayload.from(state, contractId, roster);
        }
    }
}
