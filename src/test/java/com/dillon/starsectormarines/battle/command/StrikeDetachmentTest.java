package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.RescueCommandFacts.Emplacement;
import com.dillon.starsectormarines.battle.command.RescueCommandFacts.KnownBore;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrikeDetachmentTest {
    private static final int LIFT_X = 50, LIFT_Y = 50;
    private static final List<Emplacement> HARDPOINTS = List.of(new Emplacement(LIFT_X + 5, LIFT_Y));
    private static final BiPredicate<Integer, Integer> NOWHERE = (x, y) -> false;
    private static final KnownBore NEAR = new KnownBore("1", false, 60, 50, true);
    private static final KnownBore FAR = new KnownBore("2", false, 150, 50, true);
    private static final KnownBore MOTHER = new KnownBore("mother", true, 200, 50, true);

    @Test
    void opportunisticSendsOneCapableSquadAtTheBoreNearestTheLiftAndKeepsIt() {
        StrikeDetachment strike = hardened(HivePosture.OPPORTUNISTIC);
        List<CommandSquadState> free = List.of(squad(1, false), squad(2, true), squad(3, false));
        strike.update(HELD, List.of(FAR, NEAR), HARDPOINTS, LIFT_X, LIFT_Y, free, 5, 3, NOWHERE);
        assertEquals("1", strike.target().key());
        assertEquals(Set.of(2), strike.members(), "the satchel carrier goes");
        strike.update(HELD + 30, List.of(FAR, NEAR), HARDPOINTS, LIFT_X, LIFT_Y, free, 5, 3, NOWHERE);
        assertEquals(Set.of(2), strike.members(), "membership is stable");
        assertEquals(1, strike.decisions().size());
    }

    @Test
    void aClosingGateRecallsAndHoldsTheDetachmentHomeForTheCooldown() {
        StrikeDetachment strike = hardened(HivePosture.OPPORTUNISTIC);
        List<CommandSquadState> free = List.of(squad(1, true), squad(2, true));
        strike.update(HELD, List.of(NEAR), HARDPOINTS, LIFT_X, LIFT_Y, free, 4, 3, NOWHERE);
        assertEquals(1, strike.members().size());
        // A holdout's people come out: the cohort now needs one more squad than the force can spare.
        strike.update(HELD + 30, List.of(NEAR), HARDPOINTS, LIFT_X, LIFT_Y, free, 4, 4, NOWHERE);
        assertNull(strike.target());
        assertEquals(StrikeDetachment.Kind.RECALLED, strike.decisions().get(1).kind());
        strike.update(HELD + 60, List.of(NEAR), HARDPOINTS, LIFT_X, LIFT_Y, free, 4, 3, NOWHERE);
        assertNull(strike.target(), "the gate reopened inside the cooldown");
        strike.update(HELD + 30 + StrikeDetachment.RECOMMIT_COOLDOWN_TICKS, List.of(NEAR), HARDPOINTS,
                LIFT_X, LIFT_Y, free, 4, 3, NOWHERE);
        assertEquals("1", strike.target().key());
    }

    @Test
    void theDetachmentEndsWhenItsBoreCollapses() {
        StrikeDetachment strike = hardened(HivePosture.OPPORTUNISTIC);
        List<CommandSquadState> free = List.of(squad(1, true));
        strike.update(HELD, List.of(NEAR), HARDPOINTS, LIFT_X, LIFT_Y, free, 4, 3, NOWHERE);
        KnownBore collapsed = new KnownBore("1", false, 60, 50, false);
        strike.update(HELD + 30, List.of(collapsed), HARDPOINTS, LIFT_X, LIFT_Y, free, 4, 3, NOWHERE);
        assertNull(strike.target());
        assertEquals(StrikeDetachment.Kind.TARGET_COLLAPSED, strike.decisions().get(1).kind());
    }

    @Test
    void purgeGoesForTheMotherWithTwoSquadsOnAnUnhardenedLift() {
        StrikeDetachment strike = new StrikeDetachment(HivePosture.PURGE);
        List<CommandSquadState> free = List.of(squad(1, true), squad(2, true), squad(3, false));
        strike.update(10, List.of(NEAR, MOTHER), List.of(), LIFT_X, LIFT_Y, free, 3, 3, NOWHERE);
        assertEquals("mother", strike.target().key());
        assertEquals(StrikeDetachment.MOTHER_SQUADS, strike.members().size());
    }

    @Test
    void evacuateOnlyEngagesABoreOnTheWalkOut() {
        StrikeDetachment strike = new StrikeDetachment(HivePosture.EVACUATE);
        List<CommandSquadState> free = List.of(squad(1, true));
        strike.update(10, List.of(NEAR, FAR), List.of(), LIFT_X, LIFT_Y, free, 4, 3, NOWHERE);
        assertNull(strike.target(), "nothing is on the route");
        strike.update(40, List.of(NEAR, FAR), List.of(), LIFT_X, LIFT_Y, free, 4, 3, (x, y) -> x == 150);
        assertEquals("2", strike.target().key(), "the far bore is the one on the walk");
        strike.update(70, List.of(NEAR, FAR), List.of(), LIFT_X, LIFT_Y, free, 4, 3, NOWHERE);
        assertNull(strike.target(), "off the route, it comes back");
    }

    private static final int HELD = ReadinessGate.HARDENED_WINDOW_TICKS;

    /** A detachment whose lift has had built defences and no losses for exactly the window by tick {@link #HELD}. */
    private static StrikeDetachment hardened(HivePosture posture) {
        StrikeDetachment strike = new StrikeDetachment(posture);
        strike.observeLift(0, List.of(), HARDPOINTS,
                LIFT_X, LIFT_Y);
        return strike;
    }

    private static CommandSquadState squad(int id, boolean demolition) {
        return new CommandSquadState(id, Faction.MARINE, 6, 10f + id, 10f, 10 + id, 10, 0, UnitRole.COMBATANT,
                false, false, false, null, null, null, null, null, 0, 0, 0, 0, 0, null, null, null, 0,
                new int[0], new int[0], new int[0], null, demolition, false, false);
    }
}
