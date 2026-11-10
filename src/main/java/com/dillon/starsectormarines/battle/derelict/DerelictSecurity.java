package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.drone.Drone;
import com.dillon.starsectormarines.battle.drone.DroneLook;
import com.dillon.starsectormarines.battle.drone.Flock;
import com.dillon.starsectormarines.battle.drone.FlockField;
import com.dillon.starsectormarines.battle.drone.FlockLivery;
import com.dillon.starsectormarines.battle.drone.FlockSystem;
import com.dillon.starsectormarines.battle.drone.FlockWeights;
import com.dillon.starsectormarines.battle.mech.MechSpawnPlacement;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.salvage.SalvageCache;
import com.dillon.starsectormarines.battle.salvage.SalvageResource;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Stands a derelict site's security up in a ruins battle (`derelict-security-network.md`): its zones laid along the
 * ground between the vault and the way out, its walkers posted in them by the grade's strength
 * (`derelict-lore-guide.md`, Strength by grade), and its director installed. The ruins occupant seeding
 * (`ruins-expedition.md`, Occupants) calls {@link #install} for a site whose occupant is derelict security. Each zone puts one flock
 * up from its open-sky home (`air-nouns.md`, Flocks, which stands its cradle there) and stands its node
 * ({@link DerelictNode}), its repair drone ({@link RepairDrone}) and its folded posts ({@link DerelictPost}).
 */
public final class DerelictSecurity {

    /** One zone of the plan: its node cell, its perimeter and its walkers' posts. */
    public record ZonePlan(int id, int nodeX, int nodeY, int perimeterCells, List<int[]> posts) {
        public ZonePlan {
            posts = List.copyOf(posts);
        }
    }

    /** Swarm drones in each zone's flock (`derelict-lore-guide.md`: twelve to sixteen). UNTUNED. */
    public static final int FLOCK_SIZE = 14;
    /** Distinct posts each zone offers its walkers along the route. */
    static final int POSTS_PER_ZONE = 5;

    private DerelictSecurity() {
    }

    /** Security zones by grade: one per flock the grade fields. */
    public static int zones(RuinsGrade grade) {
        return switch (grade) {
            case SCATTERED -> 1;
            case WIDESPREAD -> 2;
            case EXTENSIVE -> 3;
            case VAST -> 4;
        };
    }

    /** The grade's heavies, the vault frame first where there is one. Starting points (UNTUNED). */
    public static List<DerelictWalkerFrame> heavies(RuinsGrade grade) {
        return switch (grade) {
            case SCATTERED -> List.of(DerelictWalkerFrame.LINE);
            case WIDESPREAD -> List.of(DerelictWalkerFrame.LINE, DerelictWalkerFrame.LINE);
            case EXTENSIVE -> List.of(DerelictWalkerFrame.LINE, DerelictWalkerFrame.LINE, DerelictWalkerFrame.SIEGE);
            case VAST -> List.of(DerelictWalkerFrame.VAULT, DerelictWalkerFrame.LINE, DerelictWalkerFrame.LINE,
                    DerelictWalkerFrame.LINE, DerelictWalkerFrame.SIEGE);
        };
    }

    /**
     * Lays {@code count} zones along a route from the vault (its first cell) to the way out (its last): the first zone
     * keeps the vault, the rest step out toward the landing and stop short of it, each perimeter reaching the next so
     * the route is kept end to end. Each zone posts walkers at its node and a few cells either way along the route.
     *
     * @param route cell pairs, vault first
     */
    public static List<ZonePlan> zonesAlong(int[] route, int count) {
        int cells = route.length / 2;
        if (cells < 2 || count < 1) throw new IllegalArgumentException("A plan needs a route and a zone");
        // The last stretch before the landing is left to the company: zones cover the first 80% of the route.
        float span = (cells - 1) * 0.8f;
        float step = count == 1 ? 0f : span / (count - 1);
        int spacing = Math.max(1, Math.round(step));
        List<ZonePlan> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int index = Math.round(i * step);
            int perimeter = Math.max(8, spacing * 3 / 4 + 2);
            List<int[]> posts = new ArrayList<>();
            List<Integer> used = new ArrayList<>();
            for (int offset : new int[] {0, -4, 4, -8, 8, 12, 16}) {
                int at = Math.max(0, Math.min(cells - 1, index + offset));
                if (used.contains(at) || posts.size() == POSTS_PER_ZONE) continue;
                used.add(at);
                posts.add(new int[] {route[at * 2], route[at * 2 + 1]});
            }
            out.add(new ZonePlan(i, route[index * 2], route[index * 2 + 1], perimeter, posts));
        }
        return out;
    }

    /** Route cells the heavies hold in the contingency: the way out, short of the landing. */
    public static List<int[]> egressAlong(int[] route) {
        int cells = route.length / 2;
        List<int[]> out = new ArrayList<>();
        for (float share : new float[] {0.6f, 0.72f, 0.84f}) {
            int at = Math.min(cells - 1, Math.round((cells - 1) * share));
            out.add(new int[] {route[at * 2], route[at * 2 + 1]});
        }
        return out;
    }

    /**
     * Which zone each heavy stands in: the vault frame at the vault, a siege frame in the middle zone, line frames
     * from the vault outward in turn.
     */
    public static int[] zoneOf(List<DerelictWalkerFrame> heavies, int zones) {
        int[] out = new int[heavies.size()];
        int next = 0;
        for (int i = 0; i < heavies.size(); i++) {
            DerelictWalkerFrame frame = heavies.get(i);
            if (frame == DerelictWalkerFrame.VAULT) out[i] = 0;
            else if (frame == DerelictWalkerFrame.SIEGE) out[i] = zones / 2;
            else out[i] = next++ % zones;
        }
        return out;
    }

    /**
     * Spawns the grade's walkers on a ruins battle's ground and installs the director over them, linking each zone
     * to the next along the route. Returns the director, or null when the battle has no vault to lay zones from.
     */
    public static DerelictSecurityDirector install(BattleSimulation sim, RuinsGrade grade) {
        return install(sim, grade, null);
    }

    /**
     * As {@link #install(BattleSimulation, RuinsGrade)}, on ruins ground whose places are known: where the site has an
     * interior, its walkers are posted by {@link #interiorPosts} rather than along the route, each in the zone that
     * keeps its post.
     */
    public static DerelictSecurityDirector install(BattleSimulation sim, RuinsGrade grade, RuinsDelve delve) {
        return install(sim, grade, delve, FlockLivery.DOMAIN);
    }

    /** As {@link #install(BattleSimulation, RuinsGrade, RuinsDelve)}, its flocks in a lineage's livery. */
    public static DerelictSecurityDirector install(BattleSimulation sim, RuinsGrade grade, RuinsDelve delve,
                                                   FlockLivery livery) {
        RuinsVaultObjective vault = null;
        for (Objective objective : sim.getObjectives()) {
            if (objective instanceof RuinsVaultObjective ruins) vault = ruins;
        }
        if (vault == null || vault.liftPoints().isEmpty()) return null;
        int[] route = vault.liftPoints().get(0).route();
        List<ZonePlan> plans = zonesAlong(route, zones(grade));
        SecurityNetwork network = new SecurityNetwork();
        for (ZonePlan plan : plans) network.add(new SecurityZone(plan.id(), plan.nodeX(), plan.nodeY(),
                plan.perimeterCells()));
        for (int i = 1; i < plans.size(); i++) network.link(i - 1, i);

        List<DerelictWalkerFrame> heavies = heavies(grade);
        int[] zoneOf = zoneOf(heavies, plans.size());
        int[] postsUsed = new int[plans.size()];
        int[][] inside = interiorPosts(heavies, delve);
        List<DerelictSecurityDirector.Walker> walkers = new ArrayList<>();
        for (int i = 0; i < heavies.size(); i++) {
            ZonePlan plan = plans.get(zoneOf[i]);
            int[] post;
            if (inside != null) {
                post = inside[i];
                plan = plans.get(nearestZone(plans, post[0], post[1]));
            } else {
                post = plan.posts().get(postsUsed[zoneOf[i]]++ % plan.posts().size());
            }
            int squad = spawn(sim, heavies.get(i), post[0], post[1]);
            if (squad < 0) continue;
            sim.claimSquadCommand(squad, CommandAuthority.MISSION_COMMAND, DerelictSecurityDirector.ISSUER,
                    "derelict security walker at its post");
            walkers.add(new DerelictSecurityDirector.Walker(squad, heavies.get(i), plan.id(), post[0], post[1]));
        }
        // The line's medium and large (`derelict-watch.md`): a rounds walker a zone, on its round; hall frames in the
        // plant's largest halls, bound to them where their way out is too narrow.
        Map<Integer, PatrolRound> rounds = new TreeMap<>();
        Set<Integer> hallBound = new TreeSet<>();
        int roundsWalkers = Math.min(plans.size(), DerelictWalkerClass.roundsWalkers(grade, plans.size()));
        for (int i = 0; i < roundsWalkers; i++) {
            ZonePlan plan = plans.get(i);
            // Only places the walker can reach from its node: a sealed store is no part of a round.
            boolean[] reach = reachable(sim.getGrid(), DerelictWalkerFrame.ROUNDS.chassis.radius,
                    new int[] {plan.nodeX(), plan.nodeY()});
            List<int[]> reachablePlaces = new ArrayList<>();
            for (int[] place : roundPlaces(plan, delve)) {
                if (reach != null && reach[place[1] * sim.getGrid().getWidth() + place[0]]) reachablePlaces.add(place);
            }
            List<int[]> places = PatrolRound.order(network.zone(plan.id()), reachablePlaces);
            int[] post = places.get(0);
            int squad = spawn(sim, DerelictWalkerFrame.ROUNDS, post[0], post[1]);
            if (squad < 0) continue;
            sim.claimSquadCommand(squad, CommandAuthority.MISSION_COMMAND, DerelictSecurityDirector.ISSUER,
                    "derelict rounds walker on its round");
            walkers.add(new DerelictSecurityDirector.Walker(squad, DerelictWalkerFrame.ROUNDS, plan.id(), post[0],
                    post[1]));
            rounds.put(squad, new PatrolRound(places));
        }
        List<int[]> egress = egressAlong(route);
        for (int[] post : hallPosts(delve, DerelictWalkerClass.hallFrames(grade))) {
            int squad = spawn(sim, DerelictWalkerFrame.HALL, post[0], post[1]);
            if (squad < 0) continue;
            sim.claimSquadCommand(squad, CommandAuthority.MISSION_COMMAND, DerelictSecurityDirector.ISSUER,
                    "derelict hall frame in its hall");
            ZonePlan plan = plans.get(nearestZone(plans, post[0], post[1]));
            walkers.add(new DerelictSecurityDirector.Walker(squad, DerelictWalkerFrame.HALL, plan.id(), post[0],
                    post[1]));
            if (!reaches(sim.getGrid(), DerelictWalkerFrame.HALL.chassis.radius, post, egress)) hallBound.add(squad);
        }
        DerelictSecurityDirector director = new DerelictSecurityDirector(network, walkers, egress,
                vault.sourceCellX(), vault.sourceCellY());
        for (Map.Entry<Integer, PatrolRound> round : rounds.entrySet()) {
            director.walkRound(round.getKey(), round.getValue());
        }
        for (int squad : hallBound) director.keepHall(squad);
        director.wearLook(DerelictLook.of(livery));
        director.armWalkers(sim);
        FlockSystemPort port = new FlockSystemPort();
        port.through(sim.flocks());
        for (ZonePlan plan : plans) {
            int[] home = openSkyHome(route, plan, (x, y) -> sim.getTopology() != null
                    && sim.getTopology().isRoofIntact(x, y));
            Flock flock = sim.flocks().launch(sim, Faction.DEFENDER, home[0] + 0.5f, home[1] + 0.5f,
                    FLOCK_SIZE, FlockWeights.WASP, plan.perimeterCells() + FlockTasking.LEASH_CELLS, livery);
            port.add(flock, plan.id());
        }
        standHive(sim, grade, delve, plans, port, livery);
        director.connectFlocks(port);
        for (ZonePlan plan : plans) {
            int[] cell = nodeCell(route, plan, (x, y) -> sim.getGrid().inBounds(x, y) && sim.getGrid().isWalkable(x, y));
            long node = sim.spawn(DerelictNode.create("derelict-node-" + plan.id(), Faction.DEFENDER, cell[0], cell[1]));
            director.standNode(plan.id(), node);
        }
        standPosts(sim, route, plans, network, delve, director);
        standWatchHousings(sim, route, plans, delve, port, livery, director);
        for (ZonePlan plan : plans) standRepairDrone(sim, plan.id(), director);
        if (delve != null && !delve.layers().isEmpty()) {
            for (ZonePlan plan : plans) {
                director.feedThrough(plan.id(), DerelictPower.junctionOf(plan.nodeX(), plan.nodeY(), delve.layers()));
            }
        }
        sim.installDerelictSecurity(director);
        return director;
    }

    /**
     * Where each heavy stands on a site with an interior (`derelict-security-network.md`, Interiors): the vault frame
     * on the vault's approach, inside its forecourt; the siege frame just inside the first entrance; line frames in
     * the interior's chambers in turn (the entrances when it has none). Null when the ground has no interior, and the
     * route's posts serve.
     */
    public static int[][] interiorPosts(List<DerelictWalkerFrame> heavies, RuinsDelve delve) {
        if (delve == null || delve.interior() == null || delve.vault() == null) return null;
        RuinsDelve.Interior interior = delve.interior();
        List<RuinsDelve.Area> rooms = interior.chambers().isEmpty() ? interior.entrances() : interior.chambers();
        int[][] out = new int[heavies.size()][];
        int nextRoom = 0;
        for (int i = 0; i < heavies.size(); i++) {
            DerelictWalkerFrame frame = heavies.get(i);
            if (frame == DerelictWalkerFrame.VAULT || rooms.isEmpty()) {
                out[i] = new int[] {delve.vault().approach().x(), delve.vault().approach().y()};
            } else if (frame == DerelictWalkerFrame.SIEGE && !interior.entrances().isEmpty()) {
                RuinsDelve.Area entrance = interior.entrances().get(0);
                out[i] = new int[] {entrance.centreX(), entrance.centreY()};
            } else {
                RuinsDelve.Area room = rooms.get(nextRoom++ % rooms.size());
                out[i] = new int[] {room.centreX(), room.centreY()};
            }
        }
        return out;
    }

    /**
     * Stands the layer guardian, the hive cradle (`ruins-guardians.md`), in its court with its flock shut in the
     * court's light well, answering to the zone whose node is nearest: that zone's node and branch quiet it as they
     * quiet the zone. Null at a site without one ({@link HiveCradle#site}) or whose court has no open sky.
     */
    static Flock standHive(BattleSimulation sim, RuinsGrade grade, RuinsDelve delve, List<ZonePlan> plans,
                           FlockSystemPort port, FlockLivery livery) {
        if (delve == null) return null;
        RuinsDelve.Court court = HiveCradle.site(delve, grade);
        if (court == null) return null;
        RuinsDelve.Area ground = court.ground();
        FlockField.Open open = FlockSystem.open(sim.getGrid(), sim.getTopology());
        int[] cell = nearestIn(ground, ground.centreX(), ground.centreY(), open::open);
        if (cell == null) return null;
        float leash = Math.max(ground.right() - ground.left(), ground.top() - ground.bottom());
        Flock flock = sim.flocks().launchInWell(sim, Faction.DEFENDER, cell[0] + 0.5f, cell[1] + 0.5f, FLOCK_SIZE,
                FlockWeights.WASP, leash, livery, new HiveCradle.Hatch(),
                (x, y) -> HiveCradle.create("derelict-hive", Faction.DEFENDER, x, y));
        if (flock == null) return null;
        port.add(flock, plans.get(nearestZone(plans, cell[0], cell[1])).id());
        // The guardian keeps the court's hardware: a tech cache held from the court, out of reach while it stands.
        sim.addGuardianCache(guardianCache(court.ground(), cell));
        return flock;
    }

    /** The id of the layer guardian's tech cache, stable so the site remembers it once taken. */
    static final String GUARDIAN_CACHE_ID = "guardian-hive";

    /** The guardian room's tech cache: beside the hive's cell, held from its court. */
    static SalvageCache guardianCache(RuinsDelve.Area court, int[] hive) {
        return new SalvageCache(GUARDIAN_CACHE_ID, hive[0], hive[1], SalvageResource.MACHINERY,
                SalvageResource.MACHINERY.cacheQuantity)
                .heldFromRoom(court.left(), court.bottom(), court.right(), court.top(), 1)
                .asTechCache();
    }

    /** The cell of {@code area} nearest {@code (x0, y0)} that {@code ok} accepts, or null. */
    static int[] nearestIn(RuinsDelve.Area area, int x0, int y0, Walkable ok) {
        int[] best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = area.bottom(); y <= area.top(); y++) {
            for (int x = area.left(); x <= area.right(); x++) {
                int d = (x - x0) * (x - x0) + (y - y0) * (y - y0);
                if (d < bestDistance && ok.at(x, y)) {
                    best = new int[] {x, y};
                    bestDistance = d;
                }
            }
        }
        return best;
    }

    /**
     * The places a zone's round may pass (`derelict-watch.md`, Movement): its route posts and, on ruins ground whose
     * places are known, the interior's chambers, the layers' courts and side rooms. The rounds walker is narrow enough
     * for a side room's door. {@link PatrolRound#order} keeps those inside the perimeter.
     */
    static List<int[]> roundPlaces(ZonePlan plan, RuinsDelve delve) {
        List<int[]> out = new ArrayList<>(plan.posts());
        if (delve == null) return out;
        if (delve.interior() != null) {
            for (RuinsDelve.Area chamber : delve.interior().chambers()) {
                out.add(new int[] {chamber.centreX(), chamber.centreY()});
            }
        }
        for (RuinsDelve.Layer layer : delve.layers()) {
            if (layer.court() != null) {
                out.add(new int[] {layer.court().ground().centreX(), layer.court().ground().centreY()});
            }
            for (RuinsDelve.SideRoom room : layer.rooms()) out.add(new int[] {room.interior().x(), room.interior().y()});
        }
        return out;
    }

    /**
     * Where the grade's hall frames stand (`derelict-watch.md`, Medium and large walkers): the middles of the plant's
     * largest halls, largest first, else the interior's largest chambers; none on ground without an interior.
     */
    static List<int[]> hallPosts(RuinsDelve delve, int count) {
        if (count <= 0 || delve == null || delve.interior() == null) return List.of();
        List<RuinsDelve.Area> halls = new ArrayList<>();
        for (RuinsDelve.PlantHall hall : delve.interior().halls()) halls.add(hall.footprint());
        if (halls.isEmpty()) halls.addAll(delve.interior().chambers());
        halls.sort(Comparator.comparingInt((RuinsDelve.Area a) -> -a.width() * a.height())
                .thenComparingInt(RuinsDelve.Area::left).thenComparingInt(RuinsDelve.Area::bottom));
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < Math.min(count, halls.size()); i++) {
            out.add(new int[] {halls.get(i).centreX(), halls.get(i).centreY()});
        }
        return out;
    }

    /**
     * Whether a body of {@code radius} standing at {@code from} reaches any of {@code targets} (within two cells) over
     * the ground it can stand on: a flood over the cells it fits at the middle or a corner of (a body as wide as a
     * corridor stands on the seam between its rows).
     */
    static boolean reaches(NavigationGrid grid, float radius, int[] from, List<int[]> targets) {
        if (targets.isEmpty()) return false;
        boolean[] reach = reachable(grid, radius, from);
        if (reach == null) return false;
        int w = grid.getWidth();
        for (int[] target : targets) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    int x = target[0] + dx;
                    int y = target[1] + dy;
                    if (grid.inBounds(x, y) && reach[y * w + x]) return true;
                }
            }
        }
        return false;
    }

    /**
     * The cells a body of {@code radius} standing at {@code from} can reach over the ground it fits, row-major; null
     * when it cannot stand near {@code from}.
     */
    static boolean[] reachable(NavigationGrid grid, float radius, int[] from) {
        int w = grid.getWidth();
        int h = grid.getHeight();
        boolean[] reach = new boolean[w * h];
        boolean[] seen = new boolean[w * h];
        ArrayDeque<int[]> open = new ArrayDeque<>();
        int[] start = nearestStandable(grid, radius, from[0], from[1]);
        if (start == null) return null;
        seen[start[1] * w + start[0]] = true;
        reach[start[1] * w + start[0]] = true;
        open.add(start);
        while (!open.isEmpty()) {
            int[] cell = open.poll();
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = cell[0] + d[0];
                int y = cell[1] + d[1];
                if (x < 0 || y < 0 || x >= w || y >= h || seen[y * w + x]) continue;
                seen[y * w + x] = true;
                if (fits(grid, x, y, radius)) {
                    reach[y * w + x] = true;
                    open.add(new int[] {x, y});
                }
            }
        }
        return reach;
    }

    /** Whether a body of {@code radius} stands at the middle or at a corner of cell {@code (x, y)}. */
    private static boolean fits(NavigationGrid grid, int x, int y, float radius) {
        if (ManualTerrainMotion.canStand(grid, x + 0.5f, y + 0.5f, radius)) return true;
        for (int cx = 0; cx <= 1; cx++) {
            for (int cy = 0; cy <= 1; cy++) {
                if (ManualTerrainMotion.canStand(grid, x + cx, y + cy, radius)) return true;
            }
        }
        return false;
    }

    private static int[] nearestStandable(NavigationGrid grid, float radius, int x0, int y0) {
        for (int r = 0; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    int x = x0 + dx;
                    int y = y0 + dy;
                    if (grid.inBounds(x, y) && fits(grid, x, y, radius)) {
                        return new int[] {x, y};
                    }
                }
            }
        }
        return null;
    }

    /** The zone whose node is nearest a cell. */
    static int nearestZone(List<ZonePlan> plans, int x, int y) {
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < plans.size(); i++) {
            int distance = WalkerPosture.chebyshev(x, y, plans.get(i).nodeX(), plans.get(i).nodeY());
            if (distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Stands each zone's folded posts ({@link DerelictPost}) beside its anchors, powered down. */
    private static void standPosts(BattleSimulation sim, int[] route, List<ZonePlan> plans, SecurityNetwork network,
                                   RuinsDelve delve, DerelictSecurityDirector director) {
        NavigationGrid grid = sim.getGrid();
        Walkable walkable = (x, y) -> grid.inBounds(x, y) && grid.isWalkable(x, y);
        Set<Long> taken = new HashSet<>();
        int n = 0;
        for (ZonePlan plan : plans) {
            int placed = 0;
            for (int[] anchor : DerelictPost.anchors(network.zone(plan.id()), plan.posts(), delve)) {
                if (placed == DerelictPost.PER_ZONE) break;
                int[] cell = besideRoute(route, anchor[0], anchor[1], walkable, taken);
                if (cell == null) continue;
                long post = standPost(sim, "derelict-post-" + n++, plan.id(), cell[0], cell[1], director);
                if (post == 0L) continue;
                // Posts keep apart, so two never close a passage between them.
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        taken.add(((long) (cell[0] + dx) << 32) | ((cell[1] + dy) & 0xffffffffL));
                    }
                }
                placed++;
            }
        }
    }

    /** Puts a zone's repair drone up over its node, homed there. */
    static long standRepairDrone(BattleSimulation sim, int zoneId, DerelictSecurityDirector director) {
        Long node = director.nodes().get(zoneId);
        if (node == null) return 0L;
        float hx = sim.world().x(node);
        float hy = sim.world().y(node);
        EntitySpec drone = Drone.create("derelict-repair-" + zoneId, Faction.DEFENDER, (int) Math.floor(hx),
                (int) Math.floor(hy), 0L, DroneLook.TENDER).role(UnitRole.STRUCTURE);
        long id = sim.spawn(drone);
        if (id == 0L) return 0L;
        AirBody body = sim.world().kinematics(id);
        if (body != null) body.teleport(hx, hy, 0f);
        sim.world().setPos(id, hx, hy);
        director.standRepairDrone(zoneId, id, hx, hy);
        return id;
    }

    /** Spawns one folded post of a zone at a cell, as an emplacement is stood, its power cut; 0 when it cannot. */
    static long standPost(BattleSimulation sim, String name, int zoneId, int x, int y,
                          DerelictSecurityDirector director) {
        long post = sim.spawn(MapTurret.create(name, Faction.DEFENDER, DerelictPost.STAND_IN_STRUCTURE, x, y));
        if (post == 0L) return 0L;
        NavigationGrid grid = sim.getGrid();
        grid.setWalkable(x, y, false);
        grid.setSeeThrough(x, y, true);
        grid.recomputeCoverAt(x + 1, y);
        grid.recomputeCoverAt(x - 1, y);
        grid.recomputeCoverAt(x, y + 1);
        grid.recomputeCoverAt(x, y - 1);
        sim.turretState().cutPower(post);
        director.standPost(zoneId, post);
        return post;
    }

    /** Whether a cell can be walked. */
    interface Walkable {
        boolean at(int x, int y);
    }

    /** How far from its zone's node cell a node may stand, cells. */
    static final int NODE_SEARCH_CELLS = 6;

    /**
     * Where a zone's node stands: the nearest cell to the node cell, off the route, set against a wall, with at least
     * two open cells left across from it, so the structure never narrows the route or a corridor below a walker's
     * width. The node cell itself when no such cell is near.
     */
    static int[] nodeCell(int[] route, ZonePlan plan, Walkable walkable) {
        int[] cell = besideRoute(route, plan.nodeX(), plan.nodeY(), walkable, Set.of());
        return cell != null ? cell : new int[] {plan.nodeX(), plan.nodeY()};
    }

    /**
     * The nearest cell to {@code (x0, y0)}, within {@link #NODE_SEARCH_CELLS}, that is off the route, not taken, set
     * against a wall and leaves three open cells across from it; null when there is none.
     */
    static int[] besideRoute(int[] route, int x0, int y0, Walkable walkable, Set<Long> taken) {
        Set<Long> onRoute = new HashSet<>();
        for (int i = 0; i + 1 < route.length; i += 2) onRoute.add(((long) route[i] << 32) | (route[i + 1] & 0xffffffffL));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int ring = 1; ring <= NODE_SEARCH_CELLS; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) continue;
                    int x = x0 + dx;
                    int y = y0 + dy;
                    long key = ((long) x << 32) | (y & 0xffffffffL);
                    if (!walkable.at(x, y) || onRoute.contains(key) || taken.contains(key)) continue;
                    for (int[] d : dirs) {
                        if (!walkable.at(x - d[0], y - d[1]) && walkable.at(x + d[0], y + d[1])
                                && walkable.at(x + 2 * d[0], y + 2 * d[1])
                                && walkable.at(x + 3 * d[0], y + 3 * d[1])) {
                            return new int[] {x, y};
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Whether a cell is under an intact roof. */
    interface Roofed {
        boolean at(int x, int y);
    }

    /**
     * Where a zone's flock is homed: its node, or where the node is under a roof, the first route cell from the node
     * toward the landing that is open to the sky. A flock never enters a roofed cell (`air-nouns.md`, Flocks), so
     * an interior zone's flock keeps its doorway.
     */
    static int[] openSkyHome(int[] route, ZonePlan plan, Roofed roofed) {
        int cells = route.length / 2;
        int start = 0;
        for (int i = 0; i < cells; i++) {
            if (route[i * 2] == plan.nodeX() && route[i * 2 + 1] == plan.nodeY()) {
                start = i;
                break;
            }
        }
        for (int i = start; i < cells; i++) {
            if (!roofed.at(route[i * 2], route[i * 2 + 1])) return new int[] {route[i * 2], route[i * 2 + 1]};
        }
        return new int[] {plan.nodeX(), plan.nodeY()};
    }

    /**
     * A stir push of the site's reserve ({@code ruins-pressure.md}): a line frame per squad, each in a lance of its own,
     * come up near {@code (x, y)} out of the network's bays. They are not the director's walkers: the push commands
     * them, and no zone's repair drone tends them. A guardian is never part of the reserve.
     *
     * @return the lances raised, fewer than asked where the ground has no room
     */
    public static List<Integer> raisePush(BattleSimulation sim, int squads, int x, int y) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < squads; i++) {
            int squad = spawn(sim, DerelictWalkerFrame.LINE, x, y);
            if (squad >= 0) out.add(squad);
        }
        return out;
    }

    /** One walker in a lance of its own, at the nearest standable point to its post; -1 when none is. */
    static int spawn(BattleSimulation sim, DerelictWalkerFrame frame, int postX, int postY) {
        int squad = sim.mintSquad(Faction.DEFENDER, UnitType.HEAVY_MECH);
        if (spawnInto(sim, frame, postX, postY, squad) == 0L) return -1;
        sim.getSquad(squad).originalSize = 1;
        return squad;
    }

    /**
     * One walker into an existing lance at the nearest standable point to {@code (postX, postY)}, as its leader: a
     * raised wreck stands again in its own lance (`derelict-watch.md`, Repair). Returns the walker, or 0 when no point
     * near is standable.
     */
    static long spawnInto(BattleSimulation sim, DerelictWalkerFrame frame, int postX, int postY, int squad) {
        float radius = frame.chassis.radius;
        MechSpawnPlacement.Point at = MechSpawnPlacement.nearCell(sim.getGrid(), radius, postX, postY,
                (x, y) -> true, (x, y) -> MechSpawnPlacement.unoccupied(sim.getRoster(), x, y, radius, 0L));
        if (at == null) return 0L;
        long id = sim.spawn(frame.applyTo(new EntitySpec(frame.designation(), Faction.DEFENDER, UnitType.HEAVY_MECH,
                at.cellX(), at.cellY())).atPosition(at.x(), at.y()).squad(squad));
        if (id == 0L) return 0L;
        sim.world().attachMechLoadout(id, frame.createLoadout());
        Squad lance = sim.getSquad(squad);
        lance.leaderId = id;
        return id;
    }

    /**
     * Stands each zone's service bay beside its node and its sealed reserve bay by its flock's home, and lays the
     * service ducts' vents: one at each zone's flock home and one in each court, the court's under the nearest zone
     * (`derelict-watch.md`). The reserve bay opens a second flock in the zone's livery when the facility's wake calls
     * it.
     */
    static void standWatchHousings(BattleSimulation sim, int[] route, List<ZonePlan> plans, RuinsDelve delve,
                                   FlockSystemPort port, FlockLivery livery, DerelictSecurityDirector director) {
        NavigationGrid grid = sim.getGrid();
        Walkable walkable = (x, y) -> grid.inBounds(x, y) && grid.isWalkable(x, y);
        Roofed roofed = (x, y) -> sim.getTopology() != null && sim.getTopology().isRoofIntact(x, y);
        Set<Long> taken = new HashSet<>();
        for (long node : director.nodes().values()) taken.add(cellKey(sim.world().cellX(node), sim.world().cellY(node)));
        List<ServiceDucts.Vent> vents = new ArrayList<>();
        for (ZonePlan plan : plans) {
            int[] bay = besideRoute(route, plan.nodeX(), plan.nodeY(), walkable, taken);
            if (bay != null) {
                taken.add(cellKey(bay[0], bay[1]));
                long id = sim.spawn(DerelictNode.create("derelict-service-bay-" + plan.id(), Faction.DEFENDER,
                        bay[0], bay[1]).health(ServiceBay.MAX_STRUCTURE).armor(ServiceBay.ARMOR_CAPACITY,
                        DerelictNode.ARMOR_RATING));
                if (id != 0L) director.standServiceBay(plan.id(), id);
            }
            int[] home = openSkyHome(route, plan, roofed);
            vents.add(new ServiceDucts.Vent(vents.size(), home[0], home[1], plan.id()));
            int[] reserve = nearOpen(home, RESERVE_SET_BACK_CELLS, walkable, roofed, taken);
            if (reserve != null) {
                taken.add(cellKey(reserve[0], reserve[1]));
                long id = sim.spawn(DerelictNode.create("derelict-reserve-bay-" + plan.id(), Faction.DEFENDER,
                        reserve[0], reserve[1]));
                if (id != 0L) director.standReserveBay(plan.id(), id);
            }
        }
        if (delve != null) {
            for (RuinsDelve.Layer layer : delve.layers()) {
                if (layer.court() == null) continue;
                int x = layer.court().ground().centreX();
                int y = layer.court().ground().centreY();
                if (roofed.at(x, y) || !walkable.at(x, y)) continue;
                vents.add(new ServiceDucts.Vent(vents.size(), x, y, plans.get(nearestZone(plans, x, y)).id()));
            }
        }
        director.layDucts(new ServiceDucts(vents));
        int[] raised = {0};
        director.standPostsWith((zoneId, x, y) -> standPost(sim, "derelict-post-raised-" + raised[0]++, zoneId, x, y,
                director));
        director.launchReserves((zoneId, x, y) -> {
            ZonePlan plan = null;
            for (ZonePlan p : plans) if (p.id() == zoneId) plan = p;
            if (plan == null) return -1;
            Flock flock = sim.flocks().launch(sim, Faction.DEFENDER, x, y, FLOCK_SIZE, FlockWeights.WASP,
                    plan.perimeterCells() + FlockTasking.LEASH_CELLS, livery);
            port.add(flock, zoneId);
            return flock.id();
        });
    }

    /** How far from a flock's home its zone's reserve bay is set back, cells. */
    static final int RESERVE_SET_BACK_CELLS = 3;

    /** The nearest open-sky, walkable cell at least {@code minCells} from {@code from} and not taken; null if none near. */
    static int[] nearOpen(int[] from, int minCells, Walkable walkable, Roofed roofed, Set<Long> taken) {
        for (int r = minCells; r <= minCells + 6; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = from[0] + dx;
                    int y = from[1] + dy;
                    if (!walkable.at(x, y) || roofed.at(x, y) || taken.contains(cellKey(x, y))) continue;
                    return new int[] {x, y};
                }
            }
        }
        return null;
    }

    static long cellKey(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }
}
