package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.pool.MapPatch;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The colony's own defenders: the people who survived the first night and are
 * still holding a few buildings when the company lands.
 *
 * <p>They are {@link Faction#ALLY}, under garrison authority and outside the
 * player's command pool, the way the shelter guard always was. The shelter
 * guard is one post; on the colony map a few more buildings are held, each by
 * an ordinary hold-the-place order on that building's held place, so the squad
 * on it mans its windows and doors through the same garrisonable machinery any
 * held building uses. A couple of the colony's posts still have a working gun
 * emplacement outside the door, and a couple of militia technicals are parked
 * where they stopped.
 *
 * <p>Every choice is deterministic in the seed and the map. A post is never
 * inside the shelter's or the lift's protected zones, never on a landing pad,
 * and never so close to another post that two of them are one position.
 */
public final class ColonyMilitia {

    /** Held buildings beyond the shelter on the colony map, when they hold no colonists. */
    public static final int OUTLYING_POSTS = 3;
    /** Members per post: a fireteam, like the shelter guard. */
    public static final int POST_SIZE = RescueShelterGarrison.MEMBER_COUNT;
    /** Posts with a working emplacement outside the door. */
    public static final int EMPLACEMENTS = 2;
    /** Militia technicals parked at the colony's posts. */
    public static final int TECHNICALS = 2;
    /** The emplacement a colony post still has. */
    public static final String EMPLACEMENT_STRUCTURE = "structure.turret-heavy-mg";
    /** Militia teams holding the compound's inside: one in the muster yard, one in the shelter hall. */
    public static final int INTERIOR_TEAMS = 2;
    /**
     * The compound's last-ditch gun: rocket artillery in the muster yard that holds fire until a bore has grown
     * ({@link ColonyBreachSequence#holdBatteryUntilBoresGrow}).
     */
    public static final String BATTERY_STRUCTURE = TurretCatalogRegistry.LOCUST_STRUCTURE_ID;
    /** Flak guns in the compound's yard, against the Harriers. */
    public static final int YARD_FLAK = 2;
    /** The compound's flak: the dual flak a tender builds, which tracks fliers and still fires on the ground. */
    public static final String FLAK_STRUCTURE = TurretCatalogRegistry.DUAL_FLAK_STRUCTURE_ID;
    /** The nearest a yard flak stands to the Locust or to another flak, in cells, so the guns are spread. */
    private static final int FLAK_SPACING = 6;
    /** Command issuer the posts are claimed under. */
    public static final String ISSUER = "colony-militia";
    private static final int PATROL_RADIUS = 4;
    private static final int DOORSTEP_RADIUS = 4;
    private static final int TECHNICAL_RADIUS = 9;
    /**
     * How far placed hardware keeps from the cohort's walk to the lift. A gun
     * or a parked truck on that walk is a wall the cohort has to route round,
     * and a single-lane street with one in it is a closed one.
     */
    private static final int ROUTE_CLEARANCE = 6;
    private static final long SEED_SALT = 0x4D494C4954494131L;

    /**
     * What went in. The shelter guard is always there; the rest only on the colony map. {@code battery} is the
     * compound's held Locust, or {@code 0} without one.
     */
    public record Installed(RescueShelterGarrison shelterGuard, List<Integer> postSquads,
                            List<Long> emplacements, List<Long> technicals, List<Integer> holdouts, long battery) {
        public Installed {
            postSquads = List.copyOf(postSquads);
            holdouts = List.copyOf(holdouts);
            emplacements = List.copyOf(emplacements);
            technicals = List.copyOf(technicals);
        }
    }

    private ColonyMilitia() {}

    /**
     * Seats the shelter guard and, on the colony map, the outlying posts, their
     * emplacements and the technicals. Returns {@code null} when the shelter
     * cannot hold its guard, which re-rolls the map as it always did.
     *
     * @param colony whether this is the Colony Breach colony; a smaller rescue
     *               map seats the shelter guard alone
     * @param bores    the bore sites the breach plans; a chosen holdout keeps {@link ColonyRedoubts#BREACH_CLEARANCE}
     *                 from each
     * @param authored a cell inside each building a pooled colony's patch makes a holdout, in redoubt order; empty
     *                 to choose them
     */
    public static Installed install(BattleSimulation sim, MapResult map,
                                    CivilianEvacuationPlacement placement,
                                    List<LandingPad> pads, RiskLevel risk, long seed,
                                    boolean colony, List<ColonyEruptionPlan.Site> bores,
                                    List<MapPatch.Cell> authored) {
        RescueShelterGarrison shelter = RescueShelterGarrison.install(sim, map, placement, risk, seed);
        if (shelter == null) return null;
        if (!colony || !enabled()) return new Installed(shelter, List.of(), List.of(), List.of(), List.of(), 0L);

        Random rng = new Random(seed ^ SEED_SALT);
        List<Long> emplacements = new ArrayList<>();
        List<Integer> squads = new ArrayList<>();
        // Ground the colony's works stand on (left, top, right, bottom): nothing else is set down there.
        List<int[]> claimed = new ArrayList<>();
        long battery = raiseCompound(sim, map, placement, pads, risk, rng, emplacements, squads, claimed);
        int[] compound = claimed.isEmpty() ? null : claimed.get(0).clone();
        boolean redoubts = ColonyRedoubts.enabled();
        int wanted = redoubts ? ColonyRedoubts.holdoutsFor(seed) : OUTLYING_POSTS;
        int seats = POST_SIZE + (redoubts ? ColonyRedoubts.MAX_COHORT : 0);
        List<Post> posts = redoubts && !authored.isEmpty() ? authoredPosts(sim, map, authored, seats)
                : choosePosts(sim, map, placement, pads, seed, wanted, seats, claimed, bores);
        if (redoubts && posts.size() < ColonyRedoubts.MIN_HOLDOUTS) return null;
        List<Integer> holdouts = new ArrayList<>();
        boolean[] walk = routeMask(sim.getGrid(), placement, 1);
        RedoubtWorks.Keep keep = (x, y) -> !walk[sim.getGrid().index(x, y)]
                && !sim.isInsideRescueOpeningProtectedZone(x, y) && !onPad(pads, x, y, 2)
                && !insideWorks(claimed, x, y, 3);
        List<int[]> centres = new ArrayList<>();
        for (Post post : posts) centres.add(new int[]{post.place.centerX(), post.place.centerY()});
        int heavy = ColonyRedoubts.heavyHoldout(centres, bores);
        for (int i = 0; i < posts.size(); i++) {
            Post post = posts.get(i);
            // A holdout's people knock loopholes through every wall before its garrison takes its cells.
            if (redoubts && HoldoutLoopholes.enabled() && HoldoutLoopholes.cut(sim, post.place) > 0)
                post = new Post(post.place, post.node, interiorCells(sim, post.place), post.room);
            int squadId = seatPost(sim, post, i, risk, rng);
            squads.add(squadId);
            if (!redoubts) continue;
            ColonyRedoubts.Holdout holdout = ColonyRedoubts.fortify(sim, post.place, post.node,
                    post.cells.subList(POST_SIZE, post.cells.size()), squadId, placement, keep, i, risk, rng, seed + i,
                    i == heavy ? ColonyRedoubts.HEAVY_CORNER_GUNS : ColonyRedoubts.LIGHT_CORNER_GUNS);
            if (holdout == null) return null;
            holdouts.add(holdout.number());
            if (holdout.lineSquadId() >= 0) squads.add(holdout.lineSquadId());
            if (holdout.gun() != 0L) emplacements.add(holdout.gun());
            emplacements.addAll(holdout.cornerGuns());
            if (holdout.works() != null) {
                RedoubtWorks.Plan w = holdout.works();
                claimed.add(new int[]{w.left(), w.top(), w.right(), w.bottom()});
            }
        }
        // Across the streets into the defended area, lines thrown up after the holdouts' works and before
        // the loose hardware, which steps round them.
        if (ColonyRoadblocks.enabled()) {
            List<ColonyRoadblocks.Plan> blocks = ColonyRoadblocks.plan(sim, placement, compound, keep, POST_SIZE, seed);
            squads.addAll(ColonyRoadblocks.raise(sim, blocks, emplacements, risk, rng));
            for (ColonyRoadblocks.Plan b : blocks) claimed.add(new int[]{b.left(), b.top(), b.right(), b.bottom()});
        }
        List<int[]> taken = new ArrayList<>();
        boolean[] route = routeMask(sim.getGrid(), placement, ROUTE_CLEARANCE);
        // A redoubt with works carries its own gun at its gate; the rest keep one outside the door.
        int doorGuns = 0;
        for (Post post : posts) {
            if (doorGuns >= EMPLACEMENTS) break;
            if (fortified(claimed, post)) continue;
            StructureDef structure = TurretCatalogRegistry.requireStructure(EMPLACEMENT_STRUCTURE);
            int[] cell = openCell(sim, placement, pads, post.place.anchorCellX, post.place.anchorCellY,
                    DOORSTEP_RADIUS, Math.max(structure.footprintCellsX, structure.footprintCellsY) / 2 + 1, taken, route, claimed);
            if (cell == null) continue;
            taken.add(cell);
            doorGuns++;
            emplacements.add(seatEmplacement(sim, cell, "colony-gun-" + doorGuns));
        }
        // Setup-time grid writes, consumed here as buildMap does for defence posts; the works
        // closed ground off, so the zones a building's frontage is read from are rebuilt too.
        if (sim.getNavigationMesh().pendingGridChanges() > 0L) sim.getNavigationMesh().rebuild();
        if (!claimed.isEmpty()) sim.getZoneGraph().rebuild();

        List<Long> technicals = new ArrayList<>();
        for (Post post : posts) {
            if (technicals.size() >= TECHNICALS) break;
            int[] cell = openCell(sim, placement, pads, post.place.anchorCellX, post.place.anchorCellY,
                    TECHNICAL_RADIUS, 2, taken, route, claimed);
            if (cell == null) continue;
            taken.add(cell);
            float x = cell[0] + 0.5f, y = cell[1] + 0.5f;
            long id = sim.convoy().spawn(VehicleType.MILITIA_TECHNICAL, Faction.ALLY, VehicleMission.deployed(x, y));
            // Initial map pose, before tick zero; later motion goes through the chassis controller.
            sim.convoy().body(id).teleport(x, y, -90f);
            technicals.add(id);
        }
        return new Installed(shelter, squads, emplacements, technicals, holdouts, battery);
    }

    /**
     * Whether the colony's outlying militia are seated. {@code -Dbattle.colonyBreach.militia=false}
     * is the control: the shelter guard alone, as Civilian Rescue had it.
     */
    static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty("battle.colonyBreach.militia", "true"));
    }

    private record Post(PointOfInterest place, TacticalNode node, List<int[]> cells, float room) { }

    /**
     * Held buildings spread across the colony: each pick is the candidate
     * farthest from the shelter, the lift and the posts already chosen, out to
     * a third of the map's short side (past that every candidate is as good
     * and the seed decides), and never closer than the eruption-site spacing.
     * A candidate must seat {@code seats} people indoors and have a walk to
     * the lift, so a holdout's people can leave. Where holdouts raise works,
     * the picks are made first among buildings with room for them
     * ({@link RedoubtWorks#room} at least {@link RedoubtWorks#MIN_ROOM}), and
     * only a colony short of those falls back to the cramped ones.
     */
    private static List<Post> choosePosts(BattleSimulation sim, MapResult map,
                                          CivilianEvacuationPlacement placement,
                                          List<LandingPad> pads, long seed, int wanted, int seats,
                                          List<int[]> claimed, List<ColonyEruptionPlan.Site> bores) {
        NavigationGrid grid = sim.getGrid();
        int spacing = ColonyEruptionPlan.siteSpacing(grid.getWidth(), grid.getHeight());
        int clearance = ColonyEruptionPlan.shelterClearance(grid.getWidth(), grid.getHeight());
        boolean works = ColonyRedoubts.enabled() && ColonyRedoubts.worksEnabled();
        RedoubtWorks.Ground ground = works ? RedoubtWorks.ground(sim) : null;
        List<Post> candidates = new ArrayList<>();
        for (PointOfInterest place : map.pointsOfInterest) {
            if (place == null || place.kind == PointOfInterest.Kind.LANDING_SITE) continue;
            if (place.interiorAnchorX == placement.shelterX && place.interiorAnchorY == placement.shelterY) continue;
            if (Math.abs(place.interiorAnchorX - placement.shelterX)
                    + Math.abs(place.interiorAnchorY - placement.shelterY) < clearance) continue;
            if (sim.isInsideRescueOpeningProtectedZone(place.interiorAnchorX, place.interiorAnchorY)) continue;
            if (onPad(pads, place.interiorAnchorX, place.interiorAnchorY, 4)) continue;
            if (!ColonyRedoubts.clearOfBores(place.interiorAnchorX, place.interiorAnchorY, bores)) continue;
            if (insideWorks(claimed, place.interiorAnchorX, place.interiorAnchorY, 2 + RedoubtWorks.HOLDOUT_MARGIN)) continue;
            TacticalNode node = heldPlace(map, place);
            if (node == null) continue;
            List<int[]> cells = interiorCells(sim, place);
            if (cells.size() < seats) continue;
            float room = works ? RedoubtWorks.room(ground, place.left, place.top, place.right, place.bottom,
                    RedoubtWorks.HOLDOUT_MARGIN) : 1f;
            candidates.add(new Post(place, node, cells, room));
        }
        candidates.sort(Comparator
                .comparingLong((Post p) -> priority(seed, grid.index(p.place.interiorAnchorX, p.place.interiorAnchorY)))
                .thenComparingInt(p -> grid.index(p.place.interiorAnchorX, p.place.interiorAnchorY)));
        int reach = Math.min(grid.getWidth(), grid.getHeight()) / 3;
        List<int[]> fixed = new ArrayList<>();
        fixed.add(new int[]{placement.shelterX, placement.shelterY});
        fixed.add(new int[]{placement.liftX, placement.liftY});
        List<Post> chosen = new ArrayList<>();
        pick(grid, placement, candidates, fixed, chosen, wanted, spacing, reach, RedoubtWorks.MIN_ROOM);
        pick(grid, placement, candidates, fixed, chosen, wanted, spacing, reach, 0f);
        return chosen;
    }

    /**
     * The holdouts a person put down ({@code story-map-pools.md}, The patch): for each cell, in order, the held
     * building it falls inside. None of {@link #choosePosts}'s clearances apply, since the author's placement
     * stands; each building must still be a held place with room indoors for {@code seats}.
     *
     * @throws IllegalStateException where a cell names no such building
     */
    private static List<Post> authoredPosts(BattleSimulation sim, MapResult map, List<MapPatch.Cell> holdouts,
                                            int seats) {
        boolean works = ColonyRedoubts.worksEnabled();
        RedoubtWorks.Ground ground = works ? RedoubtWorks.ground(sim) : null;
        List<Post> posts = new ArrayList<>();
        for (MapPatch.Cell cell : holdouts) {
            PointOfInterest place = null;
            for (PointOfInterest p : map.pointsOfInterest) {
                if (p != null && p.kind != PointOfInterest.Kind.LANDING_SITE && p.left <= cell.x()
                        && cell.x() <= p.right && p.top <= cell.y() && cell.y() <= p.bottom) {
                    place = p;
                    break;
                }
            }
            TacticalNode node = place == null ? null : heldPlace(map, place);
            List<int[]> cells = node == null ? List.of() : interiorCells(sim, place);
            if (cells.size() < seats) {
                throw new IllegalStateException("the holdout authored at " + cell.x() + "," + cell.y()
                        + " names no held building with room for " + seats);
            }
            float room = works ? RedoubtWorks.room(ground, place.left, place.top, place.right, place.bottom,
                    RedoubtWorks.HOLDOUT_MARGIN) : 1f;
            posts.add(new Post(place, node, cells, room));
        }
        return posts;
    }

    /** Adds picks to {@code chosen} from the candidates with at least {@code floor} room, up to {@code wanted}. */
    private static void pick(NavigationGrid grid, CivilianEvacuationPlacement placement, List<Post> candidates,
                             List<int[]> fixed, List<Post> chosen, int wanted, int spacing, int reach, float floor) {
        while (chosen.size() < wanted) {
            Post best = null;
            int bestScore = -1;
            for (Post post : candidates) {
                if (chosen.contains(post) || post.room < floor) continue;
                int nearest = Integer.MAX_VALUE;
                for (int[] other : fixed) nearest = Math.min(nearest, distance(post.place, other[0], other[1]));
                for (Post other : chosen) {
                    nearest = Math.min(nearest, distance(post.place,
                            other.place.interiorAnchorX, other.place.interiorAnchorY));
                }
                if (nearest < spacing) continue;
                int score = Math.min(nearest, reach);
                if (score > bestScore) {
                    best = post;
                    bestScore = score;
                }
            }
            if (best == null) break;
            if (Paths.isEmpty(GridPathfinder.findPath(grid, best.place.anchorCellX, best.place.anchorCellY,
                    placement.liftX, placement.liftY))) {
                candidates.remove(best);
                continue;
            }
            chosen.add(best);
        }
    }

    private static int distance(PointOfInterest place, int x, int y) {
        int dx = place.interiorAnchorX - x, dy = place.interiorAnchorY - y;
        return (int) Math.sqrt(dx * dx + dy * dy);
    }

    private static TacticalNode heldPlace(MapResult map, PointOfInterest place) {
        if (map.tacticalMap == null) return null;
        for (TacticalNode node : map.tacticalMap.all()) {
            if (node.anchorX == place.interiorAnchorX && node.anchorY == place.interiorAnchorY) return node;
        }
        return null;
    }

    private static int seatPost(BattleSimulation sim, Post post, int index, RiskLevel risk, Random rng) {
        int squadId = sim.mintSquad(Faction.ALLY, UnitType.MILITIA);
        Squad squad = sim.getSquad(squadId);
        squad.assignedNode = post.node;
        squad.patrolRadius = PATROL_RADIUS;
        sim.assignSquadCommand(ObjectiveAssignment.holdNode(squadId, post.node),
                CommandAuthority.GARRISON, ISSUER, "hold a colony building");
        seatMembers(sim, squad, post.cells, "Colony Militia " + (index + 1), risk, rng);
        return squadId;
    }

    /**
     * A militia fireteam on the given cells, one each and at most {@link #POST_SIZE},
     * with no order yet: the caller says what it holds. Fewer cells seat fewer: a
     * carried team set down is as many as rode, and a squad that lost men before it
     * boarded once crashed the battle asking for a fourth cell.
     */
    public static int seatTeam(BattleSimulation sim, List<int[]> cells, String name, RiskLevel risk, Random rng) {
        int squadId = sim.mintSquad(Faction.ALLY, UnitType.MILITIA);
        seatMembers(sim, sim.getSquad(squadId), cells, name, risk, rng);
        return squadId;
    }

    private static void seatMembers(BattleSimulation sim, Squad squad, List<int[]> cells, String name,
                                    RiskLevel risk, Random rng) {
        MarineLoadout[] loadouts = InfantryLoadoutRolls.defenderSquad(POST_SIZE, UnitType.MILITIA, risk, rng);
        int size = Math.min(POST_SIZE, cells.size());
        for (int i = 0; i < size; i++) {
            int[] cell = cells.get(i);
            EntitySpec spec = new EntitySpec(name + "-" + (i + 1),
                    Faction.ALLY, UnitType.MILITIA, cell[0], cell[1])
                    .role(UnitRole.GARRISON)
                    .squad(squad.id)
                    .home(cell[0], cell[1]);
            loadouts[i].seedInto(spec);
            long id = sim.spawn(spec);
            if (squad.leaderId == 0L) squad.leaderId = id;
        }
        squad.originalSize = size;
    }

    /** A colony gun on one cell: the post's heavy machine gun, sealed into its cell. */
    public static long seatEmplacement(BattleSimulation sim, int[] cell, String name) {
        return seatEmplacement(sim, cell, name, EMPLACEMENT_STRUCTURE);
    }

    /** As {@link #seatEmplacement(BattleSimulation, int[], String)} with another structure on the post. */
    public static long seatEmplacement(BattleSimulation sim, int[] cell, String name, String structureId) {
        int x = cell[0], y = cell[1];
        long turret = sim.spawn(MapTurret.create(name, Faction.ALLY, structureId, x, y));
        NavigationGrid grid = sim.getGrid();
        grid.setWalkable(x, y, false);
        grid.setSeeThrough(x, y, true);
        grid.recomputeCoverAt(x + 1, y);
        grid.recomputeCoverAt(x - 1, y);
        grid.recomputeCoverAt(x, y + 1);
        grid.recomputeCoverAt(x, y - 1);
        return turret;
    }

    /** Walkable interior cells of a building, best cover first, as the shelter guard is seated. */
    private static List<int[]> interiorCells(BattleSimulation sim, PointOfInterest place) {
        NavigationGrid grid = sim.getGrid();
        int building = sim.getTopology().getBuildingId(place.interiorAnchorX, place.interiorAnchorY);
        List<int[]> cells = new ArrayList<>();
        // The wall line too: a window or loophole cell is floor a defender stands in to shoot out.
        for (int y = place.top; y <= place.bottom; y++) {
            for (int x = place.left; x <= place.right; x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y)) continue;
                if (building > 0 && sim.getTopology().getBuildingId(x, y) != building) continue;
                int cover = grid.getCoverAt(x, y) + sim.getDoodadCoverAt(x, y);
                int distance = Math.abs(x - place.interiorAnchorX) + Math.abs(y - place.interiorAnchorY);
                cells.add(new int[]{x, y, cover, distance, grid.index(x, y)});
            }
        }
        cells.sort(Comparator
                .comparingInt((int[] c) -> c[2]).reversed()
                .thenComparingInt(c -> c[3])
                .thenComparingInt(c -> c[4]));
        return cells;
    }

    /**
     * The nearest outdoor cell to a door whose whole square of {@code half}
     * cells around it is open ground — so nothing set down on it closes a lane
     * — away from protected zones, pads and what is already placed.
     */
    private static int[] openCell(BattleSimulation sim, CivilianEvacuationPlacement placement,
                                  List<LandingPad> pads, int doorX, int doorY, int radius, int half,
                                  List<int[]> taken, boolean[] route, List<int[]> claimed) {
        NavigationGrid grid = sim.getGrid();
        int[] best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = doorY - radius; y <= doorY + radius; y++) {
            for (int x = doorX - radius; x <= doorX + radius; x++) {
                int distance = Math.abs(x - doorX) + Math.abs(y - doorY);
                if (distance >= bestDistance) continue;
                if (!grid.inBounds(x, y) || route[grid.index(x, y)]) continue;
                if (insideWorks(claimed, x, y, half + 1)) continue;
                if (!openSquare(sim, x, y, half)) continue;
                if (sim.isInsideRescueOpeningProtectedZone(x, y)) continue;
                if (Math.abs(x - placement.shelterApproachX) + Math.abs(y - placement.shelterApproachY) < 6) continue;
                if (onPad(pads, x, y, half + 2)) continue;
                boolean near = false;
                for (int[] other : taken) {
                    if (Math.max(Math.abs(other[0] - x), Math.abs(other[1] - y)) <= half + 3) {
                        near = true;
                        break;
                    }
                }
                if (near) continue;
                best = new int[]{x, y};
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Whether a cell lies on or within {@code margin} of ground the colony's works claim. */
    private static boolean insideWorks(List<int[]> claimed, int x, int y, int margin) {
        for (int[] r : claimed) {
            if (x >= r[0] - margin && x <= r[2] + margin && y >= r[1] - margin && y <= r[3] + margin) return true;
        }
        return false;
    }

    private static boolean fortified(List<int[]> claimed, Post post) {
        return insideWorks(claimed, post.place.interiorAnchorX, post.place.interiorAnchorY, 0);
    }

    /**
     * Mans the civil-defence compound round the shelter, the main redoubt: the
     * generated wall dressed with its towers, gate guns, corner guns, slits and
     * floodlights; a wall team at the slits that falls back into the shelter
     * once the wall is breached; a team in the muster yard and one in the
     * shelter hall; and the held Locust in the yard, whose id is returned
     * ({@code 0} without one). A shelter in no compound stands bare.
     */
    private static long raiseCompound(BattleSimulation sim, MapResult map, CivilianEvacuationPlacement placement,
                                      List<LandingPad> pads, RiskLevel risk, Random rng, List<Long> emplacements,
                                      List<Integer> squads, List<int[]> claimed) {
        if (!CivilDefenceCompound.enabled()) return 0L;
        PointOfInterest shelter = RescueShelterGarrison.selectedShelter(map.pointsOfInterest, placement);
        if (shelter == null) return 0L;
        CivilDefenceCompound.Plan compound = CivilDefenceCompound.plan(sim, shelter, placement);
        if (compound == null) return 0L;
        CivilDefenceCompound.lay(sim, compound);
        for (int[] gun : compound.guns()) {
            emplacements.add(seatEmplacement(sim, gun, "compound-gun-" + (emplacements.size() + 1)));
        }
        for (int[] gun : compound.corners()) {
            emplacements.add(seatEmplacement(sim, gun, "compound-corner-gun-" + (emplacements.size() + 1)));
        }
        claimed.add(new int[]{compound.left(), compound.top(), compound.right(), compound.bottom()});
        RedoubtWorks.Ground ground = RedoubtWorks.ground(sim);
        if (CivilDefenceCompound.raiseFence(sim, compound,
                (x, y) -> ground.open(x, y) && !sim.isInsideRescueOpeningProtectedZone(x, y)) > 0) {
            int f = CivilDefenceCompound.FENCE_OFFSET;
            claimed.add(new int[]{compound.left() - f, compound.top() - f, compound.right() + f, compound.bottom() + f});
        }
        sim.setCivilianRedoubtGround(1, compound.left(), compound.top(), compound.right(), compound.bottom());
        int wall = ColonyRedoubts.seatLine(sim, compound.posts(), compound.gateX(), compound.gateY(),
                compound.left(), compound.top(), compound.right(), compound.bottom(),
                "Compound Wall", "man the compound wall", risk, rng);
        TacticalNode node = heldPlace(map, shelter);
        if (wall >= 0) {
            squads.add(wall);
            if (node != null) sim.setCivilianHoldoutLine(1, wall, node);
        }
        return holdInside(sim, compound, placement, node, risk, rng, squads, emplacements);
    }

    /**
     * The compound's inside: the Locust on the open yard cell nearest the yard's middle with room round it, held
     * silent; {@link #YARD_FLAK} flak on the next such cells {@link #FLAK_SPACING} from it and each other; then a
     * team on the yard round the Locust holding the compound's ground and a team holding the shelter hall, on the
     * yard cells nearest the gun. Returns the Locust, or {@code 0} when the yard has no room for it.
     */
    private static long holdInside(BattleSimulation sim, CivilDefenceCompound.Plan compound,
                                   CivilianEvacuationPlacement placement, TacticalNode hall, RiskLevel risk,
                                   Random rng, List<Integer> squads, List<Long> emplacements) {
        List<int[]> yard = yardCells(sim, compound, placement);
        if (yard.isEmpty()) return 0L;
        long sx = 0, sy = 0;
        Set<Long> ground = new HashSet<>();
        for (int[] c : yard) {
            sx += c[0];
            sy += c[1];
            ground.add(cellKey(c[0], c[1]));
        }
        int mx = (int) (sx / yard.size()), my = (int) (sy / yard.size());
        yard.sort(Comparator.comparingInt((int[] c) -> Math.abs(c[0] - mx) + Math.abs(c[1] - my))
                .thenComparingInt(c -> sim.getGrid().index(c[0], c[1])));
        List<int[]> sites = new ArrayList<>();
        for (int[] c : yard) {
            if (sites.size() > YARD_FLAK) break;
            boolean room = true;
            for (int dy = -1; dy <= 1 && room; dy++)
                for (int dx = -1; dx <= 1 && room; dx++) room = ground.contains(cellKey(c[0] + dx, c[1] + dy));
            for (int[] s : sites) room &= Math.max(Math.abs(c[0] - s[0]), Math.abs(c[1] - s[1])) >= FLAK_SPACING;
            if (room) sites.add(c);
        }
        if (sites.isEmpty()) return 0L;
        int[] gun = sites.get(0);
        long battery = seatEmplacement(sim, gun, "compound-locust", BATTERY_STRUCTURE);
        sim.turretState().cutPower(battery);
        for (int i = 1; i < sites.size(); i++) {
            emplacements.add(seatEmplacement(sim, sites.get(i), "compound-flak-" + i, FLAK_STRUCTURE));
        }
        int gx = gun[0], gy = gun[1];
        List<int[]> cells = new ArrayList<>();
        for (int[] c : yard) {
            boolean clear = true;
            for (int[] s : sites) clear &= Math.max(Math.abs(c[0] - s[0]), Math.abs(c[1] - s[1])) > 1;
            if (clear) cells.add(c);
        }
        cells.sort(Comparator.comparingInt((int[] c) -> Math.abs(c[0] - gx) + Math.abs(c[1] - gy))
                .thenComparingInt(c -> sim.getGrid().index(c[0], c[1])));
        int centreX = (compound.left() + compound.right()) / 2, centreY = (compound.top() + compound.bottom()) / 2;
        int radius = Math.max(compound.right() - compound.left(), compound.bottom() - compound.top()) / 2 + 2;
        for (int team = 0; team < INTERIOR_TEAMS && cells.size() >= POST_SIZE * (team + 1); team++) {
            boolean holdsHall = team == 1 && hall != null;
            int squadId = seatTeam(sim, cells.subList(POST_SIZE * team, POST_SIZE * (team + 1)),
                    holdsHall ? "Compound Hall" : "Compound Yard", risk, rng);
            if (holdsHall) {
                Squad squad = sim.getSquad(squadId);
                squad.assignedNode = hall;
                squad.patrolRadius = PATROL_RADIUS;
                sim.assignSquadCommand(ObjectiveAssignment.holdNode(squadId, hall),
                        CommandAuthority.GARRISON, ISSUER, "hold the shelter hall");
            } else {
                sim.assignSquadCommand(ObjectiveAssignment.defendArea(squadId, centreX, centreY, radius),
                        CommandAuthority.GARRISON, ISSUER, "hold the compound yard");
            }
            squads.add(squadId);
        }
        return battery;
    }

    /**
     * The compound's open yard: what the shelter's doorstep reaches without leaving the compound's ground, outside
     * every building and clear of the dressing and the wall team's posts.
     */
    private static List<int[]> yardCells(BattleSimulation sim, CivilDefenceCompound.Plan compound,
                                         CivilianEvacuationPlacement placement) {
        NavigationGrid grid = sim.getGrid();
        Set<Long> posts = new HashSet<>();
        for (RedoubtWorks.Bag post : compound.posts()) posts.add(cellKey(post.x(), post.y()));
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        List<int[]> yard = new ArrayList<>();
        queue.add(new int[]{placement.shelterApproachX, placement.shelterApproachY});
        seen.add(cellKey(placement.shelterApproachX, placement.shelterApproachY));
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            long k = cellKey(c[0], c[1]);
            if (sim.getTopology().getBuildingId(c[0], c[1]) == 0 && !grid.isDoorway(c[0], c[1])
                    && !compound.cells().containsKey(k) && !posts.contains(k)) yard.add(c);
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                if (nx <= compound.left() || nx >= compound.right() || ny <= compound.top() || ny >= compound.bottom())
                    continue;
                if (!grid.isWalkable(nx, ny) || sim.getTopology().getBuildingId(nx, ny) != 0
                        || !seen.add(cellKey(nx, ny))) continue;
                queue.add(new int[]{nx, ny});
            }
        }
        return yard;
    }

    private static long cellKey(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    /** Cells within {@code clearance} of the shelter-to-lift path. */
    private static boolean[] routeMask(NavigationGrid grid, CivilianEvacuationPlacement placement, int clearance) {
        boolean[] mask = new boolean[grid.getWidth() * grid.getHeight()];
        int[] path = GridPathfinder.findPath(grid, placement.shelterApproachX, placement.shelterApproachY,
                placement.liftX, placement.liftY);
        if (Paths.isEmpty(path)) return mask;
        for (int i = 0, n = Paths.cellCount(path); i < n; i++) {
            int px = Paths.cellX(path, i), py = Paths.cellY(path, i);
            for (int y = py - clearance; y <= py + clearance; y++) {
                for (int x = px - clearance; x <= px + clearance; x++) {
                    if (grid.inBounds(x, y)) mask[grid.index(x, y)] = true;
                }
            }
        }
        return mask;
    }

    private static boolean openSquare(BattleSimulation sim, int cx, int cy, int half) {
        NavigationGrid grid = sim.getGrid();
        for (int y = cy - half; y <= cy + half; y++) {
            for (int x = cx - half; x <= cx + half; x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y)) return false;
                if (sim.getTopology().getBuildingId(x, y) != 0) return false;
            }
        }
        return true;
    }

    private static boolean onPad(List<LandingPad> pads, int x, int y, int margin) {
        if (pads == null) return false;
        for (LandingPad pad : pads) {
            if (Math.abs(x - pad.centerX) <= pad.halfWidth + margin
                    && Math.abs(y - pad.centerY) <= pad.halfHeight + margin) return true;
        }
        return false;
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
