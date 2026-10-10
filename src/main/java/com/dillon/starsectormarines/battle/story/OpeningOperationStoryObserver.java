package com.dillon.starsectormarines.battle.story;

import com.dillon.starsectormarines.battle.command.OpeningOperationCommand;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommandFacts;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommandFacts.Place;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommandPicture.Reason;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.OpeningOperationStoryHooks.Facts;
import com.dillon.starsectormarines.battle.story.OpeningOperationStoryHooks.Result;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Mission-installed, post-tick observation only; never drives AI, waves, victory or RNG. */
public final class OpeningOperationStoryObserver {
    private final OpeningOperationStoryHooks hooks;
    private final List<Place> places;
    private final Set<Long> originalMilitia = new HashSet<>();

    /** Install after the authored local line is spawned, before any shuttle disembarks. */
    public OpeningOperationStoryObserver(BattleSimulation sim, OpeningOperationCommandFacts facts, StoryBeatLog log) {
        hooks = new OpeningOperationStoryHooks(facts.kind(), log);
        places = facts.depotStages().isEmpty()
                ? List.of(new Place(facts.placeName(), facts.cellX(), facts.cellY())) : facts.depotStages();
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) == Faction.ALLY && sim.identity().type(id).combatant) originalMilitia.add(id);
        }
    }

    public void observe(BattleSimulation sim) {
        boolean player = false, employer = false;
        boolean[] reached = new boolean[3];
        int locals = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (!sim.identity().type(id).combatant) continue;
            Faction faction = sim.identity().faction(id);
            if (faction == Faction.ALLY) {
                if (originalMilitia.contains(id)) locals++;
                else employer = true;
            }
            if (faction != Faction.MARINE) continue;
            player = true;
            for (int stage = 0; stage < places.size(); stage++) {
                Place place = places.get(stage);
                float dx = sim.world().x(id) - place.x(), dy = sim.world().y(id) - place.y();
                // Public proximity, not a capture or a claim to have entered the room.
                if (dx * dx + dy * dy <= 36) reached[stage] = true;
            }
        }
        boolean reserve = false, fallback = false;
        if (sim.getCommander(Faction.DEFENDER) instanceof OpeningOperationCommand command
                && command.operationPicture() != null) {
            for (var intent : command.operationPicture().squadIntents()) {
                reserve |= intent.reason() == Reason.DEPOT_RESERVE_COMMITTED;
                fallback |= intent.reason() == Reason.DEPOT_FALLBACK;
            }
        }
        int arrivals = sim.getReliefAssault() == null ? 0 : sim.getReliefAssault().arrivals().size();
        Result result = !sim.isComplete() ? Result.RUNNING
                : sim.getWinner() == Faction.MARINE ? Result.VICTORY : Result.DEFEAT;
        hooks.observe(new Facts(sim.getSimTickIndex(), player, employer, reached[0], reached[1], reached[2],
                originalMilitia.size(), locals, arrivals, reserve, fallback, result));
    }

    public void retreat(int tick) { hooks.finish(tick, Result.RETREAT); }
}
