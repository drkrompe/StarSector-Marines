package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.fort.FortTag;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.StructureFootprint;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.PlacementGuards;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortBelt;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressInterior;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationGround;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationWalls;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.DepthBand;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegion;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RockDamage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Stands exactly the battery forts the mission declared ({@link GroundDefences#fortCount(FortKind)}) and finds ground
 * for them. Like {@link FortressBatteryStage} it never reads the market and never derives a count from the map: with no
 * declaration it places nothing and draws nothing, and the map is byte-identical to one generated without it.
 *
 * <p>A fort is the authored {@link BatteryFort} layout placed on open ground at one of four turns. The city is
 * prior: a site must be open outdoor ground, off every road, doorway, authored infantry place, landing berth and
 * existing structure, with a margin of clear ground all round so the fort can never cut the walkable map in two.
 * Within that, sites are taken deep band first, then nearest the map centre, and the turn is the one that points the
 * main gate most nearly at the centre. Nothing here draws a random number.
 *
 * <p>Each gate is paved out to the nearest road when an open-ground route there exists, so the fort is reached the way
 * any other place is. The painted lane is ground surface only: it is not added to the published road graph, which the
 * convoys read and which a fort is no part of.
 *
 * <p>The stage reuses the compound machinery rather than a parallel one: buildings are shelled, windowed and given
 * their doors by {@link FortressInterior}, and the perimeter is dressed by {@link InstallationWalls}. Their core
 * structures, the battery platform and the nests are emitted as {@link DefensePost}s carrying a {@link FortTag}, so the
 * ordinary spawn path stands them up and the simulation assembles the fort from what was stamped.
 */
public final class BatteryFortStage implements GenStage {

    /** Hit points of a wall cell of the fort's outer wall: breachable by demolition, not by small arms. */
    public static final int FORT_WALL_HP = BatteryFort.WALL_HP;
    /** Hit points of a wall cell of a fort building. */
    public static final int BUILDING_WALL_HP = 300;

    /** Clear ground required all round a fort, in cells. */
    private static final int MARGIN = 2;
    /** Minimum distance between two forts' centres, in cells. */
    private static final int FORT_SEPARATION = 66;
    private static final int SCAN_STRIDE = 3;
    /** Longest open-ground route to the road network worth paving, in cells. */
    private static final int MAX_LINK_CELLS = 140;
    private static final EnumSet<DepthBand> ALLOWED_BANDS = EnumSet.of(DepthBand.MID, DepthBand.DEEP, DepthBand.UNSET);
    private static final EnumSet<GroundKind> REFUSED_GROUND = EnumSet.of(GroundKind.INDOOR, GroundKind.WATER,
            GroundKind.TILE, GroundKind.STREET, GroundKind.LZ_MARKER, GroundKind.VOID, GroundKind.SIDEWALK,
            GroundKind.STRIPED);
    private static final String[] NEST_STRUCTURES = {"structure.turret-heavy-mg", "structure.turret-dual-flak"};

    /** A candidate centre with the order it is taken in. */
    private record Site(int x, int y, int band, long centreDistSq) {}

    /** Where a fort stands, for evidence. */
    public record Placed(CompoundLayout.Placement placement, int fortId) {}

    private final List<Placed> placed = new ArrayList<>();

    public BatteryFortStage() {}

    /** The forts this stage stood on its last run, in the order they were placed. */
    public List<Placed> placed() {
        return placed;
    }

    @Override
    public void run(GenContext ctx) {
        placed.clear();
        TargetProfile profile = ctx.get(BspKeys.MARKET_PROFILE);
        int budget = profile == null ? 0 : profile.declaredDefences().fortCount(FortKind.BATTERY_FORT);
        if (budget <= 0) return;
        TacticalRegionMap regions = ctx.get(BspKeys.TACTICAL_REGIONS);
        if (regions == null) {
            throw new IllegalStateException(
                    "BatteryFortStage requires TACTICAL_REGIONS - must run after TacticalRegionStage");
        }
        CompoundLayout layout = BatteryFort.layout();
        Ground ground = new Ground(ctx);
        List<Site> sites = candidates(ctx, regions);
        sites.sort(Comparator.comparingInt(Site::band).thenComparingLong(Site::centreDistSq)
                .thenComparingInt(Site::y).thenComparingInt(Site::x));

        for (Site site : sites) {
            if (placed.size() >= budget) break;
            if (tooClose(site)) continue;
            CompoundLayout.Placement at = fit(layout, ground, site, ctx);
            if (at == null) continue;
            int fortId = placed.size();
            stamp(ctx, at, fortId, ground);
            ground.exclude(at);
            placed.add(new Placed(at, fortId));
        }
        if (placed.isEmpty()) return;
        finish(ctx);
        ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(ctx.grid, ctx.topology, ctx.get(BspKeys.AXIS)));
    }

    // ------------------------------------------------------------------------------------------ site

    private boolean tooClose(Site site) {
        long sq = (long) FORT_SEPARATION * FORT_SEPARATION;
        for (Placed p : placed) {
            int[] c = p.placement().cell(BatteryFort.WIDTH / 2, BatteryFort.HEIGHT / 2);
            long dx = c[0] - site.x();
            long dy = c[1] - site.y();
            if (dx * dx + dy * dy < sq) return true;
        }
        return false;
    }

    private static List<Site> candidates(GenContext ctx, TacticalRegionMap regions) {
        List<Site> out = new ArrayList<>();
        float cx = ctx.width * 0.5f;
        float cy = ctx.height * 0.5f;
        for (int y = 0; y < ctx.height; y += SCAN_STRIDE) {
            for (int x = 0; x < ctx.width; x += SCAN_STRIDE) {
                TacticalRegion region = regions.regionAt(x, y);
                if (region != null && !ALLOWED_BANDS.contains(region.depthBand)) continue;
                int band = region != null && region.depthBand == DepthBand.DEEP ? 0 : 1;
                long dx = Math.round(x - cx);
                long dy = Math.round(y - cy);
                out.add(new Site(x, y, band, dx * dx + dy * dy));
            }
        }
        return out;
    }

    /**
     * The placement of the layout centred on {@code site} at the first turn, ordered by how nearly the main gate faces
     * the map centre, whose whole box and margin is open ground; {@code null} when no turn fits.
     */
    private static CompoundLayout.Placement fit(CompoundLayout layout, Ground ground, Site site, GenContext ctx) {
        int[][] order = turnsByGateFacing(layout, ctx, site);
        for (int[] turn : order) {
            int q = turn[0];
            int w = q % 2 == 0 ? BatteryFort.WIDTH : BatteryFort.HEIGHT;
            int h = q % 2 == 0 ? BatteryFort.HEIGHT : BatteryFort.WIDTH;
            int x = site.x() - w / 2;
            int y = site.y() - h / 2;
            if (x < 0 || y < 0 || x + w > ctx.width || y + h > ctx.height) continue;
            CompoundLayout.Placement at = layout.at(x, y, q);
            // The works and the glacis round them need clear ground; the belt beyond follows whatever is open.
            if (ground.clear(at)) return at;
        }
        return null;
    }

    /** Turns ordered by the main gate's outward direction against the direction to the map centre, best first. */
    private static int[][] turnsByGateFacing(CompoundLayout layout, GenContext ctx, Site site) {
        double toCentreX = ctx.width * 0.5 - site.x();
        double toCentreY = ctx.height * 0.5 - site.y();
        double length = Math.hypot(toCentreX, toCentreY);
        if (length > 0) {
            toCentreX /= length;
            toCentreY /= length;
        }
        int[][] order = new int[4][2];
        for (int q = 0; q < 4; q++) {
            var at = layout.at(0, 0, q);
            int[] inside = at.cell(BatteryFort.OX + 2, BatteryFort.OY + 17);
            int[] outside = at.cell(BatteryFort.OX - 3, BatteryFort.OY + 17);
            double dot = (outside[0] - inside[0]) * toCentreX + (outside[1] - inside[1]) * toCentreY;
            order[q][0] = q;
            order[q][1] = (int) Math.round(-dot * 1000);
        }
        Arrays.sort(order, Comparator.<int[]>comparingInt(t -> t[1]).thenComparingInt(t -> t[0]));
        return order;
    }

    /** Which ground a fort may stand on, as a summed-area table so a rectangle is one lookup. */
    private static boolean looseStone(Doodad doodad) {
        String id = doodad.definitionId();
        return id != null && RockDamage.IDS.contains(id);
    }

    /** Takes loose stone off every cell the predicate claims, leaving open floor where it stood. */
    private static void levelRocks(GenContext ctx, BiPredicate<Integer, Integer> claimed) {
        List<Doodad> gone = new ArrayList<>();
        for (Doodad d : ctx.doodads) {
            if (!looseStone(d)) continue;
            boolean hit = false;
            for (int dx = 0; dx < d.footprintCellsX && !hit; dx++) {
                for (int dy = 0; dy < d.footprintCellsY && !hit; dy++) hit = claimed.test(d.cellX + dx, d.cellY + dy);
            }
            if (!hit) continue;
            gone.add(d);
            for (int dx = 0; dx < d.footprintCellsX; dx++) {
                for (int dy = 0; dy < d.footprintCellsY; dy++) {
                    int x = d.cellX + dx, y = d.cellY + dy;
                    if (!ctx.grid.inBounds(x, y) || ctx.topology.isWall(x, y)) continue;
                    ctx.grid.setWalkableFloor(x, y);
                    ctx.topology.setFixture(x, y, false);
                }
            }
        }
        ctx.doodads.removeAll(gone);
    }

    private static final class Ground {
        private final int width;
        private final int height;
        private final boolean[][] bad;
        private final boolean[][] rock;
        private int[][] sums;
        private boolean dirty = true;

        Ground(GenContext ctx) {
            width = ctx.width;
            height = ctx.height;
            bad = new boolean[width][height];
            rock = new boolean[width][height];
            // Loose stone is scatter, not ground the city holds: a fort levels it. Mountains and shelves stay.
            for (Doodad doodad : ctx.doodads) {
                if (!looseStone(doodad)) continue;
                for (int dx = 0; dx < doodad.footprintCellsX; dx++) {
                    for (int dy = 0; dy < doodad.footprintCellsY; dy++) {
                        int rx = doodad.cellX + dx, ry = doodad.cellY + dy;
                        if (rx < width && ry < height && !ctx.grid.isWalkable(rx, ry) && ctx.topology.isFixture(rx, ry)
                                && !ctx.topology.isWall(rx, ry)) {
                            rock[rx][ry] = true;
                        }
                    }
                }
            }
            NavigationGrid grid = ctx.grid;
            CellTopology topology = ctx.topology;
            boolean[][] road = ctx.get(BspKeys.ROAD_RESERVATION);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    boolean levelled = rock[x][y];
                    boolean refuse = (!levelled && (!grid.isWalkable(x, y) || topology.isFixture(x, y)))
                            || REFUSED_GROUND.contains(topology.getGroundKind(x, y))
                            || topology.isWall(x, y)
                            || PlacementGuards.touchesDoorway(grid, x, y)
                            || (road != null && road[x][y]);
                    bad[x][y] = refuse;
                }
            }
            int[][] claim = ctx.get(BspKeys.PRECINCT_CLAIM);
            if (claim != null) {
                // A place the plan has claimed is prior: the fort stands in the open country between them.
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) if (claim[x][y] != GrownTrunkPlan.UNOWNED) bad[x][y] = true;
                }
            }
            for (TacticalNode node : ctx.tactical) {
                markBox(node.left - MARGIN, node.top - MARGIN, node.right + MARGIN, node.bottom + MARGIN);
            }
            for (LandingPad pad : ctx.landingPads) {
                markBox(pad.left() - 8, pad.bottom() - 8, pad.right() + 8, pad.top() + 8);
            }
            for (DefensePost post : ctx.defensePosts) {
                markBox(post.anchorX - 4, post.anchorY - 4, post.anchorX + 4, post.anchorY + 4);
            }
            for (var poi : ctx.pois) {
                markBox(poi.left - MARGIN, poi.top - MARGIN, poi.right + MARGIN, poi.bottom + MARGIN);
            }
        }

        private void markBox(int x0, int y0, int x1, int y1) {
            for (int x = Math.max(0, x0); x <= Math.min(width - 1, x1); x++) {
                for (int y = Math.max(0, y0); y <= Math.min(height - 1, y1); y++) bad[x][y] = true;
            }
            dirty = true;
        }

        /** Takes a placed fort's whole footprint, belt included, and a margin: forts never stand flush. */
        void exclude(CompoundLayout.Placement at) {
            int[] a = at.cell(0, 0);
            int[] b = at.cell(BatteryFort.WIDTH - 1, BatteryFort.HEIGHT - 1);
            markBox(Math.min(a[0], b[0]) - MARGIN, Math.min(a[1], b[1]) - MARGIN,
                    Math.max(a[0], b[0]) + MARGIN, Math.max(a[1], b[1]) + MARGIN);
        }

        /** Whether the cell is loose stone this fort may level. */
        boolean isRock(int x, int y) {
            return x >= 0 && y >= 0 && x < width && y < height && rock[x][y];
        }

        /** Whether one cell is open ground: in bounds and not taken by the city, a road or another fort. */
        boolean ok(int x, int y) {
            return x >= 0 && y >= 0 && x < width && y < height && !bad[x][y];
        }

        /** Takes one cell, so no later fort's belt can be drawn over it. */
        void take(int x, int y) {
            if (x >= 0 && y >= 0 && x < width && y < height) {
                bad[x][y] = true;
                dirty = true;
            }
        }

        /** Whether every cell of the works and their glacis is open ground when the layout is placed as given. */
        boolean clear(CompoundLayout.Placement at) {
            for (int[] local : BatteryFort.clearCells()) {
                int[] cell = at.cell(local[0], local[1]);
                if (!ok(cell[0], cell[1])) return false;
            }
            return true;
        }

        boolean clear(int x, int y, int w, int h) {
            if (x < 0 || y < 0 || x + w > width || y + h > height) return false;
            if (dirty) rebuild();
            int bads = sums[x + w][y + h] - sums[x][y + h] - sums[x + w][y] + sums[x][y];
            return bads == 0;
        }

        private void rebuild() {
            sums = new int[width + 1][height + 1];
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    sums[x + 1][y + 1] = sums[x][y + 1] + sums[x + 1][y] - sums[x][y] + (bad[x][y] ? 1 : 0);
                }
            }
            dirty = false;
        }
    }

    // ----------------------------------------------------------------------------------------- stamp

    static void stamp(GenContext ctx, CompoundLayout.Placement at, int fortId) {
        stamp(ctx, at, fortId, null);
    }

    /**
     * Stamps one fort from the outside in: the works and their glacis first, then the buildings, structures and wall,
     * the ground and interior dressing, and last the belt, which takes only the cells the ground will give it. A null
     * {@code ground} means open country everywhere, which is what a bare test field is.
     */
    private static void stamp(GenContext ctx, CompoundLayout.Placement at, int fortId, Ground ground) {
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        int w = BatteryFort.WIDTH;
        int h = BatteryFort.HEIGHT;
        boolean[][] cleared = new boolean[ctx.width][ctx.height];
        for (int[] local : BatteryFort.clearCells()) {
            int[] cell = at.cell(local[0], local[1]);
            if (ctx.grid.inBounds(cell[0], cell[1])) cleared[cell[0]][cell[1]] = true;
        }
        // The glacis is open ground: nothing of the country's scatter stands in front of the works.
        levelRocks(ctx, (x, y) -> ctx.grid.inBounds(x, y) && cleared[x][y]);
        ctx.doodads.removeIf(d -> ctx.grid.inBounds(d.cellX, d.cellY) && cleared[d.cellX][d.cellY]);

        // The ground: every cell of the works starts as bare earth; aprons and lanes are paved over it below.
        for (int lx = 0; lx < w; lx++) {
            for (int ly = 0; ly < h; ly++) {
                if (!BatteryFort.isWorks(lx, ly)) continue;
                int[] cell = at.cell(lx, ly);
                grid.setWalkableFloor(cell[0], cell[1]);
                topology.setNatureOverlayIndex(cell[0], cell[1], -1);
                topology.setGroundKind(cell[0], cell[1], GroundKind.DIRT);
                // No surface yet: a wall ring is dressed only where nobody has given the cell one, so the paving
                // goes down last, over what is still open court.
                topology.setSurface(cell[0], cell[1], 0);
            }
        }

        // The buildings: shelled, windowed and doored by the compound machinery, left bare for their cores.
        List<RoomPacker.Placed> rooms = at.buildings();
        FortressInterior.author(ctx, rooms, rooms);
        for (RoomPacker.Placed room : rooms) {
            for (int[] cell : room.shape().wall()) {
                int x = room.originX() + cell[0];
                int y = room.originY() + cell[1];
                if (grid.isWalkable(x, y)) continue;
                // Finalize tags walls from what is not walkable, and it has run; here that would also tag the
                // structures, so the building rings are tagged by hand.
                grid.setWallHp(x, y, BUILDING_WALL_HP);
                topology.setWall(x, y, true);
            }
        }

        // The structures: one core in each aspect building, the platform, and a nest in each bastion.
        for (FortAspect aspect : FortAspect.values()) {
            String id = switch (aspect) {
                case FIRE_CONTROL -> TurretCatalogRegistry.FORT_FIRE_CONTROL_STRUCTURE_ID;
                case MAGAZINE -> TurretCatalogRegistry.FORT_MAGAZINE_STRUCTURE_ID;
                case POWER -> TurretCatalogRegistry.FORT_POWER_STRUCTURE_ID;
            };
            int[] centre = BatteryFort.coreCentre(aspect);
            emitStructure(ctx, id, at.cell(centre[0], centre[1]), new FortTag(fortId, aspect, at.x(), at.y(), at.pose().quarterTurns()));
        }
        emitStructure(ctx, TurretCatalogRegistry.FORTRESS_BATTERY_STRUCTURE_ID,
                at.cell(BatteryFort.PLATFORM[0], BatteryFort.PLATFORM[1]),
                new FortTag(fortId, null, at.x(), at.y(), at.pose().quarterTurns()));
        int nestNumber = 0;
        for (int[] local : BatteryFort.NESTS) {
            emitNest(ctx, at.cell(local[0], local[1]), fortId, at, nestNumber++);
        }

        stampWall(ctx, at);

        // The ground: compacted yard inside the wall with concrete aprons round every building, and paving only on
        // the lanes vehicles actually drive. Marked as made ground once dressed so the landscape stages leave it alone.
        boolean[][] yard = new boolean[ctx.width][ctx.height];
        boolean[][] lane = new boolean[ctx.width][ctx.height];
        for (int lx = 0; lx < w; lx++) {
            for (int ly = 0; ly < h; ly++) {
                if (!BatteryFort.isWorks(lx, ly) || BatteryFort.isWall(lx, ly)) continue;
                int[] cell = at.cell(lx, ly);
                yard[cell[0]][cell[1]] = true;
            }
        }
        InstallationGround.dress(ctx, yard, new boolean[ctx.width][ctx.height], rooms);
        for (int[] rect : BatteryFort.LANES) {
            for (int dx = 0; dx < rect[2]; dx++) {
                for (int dy = 0; dy < rect[3]; dy++) {
                    int[] cell = at.cell(rect[0] + dx, rect[1] + dy);
                    if (grid.inBounds(cell[0], cell[1]) && grid.isWalkable(cell[0], cell[1])) {
                        pave(ctx, cell[0], cell[1]);
                        lane[cell[0]][cell[1]] = true;
                    }
                }
            }
        }
        for (int lx = 0; lx < w; lx++) {
            for (int ly = 0; ly < h; ly++) {
                if (!BatteryFort.isWorks(lx, ly)) continue;
                int[] cell = at.cell(lx, ly);
                ctx.markMadeGround(cell[0], cell[1]);
                // The gate cells are on the wall, outside the dressed yard: they take the paving.
                if (!yard[cell[0]][cell[1]] && grid.isWalkable(cell[0], cell[1])) pave(ctx, cell[0], cell[1]);
            }
        }
        paveGates(ctx, at);
        dressInterior(ctx, at, lane);
        stampBelt(ctx, at, fortId, ground);
    }

    private static void emitNest(GenContext ctx, int[] cell, int fortId, CompoundLayout.Placement at, int number) {
        ctx.grid.setWalkable(cell[0], cell[1], false);
        ctx.defensePosts.add(new DefensePost(DefensePostKind.LIGHT, cell[0], cell[1],
                List.of(new DefensePost.TurretSpec(NEST_STRUCTURES[number % NEST_STRUCTURES.length], cell[0], cell[1])),
                new FortTag(fortId, null, at.x(), at.y(), at.pose().quarterTurns())));
    }

    /**
     * The outer belt: trench, sandbag line, wire and nests, drawn only on cells the ground gives up. A cell the city
     * holds is skipped, so the belt follows the land rather than overriding it.
     */
    private static void stampBelt(GenContext ctx, CompoundLayout.Placement at, int fortId, Ground ground) {
        FortDressing draw = new FortDressing(ctx, at);
        Set<Long> taken = new HashSet<>();
        int nestNumber = BatteryFort.NESTS.size();
        for (FortBelt.Cell c : FortBelt.cells()) {
            if (BatteryFort.isWorks(c.x(), c.y())) continue;
            int[] cell = at.cell(c.x(), c.y());
            if (!ctx.grid.inBounds(cell[0], cell[1]) || (ground != null && !ground.ok(cell[0], cell[1]))) continue;
            boolean levelled = ground != null && ground.isRock(cell[0], cell[1]);
            if (!levelled && (!ctx.grid.isWalkable(cell[0], cell[1]) || ctx.topology.isFixture(cell[0], cell[1]))) continue;
            taken.add(FortDressing.key(cell[0], cell[1]));
            if (ground != null) ground.take(cell[0], cell[1]);
        }
        levelRocks(ctx, (x, y) -> taken.contains(FortDressing.key(x, y)));
        ctx.doodads.removeIf(d -> taken.contains(FortDressing.key(d.cellX, d.cellY)));
        Set<Long> trench = new HashSet<>();
        for (FortBelt.Cell c : FortBelt.cells()) {
            int[] cell = at.cell(c.x(), c.y());
            if (c.role() == FortBelt.Role.TRENCH && taken.contains(FortDressing.key(cell[0], cell[1]))) {
                trench.add(FortDressing.key(cell[0], cell[1]));
            }
        }
        for (FortBelt.Cell c : FortBelt.cells()) {
            int[] cell = at.cell(c.x(), c.y());
            if (!taken.contains(FortDressing.key(cell[0], cell[1]))) continue;
            switch (c.role()) {
                case TRENCH -> {
                    draw.earth(c.x(), c.y());
                    draw.trench(cell[0], cell[1], trench);
                }
                case EARTH -> draw.earth(c.x(), c.y());
                case BAG -> {
                    draw.earth(c.x(), c.y());
                    draw.sandbag(c.x(), c.y(), c.dx(), c.dy(), c.cornerDx(), c.cornerDy());
                }
                case WIRE -> draw.concertina(c.x(), c.y(), c.dx() != 0);
                case NEST -> {
                    draw.earth(c.x(), c.y());
                    emitNest(ctx, cell, fortId, at, nestNumber++);
                }
            }
        }
    }

    /** The interior: gun pit, berms and yard furniture, kept off the paved lanes. */
    private static void dressInterior(GenContext ctx, CompoundLayout.Placement at, boolean[][] lane) {
        FortDressing d = new FortDressing(ctx, at);
        int ox = BatteryFort.OX, oy = BatteryFort.OY;
        // The battery sits in a revetted pit: a gravel floor inside two sandbag rings whose gaps do not line up, so the
        // way in turns once and the guns are never open to a straight shot from the gate.
        int px = BatteryFort.PLATFORM[0], py = BatteryFort.PLATFORM[1];
        for (int dx = -3; dx <= 3; dx++) for (int dy = -3; dy <= 3; dy++) d.gravel(px + dx, py + dy);
        d.ring(px - 1, py - 1, 3, 3, 3, -1, 0, py, 3);
        d.ring(px - 1, py - 1, 3, 3, 5, 0, -1, px, 3);

        // The fire-control bunker is low and hardened: one ring of bags a step off its walls, open at its door.
        int[] bunker = BatteryFort.floor(FortAspect.FIRE_CONTROL);
        int[] bunkerDoor = BatteryFort.door(FortAspect.FIRE_CONTROL);
        d.ring(bunker[0] - 1, bunker[1] - 1, bunker[2] + 2, bunker[3] + 2, 2, 0, 1, bunkerDoor[0], 3);
        // The magazine stands apart from the guns inside a double berm.
        int[] mag = BatteryFort.floor(FortAspect.MAGAZINE);
        int[] magDoor = BatteryFort.door(FortAspect.MAGAZINE);
        d.ring(mag[0] - 1, mag[1] - 1, mag[2] + 2, mag[3] + 2, 2, 0, 1, magDoor[0], 3);
        d.ring(mag[0] - 1, mag[1] - 1, mag[2] + 2, mag[3] + 2, 4, 0, 1, magDoor[0], 3);

        // Firing lines inside each gate cover the baffle, so a breach still has to cross open ground.
        d.line(ox + 9, oy + 15, ox + 9, oy + 19, -1, 0);
        d.line(ox + 19, oy + 9, ox + 27, oy + 9, 0, -1);

        // The power plant's fenced yard, with its plant standing in it.
        for (int x = ox + 20; x <= ox + 28; x++) {
            if (x < ox + 23 || x > ox + 25) d.wire(x, oy + 24, true);
            d.wire(x, oy + 32, true);
        }
        for (int y = oy + 25; y <= oy + 31; y++) {
            if (y < oy + 27 || y > oy + 29) d.wire(ox + 28, y, false);
        }
        d.prop(ox + 22, oy + 26, "industrial-turbine-set", lane);
        d.prop(ox + 22, oy + 29, "industrial-reactor-housing", lane);
        d.prop(ox + 25, oy + 29, "industrial-generator", lane);
        d.prop(ox + 26, oy + 29, "industrial-generator", lane);
        d.prop(ox + 27, oy + 26, "industrial-fluid-tank", lane);
        d.prop(ox + 27, oy + 27, "industrial-fluid-tank", lane);
        d.prop(ox + 22, oy + 31, "industrial-switchboard", lane);

        // Yard clutter that is cover as well: stores by the magazine, a parked carrier, spools and drums.
        d.prop(ox + 29, oy + 12, "industrial-crate-stack", lane);
        d.prop(ox + 30, oy + 12, "industrial-crate-stack", lane);
        d.prop(ox + 29, oy + 13, "industrial-crate-stack", lane);
        d.prop(ox + 29, oy + 15, "industrial-pallet-stack", lane);
        d.prop(ox + 16, oy + 15, "parked-armored-apc", lane);
        d.prop(ox + 16, oy + 17, "industrial-cable-reel", lane);
        d.prop(ox + 17, oy + 17, "industrial-drum-cluster", lane);
        d.prop(ox + 32, oy + 16, "industrial-scrap-pile", lane);
        d.prop(ox + 42, oy + 16, "industrial-crate-stack", lane);
        d.prop(ox + 43, oy + 16, "industrial-crate-stack", lane);
        d.prop(ox + 18, oy + 12, "military-radar-dish", lane);
    }

    private static void emitStructure(GenContext ctx, String structureId, int[] centre, FortTag tag) {
        StructureDef def = TurretCatalogRegistry.requireStructure(structureId);
        StructureFootprint.seal(ctx.grid, def, centre[0], centre[1]);
        ctx.defensePosts.add(new DefensePost(DefensePostKind.LIGHT, centre[0], centre[1],
                List.of(new DefensePost.TurretSpec(def.id, centre[0], centre[1])), tag));
    }

    /** The outer wall: the footprint ring less the gates, with a face on every open side so it draws. */
    private static void stampWall(GenContext ctx, CompoundLayout.Placement at) {
        boolean[][] wall = new boolean[ctx.width][ctx.height];
        for (int lx = 0; lx < BatteryFort.WIDTH; lx++) {
            for (int ly = 0; ly < BatteryFort.HEIGHT; ly++) {
                if (!BatteryFort.isWall(lx, ly)) continue;
                int[] cell = at.cell(lx, ly);
                wall[cell[0]][cell[1]] = true;
                ctx.grid.setWalkable(cell[0], cell[1], false);
                ctx.grid.setWallHp(cell[0], cell[1], FORT_WALL_HP);
                ctx.topology.setWall(cell[0], cell[1], true);
                ctx.topology.setGroundKind(cell[0], cell[1], GroundKind.STRIPED);
            }
        }
        for (int lx = 0; lx < BatteryFort.WIDTH; lx++) {
            for (int ly = 0; ly < BatteryFort.HEIGHT; ly++) {
                if (!BatteryFort.isWall(lx, ly)) continue;
                int[] cell = at.cell(lx, ly);
                int x = cell[0], y = cell[1];
                int mask = 0;
                if (y + 1 >= ctx.height || !wall[x][y + 1]) mask |= CellTopology.WALL_DIR_N;
                if (y == 0 || !wall[x][y - 1]) mask |= CellTopology.WALL_DIR_S;
                if (x + 1 >= ctx.width || !wall[x + 1][y]) mask |= CellTopology.WALL_DIR_E;
                if (x == 0 || !wall[x - 1][y]) mask |= CellTopology.WALL_DIR_W;
                ctx.topology.setWallDirMask(x, y, mask);
            }
        }
        InstallationWalls.perimeter(ctx, wall);
    }

    /** Paves a short apron outside each gate, and a lane from it to the nearest road when open ground allows. */
    private static void paveGates(GenContext ctx, CompoundLayout.Placement at) {
        boolean[][] road = ctx.get(BspKeys.ROAD_RESERVATION);
        int[][] outward = {{-1, 0}, {0, -1}};
        for (int gate = 0; gate < BatteryFort.GATE_MOUTHS.size(); gate++) {
            int[] mouth = BatteryFort.GATE_MOUTHS.get(gate);
            int nx = outward[gate][0];
            int ny = outward[gate][1];
            int px = ny != 0 ? 1 : 0;
            int py = nx != 0 ? 1 : 0;
            for (int d = 0; d < 4; d++) {
                for (int l = -2; l <= 2; l++) {
                    int[] cell = at.cell(mouth[0] + nx * d + px * l, mouth[1] + ny * d + py * l);
                    if (ctx.grid.inBounds(cell[0], cell[1]) && ctx.grid.isWalkable(cell[0], cell[1])) {
                        pave(ctx, cell[0], cell[1]);
                    }
                }
            }
            if (road == null) continue;
            int[] start = at.cell(mouth[0] + nx * 3, mouth[1] + ny * 3);
            int[] goal = nearestRoad(ctx, road, start[0], start[1]);
            if (goal == null) continue;
            int[] path = GridPathfinder.findPath(ctx.grid, start[0], start[1], goal[0], goal[1]);
            if (path.length == 0 || path.length / 2 > MAX_LINK_CELLS) continue;
            if (!laneIsOpenGround(ctx, path)) continue;
            for (int i = 0; i + 1 < path.length; i += 2) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int x = path[i] + dx, y = path[i + 1] + dy;
                        if (ctx.grid.inBounds(x, y) && ctx.grid.isWalkable(x, y)
                                && !ctx.grid.isDoorway(x, y) && openGround(ctx.topology.getGroundKind(x, y))) {
                            pave(ctx, x, y);
                        }
                    }
                }
            }
        }
    }

    private static void pave(GenContext ctx, int x, int y) {
        ctx.topology.setGroundKind(x, y, GroundKind.COURTYARD);
        ctx.topology.setSurface(x, y, ctx.topology.surfaceIndex(
                Material.CONCRETE.block(RoadRasterizer.age(.55, ctx.seed, x, y))));
        ctx.markMadeGround(x, y);
    }

    private static boolean openGround(GroundKind kind) {
        return !REFUSED_GROUND.contains(kind);
    }

    /** Whether the whole lane crosses open outdoor ground, so paving it can never run through a building. */
    private static boolean laneIsOpenGround(GenContext ctx, int[] path) {
        for (int i = 0; i + 1 < path.length; i += 2) {
            GroundKind kind = ctx.topology.getGroundKind(path[i], path[i + 1]);
            if (kind == GroundKind.INDOOR || kind == GroundKind.WATER || ctx.grid.isDoorway(path[i], path[i + 1])) {
                return false;
            }
        }
        return true;
    }

    private static int[] nearestRoad(GenContext ctx, boolean[][] road, int x, int y) {
        int[] best = null;
        long bestSq = Long.MAX_VALUE;
        for (int rx = 0; rx < ctx.width; rx++) {
            for (int ry = 0; ry < ctx.height; ry++) {
                if (!road[rx][ry] || !ctx.grid.isWalkable(rx, ry)) continue;
                long dx = rx - x, dy = ry - y;
                long sq = dx * dx + dy * dy;
                if (sq < bestSq) {
                    bestSq = sq;
                    best = new int[]{rx, ry};
                }
            }
        }
        return best;
    }

    // ---------------------------------------------------------------------------------------- finish

    /**
     * Brings the derived layers up to what was stamped after finalize had already run: cover round each fort and the
     * building list the roofs and fog read, which now includes the fort's own buildings.
     */
    private void finish(GenContext ctx) {
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        for (Placed fort : placed) {
            int[] a = fort.placement().cell(0, 0);
            int[] b = fort.placement().cell(BatteryFort.WIDTH - 1, BatteryFort.HEIGHT - 1);
            for (int y = Math.max(0, Math.min(a[1], b[1]) - 2); y <= Math.min(ctx.height - 1, Math.max(a[1], b[1]) + 2); y++) {
                for (int x = Math.max(0, Math.min(a[0], b[0]) - 2); x <= Math.min(ctx.width - 1, Math.max(a[0], b[0]) + 2); x++) {
                    grid.recomputeCoverAt(x, y);
                }
            }
        }
        ctx.put(BspKeys.BUILDINGS, BuildingFloodFill.populate(topology, ctx.seed));
    }
}
