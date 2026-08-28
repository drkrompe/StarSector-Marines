package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot;
import com.dillon.starsectormarines.battle.command.AssaultSearchSnapshot;
import com.dillon.starsectormarines.battle.command.AssaultDefenseSnapshot;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.SabotageSiteSnapshot;
import com.dillon.starsectormarines.battle.command.SabotageDefenseSnapshot;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.AssignmentReason;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.Phase;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.SquadDirective;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.TrackState;
import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.combat.FiringSystem;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.ContactInitiative;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Doctrine;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.ForceBalance;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Motion;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Posture;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Sector;
import com.dillon.starsectormarines.battle.ui.highlight.CellHighlight;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadPlanDebugPanelTest {

    @Test
    void summariesExposeThePublishedContactDecision() {
        SquadContactPicture picture = picture(1f, 0f, Doctrine.DISENGAGE);

        assertEquals("Doctrine DISENGAGE   Posture ADVANCING   Odds UNFAVORABLE",
                SquadPlanDebugPanel.doctrineSummary(picture));
        assertEquals("Threat LEFT_FLANK   Motion APPROACHING   Seen D2/T3",
                SquadPlanDebugPanel.threatSummary(picture));
        assertEquals("Force H5.50/F3   Axis +1.00,+0.00",
                SquadPlanDebugPanel.forceSummary(picture));
        assertEquals("Initiative NONE   Line M2/3 T2/3",
                SquadPlanDebugPanel.initiativeSummary(picture));
        assertEquals("Primary Raider @18,12   Confidence 0.75",
                SquadPlanDebugPanel.primarySummary(picture, "Raider"));
        assertEquals("Primary —", SquadPlanDebugPanel.primarySummary(
                SquadContactPicture.NONE, "—"));
    }

    @Test
    void tacticalAxisIsBoundedNormalizedAndDoctrineColored() {
        Squad squad = new Squad(7, Faction.MARINE);
        squad.centroidX = 10.5f;
        squad.centroidY = 10.5f;
        squad.contactPicture = picture(2f, 0f, Doctrine.HOLD);

        List<CellHighlight> cells = SquadPlanDebugPanel.doctrineAxisCells(squad);

        assertEquals(SquadPlanDebugPanel.DOCTRINE_AXIS_TRACE_CELLS, cells.size());
        for (int i = 0; i < cells.size(); i++) {
            CellHighlight cell = cells.get(i);
            assertEquals(11 + i, cell.cellX);
            assertEquals(10, cell.cellY);
            assertEquals(HighlightOverlay.COLOR_DOCTRINE_HOLD, cell.color);
        }
    }

    @Test
    void selectedSquadShowsWhetherDoctrineHoldCanStillStopMovement() {
        BattleSimulation sim = openSim();
        Squad squad = new Squad(9, Faction.MARINE);
        squad.contactPicture = picture(1f, 0f, Doctrine.HOLD);

        assertEquals("Hold stop ACTIVE   Evidence 0t/30t",
                SquadPlanDebugPanel.holdReactionSummary(squad, sim));
    }

    @Test
    void tacticalAxisIsAbsentWhenThePictureHasNoDirection() {
        Squad squad = new Squad(8, Faction.MARINE);
        squad.contactPicture = SquadContactPicture.NONE;

        assertTrue(SquadPlanDebugPanel.doctrineAxisCells(squad).isEmpty());
    }

    @Test
    void selectedFireSummaryExposesRegistrationCooldownAndLastGate() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long member = sim.spawn(new EntitySpec("Marine", Faction.MARINE,
                UnitType.MARINE, 5, 5).squad(squadId));
        long enemy = sim.spawn(new EntitySpec("Raider", Faction.DEFENDER,
                UnitType.MARINE, 8, 5));
        sim.world().setAttackRange(member, 10f);
        sim.world().setCooldownTimer(member, 0.5f);
        sim.combat().setFireIntent(member, enemy, FireStance.STANCED, false);
        new FiringSystem(sim.getGrid(), sim.getRoster()).tick(sim);

        assertEquals("Fire Ready 0   Reg 1   CD 1   Last REGISTERING 0t",
                SquadPlanDebugPanel.fireSummary(squad, sim));
    }

    @Test
    void selectedSquadSummariesExplainConquestTrackSupport() {
        TrackState track = new TrackState(1, 10, 20, 1, 2, 8,
                0.72f, 0.81f, 0.68f, 3, 5.4f, 4.2f, 17);
        SquadDirective directive = new SquadDirective(9, 2, 1,
                AssignmentReason.ADJACENT_TRACK_SUPPORT,
                AssignmentKind.CLEAR_ZONE, 17, 14, 31);
        ConquestFrontSnapshot snapshot = new ConquestFrontSnapshot(44, 42,
                TraversalAxis.SOUTH_TO_NORTH, Phase.FRONT_ADJUST, 2,
                25, CompoundState.DEFENDER_HELD, List.of(track),
                List.of(directive));
        CommandDirective committed = new CommandDirective(9, Faction.MARINE,
                "conquest-attacker", CommandAuthority.MISSION_COMMAND,
                "ADJACENT_TRACK_SUPPORT", ObjectiveAssignment.clearZone(9, 17),
                44, -1, CommandDirective.Status.ACTIVE, "");
        CommanderSnapshot<ConquestFrontSnapshot> commander =
                new CommanderSnapshot<>(Faction.MARINE, "conquest-attacker",
                        Phase.FRONT_ADJUST.name(), 44, 42, 3, 0,
                        List.of("remaining compounds=2"), List.of(committed),
                        snapshot);

        assertEquals("Command MARINE conquest-attacker   Phase FRONT_ADJUST",
                SquadPlanDebugPanel.commandSummary(commander));
        assertEquals("Directive ACTIVE   Authority MISSION_COMMAND",
                SquadPlanDebugPanel.directiveSummary(committed));
        assertEquals("Issuer conquest-attacker   Reason ADJACENT_TRACK_SUPPORT",
                SquadPlanDebugPanel.provenanceSummary(committed));
        assertEquals("Issued 44   Stable —   Lease —   Disposition —",
                SquadPlanDebugPanel.stabilitySummary(committed));
        assertEquals("Track P2→E1   Front F0.72/H0.68   Press 5.4/4.2",
                SquadPlanDebugPanel.trackSummary(snapshot, directive));
        assertEquals("Commander order CLEAR_ZONE   Target cell 14,31",
                SquadPlanDebugPanel.conquestOrderSummary(directive));
        assertEquals("Command reason ADJACENT_TRACK_SUPPORT",
                SquadPlanDebugPanel.conquestReasonSummary(directive));
        assertEquals("Command activity ACTIVE_PATH   Moving 3/8"
                        + "   At portal 0   In target 0",
                SquadPlanDebugPanel.conquestExecutionSummary(
                        new ConquestFrontSnapshot.SquadState(
                                9, 8, 1f, 2f, 3, null, false, 3)));
        assertEquals("Command reason ADJACENT_TRACK_SUPPORT"
                        + "   Capture DEFERRED_FOR_FRONT_RESISTANCE",
                SquadPlanDebugPanel.conquestReasonSummary(
                        directive.withDistantCaptureDeferred()));
    }

    @Test
    void selectedSquadSummariesExplainSabotageSiteGroup() {
        SabotageSiteSnapshot.SiteState site =
                new SabotageSiteSnapshot.SiteState(0, "SAB-01", "reactor",
                        12, 7, 3, 2f, 8f, true, false,
                        0, 0, SabotageSiteSnapshot.GroupReason.PLANTER_ACTIVE,
                        1, 0, 2, 12, 4f, 3f);
        SabotageSiteSnapshot.SquadDirective directive =
                new SabotageSiteSnapshot.SquadDirective(9, 0,
                        SabotageSiteSnapshot.GroupRole.SECURITY,
                        SabotageSiteSnapshot.AssignmentReason.SITE_SECURITY_PRESERVED,
                        AssignmentKind.CLEAR_ZONE, 3, 12, 7);
        SabotageSiteSnapshot snapshot = new SabotageSiteSnapshot(44, 42,
                Faction.MARINE, SabotageSiteSnapshot.Phase.PLANTING,
                List.of(site), List.of(), List.of(directive));

        assertEquals("Site group S1   SECURITY   Reason SITE_SECURITY_PRESERVED",
                SquadPlanDebugPanel.sabotageOrderSummary(directive));
        assertEquals("Site SAB-01 2.0/8.0   PLANTER_ACTIVE   Security 2   Press 4.0/3.0",
                SquadPlanDebugPanel.sabotageSiteSummary(snapshot, directive));
    }

    @Test
    void selectedSquadSummariesExplainAssaultSearchCoverage() {
        AssaultSearchSnapshot.SectorState sector =
                new AssaultSearchSnapshot.SectorState(2, 20, 10, 10, 8,
                        AssaultSearchSnapshot.SectorStatus.SUSPECTED,
                        3, 9, 1, 40, 2, 24, 13);
        AssaultSearchSnapshot.SquadDirective directive =
                new AssaultSearchSnapshot.SquadDirective(9, 2,
                        AssaultSearchSnapshot.AssignmentReason
                                .SUSPECTED_CONTACT_REINFORCEMENT,
                        AssignmentKind.SWEEP_SECTOR, 24, 13);
        AssaultSearchSnapshot snapshot = new AssaultSearchSnapshot(44, 42,
                Faction.MARINE, AssaultSearchSnapshot.Phase.CONVERGE, 1,
                List.of(sector), List.of(), List.of(directive));

        assertEquals("Search order SWEEP_SECTOR   Sector S3   Target 24,13"
                        + "   Reason SUSPECTED_CONTACT_REINFORCEMENT",
                SquadPlanDebugPanel.assaultOrderSummary(directive));
        assertEquals("Sector SUSPECTED   Coverage 3/9   Contacts 1   Squads 2",
                SquadPlanDebugPanel.assaultSectorSummary(snapshot, directive));
    }

    @Test
    void selectedDefenderSummaryExplainsAreaReportAndReserveResponse() {
        AssaultDefenseSnapshot.AreaState area =
                new AssaultDefenseSnapshot.AreaState(1, 15, 0, 15, 15,
                        80, 1, 1, 1, 1,
                        AssaultDefenseSnapshot.ReportState.SUSPECTED,
                        2, 40, 490, 4f, 3f, 20, 7);
        AssaultDefenseSnapshot.SquadDirective directive =
                new AssaultDefenseSnapshot.SquadDirective(9, 1,
                        AssaultDefenseSnapshot.Role.RESPONDER,
                        AssaultDefenseSnapshot.Reason
                                .SUSPECTED_CONTACT_RESPONSE,
                        AssignmentKind.DEFEND_AREA, 20, 7);
        AssaultDefenseSnapshot snapshot = new AssaultDefenseSnapshot(
                44, 42, Faction.DEFENDER,
                AssaultDefenseSnapshot.Phase.REPORTED_CONTACT_RESPONSE,
                4, 1, List.of(area), List.of(), List.of(),
                List.of(directive));

        assertEquals("Defense area A2   DEFEND_AREA   Role RESPONDER"
                        + "   Target 20,7   Reason SUSPECTED_CONTACT_RESPONSE",
                SquadPlanDebugPanel.assaultDefenseOrderSummary(directive));
        assertEquals("Report SUSPECTED 2 contacts exp 490   Cover 1+1"
                        + "   Press 4.0/3.0",
                SquadPlanDebugPanel.assaultDefenseAreaSummary(
                        snapshot, directive));
    }

    @Test
    void selectedDefenderSummaryDoesNotInventAreaZeroForUnreachableOrder() {
        AssaultDefenseSnapshot.SquadDirective directive =
                new AssaultDefenseSnapshot.SquadDirective(9, -1,
                        AssaultDefenseSnapshot.Role.RESERVE,
                        AssaultDefenseSnapshot.Reason.NO_REACHABLE_AREA,
                        null, -1, -1);

        assertEquals("Defense area —   UNASSIGNED   Role RESERVE"
                        + "   Target —   Reason NO_REACHABLE_AREA",
                SquadPlanDebugPanel.assaultDefenseOrderSummary(directive));
    }

    @Test
    void selectedDefenderSummaryShowsAlarmCoverageWithoutPlantProgress() {
        SabotageDefenseSnapshot.SiteState site =
                new SabotageDefenseSnapshot.SiteState(0, "SAB-01", "reactor",
                        12, 7, 3, false, true, 40, 490,
                        1, 1, 10, 4f, 3f);
        SabotageDefenseSnapshot.SquadDirective directive =
                new SabotageDefenseSnapshot.SquadDirective(9, 0,
                        SabotageDefenseSnapshot.Role.ALARM_RESPONDER,
                        SabotageDefenseSnapshot.Reason.SITE_ALARM_RESPONSE,
                        AssignmentKind.DEFEND_SITE, 15, 7);
        SabotageDefenseSnapshot snapshot = new SabotageDefenseSnapshot(
                44, 42, Faction.DEFENDER,
                SabotageDefenseSnapshot.Phase.ALARM_RESPONSE,
                4, 0, List.of(site), List.of(directive));

        assertEquals("Defense site S1   ALARM_RESPONDER   Reason SITE_ALARM_RESPONSE",
                SquadPlanDebugPanel.sabotageDefenseOrderSummary(directive));
        String summary = SquadPlanDebugPanel.sabotageDefenseSiteSummary(
                snapshot, directive);
        assertEquals("Site SAB-01   Alarm ACTIVE   Cover 1+1   Press 4.0/3.0",
                summary);
        assertTrue(!summary.contains("/8.0"));
    }

    @Test
    void selectedSquadSeparatesAuthoritativeOrderFromFormUpExecution() {
        Squad squad = new Squad(9, Faction.MARINE);
        squad.campaignSquadId = "campaign-1";
        squad.expectedSize = 12;
        squad.originalSize = 4;
        squad.assignedObjective = ObjectiveAssignment.escort(squad.id, 8, 8);

        assertEquals("Execution SUSPENDED   Reason FORMING_UP",
                SquadPlanDebugPanel.executionSummary(squad));

        squad.originalSize = 12;
        assertEquals("Execution READY",
                SquadPlanDebugPanel.executionSummary(squad));
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(20, 12);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid,
                new CellTopology(grid.getWidth(), grid.getHeight()));
    }

    private static SquadContactPicture picture(float axisX, float axisY,
                                               Doctrine doctrine) {
        return new SquadContactPicture(42, Posture.ADVANCING, axisX, axisY,
                3, 2, 5.5f, 3, ForceBalance.UNFAVORABLE,
                Sector.LEFT_FLANK, Motion.APPROACHING, 99L,
                18, 12, 0.75f, doctrine, 2, 3, 2, 3,
                doctrine == Doctrine.HOLD ? ContactInitiative.RECEIVE
                        : ContactInitiative.NONE);
    }
}
