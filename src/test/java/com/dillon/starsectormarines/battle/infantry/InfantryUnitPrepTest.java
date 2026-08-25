package com.dillon.starsectormarines.battle.infantry;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for reactive special-equipment targeting that runs before a marine's
 * normal action and lets them brace against a hardened target spotted in
 * passing.
 */
public class InfantryUnitPrepTest {

    private static BattleSimulation openArena(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, topology);
    }

    private static long rocketeer(BattleSimulation sim, Faction f, int x, int y) {
        long u = sim.spawn(new EntitySpec("u" + sim.liveUnitCount(), f, UnitType.MARINE, x, y)
                .secondary(MarineSecondary.ROCKET_LAUNCHER, MarineSecondary.ROCKET_LAUNCHER.startingAmmo()));
        // Primary weapon ref only — .primaryWeapon() would derive the weapon's
        // damage/accuracy/range/cooldown; this test keeps the UnitType.MARINE
        // defaults and sets attackRange separately below, so set the ref by id.
        sim.combat().setPrimaryWeapon(u, MarineWeapon.PULSE_RIFLE);
        // attackRange is a Group-S registry-backed stat — set after the unit is
        // registered (the accessor is fail-loud pre-allocate).
        sim.world().setAttackRange(u, MarineWeapon.PULSE_RIFLE.range());
        return u;
    }

    private static long heavyMarksman(BattleSimulation sim, Faction faction, int x, int y) {
        return sim.spawn(new EntitySpec("amr" + sim.liveUnitCount(), faction,
                UnitType.MARINE, x, y)
                .secondary(MarineSecondary.ANTI_MATERIEL_RIFLE,
                        MarineSecondary.ANTI_MATERIEL_RIFLE.startingAmmo()));
    }

    private static long turret(BattleSimulation sim, Faction f, TurretKind kind, int x, int y) {
        return sim.spawn(MapTurret.create("t" + sim.liveUnitCount(), f, kind, x, y));
    }

    @Test
    public void opportunityRocketStartsAimWhenTurretInRangeWithLos() {
        BattleSimulation sim = openArena(50, 10);
        long marine = rocketeer(sim, Faction.MARINE, 5, 5);
        // Past pulse-rifle range (24), well inside rocket range (32).
        long turret = turret(sim, Faction.DEFENDER, TurretKind.VULCAN, 28, 5);

        boolean started = InfantryUnitPrep.tryOpportunityRocket(marine, sim);
        assertTrue(started, "marine in rocket range with LOS should start aim");
        assertEquals(MarineSecondary.ROCKET_LAUNCHER.aimDuration(),
                sim.world().secondaryActionTimer(marine), 0.001f);
        assertEquals(turret, sim.world().secondaryAimTargetId(marine));
    }

    @Test
    public void opportunityRocketNoOpsWithNoAmmo() {
        BattleSimulation sim = openArena(50, 10);
        long marine = rocketeer(sim, Faction.MARINE, 5, 5);
        sim.world().setSecondaryAmmo(marine, 0);
        turret(sim, Faction.DEFENDER, TurretKind.VULCAN, 28, 5);

        assertFalse(InfantryUnitPrep.tryOpportunityRocket(marine, sim));
        assertEquals(0f, sim.world().secondaryActionTimer(marine), 0.001f);
    }

    @Test
    public void opportunityRocketNoOpsWhenCooldownActive() {
        BattleSimulation sim = openArena(50, 10);
        long marine = rocketeer(sim, Faction.MARINE, 5, 5);
        sim.world().setSecondaryCooldownTimer(marine, 1.0f);
        turret(sim, Faction.DEFENDER, TurretKind.VULCAN, 28, 5);

        assertFalse(InfantryUnitPrep.tryOpportunityRocket(marine, sim));
    }

    @Test
    public void opportunityRocketNoOpsWhenTurretBeyondRocketRange() {
        BattleSimulation sim = openArena(80, 10);
        long marine = rocketeer(sim, Faction.MARINE, 5, 5);
        // 50 cells away — well past rocket range (32).
        turret(sim, Faction.DEFENDER, TurretKind.VULCAN, 55, 5);

        assertFalse(InfantryUnitPrep.tryOpportunityRocket(marine, sim));
    }

    @Test
    public void opportunityRocketNoOpsWhenLosBlocked() {
        BattleSimulation sim = openArena(50, 10);
        NavigationGrid grid = sim.getGrid();
        // Wall column between marine and turret.
        for (int y = 0; y < 10; y++) grid.setWalkable(15, y, false);
        long marine = rocketeer(sim, Faction.MARINE, 5, 5);
        turret(sim, Faction.DEFENDER, TurretKind.VULCAN, 28, 5);

        assertFalse(InfantryUnitPrep.tryOpportunityRocket(marine, sim),
                "LOS-blocked turret must not trigger opportunity fire");
    }

    @Test
    public void opportunityRocketRespectsSquadCoordination() {
        // Hephaestus needs 2 rockets — first two marines commit, third holds.
        BattleSimulation sim = openArena(50, 10);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long marineA = rocketeer(sim, Faction.MARINE, 5, 5);
        sim.squad().assignSquad(marineA, squadId);
        long marineB = rocketeer(sim, Faction.MARINE, 5, 6);
        sim.squad().assignSquad(marineB, squadId);
        long marineC = rocketeer(sim, Faction.MARINE, 5, 4);
        sim.squad().assignSquad(marineC, squadId);
        turret(sim, Faction.DEFENDER, TurretKind.HEPHAESTUS, 28, 5);

        assertTrue(InfantryUnitPrep.tryOpportunityRocket(marineA, sim));
        assertTrue(InfantryUnitPrep.tryOpportunityRocket(marineB, sim),
                "second squadmate joins when one inbound rocket can't kill the turret");
        assertFalse(InfantryUnitPrep.tryOpportunityRocket(marineC, sim),
                "third squadmate must hold fire — projected damage already exceeds turret HP");
    }

    @Test
    public void opportunityRocketBlocksSecondShotOnVulcan() {
        // The common case: Vulcan dies to one rocket. Once marineA commits,
        // marineB must not also fire.
        BattleSimulation sim = openArena(50, 10);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long marineA = rocketeer(sim, Faction.MARINE, 5, 5);
        sim.squad().assignSquad(marineA, squadId);
        long marineB = rocketeer(sim, Faction.MARINE, 5, 6);
        sim.squad().assignSquad(marineB, squadId);
        turret(sim, Faction.DEFENDER, TurretKind.VULCAN, 28, 5);

        assertTrue(InfantryUnitPrep.tryOpportunityRocket(marineA, sim));
        assertFalse(InfantryUnitPrep.tryOpportunityRocket(marineB, sim),
                "Vulcan dies to one rocket — second squadmate must skip");
    }

    @Test
    public void opportunityRocketIgnoresFriendlyTurret() {
        // Defender turret near a defender unit (e.g. a rocketeer enemy).
        // Sanity: the scan filters by enemy faction, not just "is a turret."
        BattleSimulation sim = openArena(50, 10);
        long defenderRocketeer = rocketeer(sim, Faction.DEFENDER, 5, 5);
        turret(sim, Faction.DEFENDER, TurretKind.VULCAN, 28, 5);

        assertFalse(InfantryUnitPrep.tryOpportunityRocket(defenderRocketeer, sim),
                "friendly turret must not be a rocket target");
    }

    @Test
    public void antiMaterielCarrierBracesOnlyForHardenedTargets() {
        BattleSimulation softSim = openArena(50, 10);
        long softCarrier = heavyMarksman(softSim, Faction.MARINE, 5, 5);
        softSim.spawn(new EntitySpec("infantry", Faction.DEFENDER,
                UnitType.MARINE, 28, 5));
        assertFalse(InfantryUnitPrep.tryOpportunitySpecial(softCarrier, softSim),
                "scarce heavy rounds are not intentionally spent on infantry");

        BattleSimulation hardSim = openArena(50, 10);
        long hardCarrier = heavyMarksman(hardSim, Faction.MARINE, 5, 5);
        long hardTarget = turret(hardSim, Faction.DEFENDER, TurretKind.VULCAN, 28, 5);
        assertTrue(InfantryUnitPrep.tryOpportunitySpecial(hardCarrier, hardSim));
        assertEquals(hardTarget, hardSim.world().secondaryAimTargetId(hardCarrier));
        assertEquals(MarineSecondary.ANTI_MATERIEL_RIFLE.aimDuration(),
                hardSim.world().secondaryActionTimer(hardCarrier), 0.001f);
    }

    @Test
    public void antiMaterielAimCancelsWithoutAmmoOrCooldownWhenLosIsLost() {
        BattleSimulation sim = openArena(50, 10);
        long carrier = heavyMarksman(sim, Faction.MARINE, 5, 5);
        turret(sim, Faction.DEFENDER, TurretKind.VULCAN, 28, 5);
        assertTrue(InfantryUnitPrep.tryOpportunitySpecial(carrier, sim));

        for (int y = 0; y < 10; y++) sim.getGrid().setWalkable(15, y, false);
        sim.world().setSecondaryActionTimer(carrier,
                MarineSecondary.ANTI_MATERIEL_RIFLE.aimDuration() * 0.5f);

        assertTrue(InfantryUnitPrep.tickAimAndShortCircuit(carrier, sim));
        assertEquals(MarineSecondary.ANTI_MATERIEL_RIFLE.startingAmmo(),
                sim.world().secondaryAmmo(carrier));
        assertEquals(0f, sim.world().secondaryCooldownTimer(carrier), 0.001f);
        assertTrue(sim.getShotsThisFrame().isEmpty(),
                "los loss cancels instead of redirecting or spending the round");
    }

    @Test
    public void opportunityPrimaryAuthorsClosestLegalTargetWithoutChangingPursuit() {
        BattleSimulation sim = openArena(30, 10);
        long defender = sim.spawn(new EntitySpec("d", Faction.DEFENDER, UnitType.MARINE, 5, 5));
        long pursuit = sim.spawn(new EntitySpec("far", Faction.MARINE, UnitType.MARINE, 22, 5));
        long opportunity = sim.spawn(new EntitySpec("near", Faction.MARINE, UnitType.MARINE, 9, 5));
        sim.world().setAttackRange(defender, 10f);
        sim.world().setTargetId(defender, pursuit);

        assertTrue(InfantryUnitPrep.tryOpportunityPrimary(defender, sim));
        assertEquals(opportunity, sim.combat().fireTargetId(defender));
        assertEquals(pursuit, sim.world().targetId(defender),
                "a passing shot must not replace the action's pursuit target");
    }

    @Test
    public void opportunityPrimaryDoesNotOverrideActionAuthoredIntent() {
        BattleSimulation sim = openArena(30, 10);
        long defender = sim.spawn(new EntitySpec("d", Faction.DEFENDER, UnitType.MARINE, 5, 5));
        long chosen = sim.spawn(new EntitySpec("chosen", Faction.MARINE, UnitType.MARINE, 10, 5));
        sim.spawn(new EntitySpec("closer", Faction.MARINE, UnitType.MARINE, 7, 5));
        sim.combat().setFireIntent(defender, chosen,
                com.dillon.starsectormarines.battle.combat.FireStance.STANCED, false);

        assertFalse(InfantryUnitPrep.tryOpportunityPrimary(defender, sim));
        assertEquals(chosen, sim.combat().fireTargetId(defender));
    }

    @Test
    public void opportunityPrimaryKeepsNearEqualAcquisitionButSwitchesForMaterialAdvantage() {
        BattleSimulation sim = openArena(30, 10);
        long defender = sim.spawn(new EntitySpec("d", Faction.DEFENDER, UnitType.MARINE, 5, 5));
        long acquired = sim.spawn(new EntitySpec("acquired", Faction.MARINE, UnitType.MARINE, 10, 5));
        long challenger = sim.spawn(new EntitySpec("challenger", Faction.MARINE, UnitType.MARINE, 9, 5));
        sim.world().setAttackRange(defender, 10f);
        sim.combat().setReflexTargetId(defender, acquired);
        sim.combat().setReflexTimer(defender, 0.2f);

        assertTrue(InfantryUnitPrep.tryOpportunityPrimary(defender, sim));
        assertEquals(acquired, sim.combat().fireTargetId(defender),
                "one-cell advantage must not churn a legal acquisition lock");
        assertEquals(0.2f, sim.combat().reflexTimer(defender), 1e-6f,
                "retaining the lock must not restart registration");

        sim.getEntityWorld().setLong(defender, sim.getBattleComponents().COMBAT,
                com.dillon.starsectormarines.battle.component.BattleComponents.COMBAT_FIRE_TARGET_ID, 0L);
        sim.world().setCellPos(challenger, 7, 5);
        assertTrue(InfantryUnitPrep.tryOpportunityPrimary(defender, sim));
        assertEquals(challenger, sim.combat().fireTargetId(defender),
                "a target more than two cells closer must replace the lock");
        assertEquals(challenger, sim.combat().reflexTargetId(defender));
        assertEquals(ExperienceTier.REGULAR.reflexDelaySeconds,
                sim.combat().reflexTimer(defender), 1e-6f);
    }

    @Test
    public void tacticalActionsRetainDispatcherOpportunityFire() {
        assertTrue(OverwatchPosture.INSTANCE.permitsOpportunityFire(),
                "holding ground must not mean waiting passively to be shot");
        assertTrue(new ChokePointHold(1, 5, 5, java.util.List.of(new int[]{4, 5}))
                .permitsOpportunityFire(),
                "holding a choke point must not suppress legal targets outside the portal cell");
        assertTrue(new FlankApproach(10, 10).permitsOpportunityFire(),
                "flank movement should not make a discovered squad ignore legal shots");
        assertTrue(new BreachAndAdvance(1, new int[]{1}, new int[]{1},
                new int[]{2}, new int[]{2}).permitsOpportunityFire(),
                "breach movement should not make a squad ignore legal shots");
        assertTrue(RegroupPosture.INSTANCE.permitsOpportunityFire(),
                "ordinary movement should retain the opportunity-fire safety net");
    }

    @Test
    public void defenderWithoutReadyPlanStillTakesOpportunityShot() {
        BattleSimulation sim = openArena(30, 10);
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MARINE);
        long defender = sim.spawn(new EntitySpec("d", Faction.DEFENDER, UnitType.MARINE, 5, 5)
                .squad(squadId));
        long marine = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 9, 5));
        sim.world().setAttackRange(defender, 10f);

        GoapInfantryBehavior.INSTANCE.update(defender, sim);

        assertEquals(marine, sim.combat().fireTargetId(defender),
                "a replan gap must not cost a defender a legal shot");
    }

    @Test
    public void nullPlanClearsFormerActionPath() {
        BattleSimulation sim = openArena(30, 10);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long member = sim.spawn(new EntitySpec("m", Faction.MARINE,
                UnitType.MARINE, 5, 5).squad(squadId));
        sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                5, 5, 20, 5));

        GoapInfantryBehavior.INSTANCE.update(member, sim);

        assertTrue(Paths.isEmpty(sim.world().path(member)),
                "no executing plan may expose a ghost movement order");
    }

    @Test
    public void completedPlanRetainsPathForNextPostureHandoff() {
        BattleSimulation sim = openArena(30, 10);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long member = sim.spawn(new EntitySpec("m", Faction.MARINE,
                UnitType.MARINE, 5, 5).squad(squadId));
        sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                5, 5, 20, 5));
        squad.currentPlan = new SquadPlan(List.of());

        GoapInfantryBehavior.INSTANCE.update(member, sim);

        assertFalse(Paths.isEmpty(sim.world().path(member)),
                "Approach-to-Engage handoff keeps sibling movement paths");
    }

    @Test
    public void assignmentChangeInterruptsAnOtherwiseFreshPlan() {
        BattleSimulation sim = openArena(30, 10);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        sim.spawn(new EntitySpec("m", Faction.MARINE,
                UnitType.MARINE, 5, 5).squad(squadId));
        squad.aliveMembers = 1;
        squad.aliveMembersAtLastPlan = 1;
        squad.assignedObjectiveAtLastPlan =
                ObjectiveAssignment.clearZone(squadId, 1);
        SquadPlan.Step oldStep = new SquadPlan.Step(OverwatchPosture.INSTANCE);
        squad.currentPlan = new SquadPlan(List.of(oldStep));

        GoapInfantryBehavior.replanIfNeeded(squad, sim);

        assertNull(squad.assignedObjectiveAtLastPlan);
        assertNull(squad.currentPlan,
                "released mission work replans immediately to the ambient floor");
    }

    @Test
    public void formUpMasksAndThenReactivatesTheSameAuthoritativeOrder() {
        BattleSimulation sim = openArena(30, 10);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        sim.spawn(new EntitySpec("m", Faction.MARINE,
                UnitType.MARINE, 5, 5).squad(squadId));
        squad.aliveMembers = 1;
        squad.aliveMembersAtLastPlan = 1;
        ObjectiveAssignment order = ObjectiveAssignment.clearZone(squadId, 1);
        squad.assignedObjective = order;
        squad.assignedObjectiveAtLastPlan = order;
        squad.campaignSquadId = "campaign-1";
        squad.expectedSize = 12;
        squad.originalSize = 4;
        squad.currentPlan = new SquadPlan(List.of(
                new SquadPlan.Step(OverwatchPosture.INSTANCE)));

        GoapInfantryBehavior.replanIfNeeded(squad, sim);

        assertSame(order, squad.assignedObjective,
                "form-up must not erase the authoritative directive");
        assertNull(squad.assignedObjectiveAtLastPlan,
                "planner tracks the masked execution assignment");
        assertNull(squad.currentPlan);

        squad.originalSize = 12;
        squad.currentPlan = new SquadPlan(List.of(
                new SquadPlan.Step(OverwatchPosture.INSTANCE)));
        GoapInfantryBehavior.replanIfNeeded(squad, sim);

        assertSame(order, squad.assignedObjectiveAtLastPlan,
                "landing completion makes the retained order executable again");
    }

    @Test
    public void flankApproachTakesOpportunityShotWithoutAbandoningMovement() {
        BattleSimulation sim = openArena(30, 10);
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long defender = sim.spawn(new EntitySpec("d", Faction.DEFENDER, UnitType.MARINE, 5, 5)
                .squad(squadId));
        long marine = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 9, 5));
        sim.world().setAttackRange(defender, 10f);
        FlankApproach action = new FlankApproach(20, 5);
        SquadPlan.Step step = new SquadPlan.Step(action);
        step.assignments.put("any", List.of(defender));
        squad.currentPlan = new SquadPlan(List.of(step));

        GoapInfantryBehavior.INSTANCE.update(defender, sim);

        assertEquals(marine, sim.combat().fireTargetId(defender),
                "a discovered flank should return fire while continuing toward its waypoint");
        assertTrue(sim.world().path(defender).length > 0,
                "taking the passing shot must not discard the flank path");
    }

    @Test
    public void breachMovementTakesOpportunityShotWithoutAbandoningStackUp() {
        BattleSimulation sim = openArena(30, 10);
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long defender = sim.spawn(new EntitySpec("d", Faction.DEFENDER, UnitType.MARINE, 5, 5)
                .squad(squadId));
        long marine = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 9, 5));
        sim.world().setAttackRange(defender, 10f);
        BreachAndAdvance action = new BreachAndAdvance(1,
                new int[]{15}, new int[]{5}, new int[]{20}, new int[]{5});
        SquadPlan.Step step = new SquadPlan.Step(action);
        step.assignments.put("breacher:0", List.of(defender));
        squad.currentPlan = new SquadPlan(List.of(step));

        GoapInfantryBehavior.INSTANCE.update(defender, sim);

        assertEquals(marine, sim.combat().fireTargetId(defender),
                "a squad moving to its breach stack should take a legal passing shot");
        assertTrue(sim.world().path(defender).length > 0,
                "taking the passing shot must not discard the stack-up path");
    }

    @Test
    public void dispatcherFiresWhileOverwatchHoldsGround() {
        BattleSimulation sim = openArena(30, 10);
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long defender = sim.spawn(new EntitySpec("d", Faction.DEFENDER, UnitType.MARINE, 5, 5)
                .squad(squadId));
        long marine = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 9, 5));
        sim.world().setAttackRange(defender, 10f);
        SquadPlan.Step step = new SquadPlan.Step(OverwatchPosture.INSTANCE);
        step.assignments.put("any", List.of(defender));
        squad.currentPlan = new SquadPlan(List.of(step));

        GoapInfantryBehavior.INSTANCE.update(defender, sim);

        assertEquals(marine, sim.combat().fireTargetId(defender),
                "overwatch should engage a legal target while remaining planted");
        assertTrue(sim.world().path(defender).length == 0,
                "overwatch fire must not pull the holder off its ground");
    }

    @Test
    public void dispatcherFiresWhileChokePointHolderKeepsItsPost() {
        BattleSimulation sim = openArena(30, 10);
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long defender = sim.spawn(new EntitySpec("d", Faction.DEFENDER, UnitType.MARINE, 5, 5)
                .squad(squadId));
        long marine = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 9, 5));
        sim.world().setAttackRange(defender, 10f);
        ChokePointHold action = new ChokePointHold(999, 6, 5, List.of(new int[]{5, 5}));
        SquadPlan.Step step = new SquadPlan.Step(action);
        step.assignments.put(ChokePointHold.slotName(0), List.of(defender));
        squad.currentPlan = new SquadPlan(List.of(step));

        GoapInfantryBehavior.INSTANCE.update(defender, sim);

        assertEquals(marine, sim.combat().fireTargetId(defender),
                "an empty watched portal must not stop a holder shooting another legal target");
        assertEquals(5, sim.world().cellX(defender));
        assertEquals(5, sim.world().cellY(defender));
        assertTrue(sim.world().path(defender).length == 0,
                "choke-point fire must not pull the holder off its assigned post");
    }
}
