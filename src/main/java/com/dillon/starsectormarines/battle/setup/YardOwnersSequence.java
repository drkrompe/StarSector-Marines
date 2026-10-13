package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.YardOwnersCommand;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.ObjectiveFactionPresence;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.story.YardOwnersProgress;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.LatchkeyYard;
import java.util.List;
import java.util.Locale;

/** Referee for simultaneous territorial custody and an actual, destructible machine inspection. */
public final class YardOwnersSequence implements Objective {
    private final YardOwnersProgress progress = new YardOwnersProgress();
    private final int technicians;
    private final List<Integer> constables;
    private final List<List<EntitySpec>> reserves;
    private final StoryBeatLog log;
    private boolean initialized, released;
    public YardOwnersSequence(int technicians, List<Integer> constables, List<List<EntitySpec>> reserves, StoryBeatLog log) {
        this.technicians = technicians; this.constables = List.copyOf(constables);
        this.reserves = reserves.stream().map(List::copyOf).toList(); this.log = log;
    }
    public YardOwnersProgress progress() { return progress; }
    public int technicianSquadId() { return technicians; }
    public List<Integer> constableSquadIds() { return constables; }
    public int pendingMembers() { return released || progress.terminal() ? 0 : reserves.stream().mapToInt(List::size).sum(); }
    public YardOwnersProgress.Report report(BattleSimulation sim) {
        return progress.report(2 - sim.squadMemberCount(technicians), 8 - constables.stream().mapToInt(sim::squadMemberCount).sum());
    }
    public void advance(BattleSimulation sim) {
        if (sim.isComplete() || progress.terminal()) return;
        if (!initialized) { initialized = true; emit(sim,"mobilization"); }
        var before = progress.phase();
        int company = 0, friendly = 0, contested = 0;
        boolean technicianAtTools = false;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            var faction = sim.identity().faction(id);
            for (int site = 0; site < 3; site++) {
                var place = LatchkeyYard.SITES.get(site);
                boolean inside = LatchkeyYard.inside(sim.getTopology(), sim.world().cellX(id), sim.world().cellY(id), site);
                if (isCompany(sim,id) && inside) company |= 1 << site;
                if ((isCompany(sim,id) || faction == Faction.ALLY && sim.identity().type(id).combatant) && inside) friendly |= 1 << site;
                if (site == 1 && inside && sim.squad().squadId(id) == technicians) technicianAtTools = true;
                if (Faction.MARINE.hostileTo(faction) && sim.identity().type(id).combatant
                        && (inside || Math.hypot(sim.world().x(id)-place.x(),sim.world().y(id)-place.y()) < 12)) contested |= 1 << site;
            }
        }
        boolean toolingAlive = sim.getDoodads().stream().anyMatch(prop -> prop.cellX == LatchkeyYard.TOOL_X && prop.cellY == LatchkeyYard.TOOL_Y
                && (LatchkeyYard.TOOL_ID.equals(prop.definitionId()) || (LatchkeyYard.TOOL_ID + "-damaged").equals(prop.definitionId())));
        progress.advance(BattleSimulation.TICK_DT, ObjectiveFactionPresence.anyInPlay(sim,Faction.MARINE,id -> isCompany(sim,id)),
                toolingAlive, sim.squadMemberCount(technicians) > 0, technicianAtTools, company, friendly, contested);
        if (progress.visited(0) && !released && !progress.terminal()) release(sim);
        if (before != progress.phase()) emit(sim,progress.phase().name().toLowerCase(Locale.ROOT));
    }
    private void release(BattleSimulation sim) {
        released = true;
        for (var group : reserves) {
            int squad = sim.mintSquad(Faction.DEFENDER,UnitType.MILITIA);
            for (var member : group) sim.spawn(member.squad(squad));
            sim.getSquad(squad).originalSize = group.size();
            sim.claimSquadCommand(squad,CommandAuthority.MISSION_COMMAND,YardOwnersCommand.issuer(Faction.DEFENDER),"Committed Hookhand counterattack");
        }
        log.emitOnce(sim.getSimTickIndex(),"yard_owners.reserve",StoryBeatLog.Audience.OBSERVER_ONLY);
    }
    private static boolean isCompany(BattleSimulation sim,long id) {
        return sim.identity().faction(id) == Faction.MARINE && switch(sim.identity().type(id)) {
            case MARINE, MARINE_BLUE, MARINE_RED -> true; default -> false;
        };
    }
    private void emit(BattleSimulation sim,String beat) { log.emitOnce(sim.getSimTickIndex(),"yard_owners."+beat,StoryBeatLog.Audience.PLAYER); }
    public Faction owningFaction() { return Faction.MARINE; }
    public void tick(BattleView ignored) { }
    public boolean isComplete() { return progress.phase() == YardOwnersProgress.Phase.COMPLETE; }
    public boolean isFailed() { return progress.phase() == YardOwnersProgress.Phase.FAILED; }
    public String displayName() { return switch(progress.phase()) {
        case DISPATCH -> "Secure dispatch and the freight gate";
        case TOOLING -> "Secure the tooling hall; preserve the marked calibration machine";
        case ENTRANCE -> "Secure the operator entrance";
        case INSPECTION -> "Hold all three sites and protect the tooling inspection";
        case COMPLETE -> "Latchkey inspected; custody agreement remains unsigned";
        case FAILED -> "Inspection failed; replenish for a new mobilization";
    }; }
}
