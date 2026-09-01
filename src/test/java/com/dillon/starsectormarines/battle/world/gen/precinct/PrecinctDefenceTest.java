package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What actually makes a place hard to take is what shoots back.
 *
 * <p>A wall is a delay: it decides where the attack goes in and what a breach
 * costs, and then it is over. The emplacements decide whether crossing the
 * ground in front of it is survivable, which is the part a mission is tuning
 * when it says a garrison should be a problem for the force being sent.
 *
 * <p>Asked of {@link PrecinctDefence} on a hand-built claim rather than of a
 * generated map: the mechanism is "seed the gates, seed the interior, place what
 * the fortification asked for", and a rectangle with a road through it is the
 * smallest thing that can show all three.
 */
class PrecinctDefenceTest {

    private static final int W = 140;
    private static final int H = 90;
    /** The claim: a rectangle, big enough to hold a citadel's worth of guns. */
    private static final int LEFT = 40, RIGHT = 99, BOTTOM = 20, TOP = 69;
    /** The road: a band clean across the map, so it leaves by two ways. */
    private static final int ROAD_LOW = 43, ROAD_HIGH = 45;

    private record Built(GenContext ctx, int[][] claim, int[][] road,
                         PrecinctDefence.Result result) { }

    private static Built defend(Fortification fortification) {
        return defend(fortification, 42L, true, false);
    }

    private static Built defend(Fortification fortification, long seed, boolean crossed) {
        return defend(fortification, seed, crossed, false);
    }

    /**
     * The fixture: an open field, a rectangular claim, a road across it, and the
     * wall the ward would have stamped.
     *
     * @param crossed when false the claim has no road through it, so the
     *                fortification has no gates to seed from and the same
     *                placement scatters — the control for
     *                {@link #theWayInIsWatched()}
     * @param orphan  when true, a single walkable cell far from the precinct is
     *                sealed off from everything else — the condition in
     *                {@link #anOrphanPocketElsewhereDoesNotDisarmTheGarrison()}
     */
    private static Built defend(Fortification fortification, long seed, boolean crossed,
                                boolean orphan) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.GRASS);
            }
        }
        int[][] claim = filled(GrownTrunkPlan.UNOWNED);
        int[][] road = filled(GrownTrunkPlan.UNOWNED);
        for (int x = LEFT; x <= RIGHT; x++) {
            for (int y = BOTTOM; y <= TOP; y++) claim[x][y] = 0;
        }
        for (int x = 0; x < W; x++) {
            for (int y = ROAD_LOW; y <= ROAD_HIGH; y++) road[x][y] = 0;
        }

        // The wall, stamped the way the ward stamps it, because the placer
        // validates footprints against the grid: with the wall still down a
        // perimeter post happily straddles the line it is meant to sit behind.
        // Stamped from the real road either way, so the control differs from
        // the case in the seeding alone and not in the geometry.
        boolean[][] wall = PrecinctBoundary.wall(claim, road, 0, W, H, fortification.gates());
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                if (!wall[x][y]) continue;
                grid.setWalkable(x, y, false);
                grid.setWallHp(x, y, fortification.wallHp());
                topology.setWall(x, y, true);
            }
        }

        if (orphan) {
            // A cell walled in on all four sides, nowhere near the garrison and
            // nothing to do with it. This is what an earlier fill leaves behind
            // on a real map, and it is the whole difference between a guard that
            // asks about the stamp and one that asks about the map.
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                grid.setWalkable(5 + step[0], 5 + step[1], false);
            }
        }

        GenContext ctx = new GenContext(grid, topology, new Random(seed), W, H, seed);
        Precinct precinct = Precinct.garrison("garrison", (LEFT + RIGHT) / 2, (BOTTOM + TOP) / 2,
                GrownTrunkPlan.Profile.hamlet(), FortressProgram.garrison(), fortification);
        PrecinctDefence.Result result = PrecinctDefence.stamp(ctx, precinct, claim,
                crossed ? road : filled(GrownTrunkPlan.UNOWNED), 0);
        return new Built(ctx, claim, road, result);
    }

    private static int[][] filled(int value) {
        int[][] out = new int[W][H];
        for (int[] column : out) Arrays.fill(column, value);
        return out;
    }

    /** How many guns are pointing at the ground, which is the thing that fights. */
    private static int turrets(GenContext ctx) {
        int total = 0;
        for (DefensePost post : ctx.defensePosts) total += post.turrets.size();
        return total;
    }

    /**
     * The dial reaches the guns.
     *
     * <p>Counted in turrets rather than posts on purpose: a LARGE post carries
     * several and a LIGHT one carries a single, so counting emplacements would
     * report a citadel and a picket as much closer than they are.
     */
    @Test
    void aCitadelPutsMoreGunsOnTheGroundThanAPicket() {
        int few = turrets(defend(Fortification.PICKET).ctx());
        int many = turrets(defend(Fortification.CITADEL).ctx());
        assertTrue(few > 0, "a picket placed no guns at all, so the ladder has no bottom");
        assertTrue(many >= 2 * few, "a citadel put " + many + " guns on the ground against a "
                + "picket's " + few + ", which is not the difference between a screen and a "
                + "siege");
    }

    /**
     * Every emplacement stands on the precinct that paid for it.
     *
     * <p>The placer slides an anchor that will not validate, and sliding out of
     * the claim is how a garrison's guns end up defending the field next door.
     */
    @Test
    void everyEmplacementStandsOnItsOwnPrecinct() {
        Built built = defend(Fortification.CITADEL);
        for (DefensePost post : built.ctx().defensePosts) {
            assertEquals(0, built.claim()[post.anchorX][post.anchorY],
                    "an emplacement at " + post.anchorX + "," + post.anchorY
                            + " stands outside the precinct that asked for it");
        }
    }

    /**
     * The way in is covered.
     *
     * <p>The whole reason the perimeter tiers are seeded from the gates rather
     * than scattered: a gate nobody is watching is a door.
     *
     * <p>Two readings, because either alone can pass on a placement that is not
     * doing this. The bound is what the mechanism guarantees — the seed sits one
     * setback inside the gate and the placer may slide it, so the gun lands
     * inside that sum every time. The comparison is what makes the bound worth
     * asserting: a claim this size is small enough that a scatter satisfies an
     * absolute bound often, and the first version of this test compared against
     * the <em>best</em> of six scattered runs and failed — one of them had put a
     * gun three cells from the gate by luck. Luck is not the thing being
     * measured, so the control is what a scatter does on average.
     */
    @Test
    void theWayInIsWatched() {
        Built built = defend(Fortification.CITADEL);
        List<PrecinctBoundary.Gate> open = PrecinctBoundary.openGates(
                built.claim(), built.road(), 0, W, H, Fortification.CITADEL.gates());
        assertEquals(1, open.size(), "the fixture should leave a citadel exactly one way in");
        int[] gate = centroid(open.get(0));

        int watched = nearestPost(built, gate);
        long[] seeds = {1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 42L};
        double scattered = 0;
        for (long seed : seeds) {
            scattered += nearestPost(defend(Fortification.CITADEL, seed, false), gate);
        }
        scattered /= seeds.length;
        assertTrue(watched <= 12, "the nearest gun to the gate is " + watched + " cells away, "
                + "which is further than a seed one setback in and a slide can put it");
        assertTrue(watched < scattered - 2, "the nearest gun to the gate is " + watched
                + " cells away seeded and " + Math.round(scattered) + " scattered on average, "
                + "so the gate seeding is not reaching the placement");
    }

    private static int[] centroid(PrecinctBoundary.Gate gate) {
        int gx = 0;
        int gy = 0;
        for (int[] cell : gate.cells()) {
            gx += cell[0];
            gy += cell[1];
        }
        return new int[]{gx / gate.cells().size(), gy / gate.cells().size()};
    }

    private static int nearestPost(Built built, int[] at) {
        int best = Integer.MAX_VALUE;
        for (DefensePost post : built.ctx().defensePosts) {
            int dx = post.anchorX - at[0];
            int dy = post.anchorY - at[1];
            best = Math.min(best, (int) Math.round(Math.sqrt(dx * dx + dy * dy)));
        }
        return best;
    }

    /**
     * What could not be emplaced is recorded.
     *
     * <p>The same law the buildings are held to. A garrison told to hold twenty
     * heavy posts and given room for a handful is a materially easier objective
     * than the one that was asked for, and an emplacement that was never stamped
     * leaves nothing on the finished map to notice.
     */
    @Test
    void whatCouldNotBeEmplacedIsRecorded() {
        Fortification greedy = Fortification.GARRISON.withPosts(DefensePostKind.LARGE, 20);
        Built built = defend(greedy);
        int placed = built.result().placed().getOrDefault(DefensePostKind.LARGE, 0);
        int missing = built.result().unplaced().getOrDefault(DefensePostKind.LARGE, 0);
        assertTrue(missing > 0, "twenty heavy posts fitted on one garrison, so the fixture is "
                + "not tight enough to show a shortfall");
        assertEquals(20, placed + missing, "the placement lost " + (20 - placed - missing)
                + " emplacements between asking and reporting");
    }

    /**
     * Ground stranded somewhere else is not this garrison's problem.
     *
     * <p>The placement guard has to ask whether <em>this stamp</em> strands
     * ground, not whether any ground anywhere is stranded. Those come apart the
     * moment a map carries an orphan pocket, which every real one does — and the
     * map-wide form then refuses every anchor on the map and places nothing,
     * without an exception or a log line. Measured on a generated precinct map,
     * three orphan pockets totalling 21 cells cost a citadel all fifteen of its
     * guns while every count still said it had asked for them.
     *
     * <p>Held here rather than in a test of the guard itself because this is
     * where the consequence lives: a guard is only wrong in the caller that
     * cannot place anything.
     */
    @Test
    void anOrphanPocketElsewhereDoesNotDisarmTheGarrison() {
        int whole = turrets(defend(Fortification.CITADEL, 42L, true, false).ctx());
        int orphaned = turrets(defend(Fortification.CITADEL, 42L, true, true).ctx());
        assertTrue(whole > 0, "the control placed nothing, so this measures nothing");
        assertEquals(whole, orphaned, "a citadel put " + whole + " guns on an unbroken map and "
                + orphaned + " on the same map with one cell sealed off in a far corner, so the "
                + "placement is answering a question about the map rather than about the stamp");
    }

    /** A fortification with nothing on it places nothing, and says so quietly. */
    @Test
    void aBareWallEmplacesNothing() {
        Built built = defend(new Fortification(3, 240));
        assertEquals(0, built.result().placedCount());
        assertEquals(0, built.result().unplacedCount());
        assertEquals(0, turrets(built.ctx()));
    }
}
