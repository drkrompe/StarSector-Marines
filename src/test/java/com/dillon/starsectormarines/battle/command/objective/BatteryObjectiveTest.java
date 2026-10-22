package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleView;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatteryObjectiveTest {

    private static BattleView view(Set<Long> living) {
        return (BattleView) Proxy.newProxyInstance(BattleView.class.getClassLoader(),
                new Class<?>[]{BattleView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "resolveUnit" -> living.contains((Long) args[0]) ? (Long) args[0] : 0L;
                    case "getSwarmBores" -> List.of();
                    default -> throw new AssertionError("Unexpected read: " + method.getName());
                });
    }

    @Test void theBatteryIsOverrunOnlyWhenItsLastGunIsDead() {
        Set<Long> living = new HashSet<>(Set.of(1L, 2L));
        BatteryObjective battery = new BatteryObjective(List.of(1L, 2L));
        BattleView sim = view(living);
        battery.tick(sim);
        assertFalse(battery.isFailed());
        living.remove(1L);
        battery.tick(sim);
        assertFalse(battery.isFailed(), "one gun standing still fires the barrage");
        living.remove(2L);
        battery.tick(sim);
        assertTrue(battery.isFailed());
        assertFalse(battery.isComplete());
    }

    @Test void aBattleThatDugNoGunInHasNoBatteryToLose() {
        BatteryObjective battery = new BatteryObjective(List.of());
        battery.tick(view(Set.of()));
        assertFalse(battery.isFailed());
    }
}
