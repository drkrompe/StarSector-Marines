package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.StrandedDetachment;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrandedDetachmentSystemTest {

    private static final int DEADLINE = 90;

    @Test
    void theRecoveryRunsOutOnItsDeadlineAndNotBefore() {
        CampaignState state = new CampaignState();
        MarineRoster roster = new MarineRoster();
        MarineCaptain captain = new MarineCaptain("Garrison Lead", null, Rank.LIEUTENANT, 0f);
        roster.add(captain);
        roster.ensureActiveSoldiers(6);
        MarineSquad squad = roster.squads().get(0);
        long contractId = state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                ContractState.FAILED, 10, 500, -1, (byte) 0,
                state.captainRegistry.intern(captain.id()), state.marketRegistry.intern("jangala"),
                -1, 0, 1_000, (byte) 25, (byte) 25, (byte) 100);
        assertTrue(roster.bindStationing(contractId, captain.id(), List.of(squad.id())));
        captain.setStatus(Status.GARRISONED);
        state.contractStrandedUntilTick[0] = DEADLINE;
        StrandedDetachmentSystem system = new StrandedDetachmentSystem(() -> roster, id -> null);

        system.tick(state, DEADLINE - 1);
        assertTrue(StrandedDetachment.isStranded(state, 0));
        assertTrue(squad.stationed());
        // Two intervals elapsed before the deadline: six marines, two missing.
        assertEquals(4, roster.stationedLivingCount(contractId));

        system.tick(state, DEADLINE);
        assertFalse(StrandedDetachment.isStranded(state, 0));
        assertFalse(squad.stationed());
        assertEquals(Status.INJURED, captain.status());
    }

    /** Nobody left to collect ends the recovery at once rather than at the deadline. */
    @Test
    void theLastLossEndsTheRecoveryEarly() {
        CampaignState state = new CampaignState();
        MarineRoster roster = new MarineRoster();
        MarineCaptain captain = new MarineCaptain("Garrison Lead", null, Rank.LIEUTENANT, 0f);
        roster.add(captain);
        roster.ensureActiveSoldiers(1);
        MarineSquad squad = roster.squads().get(0);
        long contractId = state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                ContractState.FAILED, 10, 500, -1, (byte) 0,
                state.captainRegistry.intern(captain.id()), state.marketRegistry.intern("jangala"),
                -1, 0, 1_000, (byte) 25, (byte) 25, (byte) 100);
        assertTrue(roster.bindStationing(contractId, captain.id(), List.of(squad.id())));
        captain.setStatus(Status.GARRISONED);
        state.contractStrandedUntilTick[0] = DEADLINE;
        StrandedDetachmentSystem system = new StrandedDetachmentSystem(() -> roster, id -> null);

        system.tick(state, DEADLINE - StrandedDetachment.RECOVERY_DAYS
                + StrandedDetachment.ATTRITION_INTERVAL_DAYS);

        assertFalse(StrandedDetachment.isStranded(state, 0));
        assertFalse(squad.stationed());
    }
}
