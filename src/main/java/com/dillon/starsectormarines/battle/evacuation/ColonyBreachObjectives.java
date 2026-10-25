package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.ambient.BuildingWork;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.objective.optional.Cost;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveBoard;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveContext;
import com.dillon.starsectormarines.battle.objective.optional.ObjectivePressure;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRoll;
import com.dillon.starsectormarines.battle.objective.optional.OptionalObjective;
import com.dillon.starsectormarines.battle.objective.optional.PressuredObjective;
import com.dillon.starsectormarines.battle.objective.optional.Reward;
import com.dillon.starsectormarines.battle.setpiece.HallProcession;
import com.dillon.starsectormarines.battle.setpiece.SquareMuster;
import com.dillon.starsectormarines.battle.setpiece.StreetMouths;
import com.dillon.starsectormarines.battle.setpiece.WorkshopStrip;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.staffed.Loading;
import com.dillon.starsectormarines.battle.staffed.LoadingObjective;
import com.dillon.starsectormarines.battle.staffed.StaffedBuildings;
import com.dillon.starsectormarines.battle.world.gen.LoadingBay;
import com.dillon.starsectormarines.battle.world.gen.MainStreet;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.OpenSquare;
import com.dillon.starsectormarines.battle.world.gen.ServiceLane;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Colony Breach's optional objectives ({@code optional-objectives.md}), one rolled per battle from those the
 * colony's set pieces can host ({@code set-piece-blocks.md}), each bringing its own bore's pressure:
 *
 * <ul>
 *   <li><b>Secure the loading</b> ({@code staffed-buildings.md}): the colony's largest warehouse, worked by its
 *   own people, is loading the colony's stores onto a truck while the colony is being eaten; the company that
 *   holds the yard until the truck is away is paid out of what it carries.</li>
 *   <li><b>Strip the workshop</b>: the factory row's hands carry a workshop's machinery out across the service
 *   lane, and the company holds the lane from both gates.</li>
 *   <li><b>Hold the muster</b>: the colony's people have gathered in the market square, and the company holds
 *   it against what comes in by its mouths until the push has passed.</li>
 *   <li><b>Empty the hall</b>: the people sheltering in the civic hall walk the main street to the plaza a
 *   party at a time, and the company keeps the street open.</li>
 * </ul>
 *
 * <p>Beside the rescue, never instead of it: the cohort and the lift are still the contract, and these succeed
 * or fail only themselves.
 */
public final class ColonyBreachObjectives {

    /**
     * Forces the battle's side objectives ({@code all}, {@code none}, or pool keys) instead of rolling one, of
     * those the colony can host: a standing lever for look suites and evidence that play one objective on demand.
     */
    public static final String FORCE_PROPERTY = "battle.colonyBreach.objectives";
    /** Crates aboard before the truck leaves: a few minutes' carrying for a full crew left alone. */
    public static final int LOADING_CRATES = 16;
    /** Loads stacked before the workshop is stripped. */
    public static final int STRIP_LOADS = 18;
    /** Pool keys. */
    public static final String STRIP = "strip_workshop", MUSTER = "hold_muster", HALL = "empty_hall";
    /** Bore numbers of each objective's pressure, above the loading's. */
    static final int STRIP_BORE = BorePressure.NUMBER + 1, MUSTER_BORE = BorePressure.NUMBER + 2,
            HALL_BORE = BorePressure.NUMBER + 3;
    /** Street steps from a set piece's way in its bore opens at, nearest and farthest. */
    static final int SITE_MIN = 24, SITE_MAX = 40;
    /**
     * The same for a set piece in the open, whose streets give a guard a long field of fire: its bore opens
     * nearer, round a corner rather than down the street.
     */
    static final int OPEN_SITE_MIN = 14, OPEN_SITE_MAX = 26;
    /** Cells of open ground running out from the kerb that make a gap in the shopfronts a way in. */
    static final int MOUTH_DEPTH = 8;
    /** The set pieces' pacing: heavier and quicker than the loading's, whose yard has walls to fight from. */
    static final BorePressure.Pacing STRIP_PACING = new BorePressure.Pacing(5f, 10f, 16f, 30f, .5f);
    static final BorePressure.Pacing MUSTER_PACING = new BorePressure.Pacing(6f, 14f, 22f, 25f, .5f);
    static final BorePressure.Pacing HALL_PACING = BorePressure.Pacing.LOADING;
    private static final long SALT = 0x4C4F4144494E4700L;

    private ColonyBreachObjectives() { }

    /**
     * Rolls the pool of what the colony can host, installs the rolled objective (its runtime staffs, spawns and
     * parks what it needs as the board starts), and installs and starts the board on the battle; null where the
     * colony can host nothing, and then no objective is offered.
     */
    public static ObjectiveBoard install(BattleSimulation sim, MapResult map, long battleSeed) {
        List<OptionalObjective> pool = pool(sim, map, battleSeed);
        String forced = System.getProperty(FORCE_PROPERTY);
        List<OptionalObjective> rolled = forced != null && !forced.isBlank() ? forced(forced, pool)
                : ObjectiveRoll.pick(pool, map.layout, battleSeed ^ SALT, 1, 1);
        if (rolled.isEmpty()) return null;
        ObjectiveBoard board = new ObjectiveBoard(rolled, ObjectiveContext.forBattle(sim, map.layout, battleSeed,
                ColonyBreachSequence.BEAT_PREFIX));
        sim.installOptionalObjectives(board);
        board.start(sim.getSimTickIndex());
        return board;
    }

    /** The entries a forcing spec names, in pool order. */
    static List<OptionalObjective> forced(String spec, List<OptionalObjective> pool) {
        String s = spec.trim().toLowerCase(Locale.ROOT);
        if (s.equals("none")) return List.of();
        Set<String> wanted = new HashSet<>();
        for (String part : s.split(",")) wanted.add(part.trim());
        List<OptionalObjective> out = new ArrayList<>();
        for (OptionalObjective o : pool) if (s.equals("all") || wanted.contains(o.key())) out.add(o);
        return out;
    }

    /** What the colony's set pieces can host, in a fixed order. Reads the map; installs nothing. */
    static List<OptionalObjective> pool(BattleSimulation sim, MapResult map, long battleSeed) {
        List<OptionalObjective> pool = new ArrayList<>();
        if (StaffedBuildings.offers(sim, map, EnumSet.of(BuildingWork.Kind.WAREHOUSE), building -> true)) {
            pool.add(LoadingObjective.entry("Secure the loading",
                    "The colony's stores are going onto a truck at the warehouse: hold the yard until it is away.",
                    1, new Reward(3_500, Map.of("supplies", 20), Map.of()), new Cost("bloom.colony.stores_lost", 1),
                    () -> loading(sim, map, battleSeed),
                    l -> BorePressure.against(sim, Loading.bayOf(l.building().building(), map.loadingBays))));
        }
        if (WorkshopStrip.offered(map, sim.getGrid())) {
            pool.add(PressuredObjective.entry(STRIP, "Strip the workshop",
                    "The factory row's hands are carrying out its machinery: hold the lane from both gates until it is stacked.",
                    1, new Reward(3_000, Map.of("heavy_machinery", 10), Map.of()),
                    new Cost("bloom.colony.machinery_lost", 1),
                    () -> WorkshopStrip.install(sim, map, STRIP_LOADS), s -> stripPressure(sim, s)));
        }
        if (!map.openSquares.isEmpty()) {
            OpenSquare square = map.openSquares.get(0);
            pool.add(PressuredObjective.entry(MUSTER, "Hold the muster",
                    "The colony's people have gathered in the market square: hold it until the swarm's push has passed.",
                    1, new Reward(3_000, Map.of(), Map.of()), new Cost("bloom.colony.muster_lost", 1),
                    () -> SquareMuster.install(sim, square), m -> musterPressure(sim, square)));
        }
        if (!map.mainStreets.isEmpty() && map.mainStreets.get(0).landmarkDoorX() >= 0) {
            MainStreet street = map.mainStreets.get(0);
            pool.add(PressuredObjective.entry(HALL, "Empty the hall",
                    "The people sheltering in the civic hall are walking the main street to the plaza: keep it open.",
                    1, new Reward(3_000, Map.of(), Map.of()), new Cost("bloom.colony.hall_lost", 1),
                    () -> HallProcession.install(sim, map.pointsOfInterest, street), p -> hallPressure(sim, street)));
        }
        return pool;
    }

    /**
     * The colony's largest warehouse staffed and its truck parked; null where none could be staffed or no truck
     * could stand at its door and leave.
     */
    private static Loading loading(BattleSimulation sim, MapResult map, long battleSeed) {
        // A freight yard's warehouse first: its bay is where a truck is meant to stand (set-piece-blocks.md),
        // where a truck standing there can drive off the map. With no such bay, any warehouse but a yard's:
        // a yard whose street a truck cannot follow out is a cul-de-sac for one parked by search too.
        List<LoadingBay> bays = Loading.drivable(sim.getGrid(), sim.getTopology(), map.loadingBays);
        List<StaffedBuildings.Staffed> staffed = StaffedBuildings.install(sim, map, battleSeed ^ SALT,
                EnumSet.of(BuildingWork.Kind.WAREHOUSE), 1,
                bays.isEmpty() ? building -> Loading.bayOf(building, map.loadingBays) == null
                        : building -> Loading.bayOf(building, bays) != null);
        if (staffed.isEmpty()) return null;
        return Loading.install(sim, staffed.get(0), LOADING_CRATES, bays);
    }

    /** Down the lane from both gates to the stack, opened on the street outside the row. */
    static ObjectivePressure stripPressure(BattleSimulation sim, WorkshopStrip strip) {
        NavigationGrid grid = sim.getGrid();
        ServiceLane lane = strip.lane();
        int cx = (int) Math.floor((lane.startX() + lane.endX()) / 2f), cy = (int) Math.floor((lane.startY() + lane.endY()) / 2f);
        int[] stack = strip.stackCell();
        List<List<int[]>> lanes = List.of(
                BorePressure.lane(grid, lane.gateAX(), lane.gateAY(), lane.gateAX() - cx, lane.gateAY() - cy, stack[0], stack[1]),
                BorePressure.lane(grid, lane.gateBX(), lane.gateBY(), lane.gateBX() - cx, lane.gateBY() - cy, stack[0], stack[1]));
        // Walked from out in the street: the row's own ground is a compound's, which the walk does not cross.
        return BorePressure.of(sim, STRIP_BORE, lanes.get(0).get(0), SITE_MIN, SITE_MAX, new int[]{cx, cy}, lanes,
                STRIP_PACING);
    }

    /** In by each of the square's mouths to its centre, opened on the street beyond them. */
    static ObjectivePressure musterPressure(BattleSimulation sim, OpenSquare square) {
        NavigationGrid grid = sim.getGrid();
        List<List<int[]>> lanes = new ArrayList<>();
        for (OpenSquare.Mouth mouth : square.mouths()) {
            int mx = (mouth.fromX() + mouth.toX()) / 2, my = (mouth.fromY() + mouth.toY()) / 2;
            lanes.add(BorePressure.lane(grid, mx, my, mouth.outX(), mouth.outY(), square.centreX(), square.centreY()));
        }
        if (lanes.isEmpty()) return ObjectivePressure.NONE;
        // Walked from out past a mouth: the square stands on its market lot, which the walk does not cross.
        return BorePressure.of(sim, MUSTER_BORE, lanes.get(0).get(0), OPEN_SITE_MIN, OPEN_SITE_MAX,
                new int[]{square.centreX(), square.centreY()}, lanes, MUSTER_PACING);
    }

    /** Across the street from its side ways in, onto the spine where each meets it; down from the plaza end without one. */
    static ObjectivePressure hallPressure(BattleSimulation sim, MainStreet street) {
        NavigationGrid grid = sim.getGrid();
        int[] middle = HallProcession.spineMiddle(street);
        List<List<int[]>> lanes = new ArrayList<>();
        for (StreetMouths.Mouth mouth : StreetMouths.find(grid, sim.getTopology(), street, MOUTH_DEPTH)) {
            lanes.add(BorePressure.lane(grid, mouth.kerbX(), mouth.kerbY(), mouth.outX(), mouth.outY(),
                    mouth.spineX(), mouth.spineY()));
        }
        if (lanes.isEmpty()) {
            int sx = (int) Math.floor(street.startX()), sy = (int) Math.floor(street.startY());
            lanes.add(BorePressure.lane(grid, sx, sy, street.startX() - street.endX(), street.startY() - street.endY(),
                    middle[0], middle[1]));
        }
        return BorePressure.of(sim, HALL_BORE, middle, OPEN_SITE_MIN, OPEN_SITE_MAX, middle, lanes, HALL_PACING);
    }
}
