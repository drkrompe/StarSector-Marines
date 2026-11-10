package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.PreparedGround;
import com.dillon.starsectormarines.battle.world.gen.elevation.OreLine;
import com.dillon.starsectormarines.battle.world.gen.layout.LayoutGround;
import com.dillon.starsectormarines.battle.world.gen.layout.Props;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Function;
import java.util.function.IntPredicate;

/**
 * The yard of light industry's fabrication works ({@link LightIndustryWorks}), dressed for the work it does once its
 * halls stand: what tells a raider, from the yard, what this place is and which way the work runs.
 *
 * <ul>
 *   <li><b>The parts line.</b> An overhead conveyor links the halls in the order the work passes: receiving dock to
 *   parts stores, stores to each fabrication shop, each shop to the assembly hall, assembly to finishing, finishing
 *   to each dispatch shed. Its trestle legs stand in the yard as cover.</li>
 *   <li><b>The feeders' trucks.</b> The feeding shops' trucks are queued at the receiving dock, backed in to
 *   unload ({@link #MAX_TRUCKS}, alongside where there is no room to back in), with the stock they brought staged
 *   beside them: bar stock, cable reels, drums, pallets.</li>
 *   <li><b>Work in progress.</b> Part bins and pallets at the stores and the shops; totes and pallets at assembly;
 *   paint drums at finishing; crated goods, a loader and a van at each dispatch shed.</li>
 *   <li><b>The shifts.</b> Mess tables and a bin outside the canteen, and trodden paths from its door to each
 *   shop's.</li>
 * </ul>
 *
 * Each hall's kit is set by its doors first, then its stock again along what is left of its faces ({@link #FILL}).
 * Everything stands against the wall of the hall it belongs to, off its doors and off the roads, with a lane at least
 * {@link #LANE} cells wide left open beyond it, so the dressing never closes a way through the works. Laid by
 * {@link LightIndustryWorks#dressWorks} once the halls stand; its draws come from the map's own seed.
 */
public final class FabricationYard {

    /** The dressing's name: the key its draws are seeded from. */
    public static final String NAME = "fabrication-yard";

    /**
     * Trucks queued at the receiving dock: the hub is fed by more shops than its dock takes at once
     * ({@link com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate#feedersFor} is never
     * under three), so its face is full. UNTUNED.
     */
    static final int MAX_TRUCKS = 4;
    /** Cells of open ground left beyond anything set against a wall. UNTUNED. */
    static final int LANE = 3;
    /** Cells kept clear round a doorway's outside cell. UNTUNED. */
    static final int DOOR_CLEAR = 2;
    /** Times a hall's own stock is set again along what is left of its faces, at most. UNTUNED. */
    static final int FILL = 2;
    /** Cells a link of the parts line may run beyond twice its straight distance. UNTUNED. */
    static final int LINK_SLACK = 12;

    /** The parts line's art, by shape; the ore conveyor's stands in until the parts line's own is drawn. */
    static final String LINE_ART = "parts-line.";
    static final String LINE_STAND_IN = "ore-conveyor.";

    /** The ground the yard is dressed on, as the planner reads it. */
    public interface Ground {
        int width();

        int height();

        /** Whether a prop may stand on the cell: walkable, and no doorway, fixture or wall. */
        boolean open(int x, int y);

        boolean road(int x, int y);

        /** Whether the cell is any building's, its walls included, or anything else no one walks through. */
        boolean building(int x, int y);

        /** Whether the cell is the works' own ground. */
        boolean inWorks(int x, int y);
    }

    /**
     * One hall of the works: its step in the process, its inclusive cell bounds walls and all, and the doorsteps: the
     * outside cells in front of its doors.
     */
    public record Hall(RoomPurpose step, int x0, int y0, int x1, int y1, List<int[]> doors) {
        public Hall {
            doors = List.copyOf(doors);
        }

        float cx() { return (x0 + x1) / 2f; }

        float cy() { return (y0 + y1) / 2f; }
    }

    /** One thing set against a hall's wall: a doodad, and whether it stands across the wall (a truck backed in). */
    record Item(String doodad, boolean across) {
        static Item along(String id) { return new Item(id, false); }

        static Item across(String id) { return new Item(id, true); }
    }

    /** The yard as planned: the parts line's pieces and legs, the props, and the trodden paths' cells. */
    public record Dressing(List<Props.Placement> line, List<Props.Placement> legs, List<Props.Placement> props,
                           List<int[]> paths) { }

    private FabricationYard() { }

    /**
     * Dresses the works named {@code worksName} on the built map: plans the yard from its halls and lays it, the
     * shifts' paths as trodden ground, then the parts line, its legs and the props, each through the ordinary prop
     * placement, which skips one whose ground is no longer open.
     */
    public static void dress(GenContext c, String worksName) {
        LayoutGround ground = new LayoutGround(c);
        int[] works = ground.claimBounds(worksName);
        if (works == null) return;
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return;
        String line = registry.doodad(LINE_ART + "straight") != null ? LINE_ART : LINE_STAND_IN;
        Function<String, int[]> footprint = id -> {
            DoodadDef def = registry.doodad(id);
            return def == null ? null : new int[]{def.footprintCellsX, def.footprintCellsY};
        };
        Dressing d = plan(halls(ground, works), adapt(ground, works), ground.seed(NAME), line, footprint);
        for (int[] cell : d.paths()) PreparedGround.workedGround(c, cell[0], cell[1]);
        // The line runs overhead and takes no ground; its legs and the props stand only where the works' shared rule
        // ({@link WorksProps#open}) lets a prop stand: off streets, doorways, ramps and landing pads.
        for (Props.Placement p : d.line()) {
            PreparedGround.prop(c, p.x(), p.y(), registry.doodad(p.doodad()), p.quarterTurns());
        }
        for (List<Props.Placement> props : List.of(d.legs(), d.props())) {
            for (Props.Placement p : props) {
                DoodadDef def = registry.doodad(p.doodad());
                if (def == null) continue;
                Doodad turned = new Doodad(p.x(), p.y(), def, p.quarterTurns());
                if (!WorksProps.open(c, p.x(), p.y(), turned.footprintCellsX, turned.footprintCellsY)) continue;
                PreparedGround.prop(c, p.x(), p.y(), def, p.quarterTurns());
            }
        }
    }

    private static Ground adapt(LayoutGround g, int[] works) {
        CellTopology topology = g.topology();
        return new Ground() {
            @Override public int width() { return g.width(); }

            @Override public int height() { return g.height(); }

            @Override public boolean open(int x, int y) { return g.open(x, y); }

            @Override public boolean road(int x, int y) { return g.isRoad(x, y); }

            @Override public boolean building(int x, int y) {
                return x >= 0 && y >= 0 && x < g.width() && y < g.height()
                        && (topology.getBuildingId(x, y) != 0 || !g.walkable(x, y));
            }

            @Override public boolean inWorks(int x, int y) {
                return x >= works[0] && y >= works[1] && x <= works[2] && y <= works[3];
            }
        };
    }

    /** The works' halls on the grown ground: each building inside its claim whose rooms are a step of the work. */
    static List<Hall> halls(LayoutGround g, int[] works) {
        CellTopology topology = g.topology();
        NavigationGrid grid = g.grid();
        List<Hall> out = new ArrayList<>();
        for (Building b : g.buildings()) {
            float cx = (b.minX + b.maxX) / 2f, cy = (b.minY + b.maxY) / 2f;
            if (cx < works[0] || cx > works[2] || cy < works[1] || cy > works[3]) continue;
            Map<RoomPurpose, Integer> votes = new EnumMap<>(RoomPurpose.class);
            for (int i = 0; i < b.cellsX.length; i++) {
                RoomPurpose p = topology.getRoomPurpose(b.cellsX[i], b.cellsY[i]);
                if (p != null) votes.merge(p, 1, Integer::sum);
            }
            RoomPurpose step = null;
            int best = 0;
            for (Map.Entry<RoomPurpose, Integer> e : votes.entrySet()) {
                if (e.getValue() > best && isStep(e.getKey())) {
                    best = e.getValue();
                    step = e.getKey();
                }
            }
            if (step == null) continue;
            // The hall's walls stand a cell outside its floor. Its doors are in them, or, for a wide vehicle door,
            // on the floor's own edge; the doorstep is the first walkable cell beyond the wall.
            int x0 = b.minX - 1, y0 = b.minY - 1, x1 = b.maxX + 1, y1 = b.maxY + 1;
            List<int[]> doors = new ArrayList<>();
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    boolean wall = x == x0 || x == x1 || y == y0 || y == y1;
                    boolean edge = x == b.minX || x == b.maxX || y == b.minY || y == b.maxY;
                    if (!(wall || edge) || !grid.inBounds(x, y) || !grid.isDoorway(x, y)) continue;
                    int dx = x <= b.minX ? -1 : x >= b.maxX ? 1 : 0, dy = y <= b.minY ? -1 : y >= b.maxY ? 1 : 0;
                    if (dx != 0 && dy != 0) continue;
                    int ox = dx < 0 ? x0 - 1 : dx > 0 ? x1 + 1 : x, oy = dy < 0 ? y0 - 1 : dy > 0 ? y1 + 1 : y;
                    if (!grid.inBounds(ox, oy) || !grid.isWalkable(ox, oy) || grid.isDoorway(ox, oy)) continue;
                    boolean seen = false;
                    for (int[] d : doors) seen |= d[0] == ox && d[1] == oy;
                    if (!seen) doors.add(new int[]{ox, oy});
                }
            }
            out.add(new Hall(step, x0, y0, x1, y1, doors));
        }
        return out;
    }

    static boolean isStep(RoomPurpose p) {
        return LightIndustryWorks.PROCESS.contains(p) || p == LightIndustryWorks.CANTEEN;
    }

    /**
     * Plans the yard: the parts line first, then the shifts' paths, then the props round each hall, each kept off
     * what was planned before it.
     *
     * @param line      the parts line's art prefix, its shapes named after {@link OreLine.Shape}
     * @param footprint a doodad's footprint unturned, {@code {x, y}}, or {@code null} when it is not installed
     */
    public static Dressing plan(List<Hall> halls, Ground g, long seed, String line,
                                Function<String, int[]> footprint) {
        int w = g.width(), h = g.height();
        boolean[] taken = new boolean[w * h];
        Map<RoomPurpose, List<Hall>> byStep = new EnumMap<>(RoomPurpose.class);
        for (Hall hall : halls) byStep.computeIfAbsent(hall.step(), k -> new ArrayList<>()).add(hall);
        List<int[]> doors = new ArrayList<>();
        for (Hall hall : halls) doors.addAll(hall.doors());

        // The parts line, step to step in the order the work passes.
        List<Props.Placement> pieces = new ArrayList<>(), legs = new ArrayList<>();
        IntPredicate passable = i -> {
            int x = i % w, y = i / w;
            return x > 0 && y > 0 && x < w - 1 && y < h - 1 && !taken[i] && g.inWorks(x, y) && !g.building(x, y);
        };
        IntPredicate canStand = i -> {
            int x = i % w, y = i / w;
            return g.open(x, y) && !g.road(x, y) && !nearDoor(doors, x, y, DOOR_CLEAR) && laneAround(g, x, y);
        };
        List<RoomPurpose> steps = new ArrayList<>();
        for (RoomPurpose step : LightIndustryWorks.PROCESS) if (byStep.containsKey(step)) steps.add(step);
        for (int k = 0; k + 1 < steps.size(); k++) {
            for (Hall from : byStep.get(steps.get(k))) {
                for (Hall to : byStep.get(steps.get(k + 1))) {
                    link(from, to, g, taken, passable, canStand, line, pieces, legs);
                }
            }
        }

        // The shifts' paths: from the canteen's door to the doors of the halls they work in.
        List<int[]> paths = new ArrayList<>();
        boolean[] trodden = new boolean[w * h];
        for (Hall canteen : byStep.getOrDefault(LightIndustryWorks.CANTEEN, List.of())) {
            if (canteen.doors().isEmpty()) continue;
            int[] start = canteen.doors().get(0);
            for (Hall hall : halls) {
                if (hall.step() != LightIndustryWorks.SHOP) continue;
                if (hall.doors().isEmpty()) continue;
                int[] end = nearest(hall.doors(), start);
                IntPredicate walk = i -> {
                    int x = i % w, y = i / w;
                    return x > 0 && y > 0 && x < w - 1 && y < h - 1 && g.inWorks(x, y) && !g.building(x, y)
                            && !legAt(legs, x, y);
                };
                List<int[]> course = OreLine.route(w, h, start[0], start[1], walk, i -> i == end[1] * w + end[0]);
                for (int[] c : course) {
                    int i = c[1] * w + c[0];
                    if (trodden[i] || !g.open(c[0], c[1]) || g.road(c[0], c[1])) continue;
                    trodden[i] = true;
                    paths.add(c);
                }
            }
        }
        for (int i = 0; i < trodden.length; i++) if (trodden[i]) taken[i] = true;

        // The props, hall by hall in the order of the work.
        SplittableRandom rng = new SplittableRandom(seed);
        List<Props.Placement> props = new ArrayList<>();
        List<RoomPurpose> order = new ArrayList<>(LightIndustryWorks.PROCESS);
        order.add(LightIndustryWorks.CANTEEN);
        for (RoomPurpose step : order) {
            for (Hall hall : byStep.getOrDefault(step, List.of())) {
                set(hall, kit(step, rng), g, taken, doors, footprint, props);
            }
        }
        return new Dressing(pieces, legs, props, paths);
    }

    /** What stands against a hall's wall, by its step, in the order it is set down. */
    static List<Item> kit(RoomPurpose step, SplittableRandom rng) {
        List<Item> out = new ArrayList<>();
        if (step == LightIndustryWorks.RECEIVING) {
            String[] trucks = {"doodad.parked-cargo-truck", "doodad.parked-flatbed-truck", "doodad.parked-box-van"};
            for (int i = 0; i < MAX_TRUCKS; i++) out.add(Item.across(trucks[rng.nextInt(trucks.length)]));
            out.add(Item.along("machine-shop.bar-stock"));
            out.add(Item.along("doodad.industrial-cable-reel"));
            out.add(Item.along("doodad.industrial-pallet-stack"));
            out.add(Item.along("doodad.industrial-drum-cluster"));
            out.add(Item.along("doodad.industrial-pallet-stack"));
        } else if (step == LightIndustryWorks.STORES) {
            out.add(Item.along("doodad.stores-bin-rack"));
            out.add(Item.along("doodad.stores-bin-rack"));
            out.add(Item.along("loading-bay.pallet-tug"));
            out.add(Item.along("doodad.industrial-pallet-stack"));
        } else if (step == LightIndustryWorks.SHOP) {
            out.add(Item.along("machine-shop.bearings"));
            out.add(Item.along("doodad.stores-bin-rack"));
            out.add(Item.along(rng.nextBoolean() ? "machine-shop.electrical" : "machine-shop.bar-stock"));
            out.add(Item.along("doodad.industrial-scrap-pile"));
        } else if (step == LightIndustryWorks.ASSEMBLY) {
            out.add(Item.along("loading-bay.insulated-tote"));
            out.add(Item.along("doodad.industrial-pallet-stack"));
            out.add(Item.along("doodad.stores-cargo-loader"));
            out.add(Item.along("loading-bay.insulated-tote"));
            out.add(Item.along("doodad.industrial-pallet-stack"));
        } else if (step == LightIndustryWorks.FINISHING) {
            out.add(Item.along("doodad.industrial-drum-cluster"));
            out.add(Item.along("doodad.industrial-drum-cluster"));
            out.add(Item.along("doodad.industrial-fluid-tank"));
            out.add(Item.along("doodad.industrial-pallet-stack"));
        } else if (step == LightIndustryWorks.DISPATCH) {
            out.add(Item.across("doodad.parked-box-van"));
            out.add(Item.along("doodad.industrial-crate-stack"));
            out.add(Item.along("doodad.industrial-crate-stack"));
            out.add(Item.along("doodad.stores-cargo-loader"));
            out.add(Item.along("doodad.industrial-crate-stack"));
        } else if (step == LightIndustryWorks.CANTEEN) {
            out.add(Item.along("doodad.mess-table-long"));
            out.add(Item.along("doodad.mess-table-long"));
            out.add(Item.along("doodad.industrial-dumpster"));
        }
        return out;
    }

    /**
     * Sets a hall's kit against its walls: the faces with a door first (where the work goes in and out), each walked
     * from one end, an item standing at the first spot it fits with a cell's gap to the last; then its stock again,
     * up to {@link #FILL} times over, while there is room.
     */
    private static void set(Hall hall, List<Item> kit, Ground g, boolean[] taken, List<int[]> doors,
                            Function<String, int[]> footprint, List<Props.Placement> out) {
        int w = g.width();
        List<int[]> faces = new ArrayList<>(); // {side, hasDoor}; side 0 south, 1 north, 2 west, 3 east
        for (int side = 0; side < 4; side++) {
            boolean door = false;
            for (int[] d : hall.doors()) door |= onFace(hall, side, d);
            faces.add(new int[]{side, door ? 1 : 0});
        }
        faces.sort((a, b) -> b[1] - a[1]);
        Map<Integer, Integer> cursor = new HashMap<>();
        List<Item> fill = new ArrayList<>();
        for (Item item : kit) {
            // A truck with no room to back in stands alongside instead.
            boolean placed = place(hall, item.doodad(), item.across(), faces, cursor, g, taken, doors, footprint, out)
                    || item.across() && place(hall, item.doodad(), false, faces, cursor, g, taken, doors, footprint, out);
            if (placed && !item.across()) fill.add(item);
        }
        // Then the hall's own stock again along what is left of its faces: a works this size keeps more than one of
        // each by its doors.
        for (int i = 0; i < FILL * fill.size(); i++) {
            Item item = fill.get(i % fill.size());
            if (!place(hall, item.doodad(), false, faces, cursor, g, taken, doors, footprint, out)) break;
        }
    }

    /** Sets one item at the first spot it fits on the hall's faces in order, from each face's cursor on. */
    private static boolean place(Hall hall, String doodad, boolean across, List<int[]> faces,
                                 Map<Integer, Integer> cursor, Ground g, boolean[] taken, List<int[]> doors,
                                 Function<String, int[]> footprint, List<Props.Placement> out) {
        int[] fp = footprint.apply(doodad);
        if (fp == null) return false;
        int w = g.width();
        for (int[] face : faces) {
            int side = face[0];
            boolean horizontal = side < 2;
            // Along the wall the long side lies along it; across, it stands out from it.
            boolean longAlongX = fp[0] >= fp[1];
            int turns = horizontal == (across != longAlongX) ? 0 : 1;
            int fx = turns == 0 ? fp[0] : fp[1], fy = turns == 0 ? fp[1] : fp[0];
            int from = horizontal ? hall.x0() : hall.y0(), to = horizontal ? hall.x1() : hall.y1();
            int extent = horizontal ? fx : fy;
            for (int s = cursor.getOrDefault(side, from); s + extent - 1 <= to; s++) {
                int ax, ay;
                switch (side) {
                    case 0 -> { ax = s; ay = hall.y0() - fy; }
                    case 1 -> { ax = s; ay = hall.y1() + 1; }
                    case 2 -> { ax = hall.x0() - fx; ay = s; }
                    default -> { ax = hall.x1() + 1; ay = s; }
                }
                if (!fits(g, taken, doors, ax, ay, fx, fy, side)) continue;
                for (int y = ay; y < ay + fy; y++) for (int x = ax; x < ax + fx; x++) taken[y * w + x] = true;
                out.add(new Props.Placement(doodad, ax, ay, turns));
                cursor.put(side, s + extent + 1);
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a footprint at {@code (ax, ay)} of {@code fx} by {@code fy} cells fits against the wall on
     * {@code side}: open works ground off roads, other plans and doors, with {@link #LANE} cells of ground no building
     * stands on beyond it.
     */
    static boolean fits(Ground g, boolean[] taken, List<int[]> doors, int ax, int ay, int fx, int fy, int side) {
        int w = g.width(), h = g.height();
        for (int y = ay; y < ay + fy; y++) {
            for (int x = ax; x < ax + fx; x++) {
                if (x < 1 || y < 1 || x >= w - 1 || y >= h - 1) return false;
                if (taken[y * w + x] || !g.open(x, y) || g.road(x, y) || !g.inWorks(x, y)) return false;
                if (nearDoor(doors, x, y, DOOR_CLEAR)) return false;
            }
        }
        int dx = side == 2 ? -1 : side == 3 ? 1 : 0, dy = side == 0 ? -1 : side == 1 ? 1 : 0;
        // The lane beyond: every cell from the footprint's outer edge out LANE cells, along its whole width.
        for (int y = ay; y < ay + fy; y++) {
            for (int x = ax; x < ax + fx; x++) {
                for (int k = 1; k <= LANE; k++) {
                    int ox = x + dx * k, oy = y + dy * k;
                    if (ox >= ax && ox < ax + fx && oy >= ay && oy < ay + fy) continue;
                    if (ox < 0 || oy < 0 || ox >= w || oy >= h || g.building(ox, oy)) return false;
                }
            }
        }
        return true;
    }

    /** Lays one link of the parts line from {@code from}'s wall to {@code to}'s; false when no short course reaches. */
    private static boolean link(Hall from, Hall to, Ground g, boolean[] taken, IntPredicate passable,
                                IntPredicate canStand, String art, List<Props.Placement> pieces,
                                List<Props.Placement> legs) {
        int w = g.width(), h = g.height();
        int[] start = beside(from, to, passable, w, h);
        if (start == null) return false;
        IntPredicate inTo = i -> i % w >= to.x0() && i % w <= to.x1() && i / w >= to.y0() && i / w <= to.y1();
        IntPredicate goal = i -> passable.test(i) && ((i % w > 0 && inTo.test(i - 1))
                || (i % w < w - 1 && inTo.test(i + 1)) || (i >= w && inTo.test(i - w))
                || (i + w < w * h && inTo.test(i + w)));
        List<int[]> path = OreLine.route(w, h, start[0], start[1], passable, goal);
        if (path.isEmpty()) return false;
        int gx = Math.max(to.x0() - start[0], Math.max(0, start[0] - to.x1()));
        int gy = Math.max(to.y0() - start[1], Math.max(0, start[1] - to.y1()));
        if (path.size() > 2 * (gx + gy) + LINK_SLACK) return false;
        int[] last = path.get(path.size() - 1);
        int intoX = last[0], intoY = last[1];
        for (int d = 0; d < 4; d++) {
            int nx = last[0] + (d == 0 ? 1 : d == 2 ? -1 : 0), ny = last[1] + (d == 1 ? 1 : d == 3 ? -1 : 0);
            if (nx >= 0 && ny >= 0 && nx < w && ny < h && inTo.test(ny * w + nx)) {
                intoX = nx;
                intoY = ny;
                break;
            }
        }
        List<OreLine.Piece> course = OreLine.pieces(path, start[2], start[3], intoX, intoY, true);
        for (OreLine.Piece p : course) {
            pieces.add(new Props.Placement(art + p.shape().name().toLowerCase(Locale.ROOT), p.x(), p.y(),
                    p.quarterTurns()));
            taken[p.y() * w + p.x()] = true;
        }
        for (int[] at : OreLine.legs(course, canStand, w)) {
            int turns = 0;
            for (OreLine.Piece p : course) if (p.x() == at[0] && p.y() == at[1]) turns = p.quarterTurns() % 2;
            legs.add(new Props.Placement(art + "leg", at[0], at[1], turns));
        }
        return true;
    }

    /**
     * Where a link from {@code from} to {@code to} starts: the open cell against {@code from}'s wall (not at a
     * corner) nearest {@code to}, with the wall cell behind it, as {@code {x, y, wallX, wallY}}.
     */
    static int[] beside(Hall from, Hall to, IntPredicate passable, int w, int h) {
        int[] best = null;
        float bestD = Float.MAX_VALUE;
        for (int y = from.y0() - 1; y <= from.y1() + 1; y++) {
            for (int x = from.x0() - 1; x <= from.x1() + 1; x++) {
                boolean side = x == from.x0() - 1 || x == from.x1() + 1;
                boolean end = y == from.y0() - 1 || y == from.y1() + 1;
                if (side == end || x < 0 || y < 0 || x >= w || y >= h || !passable.test(y * w + x)) continue;
                float d = Math.abs(x - to.cx()) + Math.abs(y - to.cy());
                if (d >= bestD) continue;
                bestD = d;
                int fx = x < from.x0() ? x + 1 : x > from.x1() ? x - 1 : x;
                int fy = y < from.y0() ? y + 1 : y > from.y1() ? y - 1 : y;
                best = new int[]{x, y, fx, fy};
            }
        }
        return best;
    }

    private static boolean onFace(Hall hall, int side, int[] d) {
        return switch (side) {
            case 0 -> d[1] == hall.y0() - 1;
            case 1 -> d[1] == hall.y1() + 1;
            case 2 -> d[0] == hall.x0() - 1;
            default -> d[0] == hall.x1() + 1;
        };
    }

    static boolean nearDoor(List<int[]> doors, int x, int y, int reach) {
        for (int[] d : doors) if (Math.abs(d[0] - x) <= reach && Math.abs(d[1] - y) <= reach) return true;
        return false;
    }

    /** Whether a leg at the cell leaves its four neighbours walkable ground, so it never closes a gap. */
    private static boolean laneAround(Ground g, int x, int y) {
        int open = 0;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (!g.building(x + d[0], y + d[1])) open++;
        }
        return open == 4;
    }

    private static boolean legAt(List<Props.Placement> legs, int x, int y) {
        for (Props.Placement p : legs) if (p.x() == x && p.y() == y) return true;
        return false;
    }

    private static int[] nearest(List<int[]> cells, int[] to) {
        int[] best = cells.get(0);
        int bestD = Integer.MAX_VALUE;
        for (int[] c : cells) {
            int d = Math.abs(c[0] - to[0]) + Math.abs(c[1] - to[1]);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }
}
