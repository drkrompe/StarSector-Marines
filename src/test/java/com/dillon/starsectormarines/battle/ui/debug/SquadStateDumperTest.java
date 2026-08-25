package com.dillon.starsectormarines.battle.ui.debug;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.command.ConquestCommand;
import com.dillon.starsectormarines.battle.command.ConquestCommandDisclosure;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.CommanderService;
import com.dillon.starsectormarines.battle.combat.FireGate;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.combat.FiringSystem;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadStateDumperTest {

    @Test
    void contactPictureCarriesDecisionAgeRatioAndPrimaryEvidence() throws Exception {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        for (int i = 0; i < 4; i++) {
            long member = sim.spawn(new EntitySpec("Marine " + i,
                    Faction.MARINE, UnitType.MARINE, 10, 9 + i).squad(squadId));
            if (i == 0) squad.leaderId = member;
        }
        long enemy = sim.spawn(new EntitySpec("Raider", Faction.DEFENDER,
                UnitType.MARINE, 15, 10));

        sim.advance(BattleSimulation.TICK_DT);
        sim.world().setCellPos(enemy, 14, 10);
        sim.advance(BattleSimulation.TICK_DT);
        squad._contactDoctrineChangedThisTick = true;

        JSONObject picture = SquadStateDumper.buildSquadJson(squad, sim)
                .getJSONObject("contactPicture");
        JSONObject evidence = picture.getJSONObject("primaryEvidence");

        assertEquals(sim.simTickIndex, picture.getInt("tick"));
        assertEquals(0, picture.getInt("ageTicks"));
        assertEquals(0.25, picture.getDouble("hostileToFriendlyRatio"), 0.001);
        assertEquals(enemy, picture.getLong("primaryContactId"));
        assertEquals("Raider", picture.getString("primaryContactName"));
        assertTrue(picture.getBoolean("doctrineChangedThisTick"));
        assertTrue(picture.getBoolean("holdReactionFresh"));
        assertEquals(squad.contactPicture.contactInitiative().name(),
                picture.getString("contactInitiative"));
        assertEquals(squad.contactPicture.primaryEngageableMembers(),
                picture.getInt("primaryEngageableMembers"));
        assertEquals(squad.contactPicture.primaryEngageableFireTeams(),
                picture.getInt("primaryEngageableFireTeams"));
        assertEquals(squad.contactPicture.liveFireTeams(),
                picture.getInt("liveFireTeams"));
        assertFalse(picture.getBoolean("advanceHardHoldActive"));
        assertEquals(TacticalScoring.HOLD_AFTER_LOS_TICKS,
                picture.getInt("holdAfterLosWindowTicks"));
        assertEquals("DIRECT", evidence.getString("source"));
        assertEquals(sim.simTickIndex, evidence.getInt("lastSeenTick"));
        assertEquals(0, evidence.getInt("ageTicks"));
        assertTrue(evidence.getBoolean("observedThisTick"));
        assertTrue(evidence.getBoolean("freshMotionSample"));
        assertEquals(15, evidence.getInt("previousDirectCellX"));
        assertEquals(10, evidence.getInt("previousDirectCellY"));
        assertEquals(sim.simTickIndex - 1, evidence.getInt("previousDirectTick"));
    }

    @Test
    void memberDumpCarriesAcquisitionAndLastFireGate() throws Exception {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long member = sim.spawn(new EntitySpec("Marine", Faction.MARINE,
                UnitType.MARINE, 5, 5).squad(squadId));
        long enemy = sim.spawn(new EntitySpec("Raider", Faction.DEFENDER,
                UnitType.MARINE, 8, 5));
        sim.world().setAttackRange(member, 10f);
        sim.combat().setTargetId(member, enemy);
        sim.combat().setReflexTimer(member, 0f);
        sim.combat().setFireIntent(member, enemy, FireStance.STANCED, false);
        new FiringSystem(sim.getGrid(), sim.getRoster()).tick(sim);

        JSONArray members = SquadStateDumper.buildMembersJson(squad, sim, member);
        JSONObject row = members.getJSONObject(0);
        assertEquals(enemy, row.getLong("reflexTargetId"));
        assertEquals("Raider", row.getString("reflexTargetName"));
        assertEquals(0.0, row.getDouble("reflexTimer"), 1e-6);
        assertEquals(FireGate.FIRED.name(), row.getString("lastFireGate"));
        assertEquals(sim.getSimTickIndex(), row.getInt("lastFireGateTick"));
        assertEquals(0, row.getInt("lastFireGateAgeTicks"));
    }

    @Test
    void squadDumpPublishesSharedDirectiveProvenanceAndConquestDetail()
            throws Exception {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long member = sim.spawn(new EntitySpec("Marine", Faction.MARINE,
                UnitType.MARINE, 5, 5).squad(squadId));
        sim.spawn(new EntitySpec("distant defender", Faction.DEFENDER,
                UnitType.MILITIA, 30, 22));
        squad.leaderId = member;
        ConquestCommand command = new ConquestCommand(
                TraversalAxis.SOUTH_TO_NORTH);
        sim.setAutonomousCommander(Faction.MARINE, command,
                ConquestCommandDisclosure.INSTANCE);
        sim.advance(CommanderService.COMMANDER_TICK_PERIOD
                + BattleSimulation.TICK_DT);

        JSONObject dump = SquadStateDumper.buildSquadJson(squad, sim);

        assertTrue(dump.isNull("assignedObjective"));
        JSONObject commander = dump.getJSONObject("commander");
        JSONObject commonDirective = commander.getJSONObject("squadDirective");
        assertEquals("MARINE", commander.getString("perspective"));
        assertEquals("conquest-attacker", commander.getString("strategy"));
        assertEquals("LANE_ADVANCE", commander.getString("phase"));
        assertEquals("conquest-attacker", commonDirective.getString("issuer"));
        assertEquals("MISSION_COMMAND", commonDirective.getString("authority"));
        assertEquals("UNASSIGNED", commonDirective.getString("status"));
        assertEquals("NO_ACTIONABLE_TRACK_TARGET",
                commonDirective.getString("reason"));
        assertEquals("no incumbent assignment to retain",
                commonDirective.getString("dispositionReason"));
        assertEquals(commander.getInt("tick"),
                commonDirective.getInt("issuedTick"));
        assertEquals(-1, commonDirective.getInt("leaseUntilTick"));
        assertTrue(commonDirective.isNull("assignment"));
        assertTrue(commonDirective.isNull("proposedAssignment"));

        JSONObject conquest = dump.getJSONObject("conquestCommand");
        assertEquals("SOUTH_TO_NORTH", conquest.getString("axis"));
        assertEquals("MARINE", conquest.getString("perspective"));
        assertEquals("LANE_ADVANCE", conquest.getString("phase"));
        assertEquals(commonDirective.getString("reason"), conquest
                .getJSONObject("squadDirective").getString("reason"));
        assertEquals(3, conquest.getJSONArray("tracks").length());
    }

    @Test
    void dumpShowsExternalOwnershipAndFormUpExecutionSeparately()
            throws Exception {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        squad.campaignSquadId = "campaign-1";
        squad.expectedSize = 12;
        squad.originalSize = 4;
        ObjectiveAssignment order = ObjectiveAssignment.escort(squadId, 8, 8);
        sim.assignSquadCommand(order, CommandAuthority.PAYLOAD,
                "test-payload", "hold while unloading");

        JSONObject dump = SquadStateDumper.buildSquadJson(squad, sim);

        assertTrue(dump.isNull("commander"));
        assertEquals("ESCORT",
                dump.getJSONObject("assignedObjective").getString("kind"));
        JSONObject directive = dump.getJSONObject("commandDirective");
        assertEquals("test-payload", directive.getString("issuer"));
        assertEquals("PAYLOAD", directive.getString("authority"));
        JSONObject execution = dump.getJSONObject("assignmentExecution");
        assertEquals("SUSPENDED", execution.getString("status"));
        assertEquals("FORMING_UP", execution.getString("suspensionReason"));
        assertTrue(execution.isNull("assignment"));
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(32, 24);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid,
                new CellTopology(grid.getWidth(), grid.getHeight()));
    }
}
