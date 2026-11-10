package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.NaturalVoidZoning;
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
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Random;
import java.util.Set;

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
    /**
     * The secure store's size by layer count ({@code ruins-expedition.md}, The site is the plant that made the
     * prize): the plant's cradle hall with its antechamber, from a satellite depot's at scattered to a works' at vast.
     */
    static final int[] VAULT_WIDTHS = {15, 21, 25, 29}, VAULT_HEIGHTS = {11, 15, 17, 19};
    /** A cradle's side, cells, and the aisle left between cradles. */
    static final int CRADLE = 3, CRADLE_GAP = 1;
    static final String WRECK = "doodad.industrial-scrap-pile";
    static final String PULLED = "doodad.industrial-pallet-stack";
    /** Open ground in front of the vault's door, so its approach is a place to hold. */
    static final int FORECOURT = 9;
    static final int ROOM_WIDTH = 7, ROOM_HEIGHT = 6;
    /**
     * Corridor widths ({@code ruins-expedition.md}, Ways sized for machines): a service corridor two cells wide, which
     * a derelict hall frame cannot follow, and a freight way four wide, which joins the plant halls, the ways through
     * the lines and the entrances to the spine, so a hall frame travels the plant.
     */
    static final int SERVICE_WIDTH = 2, FREIGHT_WIDTH = 4;
    /** A mega forge hall's breadth and length, cells; vast's larger. Its pour line runs along its length. */
    static final int MEGA_WIDE = 40, MEGA_LONG = 76, MEGA_WIDE_VAST = 44, MEGA_LONG_VAST = 92;
    /** The narrowest and shortest hall the mega forge's layout fits; a smaller one is laid as a foundry. */
    static final int MEGA_MIN_WIDE = 32, MEGA_MIN_LONG = 44;
    /**
     * The mega forge's great crucible ({@code ruins-forge} sheet): its id, its melt's, its footprint, and the row above
     * its bottom where the spout on its east edge pours onto the line.
     */
    static final String GREAT_CRUCIBLE = "doodad.ruins-great-crucible",
            GREAT_CRUCIBLE_MELT = "doodad.ruins-great-crucible-melt";
    static final int GREAT_CRUCIBLE_WIDE = 12, GREAT_CRUCIBLE_LONG = 16, GREAT_CRUCIBLE_SPOUT = 8;
    /** A forge works' prize as it waits in the vault's cradle: the great crucible's control core, cut free on its skid. */
    static final String FURNACE_CORE = "doodad.ruins-furnace-core";
    /** Cells across a side room's door: a sealed store's blast door. */
    static final int STORE_DOOR = 3;
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
    /**
     * A plant hall's floor zones ({@code ruins-expedition.md}, A hall is laid out from its process): deck plate under
     * the work and a cell round it, painted walkways along the hall's long walls, worn concrete in the aisles with
     * cracked patches where the traffic broke it.
     */
    static final String HALL_PLATE = "sky-port.deck-plate", HALL_WALKWAY = "apron.marked",
            HALL_AISLE = "pavement.concrete.worn", HALL_AISLE_CRACKED = "pavement.concrete.cracked";
    /** A feed path's marked lane: the way the charge comes to a great crucible's hopper. */
    static final String HALL_FEED = "apron.marked";
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
    /** The plant kit (the {@code ruins-themes} sheet): its pieces, all laid with their long axis east-west. */
    static final String LINE_MODULE = "doodad.ruins-line-module";
    static final String LINE_END = "doodad.ruins-line-end";
    static final String EMPTY_CRADLE = "doodad.ruins-empty-cradle";
    static final String RACK_STRIPPED = "doodad.ruins-rack-stripped";
    static final String RACK_CRATES = "doodad.ruins-rack-crates";
    static final String DOCK_DOOR = "doodad.ruins-dock-door";
    static final String DOCK_STOCK = "doodad.industrial-pallet-stack";

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
        /** The plant kit's floor pieces (cradle pads, dock levelers): dressing never stands on them. */
        final boolean[] kitFloor;
        /** Cells of a feed path to a great crucible's hopper, kept clear and painted as a lane. */
        final boolean[] feed;
        /** Each works hall's plan by its footprint, for its floor's zones and skins. */
        final Map<Long, WorksHallPlan> worksPlans = new HashMap<>();
        /** A plant hall's freight aisles: kept clear of furniture and melt, so a hall frame reaches its post. */
        final boolean[] aisle;
        /** Shell cells stamped here, for their wall and door masks. */
        final boolean[] drafted;
        /** Every layer line's cells, ways included: no interior corridor crosses one. */
        final boolean[] lineCell;
        /** Solid hull of the interior, not yet carved. */
        final boolean[] hull;
        /** The interior band (+1) whose corridor network a carved cell belongs to; 0 off every network. */
        final int[] network;
        /** Network cells a hall frame travels: the spines and the freight ways ({@link #FREIGHT_WIDTH}). */
        final boolean[] freightNet;
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
            kitFloor = new boolean[w * h];
            feed = new boolean[w * h];
            aisle = new boolean[w * h];
            drafted = new boolean[w * h];
            lineCell = new boolean[w * h];
            hull = new boolean[w * h];
            network = new int[w * h];
            freightNet = new boolean[w * h];
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

            int half = vaultHeight() / 2;
            int vaultY = clamp(h / 2 + rng.nextInt(h / 3 + 1) - h / 6, half + 5, h - half - 6);
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
            List<RuinsDelve.PlantHall> plantHalls = new ArrayList<>();
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
                // A forge works lays its halls before its side rooms, so its mega hall has the band's length.
                boolean forge = plan.dressing().works().any();
                if (in && forge) layHalls(i, firstInside, region, chambers, plantHalls);
                List<SideRoom> rooms = new ArrayList<>();
                for (int r = 0; r < ROOMS_PER_LAYER; r++) {
                    SideRoom room = room(RuinsDelve.layerName(i) + ".room." + (rooms.size() + 1), region);
                    if (room == null) continue;
                    rooms.add(room);
                    if (in) connect(outside(room.footprint(), room.door()), i, null);
                }
                if (rooms.isEmpty()) throw new IllegalStateException(RuinsDelve.layerName(i) + " seats no side room");
                if (in) {
                    if (!forge) layHalls(i, firstInside, region, chambers, plantHalls);
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
            for (RuinsDelve.PlantHall plantHall : plantHalls) furnish(plantHall);
            // A great hall is furnished by its process alone: no loose dressing against its walls.
            List<Area> dressed = new ArrayList<>(chambers);
            for (RuinsDelve.PlantHall plantHall : plantHalls) {
                if (plantHall.stage() == RuinsDelve.PlantStage.WORKS
                        || plantHall.stage() == RuinsDelve.PlantStage.FOUNDRY) dressed.remove(plantHall.footprint());
            }
            dress(plan.dressing(), vault, dressed);
            for (RuinsDelve.PlantHall plantHall : plantHalls) zoneFloor(plantHall.footprint());
            finishMasks();
            repairPockets(landing.get(0).berth(0));

            LayerDraft last = drafts.get(layers - 1);
            Way shortWay = null;
            for (Way way : last.ways) if (way.kind() == WayKind.COLLAPSE) shortWay = way;
            Collapse collapse = new Collapse(RuinsDelve.COLLAPSE, layers - 1, shortWay);
            List<RuinsDelve.LeafDoor> leafDoors = leafDoors(plantHalls, entrances);
            // The vault's outer door: a hall door across its three cells, kept powered so the haul's way stays whole.
            Cell outer = vault.door();
            leafDoors.add(new RuinsDelve.LeafDoor(new Area(outer.x(), outer.y() - 1, outer.x(), outer.y() + 1), false,
                    false, RuinsDelve.DoorStance.ANSWERS));
            RuinsDelve.Interior interior = new RuinsDelve.Interior(RuinsDelve.INTERIOR, interiorBox, firstInside,
                    entrances, aprons, chambers, plantHalls, leafDoors);
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

        // ---- hall floors ---------------------------------------------------------------------

        /**
         * Lays a plant hall's floor by zone rather than as one slab. The work is every cell its furniture stands on:
         * the kit's floor (cradles, levelers, melt), every covered piece's footprint and every fixture; markings, wear
         * and overhead structure are not work. Deck plate lies under the work and one cell round it; a painted walkway runs a cell deep along the hall's two long walls; the
         * aisles left between are worn concrete, cracked in patches by coordinate hash. A melt cell keeps its
         * surface, which its drop draws over.
         */
        void zoneFloor(Area hall) {
            int hw = hall.width(), hh = hall.height();
            boolean[] work = new boolean[hw * hh];
            for (int y = hall.bottom(); y <= hall.top(); y++) {
                for (int x = hall.left(); x <= hall.right(); x++) {
                    if (kitFloor[i(x, y)] || topology.isFixture(x, y) || !grid.isWalkable(x, y)) {
                        work[(y - hall.bottom()) * hw + x - hall.left()] = true;
                    }
                }
            }
            for (Doodad d : ctx.doodads) {
                // Floor markings, wear and overhead structure are not work: only a piece with cover is.
                if (d.cover <= 0) continue;
                for (int y = d.cellY; y < d.cellY + Math.max(1, d.footprintCellsY); y++) {
                    for (int x = d.cellX; x < d.cellX + Math.max(1, d.footprintCellsX); x++) {
                        if (hall.contains(x, y)) work[(y - hall.bottom()) * hw + x - hall.left()] = true;
                    }
                }
            }
            int plate = topology.surfaceIndex(HALL_PLATE), walkway = topology.surfaceIndex(HALL_WALKWAY);
            int worn = topology.surfaceIndex(HALL_AISLE), cracked = topology.surfaceIndex(HALL_AISLE_CRACKED);
            int feedLane = topology.surfaceIndex(HALL_FEED);
            WorksHallPlan works = worksPlans.get(key(hall));
            boolean longX = hw >= hh;
            for (int y = hall.bottom(); y <= hall.top(); y++) {
                for (int x = hall.left(); x <= hall.right(); x++) {
                    if (topology.isWall(x, y) || topology.getGroundKind(x, y) != GroundKind.INDOOR) continue;
                    boolean near = false;
                    for (int dy = -1; dy <= 1 && !near; dy++) {
                        for (int dx = -1; dx <= 1 && !near; dx++) {
                            int lx = x + dx - hall.left(), ly = y + dy - hall.bottom();
                            near = lx >= 0 && ly >= 0 && lx < hw && ly < hh && work[ly * hw + lx];
                        }
                    }
                    boolean wallSide = longX ? (y == hall.bottom() || y == hall.top())
                            : (x == hall.left() || x == hall.right());
                    int surface = feed[i(x, y)] ? feedLane : aisle[i(x, y)] ? walkway : near ? plate
                            : wallSide ? walkway : hash(x / 3, y / 3, 0xF10) % 4 == 0 ? cracked : worn;
                    if (works != null) {
                        int lx = x - hall.left(), ly = y - hall.bottom();
                        switch (works.zone(lx, ly)) {
                            case LANE -> surface = feedLane;
                            case BAY -> surface = walkway;
                            case PLATE -> surface = plate;
                            case SPECIAL -> surface = topology.surfaceIndex(VAULT_ROOM_FLOOR);
                            case DERIVED -> { }
                        }
                        String skin = works.skin(lx, ly);
                        if (skin != null) surface = topology.surfaceIndex(skin);
                    }
                    topology.setSurface(x, y, surface);
                }
            }
        }

        /**
         * A plant hall's freight aisles ({@code ruins-expedition.md}, Ways sized for machines): from every freight way
         * that meets the hall, an aisle {@link #FREIGHT_WIDTH} wide runs straight in to the hall's middle row or column
         * and along it to the middle, where a hall frame stands its post. Furniture and melt keep off it, and its floor
         * is painted as a walkway, so the frame reaches its post from every freight way. Service corridors get none.
         */
        void frameAisles(Area hall) {
            int cx = hall.centreX(), cy = hall.centreY();
            for (int side = 0; side < 4; side++) {
                boolean alongX = side < 2;
                int line = side == 0 ? hall.bottom() - 1 : side == 1 ? hall.top() + 1 : side == 2 ? hall.left() - 1
                        : hall.right() + 1;
                int from = alongX ? hall.left() : hall.bottom(), to = alongX ? hall.right() : hall.top();
                int run = -1;
                for (int a = from; a <= to + 1; a++) {
                    int x = alongX ? a : line, y = alongX ? line : a;
                    boolean open = a <= to && grid.inBounds(x, y) && grid.isWalkable(x, y) && !topology.isWall(x, y);
                    if (open && run < 0) run = a;
                    if (open || run < 0) continue;
                    if (a - run > SERVICE_WIDTH) {
                        int mid = (run + a - 1) / 2;
                        if (alongX) aisleBox(hall, mid - 1, Math.min(line, cy), mid + 2, Math.max(line, cy));
                        else aisleBox(hall, Math.min(line, cx), mid - 1, Math.max(line, cx), mid + 2);
                        if (alongX) aisleBox(hall, Math.min(mid, cx) - 1, cy - 1, Math.max(mid, cx) + 2, cy + 2);
                        else aisleBox(hall, cx - 1, Math.min(mid, cy) - 1, cx + 2, Math.max(mid, cy) + 2);
                    }
                    run = -1;
                }
            }
        }

        private void aisleBox(Area hall, int l, int b, int r, int t) {
            for (int y = b; y <= t; y++) {
                for (int x = l; x <= r; x++) if (hall.contains(x, y)) aisle[i(x, y)] = true;
            }
        }

        // ---- leaf doors ----------------------------------------------------------------------

        /**
         * A sliding leaf door in every plant hall's join and a cargo door in every way in from open ground
         * ({@code ruins-expedition.md}, The ruins' doors are leaf doors). A join is a run of open cells, two to eight
         * long, through the ring of hull round a hall. Each door's stance is drawn by coordinate hash: a third stand
         * dead part-open, one in six was forced long ago, the rest still answer. A dead door's leaves stand in its
         * end cells, which are shut so the way narrows to the cells between; only a hall door of three cells or more
         * can die so. A cargo door stands in a way in, which stays open its whole width, so it never dies.
         */
        List<RuinsDelve.LeafDoor> leafDoors(List<RuinsDelve.PlantHall> halls, List<Area> entrances) {
            List<RuinsDelve.LeafDoor> out = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            for (RuinsDelve.PlantHall hall : halls) {
                Area f = hall.footprint();
                joins(out, seen, true, f.left(), f.right(), f.bottom() - 1);
                joins(out, seen, true, f.left(), f.right(), f.top() + 1);
                joins(out, seen, false, f.bottom(), f.top(), f.left() - 1);
                joins(out, seen, false, f.bottom(), f.top(), f.right() + 1);
            }
            for (Area way : entrances) {
                boolean alongX = way.width() > way.height();
                Area span = alongX ? new Area(way.left(), way.bottom(), way.right(), way.bottom())
                        : new Area(way.left(), way.bottom(), way.left(), way.top());
                if (seen.add(key(span))) out.add(stance(span, alongX, true));
            }
            return out;
        }

        private void joins(List<RuinsDelve.LeafDoor> out, Set<Long> seen, boolean alongX, int from, int to,
                           int line) {
            int run = -1;
            for (int a = from; a <= to + 1; a++) {
                int x = alongX ? a : line, y = alongX ? line : a;
                boolean gap = a <= to && grid.inBounds(x, y) && grid.isWalkable(x, y) && !topology.isWall(x, y);
                if (gap && run < 0) run = a;
                if (!gap && run >= 0) {
                    int n = a - run;
                    if (n >= 2 && n <= 8) {
                        Area span = alongX ? new Area(run, line, a - 1, line) : new Area(line, run, line, a - 1);
                        if (seen.add(key(span))) out.add(stance(span, alongX, false));
                    }
                    run = -1;
                }
            }
        }

        private static long key(Area a) {
            return ((long) a.left() << 48) ^ ((long) a.bottom() << 32) ^ ((long) a.right() << 16) ^ a.top();
        }

        /** The door's stance by hash; a dead one's end cells are shut where its leaves stand. */
        private RuinsDelve.LeafDoor stance(Area span, boolean alongX, boolean cargo) {
            int roll = hash(span.left(), span.bottom(), 0xD0A) % 6;
            int length = alongX ? span.width() : span.height();
            // A way in from open ground stays open its whole width (the delve's law), so a cargo door never dies shut.
            RuinsDelve.DoorStance stance = roll < 2 && !cargo && length >= 3 ? RuinsDelve.DoorStance.DEAD
                    : roll == 2 ? RuinsDelve.DoorStance.FORCED : RuinsDelve.DoorStance.ANSWERS;
            if (stance == RuinsDelve.DoorStance.DEAD) {
                leaf(span.left(), span.bottom());
                leaf(span.right(), span.top());
            }
            return new RuinsDelve.LeafDoor(span, alongX, cargo, stance);
        }

        /** A dead leaf standing in a door's end cell: shut to passage and sight, never taken for a wall. */
        private void leaf(int x, int y) {
            removeProps(x, y);
            grid.setWalkable(x, y, false);
            grid.setSeeThrough(x, y, false);
            topology.setFixture(x, y, true);
            kept[i(x, y)] = true;
        }

        // ---- vault ---------------------------------------------------------------------------

        Vault vault(int cy) {
            int x1 = w - 4, x0 = x1 - vaultWidth() + 1, y0 = cy - vaultHeight() / 2, y1 = y0 + vaultHeight() - 1;
            apron = new Area(x0 - FORECOURT, y0 - 3, w - 2, y1 + 3);
            open(apron, null);
            reserve(apron, 1);
            shell(x0, y0, x1, y1, VAULT_WALL, VAULT_FLOOR, BuildingKind.FORTIFIED);
            int partition = x0 + 4;
            for (int y = y0 + 1; y < y1; y++) wall(partition, y, VAULT_WALL, BuildingKind.FORTIFIED);
            // Both doors are three cells across, as wide as the aisle the cradles leave: blast doors, not hatches.
            for (int d = -1; d <= 1; d++) {
                door(x0, cy + d);
                door(partition, cy + d);
            }
            // The breach is held on the aisle, before the cradle the prize survives in at its far end.
            Cell source = new Cell(x1 - CRADLE - 2, cy);
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
            List<int[]> buttresses = new ArrayList<>(List.of(new int[]{x0 - 1, y0 - 1}, new int[]{x0 - 1, y1 + 1},
                    new int[]{x1 + 1, y0 - 1}, new int[]{x1 + 1, y1 + 1}, new int[]{x1 + 1, cy - 2},
                    new int[]{x1 + 1, cy + 2}));
            for (int x = x0 + 3; x < x1; x += 4) {
                buttresses.add(new int[]{x, y0 - 1});
                buttresses.add(new int[]{x, y1 + 1});
            }
            for (int[] b : buttresses) wall(b[0], b[1], VAULT_WALL, BuildingKind.FORTIFIED);
            int room = topology.surfaceIndex(VAULT_ROOM_FLOOR);
            for (int y = y0 + 1; y < y1; y++) for (int x = partition + 1; x < x1; x++) topology.setSurface(x, y, room);
            cradles(partition, y0, x1, y1, cy);
            prop(CONSOLE, x0 + 2, y1 - 1);
        }

        /**
         * The secure store's cradles: rows of {@link #CRADLE}-square pads along its long walls, an aisle left down the
         * middle from the inner door. Units were pulled out of most and the rest wrecked where they stood; the one
         * at the aisle's far end still holds the prize: the theme's product where it has one, else a crate.
         */
        void cradles(int partition, int y0, int x1, int y1, int cy) {
            int pad = topology.surfaceIndex(VAULT_FLOOR);
            int left = partition + 2, right = x1 - 1;
            int[] rowsLow = {y0 + 1}, rowsHigh = {y1 - CRADLE};
            if (y1 - y0 - 1 >= 4 * CRADLE + 3) {
                rowsLow = new int[]{y0 + 1, y0 + 1 + CRADLE + CRADLE_GAP};
                rowsHigh = new int[]{y1 - CRADLE, y1 - 2 * CRADLE - CRADLE_GAP};
            }
            for (int[] rows : new int[][]{rowsLow, rowsHigh}) {
                for (int by : rows) {
                    for (int bx = left; bx + CRADLE - 1 <= right - CRADLE - 1; bx += CRADLE + CRADLE_GAP) {
                        if (by <= cy + 1 && by + CRADLE - 1 >= cy - 1) continue;
                        for (int y = by; y < by + CRADLE; y++) for (int x = bx; x < bx + CRADLE; x++) {
                            topology.setSurface(x, y, pad);
                            kitFloor[i(x, y)] = true;
                        }
                        // Most cradles stand empty where their units were pulled out; a few hold a wreck. The wreck
                        // goes first, since a prop clears what is drawn over its cell; the frame then goes under it.
                        int under = ctx.doodads.size();
                        TileRegistry registry = TileRegistry.installed();
                        if (hash(bx, by, 0xC4A) % 4 == 0) wreck(registry, bx, by);
                        if (registry != null) lay(registry, EMPTY_CRADLE, bx, by, under);
                    }
                }
            }
            // The prize's cradle closes the aisle at the far wall: the one unit that never left.
            int px = x1 - CRADLE;
            for (int y = cy - 1; y <= cy + 1; y++) {
                for (int x = px; x < px + CRADLE; x++) {
                    topology.setSurface(x, y, pad);
                    kitFloor[i(x, y)] = true;
                }
            }
            TileRegistry registry = TileRegistry.installed();
            int under = ctx.doodads.size();
            String product = plan.dressing().whole();
            // A forge works' prize is the great crucible's control core on its skid, where the registry has it.
            if (plan.dressing().foundry() && registry != null && registry.doodad(FURNACE_CORE) != null) {
                product = FURNACE_CORE;
            }
            DoodadDef def = registry == null || product == null ? null : registry.doodad(product);
            if (def == null) prop(HAUL, px + 1, cy);
            else {
                // The prize itself, in its own cradle: the theme's product, whole, exactly over the frame.
                ctx.doodads.add(new Doodad(px, cy - 1, def));
                for (int y = cy - 1; y < cy - 1 + Math.max(1, def.footprintCellsY); y++) {
                    for (int x = px; x < px + Math.max(1, def.footprintCellsX); x++) {
                        grid.setWalkable(x, y, false);
                        grid.setSeeThrough(x, y, true);
                        topology.setFixture(x, y, true);
                        kept[i(x, y)] = true;
                    }
                }
            }
            if (registry != null) lay(registry, EMPTY_CRADLE, px, cy - 1, under);
        }

        int vaultWidth() { return VAULT_WIDTHS[Math.min(plan.layers(), VAULT_WIDTHS.length) - 1]; }

        int vaultHeight() { return VAULT_HEIGHTS[Math.min(plan.layers(), VAULT_HEIGHTS.length) - 1]; }

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
            // A side room may be sealed as a store, behind a three-cell blast door, so every one is cut that wide.
            Shell s = shellIn(region, ROOM_WIDTH, ROOM_HEIGHT, RUIN_WALLS[rng.nextInt(RUIN_WALLS.length)],
                    RUIN_FLOORS[rng.nextInt(RUIN_FLOORS.length)], BuildingKind.INDUSTRIAL, STORE_DOOR);
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
            return shellIn(region, width, height, wall, floor, kind, 1);
        }

        /** A stamped one-room shell whose door is {@code doorWidth} cells across, centred on its wall. */
        Shell shellIn(Area region, int width, int height, String wall, String floor, BuildingKind kind, int doorWidth) {
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
            for (int d = -(doorWidth / 2); d <= doorWidth / 2; d++) door(area.centreX() + d, dy);
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
                for (int y = 2; y <= h - 3; y++) {
                    for (int x = spine - 1; x <= spine + 1; x++) {
                        carve(x, y, k);
                        freightNet[i(x, y)] = true;
                    }
                }
                for (Way way : drafts.get(k).ways) connect(way.far(), k, null, FREIGHT_WIDTH);
                if (k + 1 < drafts.size()) {
                    for (Way way : drafts.get(k + 1).ways) connect(way.near(), k, null, FREIGHT_WIDTH);
                }
                if (k == hallLayer) connect(outside(innerHall.footprint(), innerHall.door()), k, null);
                connect(new Cell(well.ground().centreX(), well.ground().centreY()), k, null);
            }
            int last = drafts.size() - 1;
            connect(new Cell(apron.left() - 1, apron.bottom() + 2), last, apron, FREIGHT_WIDTH);
            connect(new Cell(apron.left() - 1, apron.top() - 3), last, apron, FREIGHT_WIDTH);
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
         * against each room's walls and patches on its floor, drawn by a coordinate hash and never from the layout's
         * stream.
         */
        void dress(RuinsDressing dressing, Vault vault, List<Area> chambers) {
            if (dressing == null || dressing.none()) return;
            Area room = new Area(vault.innerDoor().x() + 1, vault.footprint().bottom() + 1,
                    vault.footprint().right() - 1, vault.footprint().top() - 1);
            // The floor first, so a prop that stands on a patch is drawn over it.
            dressFloor(room, dressing.floor(), vault.source(), 0x7C);
            for (Area chamber : chambers) {
                dressFloor(chamber, dressing.floor(), new Cell(chamber.centreX(), chamber.centreY()), 0x7D);
            }
            dressRoom(room, dressing.vault(), vault.source(), 0x7A);
            for (Area chamber : chambers) {
                dressRoom(chamber, dressing.chambers(), new Cell(chamber.centreX(), chamber.centreY()), 0x7B);
            }
        }

        /** How many floor patches a room takes at most: one per sixteen cells of floor, never more than three. */
        static int patchesFor(Area room) {
            return Math.min(3, Math.max(1, room.width() * room.height() / 16));
        }

        /**
         * Flat patches on a room's open floor at hashed cells: each wholly on open room floor, off every doorway and
         * the kept cell, and off every piece already standing, other patches included. A patch is walkable and closes
         * nothing, so the grid is left as it was found.
         */
        void dressFloor(Area room, List<String> patches, Cell keepClear, int salt) {
            TileRegistry registry = TileRegistry.installed();
            if (registry == null || patches.isEmpty()) return;
            int limit = patchesFor(room), laid = 0;
            for (int k = 0; k < 4 * limit && laid < limit; k++) {
                DoodadDef def = registry.doodad(patches.get(hash(room.left() + k, room.top(), salt + 1) % patches.size()));
                if (def == null) continue;
                int fx = Math.max(1, def.footprintCellsX), fy = Math.max(1, def.footprintCellsY);
                int spanX = room.width() - fx + 1, spanY = room.height() - fy + 1;
                if (spanX <= 0 || spanY <= 0) return;
                int ax = room.left() + hash(room.left(), room.bottom() + k, salt) % spanX;
                int ay = room.bottom() + hash(room.right() + k, room.bottom(), salt + 2) % spanY;
                if (!openFloor(ax, ay, fx, fy, keepClear) || onDoodad(ax, ay, fx, fy)) continue;
                ctx.doodads.add(new Doodad(ax, ay, def));
                laid++;
            }
        }

        /** Whether a footprint is all open floor, off every doorway and clear of the kept cell. */
        boolean openFloor(int ax, int ay, int fx, int fy, Cell keepClear) {
            for (int y = ay; y < ay + fy; y++) {
                for (int x = ax; x < ax + fx; x++) {
                    if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y)) return false;
                    if (kitFloor[i(x, y)] || aisle[i(x, y)]) return false;
                    if (Math.abs(x - keepClear.x()) <= 1 && Math.abs(y - keepClear.y()) <= 1) return false;
                }
            }
            return true;
        }

        /** Whether a footprint meets a piece already standing (scrap, another patch), which a flat patch must not cover. */
        boolean onDoodad(int ax, int ay, int fx, int fy) {
            for (Doodad d : ctx.doodads) {
                int dx = Math.max(1, d.footprintCellsX), dy = Math.max(1, d.footprintCellsY);
                if (ax < d.cellX + dx && d.cellX < ax + fx && ay < d.cellY + dy && d.cellY < ay + fy) return true;
            }
            return false;
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
                    if (kitFloor[i(x, y)] || aisle[i(x, y)]) return false;
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
        /**
         * The plant halls an interior band holds, by its place among the interior's bands: the outer band takes the
         * intake docks and the lines, the deeper band storage and one more line; a lone band takes intake and lines.
         * Vast ground lays one more of each.
         */
        List<RuinsDelve.PlantStage> stages(int band, int bands) {
            if (plan.dressing().works().any()) return forgeStages(band, bands);
            List<RuinsDelve.PlantStage> out = new ArrayList<>();
            boolean outer = band == 0;
            if (outer) {
                out.add(RuinsDelve.PlantStage.INTAKE);
                out.add(RuinsDelve.PlantStage.PRODUCTION);
                out.add(RuinsDelve.PlantStage.PRODUCTION);
            }
            if (!outer || bands == 1) {
                if (bands > 1) {
                    out.add(RuinsDelve.PlantStage.STORAGE);
                    out.add(RuinsDelve.PlantStage.STORAGE);
                }
                out.add(RuinsDelve.PlantStage.PRODUCTION);
            }
            if (plan.layers() >= 4) out.addAll(List.copyOf(out));
            return out;
        }

        /**
         * The plant's halls of band {@code i}, in its own order: intake and lines in the outer band, storage deeper.
         * The outer band's first line hall is laid at a foundry's size whatever the theme, so a themed ground's
         * footprint stays the same; a forge works lays its mega forge hall instead ({@link #forgeStages}).
         */
        void layHalls(int i, int firstInside, Area region, List<Area> chambers, List<RuinsDelve.PlantHall> plantHalls) {
            for (RuinsDelve.PlantStage stage : stages(i - firstInside, plan.interiorLayers())) {
                boolean first = i == firstInside && stage == RuinsDelve.PlantStage.PRODUCTION
                        && plantHalls.stream().noneMatch(p -> p.stage() != RuinsDelve.PlantStage.INTAKE);
                Area floor = plantHall(region, first ? RuinsDelve.PlantStage.FOUNDRY : stage);
                if (floor == null) continue;
                connect(new Cell(floor.centreX(), floor.centreY()), i, null, FREIGHT_WIDTH);
                chambers.add(floor);
                plantHalls.add(new RuinsDelve.PlantHall(RuinsDelve.INTERIOR + ".hall." + (plantHalls.size() + 1),
                        stage, floor));
            }
        }

        /**
         * A forge works' halls (owner pick {@code ruins-forge-process} b1): its production is one mega forge hall in
         * the outer band after the intake, its storage deeper; no line halls. At vast the intake and the storage are
         * doubled, never the forge.
         */
        List<RuinsDelve.PlantStage> forgeStages(int band, int bands) {
            List<RuinsDelve.PlantStage> out = new ArrayList<>();
            boolean vast = plan.layers() >= 4;
            if (band == 0) {
                // The works hall is placed first, while the band still has room for it; the intake after.
                out.add(plan.dressing().foundry() ? RuinsDelve.PlantStage.FOUNDRY : RuinsDelve.PlantStage.WORKS);
                out.add(RuinsDelve.PlantStage.INTAKE);
                if (vast) out.add(RuinsDelve.PlantStage.INTAKE);
            }
            if (band > 0 || bands == 1) {
                int storage = bands > 1 ? (vast ? 4 : 2) : 0;
                for (int k = 0; k < storage; k++) out.add(RuinsDelve.PlantStage.STORAGE);
            }
            return out;
        }

        /** A plant hall of {@code stage}'s size laid in {@code region}, its floor carved; null where none fits. */
        Area plantHall(Area region, RuinsDelve.PlantStage stage) {
            boolean vast = plan.layers() >= 4;
            int cw = switch (stage) {
                case INTAKE -> 16;
                case PRODUCTION -> vast ? 30 : 26;
                case FOUNDRY, WORKS -> vast ? 40 : 34;
                case STORAGE -> 18;
            };
            int ch = switch (stage) {
                case INTAKE -> 12;
                case PRODUCTION -> vast ? 18 : 16;
                case FOUNDRY, WORKS -> vast ? 24 : 22;
                case STORAGE -> 14;
            };
            boolean mega = stage == RuinsDelve.PlantStage.WORKS
                    || stage == RuinsDelve.PlantStage.FOUNDRY && plan.dressing().foundry();
            if (mega) {
                // A forge works' mega hall: a great crucible's pour line run down the band's length.
                cw = vast ? MEGA_WIDE_VAST : MEGA_WIDE;
                ch = Math.min(vast ? MEGA_LONG_VAST : MEGA_LONG, region.height() - 8);
            }
            cw = Math.min(cw, region.width() - 6);
            if (cw < 12) return null;
            int[] at = spot(region, cw, ch, 2);
            // A mega forge that finds no room at its full length is shortened, never below the layout's least.
            while (at == null && mega && ch - 8 >= MEGA_MIN_LONG) {
                ch -= 8;
                at = spot(region, cw, ch, 2);
            }
            if (at == null) return null;
            Area room = new Area(at[0], at[1], at[0] + cw - 1, at[1] + ch - 1);
            for (int y = room.bottom(); y <= room.top(); y++) {
                for (int x = room.left(); x <= room.right(); x++) interiorFloor(x, y);
            }
            reserve(room, 2);
            return room;
        }

        /**
         * A plant hall's stage furniture from the plant kit: line runs down a production hall, rack rows across a
         * storage bay, dock doors and stock left on an intake dock's floor. Each run leaves gaps, every piece stands only where the hall stays
         * one floor, and pieces are drawn by coordinate hash, not from the layout's stream.
         */
        void furnish(RuinsDelve.PlantHall plantHall) {
            TileRegistry registry = TileRegistry.installed();
            if (registry == null) return;
            Area hall = plantHall.footprint();
            Cell centre = new Cell(hall.centreX(), hall.centreY());
            boolean mega = plantHall.stage() == RuinsDelve.PlantStage.FOUNDRY && plan.dressing().foundry()
                    && hall.width() >= MEGA_MIN_WIDE && hall.height() >= MEGA_MIN_LONG;
            // The mega forge and every works hall keep their process layout: one transfer aisle across the middle,
            // over the line, joins the frame's post to the floor round its walls (WorksHallPlan keeps it clear).
            if (mega || plantHall.stage() == RuinsDelve.PlantStage.WORKS) aisleBox(hall, hall.left(), centre.y() - 1, hall.right(), centre.y() + 2);
            else frameAisles(hall);
            switch (plantHall.stage()) {
                case FOUNDRY -> {
                    if (plan.dressing().foundry() && hall.width() >= MEGA_MIN_WIDE && hall.height() >= MEGA_MIN_LONG) {
                        megaForge(registry, hall, centre);
                    } else {
                        foundry(registry, hall, centre);
                    }
                }
                case WORKS -> {
                    WorksHall works = plan.dressing().works();
                    WorksHallPlan laid = hall.width() >= MEGA_MIN_WIDE && hall.height() >= MEGA_MIN_LONG
                            ? works.plan(hall.width(), hall.height(), worksSeed(hall))
                            : null;
                    if (laid != null) worksHall(registry, hall, centre, laid);
                    else lines(registry, hall, centre);
                }
                case PRODUCTION -> lines(registry, hall, centre);
                case STORAGE -> {
                    // Rack rows, two deep with a two-cell aisle, a gap between racks; most stripped, a few stocked.
                    for (int y = hall.bottom() + 2; y + 1 <= hall.top() - 2; y += 4) {
                        for (int x = hall.left() + 2; x + 5 <= hall.right() - 2; x += 8) {
                            piece(registry, hash(x, y, 0x5A7) % 3 == 0 ? RACK_CRATES : RACK_STRIPPED, x, y, 6, 2, hall,
                                    centre);
                        }
                    }
                }
                case INTAKE -> {
                    // Dock doors in the north wall, their levelers on the floor; stock left lying here and there.
                    for (int x = hall.left() + 1; x + 3 <= hall.right() - 1; x += 6) {
                        lay(registry, DOCK_DOOR, x, hall.top() - 1);
                        for (int y = hall.top() - 1; y <= hall.top(); y++) {
                            for (int dx = x; dx < x + 4; dx++) kitFloor[i(dx, y)] = true;
                        }
                    }
                    for (int y = hall.bottom() + 2; y <= hall.top() - 4; y += 3) {
                        for (int x = hall.left() + 2; x <= hall.right() - 2; x += 3) {
                            if (hash(x, y, 0xD0C) % 4 == 0) piece(registry, DOCK_STOCK, x, y, 1, 1, hall, centre);
                        }
                    }
                }
            }
        }

        /**
         * A themed plant's works hall, stood from its plan ({@link WorksHallPlan}, {@code ruins-expedition.md}, A hall
         * is laid out from its process): wear and floor kit drawn under everything, then the equipment, each piece
         * only where the hall stays one floor, then the overlays and the structure overhead. A piece whose art has not
         * shipped stands as its stand-in from the game's own sets, tiled over the piece's footprint. Shallow water
         * is tagged on the grid; zones and skins are laid with the floor ({@link #zoneFloor}).
         */
        void worksHall(TileRegistry registry, Area hall, Cell centre, WorksHallPlan laid) {
            worksPlans.put(key(hall), laid);
            int x0 = hall.left(), y0 = hall.bottom();
            for (int y = 0; y < laid.length(); y++) {
                for (int x = 0; x < laid.width(); x++) {
                    if (!laid.water(x, y) || !grid.isWalkable(x0 + x, y0 + y)) continue;
                    topology.setTag(x0 + x, y0 + y, CellTopology.Tag.SHALLOW_WATER, true);
                    grid.setTag(x0 + x, y0 + y, NavigationGrid.CellTag.SHALLOW_WATER, true);
                }
            }
            // The hall's drop is held unwalkable while the pieces stand, so the hall is proved one floor with it cut.
            boolean[] cut = null;
            if (laid.dropKind() != null) {
                cut = new boolean[w * h];
                for (int y = 0; y < laid.length(); y++) {
                    for (int x = 0; x < laid.width(); x++) {
                        int gx = x0 + x, gy = y0 + y;
                        if (!laid.drop(x, y) || !grid.isWalkable(gx, gy) || nearDoorway(gx, gy)) continue;
                        cut[i(gx, gy)] = true;
                        grid.setWalkable(gx, gy, false);
                    }
                }
            }
            int under = ctx.doodads.size();
            for (WorksHallPlan.Layer layer : List.of(WorksHallPlan.Layer.WEAR, WorksHallPlan.Layer.FLOOR)) {
                for (WorksHallPlan.Piece p : laid.pieces()) {
                    if (p.layer() != layer) continue;
                    int before = ctx.doodads.size();
                    tile(registry, p, x0, y0, under, hall, centre);
                    under += ctx.doodads.size() - before;
                }
            }
            for (WorksHallPlan.Layer layer : List.of(WorksHallPlan.Layer.STAND, WorksHallPlan.Layer.OVER,
                    WorksHallPlan.Layer.OVERHEAD)) {
                for (WorksHallPlan.Piece p : laid.pieces()) {
                    if (p.layer() == layer) tile(registry, p, x0, y0, -1, hall, centre);
                }
            }
            // Floor kit is kept clear of anything laid after.
            for (WorksHallPlan.Piece p : laid.pieces()) {
                if (p.layer() != WorksHallPlan.Layer.FLOOR) continue;
                for (int y = p.y(); y < p.y() + p.h(); y++) {
                    for (int x = p.x(); x < p.x() + p.w(); x++) kitFloor[i(x0 + x, y0 + y)] = true;
                }
            }
            if (cut != null) {
                boolean whole = oneFloor(hall);
                for (int k = 0; k < cut.length; k++) {
                    if (!cut[k]) continue;
                    grid.setWalkable(k % w, k / w, true);
                    kitFloor[k] = true;
                }
                if (!whole) throw new IllegalStateException("a works hall's drop split its hall at " + hall);
                ctx.put(RuinsKeys.MELT, cut);
                ctx.put(RuinsKeys.MELT_DROP, switch (laid.dropKind()) {
                    case MELT -> NaturalVoidZoning.FOUNDRY;
                    case SHAFT -> NaturalVoidZoning.DRY_GORGE;
                    case SLUDGE -> NaturalVoidZoning.TOXIC_GORGE;
                });
            }
        }

        /**
         * Lays one planned piece at the hall's corner {@code (x0, y0)}: its own art when it has shipped and fits, else
         * its stand-in, either tiled at its own size over the piece's footprint, centred. A standing piece blocks where the hall stays one floor; any
         * other is drawn, at {@code under} when that is not negative.
         */
        private void tile(TileRegistry registry, WorksHallPlan.Piece p, int x0, int y0, int under, Area hall,
                          Cell centre) {
            // Its own art where it fits the planned footprint, else its stand-in; each tiled at its own size.
            DoodadDef def = null;
            int fx = 0, fy = 0;
            for (String id : new String[]{p.id(), p.standIn()}) {
                DoodadDef d = id == null ? null : registry.doodad(id);
                if (d == null) continue;
                int dx = Math.max(1, d.footprintCellsX), dy = Math.max(1, d.footprintCellsY);
                if (dx > p.w() || dy > p.h()) continue;
                def = d;
                fx = dx;
                fy = dy;
                break;
            }
            if (def == null) return;
            int nx = p.w() / fx, ny = p.h() / fy;
            int ox = x0 + p.x() + (p.w() - nx * fx) / 2, oy = y0 + p.y() + (p.h() - ny * fy) / 2;
            for (int ty = 0; ty < ny; ty++) {
                for (int tx = 0; tx < nx; tx++) {
                    int ax = ox + tx * fx, ay = oy + ty * fy;
                    switch (p.layer()) {
                        case STAND -> dressProp(def, ax, ay, fx, fy, hall, centre);
                        case OVER, OVERHEAD -> ctx.doodads.add(new Doodad(ax, ay, def));
                        case WEAR, FLOOR -> ctx.doodads.add(Math.min(Math.max(0, under), ctx.doodads.size()),
                                new Doodad(ax, ay, def));
                    }
                    if (under >= 0) under++;
                }
            }
        }

        /**
         * A line hall's runs east-west, three deep with a two-cell aisle between: a line end feeding modules, a gap
         * after every second module so a run is never a wall, and a module wrecked here and there.
         */
        void lines(TileRegistry registry, Area hall, Cell centre) {
            for (int y = hall.bottom() + 2; y + 2 <= hall.top() - 2; y += 5) {
                int x = hall.left() + 2;
                if (piece(registry, LINE_END, x, y, 3, 3, hall, centre)) x += 3;
                int run = 0;
                while (x + 5 <= hall.right() - 2) {
                    if (run == 2) {
                        x += 2;
                        run = 0;
                        continue;
                    }
                    if (hash(x, y, 0x11E) % 7 == 0) piece(registry, WRECK, x + 2, y + 1, 1, 1, hall, centre);
                    else if (piece(registry, LINE_MODULE, x, y, 6, 3, hall, centre)) onLine(registry, x, y);
                    x += 6;
                    run++;
                }
            }
        }

        /**
         * A forge works' foundry ({@code ruins-expedition.md}, A forge works has a foundry): a bank of crucibles along
         * the north wall, a pour channel two cells wide running south from each to its casting bed, both melt (a
         * foundry drop, cut by {@link RuinsMeltStage}), and a crossing over each channel, staggered from channel to channel
         * so no one lane looks down them all. Cover stands at the crossings' ends and the pulpit in the corner. The
         * melt keeps clear of every doorway and of the hall's middle, where its way in arrives, and the hall stays one
         * floor across its crossings.
         */
        void foundry(TileRegistry registry, Area hall, Cell centre) {
            int n = Math.max(1, Math.min(3, (hall.width() + 2) / 11));
            int span = hall.width() - 4 - 3;
            boolean[] melt = new boolean[w * h];
            boolean any = false;
            for (int k = 0; k < n; k++) {
                int cx = hall.left() + 2 + (n == 1 ? span / 2 : k * span / (n - 1));
                // Off the hall's middle column, where the corridor's way in arrives.
                if (Math.abs(cx + 1 - centre.x()) <= 2) cx += cx + 1 <= centre.x() ? -3 : 3;
                int bank = hall.top() - 4, bedTop = hall.bottom() + 6, crossing = hall.bottom() + (k % 2 == 0 ? 9 : 13);
                for (int y = hall.bottom() + 2; y <= bank - 1; y++) {
                    for (int x = cx - 2; x <= cx + 5; x++) {
                        boolean channel = y > bedTop && (x == cx + 1 || x == cx + 2);
                        boolean bed = y <= bedTop;
                        if (!channel && !bed) continue;
                        if (channel && (y == crossing || y == crossing + 1)) continue;
                        if (!hall.contains(x, y) || x <= hall.left() || x >= hall.right()) continue;
                        if (Math.abs(x - centre.x()) <= 1 && Math.abs(y - centre.y()) <= 1) continue;
                        if (!grid.isWalkable(x, y) || nearDoorway(x, y) || aisle[i(x, y)]) continue;
                        melt[i(x, y)] = true;
                        any = true;
                    }
                }
                over(registry, "doodad.ruins-mould-grid", cx - 2, hall.bottom() + 2);
                // Each crossing wears the foundry drop's deck: the hall's floor under a grate catwalk.
                lay(registry, NaturalVoidZoning.FOUNDRY.deck().doodad(), cx + 1, crossing);
                // The bank: a crucible over its channel's head, the last of the bank tipped on some sites.
                boolean tipped = k == n - 1 && hash(cx, bank, 0xF0D) % 3 == 0;
                if (piece(registry, tipped ? "doodad.ruins-crucible-tipped" : "doodad.ruins-crucible", cx, bank, 3, 3,
                        hall, centre)) {
                    over(registry, tipped ? "doodad.ruins-crucible-tipped-spill" : "doodad.ruins-crucible-melt", cx,
                            bank);
                }
                piece(registry, "doodad.ruins-slag-heap", cx - 2, crossing, 2, 2, hall, centre);
                piece(registry, "doodad.ruins-slag-heap", cx + 3, crossing + 2, 2, 2, hall, centre);
                if (k + 1 < n) piece(registry, RACK_STRIPPED, cx + 4, bank + 1, 6, 2, hall, centre);
            }
            if (!any) return;
            // The melt is cut after finalize (RuinsMeltStage); here it is held clear of every later piece, and the
            // hall is proved one floor across its crossings with it in.
            for (int y = hall.bottom(); y <= hall.top(); y++) {
                for (int x = hall.left(); x <= hall.right(); x++) {
                    if (melt[i(x, y)]) grid.setWalkable(x, y, false);
                }
            }
            boolean whole = oneFloor(hall);
            for (int y = hall.bottom(); y <= hall.top(); y++) {
                for (int x = hall.left(); x <= hall.right(); x++) {
                    if (!melt[i(x, y)]) continue;
                    grid.setWalkable(x, y, true);
                    kitFloor[i(x, y)] = true;
                }
            }
            if (!whole) throw new IllegalStateException("a foundry split its hall at " + hall);
            ctx.put(RuinsKeys.MELT, melt);
            piece(registry, "doodad.ruins-fallen-ladle", hall.left() + 2 + span / 4, hall.bottom() + 11, 3, 2, hall,
                    centre);
            piece(registry, "doodad.ruins-pour-pulpit", hall.right() - 3, hall.top() - 3, 2, 2, hall, centre);
        }

        /**
         * A forge works' mega hall (owner pick {@code ruins-forge-process} b1; {@code ruins-themed-environments.md}),
         * laid from its process along its length, head to the north:
         * <ul>
         *   <li><b>Head.</b> The great crucible's open bath, 8 by 8, with two ladles waiting either side. The charge
         *   racks stand east of it, the slag heaped in a pit west of it, the pour pulpit below the racks.</li>
         *   <li><b>Pour line.</b> A two-cell trench down the hall's middle from the bath to the dispatch end, crossed
         *   every twelve rows by a grate catwalk, the drop's deck.</li>
         *   <li><b>Production lines.</b> Every sixteen rows a channel branches each way off the line to a mould grid
         *   over its casting bed, with a cooling rack beyond it.</li>
         *   <li><b>Dispatch.</b> The south end is left clear for the dispatch aisle.</li>
         *   <li><b>The collapse.</b> A ladle tipped mid-pour beside the line, another fallen near the dispatch.</li>
         * </ul>
         * The melt is planned here and cut after finalize as the foundry's is ({@link RuinsMeltStage}), proved to
         * leave the hall one floor.
         */
        void megaForge(TileRegistry registry, Area hall, Cell centre) {
            int cx = hall.centreX(), top = hall.top(), bottom = hall.bottom();
            boolean[] melt = new boolean[w * h];
            // Wear goes under everything the hall stands.
            int under = ctx.doodads.size();
            // The great crucible at the head, west of the line: its spout, flush with its east edge, pours onto the
            // line's head. Without its art (or room for it) the head is an open bath.
            int gx = cx - 1 - GREAT_CRUCIBLE_WIDE, gy = top - 1 - GREAT_CRUCIBLE_LONG;
            boolean great = piece(registry, GREAT_CRUCIBLE, gx, gy, GREAT_CRUCIBLE_WIDE, GREAT_CRUCIBLE_LONG, hall,
                    centre);
            if (great) over(registry, GREAT_CRUCIBLE_MELT, gx, gy);
            else meltBox(melt, hall, centre, cx - 4, top - 9, cx + 3, top - 2);
            int lineHead = great ? gy + GREAT_CRUCIBLE_SPOUT : top - 10;
            // The pour line down the middle, a crossing every twelve rows.
            for (int y = bottom + 6; y <= lineHead; y++) {
                if (Math.floorMod(top - 16 - y, 12) <= 1) continue;
                meltBox(melt, hall, centre, cx - 1, y, cx, y);
            }
            for (int y = top - 17; y >= bottom + 6; y -= 12) {
                lay(registry, NaturalVoidZoning.FOUNDRY.deck().doodad(), cx - 1, y);
            }
            // Production lines: a channel each way to a mould grid over its bed, a cooling rack beyond.
            int line = 0;
            // The first line leaves a two-row aisle under the great crucible, so no pocket of floor is shut in.
            for (int y0 = great ? gy - 5 : top - 20; y0 - 6 > bottom + 6; y0 -= 16, line++) {
                meltBox(melt, hall, centre, cx - 6, y0, cx - 2, y0 + 1);
                meltBox(melt, hall, centre, cx - 14, y0 - 2, cx - 7, y0 + 2);
                over(registry, "doodad.ruins-mould-grid", cx - 14, y0 - 2);
                meltBox(melt, hall, centre, cx + 1, y0, cx + 5, y0 + 1);
                meltBox(melt, hall, centre, cx + 6, y0 - 2, cx + 13, y0 + 2);
                over(registry, "doodad.ruins-mould-grid", cx + 6, y0 - 2);
                // Beyond each mould, its castings cooling on their racks; some racks already cleared.
                for (int k = 0; k < 2; k++) {
                    int wx = cx - 14 + 3 * k, ex = cx + 8 + 3 * k;
                    piece(registry, coolingRack(wx, y0), wx, y0 - 9, 2, 4, hall, centre);
                    piece(registry, coolingRack(ex, y0), ex, y0 - 9, 2, 4, hall, centre);
                }
                // Work stopped mid-pour: one line's ladle tipped beside the trench, the next's ladle lift tipped
                // over its junction where it hung.
                if (line == 1 && piece(registry, "doodad.ruins-crucible-tipped", cx + 2, y0 + 3, 3, 3, hall, centre)) {
                    over(registry, "doodad.ruins-crucible-tipped-spill", cx + 2, y0 + 3);
                }
                if (line == 2) {
                    over(registry, "doodad.ruins-ladle-lift-tipped", cx - 2, y0 - 1);
                    over(registry, "doodad.ruins-ladle-lift-tipped-pour", cx - 2, y0 - 1);
                }
                // The line's wear: coolant spilt by the racks, a floor drain in each aisle, its castings' oil.
                if (hash(cx, y0, 0xC00) % 2 == 0) lay(registry, "doodad.ruins-coolant-stain", cx - 12, y0 - 5, under);
                else lay(registry, "doodad.ruins-coolant-stain", cx + 10, y0 - 5, under);
                lay(registry, "doodad.ruins-floor-drain", cx - 4, y0 - 5, under);
                lay(registry, "doodad.ruins-floor-drain", cx + 3, y0 - 5, under);
                if (hash(cx, y0, 0x011) % 3 == 0) lay(registry, "doodad.ruins-oil-stain", cx + 14, y0 - 11, under);
                // A crane girder fallen across the west aisle under its runway, the hall's best cover.
                if (line == 1) piece(registry, "doodad.ruins-fallen-girder", hall.left() + 1, y0 - 4, 7, 1, hall, centre);
            }
            // The head, east of the line: ladles waiting by the spout, the charge racks feeding the crucible, the
            // pour pulpit watching the spout; the slag pit beyond the line. With no great crucible, a ladle each side
            // of the bath.
            if (great) {
                if (piece(registry, "doodad.ruins-crucible", cx + 3, top - 6, 3, 3, hall, centre)) {
                    over(registry, "doodad.ruins-crucible-melt", cx + 3, top - 6);
                }
                if (piece(registry, "doodad.ruins-crucible", cx + 3, gy + 1, 3, 3, hall, centre)) {
                    over(registry, "doodad.ruins-crucible-melt", cx + 3, gy + 1);
                }
                piece(registry, "doodad.ruins-pour-pulpit", cx + 3, lineHead - 1, 2, 2, hall, centre);
            } else {
                for (int side = -1; side <= 1; side += 2) {
                    int lx = side < 0 ? cx - 8 : cx + 5;
                    if (piece(registry, "doodad.ruins-crucible", lx, top - 6, 3, 3, hall, centre)) {
                        over(registry, "doodad.ruins-crucible-melt", lx, top - 6);
                    }
                }
                piece(registry, "doodad.ruins-pour-pulpit", cx + 10, top - 8, 2, 2, hall, centre);
            }
            piece(registry, RACK_STRIPPED, cx + 9, top - 3, 6, 2, hall, centre);
            // The slag pit, a crashed hover skid with its east edge on the pit's lip, the slag heaped below.
            meltBox(melt, hall, centre, cx + 14, top - 7, cx + 16, top - 5);
            if (piece(registry, "doodad.ruins-slag-skid-crashed", cx + 11, top - 7, 3, 3, hall, centre)) {
                over(registry, "doodad.ruins-slag-skid-crashed-spill", cx + 11, top - 7);
            }
            piece(registry, "doodad.ruins-slag-heap", cx + 15, top - 9, 2, 2, hall, centre);
            piece(registry, "doodad.ruins-slag-heap", cx + 12, top - 10, 2, 2, hall, centre);
            sealMelt(melt, hall);
            piece(registry, "doodad.ruins-fallen-ladle", cx + 6, bottom + 3, 3, 2, hall, centre);
            if (great) {
                // A ladle lift hung under the spout to take the pour; scorch round the spout and the core's flank.
                over(registry, "doodad.ruins-ladle-lift", cx - 2, lineHead - 4);
                over(registry, "doodad.ruins-ladle-lift-melt", cx - 2, lineHead - 4);
                lay(registry, "doodad.ruins-scorch-mark", cx + 1, lineHead - 3, under);
                lay(registry, "doodad.ruins-scorch-arc", gx - 2, gy + 6, under);
                feedRail(registry, hall, centre, gy);
            }
            dispatch(registry, hall, centre, under);
            overheads(registry, hall, centre);
        }

        /**
         * The mega forge's overhead structure, drawn above the people under it and hidden under the hall's roof until it
         * fades ({@code ruins-forge-motions}): a crane runway down each long wall, and a cable tray down the east side of
         * the pour line, broken where a length of it lies fallen on the floor.
         */
        private void overheads(TileRegistry registry, Area hall, Cell centre) {
            for (int y = hall.bottom(); y + 3 <= hall.top(); y += 4) {
                lay(registry, "doodad.ruins-crane-runway", hall.left(), y);
                lay(registry, "doodad.ruins-crane-runway", hall.right(), y);
            }
            int tx = hall.centreX() + 2;
            boolean fallen = false;
            for (int y = hall.bottom() + 6; y + 1 <= hall.top() - 10; y += 2) {
                if (!fallen && y >= hall.centreY()
                        && piece(registry, "doodad.ruins-cable-tray-fallen", tx + 1, y, 1, 4, hall, centre)) {
                    fallen = true;
                    y += 2;
                    continue;
                }
                lay(registry, "doodad.ruins-cable-tray", tx, y);
            }
        }

        /**
         * The mega forge's dispatch end, clear of the line: the pour line's trench runs on under covers, pallet bays
         * are marked along it, castings were fettled and inspected at the benches, and the haulers' skid marks and oil
         * lie across the floor. The slag path leaves the slag heaps for the east wall, generic until its own pieces
         * land.
         */
        private void dispatch(TileRegistry registry, Area hall, Cell centre, int under) {
            int cx = hall.centreX(), bottom = hall.bottom();
            lay(registry, "doodad.ruins-trench-cover", cx - 1, bottom + 4);
            lay(registry, "doodad.ruins-trench-cover", cx - 1, bottom + 2);
            lay(registry, "doodad.ruins-drain-gutter", cx + 1, bottom + 2);
            for (int k = 0; k < 2; k++) {
                lay(registry, "doodad.ruins-pallet-bay", hall.left() + 2 + 4 * k, bottom + 1, under);
                lay(registry, "doodad.ruins-pallet-bay", hall.right() - 4 - 4 * k, bottom + 1, under);
            }
            piece(registry, "doodad.ruins-finishing-cell", cx - 9, bottom + 2, 2, 2, hall, centre);
            piece(registry, "doodad.ruins-metrology-bed", cx - 6, bottom + 2, 2, 2, hall, centre);
            lay(registry, "doodad.ruins-thrust-wash", cx - 4, bottom + 1, under);
            lay(registry, "doodad.ruins-oil-trail", cx + 3, bottom + 1, under);
            lay(registry, "doodad.ruins-oil-stain", cx - 8, bottom + 4, under);
            // The slag lane: a mag-rail down the east aisle from the pit to the dispatch end, a hover skid
            // stopped on it half way.
            int sx = cx + 17;
            if (piece(registry, "doodad.ruins-slag-skid", sx - 1, hall.centreY(), 3, 3, hall, centre)) {
                over(registry, "doodad.ruins-slag-skid-melt", sx - 1, hall.centreY());
            }
            magRail(registry, sx, bottom + 8, hall.top() - 11, false);
        }

        /**
         * The feed to the great crucible: a mag-rail up the west aisle from the dispatch end to its stop at the head,
         * beside the crucible's flank where the charge goes in. One sealed feed pod waits on the rail by the crucible,
         * another lies derailed in the aisle.
         */
        private void feedRail(TileRegistry registry, Area hall, Cell centre, int gy) {
            int fx = hall.left() + 2;
            piece(registry, "doodad.ruins-feed-pod", fx, gy + 9, 2, 3, hall, centre);
            piece(registry, "doodad.ruins-feed-pod-derailed", fx + 2, hall.centreY() + 2, 2, 3, hall, centre);
            magRail(registry, fx, hall.bottom() + 8, hall.top() - 1, true);
        }

        /**
         * A mag-rail two cells wide at {@code x}, run north-south from {@code from} to {@code to}: 2x2 lengths laid
         * where the floor is clear (a piece standing on it, a pod or a skid, breaks the run under it), ending at
         * {@code to} in a decelerator stop when {@code stop}. Its cells are kept clear of dressing and painted as a lane.
         */
        private void magRail(TileRegistry registry, int x, int from, int to, boolean stop) {
            for (int y = from; y + 1 <= to; y += 2) {
                boolean clear = true;
                for (int dy = 0; dy < 2 && clear; dy++) {
                    for (int dx = 0; dx < 2 && clear; dx++) {
                        clear = grid.inBounds(x + dx, y + dy) && grid.isWalkable(x + dx, y + dy)
                                && !topology.isFixture(x + dx, y + dy) && !kitFloor[i(x + dx, y + dy)];
                    }
                }
                if (!clear) continue;
                boolean end = stop && y + 3 > to;
                lay(registry, end ? "doodad.ruins-mag-rail-end" : "doodad.ruins-mag-rail", x, y);
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        feed[i(x + dx, y + dy)] = true;
                        kitFloor[i(x + dx, y + dy)] = true;
                    }
                }
            }
        }

        /** A cooling rack at {@code x} on line {@code y0}: laden with castings, or by coordinate hash cleared. */
        private String coolingRack(int x, int y0) {
            return hash(x, y0, 0xC001) % 3 == 0 ? "doodad.ruins-cooling-rack" : "doodad.ruins-cooling-rack-castings";
        }

        /** Plans melt over a box of a hall's cells, keeping it off the walls, the doorways and the hall's middle. */
        private void meltBox(boolean[] melt, Area hall, Cell centre, int l, int b, int r, int t) {
            for (int y = b; y <= t; y++) {
                for (int x = l; x <= r; x++) {
                    if (!hall.contains(x, y) || x <= hall.left() || x >= hall.right() || y <= hall.bottom()
                            || y >= hall.top()) continue;
                    if (Math.abs(x - centre.x()) <= 1 && Math.abs(y - centre.y()) <= 1) continue;
                    if (!grid.isWalkable(x, y) || nearDoorway(x, y) || aisle[i(x, y)]) continue;
                    melt[i(x, y)] = true;
                }
            }
        }

        /** Proves the planned melt leaves the hall one floor, holds it clear of later pieces and publishes it. */
        private void sealMelt(boolean[] melt, Area hall) {
            for (int y = hall.bottom(); y <= hall.top(); y++) {
                for (int x = hall.left(); x <= hall.right(); x++) {
                    if (melt[i(x, y)]) grid.setWalkable(x, y, false);
                }
            }
            boolean whole = oneFloor(hall);
            for (int y = hall.bottom(); y <= hall.top(); y++) {
                for (int x = hall.left(); x <= hall.right(); x++) {
                    if (!melt[i(x, y)]) continue;
                    grid.setWalkable(x, y, true);
                    kitFloor[i(x, y)] = true;
                }
            }
            if (!whole) throw new IllegalStateException("a forge's melt split its hall at " + hall);
            ctx.put(RuinsKeys.MELT, melt);
        }

        /** Whether a cell is a doorway or beside one. */
        boolean nearDoorway(int x, int y) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (grid.inBounds(x + dx, y + dy) && grid.isDoorway(x + dx, y + dy)) return true;
                }
            }
            return false;
        }

        /** Draws an overlay piece over whatever stands there, blocking nothing: a crucible's melt, a mould grid. */
        void over(TileRegistry registry, String id, int x, int y) {
            DoodadDef def = registry.doodad(id);
            if (def != null) ctx.doodads.add(new Doodad(x, y, def));
        }

        /**
         * What the line left on a module: the theme's unit half-built on one of its two beds, or wrecked where it
         * stood, drawn over the module, which already blocks; most modules stand empty.
         */
        void onLine(TileRegistry registry, int x, int y) {
            RuinsDressing dressing = plan.dressing();
            int roll = hash(x, y, 0xBA1) % 6;
            String id = roll < 2 ? dressing.half() : roll == 2 ? dressing.wrecked() : null;
            DoodadDef def = id == null ? null : registry.doodad(id);
            if (def != null) ctx.doodads.add(new Doodad(x + (roll == 1 ? 3 : 0), y, def));
        }

        /**
         * A wrecked unit in the cradle at {@code (bx, by)}: the theme's own, filling the cradle and blocking its middle
         * cell, else scrap. Laid before the cradle's frame, which goes under it.
         */
        void wreck(TileRegistry registry, int bx, int by) {
            String id = plan.dressing().wrecked();
            DoodadDef def = registry == null || id == null ? null : registry.doodad(id);
            if (def == null) {
                prop(WRECK, bx + 1, by + 1);
                return;
            }
            ctx.doodads.add(new Doodad(bx, by, def));
            grid.setWalkable(bx + 1, by + 1, false);
            grid.setSeeThrough(bx + 1, by + 1, true);
            topology.setFixture(bx + 1, by + 1, true);
            kept[i(bx + 1, by + 1)] = true;
        }

        /** Stands one blocking piece of the plant kit, where the hall stays one floor; whether it stood. */
        boolean piece(TileRegistry registry, String id, int x, int y, int fx, int fy, Area hall, Cell keepClear) {
            DoodadDef def = registry.doodad(id);
            return def != null && dressProp(def, x, y, fx, fy, hall, keepClear);
        }

        /** Lays a piece that is floor to walk on (a dock's leveler, a cradle's open frame): drawn, never blocking. */
        void lay(TileRegistry registry, String id, int x, int y) {
            lay(registry, id, x, y, ctx.doodads.size());
        }

        /** {@link #lay(TileRegistry, String, int, int)}, drawn at {@code under} so what stands on it draws over it. */
        void lay(TileRegistry registry, String id, int x, int y, int under) {
            DoodadDef def = registry.doodad(id);
            if (def != null) ctx.doodads.add(Math.min(under, ctx.doodads.size()), new Doodad(x, y, def));
        }

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
         * Joins {@code from} to interior band {@code band}'s corridor network by a service corridor two cells wide
         * ({@link #connect(Cell, int, Area, int)}): side rooms and chambers, where a hall frame cannot follow.
         */
        void connect(Cell from, int band, Area avoid) {
            connect(from, band, avoid, SERVICE_WIDTH);
        }

        /**
         * Joins {@code from} to interior band {@code band}'s corridor network by the shortest way through the
         * hull, carving a corridor {@code width} cells wide; it never crosses a layer line, leaves the interior, or
         * enters {@code avoid}. Every cell it carves joins the network and is kept clear of later pieces. A way wider
         * than a service corridor ends on a spine or another freight way, never on a service corridor, so a hall
         * frame on it reaches the spine. A way that cannot run {@code width} wide anywhere runs narrower rather than
         * not at all.
         */
        void connect(Cell from, int band, Area avoid, int width) {
            int start = i(from.x(), from.y());
            boolean freight = width > SERVICE_WIDTH;
            if (network[start] == band + 1 && (!freight || freightNet[start])) return;
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
                    if (!grid.inBounds(nx, ny) || prev[i(nx, ny)] != -2 || !corridorStamp(nx, ny, avoid, width)) continue;
                    if (!hull[i(nx, ny)] && !hull[at] && !grid.canTraverseCellStep(cx, cy, nx, ny)) continue;
                    prev[i(nx, ny)] = at;
                    if (network[i(nx, ny)] == band + 1 && (!freight || freightNet[i(nx, ny)])) {
                        goal = i(nx, ny);
                        break;
                    }
                    queue[tail++] = i(nx, ny);
                }
            }
            if (goal < 0) {
                if (width > SERVICE_WIDTH) {
                    connect(from, band, avoid, width - 1);
                    return;
                }
                throw new IllegalStateException("no corridor from " + from + " to " + RuinsDelve.layerName(band));
            }
            int lo = stampLow(width);
            for (int at = goal; at >= 0; at = prev[at]) {
                int cx = at % w, cy = at / w;
                for (int dy = lo; dy < lo + width; dy++) {
                    for (int dx = lo; dx < lo + width; dx++) {
                        if (!corridorCell(cx + dx, cy + dy, avoid)) continue;
                        carve(cx + dx, cy + dy, band);
                        if (freight) freightNet[i(cx + dx, cy + dy)] = true;
                    }
                }
            }
        }

        /**
         * Whether a corridor {@code width} wide may run through a cell: every cell of the square it sweeps there
         * ({@link #stampLow}) is one a corridor may take.
         */
        boolean corridorStamp(int x, int y, Area avoid, int width) {
            if (!corridorCell(x, y, avoid)) return false;
            int lo = stampLow(width);
            for (int dy = lo; dy < lo + width; dy++) {
                for (int dx = lo; dx < lo + width; dx++) {
                    if (!corridorCell(x + dx, y + dy, avoid)) return false;
                }
            }
            return true;
        }

        /**
         * The first offset of a corridor {@code width} wide from its path: the square is centred on the path, so a
         * way {@link #FREIGHT_WIDTH} wide covers a three-cell way through a line centred on the same row, never one
         * row off it. A service corridor, two wide, runs up and right of its path.
         */
        int stampLow(int width) {
            return -(width - 1) / 2;
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

    /** The seed a works hall's plan is drawn from: its footprint, so the same ground plans the same hall. */
    static long worksSeed(Area hall) {
        return hall.left() * 31L ^ (long) hall.bottom() << 20 ^ hall.width() * 7L ^ (long) hall.height() << 40;
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
