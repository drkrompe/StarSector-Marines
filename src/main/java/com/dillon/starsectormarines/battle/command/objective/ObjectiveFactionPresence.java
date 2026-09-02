package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.Set;

/** Shared objective-side definition of a faction that still has forces in play. */
final class ObjectiveFactionPresence {

    private ObjectiveFactionPresence() {}

    static boolean anyInPlay(BattleView sim, Faction faction) {
        return anyInPlay(sim, Set.of(faction));
    }

    /**
     * True while any of {@code factions} still has forces in play. A terminal
     * check names the sides it counts, so a side present on the map but absent
     * from the set does not keep the check open — an allied militia standing
     * beside a wiped company neither ends nor prolongs the company's battle.
     */
    static boolean anyInPlay(BattleView sim, Set<Faction> factions) {
        if (factions == null || factions.isEmpty()) return false;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (factions.contains(sim.identity().faction(unit))) return true;
        }
        World world = sim.world();
        for (long id : sim.getAirEntityIds()) {
            if (!factions.contains(world.airFaction(id))) continue;
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
