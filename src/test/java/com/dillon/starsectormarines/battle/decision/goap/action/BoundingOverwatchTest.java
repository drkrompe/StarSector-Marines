package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.infantry.GoapInfantryBehavior;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Story 20 coverage for stable team slots, move/fire exclusivity, role flips, and release fallback. */
public class BoundingOverwatchTest {

    private static final int W = 64;
    private static final int H = 32;
    private static final int DEST_X = 52;
    private static final int DEST_Y = 15;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Fixture fourMarineFixture() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            EntitySpec spec = new EntitySpec("m" + i, Faction.MARINE,
                    UnitType.MARINE, 10, 14 + i).squad(squadId);
            if (i == 0) spec.primaryWeapon(WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID));
            long member = sim.spawn(spec);
            sim.world().setAttackRange(member, 30f);
            members.add(member);
        }
        squad.leaderId = members.get(0);
        squad.aliveMembers = 4;
        squad.originalSize = 4;
        squad.centroidX = 10.5f;
        squad.centroidY = 16f;

        long firstThreat = sim.spawn(new EntitySpec("d0", Faction.DEFENDER,
                UnitType.MARINE, 35, 15));
        long secondThreat = sim.spawn(new EntitySpec("d1", Faction.DEFENDER,
                UnitType.MARINE, 37, 16));
        sim.advance(BattleSimulation.TICK_DT);

        EnterZone action = new EnterZone(-99, DEST_X, DEST_Y);
        SquadPlan.Step step = new SquadPlan.Step(action);
        step.assignments.put(EnterZone.TEAM_A, new ArrayList<>(members.subList(0, 2)));
        step.assignments.put(EnterZone.TEAM_B, new ArrayList<>(members.subList(2, 4)));
        squad.currentPlan = new SquadPlan(List.of(step));
        return new Fixture(sim, squad, action, members, firstThreat, secondThreat);
    }

    @Test
    public void fourMembersRemainOneOrganizationalFireTeam() {
        Fixture f = fourMarineFixture();
        var roles = f.action.assignRoles(f.squad, f.sim, f.members);

        assertEquals(1, roles.size());
        assertEquals(EnterZone.TEAM_A, roles.keySet().iterator().next());
        assertEquals(4, roles.get(EnterZone.TEAM_A).size());
        assertFalse(f.action.permitsOpportunityFire(),
                "the dispatcher must not fill an intentionally empty bounder fire intent");
    }

    @Test
    public void twelveMembersBoundAsOneTeamWithTwoTeamsCovering() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            long member = sim.spawn(new EntitySpec("m" + i, Faction.MARINE,
                    UnitType.MARINE, 10, 4 + i).squad(squadId).fireTeam(i / 4));
            sim.world().setAttackRange(member, 30f);
            sim.setPath(member, new int[]{10, 4 + i, DEST_X, DEST_Y});
            members.add(member);
        }
        squad.leaderId = members.get(0);
        squad.aliveMembers = 12;
        squad.originalSize = 12;
        squad.centroidX = 10.5f;
        squad.centroidY = 10f;
        for (int i = 0; i < 6; i++) {
            sim.spawn(new EntitySpec("threat" + i, Faction.DEFENDER,
                    UnitType.MARINE, 35, 7 + i));
        }
        sim.advance(BattleSimulation.TICK_DT);
        for (int i = 0; i < members.size(); i++) {
            sim.setPath(members.get(i), new int[]{10, 4 + i, DEST_X, DEST_Y});
        }

        EnterZone action = new EnterZone(-99, DEST_X, DEST_Y);
        SquadPlan.Step step = new SquadPlan.Step(action);
        step.assignments.putAll(action.assignRoles(squad, sim, members));
        squad.currentPlan = new SquadPlan(List.of(step));

        action.execute(members.get(0), squad, sim);
        action.execute(members.get(4), squad, sim);
        action.execute(members.get(8), squad, sim);

        assertEquals(3, step.assignments.size());
        assertEquals(4, squad.boundingMemberIds.length,
                "only one four-marine team maneuvers in each phase");
        assertTrue(Paths.isEmpty(sim.world().path(members.get(0))));
        assertFalse(Paths.isEmpty(sim.world().path(members.get(4))));
        assertTrue(Paths.isEmpty(sim.world().path(members.get(8))));
        assertTrue(sim.combat().fireTargetId(members.get(0)) != 0L);
        assertTrue(sim.combat().fireTargetId(members.get(8)) != 0L);
    }

    @Test
    public void committedAdvanceHoldsTeamAAndMovesTeamBWithoutFire() {
        Fixture f = fourMarineFixture();
        long suppressor = f.members.get(0);
        long bounder0 = f.members.get(2);
        long bounder1 = f.members.get(3);
        f.sim.setPath(suppressor, new int[]{10, 14, DEST_X, DEST_Y});

        f.action.execute(suppressor, f.squad, f.sim);
        f.action.execute(bounder0, f.squad, f.sim);
        f.action.execute(bounder1, f.squad, f.sim);

        assertTrue(f.squad.advanceEngageCommitted);
        assertTrue(f.squad.boundingActive);
        assertEquals(0, f.squad.boundingPhase);
        assertTrue(Paths.isEmpty(f.sim.world().path(suppressor)),
                "overwatch clears objective movement and holds");
        assertEquals(f.firstThreat, f.sim.combat().fireTargetId(suppressor));
        assertEquals(WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID),
                f.sim.combat().primaryWeapon(suppressor));
        assertEquals(FireStance.STANCED.ordinal(), fireStance(f.sim, suppressor));

        assertEquals(0L, f.sim.combat().fireTargetId(bounder0));
        assertEquals(0L, f.sim.combat().fireTargetId(bounder1));
        assertFalse(Paths.isEmpty(f.sim.world().path(bounder0)));
        assertFalse(Paths.isEmpty(f.sim.world().path(bounder1)));
        assertEquals(2, f.squad.boundingMemberIds.length);
        assertTrue(f.squad.boundingTargetXs[0] != f.squad.boundingTargetXs[1]
                        || f.squad.boundingTargetYs[0] != f.squad.boundingTargetYs[1],
                "bounders reserve distinct forward cells");
    }

    @Test
    public void arrivingBoundersTakeOverOverwatchAndOldOverwatchLeapfrogsPast() {
        Fixture f = fourMarineFixture();
        f.action.execute(f.members.get(0), f.squad, f.sim);
        int firstStrideX = f.squad.boundingStrideX;
        int firstStrideY = f.squad.boundingStrideY;
        for (int i = 0; i < f.squad.boundingMemberIds.length; i++) {
            f.sim.world().setCellPos(f.squad.boundingMemberIds[i],
                    f.squad.boundingTargetXs[i], f.squad.boundingTargetYs[i]);
            f.sim.clearPath(f.squad.boundingMemberIds[i]);
        }

        f.action.execute(f.members.get(0), f.squad, f.sim);
        f.action.execute(f.members.get(2), f.squad, f.sim);

        assertEquals(1, f.squad.boundingPhase);
        assertEquals(f.members.get(0).longValue(), f.squad.boundingMemberIds[0],
                "the former overwatch team becomes the moving half");
        assertTrue(f.squad.boundingStrideX > firstStrideX
                        || f.squad.boundingStrideY != firstStrideY,
                "the next bound advances beyond the prior forward line");
        assertFalse(Paths.isEmpty(f.sim.world().path(f.members.get(0))));
        assertEquals(f.firstThreat, f.sim.combat().fireTargetId(f.members.get(2)),
                "arrived bounder takes over stanced covering fire");
        assertEquals(FireStance.STANCED.ordinal(), fireStance(f.sim, f.members.get(2)));
    }

    @Test
    public void retreatingThreatReleasesBoundAndRestoresObjectivePath() {
        Fixture f = fourMarineFixture();
        long member = f.members.get(0);
        f.action.execute(member, f.squad, f.sim);
        assertTrue(f.squad.boundingActive);

        f.sim.setPath(f.firstThreat, new int[]{35, 15, 55, 15});
        f.sim.setPath(f.secondThreat, new int[]{37, 16, 57, 16});
        f.squad.advanceThreatTick = -1;
        f.action.execute(member, f.squad, f.sim);

        assertFalse(f.squad.advanceEngageCommitted);
        assertFalse(f.squad.boundingActive);
        assertFalse(Paths.isEmpty(f.sim.world().path(member)));
        assertEquals(DEST_X, Paths.destX(f.sim.world().path(member)));
        assertEquals(DEST_Y, Paths.destY(f.sim.world().path(member)));
    }

    @Test
    public void noInitialFiringSolutionFallsBackToStory19CommittedApproach() {
        Fixture f = fourMarineFixture();
        long member = f.members.get(0);
        for (long marine : f.members) f.sim.world().setAttackRange(marine, 5f);

        f.action.execute(member, f.squad, f.sim);

        assertTrue(f.squad.advanceEngageCommitted);
        assertFalse(f.squad.boundingActive,
                "a bound never starts unless somebody can cover the movers");
        assertFalse(Paths.isEmpty(f.sim.world().path(member)),
                "the existing committed firing-position approach remains the fallback");
    }

    @Test
    public void moveOnlyPreparationDoesNotInitiateOpportunityRocket() {
        BattleSimulation sim = openSim();
        long marine = sim.spawn(new EntitySpec("rocketeer", Faction.MARINE,
                UnitType.MARINE, 10, 15));
        sim.world().attachSpecialEquipment(marine, SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID),
                SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID).startingAmmo());
        sim.spawn(MapTurret.create("turret", Faction.DEFENDER,
                TurretKind.VULCAN, 20, 15));

        assertTrue(GoapInfantryBehavior.prepareForAction(marine, sim, false),
                "move-only preparation continues into the action body");
        assertEquals(0f, sim.world().secondaryActionTimer(marine), 0.001f,
                "move-only role must not start a fresh rocket aim");

        assertFalse(GoapInfantryBehavior.prepareForAction(marine, sim, true),
                "ordinary actions still short-circuit when they commit the rocket");
        assertTrue(sim.world().secondaryActionTimer(marine) > 0f);
    }

    @Test
    public void boundingPickerPrefersDirectionalCoverOverOpenStrideCell() {
        BattleSimulation sim = openSim();
        long marine = sim.spawn(new EntitySpec("bounder", Faction.MARINE,
                UnitType.MARINE, 10, 15));
        sim.world().setAttackRange(marine, 30f);
        long threat = sim.spawn(new EntitySpec("threat", Faction.DEFENDER,
                UnitType.MARINE, 35, 15));
        sim.addDoodad(new Doodad(14, 15, new TileManifest.TileFrame(4, 7),
                false, Doodad.COVER_HEAVY));

        var positions = sim.getTacticalScoring().findBoundingPositions(
                List.of(marine), threat, 16, 15, DEST_X, DEST_Y);

        assertEquals(1, positions.size());
        assertTrue(positions.get(0).x() == 13 || positions.get(0).x() == 14,
                "heavy east-facing cover beats the exposed stride cell");
        assertEquals(15, positions.get(0).y());
    }

    private static int fireStance(BattleSimulation sim, long member) {
        return sim.getRoster().entityWorld().getInt(member,
                sim.getRoster().components().COMBAT, BattleComponents.COMBAT_FIRE_STANCE);
    }

    private record Fixture(BattleSimulation sim, Squad squad, EnterZone action,
                           List<Long> members, long firstThreat, long secondThreat) {}
}
