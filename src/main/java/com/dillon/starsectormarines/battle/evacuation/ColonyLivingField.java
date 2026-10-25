package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.ops.RiskLevel;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The colony as a living field ({@code colony-breach-nouns.md}, Living field):
 * the people scattered across it who are not the company's to save and not the
 * cohort's to count, and whose fights and losses go on away from the company.
 *
 * <ul>
 *   <li><b>Homesteads</b>: outlying houses held by a few armed colonists, their
 *       family indoors behind them, under garrison authority like any held
 *       building;</li>
 *   <li><b>Militia knots</b>: small allied teams standing in the open where the
 *       first night left them, each defending its patch of ground;</li>
 *   <li><b>Refugee families</b>: groups who leave their houses on a schedule (a
 *       couple as the company lands, the rest once the ground opens) and walk
 *       out toward the company, across the landing field to the map edge beyond it, running
 *       from any rav they sense and walking on once it is gone. One who reaches
 *       the edge is off the field and got away;</li>
 *   <li><b>Stragglers</b>: lone colonists out in the open, wandering.</li>
 * </ul>
 *
 * <p>Nothing here is scored. Allies are prey and harvest like any militia, the
 * civilians are harvest like any colonist, and what the field records
 * ({@link #snapshot}) is a reading for evidence. Everything is placed and
 * scheduled from the seed, and the refugees' walk is decided in the serial
 * tick, so a replay is the same field.
 */
public final class ColonyLivingField {

    /** Family members indoors per homestead. */
    public static final int HOMESTEAD_FAMILY = 2;
    /** Members of a militia knot. */
    public static final int KNOT_SIZE = 4;
    /** How far a refugee senses a rav it will run from, in cells: the same as a civilian senses gunfire. */
    public static final float REFUGEE_SENSE = 14f;
    /** Cells from the exit edge at which a refugee is off the field. */
    public static final int EXIT_MARGIN = 2;
    /** Command issuer the field's allied squads are claimed under. */
    public static final String ISSUER = "colony-living-field";
    private static final int PATROL_RADIUS = 4;
    private static final int KNOT_RADIUS = 6;
    /** Ticks between departures of the families no bore has sent running, once the ground has opened. */
    private static final int DEPARTURE_GAP_TICKS = 30 * 30;
    /** How long after the tremor the first family no bore has sent running sets out anyway. */
    private static final int LATE_DEPARTURE_TICKS = 60 * 30;
    /** A bore opening this close to a family's house sends it out at once, in cells. */
    public static final int FLIGHT_RADIUS = 40;
    /** A walking family's pace against its running speed: people carrying children and what they could. */
    public static final float WALK_PACE = 0.55f;
    /** How far the field keeps from the map's edge, where the opening seepage comes in, in cells. */
    private static final int EDGE_MARGIN = 16;
    /** Ticks after battle start at which the early families leave. */
    private static final int[] EARLY_DEPARTURES = {10 * 30, 45 * 30};
    /** How often the serial tick re-points refugees at their exit. */
    private static final int WALK_CADENCE_TICKS = 10;
    /** Most whole-map walk paths issued in one tick. */
    private static final int WALK_PATHS_PER_TICK = 3;
    private static final long SEED_SALT = 0x4C4956494E474649L;
    private static final ThreadLocal<LongBucket> SWARM_NEAR = ThreadLocal.withInitial(LongBucket::new);

    /**
     * How much of the field a battle carries. The owner's pick decides the
     * default; {@code -Dbattle.colonyBreach.livingField.density} selects one
     * for a comparison.
     */
    public enum Density {
        SPARSE(2, 2, 4, 4),
        STANDARD(4, 3, 6, 8),
        DENSE(6, 5, 9, 12);

        public final int homesteads, knots, families, stragglers;

        Density(int homesteads, int knots, int families, int stragglers) {
            this.homesteads = homesteads;
            this.knots = knots;
            this.families = families;
            this.stragglers = stragglers;
        }
    }

    /**
     * How a homestead is armed. {@link #FARM} is what farmers keep at home: three
     * guns at the colony's lowest grade. {@link #MILITIA} is a house the militia
     * armed: four at the grade the colony's own militia are issued.
     * {@code -Dbattle.colonyBreach.livingField.arms} selects one.
     */
    public enum Arms {
        FARM(3),
        MILITIA(4);

        public final int guns;

        Arms(int guns) { this.guns = guns; }
    }

    /** How this battle's homesteads are armed; {@link Arms#MILITIA} unless a property names another. */
    public static Arms arms() {
        String named = System.getProperty("battle.colonyBreach.livingField.arms", "militia");
        try {
            return Arms.valueOf(named.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Arms.MILITIA;
        }
    }

    /** What the field holds and has come to: placed and still alive by kind, and refugees out. */
    public record Snapshot(int homesteaders, int homesteadersAlive, int homesteadFamily, int homesteadFamilyAlive,
                           int knotMembers, int knotMembersAlive, int refugees, int refugeesDeparted,
                           int refugeesAlive, int refugeesEscaped, int stragglers, int stragglersAlive) {
        /** Civilians placed by the field, every kind. */
        public int civilians() { return homesteadFamily + refugees + stragglers; }
        /** Civilians of the field still on it or got away. */
        public int civiliansSurviving() {
            return homesteadFamilyAlive + refugeesAlive + refugeesEscaped + stragglersAlive;
        }
    }

    /** A homestead: the house's bounds and the guns holding it. */
    public record Homestead(int left, int top, int right, int bottom, List<Long> guns) {
        public Homestead { guns = List.copyOf(guns); }
    }

    /** A militia knot: the ground it defends and its members. */
    public record Knot(int x, int y, List<Long> members) {
        public Knot { members = List.copyOf(members); }
    }

    /** A family on its walk out: the ground its living walkers are spread over now. */
    public record WalkingGroup(int group, float minX, float minY, float maxX, float maxY, int walkers) { }

    private final List<Homestead> homesteads;
    private final List<Knot> knots;
    private final List<Long> homesteaders;
    private final List<Long> homesteadFamily;
    private final List<Long> knotMembers;
    private final List<Long> stragglers;
    private final List<Refugee> refugees;
    private final List<Integer> squads;
    private final int exitY;
    /** Refugees on their walk out: read by their behaviour on the parallel pool, written only in the serial tick. */
    private final LongOpenHashSet walking = new LongOpenHashSet();
    private int escaped;

    private static final class Refugee {
        final long id;
        final int group;
        final int exitX;
        final int homeX, homeY;
        boolean departed;
        boolean out;

        Refugee(long id, int group, int exitX, int homeX, int homeY) {
            this.id = id;
            this.group = group;
            this.exitX = exitX;
            this.homeX = homeX;
            this.homeY = homeY;
        }
    }

    private ColonyLivingField(List<Homestead> homesteads, List<Knot> knots, List<Long> homesteaders,
                              List<Long> homesteadFamily, List<Long> knotMembers,
                              List<Long> stragglers, List<Refugee> refugees, List<Integer> squads, int exitY) {
        this.homesteads = List.copyOf(homesteads);
        this.knots = List.copyOf(knots);
        this.homesteaders = List.copyOf(homesteaders);
        this.homesteadFamily = List.copyOf(homesteadFamily);
        this.knotMembers = List.copyOf(knotMembers);
        this.stragglers = List.copyOf(stragglers);
        this.refugees = List.copyOf(refugees);
        this.squads = List.copyOf(squads);
        this.exitY = exitY;
    }

    /**
     * Whether the colony is a living field. {@code -Dbattle.colonyBreach.livingField=false}
     * is the control: the colony carries only its redoubts, roadblocks and the
     * colonists sheltering in their houses.
     */
    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty("battle.colonyBreach.livingField", "true"));
    }

    /** The density this battle carries; {@link Density#DENSE} (the owner's pick) unless a property names another. */
    public static Density density() {
        String named = System.getProperty("battle.colonyBreach.livingField.density", "dense");
        try {
            return Density.valueOf(named.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Density.DENSE;
        }
    }

    /**
     * Scatters the field over the colony, after its militia and its sheltering
     * colonists have been placed, so nothing here stands on their ground.
     */
    public static ColonyLivingField install(BattleSimulation sim, MapResult map,
                                            CivilianEvacuationPlacement placement, List<LandingPad> pads,
                                            RiskLevel risk, long seed) {
        Density density = density();
        Arms arms = arms();
        Random rng = new Random(seed ^ SEED_SALT);
        NavigationGrid grid = sim.getGrid();
        int width = grid.getWidth(), height = grid.getHeight();
        int spacing = ColonyEruptionPlan.siteSpacing(width, height) / 2;
        int clearance = ColonyEruptionPlan.shelterClearance(width, height);
        int exitY = exitRow(pads, height);
        int padsX = padsCentreX(pads, width);

        // Fixed things the field keeps its distance from: the shelter, the lift, the pads and every redoubt.
        List<int[]> fixed = new ArrayList<>();
        fixed.add(new int[]{placement.shelterX, placement.shelterY});
        fixed.add(new int[]{placement.liftX, placement.liftY});
        if (pads != null) for (LandingPad pad : pads) fixed.add(new int[]{pad.centerX, pad.centerY});
        for (CivilianRedoubt redoubt : sim.getCivilianRedoubts()) fixed.add(new int[]{redoubt.approachX(), redoubt.approachY()});

        List<PointOfInterest> houses = new ArrayList<>();
        for (PointOfInterest place : map.pointsOfInterest) {
            if (place == null || place.kind == PointOfInterest.Kind.LANDING_SITE) continue;
            if (nearEdge(grid, place.interiorAnchorX, place.interiorAnchorY)) continue;
            if (manhattan(place.interiorAnchorX, place.interiorAnchorY,
                    placement.shelterX, placement.shelterY) < clearance) continue;
            if (sim.isInsideRescueOpeningProtectedZone(place.interiorAnchorX, place.interiorAnchorY)) continue;
            if (insideRedoubt(sim, place)) continue;
            houses.add(place);
        }
        houses.sort(Comparator
                .comparingLong((PointOfInterest p) -> priority(seed, grid.index(p.interiorAnchorX, p.interiorAnchorY)))
                .thenComparingInt(p -> grid.index(p.interiorAnchorX, p.interiorAnchorY)));

        List<Integer> squads = new ArrayList<>();
        List<Long> homesteaders = new ArrayList<>();
        List<Long> family = new ArrayList<>();
        List<int[]> chosen = new ArrayList<>();
        List<Homestead> homesteadList = new ArrayList<>();
        List<Knot> knotList = new ArrayList<>();
        int homesteads = 0;
        for (PointOfInterest house : spread(houses, fixed, chosen, density.homesteads, spacing)) {
            TacticalNode node = heldPlace(map, house);
            List<int[]> cells = openInterior(sim, house);
            if (node == null || cells.size() < arms.guns + HOMESTEAD_FAMILY) continue;
            homesteads++;
            int squadId = sim.mintSquad(Faction.ALLY, UnitType.MILITIA);
            Squad squad = sim.getSquad(squadId);
            squad.assignedNode = node;
            squad.patrolRadius = PATROL_RADIUS;
            sim.assignSquadCommand(ObjectiveAssignment.holdNode(squadId, node),
                    CommandAuthority.GARRISON, ISSUER, "hold a homestead");
            List<Long> guns = seat(sim, squad, cells.subList(0, arms.guns),
                    "Homesteader " + homesteads, arms == Arms.FARM ? RiskLevel.LOW : risk, rng);
            homesteaders.addAll(guns);
            homesteadList.add(new Homestead(house.left, house.top, house.right, house.bottom, guns));
            squads.add(squadId);
            for (int[] cell : cells.subList(arms.guns, arms.guns + HOMESTEAD_FAMILY)) {
                family.add(sim.spawn(new EntitySpec("Homestead " + homesteads + " family " + (family.size() + 1),
                        Faction.CIVILIAN, UnitType.CIVILIAN, cell[0], cell[1]).role(UnitRole.FLEE)));
            }
        }

        // Knots stand on open ground, outdoors, spread across the colony.
        List<long[]> ground = openGround(sim, pads, fixed, clearance, seed);
        List<Long> knotMembers = new ArrayList<>();
        int knots = 0;
        for (long[] at : ground) {
            if (knots >= density.knots) break;
            int x = (int) at[0], y = (int) at[1];
            if (tooNear(chosen, x, y, spacing)) continue;
            List<int[]> cells = openCellsAround(sim, x, y, KNOT_SIZE);
            if (cells.size() < KNOT_SIZE) continue;
            chosen.add(new int[]{x, y});
            knots++;
            int squadId = sim.mintSquad(Faction.ALLY, UnitType.MILITIA);
            Squad squad = sim.getSquad(squadId);
            squad.patrolRadius = KNOT_RADIUS;
            sim.assignSquadCommand(ObjectiveAssignment.defendArea(squadId, x, y),
                    CommandAuthority.GARRISON, ISSUER, "hold a patch of the colony");
            List<Long> members = seat(sim, squad, cells, "Militia knot " + knots, risk, rng);
            knotMembers.addAll(members);
            knotList.add(new Knot(x, y, members));
            squads.add(squadId);
        }

        // Families leave from houses deep in the colony, far from the edge they walk out to.
        List<PointOfInterest> deep = new ArrayList<>(houses);
        deep.removeIf(p -> Math.abs(p.interiorAnchorY - exitY) < height / 3);
        List<Refugee> refugees = new ArrayList<>();
        int group = 0;
        for (PointOfInterest house : spread(deep, fixed, chosen, density.families, spacing)) {
            List<int[]> cells = openInterior(sim, house);
            int size = 3 + rng.nextInt(3);
            if (cells.size() < size) continue;
            // Toward the company: out across the landing field, past the soldiers who came down there.
            int exitX = exitCell(grid, exitY, padsX + rng.nextInt(81) - 40);
            if (exitX < 0) continue;
            for (int[] cell : cells.subList(0, size)) {
                long id = sim.spawn(new EntitySpec("Refugee " + (group + 1) + "-" + (refugees.size() + 1),
                        Faction.CIVILIAN, rng.nextInt(4) == 0 ? UnitType.ENGINEER : UnitType.CIVILIAN,
                        cell[0], cell[1]).role(UnitRole.FLEE));
                refugees.add(new Refugee(id, group, exitX, house.interiorAnchorX, house.interiorAnchorY));
            }
            group++;
        }

        // Stragglers anywhere in the open, fields included.
        List<Long> stragglers = new ArrayList<>();
        for (long[] at : ground) {
            if (stragglers.size() >= density.stragglers) break;
            int x = (int) at[0], y = (int) at[1];
            if (tooNear(chosen, x, y, spacing / 2)) continue;
            chosen.add(new int[]{x, y});
            stragglers.add(sim.spawn(new EntitySpec("Straggler " + (stragglers.size() + 1),
                    Faction.CIVILIAN, UnitType.CIVILIAN, x, y).role(UnitRole.FLEE)));
        }
        return new ColonyLivingField(homesteadList, knotList, homesteaders, family, knotMembers, stragglers, refugees,
                squads, exitY);
    }

    public List<Homestead> homesteads() { return homesteads; }

    public List<Knot> knots() { return knots; }

    /** Families on their walk out now, each with the ground its living walkers are spread over. */
    public List<WalkingGroup> walkingGroups(BattleSimulation sim) {
        List<WalkingGroup> groups = new ArrayList<>();
        int current = -1, n = 0;
        float minX = 0f, minY = 0f, maxX = 0f, maxY = 0f;
        for (Refugee refugee : refugees) {
            if (refugee.group != current) {
                if (n > 0) groups.add(new WalkingGroup(current, minX, minY, maxX, maxY, n));
                current = refugee.group;
                n = 0;
            }
            if (!refugee.departed || refugee.out || !alive(sim, refugee.id)) continue;
            float x = sim.world().x(refugee.id), y = sim.world().y(refugee.id);
            minX = n == 0 ? x : Math.min(minX, x);
            minY = n == 0 ? y : Math.min(minY, y);
            maxX = n == 0 ? x : Math.max(maxX, x);
            maxY = n == 0 ? y : Math.max(maxY, y);
            n++;
        }
        if (n > 0) groups.add(new WalkingGroup(current, minX, minY, maxX, maxY, n));
        return groups;
    }

    /** How many of these bodies are alive and on nobody's blades. */
    public static int aliveCount(BattleSimulation sim, List<Long> ids) { return alive(sim, ids); }

    /** The allied squads the field seated: homesteads and knots. */
    public List<Integer> squads() { return squads; }

    /** Whether this body is a refugee on its walk out; the flee behaviour hands it here. */
    public boolean isWalking(long id) { return walking.contains(id); }

    /**
     * The nearest rav a walking refugee senses, or 0. A refugee walks out past
     * the company and the militia: only the swarm sends it running.
     */
    public static long nearestSwarm(long self, BattleSimulation sim) {
        float x = sim.world().x(self), y = sim.world().y(self);
        LongBucket near = SWARM_NEAR.get();
        sim.getUnitIndex().gatherFaction(x, y, REFUGEE_SENSE + 1f, Faction.DEFENDER, near);
        long best = 0L;
        float bestDistance = REFUGEE_SENSE * REFUGEE_SENSE;
        for (int i = 0; i < near.size; i++) {
            long u = near.ids[i];
            // a skitterer frightens nobody: it runs from people
            if (!sim.identity().type(u).isSwarm() || sim.identity().type(u).isSwarmWorker()
                    || !sim.world().isAlive(u)) continue;
            float dx = sim.world().x(u) - x, dy = sim.world().y(u) - y;
            float d = dx * dx + dy * dy;
            if (d < bestDistance || (d == bestDistance && u < best)) {
                best = u;
                bestDistance = d;
            }
        }
        return best;
    }

    /**
     * Serial: schedules departures, points walking refugees at their exit when
     * nothing is chasing them, and takes off the field anyone at the edge.
     */
    public void tick(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        ColonyBreachSequence breach = sim.getColonyBreach();
        int tremor = breach == null ? -1 : breach.tremorTick();
        int paths = 0;
        for (Refugee refugee : refugees) {
            if (refugee.out) continue;
            if (sim.resolveUnit(refugee.id) == 0L || !sim.world().isAlive(refugee.id)
                    || sim.swarmHarvest().isCarried(refugee.id)) {
                walking.remove(refugee.id);
                continue;
            }
            if (!refugee.departed) {
                if (!departs(sim, refugee, tick, tremor)) continue;
                refugee.departed = true;
                walking.add(refugee.id);
            }
            if (Math.abs(sim.world().cellY(refugee.id) - exitY) <= EXIT_MARGIN) {
                refugee.out = true;
                walking.remove(refugee.id);
                escaped++;
                sim.takeOffTheField(refugee.id);
                continue;
            }
            if ((tick + refugee.group) % WALK_CADENCE_TICKS != 0 || paths >= WALK_PATHS_PER_TICK) continue;
            int[] path = sim.world().path(refugee.id);
            boolean onWalk = Paths.cellCount(path) > sim.world().pathIdx(refugee.id)
                    && Paths.destX(path) == refugee.exitX && Paths.destY(path) == exitY;
            if (onWalk || nearestSwarm(refugee.id, sim) != 0L) continue;
            sim.setPath(refugee.id, sim.findPath(sim.world().cellX(refugee.id), sim.world().cellY(refugee.id),
                    refugee.exitX, exitY));
            paths++;
        }
    }

    /**
     * Whether a family sets out this tick: the first two in the opening
     * minute, as the company lands; any other the moment a bore opens within
     * {@link #FLIGHT_RADIUS} of its house; and one no bore has sent running a
     * minute after the tremor, the rest half a minute apart.
     */
    private static boolean departs(BattleSimulation sim, Refugee refugee, int tick, int tremor) {
        if (refugee.group < EARLY_DEPARTURES.length) return tick >= EARLY_DEPARTURES[refugee.group];
        for (SwarmBore bore : sim.swarmBores().bores()) {
            int dx = bore.x() - refugee.homeX, dy = bore.y() - refugee.homeY;
            if (dx * dx + dy * dy <= FLIGHT_RADIUS * FLIGHT_RADIUS) return true;
        }
        if (tremor < 0) return false;
        return tick >= tremor + LATE_DEPARTURE_TICKS
                + (refugee.group - EARLY_DEPARTURES.length) * DEPARTURE_GAP_TICKS;
    }

    public Snapshot snapshot(BattleSimulation sim) {
        int departed = 0, refugeesAlive = 0;
        for (Refugee refugee : refugees) {
            if (refugee.departed) departed++;
            if (!refugee.out && alive(sim, refugee.id)) refugeesAlive++;
        }
        return new Snapshot(homesteaders.size(), alive(sim, homesteaders), homesteadFamily.size(),
                alive(sim, homesteadFamily), knotMembers.size(), alive(sim, knotMembers), refugees.size(), departed,
                refugeesAlive, escaped, stragglers.size(), alive(sim, stragglers));
    }

    private static int alive(BattleSimulation sim, List<Long> ids) {
        int n = 0;
        for (long id : ids) if (alive(sim, id)) n++;
        return n;
    }

    private static boolean alive(BattleSimulation sim, long id) {
        return sim.resolveUnit(id) != 0L && sim.world().isAlive(id) && !sim.swarmHarvest().isCarried(id);
    }

    private static List<Long> seat(BattleSimulation sim, Squad squad, List<int[]> cells, String name,
                                   RiskLevel risk, Random rng) {
        MarineLoadout[] loadouts = InfantryLoadoutRolls.defenderSquad(cells.size(), UnitType.MILITIA, risk, rng);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            int[] cell = cells.get(i);
            EntitySpec spec = new EntitySpec(name + "-" + (i + 1), Faction.ALLY, UnitType.MILITIA, cell[0], cell[1])
                    .role(UnitRole.GARRISON)
                    .squad(squad.id)
                    .home(cell[0], cell[1]);
            loadouts[i].seedInto(spec);
            long id = sim.spawn(spec);
            if (squad.leaderId == 0L) squad.leaderId = id;
            ids.add(id);
        }
        squad.originalSize = cells.size();
        return ids;
    }

    /** The row refugees walk out to: the map edge the company's pads lie nearest, the fields' side. */
    private static int exitRow(List<LandingPad> pads, int height) {
        if (pads == null || pads.isEmpty()) return 0;
        long sum = 0;
        for (LandingPad pad : pads) sum += pad.centerY;
        return sum / pads.size() < height / 2 ? 0 : height - 1;
    }

    private static int padsCentreX(List<LandingPad> pads, int width) {
        if (pads == null || pads.isEmpty()) return width / 2;
        long sum = 0;
        for (LandingPad pad : pads) sum += pad.centerX;
        return (int) (sum / pads.size());
    }

    /** The walkable cell on the exit row nearest {@code x}, or -1. */
    private static int exitCell(NavigationGrid grid, int exitY, int x) {
        int width = grid.getWidth();
        x = Math.max(0, Math.min(width - 1, x));
        for (int d = 0; d < width; d++) {
            if (x - d >= 0 && grid.isWalkable(x - d, exitY)) return x - d;
            if (x + d < width && grid.isWalkable(x + d, exitY)) return x + d;
        }
        return -1;
    }

    /** Farthest-point picks of up to {@code wanted} places, each at least {@code spacing} from fixed and chosen. */
    private static List<PointOfInterest> spread(List<PointOfInterest> candidates, List<int[]> fixed,
                                                List<int[]> chosen, int wanted, int spacing) {
        List<PointOfInterest> picks = new ArrayList<>();
        List<PointOfInterest> left = new ArrayList<>(candidates);
        while (picks.size() < wanted && !left.isEmpty()) {
            PointOfInterest best = null;
            int bestScore = -1;
            for (PointOfInterest place : left) {
                int nearest = Integer.MAX_VALUE;
                for (int[] other : fixed) nearest = Math.min(nearest,
                        euclid(place.interiorAnchorX, place.interiorAnchorY, other[0], other[1]));
                for (int[] other : chosen) nearest = Math.min(nearest,
                        euclid(place.interiorAnchorX, place.interiorAnchorY, other[0], other[1]));
                if (nearest < spacing) continue;
                if (nearest > bestScore) {
                    best = place;
                    bestScore = nearest;
                }
            }
            if (best == null) break;
            left.remove(best);
            picks.add(best);
            chosen.add(new int[]{best.interiorAnchorX, best.interiorAnchorY});
        }
        return picks;
    }

    /**
     * Open outdoor cells, in seeded order: walkable, out of every building, a
     * clear square round them, away from the shelter, the protected zones and
     * the pads.
     */
    private static List<long[]> openGround(BattleSimulation sim, List<LandingPad> pads, List<int[]> fixed,
                                           int clearance, long seed) {
        NavigationGrid grid = sim.getGrid();
        List<long[]> cells = new ArrayList<>();
        for (int y = EDGE_MARGIN; y < grid.getHeight() - EDGE_MARGIN; y += 3) {
            for (int x = EDGE_MARGIN; x < grid.getWidth() - EDGE_MARGIN; x += 3) {
                if (!openSquare(sim, x, y, 1)) continue;
                if (sim.isInsideRescueOpeningProtectedZone(x, y)) continue;
                if (manhattan(x, y, fixed.get(0)[0], fixed.get(0)[1]) < clearance) continue;
                if (tooNear(fixed, x, y, 12)) continue;
                if (onPad(pads, x, y, 6)) continue;
                cells.add(new long[]{x, y, priority(seed ^ 0x4B4E4F54L, grid.index(x, y))});
            }
        }
        cells.sort(Comparator.comparingLong((long[] c) -> c[2]).thenComparingLong(c -> c[1] * 100000L + c[0]));
        return cells;
    }

    /** Up to {@code n} open cells round a point, nearest first, for a knot to stand on. */
    private static List<int[]> openCellsAround(BattleSimulation sim, int cx, int cy, int n) {
        NavigationGrid grid = sim.getGrid();
        List<int[]> cells = new ArrayList<>();
        for (int r = 0; r <= 2 && cells.size() < n; r++) {
            for (int y = cy - r; y <= cy + r && cells.size() < n; y++) {
                for (int x = cx - r; x <= cx + r && cells.size() < n; x++) {
                    if (Math.max(Math.abs(x - cx), Math.abs(y - cy)) != r) continue;
                    if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) continue;
                    if ((sim.getOccupancyMap()[grid.index(x, y)] & 0xFF) != 0) continue;
                    cells.add(new int[]{x, y});
                }
            }
        }
        return cells;
    }

    /** A building's walkable interior cells nobody stands on, in a fixed order. */
    private static List<int[]> openInterior(BattleSimulation sim, PointOfInterest place) {
        NavigationGrid grid = sim.getGrid();
        int building = sim.getTopology().getBuildingId(place.interiorAnchorX, place.interiorAnchorY);
        List<int[]> cells = new ArrayList<>();
        for (int y = place.top + 1; y < place.bottom; y++) {
            for (int x = place.left + 1; x < place.right; x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y)) continue;
                if (building > 0 && sim.getTopology().getBuildingId(x, y) != building) continue;
                if ((sim.getOccupancyMap()[grid.index(x, y)] & 0xFF) != 0) continue;
                cells.add(new int[]{x, y});
            }
        }
        return cells;
    }

    private static TacticalNode heldPlace(MapResult map, PointOfInterest place) {
        if (map.tacticalMap == null) return null;
        for (TacticalNode node : map.tacticalMap.all()) {
            if (node.anchorX == place.interiorAnchorX && node.anchorY == place.interiorAnchorY) return node;
        }
        return null;
    }

    private static boolean insideRedoubt(BattleSimulation sim, PointOfInterest place) {
        for (CivilianRedoubt redoubt : sim.getCivilianRedoubts()) {
            if (redoubt.contains(place.interiorAnchorX, place.interiorAnchorY, 2)) return true;
        }
        return false;
    }

    private static boolean openSquare(BattleSimulation sim, int cx, int cy, int half) {
        NavigationGrid grid = sim.getGrid();
        for (int y = cy - half; y <= cy + half; y++) {
            for (int x = cx - half; x <= cx + half; x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y)) return false;
                if (sim.getTopology().getBuildingId(x, y) != 0) return false;
                if ((sim.getOccupancyMap()[grid.index(x, y)] & 0xFF) != 0) return false;
            }
        }
        return true;
    }

    private static boolean nearEdge(NavigationGrid grid, int x, int y) {
        return x < EDGE_MARGIN || y < EDGE_MARGIN
                || x >= grid.getWidth() - EDGE_MARGIN || y >= grid.getHeight() - EDGE_MARGIN;
    }

    private static boolean onPad(List<LandingPad> pads, int x, int y, int margin) {
        if (pads == null) return false;
        for (LandingPad pad : pads) {
            if (Math.abs(x - pad.centerX) <= pad.halfWidth + margin
                    && Math.abs(y - pad.centerY) <= pad.halfHeight + margin) return true;
        }
        return false;
    }

    private static boolean tooNear(List<int[]> points, int x, int y, int spacing) {
        for (int[] p : points) if (euclid(x, y, p[0], p[1]) < spacing) return true;
        return false;
    }

    private static int manhattan(int ax, int ay, int bx, int by) {
        return Math.abs(ax - bx) + Math.abs(ay - by);
    }

    private static int euclid(int ax, int ay, int bx, int by) {
        int dx = ax - bx, dy = ay - by;
        return (int) Math.sqrt(dx * dx + dy * dy);
    }

    private static long priority(long seed, int cell) {
        long value = seed ^ (cell * 0x9E3779B97F4A7C15L);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
