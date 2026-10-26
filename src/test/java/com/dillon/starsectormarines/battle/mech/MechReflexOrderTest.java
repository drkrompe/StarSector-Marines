package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.decision.Reflex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The lance's reflex order, written down — the same law as
 * {@code InfantryReflexOrderTest} states for marines.
 *
 * <p>Pinning a one-entry list looks like ceremony and is not. The whole reason
 * the mech dispatcher declares a chain at all is that the seam exists on both
 * arms; the moment a second chassis-level interrupt is written, where it sits
 * relative to the player's move order is a decision somebody has to make on
 * purpose, and this is where that decision gets recorded.
 */
public class MechReflexOrderTest {

    @Test
    public void thePlayersMoveOrderComesFirstTheCrippledChassisSecondTheMeleeStandoffThirdAndTheAttachmentLast() {
        assertEquals(List.of("PLAYER_LOCOMOTION", "CRIPPLED_CHASSIS", "MELEE_STANDOFF", "ATTACHED"),
                MechReflexes.CHAIN.stream().map(Reflex::name).toList(),
                "a player's per-chassis move outranks doctrine (and releases itself for "
                        + "a crippled chassis); a crippled chassis outranks every plan step and cannot step back; "
                        + "a walking mech steps back from a charging Rav before its plan step walks it in; "
                        + "an attached mech keeps its slot by its squad only when none of those claims the tick; "
                        + "adding an entry is a priority decision, not an append");
    }
}
