package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.FieldOfFire;
import com.dillon.starsectormarines.battle.world.gen.road.RoadReservation;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;

/**
 * The shower of fragments the hull threw as it broke up coming in ({@code fallen-hull-nouns.md}, The ground): plates,
 * girders, pods, engine bells and great pieces of hull, with scorch and small craters, strewn along its fall line,
 * uprange of the trench, beside it and the sections, and ahead of the stern where the last pieces flew on. They thin
 * with distance from the line and from the wreck, and reach into the town and the fields, not a ring round the hull.
 *
 * <p>Large pieces are cover and stand in the way. Each such piece keeps a ring of open ground round it, so no fragment
 * closes a way, and none lands on a road, a building, water, the hull, its trench or an apron, a mission place or the
 * landing's field of fire. Flat pieces, craters and scorch lie anywhere open. A piece the tileset lacks is left out.
 * Falls on the finished country, after the mission's structures and terraces are sited and before the claim lattice
 * seats its stakes; a piece in the way keeps clear of every tactical node and of the lattice's grid points. Its own
 * stream, derived from the seed, so the country under it is unmoved.
 *
 * <p>Flat pieces lie on streets and roads too: rubble, not a barrier. Where a great or middling piece comes down on a
 * building, it strikes it instead ({@link #STRIKES}): a few buildings are knocked down and more roofs are holed or
 * burnt, as initial damage the battle applies when it is assembled, never a mission place, a fighting position, the
 * landing's field of fire or the hull. A few lone great pieces fly well out, into the fields and the town.
 */
final class DebrisShower {

    /** The great pieces, near the line: whole sections of shell, drive and engine. */
    static final String[] LARGE = {"doodad.basin-scrap-shattered-hull", "doodad.basin-scrap-hull-shell",
            "doodad.basin-scrap-drive-section", "doodad.basin-scrap-engine-break", "doodad.basin-scrap-rib-section",
            "doodad.basin-scrap-wreck-frame", "doodad.basin-scrap-intake-section", "doodad.basin-scrap-plate-stack",
            "fallen-hull.shower-bell", "fallen-hull.shower-slab", "fallen-hull.debris-bell", "fallen-hull.debris-pod"};
    /** Middling pieces: girders, pods, masts, plate. */
    static final String[] MEDIUM = {"fallen-hull.shower-girders", "fallen-hull.shower-pod", "fallen-hull.shower-mast",
            "fallen-hull.shower-plate-b", "fallen-hull.debris-girder", "fallen-hull.debris-scrap-heap",
            "fallen-hull.debris-plate-shard-a", "fallen-hull.debris-plate-shard-b"};
    /** Shards and plate that lie flat. */
    static final String[] SMALL = {"fallen-hull.shower-shard-a", "fallen-hull.shower-shard-b",
            "fallen-hull.shower-shard-c", "fallen-hull.shower-plate-a", "fallen-hull.breach-scatter",
            "ship-dressing.debris"};
    /** Where pieces struck: craters and scorch. */
    static final String[] MARKS = {"fallen-hull.crater-small", "fallen-hull.crater-large",
            "fallen-hull.scorch-splash", "fallen-hull.scorch-streak", "ship-wear.scorch"};

    /** How far the shower runs ahead of the stern and uprange of the trench's far end, cells. UNTUNED. */
    static final int AHEAD = 90, UPRANGE = 160;
    /** Spread across the line at the wreck, and its growth per cell out along the line. UNTUNED. */
    static final float SPREAD = 52f, SPREAD_GROWTH = 0.6f;
    /** How fast the shower thins along and across the line, cells per e-fold. UNTUNED. */
    static final float THIN_ALONG = 170f, THIN_ACROSS = 150f;
    /** Pieces tried, and the most of each kind laid. UNTUNED. */
    static final int TRIES = 60000, MAX_LARGE = 28, MAX_MEDIUM = 200, MAX_SMALL = 1100, MAX_MARKS = 420;
    /** Lone great pieces thrown well out, and how far across the line they may land. UNTUNED. */
    static final int LONE = 8, LONE_ACROSS = 150;
    /** Buildings knocked down, roofs holed, and the least distance between two strikes, cells. UNTUNED. */
    static final int KNOCKDOWNS = 3, HOLES = 14, STRIKE_GAP = 9;
    /** A knocked-down building's collapse and battering radii, and a holed roof's, cells. UNTUNED. */
    static final double KNOCKDOWN_RADIUS = 4.5, BATTER_RADIUS = 7, HOLE_RADIUS = 1.6;
    /**
     * How far a strike keeps from the landing's field of fire; from a mission place or a fighting position it keeps its
     * own reach and two cells more; a knocked-down building keeps its collapse off fighting positions.
     */
    static final int STRIKE_CLEAR = 10;
    /** Open ground kept round a piece that stands in the way, and kept off the landing's field of fire. */
    static final int RING = 2, FIELD_MARGIN = 4;
    /**
     * A piece in the way keeps this far from every grid point of the claim lattice ({@link ClaimLattice}, about the
     * hull's middle), so each point keeps the stake ground it would have had without the shower.
     */
    static final int STAKE_CLEAR = 4;

    private static final long SALT = 0x53_68_6F_77_65_72L;

    private DebrisShower() { }

    /** Lays the shower; returns its strikes on buildings, as damage strokes for the battle to apply. */
    static List<DamagePlan.Stroke> fall(GenContext ctx, CrashSiteStage.Frame frame, int trench, BitSet hull,
                                        BitSet trenchCells, BitSet aprons, FieldOfFire field) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return List.of();
        List<DoodadDef> large = present(registry, LARGE), medium = present(registry, MEDIUM);
        List<DoodadDef> small = present(registry, SMALL), marks = present(registry, MARKS);
        boolean[][] roads = RoadReservation.mask(ctx.get(BspKeys.ROAD_GRAPH), ctx.width, ctx.height);
        Fall fall = new Fall(ctx, frame, trench, hull, trenchCells, aprons, field, roads);
        fall.lay(large, medium, small, marks);
        fall.lone(large);
        return fall.strikes;
    }

    private static List<DoodadDef> present(TileRegistry registry, String[] ids) {
        List<DoodadDef> out = new ArrayList<>();
        for (String id : ids) {
            DoodadDef def = registry.doodad(id);
            if (def != null) out.add(def);
        }
        return out;
    }

    /** Whether a piece stands in the way: cover worth taking. */
    static boolean blocks(DoodadDef def) {
        return def.cover == DoodadCover.MED || def.cover == DoodadCover.HEAVY;
    }

    private static final class Fall {
        final GenContext ctx;
        final NavigationGrid grid;
        final CellTopology topology;
        final CrashSiteStage.Frame frame;
        final int trench, w, h;
        final BitSet hull, trenchCells, aprons;
        /** Cells a tactical node stands on: a fighting position keeps its ground. */
        final boolean[] nodes;
        final FieldOfFire field;
        final boolean[][] roads;
        final boolean[] taken;
        /** The port's landing pads, kept clear of every piece and mark. */
        final BitSet pads;
        final Random rng;
        final List<DamagePlan.Stroke> strikes = new ArrayList<>();
        final List<int[]> struck = new ArrayList<>();
        int knockdowns, holes;

        Fall(GenContext ctx, CrashSiteStage.Frame frame, int trench, BitSet hull, BitSet trenchCells, BitSet aprons,
             FieldOfFire field, boolean[][] roads) {
            this.ctx = ctx;
            this.grid = ctx.grid;
            this.topology = ctx.topology;
            this.frame = frame;
            this.trench = trench;
            this.w = ctx.width;
            this.h = ctx.height;
            this.hull = hull;
            this.trenchCells = trenchCells;
            this.aprons = aprons;
            this.field = field;
            this.roads = roads;
            this.taken = new boolean[w * h];
            this.pads = ctx.get(HullKeys.PADS);
            this.rng = new Random(ctx.seed ^ SALT);
            this.nodes = new boolean[w * h];
            for (TacticalNode node : ctx.tactical) {
                if (node.anchorX >= 0 && node.anchorY >= 0 && node.anchorX < w && node.anchorY < h) {
                    nodes[node.anchorY * w + node.anchorX] = true;
                }
            }
        }

        void lay(List<DoodadDef> large, List<DoodadDef> medium, List<DoodadDef> small, List<DoodadDef> marks) {
            int nLarge = 0, nMedium = 0, nSmall = 0, nMarks = 0;
            // The fall line runs from uprange of the trench's far end, through the bow, the sections and the stern, on.
            int from = -AHEAD, to = frame.laying().length() + trench + UPRANGE;
            int wreckEnd = frame.laying().length() + trench;
            int mid = HullPlan.LAID_BREADTH / 2;
            for (int k = 0; k < TRIES; k++) {
                int l = from + rng.nextInt(to - from);
                float out = l < 0 ? -l : l > wreckEnd ? l - wreckEnd : 0;
                float spread = SPREAD + SPREAD_GROWTH * out;
                float across = (float) rng.nextGaussian() * spread;
                float keep = (float) (Math.exp(-out / THIN_ALONG) * Math.exp(-Math.abs(across) / THIN_ACROSS));
                float roll = rng.nextFloat();
                float size = rng.nextFloat();
                if (roll > keep) continue;
                int x = frame.lx(l, mid + Math.round(across)), y = frame.ly(l, mid + Math.round(across));
                // Great pieces fall near the line and the wreck; small ones anywhere the shower reaches.
                float reach = Math.abs(across) + out * 0.5f;
                List<DoodadDef> pool;
                if (reach < 64 && size < 0.22f && nLarge < MAX_LARGE) pool = large;
                else if (reach < 110 && size < 0.38f && nMedium < MAX_MEDIUM) pool = medium;
                else if (size < 0.72f && nSmall < MAX_SMALL) pool = small;
                else if (nMarks < MAX_MARKS) pool = marks;
                else continue;
                if (pool.isEmpty()) continue;
                if ((pool == large || pool == medium) && strike(x, y, pool == large)) continue;
                DoodadDef def = pool.get(rng.nextInt(pool.size()));
                if (!place(def, x, y)) continue;
                if (pool == large) nLarge++;
                else if (pool == medium) nMedium++;
                else if (pool == small) nSmall++;
                else nMarks++;
            }
        }

        /** A few great pieces thrown far: anywhere along the line, well out across it. */
        void lone(List<DoodadDef> large) {
            if (large.isEmpty()) return;
            int from = -AHEAD, to = frame.laying().length() + trench + UPRANGE, mid = HullPlan.LAID_BREADTH / 2;
            int laid = 0;
            for (int k = 0; k < LONE * 60 && laid < LONE; k++) {
                int l = from + rng.nextInt(to - from);
                int across = (rng.nextBoolean() ? 1 : -1) * (40 + rng.nextInt(LONE_ACROSS - 40));
                int x = frame.lx(l, mid + across), y = frame.ly(l, mid + across);
                if (place(large.get(rng.nextInt(large.size())), x, y)) laid++;
            }
        }

        /**
         * Where a piece comes down on a building, it strikes it: a great piece knocks it down, a middling one holes its
         * roof. Whether the cell was a building's and the piece is spent on it.
         */
        boolean strike(int x, int y, boolean great) {
            if (x < 2 || y < 2 || x >= w - 2 || y >= h - 2) return false;
            if (!topology.hasBuildingKindHint(x, y) && topology.getGroundKind(x, y) != GroundKind.INDOOR) return false;
            boolean knock = great && knockdowns < KNOCKDOWNS;
            if (!knock && holes >= HOLES) return true;
            int clear = (int) Math.ceil(knock ? BATTER_RADIUS : HOLE_RADIUS) + 1;
            for (int[] s : struck) {
                if (Math.abs(s[0] - x) < STRIKE_GAP && Math.abs(s[1] - y) < STRIKE_GAP) return true;
            }
            for (int yy = y - clear; yy <= y + clear; yy++) {
                for (int xx = x - clear; xx <= x + clear; xx++) {
                    if (xx < 0 || yy < 0 || xx >= w || yy >= h) return true;
                    int k = yy * w + xx;
                    if (hull.get(k) || aprons.get(k) || trenchCells.get(k)) return true;
                }
            }
            if (inField(x - STRIKE_CLEAR, y - STRIKE_CLEAR, 2 * STRIKE_CLEAR + 1, 2 * STRIKE_CLEAR + 1)) return true;
            for (PointOfInterest poi : ctx.pois) {
                // Homes and stores may be struck; a building a mission anchors to (a lab, comms, the offices, the
                // clinic, a landing) keeps clear.
                if (poi.kind == PointOfInterest.Kind.RESIDENTIAL || poi.kind == PointOfInterest.Kind.DEPOT) continue;
                int l = Math.min(poi.left, poi.right) - clear, r = Math.max(poi.left, poi.right) + clear;
                int b = Math.min(poi.top, poi.bottom) - clear, t = Math.max(poi.top, poi.bottom) + clear;
                if (x >= l && x <= r && y >= b && y <= t) return true;
            }
            // A knocked-down building keeps clear of fighting positions; a holed roof leaves its walls and nodes stand.
            int keep = (int) Math.ceil(KNOCKDOWN_RADIUS) + 1;
            for (TacticalNode node : ctx.tactical) {
                if (knock && Math.abs(node.anchorX - x) <= keep && Math.abs(node.anchorY - y) <= keep) return true;
            }
            List<DamagePlan.Point> at = List.of(new DamagePlan.Point(x, y));
            if (knock) {
                strikes.add(new DamagePlan.Stroke(DamagePlan.Mode.BATTERED, BATTER_RADIUS, at));
                strikes.add(new DamagePlan.Stroke(DamagePlan.Mode.RUIN, KNOCKDOWN_RADIUS, at));
                knockdowns++;
            } else {
                strikes.add(new DamagePlan.Stroke(DamagePlan.Mode.RUIN, HOLE_RADIUS, at));
                holes++;
            }
            struck.add(new int[]{x, y});
            return true;
        }

        /** Lays {@code def} with its middle at {@code (cx, cy)} where it may lie; whether it did. */
        boolean place(DoodadDef def, int cx, int cy) {
            int fx = Math.max(1, def.footprintCellsX), fy = Math.max(1, def.footprintCellsY);
            int x0 = cx - fx / 2, y0 = cy - fy / 2;
            boolean blocks = blocks(def);
            int ring = blocks ? RING : 0;
            for (int y = y0 - ring; y < y0 + fy + ring; y++) {
                for (int x = x0 - ring; x < x0 + fx + ring; x++) {
                    if (!open(x, y, blocks)) return false;
                }
            }
            if (inField(x0, y0, fx, fy) || onPlace(x0, y0, fx, fy)) return false;
            if (blocks && nearStake(x0, y0, fx, fy)) return false;
            for (int y = y0; y < y0 + fy; y++) {
                for (int x = x0; x < x0 + fx; x++) {
                    taken[y * w + x] = true;
                    if (!blocks) continue;
                    grid.setWalkable(x, y, false);
                    grid.setSeeThrough(x, y, true);
                    topology.setFixture(x, y, true);
                }
            }
            boolean mirrored = rng.nextBoolean();
            if (blocks) ctx.doodads.add(new Doodad(x0, y0, def, 0, mirrored));
            else ctx.doodads.add(0, new Doodad(x0, y0, def, 0, mirrored));
            return true;
        }

        /** Whether a piece may cover the cell: open ground; for a piece in the way, off the trench and its lips too. */
        boolean open(int x, int y, boolean blocks) {
            if (x < 2 || y < 2 || x >= w - 2 || y >= h - 2) return false;
            int k = y * w + x;
            if (taken[k] || hull.get(k) || aprons.get(k) || pads != null && pads.get(k)) return false;
            // Rubble lies on a road or a street; nothing in the way does.
            if (blocks && (roads[x][y] || trenchCells.get(k) || nodes[k])) return false;
            if (!grid.isWalkable(x, y) || grid.isDoorway(x, y) || topology.isWall(x, y) || topology.isFixture(x, y)
                    || topology.hasBuildingKindHint(x, y) || topology.getWallRemnant(x, y) != null) return false;
            GroundKind g = topology.getGroundKind(x, y);
            return g != GroundKind.INDOOR && g != GroundKind.WATER && g != GroundKind.VOID
                    && (!blocks || g != GroundKind.STREET);
        }

        boolean nearStake(int x0, int y0, int fx, int fy) {
            Area box = frame.laid();
            int cx = box.centreX(), cy = box.centreY(), s = ClaimLattice.SPACING;
            for (int y = y0 - STAKE_CLEAR; y < y0 + fy + STAKE_CLEAR; y++) {
                for (int x = x0 - STAKE_CLEAR; x < x0 + fx + STAKE_CLEAR; x++) {
                    if (Math.floorMod(x - cx, s) == 0 && Math.floorMod(y - cy, s) == 0) return true;
                }
            }
            return false;
        }

        boolean inField(int x0, int y0, int fx, int fy) {
            if (field == null) return false;
            long r = field.radius() + FIELD_MARGIN;
            long dx = Math.max(0, Math.max(x0 - field.centerX(), field.centerX() - (x0 + fx - 1)));
            long dy = Math.max(0, Math.max(y0 - field.centerY(), field.centerY() - (y0 + fy - 1)));
            return dx * dx + dy * dy <= r * r;
        }

        /** Whether the piece touches a mission place (a shelter, a holdout, an objective), with a margin. */
        boolean onPlace(int x0, int y0, int fx, int fy) {
            for (PointOfInterest poi : ctx.pois) {
                int l = Math.min(poi.left, poi.right) - RING, r = Math.max(poi.left, poi.right) + RING;
                int b = Math.min(poi.top, poi.bottom) - RING, t = Math.max(poi.top, poi.bottom) + RING;
                if (x0 + fx - 1 >= l && x0 <= r && y0 + fy - 1 >= b && y0 <= t) return true;
            }
            return false;
        }
    }
}
