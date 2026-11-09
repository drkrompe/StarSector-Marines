package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.ruins.PowerGraph.Branch;
import com.dillon.starsectormarines.battle.ruins.PowerGraph.Consumer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerGraphTest {

    private static final Consumer STORE_1 = new Consumer("store-1", 0, Branch.STORE);
    private static final Consumer STORE_2 = new Consumer("store-2", 1, Branch.STORE);
    private static final Consumer VAULT = new Consumer("vault", 1, Branch.ONWARD);

    private static PowerGraph graph() {
        return new PowerGraph(2, List.of(Branch.ONWARD, Branch.ONWARD), List.of(STORE_1, STORE_2, VAULT));
    }

    @Test
    void aColdHallPowersNothingAndARestartPowersWhatIsFed() {
        PowerGraph graph = graph();
        assertTrue(graph.fed(VAULT));
        assertFalse(graph.powered(VAULT), "fed, but the hall is cold");

        graph.restart();
        assertTrue(graph.powered(VAULT));
        assertFalse(graph.powered(STORE_1), "its junction feeds the way on");
    }

    @Test
    void throwingAJunctionReFeedsAtOnce() {
        PowerGraph graph = graph();
        graph.restart();

        graph.throwJunction(1, Branch.STORE);

        assertTrue(graph.powered(STORE_2));
        assertFalse(graph.powered(VAULT), "the vault's junction now feeds its store");
    }

    @Test
    void anOverloadedHallPowersNothingUntilLoadIsShed() {
        PowerGraph graph = new PowerGraph(1, List.of(Branch.STORE, Branch.ONWARD), List.of(STORE_1, STORE_2, VAULT));
        graph.restart();
        assertTrue(graph.overloaded(), "store 1 and the vault on a hall that runs one");
        assertFalse(graph.powered(STORE_1));
        assertFalse(graph.powered(VAULT));

        graph.throwJunction(0, Branch.ONWARD);
        assertTrue(graph.powered(VAULT), "shedding the store brings the vault back without a second restart");
    }

    @Test
    void aConsumerHangsOnAJunctionThatExists() {
        assertThrows(IllegalArgumentException.class, () -> new PowerGraph(1, List.of(Branch.ONWARD),
                List.of(new Consumer("stray", 3, Branch.STORE))));
    }
}
