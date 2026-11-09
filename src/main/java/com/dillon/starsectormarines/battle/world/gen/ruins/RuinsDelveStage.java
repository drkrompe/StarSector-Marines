package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.LandingArea;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Cell;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Collapse;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Court;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Junction;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Layer;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.LayerKind;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.PowerHall;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.RecordsTerminal;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.SideRoom;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Vault;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Way;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.WayKind;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.WallRemnant;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Lays a ruins site's delve over the dead settlement ({@code ruins-ground.md}): the cleared landing ground at the
 * west end, the vault suite at the east end, the grade's layers across the route between them with their ways
 * through, and, behind each layer, its small ruined rooms, its landing court (every layer past the first) and the
 * works sites. Runs before finalize, so every shell it stamps is an ordinary building and every wall an ordinary
 * breachable wall. Publishes the {@link RuinsDelve} under {@link RuinsKeys#DELVE}.
 *
 * <p>Nothing new is drawn: the layers' walls and the rooms use the ruined-lot materials and rubble, the vault the
 * installation's dark cast concrete, the works the existing industrial equipment.
 *
 * <p>Its own random stream is derived from the seed, so the settlement under it draws exactly what a settlement
 * of that density would.
 */
public final class RuinsDelveStage implements GenStage {

    /** The landing ground runs from the west edge to this column. */
    static final int LANDING_DEPTH = 16;
    /** Berth centres stand on this column, deep enough into the ground that the approach is open air. */
    static final int BERTH_COLUMN = 6;
    /** Half the lateral spread of one arrival area's berths, as the shipped arrival areas have it. */
    static final int BERTH_OFFSET = 4;
    static final int VAULT_WIDTH = 11, VAULT_HEIGHT = 9;
    /** Open ground in front of the vault's door, so its approach is a place to hold. */
    static final int FORECOURT = 9;
    static final int ROOM_WIDTH = 7, ROOM_HEIGHT = 6;
    static final int HALL_WIDTH = 11, HALL_HEIGHT = 8;
    static final int COURT_WIDTH = 17, COURT_HEIGHT = 11;
    /** Rows between the centres of two ways through one layer. */
    static final int WAY_SPACING = 9;
    static final int ROOMS_PER_LAYER = 2;
    /** The most ground a repair may wall in rather than join; more is a layout error, not a pocket. */
    static final int MAX_SEALED_POCKET = 60;

    static final String[] RUIN_WALLS = {"colony.masonry-wall", "colony.ceramic-wall", "colony.olive-wall"};
    static final String[] RUIN_FLOORS = {"colony.concrete", "colony.timber", "colony.terrazzo", "colony.terracotta"};
    static final String GATE_WALL = "installation.dark-concrete-wall";
    static final String VAULT_WALL = "installation.dark-concrete-wall";
    static final String VAULT_FLOOR = "installation.concrete-floor";
    static final String HALL_WALL = "colony.olive-wall";
    static final String HALL_FLOOR = "colony.concrete";
    static final String COURT_FLOOR = "colony.concrete";
    static final String RUBBLE_BED = "pavement.gravel.cracked";
    static final String TRODDEN = "pavement.dirt.worn";
    static final String SCRAP = "doodad.industrial-scrap-pile";
    static final String[] WRECKAGE = {"doodad.shelf-dam-1", "doodad.shelf-dam-2", "doodad.desk-dam",
            "doodad.box-dam", "doodad.chair-s-yellow-dam", "doodad.chair-s-green-dam"};
    static final String GENERATOR = "doodad.industrial-generator";
    static final String CONSOLE = "doodad.industrial-control-console";
    static final String TERMINAL = "doodad.military-command-console-damaged";

    private static final long SALT = 0x52_75_69_6E_73_44_76L;

    @Override
    public void run(GenContext ctx) {
        RuinsGroundPlan plan = ctx.get(RuinsKeys.PLAN);
        if (plan == null) throw new IllegalStateException("RuinsDelveStage requires a RuinsGroundPlan");
        ctx.put(RuinsKeys.DELVE, new Layout(ctx, plan, new Random(ctx.seed ^ SALT)).lay());
    }

    /** One delve being laid. */
    private static final class Layout {
        final GenContext ctx;
        final NavigationGrid grid;
        final CellTopology topology;
        final RuinsGroundPlan plan;
        final Random rng;
        final int w, h;
        /** Ground a later piece must not be placed over. */
        final boolean[] reserved;
        /** Walls the delve stands on: layer lines and its own shells. Never opened by the pocket repair. */
        final boolean[] kept;
        /** Shell cells stamped here, for their wall and door masks. */
        final boolean[] drafted;

        Layout(GenContext ctx, RuinsGroundPlan plan, Random rng) {
            this.ctx = ctx;
            this.grid = ctx.grid;
            this.topology = ctx.topology;
            this.plan = plan;
            this.rng = rng;
            this.w = ctx.width;
            this.h = ctx.height;
            reserved = new boolean[w * h];
            kept = new boolean[w * h];
            drafted = new boolean[w * h];
        }

        RuinsDelve lay() {
            int landingY = clamp(h / 2 + rng.nextInt(h / 4 + 1) - h / 8, 18, h - 19);
            Area landingGround = new Area(1, Math.max(1, landingY - 16), LANDING_DEPTH, Math.min(h - 2, landingY + 16));
            open(landingGround, null);
            reserve(landingGround, 0);
            List<LandingArea> landing = new ArrayList<>();
            int index = 1;
            for (int lateral : new int[]{landingY - 8, landingY + 8}) {
                landing.add(new LandingArea("ruins-landing-" + index++,
                        LandingPad.civilian(BERTH_COLUMN, lateral - BERTH_OFFSET, LandingPad.Approach.WEST),
                        LandingPad.civilian(BERTH_COLUMN, lateral + BERTH_OFFSET, LandingPad.Approach.WEST)));
            }

            int vaultY = clamp(h / 2 + rng.nextInt(h / 3 + 1) - h / 6, 10, h - 11);
            Vault vault = vault(vaultY);
            int zoneStart = LANDING_DEPTH + 2;
            int zoneEnd = vault.footprint().left() - FORECOURT - 1;

            int layers = plan.layers();
            int[][] lines = new int[layers][];
            int span = zoneEnd - zoneStart;
            for (int i = 0; i < layers; i++) {
                int centre = zoneStart + (i + 1) * span / (layers + 1) + rng.nextInt(5) - 2;
                lines[i] = lineProfile(centre);
            }
            List<LayerDraft> drafts = new ArrayList<>();
            for (int i = 0; i < layers; i++) drafts.add(layer(i, lines[i], i == layers - 1 ? vaultY : -1));

            List<Layer> laid = new ArrayList<>();
            List<Junction> junctions = new ArrayList<>();
            PowerHall hall = null;
            RecordsTerminal records = null;
            int hallLayer = layers == 1 ? 0 : rng.nextInt(layers - 1);
            for (int i = 0; i < layers; i++) {
                LayerDraft d = drafts.get(i);
                int regionLeft = d.lineRight + 3;
                int regionRight = i + 1 < layers ? drafts.get(i + 1).lineLeft - 4 : zoneEnd;
                Area region = new Area(regionLeft, 2, Math.max(regionLeft, regionRight), h - 3);
                Court court = null;
                if (i > 0) court = court(i, region);
                if (i == hallLayer) hall = hall(i, region);
                if (i == 0) records = records(region);
                List<SideRoom> rooms = new ArrayList<>();
                for (int r = 0; r < ROOMS_PER_LAYER; r++) {
                    SideRoom room = room(RuinsDelve.layerName(i) + ".room." + (rooms.size() + 1), region);
                    if (room != null) rooms.add(room);
                }
                if (rooms.isEmpty()) throw new IllegalStateException(RuinsDelve.layerName(i) + " seats no side room");
                junctions.add(junction(i, d));
                Area ground = new Area(Math.max(1, d.lineLeft - 2), 1,
                        Math.max(d.lineLeft - 2, i + 1 < layers ? drafts.get(i + 1).lineLeft - 3 : zoneEnd), h - 2);
                laid.add(new Layer(RuinsDelve.layerName(i), i, d.kind, ground, d.lineLeft, d.lineRight,
                        d.ways, rooms, court));
            }
            finishMasks();
            repairPockets(landing.get(0).berth(0));

            LayerDraft last = drafts.get(layers - 1);
            Way shortWay = null;
            for (Way way : last.ways) if (way.kind() == WayKind.COLLAPSE) shortWay = way;
            Collapse collapse = new Collapse(RuinsDelve.COLLAPSE, layers - 1, shortWay);
            return new RuinsDelve(layers, landingGround, landing, vault, laid, hall, junctions, records, collapse);
        }

        // ---- vault ---------------------------------------------------------------------------

        Vault vault(int cy) {
            int x1 = w - 4, x0 = x1 - VAULT_WIDTH + 1, y0 = cy - VAULT_HEIGHT / 2, y1 = y0 + VAULT_HEIGHT - 1;
            Area apron = new Area(x0 - FORECOURT, y0 - 3, w - 2, y1 + 3);
            open(apron, null);
            reserve(apron, 1);
            shell(x0, y0, x1, y1, VAULT_WALL, VAULT_FLOOR, BuildingKind.FORTIFIED);
            int partition = x0 + 4;
            for (int y = y0 + 1; y < y1; y++) wall(partition, y, VAULT_WALL, BuildingKind.FORTIFIED);
            door(x0, cy);
            door(partition, cy);
            Cell source = new Cell((partition + 1 + x1 - 1) / 2, cy);
            Cell approach = new Cell(x0 - 1, cy);
            ctx.pois.add(new PointOfInterest(PointOfInterest.Kind.DEPOT, x0, y0, x1, y1,
                    approach.x(), approach.y(), source.x(), source.y()));
            return new Vault(new Area(x0, y0, x1, y1), new Cell(x0, cy), new Cell(partition, cy), approach, source);
        }

        // ---- layers --------------------------------------------------------------------------

        /** A layer's wall column per row: it runs straight in short segments and jogs a cell between them. */
        int[] lineProfile(int centre) {
            int[] x = new int[h];
            int y = 0;
            while (y < h) {
                int length = 7 + rng.nextInt(6), offset = rng.nextInt(3) - 1;
                for (int k = 0; k < length && y < h; k++) x[y++] = centre + offset;
            }
            return x;
        }

        private static final class LayerDraft {
            LayerKind kind;
            int lineLeft, lineRight;
            final List<Way> ways = new ArrayList<>();
        }

        LayerDraft layer(int index, int[] lx, int collapseY) {
            LayerDraft d = new LayerDraft();
            int open = plan.layers() == 1 ? 1 + rng.nextInt(2) : 2 + rng.nextInt(2);
            LayerKind[] kinds = LayerKind.values();
            d.kind = open == 1 ? kinds[rng.nextInt(2)] : kinds[rng.nextInt(kinds.length)];
            String material = d.kind == LayerKind.SEALED_GATE ? GATE_WALL : RUIN_WALLS[rng.nextInt(RUIN_WALLS.length)];

            List<Integer> centres = new ArrayList<>();
            int lo = 6, hi = h - 7;
            if (collapseY >= 0) collapseY = clamp(collapseY, lo, hi);
            for (int j = 0; j < open; j++) {
                int slotLo = lo + j * (hi - lo) / open, slotHi = lo + (j + 1) * (hi - lo) / open;
                Integer pick = pickRow(slotLo, slotHi, centres, collapseY);
                if (pick == null) pick = pickRow(lo, hi, centres, collapseY);
                if (pick == null) throw new IllegalStateException(RuinsDelve.layerName(index) + " has no room for its ways");
                centres.add(pick);
            }
            centres.sort(null);
            int gate = -1;
            if (d.kind == LayerKind.SEALED_GATE) {
                int best = Integer.MAX_VALUE;
                for (int j = 0; j < centres.size(); j++) {
                    int off = Math.abs(centres.get(j) - h / 2);
                    if (off < best) { best = off; gate = j; }
                }
            }
            List<int[]> spans = new ArrayList<>();
            List<WayKind> wayKinds = new ArrayList<>();
            List<Integer> all = new ArrayList<>(centres);
            if (collapseY >= 0) all.add(collapseY);
            all.sort(null);
            for (int c : all) {
                WayKind kind = c == collapseY && !centres.contains(c) ? WayKind.COLLAPSE
                        : d.kind == LayerKind.SEALED_GATE ? (centres.indexOf(c) == gate ? WayKind.GATE : WayKind.SIDE)
                        : d.kind == LayerKind.COLLAPSED_STREET ? WayKind.GAP : WayKind.BREACH;
                int width = kind == WayKind.GAP || kind == WayKind.GATE ? 4 : 3;
                int y0 = c - (width - 1) / 2, y1 = y0 + width - 1;
                // A way's rows and the row either side keep one column, so its span is a clean gap.
                for (int y = Math.max(0, y0 - 1); y <= Math.min(h - 1, y1 + 1); y++) lx[y] = lx[c];
                spans.add(new int[]{y0, y1});
                wayKinds.add(kind);
            }

            // The line, edge to edge, with a connector where it jogs.
            d.lineLeft = Integer.MAX_VALUE;
            d.lineRight = Integer.MIN_VALUE;
            for (int y = 0; y < h; y++) {
                int from = y > 0 ? Math.min(lx[y], lx[y - 1]) : lx[y], to = y > 0 ? Math.max(lx[y], lx[y - 1]) : lx[y];
                for (int x = from; x <= to; x++) {
                    lineWall(x, y, material);
                    d.lineLeft = Math.min(d.lineLeft, x);
                    d.lineRight = Math.max(d.lineRight, x);
                }
            }
            if (d.kind == LayerKind.COLLAPSED_STREET) {
                for (int y = 1; y < h - 1; y++) {
                    for (int dx : new int[]{-1, 1, -2, 2}) {
                        int x = lx[y] + dx;
                        if (grid.inBounds(x, y) && grid.isWalkable(x, y) && !reserved[i(x, y)]
                                && rng.nextInt(Math.abs(dx) == 1 ? 2 : 4) == 0) ground(x, y, GroundKind.RUBBLE);
                    }
                }
            }

            int number = 1;
            for (int k = 0; k < spans.size(); k++) {
                int y0 = spans.get(k)[0], y1 = spans.get(k)[1], x = lx[y0];
                WayKind kind = wayKinds.get(k);
                Area corridor = new Area(Math.max(1, x - 3), y0, Math.min(w - 2, x + 3), y1);
                if (kind == WayKind.COLLAPSE) {
                    open(corridor, GroundKind.RUBBLE);
                    for (int y = y0; y <= y1; y++) lineWall(x, y, material);
                    loose(SCRAP, x - 1, (y0 + y1) / 2);
                    loose(SCRAP, x + 1, y0);
                } else {
                    for (int y = y0; y <= y1; y++) kept[i(x, y)] = false;
                    open(corridor, kind == WayKind.GATE ? null : GroundKind.RUBBLE);
                    if (kind == WayKind.GATE) {
                        int floor = topology.surfaceIndex(VAULT_FLOOR);
                        for (int y = y0; y <= y1; y++) {
                            topology.setGroundKind(x, y, GroundKind.COURTYARD);
                            topology.setSurface(x, y, floor);
                        }
                    } else {
                        stub(x, y0, material);
                        stub(x, y1, material);
                    }
                }
                reserve(new Area(Math.max(0, x - 4), Math.max(0, y0 - 2), Math.min(w - 1, x + 4), Math.min(h - 1, y1 + 2)), 0);
                String name = kind == WayKind.COLLAPSE ? RuinsDelve.COLLAPSE
                        : RuinsDelve.layerName(index) + ".way." + number++;
                d.ways.add(new Way(name, kind, new Area(x, y0, x, y1),
                        new Cell(x - 3, (y0 + y1) / 2), new Cell(x + 3, (y0 + y1) / 2)));
            }
            for (int y = 0; y < h; y++) {
                for (int x = d.lineLeft - 1; x <= d.lineRight + 1; x++) {
                    if (grid.inBounds(x, y)) reserved[i(x, y)] = true;
                }
            }
            return d;
        }

        Integer pickRow(int lo, int hi, List<Integer> taken, int collapseY) {
            List<Integer> rows = new ArrayList<>();
            for (int y = lo; y < hi; y++) {
                boolean clear = collapseY < 0 || Math.abs(y - collapseY) >= WAY_SPACING;
                for (int t : taken) if (Math.abs(y - t) < WAY_SPACING) clear = false;
                if (clear) rows.add(y);
            }
            return rows.isEmpty() ? null : rows.get(rng.nextInt(rows.size()));
        }

        Junction junction(int index, LayerDraft d) {
            Way first = d.ways.get(0);
            for (Way way : d.ways) if (way.open()) { first = way; break; }
            int x = first.span().left() + 2, y = first.span().top() + 1;
            free(x, y);
            free(x, y + 1);
            Cell stand = new Cell(x, y);
            prop(CONSOLE, x, y + 1);
            return new Junction(RuinsDelve.junctionName(index), stand, index);
        }

        // ---- places behind a layer ------------------------------------------------------------

        Court court(int index, Area region) {
            int[] at = spot(region, COURT_WIDTH, COURT_HEIGHT, 1);
            if (at == null) throw new IllegalStateException(RuinsDelve.layerName(index) + " seats no landing court");
            Area ground = new Area(at[0], at[1], at[0] + COURT_WIDTH - 1, at[1] + COURT_HEIGHT - 1);
            open(ground, GroundKind.COURTYARD);
            int surface = topology.surfaceIndex(COURT_FLOOR);
            for (int y = ground.bottom(); y <= ground.top(); y++)
                for (int x = ground.left(); x <= ground.right(); x++) topology.setSurface(x, y, surface);
            reserve(ground, 2);
            int cx = ground.centreX(), cy = ground.centreY();
            LandingPad.Approach approach = cy >= h / 2 ? LandingPad.Approach.NORTH : LandingPad.Approach.SOUTH;
            LandingArea area = new LandingArea("ruins-court-" + (index + 1),
                    LandingPad.civilian(cx - BERTH_OFFSET, cy, approach),
                    LandingPad.civilian(cx + BERTH_OFFSET, cy, approach));
            return new Court(RuinsDelve.layerName(index) + ".court", ground, area);
        }

        PowerHall hall(int index, Area region) {
            Shell s = shellIn(region, HALL_WIDTH, HALL_HEIGHT, HALL_WALL, HALL_FLOOR, BuildingKind.INDUSTRIAL);
            if (s == null) throw new IllegalStateException(RuinsDelve.layerName(index) + " seats no power hall");
            int back = s.back();
            prop(GENERATOR, s.area.left() + 1, back);
            prop(GENERATOR, s.area.left() + 2, back);
            prop(GENERATOR, s.area.right() - 1, back);
            prop(GENERATOR, s.area.right() - 2, back);
            prop(CONSOLE, s.area.centreX(), back);
            Cell console = new Cell(s.area.centreX(), back + s.inward());
            ctx.pois.add(poi(PointOfInterest.Kind.DEPOT, s, console));
            return new PowerHall(RuinsDelve.POWER_HALL, s.area, s.door, console, index);
        }

        RecordsTerminal records(Area region) {
            Shell s = shellIn(region, ROOM_WIDTH, ROOM_HEIGHT, RUIN_WALLS[rng.nextInt(RUIN_WALLS.length)],
                    RUIN_FLOORS[rng.nextInt(RUIN_FLOORS.length)], BuildingKind.CIVIC);
            if (s == null) throw new IllegalStateException("the first layer seats no records office");
            prop(TERMINAL, s.area.centreX(), s.back());
            Cell console = new Cell(s.area.centreX(), s.back() + s.inward());
            ctx.pois.add(poi(PointOfInterest.Kind.ADMINISTRATIVE, s, console));
            return new RecordsTerminal(RuinsDelve.RECORDS, s.area, s.door, console, 0);
        }

        SideRoom room(String name, Area region) {
            Shell s = shellIn(region, ROOM_WIDTH, ROOM_HEIGHT, RUIN_WALLS[rng.nextInt(RUIN_WALLS.length)],
                    RUIN_FLOORS[rng.nextInt(RUIN_FLOORS.length)], BuildingKind.INDUSTRIAL);
            if (s == null) return null;
            // Ruined inside: a corner heaped with rubble, wreckage where the room's things fell.
            boolean east = rng.nextBoolean();
            int bx = east ? s.area.right() - 1 : s.area.left() + 1, by = s.back();
            ground(bx, by, GroundKind.RUBBLE);
            ground(east ? bx - 1 : bx + 1, by, GroundKind.RUBBLE);
            ground(bx, by + s.inward(), GroundKind.RUBBLE);
            loose(SCRAP, bx, by);
            loose(WRECKAGE[rng.nextInt(WRECKAGE.length)], east ? s.area.left() + 1 : s.area.right() - 1, by);
            Cell interior = new Cell(s.area.centreX(), (s.area.bottom() + s.area.top()) / 2);
            ctx.pois.add(poi(PointOfInterest.Kind.DEPOT, s, interior));
            return new SideRoom(name, s.area, s.door, interior);
        }

        PointOfInterest poi(PointOfInterest.Kind kind, Shell s, Cell interior) {
            return new PointOfInterest(kind, s.area.left(), s.area.bottom(), s.area.right(), s.area.top(),
                    s.outside.x(), s.outside.y(), interior.x(), interior.y());
        }

        /** A stamped one-room shell: its footprint, its door on the side facing the middle of the map, and the cell out from it. */
        private record Shell(Area area, Cell door, Cell outside, boolean doorHigh) {
            /** The interior row against the wall opposite the door. */
            int back() { return doorHigh ? area.bottom() + 1 : area.top() - 1; }
            /** The step from the back wall toward the door. */
            int inward() { return doorHigh ? 1 : -1; }
        }

        Shell shellIn(Area region, int width, int height, String wall, String floor, BuildingKind kind) {
            int[] at = spot(region, width, height, 2);
            if (at == null) return null;
            Area area = new Area(at[0], at[1], at[0] + width - 1, at[1] + height - 1);
            open(new Area(area.left() - 1, area.bottom() - 1, area.right() + 1, area.top() + 1), null);
            reserve(area, 2);
            shell(area.left(), area.bottom(), area.right(), area.top(), wall, floor, kind);
            boolean doorHigh = area.centreY() < h / 2;
            int dy = doorHigh ? area.top() : area.bottom();
            door(area.centreX(), dy);
            return new Shell(area, new Cell(area.centreX(), dy), new Cell(area.centreX(), dy + (doorHigh ? 1 : -1)), doorHigh);
        }

        /** The lower-left corner of a free {@code width x height} footprint inside {@code region}, its margin free too. */
        int[] spot(Area region, int width, int height, int margin) {
            int spanX = region.width() - width - 2 * margin, spanY = region.height() - height - 2 * margin;
            if (spanX < 0 || spanY < 0) return null;
            for (int attempt = 0; attempt < 400; attempt++) {
                int left = region.left() + margin + rng.nextInt(spanX + 1);
                int bottom = region.bottom() + margin + rng.nextInt(spanY + 1);
                if (free(left - margin, bottom - margin, left + width - 1 + margin, bottom + height - 1 + margin)) {
                    return new int[]{left, bottom};
                }
            }
            return null;
        }

        boolean free(int l, int b, int r, int t) {
            if (l < 1 || b < 1 || r > w - 2 || t > h - 2) return false;
            for (int y = b; y <= t; y++) for (int x = l; x <= r; x++) if (reserved[i(x, y)]) return false;
            return true;
        }

        // ---- cells ---------------------------------------------------------------------------

        int i(int x, int y) { return y * w + x; }

        void reserve(Area area, int margin) {
            for (int y = area.bottom() - margin; y <= area.top() + margin; y++)
                for (int x = area.left() - margin; x <= area.right() + margin; x++)
                    if (grid.inBounds(x, y)) reserved[i(x, y)] = true;
        }

        /** Clears ground to open walkable cells; {@code kind} null keeps outdoor dirt. */
        void open(Area area, GroundKind kind) {
            for (int y = Math.max(0, area.bottom()); y <= Math.min(h - 1, area.top()); y++) {
                for (int x = Math.max(0, area.left()); x <= Math.min(w - 1, area.right()); x++) {
                    if (kept[i(x, y)]) continue;
                    free(x, y);
                    ground(x, y, kind == null ? GroundKind.DIRT : kind);
                }
            }
        }

        /** Makes one cell open floor: no wall, prop, remnant or doorway left on it. */
        void free(int x, int y) {
            removeProps(x, y);
            grid.setWalkableFloor(x, y);
            grid.setSeeThrough(x, y, true);
            grid.setDoorway(x, y, false);
            topology.setWall(x, y, false);
            topology.setFixture(x, y, false);
            topology.setWallRemnant(x, y, null);
            ctx.markMadeGround(x, y);
        }

        void lineWall(int x, int y, String material) {
            if (!grid.inBounds(x, y)) return;
            removeProps(x, y);
            grid.setWalkable(x, y, false);
            grid.setSeeThrough(x, y, false);
            grid.setDoorway(x, y, false);
            topology.setFixture(x, y, false);
            topology.setWallRemnant(x, y, null);
            topology.setSurface(x, y, topology.surfaceIndex(material));
            topology.setWallGroundSurface(x, y, RUBBLE_BED);
            ctx.markMadeGround(x, y);
            kept[i(x, y)] = true;
        }

        /** The broken end of a wall at a way's edge: rubble with the wall's stub standing in it. */
        void stub(int x, int y, String material) {
            ground(x, y, GroundKind.RUBBLE);
            int faces = 0;
            if (exposed(x, y + 1)) faces |= CellTopology.WALL_DIR_N;
            if (exposed(x + 1, y)) faces |= CellTopology.WALL_DIR_E;
            if (exposed(x, y - 1)) faces |= CellTopology.WALL_DIR_S;
            if (exposed(x - 1, y)) faces |= CellTopology.WALL_DIR_W;
            topology.setWallRemnant(x, y, new WallRemnant(material, GroundKind.RUBBLE, RUBBLE_BED, faces));
        }

        boolean exposed(int x, int y) {
            return topology.inBounds(x, y) && grid.isWalkable(x, y) && topology.getWallRemnant(x, y) == null;
        }

        void shell(int l, int b, int r, int t, String wall, String floor, BuildingKind kind) {
            int floorSurface = topology.surfaceIndex(floor);
            for (int y = b; y <= t; y++) {
                for (int x = l; x <= r; x++) {
                    if (x == l || x == r || y == b || y == t) {
                        wall(x, y, wall, kind);
                    } else {
                        free(x, y);
                        topology.setGroundKind(x, y, GroundKind.INDOOR);
                        topology.setBuildingKindHint(x, y, kind);
                        topology.setSurface(x, y, floorSurface);
                        drafted[i(x, y)] = true;
                    }
                }
            }
        }

        void wall(int x, int y, String surface, BuildingKind kind) {
            removeProps(x, y);
            grid.setWalkable(x, y, false);
            grid.setSeeThrough(x, y, false);
            grid.setDoorway(x, y, false);
            topology.setWall(x, y, true);
            topology.setFixture(x, y, false);
            topology.setWallRemnant(x, y, null);
            topology.setGroundKind(x, y, GroundKind.STRIPED);
            topology.setBuildingKindHint(x, y, kind);
            topology.setSurface(x, y, topology.surfaceIndex(surface));
            ctx.markMadeGround(x, y);
            drafted[i(x, y)] = true;
            kept[i(x, y)] = true;
        }

        void door(int x, int y) {
            grid.setWalkableFloor(x, y);
            grid.setSeeThrough(x, y, true);
            grid.setDoorway(x, y, true);
            topology.setWall(x, y, false);
            topology.setGroundKind(x, y, GroundKind.INDOOR);
            kept[i(x, y)] = true;
        }

        /** Wall faces and door jambs for every shell this stage stamped, as an authored building has them. */
        void finishMasks() {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (!drafted[i(x, y)]) continue;
                    int mask = 0;
                    if (topology.isWall(x, y)) {
                        for (Direction side : Direction.CARDINALS) {
                            int nx = x + side.dx, ny = y + side.dy;
                            if (!grid.inBounds(nx, ny) || !topology.isWall(nx, ny)) mask |= bit(side);
                        }
                    } else if (grid.isDoorway(x, y)) {
                        boolean upright = topology.isWall(x, y - 1) || topology.isWall(x, y + 1);
                        mask = bit(upright ? Direction.E : Direction.N);
                        for (Direction side : Direction.CARDINALS) {
                            int nx = x + side.dx, ny = y + side.dy;
                            if (grid.inBounds(nx, ny) && !topology.hasBuildingKindHint(nx, ny)) { mask = bit(side); break; }
                        }
                    }
                    topology.setWallDirMask(x, y, mask);
                }
            }
        }

        static int bit(Direction side) {
            return switch (side) {
                case N -> CellTopology.WALL_DIR_N;
                case E -> CellTopology.WALL_DIR_E;
                case S -> CellTopology.WALL_DIR_S;
                default -> CellTopology.WALL_DIR_W;
            };
        }

        /** Outdoor ground of a kind, drawn as that kind. */
        void ground(int x, int y, GroundKind kind) {
            topology.setGroundKind(x, y, kind);
            GenMappingRegistry mapping = GenMappingRegistry.installed();
            TileRegistry registry = TileRegistry.installed();
            String block = kind == GroundKind.DIRT && registry != null && registry.hasBlock(TRODDEN) ? TRODDEN
                    : kind == GroundKind.RUBBLE ? RUBBLE_BED
                    : mapping == null ? null : mapping.groundBlockId(kind);
            if (block != null) topology.setSurface(x, y, topology.surfaceIndex(block));
            ctx.markMadeGround(x, y);
        }

        /** Takes away every prop standing on a cell, freeing the rest of its footprint too. */
        void removeProps(int x, int y) {
            for (int k = ctx.doodads.size() - 1; k >= 0; k--) {
                Doodad d = ctx.doodads.get(k);
                if (x < d.cellX || y < d.cellY || x >= d.cellX + Math.max(1, d.footprintCellsX)
                        || y >= d.cellY + Math.max(1, d.footprintCellsY)) continue;
                ctx.doodads.remove(k);
                for (int fy = d.cellY; fy < d.cellY + Math.max(1, d.footprintCellsY); fy++) {
                    for (int fx = d.cellX; fx < d.cellX + Math.max(1, d.footprintCellsX); fx++) {
                        if (!grid.inBounds(fx, fy) || kept[i(fx, fy)] || !topology.isFixture(fx, fy)) continue;
                        topology.setFixture(fx, fy, false);
                        grid.setWalkableFloor(fx, fy);
                        grid.setSeeThrough(fx, fy, true);
                    }
                }
            }
        }

        /** One-cell equipment that blocks its cell, as the industrial yards place it. */
        void prop(String id, int x, int y) {
            TileRegistry registry = TileRegistry.installed();
            DoodadDef def = registry == null ? null : registry.doodad(id);
            if (def == null) return;
            removeProps(x, y);
            ctx.doodads.add(new Doodad(x, y, def));
            grid.setWalkable(x, y, false);
            grid.setSeeThrough(x, y, true);
            topology.setFixture(x, y, true);
            kept[i(x, y)] = true;
        }

        /** Loose debris: drawn on the ground, nothing a body cannot step over. */
        void loose(String id, int x, int y) {
            TileRegistry registry = TileRegistry.installed();
            DoodadDef def = registry == null ? null : registry.doodad(id);
            if (def == null || !grid.inBounds(x, y) || !grid.isWalkable(x, y)) return;
            ctx.doodads.add(new Doodad(x, y, def));
        }

        // ---- the route never closes ----------------------------------------------------------

        /**
         * Every walkable cell must be reachable from the landing: a layer line laid across the dead settlement can
         * pocket ground between itself and a ruin's standing wall. A pocket is joined back through a wall cell
         * that is not the delve's own (a ruin's wall, a tree); a pocket nothing can join is walled in, so no
         * squad can be stranded on it.
         */
        void repairPockets(LandingPad start) {
            for (int pass = 0; pass < 400; pass++) {
                boolean[] reach = DelveCheck.reach(grid, start.centerX, start.centerY, null);
                int pocket = -1;
                for (int k = 0; k < w * h && pocket < 0; k++) {
                    if (!reach[k] && grid.isWalkableAt(k)) pocket = k;
                }
                if (pocket < 0) return;
                List<Integer> cells = component(pocket, reach);
                if (!join(cells, reach)) {
                    if (cells.size() > MAX_SEALED_POCKET) {
                        throw new IllegalStateException("ruins ground cut off " + cells.size() + " cells at "
                                + (pocket % w) + "," + (pocket / w));
                    }
                    for (int k : cells) lineWall(k % w, k / w, RUIN_WALLS[0]);
                }
            }
            throw new IllegalStateException("ruins ground left pockets after repair");
        }

        List<Integer> component(int startCell, boolean[] reach) {
            List<Integer> out = new ArrayList<>();
            boolean[] seen = new boolean[w * h];
            seen[startCell] = true;
            out.add(startCell);
            for (int head = 0; head < out.size(); head++) {
                int at = out.get(head), cx = at % w, cy = at / w;
                for (Direction side : Direction.CARDINALS) {
                    int nx = cx + side.dx, ny = cy + side.dy;
                    if (!grid.inBounds(nx, ny) || seen[i(nx, ny)] || reach[i(nx, ny)]) continue;
                    if (!grid.isWalkable(nx, ny) || !grid.canTraverseCellStep(cx, cy, nx, ny)) continue;
                    seen[i(nx, ny)] = true;
                    out.add(i(nx, ny));
                }
            }
            return out;
        }

        boolean join(List<Integer> cells, boolean[] reach) {
            for (int k : cells) {
                int cx = k % w, cy = k / w;
                for (Direction side : Direction.CARDINALS) {
                    int nx = cx + side.dx, ny = cy + side.dy;
                    if (nx < 1 || ny < 1 || nx > w - 2 || ny > h - 2 || kept[i(nx, ny)]) continue;
                    if (grid.isWalkable(nx, ny)) {
                        // Walkable but unreachable across an edge: open the edges between them.
                        if (reach[i(nx, ny)]) {
                            grid.openAllEdges(cx, cy);
                            grid.openAllEdges(nx, ny);
                            return true;
                        }
                        continue;
                    }
                    for (Direction on : Direction.CARDINALS) {
                        int fx = nx + on.dx, fy = ny + on.dy;
                        if (grid.inBounds(fx, fy) && reach[i(fx, fy)]) {
                            free(nx, ny);
                            ground(nx, ny, GroundKind.RUBBLE);
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
