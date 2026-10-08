package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationTracker;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BattleCompletionTest {
    @Test
    void retreatFreezesDefeatAndPreservesOnlyCiviliansAlreadyEvacuated() {
        CivilianEvacuationTracker evacuation = new CivilianEvacuationTracker(3);
        evacuation.register(1L);
        evacuation.register(2L);
        evacuation.register(3L);
        evacuation.markEvacuated(1L);
        evacuation.markLost(2L);
        BattleCompletion completion = new BattleCompletion(evacuation);

        assertFalse(completion.isComplete());
        assertNull(completion.winner());
        assertTrue(completion.retreat());

        assertTrue(completion.isComplete());
        assertEquals(Faction.DEFENDER, completion.winner());
        assertEquals(1, evacuation.report().evacuated);
        assertEquals(2, evacuation.report().lost);
        assertFalse(evacuation.markEvacuated(3L));
        assertFalse(completion.retreat());
        assertFalse(completion.finish(Faction.MARINE));
        assertEquals(Faction.DEFENDER, completion.winner());
    }

    @Test
    void retreatCannotReplaceAnObjectiveVictoryOrDraw() {
        for (Faction winner : new Faction[] {Faction.MARINE, Faction.DEFENDER, null}) {
            CivilianEvacuationTracker evacuation = new CivilianEvacuationTracker(1);
            evacuation.register(1L);
            BattleCompletion completion = new BattleCompletion(evacuation);
            assertTrue(completion.finish(winner));
            assertTrue(evacuation.isSealed());
            assertFalse(completion.retreat());
            assertEquals(winner, completion.winner());
        }
    }

    @Test
    void incompleteRescueSetupRemainsAnAbsentReportAfterRetreat() {
        CivilianEvacuationTracker evacuation = new CivilianEvacuationTracker(2);
        evacuation.register(1L);
        BattleCompletion completion = new BattleCompletion(evacuation);
        assertTrue(completion.retreat());
        assertNull(evacuation.report());
    }

    @Test
    void embeddedRoomCannotRetreatOrComplete() {
        CivilianEvacuationTracker evacuation = new CivilianEvacuationTracker(1);
        evacuation.register(1L);
        BattleCompletion completion = new BattleCompletion(evacuation);
        completion.setEnabled(false);
        assertFalse(completion.retreat());
        assertFalse(completion.finish(Faction.MARINE));
        assertFalse(completion.isComplete());
        assertFalse(evacuation.isSealed());
        completion.setEnabled(true);
        assertTrue(completion.retreat());
    }
}
