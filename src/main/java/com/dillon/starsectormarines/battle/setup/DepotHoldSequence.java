package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.i18n.Strings;
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

/**
 * Two precommitted attacks enter on both service roads once the company steps into dispatch and sprint into it, each
 * raider walking once inside; the final one brings a technical up the south road to park before the warehouse
 * ({@link DepotRoadTechnical}).
 */
public final class DepotHoldSequence implements Objective {
    public static final String CAPTURED = "opening.counterattack.warehouse_captured";
    public static final String FIRST = "opening.counterattack.first_road_wave";
    public static final String WARNING = "opening.counterattack.final_wave_warning";
    public static final String SECOND = "opening.counterattack.final_road_wave";
    public static final String CONTESTED = "opening.counterattack.warehouse_contested";
    public static final String SECURED = "opening.counterattack.warehouse_secured";
    private static final String ISSUER = "depot-road-arrival";
    /**
     * Within this of dispatch's door a company machine holds the warehouse once no friendly infantry is left on the
     * field: a Hound cannot get inside, and without this a company down to its walker could neither win nor lose.
     */
    static final float DOOR_REACH = 8f;
    private final PointOfInterest warehouse;
    private final List<List<EntitySpec>> forces;
    private final List<Integer> approachSquads = new ArrayList<>();
    private final List<Integer> roadSquads = new ArrayList<>();
    private final StoryBeatLog log;
    private final DepotRoadTechnical technical;
    private final DepotHoldProgress progress = new DepotHoldProgress();
    private int released;
    private float doorX = Float.NaN, doorY;

    public DepotHoldSequence(PointOfInterest warehouse, List<List<EntitySpec>> forces, StoryBeatLog log) {
        this(warehouse, forces, null, log);
    }

    /** @param technical the final wave's truck, or null for none */
    DepotHoldSequence(PointOfInterest warehouse, List<List<EntitySpec>> forces, DepotRoadTechnical technical,
                      StoryBeatLog log) {
        if (forces.size() != 4 || forces.stream().anyMatch(List::isEmpty))
            throw new IllegalArgumentException("Depot defense requires two pairs of road forces");
        this.warehouse = warehouse;
        this.forces = forces.stream().map(List::copyOf).toList();
        this.technical = technical;
        this.log = log;
    }
    public DepotHoldProgress progress() { return progress; }
    public int x() { return warehouse.interiorAnchorX; }
    public int y() { return warehouse.interiorAnchorY; }
    /** The final wave's technical hull, or 0 before it enters or when the battle fields none. */
    public long technicalId() { return technical == null ? 0L : technical.id(); }
    /** Where the technical enters and parks as {entryX, entryY, parkX, parkY}, or null when the battle fields none. */
    public int[] technicalRoute() {
        return technical == null ? null
                : new int[]{technical.entryX(), technical.entryY(), technical.parkX(), technical.parkY()};
    }
    /** The cells the technical drives, x0, y0, x1, y1, ...; empty when the battle fields none. */
    public int[] technicalPath(BattleSimulation sim) { return technical == null ? new int[0] : technical.route(sim); }
    /** Raiders in each road force, in release order: wave one north and south, then wave two. */
    public List<Integer> forceSizes() { return forces.stream().map(List::size).toList(); }
    public int pendingMembers() { return forces.subList(released * 2, 4).stream().mapToInt(List::size).sum(); }

    public void advance(BattleSimulation sim) {
        if (sim.isComplete()) return;
        boolean company = false, friendly = false, enemy = false, infantry = false, machineAtDoor = false;
        if (Float.isNaN(doorX)) findDoor(sim);
        int raiders = pendingMembers() + (technical != null && technical.standing(sim) ? 1 : 0);
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (!sim.identity().type(id).combatant) continue;
            Faction faction = sim.identity().faction(id);
            if (Faction.MARINE.hostileTo(faction)) raiders++;
            float x = sim.world().x(id), y = sim.world().y(id);
            boolean machine = sim.world().hasMechLoadout(id) || sim.convoy().isVehicle(id);
            if (Faction.MARINE.friendlyTo(faction) && !machine) infantry = true;
            if (faction == Faction.MARINE && sim.world().hasMechLoadout(id) && Math.hypot(x - doorX, y - doorY) <= DOOR_REACH)
                machineAtDoor = true;
            if (!inside(x, y)) continue;
            if (Faction.MARINE.hostileTo(faction)) enemy = true;
            company |= faction == Faction.MARINE;
            friendly |= faction == Faction.MARINE || faction == Faction.ALLY;
        }
        if (!infantry && machineAtDoor) company = friendly = true;
        progress.advance(BattleSimulation.TICK_DT, company, friendly, enemy, raiders);
        if (progress.committed()) emit(sim, CAPTURED);
        if (progress.committed() && enemy) emit(sim, CONTESTED);
        if (progress.elapsed() >= DepotHoldProgress.SECOND_WAVE_SECONDS - 12f) emit(sim, WARNING);
        while (released < progress.waves()) {
            for (int road = 0; road < 2; road++) spawn(sim, forces.get(released * 2 + road));
            if (released == 1 && technical != null) technical.enter(sim);
            emit(sim, released == 0 ? FIRST : SECOND);
            released++;
        }
        for (int squadId : List.copyOf(approachSquads)) {
            var squad = sim.getSquad(squadId);
            if (sim.squadMemberCount(squadId) == 0) { approachSquads.remove(Integer.valueOf(squadId)); continue; }
            // Into dispatch first, then the defenders' commander fights them from there.
            if (anyInside(sim, squadId)) {
                sim.squadRun().stopScripted(squadId);
                sim.handoffSquadCommand(squadId, ISSUER, CommandAuthority.MISSION_COMMAND,
                        OpeningOperationCommand.issuer(Faction.DEFENDER), null, "Road reserve assaults captured dispatch");
                approachSquads.remove(Integer.valueOf(squadId));
            }
        }
        if (progress.complete()) emit(sim, SECURED);
    }
    private boolean anyInside(BattleSimulation sim, int squadId) {
        for (int i = 0, n = sim.squadMemberCount(squadId); i < n; i++) {
            long id = sim.resolveUnit(sim.squadMemberAt(squadId, i));
            if (id != 0L && sim.world().hasPosition(id) && inside(sim.world().x(id), sim.world().y(id))) return true;
        }
        return false;
    }

    /** Dispatch's door as {x, y}, where a company machine may hold it ({@link #DOOR_REACH}). */
    public float[] door(BattleSimulation sim) {
        if (Float.isNaN(doorX)) findDoor(sim);
        return new float[]{doorX, doorY};
    }

    /** Every road squad released so far, in release order. */
    public List<Integer> roadSquads() { return List.copyOf(roadSquads); }

    /** Whether a point lies within dispatch's walls. */
    public boolean inside(float x, float y) {
        return x > warehouse.left && x < warehouse.right && y > warehouse.top && y < warehouse.bottom;
    }

    /** Dispatch's door: the middle of the walkable gap in its wall facing the yard, or its anchor if none. */
    private void findDoor(BattleSimulation sim) {
        doorX = warehouse.interiorAnchorX + .5f;
        doorY = warehouse.interiorAnchorY + .5f;
        int sum = 0, count = 0;
        for (int y = warehouse.top + 1; y < warehouse.bottom; y++)
            if (sim.getGrid().isWalkable(warehouse.left, y)) { sum += y; count++; }
        if (count == 0) return;
        doorX = warehouse.left + .5f;
        doorY = (float) sum / count + .5f;
    }

    private void spawn(BattleSimulation sim, List<EntitySpec> force) {
        int squad = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        for (EntitySpec spec : force) sim.spawn(spec.squad(squad));
        sim.getSquad(squad).originalSize = force.size();
        sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad, warehouse.interiorAnchorX, warehouse.interiorAnchorY),
                CommandAuthority.SCRIPTED, ISSUER, "Enter dispatch from the service road");
        // They sprint to the fight and take it at a walk: each raider runs until it is inside dispatch.
        sim.squadRun().runScripted(squad, id -> !inside(sim.world().x(id), sim.world().y(id)));
        approachSquads.add(squad);
        roadSquads.add(squad);
    }
    private void emit(BattleSimulation sim, String key) { log.emitOnce(sim.getSimTickIndex(), key, StoryBeatLog.Audience.PLAYER); }
    @Override public Faction owningFaction() { return Faction.MARINE; }
    @Override public void tick(BattleView sim) { /* Advanced before objective evaluation by the simulation. */ }
    @Override public boolean isComplete() { return progress.complete(); }
    @Override public boolean isFailed() { return false; }
    @Override public String displayName() { return Strings.get("depotHoldObjective"); }
}
