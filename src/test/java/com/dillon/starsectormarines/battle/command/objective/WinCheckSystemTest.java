package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WinCheckSystemTest {

    private static Objective open(Faction owner) {
        return new Objective() {
            @Override public Faction owningFaction() { return owner; }
            @Override public void tick(BattleView sim) {}
            @Override public boolean isComplete() { return false; }
            @Override public boolean isFailed() { return false; }
            @Override public String displayName() { return "open"; }
        };
    }

    @Test void aSpentCompanyLosesWhateverItsObjectivesSay() {
        WinCheckSystem check = new WinCheckSystem();
        List<Objective> objectives = List.of(open(Faction.MARINE), open(Faction.DEFENDER));
        assertTrue(check.tick(objectives, true).isOngoing());
        WinCheckSystem.WinResult spent = check.tick(objectives, false);
        assertTrue(spent.complete());
        assertEquals(Faction.DEFENDER, spent.winner());
    }

    @Test void aBattleTheCompanyOwnsNoObjectiveInNeverEndsThisWay() {
        assertTrue(new WinCheckSystem().tick(List.of(open(Faction.DEFENDER)), false).isOngoing());
    }
}
