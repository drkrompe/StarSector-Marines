package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.CommandSquadState;
import com.dillon.starsectormarines.battle.command.RescueCommandFacts.KnownBore;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The company's readiness gate and strike detachment ({@link CordonStrikes}). */
class CordonStrikesTest {

    private static final int HELD = CordonStrikes.HELD_WINDOW_TICKS;

    @Test
    void eachPostureReadsTheGateItsOwnWay() {
        assertFalse(CordonStrikes.gate(CordonPosture.HOLD, HELD, 5, 0, 3, false).open(), "Hold never raids");
        assertTrue(CordonStrikes.gate(CordonPosture.OPPORTUNISTIC, HELD, 5, 2, 3, false).open());
        assertFalse(CordonStrikes.gate(CordonPosture.OPPORTUNISTIC, HELD - 1, 5, 2, 3, false).open(), "not held long");
        assertFalse(CordonStrikes.gate(CordonPosture.OPPORTUNISTIC, HELD, 5, 2, 3, true).open(), "not in a tide");
        assertFalse(CordonStrikes.gate(CordonPosture.OPPORTUNISTIC, HELD, 1, 2, 3, false).open(), "calls uncovered");
        assertTrue(CordonStrikes.gate(CordonPosture.PUSH, 0, 5, 2, 3, true).open(), "Push waives the held window");
        assertFalse(CordonStrikes.gate(CordonPosture.PUSH, 0, 5, 2, 0, false).open(), "nobody can hurt a bore");
    }

    @Test
    void theBorePressingHardestIsTheOneInFrontOfTheWorstOffRedoubt() {
        List<CordonRadio> radio = List.of(radio(1, 100, false, false), radio(2, 200, false, true));
        List<KnownBore> bores = List.of(bore("1", 100), bore("2", 200), new KnownBore("3", false, 200, 260, false));
        assertEquals("2", CordonStrikes.pressingHardest(bores, radio, 100, 100).key(), "the bled redoubt's bore");
        List<CordonRadio> quiet = List.of(radio(1, 100, false, false), radio(2, 200, false, false));
        assertEquals("2", CordonStrikes.pressingHardest(bores, quiet, 210, 100).key(), "all alike: the one in reach");
        assertNull(CordonStrikes.pressingHardest(List.of(new KnownBore("3", false, 200, 260, false)), radio, 0, 0),
                "a collapsed bore is no target");
    }

    @Test
    void aStrikeKeepsItsSquadUntilTheGateClosesThenWaitsBeforeAnother() {
        CordonStrikes strikes = new CordonStrikes(CordonPosture.OPPORTUNISTIC);
        List<CordonRadio> radio = List.of(radio(1, 100, false, false));
        List<KnownBore> bores = List.of(bore("1", 100));
        List<CommandSquadState> owned = List.of(squad(1, true), squad(2, false), squad(3, false));
        strikes.update(HELD, bores, radio, owned, owned, 0, true, false);
        assertEquals(List.of(1), List.copyOf(strikes.members()), "the satchel squad goes");
        assertEquals(List.of("strike_committed_1"), strikes.drainBeats());
        strikes.update(HELD + 30, bores, radio, List.of(), owned, 0, true, false);
        assertEquals(List.of(1), List.copyOf(strikes.members()), "the same squad stays on it");
        strikes.update(HELD + 60, bores, radio, List.of(), owned, 0, true, true);
        assertTrue(strikes.members().isEmpty(), "a tide recalls it");
        assertEquals(List.of("strike_recalled_1"), strikes.drainBeats());
        strikes.update(HELD + 90, bores, radio, owned, owned, 0, true, false);
        assertTrue(strikes.members().isEmpty(), "and nobody is sent again inside the cooldown");
    }

    private static CordonRadio radio(int number, int x, boolean unsettled, boolean bled) {
        return new CordonRadio(number, x, 180, true, unsettled, unsettled, bled, false, false);
    }

    private static KnownBore bore(String key, int x) {
        return new KnownBore(key, false, x, 260, true);
    }

    private static CommandSquadState squad(int id, boolean demolition) {
        return new CommandSquadState(id, Faction.MARINE, 6, 10f + id, 10f, 10 + id, 10, 0, UnitRole.COMBATANT,
                false, false, false, null, null, null, null, null, 0, 0, 0, 0, 0, null, null, null, 0,
                new int[0], new int[0], new int[0], null, demolition, false, false);
    }
}
