package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder;
import com.dillon.starsectormarines.battle.infantry.GoapInfantryBehavior;
import com.dillon.starsectormarines.battle.mech.GoapMechBehavior;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What makes {@link OrderCatalog} load-bearing rather than a convenience.
 *
 * <p>The facts a kind carries used to be spread over the arbiter's shape
 * check, its guess at an unclaimed assignment's owner, the dispatchers' goal
 * lookups and the order system's completion tests, and nothing made those
 * agree. A kind added to one of them and missed in another was silent. The
 * table is only an improvement on that while a row is compulsory and while the
 * row says something true, so this asks both: every kind has exactly one row,
 * every factory builds an assignment its own row would accept, and every
 * player order names a goal the arm it claims actually carries.
 *
 * <p><b>The relevance case here is the negative one on purpose.</b> A player
 * order's goal must win outright for its own kind and must be inert for every
 * other, and only the second half can be asked of a bare squad on empty
 * ground: the positive case needs a workable world per kind, which
 * {@code PlayerOrderScene} and {@code SquadMoveOrderSystemTest} already play
 * out properly.
 */
class OrderCatalogTest {

    private static final int TARGET_X = 10;
    private static final int TARGET_Y = 5;

    private static final TacticalNode NODE = new TacticalNode(
            TacticalNode.Kind.BARRACKS, 13, 5, 12, 4, 14, 6,
            Faction.DEFENDER, 50, 4);

    @Test
    void everyKindHasExactlyOneRow() {
        List<OrderCatalog.Row> rows = OrderCatalog.rows();
        assertEquals(AssignmentKind.values().length, rows.size(),
                "the table carries one row per kind and no spares");
        Set<AssignmentKind> covered = EnumSet.noneOf(AssignmentKind.class);
        for (OrderCatalog.Row row : rows) {
            assertTrue(covered.add(row.kind()),
                    "two rows claim " + row.kind());
        }
        for (AssignmentKind kind : AssignmentKind.values()) {
            assertTrue(covered.contains(kind),
                    kind + " has no row: give it one rather than letting the "
                            + "arbiter and the dispatchers each guess");
            assertSame(kind, OrderCatalog.row(kind).kind(),
                    "the row filed under " + kind + " describes another kind");
        }
    }

    /**
     * Every {@link ObjectiveAssignment} factory, called with dummy but valid
     * arguments, produces an assignment its own row's shape accepts.
     *
     * <p><b>One factory is deliberately absent, and it is a real gap rather
     * than an oversight.</b> The three-argument {@code rushObjective} carries
     * an objective and a zone hint but no cell, which the
     * {@link OrderCatalog.Shape#OBJECTIVE_AT_CELL} row refuses — as the
     * arbiter refused it before the table existed. No production caller uses
     * it; {@code RaidCommand} takes the five-argument overload. When it grows
     * a cell or is deleted, add it here.
     */
    @Test
    void everyFactoryBuildsSomethingItsOwnRowAccepts() {
        assertShaped("clearZone", ObjectiveAssignment.clearZone(1, 7));
        assertShaped("sweepSector",
                ObjectiveAssignment.sweepSector(1, TARGET_X, TARGET_Y));
        assertShaped("defendTrack",
                ObjectiveAssignment.defendTrack(1, TARGET_X, TARGET_Y));
        assertShaped("defendSite",
                ObjectiveAssignment.defendSite(1, TARGET_X, TARGET_Y));
        assertShaped("defendArea",
                ObjectiveAssignment.defendArea(1, TARGET_X, TARGET_Y));
        assertShaped("defendArea(radius)",
                ObjectiveAssignment.defendArea(1, TARGET_X, TARGET_Y, 20));
        assertShaped("advanceTrack",
                ObjectiveAssignment.advanceTrack(1, TARGET_X, TARGET_Y));
        assertShaped("attackMove",
                ObjectiveAssignment.attackMove(1, TARGET_X, TARGET_Y));
        assertShaped("secureCompound",
                ObjectiveAssignment.secureCompound(1, 7, NODE));
        assertShaped("holdNode", ObjectiveAssignment.holdNode(1, NODE));
        assertShaped("rushObjective(cell)",
                ObjectiveAssignment.rushObjective(1, 3, 7, TARGET_X, TARGET_Y));
        assertShaped("withdraw",
                ObjectiveAssignment.withdraw(1, TARGET_X, TARGET_Y));
        assertShaped("escort",
                ObjectiveAssignment.escort(1, TARGET_X, TARGET_Y));
        assertShaped("support", ObjectiveAssignment.support(1));

        // A kind nobody can build is a kind nobody can issue. Adding one above
        // is the whole cost of adding a kind, and this is what says so.
        Set<AssignmentKind> exercised = EnumSet.of(
                AssignmentKind.CLEAR_ZONE, AssignmentKind.SWEEP_SECTOR,
                AssignmentKind.DEFEND_TRACK, AssignmentKind.DEFEND_SITE,
                AssignmentKind.DEFEND_AREA, AssignmentKind.ADVANCE_TRACK,
                AssignmentKind.ATTACK_MOVE, AssignmentKind.SECURE_COMPOUND,
                AssignmentKind.HOLD_NODE, AssignmentKind.RUSH_OBJECTIVE,
                AssignmentKind.WITHDRAW, AssignmentKind.ESCORT,
                AssignmentKind.SUPPORT);
        for (AssignmentKind kind : AssignmentKind.values()) {
            assertTrue(exercised.contains(kind),
                    kind + " has no factory exercised here: give "
                            + "ObjectiveAssignment one and call it above");
        }
    }

    @Test
    void everyPlayerOrderNamesAMissionGoalItsArmsCarry() {
        for (OrderCatalog.Row row : OrderCatalog.rows()) {
            OrderCatalog.PlayerOrder player = row.player();
            if (player == null) continue;
            Goal goal = player.goal();
            assertSame(Goal.Priority.MISSION, goal.priority(),
                    goal.name() + " serves a player order, which must outrank "
                            + "the mission it masks or the click did nothing");
            if (player.allows(OrderCatalog.Arm.INFANTRY)) {
                assertTrue(GoapInfantryBehavior.INFANTRY_GOALS.contains(goal),
                        goal.name() + " is offered to infantry by " + row.kind()
                                + " but is not in INFANTRY_GOALS, so the "
                                + "dispatcher can never pick it");
            }
            if (player.allows(OrderCatalog.Arm.MECH)) {
                assertTrue(GoapMechBehavior.MECH_GOALS.contains(goal),
                        goal.name() + " is offered to mechs by " + row.kind()
                                + " but is not in MECH_GOALS, so the "
                                + "dispatcher can never pick it");
            }
        }
    }

    @Test
    void aPlayerOrderGoalIsInertUnderEveryOtherKind() {
        BattleSimulation sim = openSimulation();
        Squad squad = infantrySquad(sim, 3, 5, 2);

        // The control. Every assertion below is a zero, and a squad whose
        // assignment never reached the goal at all would produce zeros for a
        // reason that has nothing to do with the table: one goal wanting its
        // own kind here is what says the instrument is live.
        squad.assignedObjective = sample(AssignmentKind.ATTACK_MOVE, squad.id);
        assertTrue(AttackMoveGoal.INSTANCE.relevance(
                        WorldStateBuilder.build(squad, sim), squad, sim) > 0f,
                "this squad cannot see its own assignment, so the zeros below "
                        + "measure nothing");

        for (OrderCatalog.Row row : OrderCatalog.rows()) {
            if (row.player() == null) continue;
            Goal goal = row.player().goal();
            for (AssignmentKind other : AssignmentKind.values()) {
                if (other == row.kind()) continue;
                squad.assignedObjective = sample(other, squad.id);
                WorldState state = WorldStateBuilder.build(squad, sim);
                assertEquals(0f, goal.relevance(state, squad, sim), 0f,
                        goal.name() + " claims relevance under " + other
                                + "; a player goal that fires on somebody "
                                + "else's kind hijacks the mission tier");
            }
        }
    }

    @Test
    void aPlayerOrderKnowsWhenItIsOver() {
        for (OrderCatalog.Row row : OrderCatalog.rows()) {
            if (row.player() == null) continue;
            assertNotNull(row.player().completion(),
                    row.kind() + "'s player order never reports itself done, "
                            + "so it would stand over the mission forever");
        }

        BattleSimulation sim = openSimulation();
        Squad squad = infantrySquad(sim, TARGET_X, TARGET_Y, 2);
        ObjectiveAssignment order =
                ObjectiveAssignment.attackMove(squad.id, TARGET_X, TARGET_Y);
        placeSquadAt(sim, squad, TARGET_X, TARGET_Y);

        assertFalse(OrderCatalog.PERSISTENT.isComplete(squad, order, sim),
                "a standing defense does not finish by being reached");
        assertTrue(OrderCatalog.ARRIVED.isComplete(squad, order, sim),
                "a squad standing on the ground it was pointed at has arrived");

        placeSquadAt(sim, squad, TARGET_X - 10, TARGET_Y);
        assertFalse(OrderCatalog.ARRIVED.isComplete(squad, order, sim),
                "ten cells out is not arrival");
    }

    private static void assertShaped(String factory, ObjectiveAssignment made) {
        OrderCatalog.Shape shape = OrderCatalog.row(made.kind()).shape();
        switch (shape) {
            case ZONE -> assertTrue(made.targetZoneId() >= 0,
                    factory + " builds a " + shape + " assignment with no zone");
            case CELL -> assertTrue(
                    made.targetCellX() >= 0 && made.targetCellY() >= 0,
                    factory + " builds a " + shape + " assignment with no cell");
            case NODE -> assertNotNull(made.targetNode(),
                    factory + " builds a " + shape + " assignment with no node");
            case OBJECTIVE_AT_CELL -> {
                assertTrue(made.objectiveId() >= 0,
                        factory + " builds a " + shape
                                + " assignment with no objective");
                assertTrue(made.targetCellX() >= 0 && made.targetCellY() >= 0,
                        factory + " builds a " + shape
                                + " assignment with no cell");
            }
            case NONE -> { }
        }
    }

    /** A well-formed assignment of {@code kind}, for asking what it is not. */
    private static ObjectiveAssignment sample(AssignmentKind kind, int squadId) {
        return switch (kind) {
            case CLEAR_ZONE -> ObjectiveAssignment.clearZone(squadId, 7);
            case SWEEP_SECTOR ->
                    ObjectiveAssignment.sweepSector(squadId, TARGET_X, TARGET_Y);
            case DEFEND_TRACK ->
                    ObjectiveAssignment.defendTrack(squadId, TARGET_X, TARGET_Y);
            case DEFEND_SITE ->
                    ObjectiveAssignment.defendSite(squadId, TARGET_X, TARGET_Y);
            case DEFEND_AREA ->
                    ObjectiveAssignment.defendArea(squadId, TARGET_X, TARGET_Y);
            case ADVANCE_TRACK ->
                    ObjectiveAssignment.advanceTrack(squadId, TARGET_X, TARGET_Y);
            case ATTACK_MOVE ->
                    ObjectiveAssignment.attackMove(squadId, TARGET_X, TARGET_Y);
            case SECURE_COMPOUND ->
                    ObjectiveAssignment.secureCompound(squadId, 7, NODE);
            case HOLD_NODE -> ObjectiveAssignment.holdNode(squadId, NODE);
            case RUSH_OBJECTIVE -> ObjectiveAssignment.rushObjective(
                    squadId, 3, 7, TARGET_X, TARGET_Y);
            case WITHDRAW ->
                    ObjectiveAssignment.withdraw(squadId, TARGET_X, TARGET_Y);
            case SUPPORT -> ObjectiveAssignment.support(squadId);
            case ESCORT ->
                    ObjectiveAssignment.escort(squadId, TARGET_X, TARGET_Y);
        };
    }

    /** Arrival is the action's own footprint, so the bodies have to move. */
    private static void placeSquadAt(BattleSimulation sim, Squad squad,
                                     int x, int y) {
        for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
            long member = sim.resolveUnit(sim.squadMemberAt(squad.id, i));
            if (member != 0L) sim.world().setCellPos(member, x, y);
        }
        squad.centroidX = x + 0.5f;
        squad.centroidY = y + 0.5f;
    }

    private static Squad infantrySquad(BattleSimulation sim, int x, int y,
                                       int count) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        float sumX = 0f;
        float sumY = 0f;
        for (int i = 0; i < count; i++) {
            EntitySpec spec = new EntitySpec("member-" + squadId + "-" + i,
                    Faction.MARINE, UnitType.MARINE, x, y + i).squad(squadId);
            spec.primaryWeapon(WeaponRegistry.require(
                    WeaponRegistry.SQUAD_AUTOMATIC_ID));
            long member = sim.spawn(spec);
            if (i == 0) squad.leaderId = member;
            sumX += sim.world().x(member);
            sumY += sim.world().y(member);
        }
        squad.aliveMembers = count;
        squad.originalSize = count;
        squad.centroidX = sumX / count;
        squad.centroidY = sumY / count;
        return squad;
    }

    private static BattleSimulation openSimulation() {
        int width = 24;
        int height = 16;
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }
}
