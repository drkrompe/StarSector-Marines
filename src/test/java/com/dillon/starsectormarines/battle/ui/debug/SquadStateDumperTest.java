package com.dillon.starsectormarines.battle.ui.debug;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.command.ConquestCommand;
import com.dillon.starsectormarines.battle.command.ConquestCommandDisclosure;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.CommanderService;
import com.dillon.starsectormarines.battle.command.SabotageCommand;
import com.dillon.starsectormarines.battle.command.SabotageCommandDisclosure;
import com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective;
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
        assertEquals(5.5, row.getDouble("x"), 1e-6);
        assertEquals(5.5, row.getDouble("y"), 1e-6);
        assertTrue(row.getBoolean("targetCellVisible"));
        assertTrue(row.getBoolean("targetClearShot"));
    }

    @Test
    void memberDumpExposesProjectedVisibilityVersusPhysicalShotMismatch()
            throws Exception {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long member = sim.spawn(new EntitySpec("Marine", Faction.MARINE,
                UnitType.MARINE, 5, 5).squad(squadId));
        long enemy = sim.spawn(new EntitySpec("Raider", Faction.DEFENDER,
                UnitType.MARINE, 7, 6));
        sim.world().setPos(member, 5.1f, 5.9f);
        sim.world().setPos(enemy, 7.9f, 6.9f);
        sim.getGrid().setWalkable(6, 6, false);
        sim.world().setTargetId(member, enemy);

        JSONObject row = SquadStateDumper.buildMembersJson(
                squad, sim, member).getJSONObject(0);

        assertEquals(5.1, row.getDouble("x"), 1e-6);
        assertEquals(5.9, row.getDouble("y"), 1e-6);
        assertTrue(row.getBoolean("targetCellVisible"));
        assertFalse(row.getBoolean("targetClearShot"));
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
        assertEquals(-1, commonDirective.getInt("stableUntilTick"));
        assertTrue(commonDirective.isNull("assignment"));
        assertTrue(commonDirective.isNull("proposedAssignment"));

        JSONObject conquest = dump.getJSONObject("conquestCommand");
        assertEquals("SOUTH_TO_NORTH", conquest.getString("axis"));
        assertEquals("MARINE", conquest.getString("perspective"));
        assertEquals("LANE_ADVANCE", conquest.getString("phase"));
        assertEquals(commonDirective.getString("reason"), conquest
                .getJSONObject("squadDirective").getString("reason"));
        assertFalse(conquest.getJSONObject("squadDirective")
                .getBoolean("distantCaptureDeferred"));
        assertEquals(3, conquest.getJSONArray("tracks").length());

        JSONObject influence = dump.getJSONObject("currentCommanderInfluence");
        assertEquals("MARINE", influence.getString("perspective"));
        assertEquals(sim.getGrid().getWidth(), influence.getInt("worldWidth"));
        assertEquals(sim.getGrid().getHeight(), influence.getInt("worldHeight"));
        JSONArray contacts = influence.getJSONArray("contacts");
        for (int i = 0; i < contacts.length(); i++) {
            JSONObject contact = contacts.getJSONObject(i);
            assertFalse(contact.has("liveCellX"));
            assertFalse(contact.has("liveCellY"));
            assertTrue(contact.has("observedTick"));
            assertTrue(contact.has("reporterSquadId"));
        }
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

    @Test
    void dumpPublishesConquestLaneStageOrderAndMarker() throws Exception {
        BattleSimulation sim = openSim(32, 100);
        int rearId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad rear = sim.getSquad(rearId);
        long rearMember = sim.spawn(new EntitySpec("rear", Faction.MARINE,
                UnitType.MARINE, 5, 2).squad(rearId));
        rear.leaderId = rearMember;

        int reporterId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad reporter = sim.getSquad(reporterId);
        long reporterMember = sim.spawn(new EntitySpec("reporter", Faction.MARINE,
                UnitType.MARINE, 5, 80).squad(reporterId));
        reporter.leaderId = reporterMember;
        sim.spawn(new EntitySpec("defender", Faction.DEFENDER,
                UnitType.MILITIA, 5, 82));

        ConquestCommand command = new ConquestCommand(
                TraversalAxis.SOUTH_TO_NORTH);
        sim.setAutonomousCommander(Faction.MARINE, command,
                ConquestCommandDisclosure.INSTANCE);
        sim.advance(BattleSimulation.TICK_DT);
        sim.advance(CommanderService.COMMANDER_TICK_PERIOD
                + BattleSimulation.TICK_DT);

        JSONObject dump = SquadStateDumper.buildSquadJson(rear, sim);
        JSONObject assigned = dump.getJSONObject("assignedObjective");
        assertEquals("ADVANCE_TRACK", assigned.getString("kind"));
        JSONObject common = dump.getJSONObject("commander")
                .getJSONObject("squadDirective");
        assertEquals("ADVANCE_TRACK", common.getJSONObject("assignment")
                .getString("kind"));
        JSONObject conquest = dump.getJSONObject("conquestCommand")
                .getJSONObject("squadDirective");
        assertEquals("TRACK_LINE_ADVANCE", conquest.getString("reason"));
        assertEquals("ADVANCE_TRACK", conquest.getString("assignmentKind"));
        assertEquals(assigned.getInt("targetCellX"),
                conquest.getInt("markerCellX"));
        assertEquals(assigned.getInt("targetCellY"),
                conquest.getInt("markerCellY"));
    }

    @Test
    void dumpPublishesSabotageSiteIdentityAndGroupState() throws Exception {
        BattleSimulation sim = openSim();
        sim.addObjective(new ChargeSiteObjective(
                20, 12, 8f, "SAB-01", "reactor"));
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long member = sim.spawn(new EntitySpec("security", Faction.MARINE,
                UnitType.MARINE, 3, 12).squad(squadId));
        squad.leaderId = member;
        sim.setAutonomousCommander(Faction.MARINE, new SabotageCommand(),
                SabotageCommandDisclosure.INSTANCE);
        sim.advance(CommanderService.COMMANDER_TICK_PERIOD
                + BattleSimulation.TICK_DT);

        JSONObject dump = SquadStateDumper.buildSquadJson(squad, sim);
        JSONObject sabotage = dump.getJSONObject("sabotageCommand");
        JSONObject directive = sabotage.getJSONObject("squadDirective");
        JSONObject site = sabotage.getJSONArray("sites").getJSONObject(0);
        assertEquals("SAB-01", site.getString("id"));
        assertEquals(0, directive.getInt("siteIndex"));
        assertEquals("SECURITY", directive.getString("groupRole"));
        assertEquals("CLEAR_ZONE", directive.getString("assignmentKind"));
        assertEquals("sabotage-attacker",
                dump.getJSONObject("commander").getString("strategy"));
    }

    private static BattleSimulation openSim() {
        return openSim(32, 24);
    }

    private static BattleSimulation openSim(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }
}
