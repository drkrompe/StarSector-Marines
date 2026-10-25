package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationWalls;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds each redoubt and the logistics base from what its {@link CordonConstruction} says, on a finished Cordon map
 * ({@code cordon-nouns.md}, Field redoubt, Logistics base). The generator lays every redoubt as the allies' compound
 * and the base as a fenced shed; a construction other than those clears that ground and builds its own
 * ({@link CordonBuilders}) before the battle is stood up, so the battle reads it as generated ground.
 *
 * <p>What was built is recorded in the map's layout, one {@link Laid#kind() STRUCTURE} piece per redoubt
 * ({@link #WORKS} plus its number) and one for the base ({@link #BASE_WORKS}): its construction's id as the reason,
 * the bounds of what stands as its two cells, and the cells its guns are seated on as its nests. The garrison reads
 * those ({@link CordonGarrisons}); a compound's guns are its gun towers, found by the compound's own dressing.
 *
 * <p>A base built from a warehouse or a car park keeps the shed's work: its two open-air gantries are machine berths
 * in one vehicle bay, serviced from their flanks and heads, so the base builds one machine at a time as the shed did.
 * Any base also loses the capture beacon its shed's vehicle bay published: a motor pool is an armoury to the
 * conquest's compound layer, and the allies' base is no compound for the swarm to hold.
 */
public final class CordonConstructions {

    /** The layout piece a redoubt's construction is recorded under, its number appended. */
    public static final String WORKS = "cordon-works-";
    /** The layout piece the base's construction is recorded under. */
    public static final String BASE_WORKS = "cordon-base-works";
    /** How far from the base's seed its fence is looked for. */
    static final int BASE_REACH = 40;
    /**
     * Cells of road the busiest column and row through a city redoubt's ground must each hold for it to be built
     * round its crossing: with less there is no crossroads to wall up, and it stays the compound.
     */
    static final int CROSSING_ROAD = 30;

    private CordonConstructions() {}

    /**
     * The map with every redoubt and the base built as the seed picks ({@link CordonConstruction#redoubt},
     * {@link CordonConstruction#base}); with {@code mixed} false every one is the generated work, recorded as such.
     * The map's grid and topology are edited in place.
     */
    public static MapResult build(MapResult map, PrecinctPlan plan, CordonPlan.Density density, long seed,
                                  boolean mixed) {
        List<Precinct> redoubts = CordonPlan.redoubts(plan);
        Precinct base = null;
        for (Precinct p : plan.precincts()) if (p.name().equals(CordonPlan.BASE_PLACE)) base = p;
        int front = CordonFront.frontward(plan);
        int nearest = nearestBase(redoubts, base);
        List<Laid> laid = new ArrayList<>(map.layout.pieces());
        Buildings buildings = map.buildings;
        for (int i = 0; i < redoubts.size(); i++) {
            Precinct p = redoubts.get(i);
            int[] box = CordonGarrisons.perimeterBox(map.topology, p);
            CordonConstruction pick = mixed ? CordonConstruction.redoubt(density, i, nearest, seed) : CordonConstruction.COMPOUND;
            CordonBuilders.Builder builder = box == null ? null : CordonBuilders.of(pick);
            CordonBuilders.Site site = box == null ? null : new CordonBuilders.Site(box[0], box[1], box[2], box[3], front);
            if (builder != null && (pick == CordonConstruction.BLOCK || pick == CordonConstruction.TENEMENT)) {
                CordonWorks probe = new CordonWorks(map.grid, map.topology, new ArrayList<>(), seed, site.cx(), site.cy());
                int[] road = CordonBuilders.crossingRoad(probe, site);
                if (road[0] < CROSSING_ROAD || road[1] < CROSSING_ROAD) builder = null;
            }
            if (builder == null) {
                laid.add(record(WORKS + (i + 1), CordonConstruction.COMPOUND, box, List.of()));
                continue;
            }
            CordonWorks works = new CordonWorks(map.grid, map.topology, map.doodads, seed ^ (i + 1) * 0x2545F4914F6CDD1DL,
                    site.cx(), site.cy());
            CordonBuilders.Frame frame = new CordonBuilders.Frame(site.cx(), site.cy(), front);
            CordonBuilders.Built built = builder.build(works, frame, site);
            works.finish();
            buildings = works.buildings(buildings);
            int[] stands = pick == CordonConstruction.FARM || pick == CordonConstruction.SCHOOL ? box : works.structure();
            laid.add(record(WORKS + (i + 1), pick, stands != null ? stands : box, built.guns()));
        }
        List<Gantry> gantries = map.gantries;
        List<FixtureTask> tasks = map.fixtureTasks;
        TacticalMap tactical = map.tacticalMap;
        if (base != null) {
            int[] fence = baseBox(map.topology, base);
            CordonConstruction pick = mixed ? CordonConstruction.base(density, seed) : CordonConstruction.SHED;
            CordonBuilders.Builder builder = CordonBuilders.of(pick);
            if (builder == null || fence == null) {
                laid.add(record(BASE_WORKS, CordonConstruction.SHED, fence, List.of()));
            } else {
                CordonBuilders.Site site = new CordonBuilders.Site(fence[0], fence[1], fence[2], fence[3], front);
                CordonWorks works = new CordonWorks(map.grid, map.topology, map.doodads, seed ^ BASE_SALT,
                        site.cx(), site.cy());
                Laid dump = map.layout.find(CordonSetPieces.AMMO_DUMP);
                int[] kept = dump != null && dump.isLaid() && dump.cells().size() == 2
                        ? new int[]{dump.cells().get(0)[0] - 1, dump.cells().get(0)[1] - 1,
                        dump.cells().get(1)[0] + 1, dump.cells().get(1)[1] + 1} : null;
                works.protect(kept);
                CordonBuilders.Frame frame = new CordonBuilders.Frame(site.cx(), site.cy(), front);
                CordonBuilders.Built built = builder.build(works, frame, site);
                works.refloor(kept);
                works.finish();
                buildings = works.buildings(buildings);
                // A work point stands on open floor: what the builder put down after the gantries may have taken one.
                for (CordonBuilders.Berth b : built.berths()) {
                    b.service().removeIf(s -> !map.grid.isWalkable(s[0], s[1]) || map.topology.isFixture(s[0], s[1]));
                    b.work().removeIf(t -> !map.grid.isWalkable(t.cellX(), t.cellY())
                            || map.topology.isFixture(t.cellX(), t.cellY()));
                }
                List<Gantry> keptGantries = new ArrayList<>();
                List<FixtureTask> keptTasks = new ArrayList<>();
                rehouse(map.gantries, map.fixtureTasks, fence, built, map.topology, keptGantries, keptTasks);
                gantries = keptGantries;
                tasks = keptTasks;
                laid.add(record(BASE_WORKS, pick, fence, List.of()));
            }
            tactical = withoutCompounds(map.tacticalMap, fence != null ? fence
                    : new int[]{base.seedX() - BASE_REACH, base.seedY() - BASE_REACH,
                    base.seedX() + BASE_REACH, base.seedY() + BASE_REACH});
        }
        MapResult out = new MapResult(map.grid, map.topology, map.marineSpawnX, map.marineSpawnY, map.defenderSpawnX,
                map.defenderSpawnY, map.pointsOfInterest, map.doodads, tactical, buildings, map.defensePosts,
                map.roadGraph, map.landingPads, map.landingAreas, map.biomeMap, gantries, tasks, map.runways,
                map.shelters, map.vehicleCorridor, map.frontDepth, map.lanes)
                .withFieldOfFire(map.fieldOfFire).withLayout(new LaidLayout(laid));
        if (map.initialDamage != null) out = out.withInitialDamage(map.initialDamage);
        return out;
    }

    private static final long BASE_SALT = 0x42415345425549L;

    /** What the map's layout records redoubt {@code number} is built from; the compound where nothing is recorded. */
    public static CordonConstruction redoubt(LaidLayout layout, int number) {
        Laid piece = layout == null ? null : layout.find(WORKS + number);
        CordonConstruction c = piece == null ? null : CordonConstruction.byId(piece.reason());
        return c == null ? CordonConstruction.COMPOUND : c;
    }

    /** What the map's layout records the base is built from; the shed where nothing is recorded. */
    public static CordonConstruction base(LaidLayout layout) {
        Laid piece = layout == null ? null : layout.find(BASE_WORKS);
        CordonConstruction c = piece == null ? null : CordonConstruction.byId(piece.reason());
        return c == null ? CordonConstruction.SHED : c;
    }

    /** The index of the redoubt nearest the base's seed, or -1 with no base. */
    static int nearestBase(List<Precinct> redoubts, Precinct base) {
        if (base == null) return -1;
        int best = -1;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < redoubts.size(); i++) {
            long dx = redoubts.get(i).seedX() - base.seedX(), dy = redoubts.get(i).seedY() - base.seedY();
            if (dx * dx + dy * dy < bestD) {
                bestD = dx * dx + dy * dy;
                best = i;
            }
        }
        return best;
    }

    /** A construction's record: its id, its bounds as two cells, its guns as nests. */
    static Laid record(String name, CordonConstruction construction, int[] box, List<int[]> guns) {
        List<int[]> corners = box == null ? List.of() : List.of(new int[]{box[0], box[1]}, new int[]{box[2], box[3]});
        return new Laid(name, Laid.Kind.STRUCTURE, Laid.Status.LAID, construction.id, false, corners, List.of(), guns);
    }

    /** The bounds of the base's fence, {@code [minX, minY, maxX, maxY]}, or {@code null}. */
    static int[] baseBox(CellTopology topology, Precinct base) {
        int lx = Integer.MAX_VALUE, ly = Integer.MAX_VALUE, hx = Integer.MIN_VALUE, hy = Integer.MIN_VALUE;
        for (int y = base.seedY() - BASE_REACH; y <= base.seedY() + BASE_REACH; y++) {
            for (int x = base.seedX() - BASE_REACH; x <= base.seedX() + BASE_REACH; x++) {
                if (!topology.inBounds(x, y) || !topology.isWall(x, y)
                        || !InstallationWalls.PERIMETER.equals(topology.getSurfaceId(x, y))) continue;
                lx = Math.min(lx, x);
                ly = Math.min(ly, y);
                hx = Math.max(hx, x);
                hy = Math.max(hy, y);
            }
        }
        return lx > hx ? null : new int[]{lx, ly, hx, hy};
    }

    /**
     * The map's berths and work points with the base's replaced: every berth inside the fence and every point
     * inside it or serving such a berth gone, the builder's gantries appended with the points they are serviced
     * from, and their shared vehicle bay painted on the ground so the works find it.
     */
    static void rehouse(List<Gantry> gantries, List<FixtureTask> tasks, int[] fence, CordonBuilders.Built built,
                        CellTopology topology, List<Gantry> outGantries, List<FixtureTask> outTasks) {
        Map<Integer, Integer> moved = new HashMap<>();
        for (int i = 0; i < gantries.size(); i++) {
            Gantry g = gantries.get(i);
            if (inside(fence, g.centerX, g.centerY)) continue;
            moved.put(i, outGantries.size());
            outGantries.add(g);
        }
        for (FixtureTask t : tasks) {
            if (inside(fence, t.cellX(), t.cellY())) continue;
            if (t.berth() == FixtureTask.NO_BERTH) {
                outTasks.add(t);
                continue;
            }
            Integer berth = moved.get(t.berth());
            if (berth != null) outTasks.add(new FixtureTask(t.cellX(), t.cellY(), t.affordance(), t.fixtureX(),
                    t.fixtureY(), berth, t.inService()));
        }
        for (int[] c : built.bayCells()) {
            if (topology.inBounds(c[0], c[1]) && !topology.isWall(c[0], c[1])) {
                topology.setRoomPurpose(c[0], c[1], RoomPurpose.VEHICLE_BAY);
            }
        }
        for (CordonBuilders.Berth b : built.berths()) {
            int index = outGantries.size();
            outGantries.add(b.gantry());
            for (int[] s : b.service()) outTasks.add(FixtureTask.servingBerth(s[0], s[1], index, s[2], s[3]));
            outTasks.addAll(b.work());
        }
    }

    /** The tactical map less every compound-kind node anchored inside {@code box}. */
    static TacticalMap withoutCompounds(TacticalMap map, int[] box) {
        if (map == null) return null;
        List<TacticalNode> kept = new ArrayList<>();
        for (TacticalNode node : map.all()) {
            if (CompoundService.isCompound(node.kind) && inside(box, node.anchorX, node.anchorY)) continue;
            kept.add(node);
        }
        return kept.size() == map.size() ? map : new TacticalMap(kept);
    }

    static boolean inside(int[] box, int x, int y) {
        return x >= box[0] && x <= box[2] && y >= box[1] && y <= box[3];
    }
}
