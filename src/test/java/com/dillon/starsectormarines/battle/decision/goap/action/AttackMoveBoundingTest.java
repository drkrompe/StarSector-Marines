package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An attack move bounds. The behaviour is the room-crossing advance's, shared
 * through {@link AbstractZoneAction} rather than reimplemented — these pin that
 * the order actually reaches it, and that it stays off when there is nothing to
 * bound against.
 */
public class AttackMoveBoundingTest {

    private static final int W = 64;
    private static final int H = 32;
    private static final int DEST_X = 52;
    private static final int DEST_Y = 15;

    private record Fixture(BattleSimulation sim, Squad squad,
                           AttackMove action, List<Long> members) { }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Fixture fixture(boolean withThreats) {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            EntitySpec spec = new EntitySpec("m" + i, Faction.MARINE,
                    UnitType.MARINE, 10, 14 + i).squad(squadId);
            if (i == 0) {
                spec.primaryWeapon(WeaponRegistry.require(
                        WeaponRegistry.SQUAD_AUTOMATIC_ID));
            }
            long member = sim.spawn(spec);
            sim.world().setAttackRange(member, 30f);
            members.add(member);
        }
        squad.leaderId = members.get(0);
        squad.aliveMembers = 4;
        squad.originalSize = 4;
        squad.centroidX = 10.5f;
        squad.centroidY = 16f;
        squad.assignedObjective = ObjectiveAssignment.attackMove(
                squad.id, DEST_X, DEST_Y);

        if (withThreats) {
            // Two contacts astride the route saturate the threat score, which
            // is what commits the advance and licenses a bound.
            sim.spawn(new EntitySpec("d0", Faction.DEFENDER, UnitType.MARINE, 35, 15));
            sim.spawn(new EntitySpec("d1", Faction.DEFENDER, UnitType.MARINE, 37, 16));
        } else {
            // A scene needs both sides present or the simulation returns
            // without advancing a tick; keep one defender far out of the fight.
            sim.spawn(new EntitySpec("far", Faction.DEFENDER, UnitType.MARINE, 62, 30));
        }
        sim.advance(BattleSimulation.TICK_DT);

        AttackMove action = new AttackMove(DEST_X, DEST_Y);
        SquadPlan.Step step = new SquadPlan.Step(action);
        step.assignments.put(AbstractZoneAction.TEAM_A,
                new ArrayList<>(members.subList(0, 2)));
        step.assignments.put(AbstractZoneAction.TEAM_B,
                new ArrayList<>(members.subList(2, 4)));
        squad.currentPlan = new SquadPlan(List.of(step));
        return new Fixture(sim, squad, action, members);
    }

    @Test
    public void aCommittedAttackMoveBoundsByFireTeam() {
        Fixture f = fixture(true);
        for (long member : f.members) {
            f.action.execute(member, f.squad, f.sim);
        }

        assertTrue(f.squad.boundingActive,
                "route contact on an attack move opens a bound");
        assertTrue(f.squad.boundingMemberIds.length > 0
                        && f.squad.boundingMemberIds.length < f.members.size(),
                "one team moves while its siblings hold, never the whole squad");
    }

    @Test
    public void theOverwatchingTeamHoldsWhileItsSiblingMoves() {
        Fixture f = fixture(true);
        for (long member : f.members) {
            f.action.execute(member, f.squad, f.sim);
        }
        assertTrue(f.squad.boundingActive);

        for (long member : f.members) {
            boolean bounding = false;
            for (long id : f.squad.boundingMemberIds) if (id == member) bounding = true;
            if (bounding) continue;
            assertTrue(Paths.isEmpty(f.sim.world().path(member)),
                    "a member on overwatch is not also walking");
        }
    }

    @Test
    public void aCommittedThreatTooFarToShootDoesNotEarnABound() {
        // Commitment reaches much further than fire does: the advance-threat
        // score looks tens of cells down the route, so a squad can be committed
        // to a contact that cannot touch it. Bounding that stretch moves half
        // the squad at a time and buys nothing.
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            long member = sim.spawn(new EntitySpec("m" + i, Faction.MARINE,
                    UnitType.MARINE, 10, 14 + i).squad(squadId));
            sim.world().setAttackRange(member, 30f);
            members.add(member);
        }
        squad.leaderId = members.get(0);
        squad.aliveMembers = 4;
        squad.originalSize = 4;
        squad.centroidX = 10.5f;
        squad.centroidY = 16f;
        squad.assignedObjective = ObjectiveAssignment.attackMove(
                squad.id, DEST_X, DEST_Y);

        long far = sim.spawn(new EntitySpec("d0", Faction.DEFENDER,
                UnitType.MARINE, 44, 15));
        sim.spawn(new EntitySpec("d1", Faction.DEFENDER,
                UnitType.MARINE, 45, 16));
        sim.advance(BattleSimulation.TICK_DT);

        AttackMove action = new AttackMove(DEST_X, DEST_Y);
        SquadPlan.Step step = new SquadPlan.Step(action);
        step.assignments.put(AbstractZoneAction.TEAM_A,
                new ArrayList<>(members.subList(0, 2)));
        step.assignments.put(AbstractZoneAction.TEAM_B,
                new ArrayList<>(members.subList(2, 4)));
        squad.currentPlan = new SquadPlan(List.of(step));

        for (long member : members) action.execute(member, squad, sim);

        assertTrue(squad.advanceEngageCommitted,
                "the distant pair still commits the advance");
        assertFalse(sim.getTacticalScoring().threatReaches(far,
                squad.centroidX, squad.centroidY, AbstractZoneAction.BOUNDING_STRIDE),
                "and is nonetheless outside its own beaten zone");
        assertFalse(squad.boundingActive,
                "so the squad walks rather than bounding at nothing");
    }

    @Test
    public void anUncontestedAttackMoveDoesNotBound() {
        Fixture f = fixture(false);
        for (long member : f.members) {
            f.action.execute(member, f.squad, f.sim);
        }

        assertFalse(f.squad.boundingActive,
                "bounding into empty ground is a slow walk with extra steps");
        assertFalse(Paths.isEmpty(f.sim.world().path(f.squad.leaderId)),
                "the squad still moves on its objective");
    }
}
