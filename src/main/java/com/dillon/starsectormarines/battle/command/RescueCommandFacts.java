package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.ExtractionPayloadObjective;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Mission-owned Rescue facts available to the Marine corridor strategy. */
public record RescueCommandFacts(
        ExtractionObjectiveFacts cohort,
        Map<Integer, AuthoredDuty> authoredDuties) {

    public enum AuthoredDuty { SHELTER_GUARD, PICKUP_GUARD }

    public RescueCommandFacts {
        authoredDuties = Map.copyOf(authoredDuties);
    }

    static RescueCommandFacts freeze(BattleView sim) {
        List<ExtractionObjectiveFacts> cohorts = ExtractionObjectiveDisclosure
                .freeze(sim, Faction.MARINE).stream()
                .filter(payload -> payload.kind()
                        == ExtractionPayloadObjective.Kind.COHORT)
                .toList();
        if (cohorts.size() != 1) {
            throw new IllegalStateException(
                    "civilian Rescue command requires one cohort objective");
        }
        Map<Integer, AuthoredDuty> duties = new HashMap<>();
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.MARINE) continue;
            if (squad.rescueShelterGuard) {
                duties.put(squad.id, AuthoredDuty.SHELTER_GUARD);
            } else if (squad.rescuePickupGuard) {
                duties.put(squad.id, AuthoredDuty.PICKUP_GUARD);
            }
        }
        return new RescueCommandFacts(cohorts.get(0), duties);
    }

    public AuthoredDuty authoredDuty(int squadId) {
        return authoredDuties.get(squadId);
    }
}
