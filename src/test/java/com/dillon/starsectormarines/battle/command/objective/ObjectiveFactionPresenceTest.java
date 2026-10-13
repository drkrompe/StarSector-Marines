package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectiveFactionPresenceTest {
    @Test void filteredPersonnelCanBeAbsentWhileDeployablesAndCommittedArrivalsRemain() {
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(16, 16), null);
        long drone = roster.spawn(new EntitySpec("builder", Faction.MARINE, UnitType.DRONE, 4, 4));
        AtomicInteger pending = new AtomicInteger();
        BattleView view = (BattleView) Proxy.newProxyInstance(BattleView.class.getClassLoader(),
                new Class<?>[]{BattleView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "liveUnitCount" -> 1;
                    case "liveUnitAt" -> drone;
                    case "identity" -> roster.identity();
                    case "pendingGroundArrivals" -> args[0] == Faction.MARINE ? pending.get() : 0;
                    case "getAirEntityIds" -> new long[0];
                    case "isCapturedStructure" -> false;
                    case "world", "survivorPickup" -> null;
                    default -> throw new AssertionError("Unexpected read: " + method.getName());
                });
        assertTrue(ObjectiveFactionPresence.anyInPlay(view, Faction.MARINE));
        assertFalse(ObjectiveFactionPresence.anyInPlay(view, Faction.MARINE, id -> false));
        pending.set(2);
        assertTrue(ObjectiveFactionPresence.anyInPlay(view, Faction.MARINE, id -> false));
        assertFalse(ObjectiveFactionPresence.anyInPlay(view, Faction.DEFENDER, id -> true));
        pending.set(0);
        assertTrue(ObjectiveFactionPresence.anyInPlay(view, Faction.MARINE, id -> id == drone));
    }
}
