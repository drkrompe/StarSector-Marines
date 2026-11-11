package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.StreetDressing;
import com.dillon.starsectormarines.battle.world.gen.nature.FormationBrush;
import com.dillon.starsectormarines.battle.world.gen.nature.MineralFormation;
import com.dillon.starsectormarines.battle.world.gen.precinct.FieldOfFire;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A mine's country round its pit and ore line ({@code long-survey-maps.md}, Chapter 2): spoil heaps and tailings
 * mounds, ore carts tipped off their rails, a stalled ore train, a pumping station on the pit's rim and ruined
 * headframes, each where the hull patch's {@code dress} names it ({@link HullSitePlan.Dress}). Laid with the crash
 * site's finish, before the claim lattice seats round it.
 *
 * <p>The heaps and mounds stand on the planet's own ground, their art's rims soft against it. As the debris
 * shower's pieces do ({@link DebrisShower}), a piece worth taking cover behind stands solid with a ring
 * of open ground round it, and a flat one only lies on the ground. Nothing that stands is laid on a road, the hull, its
 * trench or aprons, the landing's field of fire, near a claim lattice grid point, or within {@link #SIGHTLINE} of the
 * ore line, so the line's open ground stays a field of fire along its length. A piece that does not fit is left out.
 */
public final class MineCountry {

    /** Cells either side of the ore line kept clear of anything in the way. UNTUNED. */
    static final int SIGHTLINE = 5;
    /** Open ground round a piece in the way, and cells kept off a claim lattice grid point. */
    static final int RING = 2, STAKE_CLEAR = 6;
    /** The art each piece is made of. */
    static final String SPOIL = "doodad.ruins-spoil-heap", SLIDE = "doodad.ruins-spoil-slide",
            SLAG = "doodad.ruins-slag-heap", HEADFRAME = "doodad.ruins-headframe",
            GIRDER = "doodad.ruins-fallen-girder", SCRAP = "doodad.industrial-scrap-pile",
            RUBBLE = "doodad.decal-rubble-2", RAIL = "doodad.ruins-mag-rail",
            CAR = "doodad.ruins-unit-carrier-derailed", POD = "doodad.ruins-feed-pod-derailed",
            ENGINE = "doodad.ruins-bed-changer-derailed", TANK = "doodad.fuel-tank-vertical",
            FLUID = "doodad.industrial-fluid-tank", PUMP = "doodad.industrial-pump-set",
            RADIATOR = "doodad.radiator-skid";
    /**
     * The mine's own pieces, made for it; until a sheet holds one, its stand-in above is drawn in its place
     * ({@link #STAND_IN}). A pump house stands on the pumping station's pad in place of its kit.
     */
    static final String MINE_SPOIL = "doodad.mine-spoil-heap", MINE_TAILINGS = "doodad.mine-tailings-mound",
            MINE_CAR = "doodad.mine-ore-car", MINE_HEADFRAME = "doodad.mine-headframe",
            MINE_PUMP_HOUSE = "doodad.mine-pump-house";
    static final Map<String, String> STAND_IN = Map.of(MINE_SPOIL, SPOIL, MINE_TAILINGS, SLIDE, MINE_CAR, CAR,
            MINE_HEADFRAME, HEADFRAME);
    /** A route's cover, by turns: a spoil heap, a wagon, a slag heap, each at {@link #BIG} times its sheet. */
    static final String[] COVER = {MINE_SPOIL, MINE_CAR, SLAG};
    /**
     * Cells between two pieces of a route's cover, and either side of its line they stand by turns; a cluster's pieces
     * stand this far about its stake; a piece that does not fit its cell is tried up to {@code SEARCH} cells off it.
     * UNTUNED.
     */
    static final int COVER_STEP = 8, COVER_SIDE = 4, CLUSTER_REACH = 9, SEARCH = 3;
    /** The ore line's art: its pieces mark the line the sightline is kept along. */
    static final String ORE_LINE = "ore-conveyor.";
    /** What the mine's pieces are drawn at, times their sheet: a wagon, a heap, read at a map's scale. */
    static final int BIG = 2;
    /** Cells from one wagon's front to the next's: a wagon of six and two of coupling gap. */
    static final int TRAIN_PITCH = 8;
    /** A headframe's frame, cells square. */
    static final int FRAME = 12;
    /** The pumping station's pad. */
    static final int PAD_W = 10, PAD_H = 7;

    private final GenContext ctx;
    private final TileRegistry registry;
    private final BitSet hull, trench, aprons, taken, sight;
    private final boolean[][] roads;
    private final FieldOfFire fire;
    private final int w, h, latticeX, latticeY;

    private MineCountry(GenContext ctx, TileRegistry registry, BitSet hull, BitSet trench, BitSet aprons,
                        FieldOfFire fire, int latticeX, int latticeY) {
        this.ctx = ctx;
        this.registry = registry;
        this.hull = hull;
        this.trench = trench;
        this.aprons = aprons;
        this.fire = fire;
        this.w = ctx.width;
        this.h = ctx.height;
        this.latticeX = latticeX;
        this.latticeY = latticeY;
        this.roads = ctx.get(BspKeys.ROAD_RESERVATION);
        this.taken = new BitSet(w * h);
        this.sight = sightline(ctx);
    }

    /** Lays {@code dress} on {@code ctx}; the claim lattice's grid is about {@code (latticeX, latticeY)}. */
    static void lay(GenContext ctx, List<HullSitePlan.Dress> dress, BitSet hull, BitSet trench, BitSet aprons,
                    FieldOfFire fire, int latticeX, int latticeY) {
        TileRegistry registry = TileRegistry.installed();
        if (dress == null || dress.isEmpty() || registry == null) return;
        MineCountry country = new MineCountry(ctx, registry, hull, trench, aprons, fire, latticeX, latticeY);
        for (HullSitePlan.Dress d : dress) country.piece(d);
    }

    private void piece(HullSitePlan.Dress d) {
        int x = d.x(), y = d.y();
        switch (d.kind()) {
            case SPOIL -> {
                big(MINE_SPOIL, x, y);
                big(MINE_TAILINGS, x + 9, y - 2);
                big(SLAG, x - 5, y + 2);
            }
            case TAILINGS -> {
                int r = Math.max(6, d.length());
                big(MINE_TAILINGS, x - r + 1, y - 2);
                big(MINE_TAILINGS, x + 1, y + 3);
                big(MINE_SPOIL, x - 4, y - r + 1);
                big(SLAG, x + r - 5, y - 4);
            }
            case HEADFRAME -> headframe(x, y);
            case TRAIN -> train(x, y, Math.max(12, d.length()));
            case CARTS -> {
                big(MINE_CAR, x, y);
                big(POD, x + 7, y + 2);
                big(MINE_CAR, x + 2, y + 9);
            }
            case PUMPS -> pumps(x, y, d.length());
            case ROUTE -> {
                int n = 0;
                for (int at = 0; at <= Math.max(0, d.length()); at += COVER_STEP, n++) {
                    cover(x + (n % 2 == 0 ? -COVER_SIDE : COVER_SIDE), y + at, n);
                }
            }
            case CLUSTER -> {
                int[][] about = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
                for (int i = 0; i < about.length; i++) {
                    cover(x + about[i][0] * CLUSTER_REACH, y + about[i][1] * CLUSTER_REACH, i + x + y);
                }
            }
        }
    }

    /**
     * The {@code n}th piece of hard cover ({@link #COVER}, by turns) at {@code (x, y)}, or the nearest cell within
     * {@link #SEARCH} it fits; left out when none does.
     */
    private boolean cover(int x, int y, int n) {
        String id = COVER[Math.floorMod(n, COVER.length)];
        for (int r = 0; r <= SEARCH; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if (big(id, x + dx, y + dy)) return true;
                }
            }
        }
        return false;
    }

    /** {@code id}'s art: the mine's own piece where a sheet holds it, else its stand-in ({@link #STAND_IN}). */
    private DoodadDef art(String id) {
        DoodadDef def = registry.doodad(id);
        String standIn = STAND_IN.get(id);
        return def != null || standIn == null ? def : registry.doodad(standIn);
    }

    /**
     * A ruined headframe: its frame standing over the shaft with its four legs solid, a girder fallen off it, scrap and
     * rubble under it and spoil beside it.
     */
    private void headframe(int x, int y) {
        DoodadDef frame = art(MINE_HEADFRAME);
        if (frame == null || !fits(x, y, FRAME, FRAME, false)) return;
        clearFormations(x, y, x + FRAME - 1, y + FRAME - 1);
        for (int yy = y; yy < y + FRAME; yy++) for (int xx = x; xx < x + FRAME; xx++) taken.set(yy * w + xx);
        DoodadDef rubble = registry.doodad(RUBBLE);
        if (rubble != null) ctx.doodads.add(0, new Doodad(x + FRAME / 2 - 1, y + FRAME / 2 - 1, rubble));
        for (int[] leg : new int[][]{{x, y}, {x + FRAME - 1, y}, {x, y + FRAME - 1}, {x + FRAME - 1, y + FRAME - 1}}) {
            fixture(leg[0], leg[1]);
        }
        ctx.doodads.add(new Doodad(x, y, frame));
        put(GIRDER, x + 2, y - 3, 0);
        put(SCRAP, x + FRAME + 2, y + 4, 0);
        put(MINE_SPOIL, x + FRAME + 2, y + 7, 0);
    }

    /**
     * A stalled ore train: its rail laid flat along the run, the engine at its head and the cars behind it, each knocked
     * a cell off the rail by turns, {@link #TRAIN_PITCH} apart so a figure gets between them; it runs north along y, as its
     * art is drawn.
     */
    private void train(int x, int y, int length) {
        // The rail under the cars' middle: the cars stand on it at twice their sheet, two cells either side.
        for (int i = 0; i + 2 <= length; i += 2) rail(x + 1, y + i, 0);
        int at = 0, car = 0;
        if (big(ENGINE, x - 1, y)) at = TRAIN_PITCH;
        for (; at + 6 <= length; at += TRAIN_PITCH, car++) big(car % 3 == 2 ? POD : MINE_CAR, x + car % 2, y + at);
    }

    /**
     * A pumping station on the pit's rim: its pad, a tank, fluid tanks, pump sets and a radiator, and its rising main
     * run {@code reach} cells north toward the pit as far as open ground goes.
     */
    private void pumps(int x, int y, int reach) {
        if (!fits(x, y, PAD_W, PAD_H, false)) return;
        clearFormations(x, y, x + PAD_W - 1, y + PAD_H - 1);
        for (int yy = y; yy < y + PAD_H; yy++) {
            for (int xx = x; xx < x + PAD_W; xx++) {
                ctx.topology.setGroundKind(xx, yy, GroundKind.COURTYARD);
                ctx.topology.setSurface(xx, yy, 0);
                ctx.topology.setNatureOverlayIndex(xx, yy, -1);
                ctx.markMadeGround(xx, yy);
            }
        }
        if (!put(MINE_PUMP_HOUSE, x + 1, y + 1, 0, true)) {
            put(TANK, x + 1, y + 2, 0, true);
            put(FLUID, x + 6, y + 5, 0, true);
            put(FLUID, x + 8, y + 5, 0, true);
            put(PUMP, x + 5, y + 2, 0, true);
            put(PUMP, x + 7, y + 2, 0, true);
            put(RADIATOR, x + 6, y, 0, true);
        }
        int px = x + PAD_W / 2 - 1, top = y + PAD_H;
        int end = top - 1;
        while (end + 1 < top + Math.max(0, reach) && openAt(px, end + 1, true)) end++;
        if (end - (top - 1) >= 4) {
            StreetDressing.pipeRun(ctx.grid, ctx.topology, ctx.doodads, px, top - 1, px, end, 4);
            for (int yy = top; yy <= end; yy++) taken.set(yy * w + px);
        }
    }

    /** {@code id} at {@link #BIG} times its sheet, unturned, solid with its ring if it is cover. */
    private boolean big(String id, int x, int y) {
        DoodadDef def = art(id);
        if (def == null) return false;
        boolean blocks = def.cover == DoodadCover.MED || def.cover == DoodadCover.HEAVY;
        return place(def, x, y, 0, blocks, false, BIG);
    }

    private boolean put(String id, int x, int y, int turns) {
        return put(id, x, y, turns, false);
    }

    /** One piece from {@code (x, y)}, solid with its ring if it is cover; on {@code pad}, made ground is its ground. */
    private boolean put(String id, int x, int y, int turns, boolean pad) {
        DoodadDef def = art(id);
        if (def == null) return false;
        boolean blocks = def.cover == DoodadCover.MED || def.cover == DoodadCover.HEAVY;
        return place(def, x, y, turns, blocks, pad, 1);
    }

    /** A rail piece: flat, laid on open ground under whatever stands on it, claiming nothing. */
    private void rail(int x, int y, int turns) {
        DoodadDef def = registry.doodad(RAIL);
        if (def == null || !fits(x, y, 2, 2, false)) return;
        clearFormations(x, y, x + 1, y + 1);
        ctx.doodads.add(0, new Doodad(x, y, def, turns));
    }

    private boolean place(DoodadDef def, int x, int y, int turns, boolean blocks, boolean pad, int scale) {
        int fx = Math.max(1, def.footprintCellsX) * scale, fy = Math.max(1, def.footprintCellsY) * scale;
        if (turns % 2 == 1) {
            int t = fx;
            fx = fy;
            fy = t;
        }
        int ring = blocks && !pad ? RING : 0;
        if (!fits(x - ring, y - ring, fx + 2 * ring, fy + 2 * ring, pad)) return false;
        if (blocks && (nearLattice(x, y, fx, fy) || inSight(x, y, fx, fy))) return false;
        clearFormations(x - ring, y - ring, x + fx + ring - 1, y + fy + ring - 1);
        for (int yy = y; yy < y + fy; yy++) {
            for (int xx = x; xx < x + fx; xx++) {
                taken.set(yy * w + xx);
                if (blocks) fixture(xx, yy);
            }
        }
        Doodad d = scale == 1 ? new Doodad(x, y, def, turns) : scaled(def, scale, x, y);
        if (blocks) ctx.doodads.add(d);
        else ctx.doodads.add(0, d);
        return true;
    }

    /** {@code def} drawn {@code k} times its sheet from {@code (x, y)}: its sheet read at a cell {@code k} times finer. */
    private static Doodad scaled(DoodadDef def, int k, int x, int y) {
        int cell0 = def.sourceCellPx > 0 ? def.sourceCellPx : TileManifest.TILE_SIZE;
        int fx = Math.max(1, def.footprintCellsX), fy = Math.max(1, def.footprintCellsY);
        return new Doodad(x, y, new TileManifest.TileFrame(def.col * k, def.row * k), def.sheetPath,
                def.cover.level(), def.ballisticHalfHeight, fx * k, fy * k, null, cell0 / k);
    }

    private boolean fits(int x0, int y0, int fw, int fh, boolean pad) {
        for (int y = y0; y < y0 + fh; y++) for (int x = x0; x < x0 + fw; x++) if (!openAt(x, y, pad)) return false;
        return true;
    }

    /** Whether a piece may cover the cell: open outdoor ground off the roads, the hull and the landing's field. */
    private boolean openAt(int x, int y, boolean pad) {
        if (x < 2 || y < 2 || x >= w - 2 || y >= h - 2) return false;
        int k = y * w + x;
        if (taken.get(k) || hull.get(k) || trench.get(k) || aprons.get(k)) return false;
        if (roads != null && roads[x][y]) return false;
        if (fire != null && fire.contains(x, y)) return false;
        // The wild ground's formations are cleared for the mine's country: they count as open.
        boolean formation = ctx.grid.hasTag(x, y, CellTag.NATURAL_SOLID);
        if (!formation && (!ctx.grid.isWalkable(x, y) || ctx.topology.isFixture(x, y))) return false;
        if (ctx.grid.isDoorway(x, y) || ctx.topology.isWall(x, y) || ctx.topology.getBuildingId(x, y) != 0) return false;
        GroundKind g = ctx.topology.getGroundKind(x, y);
        if (pad && g == GroundKind.COURTYARD) return true;
        return g != GroundKind.INDOOR && g != GroundKind.WATER && g != GroundKind.VOID && g != GroundKind.STREET
                && (pad || !ctx.isMadeGround(x, y));
    }

    /**
     * Takes the wild ground's formations ({@link FormationBrush}) that reach into the box off the map, their cells open
     * again, as the mine cleared its country: a formation is whole or gone.
     */
    private void clearFormations(int x0, int y0, int x1, int y1) {
        for (Iterator<Doodad> it = ctx.doodads.iterator(); it.hasNext(); ) {
            Doodad d = it.next();
            MineralFormation shape = formation(d.definitionId());
            if (shape == null) continue;
            int span = Math.max(d.footprintCellsX, d.footprintCellsY);
            if (d.cellX > x1 || d.cellY > y1 || d.cellX + span - 1 < x0 || d.cellY + span - 1 < y0) continue;
            boolean reaches = false;
            for (int y = d.cellY; y < d.cellY + span && !reaches; y++) {
                for (int x = d.cellX; x < d.cellX + span && !reaches; x++) {
                    reaches = x >= x0 && x <= x1 && y >= y0 && y <= y1
                            && shape.blocks(x - d.cellX, y - d.cellY, d.quarterTurns);
                }
            }
            if (!reaches) continue;
            it.remove();
            for (int y = d.cellY; y < d.cellY + span; y++) {
                for (int x = d.cellX; x < d.cellX + span; x++) {
                    if (x < 0 || y < 0 || x >= w || y >= h || !shape.blocks(x - d.cellX, y - d.cellY, d.quarterTurns)
                            || !ctx.grid.hasTag(x, y, CellTag.NATURAL_SOLID)) continue;
                    ctx.grid.setTag(x, y, CellTag.NATURAL_SOLID, false);
                    ctx.grid.setWalkable(x, y, true);
                    ctx.grid.setSeeThrough(x, y, true);
                    ctx.topology.setFixture(x, y, false);
                }
            }
        }
    }

    private static MineralFormation formation(String id) {
        if (id == null) return null;
        for (MineralFormation m : MineralFormation.values()) if (m.id().equals(id)) return m;
        return null;
    }

    private boolean nearLattice(int x0, int y0, int fx, int fy) {
        int s = ClaimLattice.SPACING;
        for (int y = y0 - STAKE_CLEAR; y < y0 + fy + STAKE_CLEAR; y++) {
            for (int x = x0 - STAKE_CLEAR; x < x0 + fx + STAKE_CLEAR; x++) {
                if (Math.floorMod(x - latticeX, s) == 0 && Math.floorMod(y - latticeY, s) == 0) return true;
            }
        }
        return false;
    }

    private boolean inSight(int x0, int y0, int fx, int fy) {
        for (int y = y0; y < y0 + fy; y++) for (int x = x0; x < x0 + fx; x++) if (sight.get(y * w + x)) return true;
        return false;
    }

    private void fixture(int x, int y) {
        ctx.grid.setWalkable(x, y, false);
        ctx.grid.setSeeThrough(x, y, true);
        ctx.topology.setFixture(x, y, true);
    }

    /** The cells within {@link #SIGHTLINE} of the ore line's pieces. */
    private static BitSet sightline(GenContext ctx) {
        int w = ctx.width, h = ctx.height;
        BitSet out = new BitSet(w * h);
        for (Doodad d : ctx.doodads) {
            String id = d.definitionId();
            if (id == null || !id.startsWith(ORE_LINE)) continue;
            for (int y = Math.max(0, d.cellY - SIGHTLINE); y <= Math.min(h - 1, d.cellY + SIGHTLINE); y++) {
                for (int x = Math.max(0, d.cellX - SIGHTLINE); x <= Math.min(w - 1, d.cellX + SIGHTLINE); x++) {
                    out.set(y * w + x);
                }
            }
        }
        return out;
    }
}
