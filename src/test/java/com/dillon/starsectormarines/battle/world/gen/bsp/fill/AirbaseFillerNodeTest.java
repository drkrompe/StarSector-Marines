package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which of the two airbases in a city is a base anybody flies from.
 *
 * <p>The distinction is one argument wide and silent both ways round, which is
 * why it is pinned rather than left to the fillers' prose. A compound that
 * publishes no {@code AIRBASE} node keeps its berths, its aircraft and its
 * markings while no commander can see it is a field, and its sorties fly
 * pre-loaded — the spawner that loading on the ground exists to replace. A
 * block-sized civil pad that starts publishing one turns a landing site in the
 * middle of a city into something the attacker has to capture, which is a
 * different game.
 */
class AirbaseFillerNodeTest {

    private static final int WIDTH = 40;
    private static final int HEIGHT = 40;

    @Test
    void theCompoundAirbaseIsAFieldWithANodeOnIt() {
        GenContext ctx = blankCity();
        BlockLeaf seed = new BlockLeaf(2, 2, WIDTH - 3, HEIGHT - 3, false);
        seed.kind = BlockKind.AIRBASE_COMPOUND;
        new AirbaseCompoundFiller().fill(new Compound(BlockKind.AIRBASE_COMPOUND,
                seed, List.of(seed), new IdentityHashMap<>(), BiomeKind.CITY), ctx);

        assertEquals(List.of(TacticalNode.Kind.AIRBASE),
                ctx.tactical.stream().map(node -> node.kind).toList(),
                "the compound airbase is a place a commander can be told to hold");
        assertTrue(ctx.landingPads.stream().anyMatch(
                        pad -> pad.purpose == LandingPad.Purpose.GARRISON_AIRFIELD),
                "and a place the air arm draws sorties from");
    }

    @Test
    void theBlockSizedPadIsALandingSiteAndNothingMore() {
        GenContext ctx = blankCity();
        BlockLeaf leaf = new BlockLeaf(2, 2, WIDTH - 3, HEIGHT - 3, false);
        leaf.kind = BlockKind.AIRBASE_PAD;
        new AirbasePadFiller().fill(leaf, ctx);

        assertTrue(ctx.tactical.isEmpty(),
                "a civil pad is not a base, so nothing gates on holding it");
        assertFalse(ctx.landingPads.isEmpty(), "it is still somewhere to put down");
        assertFalse(ctx.landingPads.stream().anyMatch(
                        pad -> pad.purpose == LandingPad.Purpose.GARRISON_AIRFIELD),
                "and nothing is based on it");
    }

    private static GenContext blankCity() {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
                topology.setRoomPurpose(x, y, RoomPurpose.GENERIC);
            }
        }
        return new GenContext(grid, topology, new Random(7L), WIDTH, HEIGHT, 7L);
    }
}
