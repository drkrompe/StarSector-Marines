package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.DefensePostStamper;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
    /** The road: a band clean across the map, so it leaves by two ways, joined round the outside. */
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
        // Its two ends joined round the outside, so they are one road and the cap decides which way in stays open.
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                boolean ring = y >= H - 4 && y <= H - 2 || (x >= 1 && x <= 3 || x >= W - 4 && x <= W - 2)
                        && y >= ROAD_LOW && y <= H - 2;
                if (ring) road[x][y] = 0;
            }
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
        // Walled on the claim's own edge, as the fixture stamps it: built directly, because a garrison laid square
        // (Precinct.garrison) stands its wall inside a glacis the fixture does not lay.
        Precinct precinct = new Precinct("garrison", (LEFT + RIGHT) / 2, (BOTTOM + TOP) / 2,
                GrownTrunkPlan.Profile.hamlet(), FortressProgram.garrison(), Precinct.Boundary.WALLED,
                fortification, null, null);
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
     * Every emplacement stands on the precinct that paid for it or, out in front of its wall, on ground no other
     * place holds.
     *
     * <p>The placer slides an anchor that will not validate, and sliding onto
     * another claim is how a garrison's guns end up defending the field next door.
     */
    @Test
    void everyEmplacementStandsOnItsOwnPrecinctOrNoOnesGround() {
        Built built = defend(Fortification.CITADEL);
        for (DefensePost post : built.ctx().defensePosts) {
            int owner = built.claim()[post.anchorX][post.anchorY];
            assertTrue(owner == 0 || owner < 0,
                    "an emplacement at " + post.anchorX + "," + post.anchorY
                            + " stands on another precinct's ground");
        }
    }

    /**
     * The way in is covered: a gate nobody is watching is a door, so the
     * perimeter's posts are sited at the gates before anywhere else, a short
     * setback behind the wall either side of the approach.
     */
    @Test
    void theWayInIsWatched() {
        Built built = defend(Fortification.CITADEL);
        List<PrecinctBoundary.Gate> open = PrecinctBoundary.openGates(
                built.claim(), built.road(), 0, W, H, Fortification.CITADEL.gates());
        assertEquals(1, open.size(), "the fixture should leave a citadel exactly one way in");
        int watched = nearestPost(built, centroid(open.get(0)));
        assertTrue(watched <= 12, "the nearest gun to the gate is " + watched + " cells away, "
                + "further than a flanking post set back behind the wall stands");
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

    /**
     * Every gun has somebody standing at it.
     *
     * <p>A defence post is a structure and fires on its own; the squad beside it
     * is what makes crossing the ground in front of it a fight rather than a
     * shooting gallery against a fixed emplacement. The allocator matches squads
     * to posts by looking for a {@link TacticalNode.Kind#GUARDPOST} node at the
     * post's own anchor, so a post with no node is an unmanned one and there is
     * nothing on the finished map that says so.
     *
     * <p>The priority and garrison come off the tier rather than being restated
     * here: what is being pinned is that a precinct's emplacements are manned on
     * the same terms as the conquest recipe's, not a particular number.
     */
    @Test
    void everyEmplacementIsManned() {
        Built built = defend(Fortification.CITADEL);
        assertTrue(!built.ctx().defensePosts.isEmpty(), "no posts placed, so this measures nothing");
        for (DefensePost post : built.ctx().defensePosts) {
            if (post.tier.garrisonSize == 0) continue;
            TacticalNode manning = null;
            for (TacticalNode node : built.ctx().tactical) {
                if (node.kind != TacticalNode.Kind.GUARDPOST) continue;
                if (node.anchorX == post.anchorX && node.anchorY == post.anchorY) {
                    manning = node;
                    break;
                }
            }
            assertTrue(manning != null, "the " + post.tier + " emplacement at " + post.anchorX
                    + "," + post.anchorY + " has no guard-post node at its anchor, so nothing "
                    + "will ever stand at it");
            assertEquals(post.tier.priorityScore, manning.priorityScore,
                    "the guard post at " + post.anchorX + "," + post.anchorY
                            + " carries a priority its own tier did not give it");
            assertEquals(post.tier.garrisonSize, manning.garrisonSize,
                    "the guard post at " + post.anchorX + "," + post.anchorY
                            + " carries a garrison its own tier did not give it");
            assertEquals(Faction.DEFENDER, manning.defaultGuard,
                    "a garrison's own emplacement is manned by somebody else");
        }
    }

    /**
     * No emplacement stands on a road (owner: "the roads are being cut off with turret emplacements"). The fixture's
     * road runs clean across the place, three cells wide; every cell of every post's footprint keeps off it.
     */
    @Test
    void noEmplacementStandsOnTheRoad() {
        for (Fortification fortification : List.of(Fortification.GARRISON, Fortification.STRONGHOLD,
                Fortification.CITADEL)) {
            Built built = defend(fortification);
            assertTrue(!built.ctx().defensePosts.isEmpty(), "no posts placed, so this measures nothing");
            for (DefensePost post : built.ctx().defensePosts) {
                int r = PrecinctDefence.reach(post.tier);
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = -r; dy <= r; dy++) {
                        assertEquals(GrownTrunkPlan.UNOWNED, built.road()[post.anchorX + dx][post.anchorY + dy],
                                "the " + post.tier + " post at " + post.anchorX + "," + post.anchorY
                                        + " stands on the road");
                    }
                }
            }
        }
    }

    /** Every gate the wall leaves open has a gun within a short walk of it. */
    @Test
    void everyOpenGateIsCovered() {
        Built built = defend(Fortification.GARRISON);
        List<PrecinctBoundary.Gate> open = PrecinctBoundary.openGates(
                built.claim(), built.road(), 0, W, H, Fortification.GARRISON.gates());
        assertEquals(2, open.size(), "the fixture's road should leave the garrison two ways in");
        for (PrecinctBoundary.Gate gate : open) {
            int[] at = centroid(gate);
            assertTrue(nearestPost(built, at) <= 12, "the gate at " + at[0] + "," + at[1] + " is unwatched");
        }
    }

    /** A fortification with nothing on it places nothing, and says so quietly. */
    @Test
    void aBareWallEmplacesNothing() {
        Built built = defend(new Fortification(3, 240));
        assertEquals(0, built.result().placedCount());
        assertEquals(0, built.result().unplacedCount());
        assertEquals(0, turrets(built.ctx()));
    }

    /** A military base is owed a post every so many cells of wall, in its heaviest kind, and none inside. */
    @Test
    void aBaseIsOwedItsWallsAndApproachesInItsHeaviestKind() {
        int wall = 4 * 100;
        Map<DefensePostKind, Integer> owed = PrecinctDefence.owed(Fortification.STRONGHOLD, true, 3, wall);
        int perimeter = owed.getOrDefault(DefensePostKind.LIGHT, 0) + owed.getOrDefault(DefensePostKind.MEDIUM, 0)
                + owed.getOrDefault(DefensePostKind.LARGE, 0);
        assertEquals(wall / PrecinctDefence.WALL_EVERY, perimeter, "a post every stretch of wall, none inside");
        assertEquals(2, owed.get(DefensePostKind.MEDIUM), "the grade's own mediums are kept");
        assertEquals(perimeter - 2, owed.get(DefensePostKind.LARGE), "the rest are its heaviest kind");
        assertEquals(1, owed.get(DefensePostKind.ARTILLERY));
        assertEquals(0, owed.getOrDefault(DefensePostKind.LIGHT, 0));
    }

    @Test
    void aSmallBaseStillMansEveryGateAndCorner() {
        Map<DefensePostKind, Integer> owed = PrecinctDefence.owed(Fortification.PICKET, true, 4, 40);
        assertEquals(2 * 4 + PrecinctDefence.CORNERS, owed.get(DefensePostKind.LIGHT));
    }

    @Test
    void aPlaceThatIsNotABaseKeepsItsGradesLoadout() {
        assertEquals(Fortification.CITADEL.posts(), PrecinctDefence.owed(Fortification.CITADEL, false, 1, 400));
    }

    @Test
    void aWallIsTheEdgeOfItsGround() {
        boolean[][] own = new boolean[12][12];
        for (int x = 1; x <= 10; x++) for (int y = 1; y <= 10; y++) own[x][y] = true;
        assertEquals(36, PrecinctDefence.wallLength(own, 12, 12));
    }

    /**
     * An open field a place guards without a wall (a refinery's process field) is armed by the same doctrine: its
     * direct-fire guns stand outside its edge with their lanes open, its standoff weapon inside, and each is crewed by
     * the place named for it.
     */
    @Test
    void anOpenFieldIsGuardedFromOutsideItsEdgeAndCrewedByItsPlace() {
        GenContext ctx = openGround();
        boolean[][] field = field();
        PrecinctDefence.Result result = PrecinctDefence.stampField(ctx, field, List.of(lane()),
                Map.of(DefensePostKind.LIGHT, 4, DefensePostKind.ARTILLERY, 1), null, "works");
        assertTrue(result.placedCount() >= 4, "the field took " + result.placedCount() + " of its five posts");
        for (DefensePost post : ctx.defensePosts) {
            boolean in = field[post.anchorX][post.anchorY];
            assertEquals(post.tier == DefensePostKind.ARTILLERY, in, post.tier + " at " + post.anchorX + ","
                    + post.anchorY + (in ? " stands in the field it should cover" : " stands outside the field"));
        }
        for (TacticalNode node : ctx.tactical) assertEquals("works", node.place());
    }

    /**
     * A field sited beside another place's posts moves its sites clear of them rather than losing them: the placer
     * refuses a post nearer than its separation to one already standing.
     */
    @Test
    void aFieldSitesItsPostsClearOfPostsAlreadyStanding() {
        Map<DefensePostKind, Integer> owed = Map.of(DefensePostKind.LIGHT, 4, DefensePostKind.ARTILLERY, 1);
        GenContext alone = openGround();
        int whole = PrecinctDefence.stampField(alone, field(), List.of(lane()), owed, null, "works").placedCount();
        DefensePost first = alone.defensePosts.get(0);
        GenContext ctx = openGround();
        DefensePost standing = new DefensePost(first.tier, first.anchorX, first.anchorY, List.of());
        ctx.defensePosts.add(standing);
        PrecinctDefence.Result result = PrecinctDefence.stampField(ctx, field(), List.of(lane()), owed, null, "works");
        assertEquals(whole, result.placedCount(), "a site planned on another's post was lost, not moved");
        for (DefensePost post : ctx.defensePosts) {
            if (post == standing) continue;
            int dx = post.anchorX - standing.anchorX, dy = post.anchorY - standing.anchorY;
            assertTrue(dx * dx + dy * dy >= DefensePostStamper.POST_MIN_SEPARATION * DefensePostStamper.POST_MIN_SEPARATION,
                    post.tier + " at " + post.anchorX + "," + post.anchorY + " stands on the post already there");
        }
    }

    private static GenContext openGround() {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.GRASS);
            }
        }
        return new GenContext(grid, topology, new Random(7L), W, H, 7L);
    }

    private static boolean[][] field() {
        boolean[][] field = new boolean[W][H];
        for (int x = LEFT; x <= RIGHT; x++) for (int y = BOTTOM; y <= TOP; y++) field[x][y] = true;
        return field;
    }

    private static DefenceDoctrine.Gate lane() {
        return new DefenceDoctrine.Gate((LEFT + RIGHT) / 2, BOTTOM, 0, -1, 1);
    }
}
