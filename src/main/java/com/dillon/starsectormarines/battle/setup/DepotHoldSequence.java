package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommand;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.story.DepotHoldProgress;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.ArrayList;
import java.util.List;

/** Two precommitted attacks enter on both service roads after dispatch is captured. */
public final class DepotHoldSequence implements Objective {
    public static final String CAPTURED = "opening.counterattack.warehouse_captured";
    public static final String FIRST = "opening.counterattack.first_road_wave";
    public static final String WARNING = "opening.counterattack.final_wave_warning";
    public static final String SECOND = "opening.counterattack.final_road_wave";
    public static final String CONTESTED = "opening.counterattack.warehouse_contested";
    public static final String SECURED = "opening.counterattack.warehouse_secured";
    private static final String ISSUER = "depot-road-arrival";
    private final PointOfInterest warehouse;
    private final List<List<EntitySpec>> forces;
    private final List<Integer> approachSquads = new ArrayList<>();
    private final StoryBeatLog log;
    private final DepotHoldProgress progress = new DepotHoldProgress();
    private int released;

    public DepotHoldSequence(PointOfInterest warehouse, List<List<EntitySpec>> forces, StoryBeatLog log) {
        if (forces.size() != 4 || forces.stream().anyMatch(List::isEmpty))
            throw new IllegalArgumentException("Depot defense requires two pairs of road forces");
        this.warehouse = warehouse;
        this.forces = forces.stream().map(List::copyOf).toList();
        this.log = log;
    }
    public DepotHoldProgress progress() { return progress; }
    public int x() { return warehouse.interiorAnchorX; }
    public int y() { return warehouse.interiorAnchorY; }
    public int pendingMembers() { return forces.subList(released * 2, 4).stream().mapToInt(List::size).sum(); }

    public void advance(BattleSimulation sim) {
        if (sim.isComplete()) return;
        boolean company = false, friendly = false, enemy = false;
        int raiders = pendingMembers();
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (!sim.identity().type(id).combatant) continue;
            Faction faction = sim.identity().faction(id);
            if (Faction.MARINE.hostileTo(faction)) raiders++;
            float x = sim.world().x(id), y = sim.world().y(id);
            if (x <= warehouse.left || x >= warehouse.right || y <= warehouse.top || y >= warehouse.bottom) continue;
            if (Faction.MARINE.hostileTo(faction)) enemy = true;
            company |= faction == Faction.MARINE;
            friendly |= faction == Faction.MARINE || faction == Faction.ALLY;
        }
        progress.advance(BattleSimulation.TICK_DT, company, friendly, enemy, raiders);
        if (progress.committed()) emit(sim, CAPTURED);
        if (progress.committed() && enemy) emit(sim, CONTESTED);
        if (progress.elapsed() >= DepotHoldProgress.SECOND_WAVE_SECONDS - 12f) emit(sim, WARNING);
        while (released < progress.waves()) {
            for (int road = 0; road < 2; road++) spawn(sim, forces.get(released * 2 + road));
            emit(sim, released == 0 ? FIRST : SECOND);
            released++;
        }
        for (int squadId : List.copyOf(approachSquads)) {
            var squad = sim.getSquad(squadId);
            if (sim.squadMemberCount(squadId) == 0) { approachSquads.remove(Integer.valueOf(squadId)); continue; }
            if (Math.hypot(squad.centroidX - 78, squad.centroidY - 35) <= 6 || squad.hasDirectContactThisTick()) {
                sim.handoffSquadCommand(squadId, ISSUER, CommandAuthority.MISSION_COMMAND,
                        OpeningOperationCommand.issuer(Faction.DEFENDER), null, "Road reserve assaults captured dispatch");
                approachSquads.remove(Integer.valueOf(squadId));
            }
        }
        if (progress.complete()) emit(sim, SECURED);
    }
    private void spawn(BattleSimulation sim, List<EntitySpec> force) {
        int squad = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        for (EntitySpec spec : force) sim.spawn(spec.squad(squad));
        sim.getSquad(squad).originalSize = force.size();
        sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad, 78, 35),
                CommandAuthority.SCRIPTED, ISSUER, "Enter depot along the service road");
        approachSquads.add(squad);
    }
    private void emit(BattleSimulation sim, String key) { log.emitOnce(sim.getSimTickIndex(), key, StoryBeatLog.Audience.PLAYER); }
    @Override public Faction owningFaction() { return Faction.MARINE; }
    @Override public void tick(BattleView sim) { /* Advanced before objective evaluation by the simulation. */ }
    @Override public boolean isComplete() { return progress.complete(); }
    @Override public boolean isFailed() { return false; }
    @Override public String displayName() { return "Capture dispatch and defeat both road attacks"; }
}
