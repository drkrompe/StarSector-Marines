package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommittedGroundArrivalTest {
    @Test void emptyGroundDoesNotWinWhileTheCountedSideStillHasCommittedArrivals() {
        AtomicInteger pending = new AtomicInteger(4);
        BattleView view = (BattleView) Proxy.newProxyInstance(BattleView.class.getClassLoader(),
                new Class<?>[]{BattleView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "liveUnitCount" -> 0;
                    case "pendingGroundArrivals" -> args[0] == Faction.DEFENDER ? pending.get() : 0;
                    case "getAirEntityIds" -> new long[0];
                    case "world" -> null; // No aircraft to inspect.
                    default -> throw new AssertionError("Unexpected read: " + method.getName());
                });
        EliminateFactionObjective attack = new EliminateFactionObjective(Faction.MARINE, Faction.DEFENDER);
        attack.tick(view);
        assertFalse(attack.isComplete());
        EliminateFactionObjective otherSide = new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE);
        otherSide.tick(view);
        assertTrue(otherSide.isComplete(), "a defender reserve cannot keep an absent player in play");
        pending.set(0);
        attack.tick(view);
        assertTrue(attack.isComplete());
    }
}
