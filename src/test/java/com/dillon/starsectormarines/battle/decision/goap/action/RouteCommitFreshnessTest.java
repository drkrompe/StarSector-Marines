package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadBeliefTestAccess;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A route-threat commit stands on current evidence, not on memory alone. */
class RouteCommitFreshnessTest {

    @Test
    void aCommitWithCurrentEvidenceStands() {
        assertTrue(AbstractZoneAction.commitStands(true, true));
    }

    @Test
    void aCommitRestingOnMemoryAloneLapses() {
        // The live defect: nine marines stood still for a belief's whole lifetime with the player clicking at them.
        assertFalse(AbstractZoneAction.commitStands(true, false));
    }

    @Test
    void noCommitIsNeverCreatedByFreshness() {
        assertFalse(AbstractZoneAction.commitStands(false, true));
        assertFalse(AbstractZoneAction.commitStands(false, false));
    }

    @Test
    void theSwitchRestoresTheOldBehaviour() {
        AbstractZoneAction.setRouteCommitNeedsFreshContactForEvidence(false);
        try {
            assertTrue(AbstractZoneAction.commitStands(true, false));
        } finally {
            AbstractZoneAction.setRouteCommitNeedsFreshContactForEvidence(true);
        }
    }

    @Test
    void aBeliefIsFreshOnlyInsideTheHoldWindow() {
        Squad squad = new Squad(1, Faction.MARINE);
        SquadBeliefTestAccess.observeDirect(squad, 7L, 20, 5, 100);
        int window = TacticalScoring.HOLD_AFTER_LOS_TICKS;

        assertTrue(TacticalScoring.hasFreshBelief(squad, 100));
        assertTrue(TacticalScoring.hasFreshBelief(squad, 100 + window));
        assertFalse(TacticalScoring.hasFreshBelief(squad, 100 + window + 1),
                "the belief is still remembered, but it can no longer hold a squad in place");
        assertTrue(squad.hasBelievedContacts());
    }

    @Test
    void aSquadThatRemembersNobodyHasNoFreshBelief() {
        assertFalse(TacticalScoring.hasFreshBelief(new Squad(1, Faction.MARINE), 50));
    }
}
