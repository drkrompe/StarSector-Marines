package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Immutable setup-time disclosure of the authored Conquest patrol reserve. */
public record ConquestDefenderStartingForce(
        Set<Integer> mobileSquadIds,
        Map<Integer, Integer> homeTracks) {

    public ConquestDefenderStartingForce {
        mobileSquadIds = Set.copyOf(mobileSquadIds);
        homeTracks = Map.copyOf(homeTracks);
    }

    public static ConquestDefenderStartingForce capture(
            BattleView sim, ConquestTrackLayout tracks) {
        Set<Integer> squads = new TreeSet<>();
        Map<Integer, Integer> homes = new TreeMap<>();
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.DEFENDER
                    || sim.squadMemberCount(squad.id) <= 0) continue;
            long anchor = sim.squadMemberAt(squad.id, 0);
            if (sim.role().role(anchor) != UnitRole.PATROL) continue;
            squads.add(squad.id);
            homes.put(squad.id, tracks.trackForCell(
                    sim.world().cellX(anchor), sim.world().cellY(anchor)));
        }
        return new ConquestDefenderStartingForce(squads, homes);
    }

    public static ConquestDefenderStartingForce empty() {
        return new ConquestDefenderStartingForce(Set.of(), Map.of());
    }
}
