package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.satchel.DemolitionKit;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fort restraint as units: what lifts it for a squad, what the target pickers do with a restrained core, and what
 * counts as a squad able to wreck one. The whole-fort behaviour is the shield-fort scene's.
 */
class FortRestraintTest {

    private static final int CORE_X = 30;
    private static final int CORE_Y = 20;

    // --- what lifts it -------------------------------------------------------------------------------------------

    @Test
    void anOrderWithoutLeaveNeverLiftsItHoweverNearItIs() {
        ObjectiveAssignment beside = ObjectiveAssignment.attackMove(1, CORE_X - 2, CORE_Y);
        assertFalse(FortRestraint.lifts(beside, CommandAuthority.MISSION_COMMAND, CORE_X, CORE_Y));
        assertFalse(FortRestraint.lifts(null, null, CORE_X, CORE_Y), "no order, no leave");
    }

    @Test
    void aCommandersWreckTaskLiftsItForACoreNearItsDestinationOnly() {
        ObjectiveAssignment wreck = ObjectiveAssignment.attackMove(1, CORE_X - 3, CORE_Y).withWreckAuthorised(true);
        assertTrue(FortRestraint.lifts(wreck, CommandAuthority.MISSION_COMMAND, CORE_X, CORE_Y));
        assertTrue(FortRestraint.lifts(wreck, CommandAuthority.MISSION_COMMAND,
                CORE_X - 3 + FortRestraint.LIFT_REACH_CELLS, CORE_Y), "the edge of its reach is still its destination");
        assertFalse(FortRestraint.lifts(wreck, CommandAuthority.MISSION_COMMAND,
                CORE_X - 3 + FortRestraint.LIFT_REACH_CELLS + 1, CORE_Y), "the next building along is not");
        assertFalse(FortRestraint.lifts(ObjectiveAssignment.attackMove(1, 5, 5).withWreckAuthorised(true),
                CommandAuthority.MISSION_COMMAND, CORE_X, CORE_Y), "leave for one place is not leave for another");
    }

    @Test
    void aPlayersOrderPointedAtACoreLiftsItAndOneElsewhereDoesNot() {
        ObjectiveAssignment at = ObjectiveAssignment.attackMove(1, CORE_X + 1, CORE_Y);
        assertTrue(FortRestraint.lifts(at, CommandAuthority.PLAYER_INTERVENTION, CORE_X, CORE_Y));
        assertFalse(FortRestraint.lifts(ObjectiveAssignment.attackMove(1, 90, 90),
                CommandAuthority.PLAYER_INTERVENTION, CORE_X, CORE_Y), "a player walking past is not asking for it");
        assertFalse(FortRestraint.lifts(at, CommandAuthority.MISSION_COMMAND, CORE_X, CORE_Y),
                "the same order from a commander that did not say wreck");
    }

    @Test
    void theSwitchTurnsTheWholeRuleOff() {
        FortRestraint on = new FortRestraint(true);
        FortRestraint off = new FortRestraint(false);
        on.restrain(7L);
        off.restrain(7L);
        assertTrue(on.restrains(7L));
        assertFalse(on.restrains(8L));
        assertFalse(off.restrains(7L), "off means every core is an ordinary target");
        assertFalse(off.any());
    }

    // --- what the target pickers do ------------------------------------------------------------------------------

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(60, 40);
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 60; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(60, 40));
    }

    private static long hostileCore(BattleSimulation sim) {
        long core = sim.spawn(MapTurret.create("core", Faction.DEFENDER, TurretCatalogRegistry.VULCAN_STRUCTURE_ID,
                CORE_X, CORE_Y));
        sim.getUnitIndex().restraint().restrain(core);
        sim.getUnitIndex().withdrawFromHostileScans(core);
        return core;
    }

    private static Squad squadWithOneMarine(BattleSimulation sim, long[] marineOut) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        marineOut[0] = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, CORE_X - 6, CORE_Y)
                .squad(squadId));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = marineOut[0];
        squad.aliveMembers = 1;
        return squad;
    }

    @Test
    void aMarineInViewOfAHostileCoreDoesNotPickItAsATargetNorFireOnItInPassing() {
        BattleSimulation sim = arena();
        long core = hostileCore(sim);
        long[] marine = new long[1];
        squadWithOneMarine(sim, marine);
        sim.getUnitIndex().rebuild(sim.getRoster());

        assertEquals(0L, sim.getTacticalScoring().findBestTarget(marine[0]), "nothing to shoot but the core");
        assertEquals(0L, sim.getTacticalScoring().closestEnemyInAttackRange(marine[0]));
        assertFalse(sim.getTacticalScoring().mayEngage(marine[0], core), "a satchel or rocket asks this too");
        assertEquals(0, sim.getUnitIndex().countHostileCombatants(CORE_X, CORE_Y, 10f, Faction.MARINE, 0L),
                "and it is not a threat anyone's picture counts");
    }

    @Test
    void theCoreIsStillThereToBeHitSeenAndGatheredJustNotOfferedAsSomethingToShootAt() {
        BattleSimulation sim = arena();
        long core = hostileCore(sim);
        sim.getUnitIndex().rebuild(sim.getRoster());

        LongBucket near = new LongBucket();
        sim.getUnitIndex().gather(CORE_X, CORE_Y, 4f, near);
        boolean found = false;
        for (int i = 0; i < near.size; i++) found |= near.ids[i] == core;
        assertTrue(found, "ballistics and fog still find the body");
    }

    @Test
    void aSquadWithLeaveToWreckPicksTheCoreAndShootsAtIt() {
        BattleSimulation sim = arena();
        long core = hostileCore(sim);
        long[] marine = new long[1];
        Squad squad = squadWithOneMarine(sim, marine);
        squad.assignedObjective = ObjectiveAssignment.attackMove(squad.id, CORE_X - 3, CORE_Y).withWreckAuthorised(true);
        squad.assignedAuthority = CommandAuthority.MISSION_COMMAND;
        sim.getUnitIndex().rebuild(sim.getRoster());

        assertEquals(core, sim.getTacticalScoring().findBestTarget(marine[0]));
        assertEquals(core, sim.getTacticalScoring().closestEnemyInAttackRange(marine[0]));
        assertTrue(sim.getTacticalScoring().mayEngage(marine[0], core));
    }

    @Test
    void aSquadUnderAPlayersOrderToThePlaceMayToo() {
        BattleSimulation sim = arena();
        long core = hostileCore(sim);
        long[] marine = new long[1];
        Squad squad = squadWithOneMarine(sim, marine);
        squad.assignedObjective = ObjectiveAssignment.attackMove(squad.id, CORE_X - 2, CORE_Y);
        squad.assignedAuthority = CommandAuthority.PLAYER_INTERVENTION;
        sim.getUnitIndex().rebuild(sim.getRoster());

        assertEquals(core, sim.getTacticalScoring().findBestTarget(marine[0]));
    }

    @Test
    void leaveForOneCoreIsNotLeaveForAnother() {
        BattleSimulation sim = arena();
        hostileCore(sim);
        long[] marine = new long[1];
        Squad squad = squadWithOneMarine(sim, marine);
        squad.assignedObjective = ObjectiveAssignment.attackMove(squad.id, 5, 5).withWreckAuthorised(true);
        squad.assignedAuthority = CommandAuthority.MISSION_COMMAND;
        sim.getUnitIndex().rebuild(sim.getRoster());

        assertEquals(0L, sim.getTacticalScoring().findBestTarget(marine[0]));
    }

    @Test
    void ordinaryHostilesAreStillTargetsBesideARestrainedCore() {
        BattleSimulation sim = arena();
        hostileCore(sim);
        long[] marine = new long[1];
        squadWithOneMarine(sim, marine);
        long guard = sim.spawn(new EntitySpec("guard", Faction.DEFENDER, UnitType.MARINE, CORE_X - 2, CORE_Y + 1));
        sim.getUnitIndex().rebuild(sim.getRoster());

        assertEquals(guard, sim.getTacticalScoring().findBestTarget(marine[0]), "a fort still defends itself");
    }

    @Test
    void withTheRestraintOffTheCoreIsAnOrdinaryTarget() {
        BattleSimulation sim = arena();
        sim.getUnitIndex().restraint(new FortRestraint(false));
        long core = hostileCore(sim);
        long[] marine = new long[1];
        squadWithOneMarine(sim, marine);
        sim.getUnitIndex().rebuild(sim.getRoster());

        assertEquals(core, sim.getTacticalScoring().findBestTarget(marine[0]));
        assertTrue(sim.getTacticalScoring().mayEngage(marine[0], core));
    }

    // --- what counts as able to wreck ----------------------------------------------------------------------------

    private static long carrying(BattleSimulation sim, String specialId, int x) {
        EntitySpec spec = new EntitySpec("carrier-" + x, Faction.MARINE, UnitType.MARINE, x, 5);
        if (specialId != null) spec.specialEquipment(SpecialEquipmentRegistry.require(specialId), 0);
        return sim.spawn(spec);
    }

    @Test
    void onlyASatchelKitMakesAMarineAbleToWreckABuilding() {
        BattleSimulation sim = arena();
        assertTrue(DemolitionKit.carried(sim.world(), carrying(sim, SpecialEquipmentRegistry.SATCHEL_CHARGE_ID, 3)));
        assertFalse(DemolitionKit.carried(sim.world(), carrying(sim, null, 4)), "a rifleman carries nothing to plant");
        assertFalse(DemolitionKit.carried(sim.world(), carrying(sim, SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID, 5)),
                "a rocket is not a demolition charge");
    }

    @Test
    void aDemolitionOrderAimedAtACoreLetsTheSatchelCarrierPlantOnIt() {
        BattleSimulation sim = arena();
        long core = hostileCore(sim);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long sapper = sim.spawn(new EntitySpec("sapper", Faction.MARINE, UnitType.MARINE, CORE_X - 2, CORE_Y)
                .specialEquipment(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID), 0)
                .squad(squadId));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = sapper;
        squad.aliveMembers = 1;
        sim.getUnitIndex().rebuild(sim.getRoster());

        assertFalse(sim.getTacticalScoring().mayEngage(sapper, core), "without leave the charge stays in the bag");
        squad.assignedObjective = ObjectiveAssignment.attackMove(squadId, CORE_X - 2, CORE_Y).withWreckAuthorised(true);
        squad.assignedAuthority = CommandAuthority.MISSION_COMMAND;
        assertTrue(sim.getTacticalScoring().mayEngage(sapper, core));
    }
}
