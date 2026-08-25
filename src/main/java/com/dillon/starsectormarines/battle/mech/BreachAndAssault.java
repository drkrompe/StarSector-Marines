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
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * Assault mech point action. The assault member advances into an assigned
 * zone, then closes to a short standoff from its contact while keeping every
 * installed weapon live. With no assignment it advances on the squad's known
 * contact, which gives the same behavior to attacker and defender squads.
 * The point advance is formation-leashed to nearby combat infantry or a live
 * mech of another chassis: an unsupported assault mech holds and fires instead
 * of making a solo close-range charge. Same-chassis assault mechs cannot
 * bootstrap one another into an unsupported push.
 *
 * <p>Mixed-role mech squads keep their existing doctrine inside the shared
 * step: LR Support delegates to overwatch and Armored Support delegates to
 * backstop while the assault member walks point.
 */
public final class BreachAndAssault implements Action {

    public static final BreachAndAssault INSTANCE = new BreachAndAssault();

    static final float CONTACT_STANDOFF = 3f;
    static final float SUPPORT_ACQUIRE_DISTANCE = 12f;
    static final float MAX_SUPPORT_LEAD = 6f;
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

        long support = nearestSupport(member, squad, sim);
        if (support == 0L) {
            hold(member, sim);
            return ActionStatus.RUNNING;
        }
        int[] destination = destination(member, squad, target, sim);
        if (destination == null) {
            hold(member, sim);
            return ActionStatus.RUNNING;
        }
        int[] cohesiveDestination = clampToSupport(destination[0], destination[1],
                support, sim);
        if (cohesiveDestination == null) {
            hold(member, sim);
            return ActionStatus.RUNNING;
        }
        moveToward(member, cohesiveDestination[0], cohesiveDestination[1], sim);
        return ActionStatus.RUNNING;
    }

    static long nearestSupport(long member, Squad squad, BattleView sim) {
        float memberX = sim.world().x(member);
        float memberY = sim.world().y(member);
        float maxDistanceSq = SUPPORT_ACQUIRE_DISTANCE * SUPPORT_ACQUIRE_DISTANCE;
        long best = 0L;
        float bestDistanceSq = Float.MAX_VALUE;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long candidate = sim.liveUnitAt(i);
            if (candidate == member
                    || sim.identity().faction(candidate) != squad.faction) continue;
            UnitType type = sim.identity().type(candidate);
            if (!type.isMech() && !isCombatInfantry(type)) continue;
            Squad candidateSquad = sim.squadOf(candidate);
            if (candidateSquad == null || candidateSquad.aliveMembers == 0
                    || type.isMech() && candidateSquad.rescuePickupMech) continue;
            if (type.isMech() && sameMechVariant(member, candidate, sim)) continue;

            float dx = sim.world().x(candidate) - memberX;
            float dy = sim.world().y(candidate) - memberY;
            float distanceSq = dx * dx + dy * dy;
            if (distanceSq > maxDistanceSq) continue;
            if (distanceSq < bestDistanceSq
                    || distanceSq == bestDistanceSq && candidate < best) {
                best = candidate;
                bestDistanceSq = distanceSq;
            }
        }
        return best;
    }

    private static boolean sameMechVariant(long first, long second,
                                           BattleView sim) {
        MechVariant firstVariant = mechVariant(first, sim);
        return firstVariant != null && firstVariant == mechVariant(second, sim);
    }

    private static MechVariant mechVariant(long mech, BattleView sim) {
        MechVariant identityVariant = sim.identity().mechVariant(mech);
        if (identityVariant != null) return identityVariant;
        MechLoadoutComponent loadout = sim.world().mechLoadout(mech);
        return loadout != null ? loadout.variant : null;
    }

    private static boolean isCombatInfantry(UnitType type) {
        return type == UnitType.MARINE || type == UnitType.MARINE_BLUE
                || type == UnitType.MARINE_RED || type == UnitType.MILITIA;
    }

    private static int[] clampToSupport(int destinationX, int destinationY,
                                        long support, BattleView sim) {
        float supportX = sim.world().x(support);
        float supportY = sim.world().y(support);
        float dx = destinationX + 0.5f - supportX;
        float dy = destinationY + 0.5f - supportY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance <= MAX_SUPPORT_LEAD) {
            return new int[]{destinationX, destinationY};
        }

        float idealX = supportX + dx / distance * MAX_SUPPORT_LEAD;
        float idealY = supportY + dy / distance * MAX_SUPPORT_LEAD;
        NavigationGrid grid = sim.getGrid();
        int centerX = (int) Math.floor(idealX);
        int centerY = (int) Math.floor(idealY);
        int[] best = null;
        float bestScore = Float.MAX_VALUE;
        for (int radius = 0; radius <= 3; radius++) {
            for (int oy = -radius; oy <= radius; oy++) {
                for (int ox = -radius; ox <= radius; ox++) {
                    if (Math.max(Math.abs(ox), Math.abs(oy)) != radius) continue;
                    int x = centerX + ox;
                    int y = centerY + oy;
                    if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) continue;
                    float leadX = x + 0.5f - supportX;
                    float leadY = y + 0.5f - supportY;
                    if (leadX * leadX + leadY * leadY
                            > MAX_SUPPORT_LEAD * MAX_SUPPORT_LEAD + 0.01f) continue;
                    float idealDx = x + 0.5f - idealX;
                    float idealDy = y + 0.5f - idealY;
                    float score = idealDx * idealDx + idealDy * idealDy;
                    if (score < bestScore) {
                        best = new int[]{x, y};
                        bestScore = score;
                    }
                }
            }
        }
        return best;
    }

    private static void hold(long member, BattleControl sim) {
        if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
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
        ObjectiveAssignment assignment = squad.assignmentForExecution();
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
                || Paths.destX(path) != x || Paths.destY(path) != y;
        if (destinationShifted && !Paths.isEmpty(path)) {
            sim.clearPath(member);
            path = sim.world().path(member);
        }
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
