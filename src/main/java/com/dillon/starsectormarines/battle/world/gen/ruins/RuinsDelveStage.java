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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Random;

/**
 * Lays a ruins site's delve over the dead settlement ({@code mapgen-nouns.md}, Ruins ground): the cleared landing ground at the
 * west end, the vault suite at the east end, the grade's layers across the route between them with their ways
 * through, and, behind each layer, its small ruined rooms, its landing court (every layer past the first) and the
 * works sites; the vault's end is the interior, a roofed megastructure carved out of hull (a bunker round the
 * vault on scattered ground). Runs before finalize, so every shell it stamps is an ordinary building and every wall an ordinary
 * breachable wall. Publishes the {@link RuinsDelve} under {@link RuinsKeys#DELVE}.
 *
 * <p>Nothing new is drawn: the layers' walls and the rooms use the ruined-lot materials and rubble, the vault the
 * installation's dark cast concrete, the works the existing industrial equipment.
 *
 * <p>Its own random stream is derived from the seed, so the settlement under it draws exactly what a settlement
 * of that density would.
 */
public final class RuinsDelveStage implements GenStage {

    /** The landing ground's width in columns. */
    static final int LANDING_DEPTH = 16;
    /** Berth centres stand this many columns into the landing ground, deep enough that the approach is open air. */
    static final int BERTH_COLUMN = 5;
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

    /**
     * The landing ground's west column on a map {@code width} cells wide: a tenth of the way in, never at the
     * edge (owner: "we don't need to keep landing zones at the edge of the map"). The dead settlement and open
     * ground lie behind it as well as ahead, so the company lands among the ruins rather than at their rim; the
     * delve still runs east from it to the vault.
     */
    static int landingLeft(int width) {
        return Math.max(2, width / 10);
    }

    /** An entrance's apron: this deep west of the door and this tall about it. */
    static final int APRON_DEPTH = 13, APRON_HALF_HEIGHT = 4;
    /** Columns between the door and the apron's east edge, past any jog in the hull face. */
    static final int APRON_GAP = 2;
    /** The nearer berth's centre stands this far in from the apron's east edge. */
    static final int APRON_BERTH_INSET = 2;

    /**
     * The apron in front of an entrance whose door is {@code door}, a vertical span in a west-facing wall: the
     * open ground west of it, clear of the hull face's jogs, centred on the door's rows.
     */
    static Area apronFor(Area door) {
        int right = door.left() - 1 - APRON_GAP, cy = (door.bottom() + door.top()) / 2;
        return new Area(right - APRON_DEPTH + 1, cy - APRON_HALF_HEIGHT, right, cy + APRON_HALF_HEIGHT);
    }

    static final String[] RUIN_WALLS = {"colony.masonry-wall", "colony.ceramic-wall", "colony.olive-wall"};
    static final String[] RUIN_FLOORS = {"colony.concrete", "colony.timber", "colony.terrazzo", "colony.terracotta"};
    /** A layer line's wall: the ruined-lot walls that read as masonry from above; the olive wall reads as a hedge. */
    static final String[] LINE_WALLS = {"colony.masonry-wall", "colony.ceramic-wall"};
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
    /** The megastructure's hull and floor: the installation's cast concrete, as the vault is built. */
    static final String HULL_WALL = "installation.dark-concrete-wall";
    static final String HULL_FLOOR = "installation.concrete-floor";
    static final BuildingKind HULL_KIND = BuildingKind.FORTIFIED;
    /** The vault room's floor: the installation's command floor, so the heart of the site reads apart. */
    static final String VAULT_ROOM_FLOOR = "installation.command-floor";
    static final String HAUL = "doodad.industrial-crate-stack";
    /** The dead town's furniture and stores, as the ruined-lot filler leaves them; weathering takes most away. */
    static final String[] FURNISHINGS = {"doodad.shelf-dam-1", "doodad.shelf-dam-2", "doodad.desk-dam",
            "doodad.box-dam", "doodad.chair-s-yellow-dam", "doodad.chair-s-green-dam",
            "doodad.industrial-pallet-stack", "doodad.industrial-crate-stack", "doodad.industrial-drum-cluster",
            "doodad.industrial-cable-reel"};
    /** The ruined-lot wall that reads as a hedge from above; a long-dead town's walls are masonry. */
    static final String HEDGE_WALL = "colony.olive-wall";
    static final String DEAD_WALL = "colony.masonry-wall";
    /** Hull cells per chamber tried in an interior layer's band, besides its published rooms; at least four. UNTUNED. */
    static final int HULL_PER_CHAMBER = 220;

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
        /** Every layer line's cells, ways included: no interior corridor crosses one. */
        final boolean[] lineCell;
        /** Solid hull of the interior, not yet carved. */
        final boolean[] hull;
        /** The interior band (+1) whose corridor network a carved cell belongs to; 0 off every network. */
        final int[] network;
        /** The vault's apron: its forecourt and the open ground round it. */
        Area apron;
        PointOfInterest vaultPoi;
        /** The interior's footprint, and the hull face's column per row (null for the bunker). */
        Area interiorBox;
        int[] faceLx;
        /** The power hall when it stands inside, laid with its band before the spine. */
        PowerHall innerHall;
        /** Each interior layer's court, laid with its band so the spine runs through it. */
        final Map<Integer, Court> wells = new HashMap<>();

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
            lineCell = new boolean[w * h];
            hull = new boolean[w * h];
            network = new int[w * h];
        }

        RuinsDelve lay() {
            endStreets();
            weather();
            int landingY = clamp(h / 2 + rng.nextInt(h / 4 + 1) - h / 8, 18, h - 19);
            int landingLeft = landingLeft(w);
            Area landingGround = new Area(landingLeft, Math.max(1, landingY - 16), landingLeft + LANDING_DEPTH - 1,
                    Math.min(h - 2, landingY + 16));
            int berthX = landingLeft + BERTH_COLUMN;
            open(landingGround, null);
            reserve(landingGround, 0);
            List<LandingArea> landing = new ArrayList<>();
            int index = 1;
            for (int lateral : new int[]{landingY - 8, landingY + 8}) {
                landing.add(new LandingArea("ruins-landing-" + index++,
                        LandingPad.civilian(berthX, lateral - BERTH_OFFSET, LandingPad.Approach.WEST),
                        LandingPad.civilian(berthX, lateral + BERTH_OFFSET, LandingPad.Approach.WEST)));
            }

            int vaultY = clamp(h / 2 + rng.nextInt(h / 3 + 1) - h / 6, 10, h - 11);
            Vault vault = vault(vaultY);
            int zoneStart = landingGround.right() + 2;
            int zoneEnd = vault.footprint().left() - FORECOURT - 1;

            int layers = plan.layers();
            int[][] lines = new int[layers][];
            int span = zoneEnd - zoneStart;
            for (int i = 0; i < layers; i++) {
                int centre = zoneStart + (i + 1) * span / (layers + 1) + rng.nextInt(5) - 2;
                lines[i] = lineProfile(centre);
            }
            List<LayerDraft> drafts = new ArrayList<>();
            for (int i = 0; i < layers; i++) {
                drafts.add(layer(i, lines[i], i == layers - 1 ? vaultY : -1, i >= layers - plan.interiorLayers()));
            }

            int firstInside = layers - plan.interiorLayers();
            // The power hall stands in a layer before the last; inside, when the interior has one.
            int hallLayer = layers == 1 ? 0 : plan.interiorLayers() == 2 ? layers - 2 : rng.nextInt(layers - 1);
            List<Area> entrances = new ArrayList<>();
            if (firstInside == layers) entrances.add(bunker(vaultY));
            else entrances.addAll(enclose(drafts, firstInside, zoneEnd, hallLayer));
            List<RuinsDelve.Apron> aprons = new ArrayList<>();
            for (Area door : entrances) aprons.add(apron(aprons.size(), door));

            List<Layer> laid = new ArrayList<>();
            List<Junction> junctions = new ArrayList<>();
            List<Area> chambers = new ArrayList<>();
            PowerHall hall = null;
            RecordsTerminal records = null;
            for (int i = 0; i < layers; i++) {
                LayerDraft d = drafts.get(i);
                boolean in = i >= firstInside;
                int regionLeft = d.lineRight + 3;
                int regionRight = i + 1 < layers ? drafts.get(i + 1).lineLeft - 4 : zoneEnd;
                Area region = new Area(regionLeft, 2, Math.max(regionLeft, regionRight), h - 3);
                Court court = null;
                if (i > 0) {
                    court = in ? wells.get(i) : court(i, region);
                }
                if (i == hallLayer) hall = in ? innerHall : hall(i, region);
                if (i == 0) records = records(region);
                List<SideRoom> rooms = new ArrayList<>();
                for (int r = 0; r < ROOMS_PER_LAYER; r++) {
                    SideRoom room = room(RuinsDelve.layerName(i) + ".room." + (rooms.size() + 1), region);
                    if (room == null) continue;
                    rooms.add(room);
                    if (in) connect(outside(room.footprint(), room.door()), i, null);
                }
                if (rooms.isEmpty()) throw new IllegalStateException(RuinsDelve.layerName(i) + " seats no side room");
                if (in) {
                    int tries = Math.max(4, region.width() * region.height() / HULL_PER_CHAMBER);
                    for (int c = 0; c < tries; c++) {
                        Area chamber = chamber(region);
                        if (chamber == null) continue;
                        connect(new Cell(chamber.centreX(), chamber.centreY()), i, null);
                        chambers.add(chamber);
                    }
                }
                junctions.add(junction(i, d));
                Area ground = new Area(Math.max(1, d.lineLeft - 2), 1,
                        Math.max(d.lineLeft - 2, i + 1 < layers ? drafts.get(i + 1).lineLeft - 3 : zoneEnd), h - 2);
                laid.add(new Layer(RuinsDelve.layerName(i), i, d.kind, ground, d.lineLeft, d.lineRight,
                        d.ways, rooms, court));
            }
            dress(plan.dressing(), vault, chambers);
            finishMasks();
            repairPockets(landing.get(0).berth(0));

            LayerDraft last = drafts.get(layers - 1);
            Way shortWay = null;
            for (Way way : last.ways) if (way.kind() == WayKind.COLLAPSE) shortWay = way;
            Collapse collapse = new Collapse(RuinsDelve.COLLAPSE, layers - 1, shortWay);
            RuinsDelve.Interior interior = new RuinsDelve.Interior(RuinsDelve.INTERIOR, interiorBox, firstInside,
                    entrances, aprons, chambers);
            return new RuinsDelve(layers, landingGround, landing, vault, laid, hall, junctions, records, collapse,
                    interior);
        }

        /**
         * A dead town has no live streets. Street and pavement ground (the frame between lots, which never was in the
         * road mask) is broken up into trodden earth and rubble bed, so nothing is finished as a carriageway with
         * lane markings; near the map edge it is earth alone, with a lip of rubble where each street ends. The mix
         * is a coordinate hash: it draws nothing from the layout's random stream.
         */
        void endStreets() {
            int falloff = RuinedLotsStage.STREET_FALLOFF;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    GroundKind kind = topology.getGroundKind(x, y);
                    if (kind != GroundKind.STREET && kind != GroundKind.SIDEWALK) continue;
                    int edge = Math.min(Math.min(x, w - 1 - x), Math.min(y, h - 1 - y));
                    boolean rubble = edge < falloff ? edge == falloff - 1 : broken(x, y);
                    ground(x, y, rubble ? GroundKind.RUBBLE : GroundKind.DIRT);
                }
            }
        }

        /**
         * A ruins site has been dead for a long time, longer than a town's freshly ruined lots: its floors are
         * broken into rubble and earth with patches of the old floor showing through, its standing walls are
         * masonry (the olive sheet reads as a hedge from above), most of its furniture and stores are gone, and a
         * share of what went is left as scrap. Runs over the dead settlement before the delve is laid, so
         * the delve's own shells are untouched; a coordinate hash, drawing nothing from the layout's stream.
         */
        void weather() {
            int dirtFloor = topology.surfaceIndex(TRODDEN);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (HEDGE_WALL.equals(topology.getSurfaceId(x, y)) && !grid.isWalkable(x, y)) {
                        topology.setSurface(x, y, topology.surfaceIndex(DEAD_WALL));
                    }
                    WallRemnant remnant = topology.getWallRemnant(x, y);
                    if (remnant != null && HEDGE_WALL.equals(remnant.materialId())) {
                        topology.setWallRemnant(x, y, new WallRemnant(DEAD_WALL, remnant.groundKind(),
                                remnant.groundSurfaceId(), remnant.exposedFaces()));
                    }
                    if (topology.getGroundKind(x, y) != GroundKind.COURTYARD || !grid.isWalkable(x, y)
                            || remnant != null) continue;
                    // Patches about three cells across, their edges jittered a cell so they never read as a grid;
                    // a fifth of the old floor shows through.
                    int px = (x + hash(x, y, 0x11) % 2) / 3, py = (y + hash(x, y, 0x22) % 2) / 3;
                    int roll = hash(px, py, 0x5EED) % 20;
                    if (roll < 11) ground(x, y, GroundKind.RUBBLE);
                    else if (roll < 16) {
                        topology.setGroundKind(x, y, GroundKind.DIRT);
                        if (dirtFloor != 0) topology.setSurface(x, y, dirtFloor);
                    }
                }
            }
            List<int[]> scrap = new ArrayList<>();
            for (int k = ctx.doodads.size() - 1; k >= 0; k--) {
                Doodad d = ctx.doodads.get(k);
                // Only loose pieces: a blocking store would leave its cell blocked with nothing drawn on it.
                if (!furnishing(d.definitionId()) || !grid.isWalkable(d.cellX, d.cellY)) continue;
                int roll = hash(d.cellX, d.cellY, 0xF0) % 3;
                if (roll == 0) continue;
                ctx.doodads.remove(k);
                if (roll == 1) scrap.add(new int[]{d.cellX, d.cellY});
            }
            for (int[] at : scrap) loose(SCRAP, at[0], at[1]);
        }

        static boolean furnishing(String id) {
            if (id == null) return false;
            for (String f : FURNISHINGS) if (f.equals(id)) return true;
            return false;
        }

        /** A non-negative hash of a cell and the seed, for dressing that must not draw from the layout's stream. */
        int hash(int x, int y, int salt) {
            long v = x * 73_856_093L ^ y * 19_349_663L ^ (ctx.seed + salt) * 83_492_791L;
            v ^= (v >>> 13);
            v *= 0x5bd1e995L;
            v ^= (v >>> 15);
            return (int) Math.floorMod(v, 1_000_003L);
        }

        /** Whether a dead street's cell is rubble rather than earth: patches about three cells across, a third of it. */
        boolean broken(int x, int y) {
            long hash = (x / 3) * 73_856_093L ^ (y / 3) * 19_349_663L ^ ctx.seed * 83_492_791L;
            hash ^= (hash >>> 13);
            hash *= 0x5bd1e995L;
            hash ^= (hash >>> 15);
            return Math.floorMod(hash, 3) == 0;
        }

        // ---- vault ---------------------------------------------------------------------------

        Vault vault(int cy) {
            int x1 = w - 4, x0 = x1 - VAULT_WIDTH + 1, y0 = cy - VAULT_HEIGHT / 2, y1 = y0 + VAULT_HEIGHT - 1;
            apron = new Area(x0 - FORECOURT, y0 - 3, w - 2, y1 + 3);
            open(apron, null);
            reserve(apron, 1);
            shell(x0, y0, x1, y1, VAULT_WALL, VAULT_FLOOR, BuildingKind.FORTIFIED);
            int partition = x0 + 4;
            for (int y = y0 + 1; y < y1; y++) wall(partition, y, VAULT_WALL, BuildingKind.FORTIFIED);
            door(x0, cy);
            door(partition, cy);
            Cell source = new Cell((partition + 1 + x1 - 1) / 2, cy);
            dressVault(x0, y0, x1, y1, partition, cy);
            Cell approach = new Cell(x0 - 1, cy);
            vaultPoi = new PointOfInterest(PointOfInterest.Kind.DEPOT, x0, y0, x1, y1,
                    approach.x(), approach.y(), source.x(), source.y());
            ctx.pois.add(vaultPoi);
            return new Vault(new Area(x0, y0, x1, y1), new Cell(x0, cy), new Cell(partition, cy), approach, source);
        }

        /**
         * The vault as the site's hardened heart rather than a plain box: buttresses stand out from its corners and
         * along its faces (never on the door's face but at its corners), the vault room is floored apart in the
         * command floor, the haul stands crated in its corners, and a console stands in the antechamber.
         */
        void dressVault(int x0, int y0, int x1, int y1, int partition, int cy) {
            int[][] buttresses = {{x0 - 1, y0 - 1}, {x0 - 1, y1 + 1}, {x1 + 1, y0 - 1}, {x1 + 1, y1 + 1},
                    {x0 + 3, y0 - 1}, {x0 + 3, y1 + 1}, {x0 + 7, y0 - 1}, {x0 + 7, y1 + 1},
                    {x1 + 1, cy - 2}, {x1 + 1, cy + 2}};
            for (int[] b : buttresses) wall(b[0], b[1], VAULT_WALL, BuildingKind.FORTIFIED);
            int room = topology.surfaceIndex(VAULT_ROOM_FLOOR);
            for (int y = y0 + 1; y < y1; y++) for (int x = partition + 1; x < x1; x++) topology.setSurface(x, y, room);
            prop(HAUL, x1 - 1, y0 + 1);
            prop(HAUL, x1 - 1, y1 - 1);
            prop(HAUL, partition + 1, y0 + 1);
            prop(HAUL, partition + 1, y1 - 1);
            prop(CONSOLE, x0 + 2, y1 - 1);
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
            /** The line's column per row. */
            int[] lx;
            final List<Way> ways = new ArrayList<>();
            /** Every cell the line was laid on, its ways' spans included. */
            final List<Integer> cells = new ArrayList<>();
        }

        /**
         * Layer {@code index} laid on the column profile {@code lx}. Inside the megastructure the line stands edge to
         * edge, a hull wall with its ways through. Outside it is a boundary, not a wall (owner: a full-height line
         * across open ground "has no reason to exist"): only short stubs of an old foundation stand along it, so
         * the line is data the layer's places are laid against, and a sealed gate, which only shuts a standing
         * line, is never laid outside.
         */
        LayerDraft layer(int index, int[] lx, int collapseY, boolean inside) {
            LayerDraft d = new LayerDraft();
            int open = plan.layers() == 1 ? 1 + rng.nextInt(2) : 2 + rng.nextInt(2);
            LayerKind[] kinds = LayerKind.values();
            d.kind = open == 1 || !inside ? kinds[rng.nextInt(2)] : kinds[rng.nextInt(kinds.length)];
            String material = d.kind == LayerKind.SEALED_GATE ? GATE_WALL : LINE_WALLS[rng.nextInt(LINE_WALLS.length)];

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
            d.lx = lx;
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

            // The line, edge to edge, with a connector where it jogs: walled throughout inside, in stubs outside.
            boolean[] stands = standing(inside, lx);
            d.lineLeft = Integer.MAX_VALUE;
            d.lineRight = Integer.MIN_VALUE;
            for (int y = 0; y < h; y++) {
                int from = y > 0 ? Math.min(lx[y], lx[y - 1]) : lx[y], to = y > 0 ? Math.max(lx[y], lx[y - 1]) : lx[y];
                for (int x = from; x <= to; x++) {
                    if (stands[y]) lineWall(x, y, material);
                    d.cells.add(i(x, y));
                    lineCell[i(x, y)] = true;
                    d.lineLeft = Math.min(d.lineLeft, x);
                    d.lineRight = Math.max(d.lineRight, x);
                }
            }
            skirt(lx, stands, inside ? (d.kind == LayerKind.COLLAPSED_STREET ? 4 : 3) : 2);

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

        /**
         * The rubble a standing line has shed: a bed of it against both faces, thinning out to {@code reach}
         * cells, and scrap where sections came down, so a line reads as an old wall gone to ruin rather than a
         * clean one drawn across the ground. A collapsed street sheds the most. Only open ground is touched.
         */
        void skirt(int[] lx, boolean[] stands, int reach) {
            for (int y = 1; y < h - 1; y++) {
                if (!stands[y] && !stands[y - 1] && !stands[y + 1]) continue;
                for (int side : new int[]{-1, 1}) {
                    for (int step = 1; step <= reach; step++) {
                        int x = lx[y] + side * step;
                        if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || reserved[i(x, y)]) continue;
                        // Near the face nearly always, then patchier: chance falls by a quarter a cell.
                        if (hash(x, y / 2, 0x5C1) % 100 < 95 - step * 22) ground(x, y, GroundKind.RUBBLE);
                    }
                }
                if (hash(lx[y], y, 0x5C2) % 11 == 0) {
                    int side = hash(y, lx[y], 0x5C3) % 2 == 0 ? -1 : 1;
                    loose(SCRAP, lx[y] + side, y);
                }
            }
        }

        /**
         * Which rows of a line stand as wall: every row inside; outside, stubs of two to four rows with gaps of
         * four to nine between, starting in a gap, so no stretch of the line reads as a wall across the map, and
         * only among the dead town's own walls: in open country the line stands nowhere.
         */
        boolean[] standing(boolean inside, int[] lx) {
            boolean[] stands = new boolean[h];
            if (inside) {
                Arrays.fill(stands, true);
                return stands;
            }
            int y = 1 + rng.nextInt(6);
            while (y < h) {
                int length = 2 + rng.nextInt(3);
                boolean town = amongRuins(lx[y], y);
                for (int k = 0; k < length && y < h; k++) stands[y++] = town;
                y += 4 + rng.nextInt(6);
            }
            return stands;
        }

        /** Whether a wall of the dead town stands within a few cells of {@code (x, y)}. */
        boolean amongRuins(int x, int y) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dx = -5; dx <= 5; dx++) {
                    if (grid.inBounds(x + dx, y + dy) && topology.isWall(x + dx, y + dy)) return true;
                }
            }
            return false;
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
            // The stand is on the way's own passage, its last row; the box is set in the wall just past it.
            int x = first.span().left() + 2, y = first.span().top();
            floorAt(x, y);
            floorAt(x, y + 1);
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
            if (insideCell(at[0], at[1])) {
                // Inside the megastructure a room is built of its hull, not of the dead town's materials.
                wall = HULL_WALL;
                floor = HULL_FLOOR;
            }
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
                    if (insideCell(x, y)) {
                        if (kind == GroundKind.COURTYARD) {
                            // A court inside is a light well: open to the sky, so no roof over it.
                            free(x, y);
                            ground(x, y, kind);
                            topology.setBuildingKindHint(x, y, null);
                        } else {
                            interiorFloor(x, y);
                        }
                        continue;
                    }
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
            hull[i(x, y)] = false;
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

        // ---- interior -------------------------------------------------------------------------

        /** Whether a cell is inside the interior: within its footprint and east of the hull face. */
        boolean insideCell(int x, int y) {
            if (interiorBox == null || !interiorBox.contains(x, y)) return false;
            return faceLx == null || x > faceLx[y];
        }

        /**
         * The megastructure: from the wall line of layer {@code first} to the map's east edge, every cell but the
         * ways' passages and the vault's apron is filled with hull, and the layer lines inside become its walls
         * and bulkheads. Each interior band then gets a spine corridor running its length, a corridor from every
         * way into it and out of it, and two doors from the last band into the vault's hall (the apron, roofed).
         * Returns the entrances: the first interior layer's open ways.
         */
        List<Area> enclose(List<LayerDraft> drafts, int first, int zoneEnd, int hallLayer) {
            LayerDraft face = drafts.get(first);
            faceLx = face.lx;
            interiorBox = new Area(face.lineLeft, 0, w - 1, h - 1);
            prunePlaces();
            boolean[] passage = new boolean[w * h];
            boolean[] collapsed = new boolean[w * h];
            for (int k = first; k < drafts.size(); k++) {
                for (Way way : drafts.get(k).ways) {
                    int x = way.span().left();
                    for (int y = way.span().bottom(); y <= way.span().top(); y++) {
                        if (way.kind() == WayKind.COLLAPSE) collapsed[i(x, y)] = true;
                        for (int cx = x - 3; cx <= x + 3; cx++) if (grid.inBounds(cx, y)) passage[i(cx, y)] = true;
                    }
                }
            }
            for (int y = 0; y < h; y++) {
                for (int x = interiorBox.left(); x < w; x++) {
                    int k = i(x, y);
                    if (!insideCell(x, y) || lineCell[k] || apron.contains(x, y)) continue;
                    if (passage[k] && grid.isWalkable(x, y)) roofOver(x, y);
                    else if (!kept[k]) hullCell(x, y);
                }
            }
            for (int k = first; k < drafts.size(); k++) {
                for (int cell : drafts.get(k).cells) {
                    int x = cell % w, y = cell / w;
                    if (collapsed[cell] || grid.isWalkable(x, y)) continue;
                    wall(x, y, HULL_WALL, HULL_KIND);
                }
            }
            hallUnderRoof();
            for (int k = first; k < drafts.size(); k++) {
                int left = drafts.get(k).lineRight + 2;
                int right = k + 1 < drafts.size() ? drafts.get(k + 1).lineLeft - 2 : apron.left() - 2;
                // The band's big pieces first, while the whole band is free: the power hall, then the court,
                // a light well the spine runs through. The spine is laid after them, clear of the hall, so neither
                // has to squeeze in beside the corridor.
                int regionLeft = drafts.get(k).lineRight + 3;
                int regionRight = k + 1 < drafts.size() ? drafts.get(k + 1).lineLeft - 4 : zoneEnd;
                Area region = new Area(regionLeft, 2, Math.max(regionLeft, regionRight), h - 3);
                if (k == hallLayer) innerHall = hall(k, region);
                Court well = court(k, region);
                wells.put(k, well);
                int spine = spineColumn(well, k == hallLayer ? innerHall : null, left + 1, right - 1, k);
                for (int y = 2; y <= h - 3; y++) for (int x = spine - 1; x <= spine + 1; x++) carve(x, y, k);
                for (Way way : drafts.get(k).ways) connect(way.far(), k, null);
                if (k + 1 < drafts.size()) for (Way way : drafts.get(k + 1).ways) connect(way.near(), k, null);
                if (k == hallLayer) connect(outside(innerHall.footprint(), innerHall.door()), k, null);
                connect(new Cell(well.ground().centreX(), well.ground().centreY()), k, null);
            }
            int last = drafts.size() - 1;
            connect(new Cell(apron.left() - 1, apron.bottom() + 2), last, apron);
            connect(new Cell(apron.left() - 1, apron.top() - 3), last, apron);
            List<Area> entrances = new ArrayList<>();
            for (Way way : face.ways) if (way.open()) entrances.add(way.span());
            return entrances;
        }

        /**
         * Scattered ground's interior: a bunker round the vault. Its forecourt is roofed as an antechamber, a hull
         * wall rings it, and one seal door in its west face, on the vault's row, lets the company in. Returns the
         * door.
         */
        Area bunker(int vaultY) {
            interiorBox = new Area(apron.left() - 1, apron.bottom() - 1, w - 1, Math.min(h - 1, apron.top() + 1));
            prunePlaces();
            for (int y = interiorBox.bottom(); y <= interiorBox.top(); y++) {
                for (int x = interiorBox.left(); x <= interiorBox.right(); x++) {
                    if (!apron.contains(x, y) && !kept[i(x, y)]) hullCell(x, y);
                }
            }
            hallUnderRoof();
            int x = interiorBox.left();
            for (int y = vaultY; y <= vaultY + 1; y++) {
                hull[i(x, y)] = false;
                door(x, y);
                drafted[i(x, y)] = true;
            }
            Area step = new Area(x - 3, vaultY - 1, x - 1, vaultY + 2);
            open(step, null);
            reserve(step, 1);
            return new Area(x, vaultY, x, vaultY + 1);
        }

        /**
         * The apron outside entrance {@code index}: open ground cleared west of its door, kept clear of the places
         * laid after it, with two berths the boat can come to once the mission makes it the entry. Never cleared
         * into the interior.
         */
        RuinsDelve.Apron apron(int index, Area door) {
            Area ground = apronFor(door);
            for (int y = Math.max(1, ground.bottom()); y <= Math.min(h - 2, ground.top()); y++) {
                for (int x = Math.max(1, ground.left()); x <= ground.right(); x++) {
                    if (insideCell(x, y) || hull[i(x, y)]) continue;
                    open(new Area(x, y, x, y), null);
                }
            }
            reserve(ground, 1);
            int cy = ground.centreY();
            LandingArea lift = new LandingArea("ruins-entrance-" + (index + 1),
                    LandingPad.civilian(ground.right() - APRON_BERTH_INSET, cy, LandingPad.Approach.WEST),
                    LandingPad.civilian(ground.left() + APRON_BERTH_INSET, cy, LandingPad.Approach.WEST));
            return new RuinsDelve.Apron(RuinsDelve.Interior.apronName(index), door, ground, lift);
        }

        /** The vault's apron as a roofed hall: every open cell of it but the vault suite itself. */
        void hallUnderRoof() {
            for (int y = apron.bottom(); y <= apron.top(); y++) {
                for (int x = apron.left(); x <= apron.right(); x++) {
                    if (!kept[i(x, y)] && !drafted[i(x, y)]) interiorFloor(x, y);
                }
            }
        }

        /** Drops the dead settlement's points of interest under the interior; the vault's own stays. */
        void prunePlaces() {
            ctx.pois.removeIf(p -> p != vaultPoi && interiorBox.contains(p.interiorAnchorX, p.interiorAnchorY));
        }

        // ---- the prize's dressing ---------------------------------------------------------------

        /** How many props a room of this floor takes at most: one per six cells of its walls. */
        static int dressingFor(Area room) {
            return Math.max(2, (room.width() + room.height()) / 3);
        }

        /**
         * Furnishes the vault room and the interior's chambers from the site's dressing ({@link RuinsDressing}): props
         * against each room's walls, drawn by a coordinate hash and never from the layout's stream.
         */
        void dress(RuinsDressing dressing, Vault vault, List<Area> chambers) {
            if (dressing == null || dressing.none()) return;
            Area room = new Area(vault.innerDoor().x() + 1, vault.footprint().bottom() + 1,
                    vault.footprint().right() - 1, vault.footprint().top() - 1);
            dressRoom(room, dressing.vault(), vault.source(), 0x7A);
            for (Area chamber : chambers) {
                dressRoom(chamber, dressing.chambers(), new Cell(chamber.centreX(), chamber.centreY()), 0x7B);
            }
        }

        /** Props round one room's walls, clockwise from a hashed start, each kept only if the room stays one floor. */
        void dressRoom(Area room, List<String> props, Cell keepClear, int salt) {
            TileRegistry registry = TileRegistry.installed();
            if (registry == null || props.isEmpty()) return;
            List<int[]> ring = new ArrayList<>();
            for (int x = room.left(); x <= room.right(); x++) ring.add(new int[]{x, room.bottom()});
            for (int y = room.bottom() + 1; y <= room.top(); y++) ring.add(new int[]{room.right(), y});
            for (int x = room.right() - 1; x >= room.left(); x--) ring.add(new int[]{x, room.top()});
            for (int y = room.top() - 1; y > room.bottom(); y--) ring.add(new int[]{room.left(), y});
            int start = hash(room.left(), room.bottom(), salt) % ring.size();
            int placed = 0, limit = dressingFor(room);
            for (int k = 0; k < ring.size() && placed < limit; k += 2) {
                int[] at = ring.get((start + k) % ring.size());
                DoodadDef def = registry.doodad(props.get(hash(at[0], at[1], salt + 1) % props.size()));
                if (def == null) continue;
                int fx = Math.max(1, def.footprintCellsX), fy = Math.max(1, def.footprintCellsY);
                // Anchor the footprint so it lies inside the room from the ring cell.
                int ax = Math.min(at[0], room.right() - fx + 1), ay = Math.min(at[1], room.top() - fy + 1);
                if (ax < room.left() || ay < room.bottom()) continue;
                if (dressProp(def, ax, ay, fx, fy, room, keepClear)) {
                    placed++;
                    k += 2 * Math.max(fx, fy);
                }
            }
        }

        /** Stands a prop if its footprint is open room floor, off every doorway and the kept cell, and the room stays whole. */
        boolean dressProp(DoodadDef def, int ax, int ay, int fx, int fy, Area room, Cell keepClear) {
            for (int y = ay; y < ay + fy; y++) {
                for (int x = ax; x < ax + fx; x++) {
                    if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y)) return false;
                    if (Math.abs(x - keepClear.x()) <= 1 && Math.abs(y - keepClear.y()) <= 1) return false;
                    // Never in the mouth of a way in: no open cell outside the room beside it.
                    for (Direction side : Direction.CARDINALS) {
                        int nx = x + side.dx, ny = y + side.dy;
                        if (!room.contains(nx, ny) && grid.inBounds(nx, ny) && grid.isWalkable(nx, ny)) return false;
                    }
                }
            }
            for (int y = ay; y < ay + fy; y++) for (int x = ax; x < ax + fx; x++) grid.setWalkable(x, y, false);
            if (!oneFloor(room)) {
                for (int y = ay; y < ay + fy; y++) for (int x = ax; x < ax + fx; x++) grid.setWalkableFloor(x, y);
                return false;
            }
            ctx.doodads.add(new Doodad(ax, ay, def));
            for (int y = ay; y < ay + fy; y++) {
                for (int x = ax; x < ax + fx; x++) {
                    grid.setSeeThrough(x, y, true);
                    topology.setFixture(x, y, true);
                    kept[i(x, y)] = true;
                }
            }
            return true;
        }

        /** Whether a room's open cells are all one floor, four-connected. */
        boolean oneFloor(Area room) {
            int open = 0;
            int[] first = null;
            for (int y = room.bottom(); y <= room.top(); y++) {
                for (int x = room.left(); x <= room.right(); x++) {
                    if (!grid.isWalkable(x, y)) continue;
                    open++;
                    if (first == null) first = new int[]{x, y};
                }
            }
            if (first == null) return false;
            boolean[] seen = new boolean[room.width() * room.height()];
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            queue.add(first);
            seen[(first[1] - room.bottom()) * room.width() + first[0] - room.left()] = true;
            int reached = 0;
            while (!queue.isEmpty()) {
                int[] c = queue.poll();
                reached++;
                for (Direction side : Direction.CARDINALS) {
                    int nx = c[0] + side.dx, ny = c[1] + side.dy;
                    if (!room.contains(nx, ny) || !grid.isWalkable(nx, ny)) continue;
                    int at = (ny - room.bottom()) * room.width() + nx - room.left();
                    if (seen[at]) continue;
                    seen[at] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
            return reached == open;
        }

        /** A chamber off an interior band: plain hull floor in the solid, its walls the hull round it. */
        Area chamber(Area region) {
            int cw = 6 + rng.nextInt(5), ch = 5 + rng.nextInt(4);
            int[] at = spot(region, cw, ch, 1);
            if (at == null) return null;
            Area room = new Area(at[0], at[1], at[0] + cw - 1, at[1] + ch - 1);
            for (int y = room.bottom(); y <= room.top(); y++) {
                for (int x = room.left(); x <= room.right(); x++) interiorFloor(x, y);
            }
            reserve(room, 1);
            if (rng.nextBoolean()) loose(WRECKAGE[rng.nextInt(WRECKAGE.length)], room.left() + 1, room.bottom() + 1);
            else loose(SCRAP, room.right() - 1, room.top() - 1);
            return room;
        }

        /**
         * The column a band's spine runs down: through the court, as near its middle as it can (else anywhere clear of the hall), three cells wide
         * and clear of the power hall and the ring of open floor round it, within {@code lo..hi}.
         */
        int spineColumn(Court well, PowerHall hall, int lo, int hi, int band) {
            int best = -1, centre = well.ground().centreX();
            for (int x = Math.max(lo, well.ground().left() + 1); x <= Math.min(hi, well.ground().right() - 1); x++) {
                if (hall != null && x + 1 >= hall.footprint().left() - 2 && x - 1 <= hall.footprint().right() + 2) continue;
                if (best < 0 || Math.abs(x - centre) < Math.abs(best - centre)) best = x;
            }
            // A court straight above or below the hall: the spine runs clear of the hall, the court is joined to it.
            for (int x = lo; best < 0 && x <= hi; x++) {
                if (hall == null || x + 1 < hall.footprint().left() - 2 || x - 1 > hall.footprint().right() + 2) best = x;
            }
            if (best < 0) throw new IllegalStateException(RuinsDelve.layerName(band) + " has no room for its spine");
            return best;
        }

        /** The open cell straight out from a shell's door. */
        Cell outside(Area footprint, Cell door) {
            int x = door.x(), y = door.y();
            if (y == footprint.top()) return new Cell(x, y + 1);
            if (y == footprint.bottom()) return new Cell(x, y - 1);
            return new Cell(x == footprint.left() ? x - 1 : x + 1, y);
        }

        /**
         * Joins {@code from} to interior band {@code band}'s corridor network by the shortest way through the
         * hull, carving a corridor two cells wide; it never crosses a layer line, leaves the interior, or enters
         * {@code avoid}. Every cell it carves joins the network and is kept clear of later pieces.
         */
        void connect(Cell from, int band, Area avoid) {
            int start = i(from.x(), from.y());
            if (network[start] == band + 1) return;
            int[] prev = new int[w * h];
            Arrays.fill(prev, -2);
            int[] queue = new int[w * h];
            int head = 0, tail = 0, goal = -1;
            prev[start] = -1;
            queue[tail++] = start;
            while (head < tail && goal < 0) {
                int at = queue[head++], cx = at % w, cy = at / w;
                for (Direction side : Direction.CARDINALS) {
                    int nx = cx + side.dx, ny = cy + side.dy;
                    if (!grid.inBounds(nx, ny) || prev[i(nx, ny)] != -2 || !corridorCell(nx, ny, avoid)) continue;
                    if (!hull[i(nx, ny)] && !hull[at] && !grid.canTraverseCellStep(cx, cy, nx, ny)) continue;
                    prev[i(nx, ny)] = at;
                    if (network[i(nx, ny)] == band + 1) { goal = i(nx, ny); break; }
                    queue[tail++] = i(nx, ny);
                }
            }
            if (goal < 0) {
                throw new IllegalStateException("no corridor from " + from + " to " + RuinsDelve.layerName(band));
            }
            for (int at = goal; at >= 0; at = prev[at]) {
                int cx = at % w, cy = at / w;
                carve(cx, cy, band);
                if (corridorCell(cx + 1, cy, avoid)) carve(cx + 1, cy, band);
                if (corridorCell(cx, cy + 1, avoid)) carve(cx, cy + 1, band);
            }
        }

        /** Whether a corridor may run through a cell: inside, off the lines, outside {@code avoid}, open or hull. */
        boolean corridorCell(int x, int y, Area avoid) {
            if (x < 1 || y < 1 || x > w - 2 || y > h - 2) return false;
            if (!insideCell(x, y) || lineCell[i(x, y)] || (avoid != null && avoid.contains(x, y))) return false;
            return hull[i(x, y)] || grid.isWalkable(x, y);
        }

        /** One corridor cell of band {@code band}: hull is carved to floor; it is kept clear of later pieces. */
        void carve(int x, int y, int band) {
            if (!corridorCell(x, y, null)) return;
            if (hull[i(x, y)]) interiorFloor(x, y);
            network[i(x, y)] = band + 1;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (grid.inBounds(x + dx, y + dy)) reserved[i(x + dx, y + dy)] = true;
                }
            }
        }

        /** Solid hull: a building wall that a later corridor or room may carve, and the pocket repair may break. */
        void hullCell(int x, int y) {
            wall(x, y, HULL_WALL, HULL_KIND);
            kept[i(x, y)] = false;
            hull[i(x, y)] = true;
        }

        /** Open floor under the megastructure's roof. */
        void interiorFloor(int x, int y) {
            free(x, y);
            roofOver(x, y);
            topology.setSurface(x, y, topology.surfaceIndex(HULL_FLOOR));
        }

        /** Puts an open cell under the roof, keeping its floor (a breach's rubble stays rubble). */
        void roofOver(int x, int y) {
            topology.setGroundKind(x, y, GroundKind.INDOOR);
            topology.setBuildingKindHint(x, y, HULL_KIND);
            drafted[i(x, y)] = true;
        }

        /** An open cell for a works site: interior floor inside, cleared ground outside. */
        void floorAt(int x, int y) {
            if (insideCell(x, y)) interiorFloor(x, y);
            else free(x, y);
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
