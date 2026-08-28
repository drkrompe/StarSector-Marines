package com.dillon.starsectormarines.battle.ui.debug;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.command.ConquestCommand;
import com.dillon.starsectormarines.battle.command.AssaultCommand;
import com.dillon.starsectormarines.battle.command.AssaultCommandDisclosure;
import com.dillon.starsectormarines.battle.command.AssaultDefenderCommand;
import com.dillon.starsectormarines.battle.command.AssaultDefenderCommandDisclosure;
import com.dillon.starsectormarines.battle.command.ConquestCommandDisclosure;
import com.dillon.starsectormarines.battle.command.ExtractionCommand;
import com.dillon.starsectormarines.battle.command.ExtractionCommandDisclosure;
import com.dillon.starsectormarines.battle.command.ExtractionDefenderCommand;
import com.dillon.starsectormarines.battle.command.ExtractionDefenderCommandDisclosure;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.CommanderService;
import com.dillon.starsectormarines.battle.command.SabotageCommand;
import com.dillon.starsectormarines.battle.command.SabotageCommandDisclosure;
import com.dillon.starsectormarines.battle.command.SabotageDefenderCommand;
import com.dillon.starsectormarines.battle.command.SabotageDefenderCommandDisclosure;
import com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.command.objective.ExtractionObjective;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPayload;
import com.dillon.starsectormarines.battle.combat.FireGate;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.combat.FiringSystem;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadStateDumperTest {

    @Test
    void snapshotWriteIsSynchronousAndUsesExpectedCommonPath() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<JSONObject> json = new AtomicReference<>();
        AtomicBoolean onlyIfChanged = new AtomicBoolean(true);
        SettingsAPI previous = Global.getSettings();
        SettingsAPI settings = (SettingsAPI) Proxy.newProxyInstance(
                SettingsAPI.class.getClassLoader(),
                new Class<?>[]{SettingsAPI.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("writeJSONToCommon")) {
                        path.set((String) args[0]);
                        json.set((JSONObject) args[1]);
                        onlyIfChanged.set((Boolean) args[2]);
                    }
                    return null;
                });
        JSONObject snapshot = new JSONObject().put("squad", 180);

        try {
            Global.setSettings(settings);
            SquadStateDumper.writeSnapshot(
                    "starsector_marines/debug/squad_180.json", snapshot);
        } finally {
            Global.setSettings(previous);
        }

        assertEquals("starsector_marines/debug/squad_180.json", path.get());
        assertSame(snapshot, json.get());
        assertFalse(onlyIfChanged.get());
    }

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
        JSONObject commandState = conquest.getJSONObject("squadState");
        assertEquals(1, commandState.getInt("aliveMembers"));
        assertEquals(1, commandState.getInt("activePathMembers"));
        assertTrue(commandState.has("membersInTargetZone"));
        assertTrue(commandState.getBoolean("localContact"));
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
    void squadDumpPublishesAssaultSearchPicture() throws Exception {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long member = sim.spawn(new EntitySpec("searcher", Faction.MARINE,
                UnitType.MARINE, 3, 3).squad(squadId));
        squad.leaderId = member;
        for (int y = 0; y < sim.getGrid().getHeight(); y++) {
            sim.getGrid().setWalkable(16, y, false);
        }
        sim.spawn(new EntitySpec("hidden defender", Faction.DEFENDER,
                UnitType.MILITIA, 25, 20).health(10_000f));
        sim.setAutonomousCommander(Faction.MARINE, new AssaultCommand(),
                AssaultCommandDisclosure.INSTANCE);
        sim.advance(CommanderService.COMMANDER_TICK_PERIOD
                + BattleSimulation.TICK_DT);

        JSONObject dump = SquadStateDumper.buildSquadJson(squad, sim);
        assertFalse(dump.isNull("commander"));
        JSONObject assault = dump.getJSONObject("assaultCommand");
        JSONObject directive = assault.getJSONObject("squadDirective");
        assertEquals("assault-attacker",
                dump.getJSONObject("commander").getString("strategy"));
        assertEquals("SEARCH", assault.getString("phase"));
        assertEquals("SWEEP_SECTOR", directive.getString("assignmentKind"));
        assertTrue(directive.getInt("sectorIndex") >= 0);
        JSONArray sectors = assault.getJSONArray("sectors");
        assertTrue(sectors.length() >= 4);
        for (int i = 0; i < sectors.length(); i++) {
            JSONObject sector = sectors.getJSONObject(i);
            assertTrue(sector.has("visitedLegs"));
            assertTrue(sector.has("believedContacts"));
            assertFalse(sector.has("liveDefenders"));
            assertFalse(sector.has("hostileCellX"));
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
        assertEquals("AWAITING_PLANTER", site.getString("groupReason"));
        assertEquals(0, site.getInt("activeKitDrops"));
        assertEquals(0, site.getInt("unclaimedKitDrops"));
        assertEquals(0, directive.getInt("siteIndex"));
        assertEquals("SECURITY", directive.getString("groupRole"));
        assertEquals("CLEAR_ZONE", directive.getString("assignmentKind"));
        assertEquals("sabotage-attacker",
                dump.getJSONObject("commander").getString("strategy"));
    }

    @Test
    void defenderDumpPublishesAlarmCoverageWithoutAttackerTaskFacts()
            throws Exception {
        BattleSimulation sim = openSim();
        sim.addObjective(new ChargeSiteObjective(
                20, 12, 8f, "SAB-01", "reactor"));
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        Squad squad = sim.getSquad(squadId);
        long member = sim.spawn(new EntitySpec("security", Faction.DEFENDER,
                UnitType.MILITIA, 3, 12).squad(squadId).role(UnitRole.PATROL));
        squad.leaderId = member;
        sim.setAutonomousCommander(Faction.DEFENDER,
                new SabotageDefenderCommand(java.util.Set.of(squadId)),
                SabotageDefenderCommandDisclosure.INSTANCE);
        sim.advance(CommanderService.COMMANDER_TICK_PERIOD
                + BattleSimulation.TICK_DT);

        JSONObject dump = SquadStateDumper.buildSquadJson(squad, sim);
        JSONObject defense = dump.getJSONObject("sabotageDefenseCommand");
        JSONObject directive = defense.getJSONObject("squadDirective");
        JSONObject site = defense.getJSONArray("sites").getJSONObject(0);
        assertEquals("SAB-01", site.getString("id"));
        assertEquals("QUIET", site.getBoolean("alarmActive")
                ? "ACTIVE" : "QUIET");
        assertEquals("ROUTINE_SECURITY", directive.getString("role"));
        assertEquals("DEFEND_SITE", directive.getString("assignmentKind"));
        assertFalse(site.has("progress"));
        assertFalse(site.has("plantDuration"));
        assertFalse(site.has("planterOnSite"));
        assertFalse(site.has("activeKitDrops"));
        assertEquals("sabotage-defender",
                dump.getJSONObject("commander").getString("strategy"));
    }

    @Test
    void assaultDefenderDumpPublishesOnlyDefenderAreaBeliefs()
            throws Exception {
        BattleSimulation sim = openSim();
        for (int y = 0; y < sim.getGrid().getHeight(); y++) {
            sim.getGrid().setWalkable(16, y, false);
        }
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        Squad squad = sim.getSquad(squadId);
        long member = sim.spawn(new EntitySpec("security", Faction.DEFENDER,
                UnitType.MILITIA, 3, 12).squad(squadId).role(UnitRole.PATROL));
        squad.leaderId = member;
        sim.spawn(new EntitySpec("hidden-marine", Faction.MARINE,
                UnitType.MARINE, 25, 12).moveSpeed(0f));
        sim.setAutonomousCommander(Faction.DEFENDER,
                new AssaultDefenderCommand(java.util.Set.of(squadId)),
                AssaultDefenderCommandDisclosure.INSTANCE);
        sim.advance(CommanderService.COMMANDER_TICK_PERIOD
                + BattleSimulation.TICK_DT);

        JSONObject dump = SquadStateDumper.buildSquadJson(squad, sim);
        JSONObject defense = dump.getJSONObject("assaultDefenseCommand");
        JSONObject directive = defense.getJSONObject("squadDirective");
        JSONObject area = defense.getJSONArray("areas").getJSONObject(0);
        assertEquals("DEFENDER", defense.getString("perspective"));
        assertEquals("AREA_SECURITY", defense.getString("phase"));
        assertEquals("QUIET", area.getString("reportState"));
        assertEquals("ROUTINE_SECURITY", directive.getString("role"));
        assertTrue(defense.has("strongpoints"));
        assertTrue(dump.isNull("assaultCommand"));
        assertFalse(area.has("hostileCellX"));
        assertFalse(area.has("hostileCellY"));
    }

    @Test
    void extractionDumpPublishesPayloadRoleReasonAndTarget()
            throws Exception {
        BattleSimulation sim = openSim();
        int zone = sim.getZoneGraph().zoneIdAt(24, 12);
        sim.addObjective(new ExtractionObjective("EXTRACTION-01", "package",
                zone, new int[]{24, 12, 23, 12, 22, 12, 21, 12,
                20, 12, 19, 12, 18, 12, 17, 12, 16, 12, 15, 12,
                14, 12, 13, 12, 12, 12, 11, 12, 10, 12, 9, 12,
                8, 12, 7, 12, 6, 12, 5, 12, 4, 12, 3, 12}));
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long member = sim.spawn(new EntitySpec("escort", Faction.MARINE,
                UnitType.MARINE, 3, 12).squad(squadId));
        squad.leaderId = member;
        sim.setAutonomousCommander(Faction.MARINE,
                new ExtractionCommand(), ExtractionCommandDisclosure.INSTANCE);
        sim.advance(CommanderService.COMMANDER_TICK_PERIOD
                + BattleSimulation.TICK_DT);

        JSONObject dump = SquadStateDumper.buildSquadJson(squad, sim);
        JSONObject extraction = dump.getJSONObject("extractionCommand");
        JSONObject intent = extraction.getJSONObject("squadIntent");

        assertEquals("extraction-attacker",
                dump.getJSONObject("commander").getString("strategy"));
        assertEquals("AT_SOURCE", extraction.getString("phase"));
        assertEquals("EXTRACTION-01", extraction.getString("payloadId"));
        assertEquals("PAYLOAD_ELEMENT", intent.getString("role"));
        assertEquals("PACKAGE_PICKUP", intent.getString("reason"));
        assertEquals("ESCORT", intent.getString("assignmentKind"));
        assertEquals(24, intent.getInt("targetCellX"));
        assertFalse(intent.getBoolean("localContact"));
    }

    @Test
    void extractionDefenseDumpPublishesAlarmPictureWithoutHiddenCorridor()
            throws Exception {
        BattleSimulation sim = openSim();
        int zone = sim.getZoneGraph().zoneIdAt(24, 12);
        sim.addObjective(new ExtractionObjective("EXTRACTION-01", "package",
                zone, new int[]{24, 12, 23, 12, 22, 12, 21, 12,
                20, 12, 19, 12, 18, 12, 17, 12, 16, 12, 15, 12,
                14, 12, 13, 12, 12, 12, 11, 12, 10, 12, 9, 12,
                8, 12, 7, 12, 6, 12, 5, 12, 4, 12, 3, 12}));
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        Squad squad = sim.getSquad(squadId);
        long member = sim.spawn(new EntitySpec("guard", Faction.DEFENDER,
                UnitType.MILITIA, 20, 12).squad(squadId));
        squad.leaderId = member;
        sim.setAutonomousCommander(Faction.DEFENDER,
                new ExtractionDefenderCommand(java.util.Set.of(squadId)),
                ExtractionDefenderCommandDisclosure.INSTANCE);
        sim.advance(CommanderService.COMMANDER_TICK_PERIOD
                + BattleSimulation.TICK_DT);

        JSONObject dump = SquadStateDumper.buildSquadJson(squad, sim);
        JSONObject defense = dump.getJSONObject("extractionDefenseCommand");
        JSONObject intent = defense.getJSONObject("squadIntent");

        assertEquals("extraction-defender",
                dump.getJSONObject("commander").getString("strategy"));
        assertEquals("ROUTINE_SECURITY", defense.getString("phase"));
        assertEquals("SOURCE_GUARD", intent.getString("role"));
        assertTrue(dump.isNull("extractionCommand"));
        assertFalse(defense.has("egressCellX"));
        assertFalse(defense.has("payloadCellX"));
        assertFalse(defense.has("progress"));
        assertFalse(defense.has("controllingSquadId"));
    }

    @Test
    void rescueDumpPublishesMarineCorridorAndSwarmDirector() throws Exception {
        BattleSimulation sim = openSim(40, 30);
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(new PointOfInterest(
                        PointOfInterest.Kind.RESIDENTIAL,
                        9, 5, 13, 9, 8, 7, 11, 7)), 81L);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long member = sim.spawn(new EntitySpec("escort", Faction.MARINE,
                UnitType.MARINE, 3, 12).squad(squadId));
        squad.leaderId = member;
        sim.spawn(new EntitySpec("runner", Faction.DEFENDER,
                UnitType.SWARM_RUNNER, 39, 29)
                .role(UnitRole.SWARM_PRESSURE));
        assertTrue(sim.configureSwarmReinforcements(
                payload.placement, 1, 81L));
        sim.advance(CommanderService.COMMANDER_TICK_PERIOD
                + BattleSimulation.TICK_DT);

        JSONObject dump = SquadStateDumper.buildSquadJson(squad, sim);
        JSONObject rescue = dump.getJSONObject("rescueCommand");
        JSONObject intent = rescue.getJSONObject("squadIntent");
        JSONObject director = dump.getJSONObject("swarmPressureDirector");

        assertEquals("rescue-corridor",
                dump.getJSONObject("commander").getString("strategy"));
        assertEquals("AT_SOURCE", rescue.getString("phase"));
        assertEquals("COHORT_ESCORT", intent.getString("role"));
        assertEquals("SHELTER_RELIEF", intent.getString("reason"));
        assertEquals("SWARM", director.getString("perspective"));
        assertEquals("rescue-swarm-pressure",
                director.getString("director"));
        assertEquals(4, director.getJSONArray("approaches").length());
        assertTrue(dump.isNull("extractionCommand"));
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
