package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for the cross-squad half of an attack move: who fixes, who moves,
 * and when the answer is "nobody is cooperating".
 */
public class AssaultCoordinationSystemTest {

    private static final int W = 64;
    private static final int H = 32;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Squad attackMoveSquad(BattleSimulation sim, int size, int x, int y) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            members.add(sim.spawn(new EntitySpec("m" + squadId + "-" + i,
                    Faction.MARINE, UnitType.MARINE, x, y + i).squad(squad.id)));
        }
        squad.leaderId = members.get(0);
        squad.aliveMembers = size;
        squad.originalSize = size;
        squad.centroidX = x + 0.5f;
        squad.centroidY = y + 0.5f;
        squad.assignedObjective = ObjectiveAssignment.attackMove(squad.id, 50, 15);
        return squad;
    }

    /** Stamps the belief-derived reading the coordination system consumes. */
    private static void seeContact(Squad squad, long contact, int engageableTeams,
                                   int engageableMembers) {
        squad.contactPicture = new SquadContactPicture(1,
                SquadContactPicture.Posture.ADVANCING, 1f, 0f, 1, 1, 1f, 4,
                SquadContactPicture.ForceBalance.FAVORABLE,
                SquadContactPicture.Sector.FRONT,
                SquadContactPicture.Motion.LATERAL, contact, 30, 15, 1f,
                SquadContactPicture.Doctrine.HOLD, engageableMembers, 4,
                engageableTeams, 2,
                SquadContactPicture.ContactInitiative.RECEIVE);
    }

    private static long defender(BattleSimulation sim, int x, int y) {
        return sim.spawn(new EntitySpec("d" + x + "-" + y, Faction.DEFENDER,
                UnitType.MARINE, x, y));
    }

    @Test
    public void squadsSharingOneContactSplitIntoBaseOfFireAndManeuver() {
        BattleSimulation sim = openSim();
        long contact = defender(sim, 30, 15);
        Squad shooter = attackMoveSquad(sim, 4, 20, 14);
        Squad mover = attackMoveSquad(sim, 4, 20, 22);
        seeContact(shooter, contact, 2, 4);
        seeContact(mover, contact, 0, 0);

        new AssaultCoordinationSystem(sim.getRoster()).tick(1);

        assertEquals(SquadAssaultPicture.Role.BASE_OF_FIRE, shooter.assaultPicture.role(),
                "the squad that can actually shoot holds the enemy");
        assertEquals(SquadAssaultPicture.Role.MANEUVER, mover.assaultPicture.role(),
                "the squad that cannot shoot from here is the one that moves");
        assertEquals(shooter.id, mover.assaultPicture.partnerSquadId());
        assertEquals(contact, mover.assaultPicture.sharedContactId());
        assertTrue(mover.assaultPicture.axisX() > 0.9f,
                "axis runs from the fixing squad toward the contact");
    }

    @Test
    public void aLoneSquadOnAContactIsNotCooperatingWithAnybody() {
        BattleSimulation sim = openSim();
        long contact = defender(sim, 30, 15);
        Squad alone = attackMoveSquad(sim, 4, 20, 14);
        seeContact(alone, contact, 2, 4);

        new AssaultCoordinationSystem(sim.getRoster()).tick(1);

        assertSame(SquadAssaultPicture.NONE, alone.assaultPicture,
                "one squad is the degenerate case, not a group of one");
    }

    @Test
    public void squadsLookingAtDifferentEnemiesAreNotAGroup() {
        BattleSimulation sim = openSim();
        long first = defender(sim, 30, 15);
        long second = defender(sim, 30, 25);
        Squad a = attackMoveSquad(sim, 4, 20, 14);
        Squad b = attackMoveSquad(sim, 4, 20, 24);
        seeContact(a, first, 2, 4);
        seeContact(b, second, 2, 4);

        new AssaultCoordinationSystem(sim.getRoster()).tick(1);

        assertSame(SquadAssaultPicture.NONE, a.assaultPicture);
        assertSame(SquadAssaultPicture.NONE, b.assaultPicture,
                "proximity is not cooperation — a shared believed contact is");
    }

    @Test
    public void nobodyManeuversAroundAnEnemyNoOneCanHold() {
        BattleSimulation sim = openSim();
        long contact = defender(sim, 30, 15);
        Squad a = attackMoveSquad(sim, 4, 20, 14);
        Squad b = attackMoveSquad(sim, 4, 20, 22);
        seeContact(a, contact, 0, 0);
        seeContact(b, contact, 0, 0);

        new AssaultCoordinationSystem(sim.getRoster()).tick(1);

        assertSame(SquadAssaultPicture.NONE, a.assaultPicture);
        assertSame(SquadAssaultPicture.NONE, b.assaultPicture,
                "sending a squad round the flank of an enemy nobody is fixing is "
                        + "worse than both of them closing");
    }

    @Test
    public void anExactTieAlwaysPicksTheSameSquad() {
        // Determinism is a hard requirement: the commander evidence harness
        // replays each fixture twice and compares the traces byte for byte.
        for (int run = 0; run < 4; run++) {
            BattleSimulation sim = openSim();
            long contact = defender(sim, 30, 15);
            Squad a = attackMoveSquad(sim, 4, 20, 14);
            Squad b = attackMoveSquad(sim, 4, 20, 22);
            seeContact(a, contact, 2, 4);
            seeContact(b, contact, 2, 4);

            new AssaultCoordinationSystem(sim.getRoster()).tick(1);

            assertEquals(SquadAssaultPicture.Role.BASE_OF_FIRE, a.assaultPicture.role(),
                    "the lower squad id wins an exact tie, every run");
            assertEquals(SquadAssaultPicture.Role.MANEUVER, b.assaultPicture.role());
        }
    }

    @Test
    public void aDeadSharedContactDropsTheRoleRatherThanStranding() {
        BattleSimulation sim = openSim();
        long contact = defender(sim, 30, 15);
        Squad a = attackMoveSquad(sim, 4, 20, 14);
        Squad b = attackMoveSquad(sim, 4, 20, 22);
        seeContact(a, contact, 2, 4);
        seeContact(b, contact, 0, 0);
        AssaultCoordinationSystem system = new AssaultCoordinationSystem(sim.getRoster());
        system.tick(1);
        assertNotEquals(SquadAssaultPicture.Role.NONE, b.assaultPicture.role());

        // Liveness is HP, not registry membership — dropping the dense slot
        // alone leaves isAliveById answering true.
        sim.world().setHp(contact, 0f);
        system.tick(2);

        assertSame(SquadAssaultPicture.NONE, a.assaultPicture);
        assertSame(SquadAssaultPicture.NONE, b.assaultPicture,
                "a squad must not keep maneuvering around a corpse");
    }

    @Test
    public void aSquadWithoutTheOrderIsNeverGivenARole() {
        BattleSimulation sim = openSim();
        long contact = defender(sim, 30, 15);
        Squad attacking = attackMoveSquad(sim, 4, 20, 14);
        Squad other = attackMoveSquad(sim, 4, 20, 22);
        other.assignedObjective = null;
        seeContact(attacking, contact, 2, 4);
        seeContact(other, contact, 2, 4);

        new AssaultCoordinationSystem(sim.getRoster()).tick(1);

        assertSame(SquadAssaultPicture.NONE, other.assaultPicture,
                "coordination belongs to the order, not to whoever is nearby");
        assertSame(SquadAssaultPicture.NONE, attacking.assaultPicture,
                "which leaves the attacker alone on its contact");
    }
}
