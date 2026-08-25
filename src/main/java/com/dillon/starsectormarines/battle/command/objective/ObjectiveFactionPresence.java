package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Shared objective-side definition of a faction that still has forces in play. */
final class ObjectiveFactionPresence {

    private ObjectiveFactionPresence() {}

    static boolean anyInPlay(BattleView sim, Faction faction) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == faction) return true;
        }
        World world = sim.world();
        for (long id : sim.getAirEntityIds()) {
            if (world.airFaction(id) != faction) continue;
            ShuttleMission mission = world.mission(id);
            if (mission == null || mission.state == ShuttleState.GONE) continue;
            boolean currentPayloadInbound = mission.marinesRemaining > 0
                    && mission.state != ShuttleState.DEPARTING;
            boolean futureSortieCommitted =
                    mission.currentCycle + 1 < mission.totalCycles;
            if (currentPayloadInbound || futureSortieCommitted) return true;
        }
        return false;
    }
}
