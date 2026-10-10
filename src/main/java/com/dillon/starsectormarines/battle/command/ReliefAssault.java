package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.ReliefCheckpoint.Cell;

import java.util.ArrayList;
import java.util.List;

/** Arrival policy for Relief's committed, finite ground reserve; never replenishes tickets. */
public final class ReliefAssault {
    private static final String ISSUER = "relief-road-arrival";
    public record Arrival(String road, int tick, int members) {}
    private static final class Group {
        final String road;
        final List<EntitySpec> force;
        final List<Cell> route;
        final List<Long> members = new ArrayList<>();
        int squadId = -1;
        int next = 1;
        boolean handedOff;
        Group(String road, List<EntitySpec> force, List<Cell> route) {
            this.road = road;
            this.force = List.copyOf(force);
            this.route = List.copyOf(route);
        }
    }
    private final Cell gate;
    private final List<Group> groups;
    private final List<Arrival> arrivals = new ArrayList<>();
    private int firstLanding = -1;
    private int firstRelief = -1;

    public ReliefAssault(Cell gate, List<EntitySpec> mainForce, List<Cell> mainRoute,
                         List<EntitySpec> serviceForce, List<Cell> serviceRoute) {
        this.gate = gate;
        if (mainForce.isEmpty() || serviceForce.isEmpty() || mainRoute.size() < 2 || serviceRoute.size() < 2)
            throw new IllegalArgumentException("Relief reserves require forces and road approaches");
        groups = List.of(new Group("Freight road", mainForce, mainRoute),
                new Group("Warehouse service road", serviceForce, serviceRoute));
    }

    public int pendingMembers() {
        return groups.stream().filter(g -> g.squadId < 0).mapToInt(g -> g.force.size()).sum();
    }
    public List<Arrival> arrivals() { return List.copyOf(arrivals); }

    /** Latest times prevent a player holding at the apron from cancelling the assault. */
    static int releaseTick(int group, int firstLanding, int firstRelief) {
        if (group == 0) return boundedTrigger(18 * 30, 28 * 30, firstLanding, 6 * 30);
        return boundedTrigger(38 * 30, 55 * 30, firstRelief, 8 * 30);
    }

    private static int boundedTrigger(int earliest, int latest, int trigger, int delay) {
        return trigger < 0 ? latest : Math.max(earliest, Math.min(latest, trigger + delay));
    }

    public void tick(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE || !sim.identity().type(unit).combatant) continue;
            if (firstLanding < 0) firstLanding = tick;
            float dx = sim.world().x(unit) - gate.x(), dy = sim.world().y(unit) - gate.y();
            if (firstRelief < 0 && dx * dx + dy * dy <= 64) firstRelief = tick;
        }
        for (int i = 0; i < groups.size(); i++) {
            Group group = groups.get(i);
            if (group.squadId < 0 && tick >= releaseTick(i, firstLanding, firstRelief)) spawn(sim, group, tick);
            if (group.squadId >= 0 && !group.handedOff) advance(sim, group);
        }
    }

    private void spawn(BattleSimulation sim, Group group, int tick) {
        group.squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        for (EntitySpec spec : group.force) group.members.add(sim.spawn(spec.squad(group.squadId)));
        sim.getSquad(group.squadId).originalSize = group.force.size();
        arrivals.add(new Arrival(group.road, tick, group.force.size()));
        order(sim, group);
    }

    private void advance(BattleSimulation sim, Group group) {
        float x = 0, y = 0;
        int alive = 0;
        for (long id : group.members) if (sim.world().isAlive(id)) {
            x += sim.world().x(id);
            y += sim.world().y(id);
            alive++;
        }
        if (alive == 0) { group.handedOff = true; return; }
        Cell target = group.route.get(group.next);
        float dx = x / alive - target.x(), dy = y / alive - target.y();
        // Once the marching squad sees the fight, ordinary assault command owns it.
        // Transport staging must not hold it forever on a road waypoint under fire.
        boolean contact = sim.getSquad(group.squadId).hasDirectContactThisTick();
        if (!contact && dx * dx + dy * dy > 16) return;
        group.next = contact ? group.route.size() : group.next + 1;
        if (group.next < group.route.size()) order(sim, group);
        else {
            sim.handoffSquadCommand(group.squadId, ISSUER, CommandAuthority.MISSION_COMMAND,
                    OpeningOperationCommand.issuer(Faction.DEFENDER),
                    ObjectiveAssignment.sweepSector(group.squadId, gate.x(), gate.y()),
                    "Relief attackers reached the checkpoint approach");
            group.handedOff = true;
        }
    }

    private static void order(BattleSimulation sim, Group group) {
        Cell next = group.route.get(group.next);
        sim.assignSquadCommand(ObjectiveAssignment.sweepSector(group.squadId, next.x(), next.y()),
                CommandAuthority.SCRIPTED, ISSUER, "advance along " + group.road);
    }
}
