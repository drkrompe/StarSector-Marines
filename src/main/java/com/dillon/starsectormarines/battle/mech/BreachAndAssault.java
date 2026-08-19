package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;

/**
 * Assault mech point action. The assault member advances into an assigned
 * zone, then closes to a short standoff from its contact while keeping every
 * installed weapon live. With no assignment it advances on the squad's known
 * contact, which gives the same behavior to attacker and defender squads.
 *
 * <p>Mixed-role mech squads keep their existing doctrine inside the shared
 * step: LR Support delegates to overwatch and Armored Support delegates to
 * backstop while the assault member walks point.
 */
public final class BreachAndAssault implements Action {

    public static final BreachAndAssault INSTANCE = new BreachAndAssault();

    static final float CONTACT_STANDOFF = 3f;
    private static final int DESTINATION_REPICK_DISTANCE = 2;
    private static final WorldState EFFECTS = WorldState.EMPTY
            .with(Predicate.ENEMY_DAMAGED, true);

    private BreachAndAssault() {}

    @Override public String name() { return "BreachAndAssault"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return EFFECTS; }
    @Override public float cost(WorldState state, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        MechLoadoutComponent loadout = sim.world().mechLoadout(member);
        if (loadout == null) return ActionStatus.FAILURE;
        if (loadout.role == MechRole.LR_SUPPORT) {
            return OverwatchKillZone.INSTANCE.execute(member, squad, sim);
        }
        if (loadout.role == MechRole.ARMORED_SUPPORT) {
            return BackstopAssignedSquad.INSTANCE.execute(member, squad, sim);
        }
        if (loadout.role != MechRole.ASSAULT) {
            return EngageAtCurrentBand.INSTANCE.execute(member, squad, sim);
        }

        long target = MechTargeting.refreshTarget(member, sim);
        sim.world().setTargetId(member, target);
        fireWhileAdvancing(member, loadout, target, sim);

        int[] destination = destination(member, squad, target, sim);
        if (destination == null) return ActionStatus.RUNNING;
        moveToward(member, destination[0], destination[1], sim);
        return ActionStatus.RUNNING;
    }

    private static void fireWhileAdvancing(long member, MechLoadoutComponent loadout,
                                           long target, BattleControl sim) {
        if (target == 0L || sim.resolveUnit(target) == 0L) return;
        float distance = TacticalScoring.cellDistance(
                sim.world().x(member), sim.world().y(member),
                sim.world().x(target), sim.world().y(target));
        if (distance > sim.world().attackRange(member)) return;
        boolean visible = sim.getGrid().hasLineOfSight(
                sim.world().cellX(member), sim.world().cellY(member),
                sim.world().cellX(target), sim.world().cellY(target));
        MechCombatantBehavior.tryFireMechWeapons(
                member, loadout, target, distance, sim, visible);
    }

    private static int[] destination(long member, Squad squad, long target,
                                     BattleView sim) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        if (assignment != null && assignment.targetZoneId() >= 0) {
            int assignedZone = assignment.targetZoneId();
            NavigationZone zone = sim.getZoneGraph().zoneById(assignedZone);
            if (zone != null) {
                int memberZone = sim.getZoneGraph().zoneIdAt(
                        sim.world().cellX(member), sim.world().cellY(member));
                if (memberZone != assignedZone) {
                    return representativeCell(zone, sim.getGrid());
                }
                if (target != 0L && sim.resolveUnit(target) != 0L
                        && sim.getZoneGraph().zoneIdAt(
                        sim.world().cellX(target), sim.world().cellY(target)) == assignedZone) {
                    return approachCell(member, sim.world().cellX(target),
                            sim.world().cellY(target), sim);
                }
                // Do not let a contact outside the ordered zone pull the point
                // mech back through the portal it just breached.
                return representativeCell(zone, sim.getGrid());
            }
        }

        if (target != 0L && sim.resolveUnit(target) != 0L) {
            return approachCell(member, sim.world().cellX(target),
                    sim.world().cellY(target), sim);
        }
        if (squad.lastSeenEnemyX >= 0 && squad.lastSeenEnemyY >= 0) {
            return approachCell(member, squad.lastSeenEnemyX,
                    squad.lastSeenEnemyY, sim);
        }
        return null;
    }

    private static int[] representativeCell(NavigationZone zone, NavigationGrid grid) {
        int[] cells = zone.getCellIndices();
        if (cells.length == 0) return null;
        long sumX = 0;
        long sumY = 0;
        for (int cell : cells) {
            sumX += cell % grid.getWidth();
            sumY += cell / grid.getWidth();
        }
        float centerX = (float) sumX / cells.length;
        float centerY = (float) sumY / cells.length;
        int best = cells[0];
        float bestDistance = Float.MAX_VALUE;
        for (int cell : cells) {
            int x = cell % grid.getWidth();
            int y = cell / grid.getWidth();
            float dx = x - centerX;
            float dy = y - centerY;
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                best = cell;
                bestDistance = distance;
            }
        }
        return new int[]{best % grid.getWidth(), best / grid.getWidth()};
    }

    private static int[] approachCell(long member, int targetX, int targetY,
                                      BattleView sim) {
        float dx = sim.world().x(member) - (targetX + 0.5f);
        float dy = sim.world().y(member) - (targetY + 0.5f);
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= CONTACT_STANDOFF) return null;
        int anchorX = Math.round(targetX + dx / length * CONTACT_STANDOFF);
        int anchorY = Math.round(targetY + dy / length * CONTACT_STANDOFF);
        NavigationGrid grid = sim.getGrid();
        for (int radius = 0; radius <= 3; radius++) {
            for (int oy = -radius; oy <= radius; oy++) {
                for (int ox = -radius; ox <= radius; ox++) {
                    if (Math.max(Math.abs(ox), Math.abs(oy)) != radius) continue;
                    int x = anchorX + ox;
                    int y = anchorY + oy;
                    if (grid.inBounds(x, y) && grid.isWalkable(x, y)
                            && !(x == targetX && y == targetY)) {
                        return new int[]{x, y};
                    }
                }
            }
        }
        return null;
    }

    private static void moveToward(long member, int x, int y, BattleControl sim) {
        if (sim.movement().atCell(member, x, y)) {
            if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
            return;
        }
        int[] path = sim.world().path(member);
        boolean destinationShifted = Paths.isEmpty(path)
                || Math.abs(Paths.destX(path) - x) > DESTINATION_REPICK_DISTANCE
                || Math.abs(Paths.destY(path) - y) > DESTINATION_REPICK_DISTANCE;
        if (sim.movement().mayRepath(member) && destinationShifted) {
            sim.setPath(member, GridPathfinder.findPath(
                    sim.getGrid(), sim.world().cellX(member), sim.world().cellY(member),
                    x, y, sim.getOccupancyMap()));
        }
        if (sim.world().pathIdx(member) < Paths.cellCount(sim.world().path(member))) {
            sim.advanceMovement(member);
        }
    }
}
