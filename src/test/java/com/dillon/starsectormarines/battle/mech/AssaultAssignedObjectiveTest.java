package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssaultAssignedObjectiveTest {

    @Test
    void supportedMarineAssaultMechAdvancesTowardCommanderAssignedZone() {
        BattleSimulation sim = twoRoomSimulation();
        Squad squad = assaultSquad(sim, Faction.MARINE, 2, 3);
        long mech = squad.leaderId;
        spawnInfantrySupport(sim, Faction.MARINE, 3, 3);
        int targetZone = sim.getZoneGraph().zoneIdAt(8, 3);
        squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id, targetZone);

        GoapMechBehavior.replanIfNeeded(squad, sim);

        assertSame(AssaultAssignedObjectiveGoal.INSTANCE, squad.currentGoal);
        GoapMechBehavior.INSTANCE.update(mech, sim);
        int[] path = sim.movement().path(mech);
        assertTrue(Paths.cellCount(path) > 0, "assigned assault should author a route");
        assertTrue(Paths.destX(path) > sim.world().cellX(mech),
                "the supported marine mech advances toward its assigned zone");
    }

    @Test
    void defenderAssaultMechAdvancesOnKnownContactWithoutCommander() {
        BattleSimulation sim = openSimulation(24, 12);
        Squad squad = assaultSquad(sim, Faction.DEFENDER, 3, 5);
        long mech = squad.leaderId;
        spawnInfantrySupport(sim, Faction.DEFENDER, 4, 5);
        sim.spawn(new EntitySpec("marine", Faction.MARINE, UnitType.MARINE, 18, 5));
        squad.lastSeenEnemyX = 18;
        squad.lastSeenEnemyY = 5;

        GoapMechBehavior.replanIfNeeded(squad, sim);

        assertSame(AssaultAssignedObjectiveGoal.INSTANCE, squad.currentGoal);
        GoapMechBehavior.INSTANCE.update(mech, sim);
        assertTrue(Paths.destX(sim.movement().path(mech)) > 3,
                "the defender uses the same point action to close on a marine contact");
    }

    @Test
    void unsupportedAssaultMechHoldsInsteadOfSoloCharging() {
        BattleSimulation sim = openSimulation(24, 12);
        Squad squad = assaultSquad(sim, Faction.MARINE, 3, 5);
        long mech = squad.leaderId;
        sim.spawn(new EntitySpec("enemy", Faction.DEFENDER, UnitType.MARINE, 18, 5));
        squad.lastSeenEnemyX = 18;
        squad.lastSeenEnemyY = 5;

        GoapMechBehavior.replanIfNeeded(squad, sim);
        sim.setPath(mech, GridPathfinder.findPath(sim.getGrid(), 3, 5, 15, 5));
        GoapMechBehavior.INSTANCE.update(mech, sim);

        assertTrue(Paths.isEmpty(sim.movement().path(mech)),
                "a Hound without nearby infantry or another mech must not charge");
    }

    @Test
    void friendlyInfantryLeashesAssaultAdvanceToFormationDepth() {
        BattleSimulation sim = openSimulation(32, 12);
        Squad squad = assaultSquad(sim, Faction.MARINE, 3, 5);
        long mech = squad.leaderId;
        long support = spawnInfantrySupport(sim, Faction.MARINE, 5, 5);
        sim.spawn(new EntitySpec("enemy", Faction.DEFENDER, UnitType.MARINE, 26, 5));
        squad.lastSeenEnemyX = 26;
        squad.lastSeenEnemyY = 5;

        GoapMechBehavior.replanIfNeeded(squad, sim);
        GoapMechBehavior.INSTANCE.update(mech, sim);

        int[] path = sim.movement().path(mech);
        float dx = Paths.destX(path) + 0.5f - sim.world().x(support);
        float dy = Paths.destY(path) + 0.5f - sim.world().y(support);
        assertTrue(dx * dx + dy * dy
                        <= BreachAndAssault.MAX_SUPPORT_LEAD * BreachAndAssault.MAX_SUPPORT_LEAD,
                "the Hound may lead the infantry pocket but cannot run away from it");
        assertTrue(Paths.destX(path) < 20,
                "the cohesion anchor must prevent the old three-cell solo standoff");
    }

    @Test
    void anotherFriendlyMechAlsoReleasesTheAssaultAdvance() {
        BattleSimulation sim = openSimulation(32, 12);
        Squad squad = assaultSquad(sim, Faction.MARINE, 3, 5);
        long mech = squad.leaderId;
        assaultSquad(sim, Faction.MARINE, 5, 5);
        sim.spawn(new EntitySpec("enemy", Faction.DEFENDER, UnitType.MARINE, 26, 5));
        squad.lastSeenEnemyX = 26;
        squad.lastSeenEnemyY = 5;

        GoapMechBehavior.replanIfNeeded(squad, sim);
        GoapMechBehavior.INSTANCE.update(mech, sim);

        assertTrue(Paths.cellCount(sim.movement().path(mech)) > 0,
                "a lance-mate lets the Hound advance as a mech pair");
    }

    @Test
    void enemyAndDistantFriendliesDoNotCountAsSupport() {
        BattleSimulation sim = openSimulation(40, 12);
        Squad squad = assaultSquad(sim, Faction.MARINE, 3, 5);
        long mech = squad.leaderId;
        spawnInfantrySupport(sim, Faction.DEFENDER, 4, 5);
        spawnInfantrySupport(sim, Faction.MARINE, 20, 5);
        squad.lastSeenEnemyX = 30;
        squad.lastSeenEnemyY = 5;

        GoapMechBehavior.replanIfNeeded(squad, sim);
        GoapMechBehavior.INSTANCE.update(mech, sim);

        assertTrue(Paths.isEmpty(sim.movement().path(mech)));
    }

    @Test
    void brokenAssaultMechYieldsToSurvival() {
        BattleSimulation sim = openSimulation(16, 10);
        Squad squad = assaultSquad(sim, Faction.MARINE, 3, 4);
        squad.lastSeenEnemyX = 12;
        squad.lastSeenEnemyY = 4;
        WorldState broken = WorldState.EMPTY.with(Predicate.MORALE_BROKEN, true);

        assertEquals(0f, AssaultAssignedObjectiveGoal.INSTANCE.relevance(broken, squad, sim));
        Goal picked = Goal.pickMostRelevant(GoapMechBehavior.MECH_GOALS, broken, squad, sim);
        assertSame(MechSurviveContact.INSTANCE, picked);
    }

    @Test
    void rescuePickupMissionStillOutranksAssaultDoctrine() {
        BattleSimulation sim = openSimulation(16, 10);
        Squad squad = assaultSquad(sim, Faction.MARINE, 3, 4);
        squad.lastSeenEnemyX = 12;
        squad.lastSeenEnemyY = 4;
        squad.rescuePickupMech = true;
        squad.rescuePatrolCells = new int[]{3, 4, 7, 4};

        assertEquals(0f, AssaultAssignedObjectiveGoal.INSTANCE.relevance(
                WorldState.EMPTY, squad, sim));
        assertSame(PatrolRescueFormationGoal.INSTANCE,
                Goal.pickMostRelevant(GoapMechBehavior.MECH_GOALS,
                        WorldState.EMPTY, squad, sim));
    }

    private static Squad assaultSquad(BattleSimulation sim, Faction faction, int x, int y) {
        int squadId = sim.mintSquad(faction, UnitType.HEAVY_MECH);
        EntitySpec spec = MechVariant.HOUND.applyTo(new EntitySpec(
                "hound", faction, UnitType.HEAVY_MECH, x, y).squad(squadId));
        long mech = sim.spawn(spec);
        sim.world().attachMechLoadout(mech,
                MechLoadoutComponent.defaultLoadout(MechRole.ASSAULT));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = mech;
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.centroidX = x + 0.5f;
        squad.centroidY = y + 0.5f;
        return squad;
    }

    private static long spawnInfantrySupport(BattleSimulation sim, Faction faction,
                                             int x, int y) {
        int squadId = sim.mintSquad(faction, UnitType.MARINE);
        long infantry = sim.spawn(new EntitySpec(
                "support-" + squadId, faction, UnitType.MARINE, x, y).squad(squadId));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = infantry;
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.centroidX = x + 0.5f;
        squad.centroidY = y + 0.5f;
        return infantry;
    }

    private static BattleSimulation twoRoomSimulation() {
        int width = 12;
        int height = 10;
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x != 6) grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkableFloor(6, 5);
        grid.setDoorway(6, 5, true);
        return new BattleSimulation(grid, new CellTopology(width, height));
    }

    private static BattleSimulation openSimulation(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }
}
