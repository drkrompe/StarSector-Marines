package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.SquadDirective;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How much of the mobile pool a threatened defender actually commits.
 *
 * <p>The fixed two-per-track cap was written for a starting force of a few
 * patrols. A live battle showed it at sixty-two: three threatened tracks drew
 * six squads and fifty-six sat on a home-track hold while the base was taken
 * compound by compound. These pin both ends of the range — a three-patrol pool
 * behaves exactly as it did, a sixty-patrol pool commits most of itself, and
 * the control flag restores the old numbers on the same frame.
 *
 * <p>Measured on the sixty-patrol frame: caps 30/8/8 for believed contacts of
 * 4/1/1, forty-five committed as 30/8/7, fifteen held. The same frame with the
 * flag off commits six and holds fifty-four.
 */
class ConquestDefenderCommandScalingTest {

    private static final int W = 30;
    private static final int H = 60;

    private final boolean scaledResponseAtStart =
            ConquestDefenderCommand.isScaledResponseEnabled();

    @AfterEach
    void restoreScaledResponse() {
        ConquestDefenderCommand.setScaledResponseForEvidence(
                scaledResponseAtStart);
    }

    @Test
    void threePatrolPoolStillSendsTwoAndHoldsOne() {
        BattleSimulation sim = openSim();
        addDefender(sim, "reporter", 5, 10);
        addDefender(sim, "first", 5, 46);
        addDefender(sim, "second", 5, 48);
        addDefender(sim, "third", 5, 50);
        addContact(sim, "contact", 5, 14);
        sim.advance(BattleSimulation.TICK_DT);

        CommandPlan<ConquestFrontSnapshot> plan = plan(sim);

        assertEquals(3, plan.commandPoolSize());
        assertEquals(2, responders(plan, 0),
                "a small pool answers exactly as it did before the share rule");
        assertEquals(1, plan.reserveCount());
        assertEquals(2, plan.detail().track(0).responderCap(),
                "the fixed cap survives as the floor");
    }

    @Test
    void largePoolCommitsMostOfItselfWeightedByBelievedContacts() {
        BattleSimulation sim = threeTrackThreat();

        CommandPlan<ConquestFrontSnapshot> plan = plan(sim);

        assertEquals(4, plan.detail().track(0).knownHostileContacts());
        assertEquals(1, plan.detail().track(1).knownHostileContacts());
        assertEquals(1, plan.detail().track(2).knownHostileContacts());
        assertEquals(60, plan.commandPoolSize());

        int committed = responders(plan, 0) + responders(plan, 1)
                + responders(plan, 2);
        assertTrue(committed >= 30,
                "a sixty-patrol pool must answer with more than six squads, was "
                        + committed + " split " + responders(plan, 0) + "/"
                        + responders(plan, 1) + "/" + responders(plan, 2));
        assertEquals(60 - committed, plan.reserveCount());
        assertTrue(plan.reserveCount() >= 15,
                "a quarter of the pool stays back, was " + plan.reserveCount());
        assertTrue(responders(plan, 0) > responders(plan, 1)
                        && responders(plan, 0) > responders(plan, 2),
                "the heaviest threat draws the most");
        for (int track = 0; track < 3; track++) {
            assertTrue(responders(plan, track)
                            <= plan.detail().track(track).responderCap(),
                    "track " + track + " exceeded its published cap");
        }
    }

    @Test
    void controlFlagRestoresTheFixedTwoPerTrackCap() {
        BattleSimulation sim = threeTrackThreat();
        ConquestDefenderCommand.setScaledResponseForEvidence(false);

        CommandPlan<ConquestFrontSnapshot> plan = plan(sim);

        assertEquals(2, responders(plan, 0));
        assertEquals(2, responders(plan, 1));
        assertEquals(2, responders(plan, 2));
        assertEquals(54, plan.reserveCount());
    }

    /**
     * Sixty free patrols spread evenly over the three tracks, and a believed
     * contact weight of 4/1/1 across them. Each track's reporter stands in its
     * own lane, so the alert is faction-honest rather than injected. The rest
     * stand deeper than {@code UnitType.MILITIA}'s own sight, or they see the
     * contacts themselves and hand execution to squad doctrine instead of
     * being the free pool this is measuring.
     */
    private static BattleSimulation threeTrackThreat() {
        BattleSimulation sim = openSim();
        addDefender(sim, "left-reporter", 5, 10);
        addDefender(sim, "centre-reporter", 15, 10);
        addDefender(sim, "right-reporter", 25, 10);
        for (int i = 0; i < 10; i++) {
            addDefender(sim, "left-front-" + i, 3, 45 + i);
            addDefender(sim, "left-rear-" + i, 7, 45 + i);
            addDefender(sim, "centre-front-" + i, 13, 45 + i);
            addDefender(sim, "centre-rear-" + i, 17, 45 + i);
            addDefender(sim, "right-front-" + i, 23, 45 + i);
            addDefender(sim, "right-rear-" + i, 27, 45 + i);
        }
        addContact(sim, "left-contact-a", 4, 13);
        addContact(sim, "left-contact-b", 5, 13);
        addContact(sim, "left-contact-c", 6, 13);
        addContact(sim, "left-contact-d", 5, 14);
        addContact(sim, "centre-contact", 15, 13);
        addContact(sim, "right-contact", 25, 13);
        sim.advance(BattleSimulation.TICK_DT);
        return sim;
    }

    private static int responders(CommandPlan<ConquestFrontSnapshot> plan,
                                  int track) {
        int result = 0;
        for (SquadDirective directive : plan.detail().directives()) {
            if (directive.effectiveTrack() == track
                    && directive.assignmentKind() == AssignmentKind.DEFEND_TRACK) {
                result++;
            }
        }
        return result;
    }

    private static CommandPlan<ConquestFrontSnapshot> plan(BattleSimulation sim) {
        ConquestTrackLayout tracks = new ConquestTrackLayout(
                TraversalAxis.SOUTH_TO_NORTH, W, H);
        ConquestDefenderCommand command = new ConquestDefenderCommand(tracks,
                ConquestDefenderStartingForce.capture(sim, tracks));
        CommandTopology topology = CommandTopology.freeze(sim);
        ConquestCommandFrame frame = ConquestCommandDisclosure.INSTANCE.freeze(
                sim, command.faction(), topology,
                new CommandAssignmentSnapshot(Map.of()));
        return command.plan(frame);
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static void addContact(BattleSimulation sim, String name,
                                   int x, int y) {
        sim.spawn(new EntitySpec(name, Faction.MARINE, UnitType.MARINE, x, y)
                .moveSpeed(0f).health(10_000f));
    }

    private static Squad addDefender(BattleSimulation sim, String name,
                                     int x, int y) {
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        long member = sim.spawn(new EntitySpec(name, Faction.DEFENDER,
                UnitType.MILITIA, x, y).role(UnitRole.PATROL)
                .squad(squadId).moveSpeed(0f));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = member;
        squad.centroidX = x;
        squad.centroidY = y;
        return squad;
    }
}
