package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.ruins.PowerGraph.Branch;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerelictPowerTest {

    private static RuinsDelve.Layer layer(int index, int left, int right) {
        return new RuinsDelve.Layer("ruins.layer." + (index + 1), index, null, new Area(left, 0, right, 50), left,
                right, List.of(), List.of(), null);
    }

    @Test
    void aZoneIsFedByTheLayerItsNodeStandsInElseTheNearest() {
        List<RuinsDelve.Layer> layers = List.of(layer(0, 10, 39), layer(1, 40, 69), layer(2, 70, 99));
        assertEquals(1, DerelictPower.junctionOf(50, 20, layers));
        assertEquals(2, DerelictPower.junctionOf(110, 20, layers), "the vault's zone, past the last layer");
        assertEquals(0, DerelictPower.junctionOf(2, 20, layers));
    }

    @Test
    void aCutBranchDarkensItsZoneUntilItIsThrownBack() {
        SecurityNetwork network = new SecurityNetwork()
                .add(new SecurityZone(0, 10, 10, 8))
                .add(new SecurityZone(1, 30, 10, 8))
                .link(0, 1);
        Map<Integer, Integer> junctions = Map.of(0, 0, 1, 1);
        network.stir(0, 1);
        assertEquals(SecurityZone.State.AWAKE, network.zone(0).state());

        Branch[] position = {Branch.STORE, Branch.ONWARD};
        assertEquals(List.of(0), DerelictPower.apply(network, junctions, j -> position[j]));
        SecurityZone dark = network.zone(0);
        assertEquals(SecurityZone.State.DORMANT, dark.state());
        assertFalse(dark.online());

        // Dark, it hears nothing: no stir, no relay, no call.
        network.stir(0, 2);
        assertEquals(SecurityZone.State.DORMANT, dark.state());
        network.stir(1, 3);
        assertEquals(SecurityZone.State.DORMANT, dark.state());
        network.breachVault(4);
        assertEquals(SecurityZone.State.DORMANT, dark.state());

        position[0] = Branch.ONWARD;
        assertEquals(List.of(0), DerelictPower.apply(network, junctions, j -> position[j]));
        assertTrue(dark.online());
        network.settle(5);
        assertEquals(SecurityZone.State.AWAKE, dark.state(), "back on power, the contingency calls it");
    }
}
