package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefendAssignedSiteTravelTest {
    @Test void rememberedEnemyDoesNotPinSecurityAtItsLandingZone() {
        Squad squad = new Squad(1, Faction.MARINE);
        squad.assignedObjective = ObjectiveAssignment.defendSite(1, 50, 30);
        squad.centroidX = 80; squad.centroidY = 2;
        var contact = WorldState.EMPTY.with(Predicate.HAS_TARGET, true);
        assertTrue(DefendAssignedSiteGoal.INSTANCE.relevance(contact, squad, null) > 0);
        squad.centroidX = 49; squad.centroidY = 30;
        assertEquals(0f, DefendAssignedSiteGoal.INSTANCE.relevance(contact, squad, null));
        squad.centroidX = 80;
        assertEquals(0f, DefendAssignedSiteGoal.INSTANCE.relevance(
                contact.with(Predicate.MORALE_BROKEN, true), squad, null));
    }
}
