package com.dillon.starsectormarines.battle.story;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.RelayNineCommand;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.RelayNineStoryHooks.Facts;
import com.dillon.starsectormarines.battle.story.RelayNineStoryHooks.Result;
import com.dillon.starsectormarines.battle.unit.Faction;
import java.util.ArrayList;

/** Post-tick observer; a proposed hold is not an established garrison. */
public final class RelayNineStoryObserver {
    private final RelayNineStoryHooks hooks;
    public RelayNineStoryObserver(StoryBeatLog log) { hooks = new RelayNineStoryHooks(log); }
    public void retreat(int tick) { hooks.finish(tick, Result.RETREAT); }
    public void observe(BattleSimulation sim) {
        var sites = new ArrayList<>(sim.getCompoundService().getRecords());
        boolean player = false, employer = false;
        var holding = new ArrayList<Integer>();
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.identity().type(unit).combatant) continue;
            player |= sim.identity().faction(unit) == Faction.MARINE;
            employer |= sim.identity().faction(unit) == Faction.ALLY;
        }
        for (var squad : sim.getSquads()) {
            var directive = sim.getSquadCommandDirective(squad.id);
            if (directive == null || directive.authority() != CommandAuthority.MISSION_COMMAND
                    || !RelayNineCommand.issuer(squad.faction).equals(directive.issuer()) || sim.squadMemberCount(squad.id) <= 0) continue;
            var assignment = directive.assignment();
            if (squad.faction != Faction.ALLY || assignment == null || assignment.kind() != AssignmentKind.DEFEND_AREA) continue;
            for (int site = 1; site < sites.size(); site++) {
                var r = sites.get(site);
                if (r.state != CompoundState.MARINE_HELD || assignment.targetCellX() != r.captureCellX
                        || assignment.targetCellY() != r.captureCellY) continue;
                int zone = sim.getCompoundService().captureZoneId(r, sim);
                for (int member = 0; member < sim.squadMemberCount(squad.id); member++) {
                    long unit = sim.squadMemberAt(squad.id, member);
                    int x = sim.world().cellX(unit), y = sim.world().cellY(unit);
                    if (x > r.node.left && x < r.node.right && y > r.node.top && y < r.node.bottom
                            && sim.getZoneGraph().zoneIdAt(x, y) == zone) { if (!holding.contains(site)) holding.add(site); }
                }
            }
        }
        hooks.observe(new Facts(sim.getSimTickIndex(), player, employer, holding, sim.getRelayNineSequence().arrived(),
                sites.stream().map(r -> r.state).toList(), !sim.isComplete() ? Result.RUNNING
                : sim.getWinner() == Faction.MARINE ? Result.VICTORY : Result.DEFEAT));
    }
}
