package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.decision.Reflex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The lance's reflex order, written down — the same law as
 * {@code InfantryReflexOrderTest} states for marines, over a chain that has one
 * entry today.
 *
 * <p>Pinning a one-entry list looks like ceremony and is not. The whole reason
 * the mech dispatcher declares a chain at all is that the seam exists on both
 * arms; the moment a second chassis-level interrupt is written, where it sits
 * relative to the player's move order is a decision somebody has to make on
 * purpose, and this is where that decision gets recorded.
 */
public class MechReflexOrderTest {

    @Test
    public void thePlayersMoveOrderIsTheLancesOnlyReflexAndComesFirst() {
        assertEquals(List.of("PLAYER_LOCOMOTION"),
                MechReflexes.CHAIN.stream().map(Reflex::name).toList(),
                "a player's per-chassis move outranks doctrine; adding a second entry "
                        + "is a priority decision, not an append");
    }
}
