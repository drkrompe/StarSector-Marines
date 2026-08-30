package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.squad.MechScreenMode;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Story 23 coverage for faction-neutral mech selection and threat-relative dynamic formations. */
class MechScreenAdvanceTest {

    private static final int TARGET_ZONE = 777;
    private static final int DEST_X = 52;
    private static final int DEST_Y = 15;

    @Test
    void bothFactionsPreferTheSameAssignmentAndRejectInvalidCandidates() {
        for (Faction faction : List.of(Faction.MARINE, Faction.DEFENDER)) {
            Fixture f = fixture(faction);
            Faction enemyFaction = opposite(faction);
            spawnMech(f.sim, enemyFaction, MechRole.ARMORED_SUPPORT, 12, 15, false, true);
            spawnMech(f.sim, faction, MechRole.ASSAULT, 13, 15, false, true);
            spawnMech(f.sim, faction, MechRole.ARMORED_SUPPORT, 14, 15, true, true);
            spawnMech(f.sim, faction, MechRole.ARMORED_SUPPORT, 15, 15, false, false);
            spawnMech(f.sim, faction, MechRole.ARMORED_SUPPORT, 2, 15, false, true);

            assertEquals(f.mech, MechScreenAdvance.selectScreeningMech(
                    f.squad, TARGET_ZONE, DEST_X, DEST_Y, f.sim), faction.name());
        }
    }

    @Test
    void selectorReturnsNoScreenForEnemyNonTankOrRescueMechs() {
        Fixture f = fixture(Faction.MARINE);
        f.sim.world().mechLoadout(f.mech)
                .applyBattleOverride(MechRole.ASSAULT);
        spawnMech(f.sim, Faction.DEFENDER, MechRole.ARMORED_SUPPORT, 12, 15, false, true);
        spawnMech(f.sim, Faction.MARINE, MechRole.ASSAULT, 13, 15, false, true);
        spawnMech(f.sim, Faction.MARINE, MechRole.ARMORED_SUPPORT, 14, 15, true, true);

        assertEquals(0L, MechScreenAdvance.selectScreeningMech(
                f.squad, TARGET_ZONE, DEST_X, DEST_Y, f.sim));
    }

    @Test
    void unassignedFallbackMustAlreadyBeNearAndOnTheObjectiveAxis() {
        Fixture f = fixture(Faction.MARINE);
        f.sim.world().mechLoadout(f.mech)
                .applyBattleOverride(MechRole.ASSAULT);
        spawnMech(f.sim, Faction.MARINE, MechRole.ARMORED_SUPPORT, 15, 25, false, false);

        assertEquals(0L, MechScreenAdvance.selectScreeningMech(
                f.squad, TARGET_ZONE, DEST_X, DEST_Y, f.sim));

        long axial = spawnMech(f.sim, Faction.MARINE,
                MechRole.ARMORED_SUPPORT, 15, 18, false, false);
        assertEquals(axial, MechScreenAdvance.selectScreeningMech(
                f.squad, TARGET_ZONE, DEST_X, DEST_Y, f.sim));
    }

    @Test
    void followPocketStaysBehindTheMechAndMovesWithIt() {
        Fixture f = fixture(Faction.MARINE);

        f.action.execute(f.members.get(0), f.squad, f.sim);

        assertEquals(MechScreenMode.FOLLOW, f.squad.mechScreenMode);
        assertEquals(f.mech, f.squad.screeningMechId);
        int[] firstXs = f.squad.mechScreenTargetXs.clone();
        assertEquals(f.members.size(), firstXs.length);
        for (int x : firstXs) {
            assertTrue(x < f.sim.world().cellX(f.mech),
                    "the east-facing threat axis keeps every follower behind the chassis");
        }
        assertDistinctTargets(f.squad);

        f.sim.world().setCellPos(f.mech, 24, 15);
        f.squad.mechScreenTick = -1;
        f.action.execute(f.members.get(0), f.squad, f.sim);

        for (int i = 0; i < firstXs.length; i++) {
            assertTrue(f.squad.mechScreenTargetXs[i] > firstXs[i],
                    "formation cells must follow the live mech instead of remaining terrain anchors");
            assertTrue(f.squad.mechScreenTargetXs[i] < f.sim.world().cellX(f.mech));
        }
    }

    @Test
    void visibleMechContactBuildsALeashedTwoSidedFanThatCanFire() {
        Fixture f = fixture(Faction.DEFENDER);
        f.sim.world().setTargetId(f.mech, f.enemy);
        for (long member : f.members) f.sim.world().setAttackRange(member, 40f);

        f.action.execute(f.members.get(0), f.squad, f.sim);

        assertEquals(MechScreenMode.FAN, f.squad.mechScreenMode);
        assertEquals(f.enemy, f.squad.mechScreenThreatId);
        boolean above = false;
        boolean below = false;
        float mechX = f.sim.world().x(f.mech);
        float mechY = f.sim.world().y(f.mech);
        for (int i = 0; i < f.squad.mechScreenTargetXs.length; i++) {
            int x = f.squad.mechScreenTargetXs[i];
            int y = f.squad.mechScreenTargetYs[i];
            above |= y + 0.5f > mechY;
            below |= y + 0.5f < mechY;
            float dx = x + 0.5f - mechX;
            float dy = y + 0.5f - mechY;
            assertTrue(dx * dx + dy * dy
                    <= MechScreenAdvance.SCREEN_LEASH * MechScreenAdvance.SCREEN_LEASH);
        }
        assertTrue(above && below, "the contact fan must occupy both sides of the mech");
        assertDistinctTargets(f.squad);

        for (int i = 0; i < f.squad.mechScreenMemberIds.length; i++) {
            long member = f.squad.mechScreenMemberIds[i];
            f.sim.world().setCellPos(member,
                    f.squad.mechScreenTargetXs[i], f.squad.mechScreenTargetYs[i]);
            f.sim.clearPath(member);
            f.action.execute(member, f.squad, f.sim);
            assertEquals(f.enemy, f.sim.combat().fireTargetId(member));
        }
    }

    @Test
    void losingTheTankRoleImmediatelyFallsBackToOrdinaryEnterZoneMovement() {
        Fixture f = fixture(Faction.MARINE);
        f.action.execute(f.members.get(0), f.squad, f.sim);
        assertEquals(f.mech, f.squad.screeningMechId);

        f.sim.world().mechLoadout(f.mech)
                .applyBattleOverride(MechRole.ASSAULT);
        f.squad.mechScreenTick = -1;
        long member = f.members.get(0);
        f.action.execute(member, f.squad, f.sim);

        assertEquals(0L, f.squad.screeningMechId);
        assertEquals(MechScreenMode.NONE, f.squad.mechScreenMode);
        assertNotEquals(0, f.sim.world().path(member).length,
                "ordinary EnterZone movement resumes in the same action tick");
    }

    private static Fixture fixture(Faction faction) {
        BattleSimulation sim = openSimulation();
        int infantrySquadId = sim.mintSquad(faction, UnitType.MARINE);
        Squad squad = sim.getSquad(infantrySquadId);
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            members.add(sim.spawn(new EntitySpec("inf" + i, faction,
                    UnitType.MARINE, 10, 13 + i).squad(infantrySquadId)));
        }
        squad.leaderId = members.get(0);
        squad.aliveMembers = members.size();
        squad.originalSize = members.size();
        squad.centroidX = 10.5f;
        squad.centroidY = 15.5f;
        squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id, TARGET_ZONE);

        long mech = spawnMech(sim, faction, MechRole.ARMORED_SUPPORT, 20, 15, false, true);
        long enemy = sim.spawn(new EntitySpec("enemy", opposite(faction),
                UnitType.MARINE, 42, 15));

        EnterZone action = new EnterZone(TARGET_ZONE, DEST_X, DEST_Y);
        SquadPlan.Step step = new SquadPlan.Step(action);
        step.assignments.put(EnterZone.TEAM_A,
                new ArrayList<>(members.subList(0, 2)));
        step.assignments.put(EnterZone.TEAM_B,
                new ArrayList<>(members.subList(2, 4)));
        squad.currentPlan = new SquadPlan(List.of(step));
        return new Fixture(sim, squad, action, members, mech, enemy);
    }

    private static long spawnMech(BattleSimulation sim, Faction faction, MechRole role,
                                  int x, int y, boolean rescue, boolean matchingAssignment) {
        int squadId = sim.mintSquad(faction, UnitType.HEAVY_MECH);
        long mech = sim.spawn(MechVariant.HOUND.applyTo(new EntitySpec(
                "mech" + sim.liveUnitCount(), faction, UnitType.HEAVY_MECH, x, y)
                .squad(squadId)));
        sim.world().attachMechLoadout(mech, MechLoadoutComponent.defaultLoadout(role));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = mech;
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.centroidX = x + 0.5f;
        squad.centroidY = y + 0.5f;
        squad.rescuePickupMech = rescue;
        if (matchingAssignment) {
            squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id, TARGET_ZONE);
        }
        return mech;
    }

    private static BattleSimulation openSimulation() {
        int width = 64;
        int height = 32;
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }

    private static Faction opposite(Faction faction) {
        return faction == Faction.MARINE ? Faction.DEFENDER : Faction.MARINE;
    }

    private static void assertDistinctTargets(Squad squad) {
        for (int i = 0; i < squad.mechScreenTargetXs.length; i++) {
            for (int j = i + 1; j < squad.mechScreenTargetXs.length; j++) {
                assertTrue(squad.mechScreenTargetXs[i] != squad.mechScreenTargetXs[j]
                                || squad.mechScreenTargetYs[i] != squad.mechScreenTargetYs[j],
                        "screen members must not dogpile one destination cell");
            }
        }
    }

    private record Fixture(BattleSimulation sim, Squad squad, EnterZone action,
                           List<Long> members, long mech, long enemy) {}
}
