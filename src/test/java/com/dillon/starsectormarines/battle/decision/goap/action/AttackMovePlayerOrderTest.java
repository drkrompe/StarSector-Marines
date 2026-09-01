package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.ContactInitiative;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Doctrine;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.ForceBalance;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Motion;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Posture;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Sector;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A directly ordered attack move keeps its destination when the contact
 * picture would otherwise send the squad off to prosecute a contact.
 *
 * <p>Both cases run the same geometry and the same HOLD/{@code PROSECUTE}
 * picture, and differ only in who issued the order. The control matters as
 * much as the case: it is what shows the assertion is measuring the new gate
 * rather than a squad that was going to walk east anyway.
 */
class AttackMovePlayerOrderTest {

    private static final int W = 64;
    private static final int H = 32;
    private static final int DEST_X = 50;
    private static final int DEST_Y = 15;
    /** Off the advance axis, and too far from the objective to earn a firing spot near it. */
    private static final int ENEMY_X = 20;
    private static final int ENEMY_Y = 25;

    @Test
    void playerOrderedAttackMoveKeepsItsDestinationThroughContactProsecution() {
        BattleSimulation sim = openSim();
        Squad squad = marineSquad(sim);
        long enemy = prosecutableContact(sim, squad);

        sim.getSquadMoveOrderService().requestMove(squad.id, DEST_X, DEST_Y);
        sim.getSquadMoveOrderSystem().tick(sim);
        assertTrue(squad.hasPlayerOrder(AssignmentKind.ATTACK_MOVE),
                "the click has to land as a player order or the test proves nothing");

        ActionStatus status = new AttackMove(DEST_X, DEST_Y)
                .execute(squad.leaderId, squad, sim);

        assertEquals(ActionStatus.RUNNING, status);
        int[] path = sim.world().path(squad.leaderId);
        assertFalse(Paths.isEmpty(path),
                "an ordered squad that stops walking has not obeyed the order");
        assertEquals(DEST_X, Paths.destX(path));
        assertEquals(DEST_Y, Paths.destY(path));
        assertTrue(sim.resolveUnit(enemy) != 0L,
                "the contact is still alive — the squad declined it rather than removing it");
    }

    @Test
    void commanderAttackMoveStillProsecutesTheContact() {
        BattleSimulation sim = openSim();
        Squad squad = marineSquad(sim);
        squad.assignedObjective =
                ObjectiveAssignment.attackMove(squad.id, DEST_X, DEST_Y);
        prosecutableContact(sim, squad);

        ActionStatus status = new AttackMove(DEST_X, DEST_Y)
                .execute(squad.leaderId, squad, sim);

        assertEquals(ActionStatus.RUNNING, status);
        int[] path = sim.world().path(squad.leaderId);
        assertFalse(Paths.isEmpty(path));
        assertTrue(Paths.destX(path) != DEST_X || Paths.destY(path) != DEST_Y,
                "an autonomously assigned advance still chooses its own fight");
        assertTrue(TacticalScoring.cellDistance(
                        Paths.destX(path), Paths.destY(path),
                        Math.round(squad.centroidX - 0.5f),
                        Math.round(squad.centroidY - 0.5f))
                        <= AbstractZoneAction.ADVANCE_LEASH_MAX,
                "prosecution anchors the firing position on the squad, not the objective");
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Squad marineSquad(BattleSimulation sim) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long leader = 0L;
        for (int i = 0; i < 4; i++) {
            long member = sim.spawn(new EntitySpec("m" + i, Faction.MARINE,
                    UnitType.MARINE, 10, 14 + i).squad(squadId));
            if (i == 0) leader = member;
        }
        squad.leaderId = leader;
        squad.aliveMembers = 4;
        squad.originalSize = 4;
        squad.centroidX = 10.5f;
        squad.centroidY = 15.5f;
        return squad;
    }

    /**
     * A live enemy the squad has decided to go and fight: doctrine HOLD with
     * {@code PROSECUTE} initiative, off the advance axis so the route-threat
     * commit stays out of it and the only thing under test is prosecution.
     */
    private static long prosecutableContact(BattleSimulation sim, Squad squad) {
        long enemy = sim.spawn(new EntitySpec("d0", Faction.DEFENDER,
                UnitType.MARINE, ENEMY_X, ENEMY_Y));
        sim.advance(BattleSimulation.TICK_DT);
        // After the tick, not before: a tick re-derives a member's reach from
        // its (absent) weapon and would put the range back.
        for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
            long member = sim.squadMemberAt(squad.id, i);
            sim.world().setAttackRange(member, 5f);
            // Both cases start with empty hands. The setup tick leaves an
            // objective path behind and the repath throttle then makes every
            // branch walk it on regardless of what it decided, which is a
            // control that agrees with the case for the wrong reason.
            sim.clearPath(member);
        }
        sim.world().setTargetId(squad.leaderId, enemy);
        squad.contactPicture = new SquadContactPicture(sim.getSimTickIndex(),
                Posture.ADVANCING, 1f, 0f, 1, 1, 1f, 4,
                ForceBalance.FAVORABLE, Sector.FRONT, Motion.LATERAL,
                enemy, ENEMY_X, ENEMY_Y, 1f, Doctrine.HOLD, 0, 4, 0, 1,
                ContactInitiative.PROSECUTE);
        return enemy;
    }
}
