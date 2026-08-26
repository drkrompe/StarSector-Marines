package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.infantry.EquipmentDrop;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link SabotageCommand}'s assignment policy. The map is a
 * trivial open grid — no walls/doorways needed because the commander only
 * picks the closest unfinished site by squad-centroid distance and reads
 * the zone id at the site cell.
 */
public class SabotageCommandTest {

    private static final int W = 20;
    private static final int H = 10;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        for (int[] cell : new int[][]{{4, 2}, {4, 4}, {10, 4}, {18, 4}, {18, 7}}) {
            grid.setDoorway(cell[0], cell[1], true);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /**
     * Mint a squad through the public sim API so it shows up in
     * {@code sim.getSquads()} (the commander reads from there). Centroid +
     * {@code aliveMembers} are normally refreshed by
     * {@code BattleSimulation.updateSquadAlertLevels} on the regular tick
     * path; we set them by hand here since the test calls
     * {@code SabotageCommand.tick} directly without driving a sim tick.
     */
    private static Squad addSquad(BattleSimulation sim, float centroidX, float centroidY) {
        long leader = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE,
                Math.round(centroidX), Math.round(centroidY)));
        int sid = sim.mintSquad(Faction.MARINE, leader);
        sim.squad().assignSquad(leader, sid);
        Squad squad = sim.getSquad(sid);
        squad.aliveMembers = 1;
        squad.centroidX = centroidX;
        squad.centroidY = centroidY;
        return squad;
    }

    @Test
    public void assignsNonPlanterSquadToNearestUnfinishedSite() {
        BattleSimulation sim = openSim();
        ChargeSiteObjective near = new ChargeSiteObjective(4, 4, 5f, "near");
        ChargeSiteObjective far  = new ChargeSiteObjective(18, 4, 5f, "far");
        sim.addObjective(near);
        sim.addObjective(far);

        Squad squad = addSquad(sim, 2f, 4f);

        tick(new SabotageCommand(), sim);

        ObjectiveAssignment a = squad.assignedObjective;
        assertNotNull(a, "non-planter squad should receive an assignment");
        assertEquals(AssignmentKind.CLEAR_ZONE, a.kind());
        int expectedZone = sim.getZoneGraph().zoneIdAt(4, 4);
        assertEquals(expectedZone, a.targetZoneId(),
                "should be routed to the near charge site, not the far one");
    }

    @Test
    public void planterEquippedSquadIsNotOverridden() {
        BattleSimulation sim = openSim();
        ChargeSiteObjective site = new ChargeSiteObjective(10, 4, 5f, "site");
        sim.addObjective(site);

        Squad squad = addSquad(sim, 2f, 4f);
        long planter = sim.spawn(new EntitySpec("p1", Faction.MARINE, UnitType.MARINE, 2, 4)
                .squad(squad.id)
                .role(UnitRole.PLANTER)
                .assignedObjective(site));

        tick(new SabotageCommand(), sim);

        assertNull(squad.assignedObjective,
                "squad with a live planter on an unfinished site should be left for SecureObjectiveZone");
    }

    @Test
    public void completedSitesAreSkippedWhenChoosingTarget() {
        BattleSimulation sim = openSim();
        // The near site is "complete" — commander should pick the far one instead.
        ChargeSiteObjective near = completedSite(sim, 4, 4, "near");
        ChargeSiteObjective far  = new ChargeSiteObjective(18, 4, 5f, "far");
        sim.addObjective(near);
        sim.addObjective(far);

        Squad squad = addSquad(sim, 2f, 4f);

        tick(new SabotageCommand(), sim);

        ObjectiveAssignment a = squad.assignedObjective;
        assertNotNull(a);
        int farZone = sim.getZoneGraph().zoneIdAt(18, 4);
        assertEquals(farZone, a.targetZoneId(), "completed site should be ignored");
    }

    @Test
    public void allSitesCompleteClearsAssignments() {
        BattleSimulation sim = openSim();
        ChargeSiteObjective only = completedSite(sim, 4, 4, "only");
        sim.addObjective(only);

        Squad squad = addSquad(sim, 2f, 4f);
        squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id, 0);

        tick(new SabotageCommand(), sim);

        assertNull(squad.assignedObjective,
                "no unfinished sites → commander clears stale assignments");
    }

    @Test
    public void idempotentReassignmentDoesNotChurnRecord() {
        BattleSimulation sim = openSim();
        ChargeSiteObjective site = new ChargeSiteObjective(4, 4, 5f, "site");
        sim.addObjective(site);
        Squad squad = addSquad(sim, 2f, 4f);

        SabotageCommand cmd = new SabotageCommand();
        tick(cmd, sim);
        ObjectiveAssignment first = squad.assignedObjective;
        tick(cmd, sim);
        ObjectiveAssignment second = squad.assignedObjective;

        assertNotNull(first);
        // Same record instance — commander short-circuits when the chosen
        // zone matches the existing assignment, so an in-flight squad plan
        // doesn't get invalidated by re-allocation churn.
        assertEquals(first, second,
                "stable inputs should produce the same assignment record across ticks");
    }

    @Test
    public void spreadsInitialSecurityAcrossEveryReachableSite() {
        BattleSimulation sim = openSim();
        sim.addObjective(new ChargeSiteObjective(4, 2, 5f, "SAB-01", "one"));
        sim.addObjective(new ChargeSiteObjective(10, 4, 5f, "SAB-02", "two"));
        sim.addObjective(new ChargeSiteObjective(18, 7, 5f, "SAB-03", "three"));
        Squad first = addSquad(sim, 1f, 2f);
        Squad second = addSquad(sim, 1f, 4f);
        Squad third = addSquad(sim, 1f, 7f);

        SabotageCommand command = new SabotageCommand();
        tick(command, sim);

        assertEquals(3, java.util.Set.of(
                command.siteSnapshot().directiveFor(first.id).siteIndex(),
                command.siteSnapshot().directiveFor(second.id).siteIndex(),
                command.siteSnapshot().directiveFor(third.id).siteIndex()).size(),
                "coverage should reach all three sites before surplus stacks");
        assertEquals(java.util.List.of("SAB-01", "SAB-02", "SAB-03"),
                command.siteSnapshot().sites().stream()
                        .map(SabotageSiteSnapshot.SiteState::id).toList());
    }

    @Test
    public void mixedTaskGroupsCoverEverySiteBeforeSurplusConcentrates() {
        BattleSimulation sim = openSim();
        List<ChargeSiteObjective> sites = List.of(
                new ChargeSiteObjective(4, 2, 5f, "SAB-01", "one"),
                new ChargeSiteObjective(10, 4, 5f, "SAB-02", "two"),
                new ChargeSiteObjective(18, 7, 5f, "SAB-03", "three"));
        sites.forEach(sim::addObjective);
        for (int i = 0; i < sites.size(); i++) {
            Squad squad = addSquad(sim, 1f, 2f + i * 2f);
            sim.spawn(new EntitySpec("planter-" + i, Faction.MARINE,
                    UnitType.MARINE, 1, 2 + i * 2)
                    .squad(squad.id).role(UnitRole.PLANTER)
                    .assignedObjective(sites.get(i)));
        }
        Squad firstSecurity = addSquad(sim, 2f, 2f);
        Squad secondSecurity = addSquad(sim, 2f, 4f);
        Squad thirdSecurity = addSquad(sim, 2f, 7f);
        Squad surplus = addSquad(sim, 2f, 5f);

        SabotageCommand command = new SabotageCommand();
        tick(command, sim);

        assertEquals(3, Set.of(
                command.siteSnapshot().directiveFor(firstSecurity.id).siteIndex(),
                command.siteSnapshot().directiveFor(secondSecurity.id).siteIndex(),
                command.siteSnapshot().directiveFor(thirdSecurity.id).siteIndex())
                .size(), "dedicated security should cover each site first");
        assertEquals(4, command.siteSnapshot().sites().stream()
                .mapToInt(SabotageSiteSnapshot.SiteState::securitySquads).sum());
        assertEquals(1, command.siteSnapshot().sites().stream()
                .filter(site -> site.securitySquads() == 2).count(),
                "only the surplus squad should reinforce an already-covered site");
        assertEquals(SabotageSiteSnapshot.AssignmentReason
                        .SITE_REINFORCEMENT_ASSIGNED,
                command.siteSnapshot().directiveFor(surplus.id).reason());

        Map<Integer, Integer> firstAffinities = command.siteSnapshot().directives()
                .stream().collect(Collectors.toMap(
                        SabotageSiteSnapshot.SquadDirective::squadId,
                        SabotageSiteSnapshot.SquadDirective::siteIndex));
        tick(command, sim);
        assertEquals(firstAffinities, command.siteSnapshot().directives().stream()
                .collect(Collectors.toMap(
                        SabotageSiteSnapshot.SquadDirective::squadId,
                        SabotageSiteSnapshot.SquadDirective::siteIndex)));
    }

    @Test
    public void planterPublishesSiteContextWithoutCompetingSquadOrder() {
        BattleSimulation sim = openSim();
        ChargeSiteObjective site = new ChargeSiteObjective(
                10, 4, 5f, "SAB-01", "site");
        sim.addObjective(site);
        Squad squad = addSquad(sim, 2f, 4f);
        sim.spawn(new EntitySpec("planter", Faction.MARINE, UnitType.MARINE, 2, 4)
                .squad(squad.id).role(UnitRole.PLANTER).assignedObjective(site));

        SabotageCommand command = new SabotageCommand();
        tick(command, sim);

        SabotageSiteSnapshot.SquadDirective directive =
                command.siteSnapshot().directiveFor(squad.id);
        assertNotNull(directive);
        assertEquals(0, directive.siteIndex());
        assertEquals(SabotageSiteSnapshot.AssignmentReason.PLANTER_OBJECTIVE_PRESERVED,
                directive.reason());
        assertNull(directive.assignmentKind());
        assertNull(squad.assignedObjective);
        assertTrue(command.siteSnapshot().sites().get(0).planterSquads() > 0);
    }

    @Test
    public void kitRetrieverStaysAffiliatedWithItsChargeSite() {
        BattleSimulation sim = openSim();
        ChargeSiteObjective site = new ChargeSiteObjective(
                10, 4, 5f, "SAB-01", "site");
        sim.addObjective(site);
        Squad squad = addSquad(sim, 2f, 4f);
        long retriever = sim.spawn(new EntitySpec("retriever", Faction.MARINE,
                UnitType.MARINE, 2, 4).squad(squad.id)
                .role(UnitRole.KIT_RETRIEVER));
        sim.task().setEquipmentDropTarget(retriever,
                new EquipmentDrop(5, 4, site));

        SabotageCommand command = new SabotageCommand();
        tick(command, sim);

        SabotageSiteSnapshot.SquadDirective directive =
                command.siteSnapshot().directiveFor(squad.id);
        assertEquals(0, directive.siteIndex());
        assertEquals(SabotageSiteSnapshot.AssignmentReason.KIT_RECOVERY_PRESERVED,
                directive.reason());
        assertNull(directive.assignmentKind());
        assertEquals(SabotageSiteSnapshot.Phase.KIT_RECOVERY,
                command.siteSnapshot().phase());
    }

    @Test
    public void completingStickySiteReleasesStabilityAndRedistributesSecurity() {
        BattleSimulation sim = openSim();
        ChargeSiteObjective first = new ChargeSiteObjective(
                4, 4, 0.0001f, "SAB-01", "first");
        ChargeSiteObjective second = new ChargeSiteObjective(
                18, 4, 5f, "SAB-02", "second");
        sim.addObjective(first);
        sim.addObjective(second);
        Squad squad = addSquad(sim, 2f, 4f);
        SabotageCommand command = new SabotageCommand();
        tick(command, sim);
        assertEquals(0, command.siteSnapshot().directiveFor(squad.id).siteIndex());

        sim.spawn(new EntitySpec("finisher", Faction.MARINE, UnitType.MARINE, 4, 4)
                .role(UnitRole.PLANTER).assignedObjective(first));
        first.tick(sim);
        tick(command, sim);

        assertEquals(1, command.siteSnapshot().directiveFor(squad.id).siteIndex());
        assertEquals(sim.getZoneGraph().zoneIdAt(18, 4),
                squad.assignedObjective.targetZoneId());
    }

    @Test
    public void strongerExternalAssignmentIsPreserved() {
        BattleSimulation sim = openSim();
        sim.addObjective(new ChargeSiteObjective(10, 4, 5f, "SAB-01", "site"));
        Squad squad = addSquad(sim, 2f, 4f);
        ObjectiveAssignment payload = ObjectiveAssignment.escort(squad.id, 3, 3);
        sim.assignSquadCommand(payload, CommandAuthority.PAYLOAD,
                "payload", "escort payload");
        SabotageCommand command = new SabotageCommand();

        tick(command, sim);

        assertEquals(payload, squad.assignedObjective);
        assertEquals(SabotageSiteSnapshot.AssignmentReason.EXTERNAL_OWNERSHIP_PRESERVED,
                command.siteSnapshot().directiveFor(squad.id).reason());
    }

    @Test
    public void kitRecruitmentDoesNotCreateTwoSpecialSiteDutiesInOneSquad() {
        BattleSimulation sim = openSim();
        ChargeSiteObjective first = new ChargeSiteObjective(
                4, 4, 5f, "SAB-01", "first");
        ChargeSiteObjective second = new ChargeSiteObjective(
                18, 4, 5f, "SAB-02", "second");
        sim.addObjective(first);
        sim.addObjective(second);
        Squad planterSquad = addSquad(sim, 2f, 4f);
        sim.spawn(new EntitySpec("planter", Faction.MARINE, UnitType.MARINE, 2, 4)
                .squad(planterSquad.id).role(UnitRole.PLANTER)
                .assignedObjective(first));
        Squad freeSquad = addSquad(sim, 15f, 4f);
        EquipmentDrop drop = new EquipmentDrop(2, 4, second);
        sim.getEquipmentDrops().add(drop);

        sim.advance(BattleSimulation.TICK_DT);

        long planterLeader = sim.resolveUnit(planterSquad.leaderId);
        long freeLeader = sim.resolveUnit(freeSquad.leaderId);
        assertEquals(UnitRole.COMBATANT, sim.role().role(planterLeader));
        assertEquals(UnitRole.KIT_RETRIEVER, sim.role().role(freeLeader));
        assertEquals(drop, sim.task().equipmentDropTarget(freeLeader));
    }

    @Test
    public void unclaimedKitPublishesUnsupportedRecoveryReason() {
        BattleSimulation sim = openSim();
        ChargeSiteObjective unsupported = new ChargeSiteObjective(
                10, 4, 5f, "SAB-01", "unsupported");
        ChargeSiteObjective occupied = new ChargeSiteObjective(
                18, 7, 5f, "SAB-02", "occupied");
        sim.addObjective(unsupported);
        sim.addObjective(occupied);
        Squad busySquad = addSquad(sim, 2f, 4f);
        sim.spawn(new EntitySpec("busy-planter", Faction.MARINE,
                UnitType.MARINE, 2, 4).squad(busySquad.id)
                .role(UnitRole.PLANTER).assignedObjective(occupied));
        EquipmentDrop drop = new EquipmentDrop(5, 4, unsupported);
        sim.getEquipmentDrops().add(drop);

        sim.advance(BattleSimulation.TICK_DT);

        assertFalse(drop.consumed);
        SabotageCommand command = new SabotageCommand();
        tick(command, sim);
        SabotageSiteSnapshot.SiteState site = command.siteSnapshot().site(0);
        assertEquals(1, site.activeKitDrops());
        assertEquals(1, site.unclaimedKitDrops());
        assertEquals(SabotageSiteSnapshot.GroupReason.KIT_RECOVERY_UNSUPPORTED,
                site.groupReason());
        assertEquals(SabotageSiteSnapshot.Phase.KIT_RECOVERY,
                command.siteSnapshot().phase());
    }

    @Test
    public void planterAndRetrieverSquadsClaimAssignmentlessCommandOwnership() {
        BattleSimulation sim = openSim();
        ChargeSiteObjective site = new ChargeSiteObjective(
                10, 4, 5f, "SAB-01", "site");
        sim.addObjective(site);
        Squad planterSquad = addSquad(sim, 2f, 4f);
        sim.spawn(new EntitySpec("planter", Faction.MARINE, UnitType.MARINE, 2, 4)
                .squad(planterSquad.id).role(UnitRole.PLANTER)
                .assignedObjective(site));
        Squad retrieverSquad = addSquad(sim, 2f, 7f);
        long retriever = sim.resolveUnit(retrieverSquad.leaderId);
        sim.role().setRole(retriever, UnitRole.KIT_RETRIEVER);
        sim.task().setEquipmentDropTarget(retriever,
                new EquipmentDrop(5, 7, site));
        sim.setAutonomousCommander(Faction.MARINE, new SabotageCommand(),
                SabotageCommandDisclosure.INSTANCE);

        int ticks = (int) Math.ceil(CommanderService.COMMANDER_TICK_PERIOD
                / BattleSimulation.TICK_DT) + 1;
        for (int i = 0; i < ticks; i++) sim.advance(BattleSimulation.TICK_DT);

        assertAssignmentlessSabotageOwnership(sim, planterSquad);
        assertAssignmentlessSabotageOwnership(sim, retrieverSquad);
    }

    private static void assertAssignmentlessSabotageOwnership(
            BattleSimulation sim, Squad squad) {
        CommandDirective directive = sim.getSquadCommandDirective(squad.id);
        assertNotNull(directive);
        assertEquals("sabotage-attacker", directive.issuer());
        assertEquals(CommandAuthority.MISSION_COMMAND, directive.authority());
        assertEquals(CommandDirective.Status.ACTIVE, directive.status());
        assertNull(directive.assignment());
        assertNull(squad.assignedObjective);
    }

    private static ChargeSiteObjective completedSite(BattleSimulation sim, int x, int y, String name) {
        ChargeSiteObjective cs = new ChargeSiteObjective(x, y, 0.0001f, name);
        // Plant a planter on top + tick once so isComplete() flips true
        // without needing the rest of the sim to be set up properly.
        // No path assigned, so the planter is settled and at-cell by default —
        // ChargeSiteObjective.isComplete() gates on sim.movement().atCell/settled.
        long p = sim.spawn(new EntitySpec("complete-" + name, Faction.MARINE, UnitType.MARINE, x, y)
                .role(UnitRole.PLANTER)
                .assignedObjective(cs));
        cs.tick(sim);
        return cs;
    }

    private static void tick(SabotageCommand command, BattleSimulation sim) {
        CommanderService.runSingle(command, SabotageCommandDisclosure.INSTANCE,
                sim);
    }
}
