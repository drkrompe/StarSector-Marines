package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Relevance gating for {@link BackstopAssignedSquadGoal}. Sister coverage to
 * {@link OverwatchKillZoneGoalTest} — the morale gate is the same pattern,
 * carved out for the same reason (role hint, not unit-level objective).
 */
public class BackstopAssignedSquadGoalTest {

    private static final int W = 12;
    private static final int H = 12;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Squad armoredSquadWithFriendlyInfantry(BattleSimulation sim) {
        int mechSid = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long mech = sim.spawn(new EntitySpec("ar0", Faction.MARINE, UnitType.HEAVY_MECH, 3, 3).squad(mechSid));
        sim.world().attachMechLoadout(mech, MechLoadoutComponent.defaultLoadout(MechRole.ARMORED_SUPPORT));
        Squad mechSquad = sim.getSquad(mechSid);
        mechSquad.aliveMembers = 1;

        int infSid = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long grunt = sim.spawn(new EntitySpec("m0", Faction.MARINE, UnitType.MARINE, 5, 5).squad(infSid));
        Squad infSquad = sim.getSquad(infSid);
        infSquad.aliveMembers = 1;

        return mechSquad;
    }

    @Test
    public void relevancePositiveWithArmoredAndFriendlyInfantry() {
        BattleSimulation sim = openSim();
        Squad squad = armoredSquadWithFriendlyInfantry(sim);
        assertTrue(BackstopAssignedSquadGoal.INSTANCE.relevance(WorldState.EMPTY, squad, sim) > 0f,
                "ARMORED_SUPPORT + friendly non-mech squad → backstop is on the table");
    }

    @Test
    public void relevanceZeroWhenMoraleBroken() {
        // Same shape as the OverwatchKillZone gate — see that test's comment
        // for the playtest provenance. A backstop squad whose morale has
        // broken must yield so SurviveContact's BreakContact can pull them
        // out of the line.
        BattleSimulation sim = openSim();
        Squad squad = armoredSquadWithFriendlyInfantry(sim);
        WorldState broken = WorldState.EMPTY.with(Predicate.MORALE_BROKEN, true);

        assertEquals(0f, BackstopAssignedSquadGoal.INSTANCE.relevance(broken, squad, sim),
                "morale-broken backstop squad → role hint yields, SurviveContact takes over");
    }

    @Test
    public void tankWithoutAnchorHoldsInsteadOfFollowingGenericAssignment() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long tank = sim.spawn(new EntitySpec(
                "solo-tank", Faction.MARINE, UnitType.HEAVY_MECH, 3, 3)
                .squad(squadId));
        sim.world().attachMechLoadout(tank,
                MechLoadoutComponent.defaultLoadout(MechRole.ARMORED_SUPPORT));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = tank;
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.assignedObjective = ObjectiveAssignment.advanceTrack(squadId, 10, 3);

        Goal picked = Goal.pickMostRelevant(GoapMechBehavior.MECH_GOALS,
                WorldState.EMPTY, squad, sim);

        assertSame(MechAssignedObjectiveGoal.INSTANCE, picked,
                "the exact command stays represented for the mixed-role dispatcher");
        picked.customPlan(squad, sim).currentStep().action.execute(tank, squad, sim);
        assertTrue(Paths.isEmpty(sim.world().path(tank)),
                "the Tank member still holds rather than servicing the cell without an anchor");
    }

    @Test
    public void distantAnchorMustShareTheSpecificMissionTarget() {
        int width = 48;
        NavigationGrid grid = new NavigationGrid(width, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(
                grid, new CellTopology(width, H));

        int tankSquadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long tank = sim.spawn(new EntitySpec(
                "tank", Faction.MARINE, UnitType.HEAVY_MECH, 3, 5)
                .squad(tankSquadId));
        sim.world().attachMechLoadout(tank,
                MechLoadoutComponent.defaultLoadout(MechRole.ARMORED_SUPPORT));
        Squad tankSquad = sim.getSquad(tankSquadId);
        tankSquad.leaderId = tank;
        tankSquad.aliveMembers = 1;
        tankSquad.centroidX = 3.5f;
        tankSquad.centroidY = 5.5f;
        tankSquad.assignedObjective = ObjectiveAssignment.rushObjective(
                tankSquadId, 11, 2, 30, 5);

        int infantrySquadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long infantry = sim.spawn(new EntitySpec(
                "distant-infantry", Faction.MARINE, UnitType.MARINE, 40, 5)
                .squad(infantrySquadId));
        Squad infantrySquad = sim.getSquad(infantrySquadId);
        infantrySquad.leaderId = infantry;
        infantrySquad.aliveMembers = 1;
        infantrySquad.centroidX = 40.5f;
        infantrySquad.centroidY = 5.5f;
        infantrySquad.assignedObjective = ObjectiveAssignment.rushObjective(
                infantrySquadId, 12, 2, 30, 5);

        assertNull(BackstopAssignedSquad.pickBackedSquad(
                        tank, tankSquad, sim),
                "sharing only a broad zone does not invent an across-map support task");

        infantrySquad.assignedObjective = ObjectiveAssignment.rushObjective(
                infantrySquadId, 11, 2, 30, 5);
        assertNull(BackstopAssignedSquad.pickBackedSquad(
                        tank, tankSquad, sim),
                "even an exact mission match cannot waive the nearby-anchor cap");

        sim.world().setCellPos(infantry, 15, 5);
        infantrySquad.centroidX = 15.5f;
        assertSame(infantrySquad, BackstopAssignedSquad.pickBackedSquad(
                        tank, tankSquad, sim),
                "a nearby exact mission match is a valid support relationship");

        MechLoadoutComponent loadout = sim.world().mechLoadout(tank);
        loadout.assignedSquadId = infantrySquadId;
        infantrySquad.assignedObjective = ObjectiveAssignment.rushObjective(
                infantrySquadId, 12, 2, 30, 5);
        BackstopAssignedSquad.INSTANCE.execute(tank, tankSquad, sim);
        assertEquals(-1, loadout.assignedSquadId,
                "a cached anchor is revalidated when its mission changes");

        infantrySquad.assignedObjective = ObjectiveAssignment.rushObjective(
                infantrySquadId, 11, 2, 30, 5);
        sim.world().setCellPos(infantry, 40, 5);
        infantrySquad.centroidX = 40.5f;
        loadout.assignedSquadId = infantrySquadId;
        BackstopAssignedSquad.INSTANCE.execute(tank, tankSquad, sim);
        assertEquals(-1, loadout.assignedSquadId,
                "a cached mission match is still released beyond the nearby-anchor cap");
    }

    @Test
    public void tankUsesOnlyItsOwnSquadsThreatAxis() {
        int width = 32;
        NavigationGrid grid = new NavigationGrid(width, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(
                grid, new CellTopology(width, H));
        int tankSquadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long tank = sim.spawn(new EntitySpec(
                "tank", Faction.MARINE, UnitType.HEAVY_MECH, 3, 5)
                .squad(tankSquadId));
        sim.world().attachMechLoadout(tank,
                MechLoadoutComponent.defaultLoadout(MechRole.ARMORED_SUPPORT));
        Squad tankSquad = sim.getSquad(tankSquadId);
        tankSquad.leaderId = tank;
        tankSquad.aliveMembers = 1;
        tankSquad.centroidX = 3.5f;
        tankSquad.centroidY = 5.5f;

        int infantrySquadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long infantry = sim.spawn(new EntitySpec(
                "infantry", Faction.MARINE, UnitType.MARINE, 10, 5)
                .squad(infantrySquadId));
        Squad infantrySquad = sim.getSquad(infantrySquadId);
        infantrySquad.leaderId = infantry;
        infantrySquad.aliveMembers = 1;
        infantrySquad.centroidX = 10.5f;
        infantrySquad.centroidY = 5.5f;
        infantrySquad.lastSeenEnemyX = 25;
        infantrySquad.lastSeenEnemyY = 5;

        BackstopAssignedSquad.INSTANCE.execute(tank, tankSquad, sim);
        MechLoadoutComponent loadout = sim.world().mechLoadout(tank);
        assertEquals(-1, loadout.frontlineThreatX,
                "the backed squad cannot donate hostile knowledge");
        assertEquals(10, loadout.overwatchCellX,
                "without own belief the Tank anchors on the supported centroid");

        tankSquad.lastSeenEnemyX = 25;
        tankSquad.lastSeenEnemyY = 5;
        BackstopAssignedSquad.INSTANCE.execute(tank, tankSquad, sim);
        assertEquals(25, loadout.frontlineThreatX);
        assertTrue(loadout.overwatchCellX > 10,
                "its own threat axis moves the Tank to the facing side");
    }
}
