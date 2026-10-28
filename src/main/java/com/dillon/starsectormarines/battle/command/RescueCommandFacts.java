package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.ExtractionPayloadObjective;
import com.dillon.starsectormarines.battle.evacuation.CivilianRedoubt;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mission-owned Rescue facts available to the Marine corridor strategy: the
 * cohort, the authored duties, the redoubts the cohort is holed up in (one
 * for a single-shelter rescue), the swarm bores that have opened, and the
 * company's own emplacements.
 *
 * <p>A bore is known from the moment it opens: an eruption tears a hole in
 * the colony and is announced to the player, so the commander needs no
 * sighting to know where it is, and it learns of a collapse the same way.
 */
public record RescueCommandFacts(
        ExtractionObjectiveFacts cohort,
        Map<Integer, AuthoredDuty> authoredDuties,
        List<CivilianRedoubt> redoubts,
        List<KnownBore> bores,
        List<Emplacement> emplacements) {

    public enum AuthoredDuty { SHELTER_GUARD, PICKUP_GUARD }

    /** An opened bore: its beat key ({@code 1}.. or {@code mother}), its body cell, and whether it still stands. */
    public record KnownBore(String key, boolean mother, int x, int y, boolean living) { }

    /** One of the company's living emplacements: a built turret, by cell. */
    public record Emplacement(int x, int y) { }

    public RescueCommandFacts {
        authoredDuties = Map.copyOf(authoredDuties);
        redoubts = List.copyOf(redoubts);
        bores = List.copyOf(bores);
        emplacements = List.copyOf(emplacements);
    }

    public RescueCommandFacts(ExtractionObjectiveFacts cohort, Map<Integer, AuthoredDuty> authoredDuties) {
        this(cohort, authoredDuties, List.of(), List.of(), List.of());
    }

    public RescueCommandFacts(ExtractionObjectiveFacts cohort, Map<Integer, AuthoredDuty> authoredDuties,
                              List<CivilianRedoubt> redoubts) {
        this(cohort, authoredDuties, redoubts, List.of(), List.of());
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
            // The shelter guard and the pickup guard are both allied squads,
            // so each is named by its duty rather than by its faction.
            if (sim.isShelterGuard(squad.id)) {
                duties.put(squad.id, AuthoredDuty.SHELTER_GUARD);
            } else if (squad.rescuePickupGuard) {
                duties.put(squad.id, AuthoredDuty.PICKUP_GUARD);
            }
        }
        List<KnownBore> bores = new ArrayList<>();
        for (SwarmBore bore : sim.getSwarmBores()) {
            bores.add(new KnownBore(bore.beatKey(), bore.mother(), bore.x(), bore.y(), !bore.collapsed()));
        }
        List<Emplacement> emplacements = new ArrayList<>();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE || !sim.identity().type(unit).isTurret()) continue;
            emplacements.add(new Emplacement(sim.world().cellX(unit), sim.world().cellY(unit)));
        }
        return new RescueCommandFacts(cohorts.get(0), duties, sim.getCivilianRedoubts(), bores, emplacements);
    }

    public AuthoredDuty authoredDuty(int squadId) {
        return authoredDuties.get(squadId);
    }
}
