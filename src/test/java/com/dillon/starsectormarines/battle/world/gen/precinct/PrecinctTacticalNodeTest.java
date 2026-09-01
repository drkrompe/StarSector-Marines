package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.PrecinctWardStage;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A garrison is somewhere a battle can be about, not just somewhere buildings
 * are.
 *
 * <p>Without tactical nodes an installation has walls, roofs, a motor pool and
 * two runways, and nothing to fight over: no objectives, no garrison spawns,
 * nothing for the commander tier to reason about. On a map with settlements
 * around it that hides behind their points of interest; on a remote map, where
 * the installation is the only place, the whole map measured zero.
 */
class PrecinctTacticalNodeTest {

    private static final int W = 560;
    private static final int H = 336;

    private static GenContext ward(List<Precinct> precincts, int w, int h) {
        GrownTrunkPlan.Grown grown = GrownTrunkPlan.grow(w, h, new Random(42L),
                precincts.stream()
                        .map(p -> new GrownTrunkPlan.Seed(p.seedX(), p.seedY(), p.growth()))
                        .toList());
        int[] allowance = PrecinctAllowance.derive(precincts, grown.owner(), w, h);
        int[][] claim =
                PrecinctClaim.byKind(precincts).assign(grown.owner(), w, h, allowance);

        GenContext ctx = new GenContext(new NavigationGrid(w, h), new CellTopology(w, h),
                new Random(42L), w, h, 42L);
        ctx.put(BspKeys.PRECINCTS, PrecinctPlan.authored(precincts));
        ctx.put(BspKeys.PRECINCT_CLAIM, claim);
        ctx.put(BspKeys.PRECINCT_ROAD, grown.owner());
        new PrecinctWardStage().run(ctx);
        return ctx;
    }

    private static List<Precinct> oneGarrison() {
        return List.of(
                Precinct.garrison("garrison", W / 3, H / 2,
                        GrownTrunkPlan.Profile.hamlet(), FortressProgram.garrison()),
                Precinct.settlement("town", 2 * W / 3, H / 3,
                        GrownTrunkPlan.Profile.town()));
    }

    private static long count(GenContext ctx, TacticalNode.Kind kind) {
        return ctx.tactical.stream().filter(n -> n.kind == kind).count();
    }

    /** The place has things worth taking, and they are the ones it built. */
    @Test
    void aGarrisonEmitsWhatItBuilt() {
        GenContext ctx = ward(oneGarrison(), W, H);
        assertTrue(!ctx.tactical.isEmpty(),
                "a packed garrison produced no tactical nodes at all, so nothing on the "
                        + "map is worth taking and nothing garrisons it");
        assertTrue(count(ctx, TacticalNode.Kind.BARRACKS) > 0, "no barracks to garrison");
        assertTrue(count(ctx, TacticalNode.Kind.ARMORY) > 0, "nothing worth raiding");
    }

    /**
     * A precinct garrison keeps its own command post.
     *
     * <p>This is where it differs from the conquest ward, which is packed around
     * a citadel compound seeded separately and has the keep taken out of its
     * program. Nothing else is going to provide one here.
     */
    @Test
    void aSelfContainedGarrisonHasItsOwnCommandPost() {
        assertEquals(1, count(ward(oneGarrison(), W, H), TacticalNode.Kind.COMMAND_POST),
                "a self-contained garrison has no command post, or more than one");
    }

    /** Two garrisons are two places, so they get one command post each. */
    @Test
    void twoGarrisonsGetOneCommandPostEach() {
        List<Precinct> two = List.of(
                Precinct.garrison("north", 150, 240, GrownTrunkPlan.Profile.hamlet(),
                        FortressProgram.garrison()),
                Precinct.garrison("south", 400, 90, GrownTrunkPlan.Profile.hamlet(),
                        FortressProgram.garrison()));
        assertEquals(2, count(ward(two, W, H), TacticalNode.Kind.COMMAND_POST),
                "two garrisons on one map did not come out with a command post each, "
                        + "which is the thing precincts exist to make expressible");
    }

    /** Every node belongs to the defender and stands somewhere occupiable. */
    @Test
    void everyNodeIsSomewhereADefenderCanStand() {
        GenContext ctx = ward(oneGarrison(), W, H);
        for (TacticalNode node : ctx.tactical) {
            assertEquals(Faction.DEFENDER, node.defaultGuard,
                    "a garrison emitted a node the attacker owns");
            assertTrue(ctx.grid.isWalkable(node.anchorX, node.anchorY),
                    "node " + node.kind + " at " + node.anchorX + "," + node.anchorY
                            + " stands on something nobody can occupy");
            assertTrue(!ctx.grid.isDoorway(node.anchorX, node.anchorY),
                    "node " + node.kind + " garrisons a doorway, which nothing may block");
        }
    }

    /** An airfield is a thing that can be taken to stop it flying. */
    @Test
    void anAirfieldIsOnTheMapAsSomethingToTake() {
        GenContext ctx = ward(oneGarrison(), W, H);
        assertTrue(count(ctx, TacticalNode.Kind.AIRBASE) > 0,
                "the garrison built an airfield that nothing can be ordered to attack");
    }
}
