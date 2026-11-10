package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.DefensePostStamper;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Puts a walled precinct's emplacements on the ground.
 *
 * <p>The wall is the part of a fortification that is easiest to see and the
 * least of what makes it hard. A wall is a delay and a detour; what turns a
 * place into a problem is what shoots at the ground in front of it, so
 * {@link Fortification#posts} is the dial that decides the fight and this is
 * where it lands.
 *
 * <p>Where each one stands is {@link DefenceDoctrine}'s: flanking every gate,
 * at the wall's corners facing the threat, beside the motor pool and command,
 * and the standoff weapons deepest. This class gathers what the doctrine is
 * sited against — the gates the wall left open, the ground behind the wall, and
 * the ground nothing defensive may stand on — and stamps the sites. That ground
 * is every road (the town's, the place's own road in, its streets, parade and
 * motor pool) and every gate's approach: the owner's notes were that the guns
 * cut the roads, and a placer that only knew the road graph's centreline let
 * them.
 */
public final class PrecinctDefence {

    private PrecinctDefence() {}

    /** Cells beside a road an emplacement's footprint keeps off: the verge. */
    static final int VERGE = 1;

    /**
     * What a walled place's ward leaves for its defences: the gates its wall left
     * open, what its inner posts cover (motor pool, command; most important first)
     * and the ground it keeps for moving, map-sized.
     */
    public record Works(List<PrecinctBoundary.Gate> gates, List<int[]> hearts, boolean[][] kept) {
        public Works {
            gates = List.copyOf(gates);
            hearts = List.copyOf(hearts);
        }
    }

    /** What the fortification asked for, and what the ground actually took. */
    public record Result(Map<DefensePostKind, Integer> placed,
                         Map<DefensePostKind, Integer> unplaced) {

        public int placedCount() {
            return total(placed);
        }

        public int unplacedCount() {
            return total(unplaced);
        }

        private static int total(Map<DefensePostKind, Integer> counts) {
            int sum = 0;
            for (int count : counts.values()) sum += count;
            return sum;
        }
    }

    /**
     * Stamp every emplacement {@code precinct}'s fortification asks for, and say
     * what could not be placed.
     *
     * <p>Runs after the wall, and that order is load-bearing twice over. The
     * gates are not known until the wall decides which crossings stay open, and
     * the placer validates a footprint against the grid — so with the wall down
     * a perimeter post happily straddles the line it is meant to sit behind.
     */
    public static Result stamp(GenContext ctx, Precinct precinct,
                               int[][] claim, int[][] road, int who) {
        Map<DefensePostKind, Integer> placed = new EnumMap<>(DefensePostKind.class);
        Map<DefensePostKind, Integer> unplaced = new EnumMap<>(DefensePostKind.class);
        Fortification fortification = precinct.fortification();
        if (fortification == null || fortification.postCount() == 0) {
            return new Result(Map.copyOf(placed), Map.copyOf(unplaced));
        }
        int w = ctx.width, h = ctx.height;
        // The place's own ground inside its glacis; behind its wall, the part a gun may stand on.
        boolean[][] own = own(claim, who, fortification.square() ? fortification.glacis() : 0, w, h);
        boolean[][] inside = new boolean[w][h];
        boolean any = false;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                inside[x][y] = own[x][y] && !ctx.topology.isWall(x, y);
                any |= inside[x][y];
            }
        }
        if (!any) return new Result(Map.copyOf(placed), Map.copyOf(unplaced));

        Map<Integer, Works> allWorks = ctx.get(BspKeys.PRECINCT_WORKS);
        Works works = allWorks == null ? null : allWorks.get(who);
        List<PrecinctBoundary.Gate> open = works != null ? works.gates()
                : PrecinctBoundary.openGates(claim, road, who, w, h, fortification.gates());
        List<DefenceDoctrine.Gate> gates = new ArrayList<>();
        for (PrecinctBoundary.Gate gate : open) {
            DefenceDoctrine.Gate g = DefenceDoctrine.gateOf(gate.cells(), own);
            if (g != null) gates.add(g);
        }

        boolean[][] clear = keepClear(road, ctx.get(BspKeys.ROAD_RESERVATION),
                works == null ? null : works.kept(), gates, w, h);
        DefenceDoctrine.Fits fits = (x, y, kind) -> fits(ctx, inside, clear, x, y, kind);
        int[] threat = threat(ctx);
        List<int[]> hearts = works == null ? List.of() : works.hearts();
        boolean cantonment = precinct.isProgrammed() && precinct.program().cantonment();
        Map<DefensePostKind, Integer> wanted = owed(fortification, cantonment, gates.size(), hearts.size(),
                wallLength(own, w, h));

        DefensePostStamper.Ground ground = (x, y) -> x >= 0 && x < w && y >= 0 && y < h && inside[x][y];
        for (DefenceDoctrine.Site site : DefenceDoctrine.plan(inside, gates, hearts, threat, wanted, fits)) {
            int got = DefensePostStamper.stampAtSites(ctx.grid, ctx.topology, ground, clear, ctx.doodads,
                    ctx.tactical, ctx.defensePosts, ctx.rng, site.kind(), List.of(new int[]{site.x(), site.y()}));
            if (got > 0) placed.merge(site.kind(), got, Integer::sum);
        }
        for (Map.Entry<DefensePostKind, Integer> e : wanted.entrySet()) {
            int short_ = e.getValue() - placed.getOrDefault(e.getKey(), 0);
            if (short_ > 0) unplaced.put(e.getKey(), short_);
        }
        return new Result(Map.copyOf(placed), Map.copyOf(unplaced));
    }

    /** Cells of a military base's wall one perimeter post holds: a post every this many along it. UNTUNED. */
    static final int WALL_EVERY = 16;

    /** Corners a base's wall turns, each with a tower. */
    static final int CORNERS = 4;

    /**
     * The posts a place is owed: its fortification's. A military base (a cantonment) is defended on its approaches and
     * its walls, not only as far as its grade's loadout reaches: two posts flanking each gate, a tower at each corner,
     * a post every {@link #WALL_EVERY} cells of wall, whichever asks more, and one at each thing it guards inside.
     * What its loadout lacks is made up in its heaviest perimeter kind, so a fortified base's wall is lined with
     * heavy guns and an open one's with light. These are static: the attack picks which to meet
     * ({@code BattleForceScore#standingDefensePosts}).
     */
    static Map<DefensePostKind, Integer> owed(Fortification fortification, boolean cantonment, int gates,
                                              int hearts, int wallLength) {
        Map<DefensePostKind, Integer> posts = new EnumMap<>(DefensePostKind.class);
        for (DefensePostKind kind : Fortification.PRECEDENCE) {
            if (fortification.posts(kind) > 0) posts.put(kind, fortification.posts(kind));
        }
        if (!cantonment) return posts;
        int perimeter = 0;
        DefensePostKind heaviest = null;
        for (DefensePostKind kind : DefenceDoctrine.PERIMETER) {
            int n = posts.getOrDefault(kind, 0);
            perimeter += n;
            if (heaviest == null && n > 0) heaviest = kind;
        }
        int wall = Math.max(2 * gates + CORNERS, wallLength / WALL_EVERY);
        int short_ = wall + hearts - perimeter;
        if (short_ > 0) posts.merge(heaviest == null ? DefensePostKind.LIGHT : heaviest, short_, Integer::sum);
        return posts;
    }

    /** Cells along the edge of {@code own}: its wall's length. */
    static int wallLength(boolean[][] own, int w, int h) {
        int n = 0;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (!own[x][y]) continue;
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1
                        || !own[x - 1][y] || !own[x + 1][y] || !own[x][y - 1] || !own[x][y + 1]) n++;
            }
        }
        return n;
    }

    /**
     * Ground no emplacement may stand on: every road, the road graph's
     * reservation, the place's own streets and yards, and every gate's approach.
     */
    static boolean[][] keepClear(int[][] road, boolean[][] reservation, boolean[][] kept,
                                 List<DefenceDoctrine.Gate> gates, int w, int h) {
        boolean[][] clear = DefenceDoctrine.approaches(gates, w, h);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (road[x][y] != GrownTrunkPlan.UNOWNED
                        || reservation != null && reservation[x][y]
                        || kept != null && kept[x][y]) clear[x][y] = true;
            }
        }
        return clear;
    }

    /**
     * Whether an emplacement of {@code kind} can stand at {@code (x, y)}: its
     * footprint's box behind the wall on open walkable ground, and that box and a
     * verge round it off the ground kept clear.
     */
    private static boolean fits(GenContext ctx, boolean[][] inside, boolean[][] clear, int x, int y,
                                DefensePostKind kind) {
        int r = reach(kind);
        int w = ctx.width, h = ctx.height;
        for (int dx = -r - VERGE; dx <= r + VERGE; dx++) {
            for (int dy = -r - VERGE; dy <= r + VERGE; dy++) {
                int nx = x + dx, ny = y + dy;
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) return false;
                if (clear[nx][ny]) return false;
                if (Math.abs(dx) > r || Math.abs(dy) > r) continue;
                if (!inside[nx][ny] || !ctx.grid.isWalkable(nx, ny) || ctx.grid.isDoorway(nx, ny)) return false;
            }
        }
        return true;
    }

    /** Half the widest footprint a kind stamps: a heavy post's five cells, three for the rest. */
    static int reach(DefensePostKind kind) {
        return kind == DefensePostKind.LARGE || kind == DefensePostKind.ARTILLERY ? 2 : 1;
    }

    /** Where an attack comes from: the plan's landing place, or null where the plan has none. */
    private static int[] threat(GenContext ctx) {
        PrecinctPlan plan = ctx.get(BspKeys.PRECINCTS);
        Precinct landing = plan == null ? null : plan.landingPlace();
        return landing == null ? null : new int[]{landing.seedX(), landing.seedY()};
    }

    /** The claim's cells further than {@code glacis} from anything that is not the claim. */
    static boolean[][] own(int[][] claim, int who, int glacis, int w, int h) {
        boolean[][] mine = new boolean[w][h];
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) mine[x][y] = claim[x][y] == who;
        if (glacis <= 0) return mine;
        int[][] depth = DefenceDoctrine.depth(mine);
        boolean[][] own = new boolean[w][h];
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) own[x][y] = depth[x][y] > glacis;
        return own;
    }

    /**
     * Adds to {@code hearts} what a programmed place's inner posts cover: its
     * motor pool (its first vehicle shed, where nothing has named a pool yet) and
     * its command.
     */
    public static void hearts(List<RoomPacker.Placed> rooms, List<int[]> hearts) {
        if (hearts.isEmpty()) {
            for (RoomPacker.Placed room : rooms) {
                if (room.purpose() != RoomPurpose.VEHICLE_BAY) continue;
                hearts.add(centre(room));
                break;
            }
        }
        for (RoomPacker.Placed room : rooms) {
            if (room.purpose() != RoomPurpose.KEEP_THRONE) continue;
            hearts.add(centre(room));
            break;
        }
    }

    /** The middle of a list of cells. */
    public static int[] middle(List<int[]> cells) {
        long sx = 0, sy = 0;
        for (int[] c : cells) {
            sx += c[0];
            sy += c[1];
        }
        return new int[]{(int) (sx / cells.size()), (int) (sy / cells.size())};
    }

    private static int[] centre(RoomPacker.Placed room) {
        return new int[]{room.originX() + room.shape().width() / 2, room.originY() + room.shape().height() / 2};
    }
}
