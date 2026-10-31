package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.evacuation.ColonyMilitia;
import com.dillon.starsectormarines.battle.evacuation.FortifiedWorks;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Holds the Road's cut-off town ({@code cordon-nouns.md}, Cut-off town): its compound's wall dressed as a redoubt's
 * is (a gun tower beside each gate, firing slits along the runs, floodlights), the trapped townspeople spread through
 * it, and its defenders: what is left of the town militia (two teams, the ones beat R4 rides out) at the posts behind
 * the slits, the side facing the front first, and the town guard (three more) inside, a team with each of the largest
 * groups of townspeople. Allied, under garrison authority, never in the player's command pool. Enough to hold the
 * opening and the first convoy's loading, not to outlast the tides alone: nobody reinforces the town, so each tide
 * bleeds it and the company is wanted there later.
 *
 * <p>The townspeople wait in groups (owner, 2026-10-31: one bunch looks wrong, and spread they make the player choose
 * what to hold): the first truckload in the staging area beside the truck yard ({@link CordonStagingArea}), the rest
 * in the compound's largest rooms, and each group walks out to the yard while a truck there has seats.
 *
 * <p>At town density the compound also has flak (owner, 2026-10-31: "put flak turrets in the central compound to
 * defend from fliers"): {@link #FLAK_MIN} to {@link #FLAK_MAX} allied anti-air flak positions on its open courtyard
 * ground, spread so every cell inside the wall is within {@link #FLAK_COVER} of one ({@link #flakSites}).
 */
public final class CordonTownGarrison {

    /** The town militia's teams, at the wall: what is left of the colony militia, ridden out by beat R4. */
    static final int MILITIA_TEAMS = 2;
    /** The town guard's teams, inside: one with each of the largest groups of townspeople. */
    static final int GUARD_TEAMS = 3;
    /** Cells off the staging area a defender is seated at the nearest: round it, never on it. */
    static final int STAGING_CLEAR = 1;
    /** Cells round the corridor's end no defender is seated on: where the trucks pull up. */
    static final int ROAD_CLEAR = 3;
    /** Rooms of the compound the trapped are spread over, at most: the largest. Untuned. */
    static final int ROOMS = 4;
    /** Open cells a room needs to shelter a group. */
    static final int ROOM_MIN = 6;
    /** The trapped who wait in the staging area by the yard: a convoy's seats, the first truckload. Untuned. */
    static final int AT_STAGING = CordonConvoys.TRUCKS * CordonConvoys.SEATS;
    /** Cells round a room an inside team holds. */
    static final int ROOM_HOLD = 6;
    /** How far from its plan seed the compound's wall is looked for, as a redoubt's. */
    private static final int WALL_REACH = CordonGarrisons.WALL_REACH;
    /** Cells into the compound past the gate the defenders stand where no wall could be read. */
    private static final int FALLBACK_DEPTH = 6;
    /** A team's cluster where no wall could be read: room for its four and a little more. */
    private static final int FALLBACK_CLUSTER = 6;
    /** The compound's flak positions at town density, at least: a pair whose arcs overlap over the rooms. */
    static final int FLAK_MIN = 2;
    /** The compound's flak positions, at most. */
    static final int FLAK_MAX = 4;
    /**
     * Cells every cell inside the wall is brought within of a flak position, while {@link #FLAK_MAX} allows: well
     * inside the gun's reach, so neighbouring arcs overlap over the rooms. Untuned.
     */
    static final float FLAK_COVER = 13f;
    /** Cells in from a gate or postern no flak position stands on: the way through kept clear behind it. */
    static final int GATE_CLEAR = 4;

    private CordonTownGarrison() { }

    /**
     * The town held, the militia's squads among its defenders (for beat R4), and where its people wait: the staging
     * area then the rooms ({@code groups}), {@code counts[i]} of the trapped in each.
     */
    public record Held(CordonTown town, List<Integer> militia, List<List<int[]>> groups, int[] counts) {
        /** The staging area beside the truck yard. */
        public CordonStagingArea staging() { return town.staging(); }
    }

    /** As {@link #man(BattleSimulation, Precinct, int[], CordonGateYard, int, RiskLevel, Random)}, with no truck yard. */
    public static Held man(BattleSimulation sim, Precinct compound, int[] gate, int front, RiskLevel risk, Random rng) {
        return man(sim, compound, gate, null, front, risk, rng);
    }

    /** As {@link #man(BattleSimulation, Precinct, int[], CordonGateYard, int, RiskLevel, Random, boolean)}, no flak. */
    public static Held man(BattleSimulation sim, Precinct compound, int[] gate, CordonGateYard truckYard, int front,
                           RiskLevel risk, Random rng) {
        return man(sim, compound, gate, truckYard, front, risk, rng, false);
    }

    /**
     * Dresses and mans the compound round {@code compound}, its loading gate the one nearest {@code gate} (the
     * corridor's last cell), and spreads its {@code CordonConvoys#TOWNSPEOPLE} through it. Where the map laid a truck
     * yard inside that gate the staging area stands beside it and no defender stands in it. Where no wall can be read
     * the defenders stand past the gate as the militia did before and everyone waits by the road's end; never
     * {@code null}.
     *
     * @param truckYard the yard the convoys drive into, or {@code null}
     * @param front     +1 where the front is high y, -1 where it is low y
     * @param flak      whether the compound has flak ({@link #flakSites}): town density's
     */
    public static Held man(BattleSimulation sim, Precinct compound, int[] gate, CordonGateYard truckYard, int front,
                           RiskLevel risk, Random rng, boolean flak) {
        NavigationGrid grid = sim.getGrid();
        int[] box = compound == null ? null : CordonGarrisons.perimeterBox(sim.getTopology(), compound);
        if (box != null) {
            int centreX = (box[0] + box[2]) / 2, centreY = (box[1] + box[3]) / 2;
            int[] open0 = CordonGarrisons.nearestOpen(grid, centreX, centreY, box[0] + 1, box[1] + 1, box[2] - 1,
                    box[3] - 1);
            if (open0 != null) {
                CivilDefenceCompound.Dressed wall = CivilDefenceCompound.dress(sim, centreX, centreY, open0[0], open0[1],
                        gate[0], gate[1], WALL_REACH);
                int left = wall != null ? wall.left() + 1 : box[0] + 1, top = wall != null ? wall.top() + 1 : box[1] + 1;
                int right = wall != null ? wall.right() - 1 : box[2] - 1;
                int bottom = wall != null ? wall.bottom() - 1 : box[3] - 1;
                List<Long> guns = new ArrayList<>();
                for (int[] gun : wall != null ? wall.guns() : List.<int[]>of()) {
                    guns.add(ColonyMilitia.seatEmplacement(sim, gun, "Town gun tower " + (guns.size() + 1)));
                }
                int frontRow = front > 0 ? bottom : top;
                int[] loading = wall != null ? new int[]{wall.gateX(), wall.gateY()} : gate;
                List<int[]> open = new ArrayList<>();
                for (int row = 0; row <= bottom - top; row++) {
                    int y = front > 0 ? bottom - row : top + row;
                    for (int x = left; x <= right; x++) {
                        if (grid.isWalkable(x, y) && !grid.isDoorway(x, y)) open.add(new int[]{x, y});
                    }
                }
                // Beside the truck yard where there is one, from where a truck stands to load; never on it.
                int[] anchor = truckYard != null ? new int[]{truckYard.stopX(), truckYard.stopY()} : loading;
                CordonStagingArea staging = CordonStagingArea.of(
                        (x, y) -> grid.inBounds(x, y) && grid.isWalkable(x, y) && !grid.isDoorway(x, y)
                                && (truckYard == null || !truckYard.contains(x, y)),
                        left, top, right, bottom, anchor[0], anchor[1]);
                List<List<int[]>> rooms = rooms(grid, sim.getTopology(), left, top, right, bottom, staging, truckYard);
                // The militia at the wall, the front first; the guard inside, a team with each of the largest groups.
                List<int[]> wallPosts = seats(wall != null ? wall.posts() : List.of(), open, staging, gate, truckYard,
                        frontRow, centreX, MILITIA_TEAMS * ColonyMilitia.POST_SIZE);
                List<List<int[]>> teams = new ArrayList<>();
                for (int t = 0; t < MILITIA_TEAMS; t++) {
                    List<int[]> cells = new ArrayList<>();
                    for (int k = t; k < wallPosts.size(); k += MILITIA_TEAMS) cells.add(wallPosts.get(k));
                    teams.add(cells);
                }
                // A guard team just inside each way in the convoys do not use (the swarm's other doors), then one
                // with each of the largest rooms, then by the staging area.
                List<int[]> holds = new ArrayList<>();
                for (int[] g : gates(grid, left, top, right, bottom)) {
                    if (holds.size() >= GUARD_TEAMS - 1) break;
                    if (Math.max(Math.abs(g[0] - loading[0]), Math.abs(g[1] - loading[1])) <= g[2]) continue;
                    // Nor the one-way yard's exit: the trucks' way out stays clear.
                    int[] exit = truckYard == null ? null : truckYard.exit();
                    if (exit != null && Math.max(Math.abs(g[0] - exit[0]), Math.abs(g[1] - exit[1])) <= g[2]) continue;
                    int[] post = inside(g, left, top, right, bottom);
                    if (truckYard != null && truckYard.contains(post[0], post[1])) continue;
                    holds.add(post);
                }
                for (List<int[]> room : rooms) {
                    if (holds.size() >= GUARD_TEAMS) break;
                    holds.add(middle(room));
                }
                while (holds.size() < GUARD_TEAMS) holds.add(new int[]{staging.centreX(), staging.centreY()});
                for (int t = 0; t < GUARD_TEAMS; t++) {
                    int[] at = holds.get(t);
                    List<int[]> cells = new ArrayList<>();
                    for (int[] c : CordonGround.clusterNear(sim, at[0], at[1], ColonyMilitia.POST_SIZE * 3)) {
                        if (cells.size() >= ColonyMilitia.POST_SIZE) break;
                        if (staging.contains(c[0], c[1]) || truckYard != null && truckYard.contains(c[0], c[1])
                                || taken(teams, c)) continue;
                        cells.add(c);
                    }
                    teams.add(cells);
                }
                if (flak) {
                    List<int[]> taken = new ArrayList<>();
                    for (List<int[]> team : teams) taken.addAll(team);
                    CellTopology topology = sim.getTopology();
                    List<int[]> sites = flakSites((x, y) -> grid.inBounds(x, y) && grid.isWalkable(x, y)
                                    && !grid.isDoorway(x, y), (x, y) -> grid.inBounds(x, y)
                                    && topology.getBuildingId(x, y) != 0,
                            (x, y) -> grid.inBounds(x, y) && topology.isStreet(x, y),
                            left, top, right, bottom, gates(grid, left, top, right, bottom), staging, truckYard, gate,
                            taken);
                    for (int i = 0; i < sites.size(); i++) {
                        guns.add(ColonyMilitia.seatEmplacement(sim, sites.get(i), "Town flak " + (i + 1),
                                TurretCatalogRegistry.ANTI_AIR_STRUCTURE_ID));
                    }
                }
                FortifiedWorks.mark(grid, left - 1, top - 1, right + 1, bottom + 1);
                Held held = seat(sim, teams, holds, left, top, right, bottom, loading, staging, guns, risk, rng, rooms);
                return held;
            }
        }
        // No wall to read: the staging area is the ground round the road's end, and the defenders stand past it.
        int reach = CordonStagingArea.REACH;
        CordonStagingArea staging = CordonStagingArea.of(
                (x, y) -> grid.inBounds(x, y) && grid.isWalkable(x, y) && !grid.isDoorway(x, y),
                gate[0] - reach, gate[1] - reach, gate[0] + reach, gate[1] + reach, gate[0], gate[1]);
        List<List<int[]>> teams = new ArrayList<>();
        for (int t = 0; t < MILITIA_TEAMS + GUARD_TEAMS; t++) {
            List<int[]> cells = CordonGround.clusterNear(sim, gate[0],
                    gate[1] + front * (reach + FALLBACK_DEPTH + t * 3), FALLBACK_CLUSTER);
            teams.add(new ArrayList<>(cells.subList(0, Math.min(ColonyMilitia.POST_SIZE, cells.size()))));
        }
        int l = gate[0], t = gate[1], r = gate[0], b = gate[1];
        for (List<int[]> team : teams) {
            for (int[] p : team) {
                l = Math.min(l, p[0]);
                t = Math.min(t, p[1]);
                r = Math.max(r, p[0]);
                b = Math.max(b, p[1]);
            }
        }
        return seat(sim, teams, List.of(), l, t, r, b, gate, staging, List.of(), risk, rng, List.of());
    }

    /**
     * Seats each team on its cells and tells it what to hold under garrison authority: the militia's the compound,
     * from the wall; each guard team the ground round its room ({@code holds}). The militia's teams first.
     */
    private static Held seat(BattleSimulation sim, List<List<int[]>> teams, List<int[]> holds, int left, int top,
                             int right, int bottom, int[] gate, CordonStagingArea staging, List<Long> guns,
                             RiskLevel risk, Random rng, List<List<int[]>> rooms) {
        int centreX = (left + right) / 2, centreY = (top + bottom) / 2;
        int radius = Math.max(right - left, bottom - top) / 2 + 2;
        List<Integer> squads = new ArrayList<>();
        List<Integer> militia = new ArrayList<>();
        int garrison = 0;
        for (int t = 0; t < teams.size(); t++) {
            if (teams.get(t).isEmpty()) continue;
            boolean isMilitia = t < MILITIA_TEAMS;
            String name = isMilitia ? "Town militia " + (t + 1) : "Town guard " + (t - MILITIA_TEAMS + 1);
            int squad = ColonyMilitia.seatTeam(sim, teams.get(t), name, risk, rng);
            int guard = t - MILITIA_TEAMS;
            ObjectiveAssignment hold = !isMilitia && guard < holds.size()
                    ? ObjectiveAssignment.defendArea(squad, holds.get(guard)[0], holds.get(guard)[1], ROOM_HOLD)
                    : ObjectiveAssignment.defendArea(squad, centreX, centreY, radius);
            sim.assignSquadCommand(hold, CommandAuthority.GARRISON, CordonGarrisons.ISSUER,
                    isMilitia ? "hold the town" : "guard the townspeople");
            squads.add(squad);
            if (isMilitia) militia.add(squad);
            garrison += sim.squadMemberCount(squad);
        }
        CordonTown town = new CordonTown(left, top, right, bottom, gate[0], gate[1], staging, squads, guns, garrison);
        List<List<int[]>> groups = new ArrayList<>();
        groups.add(staging.cells());
        groups.addAll(rooms);
        int[] capacities = new int[rooms.size()];
        for (int i = 0; i < rooms.size(); i++) capacities[i] = rooms.get(i).size();
        int[] counts = split(CordonConvoys.TOWNSPEOPLE, capacities, AT_STAGING);
        town.attachRooms(rooms, counts);
        return new Held(town, militia, groups, counts);
    }

    /**
     * How the trapped are spread, staging area first and then each room: {@code atStaging} by the yard (the first
     * truckload), the rest shared evenly over the rooms, the larger first for any odd one, none past what a room has
     * cells for; what no room can hold waits by the yard. Pure.
     */
    static int[] split(int people, int[] capacities, int atStaging) {
        int[] out = new int[capacities.length + 1];
        out[0] = Math.min(Math.max(0, atStaging), Math.max(0, people));
        int rest = Math.max(0, people) - out[0];
        if (capacities.length == 0) {
            out[0] += rest;
            return out;
        }
        int share = rest / capacities.length, odd = rest % capacities.length;
        for (int i = 0; i < capacities.length; i++) {
            int wanted = share + (i < odd ? 1 : 0);
            out[i + 1] = Math.min(wanted, Math.max(0, capacities[i]));
            out[0] += wanted - out[i + 1];
        }
        return out;
    }

    /**
     * The compound's rooms the trapped wait in: open cells inside its buildings, grouped by building, off the staging
     * area and the truck yard, the largest {@link #ROOMS} with {@link #ROOM_MIN} cells or more, largest first.
     */
    static List<List<int[]>> rooms(NavigationGrid grid, CellTopology topology, int left, int top, int right, int bottom,
                                   CordonStagingArea staging, CordonGateYard truckYard) {
        Map<Integer, List<int[]>> byBuilding = new LinkedHashMap<>();
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                int id = topology.getBuildingId(x, y);
                if (id == 0 || !grid.isWalkable(x, y) || grid.isDoorway(x, y) || staging.contains(x, y)
                        || truckYard != null && truckYard.contains(x, y)) continue;
                byBuilding.computeIfAbsent(id, k -> new ArrayList<>()).add(new int[]{x, y});
            }
        }
        List<List<int[]>> rooms = new ArrayList<>();
        for (List<int[]> cells : byBuilding.values()) if (cells.size() >= ROOM_MIN) rooms.add(cells);
        rooms.sort(Comparator.<List<int[]>>comparingInt(List::size).reversed());
        return rooms.size() > ROOMS ? new ArrayList<>(rooms.subList(0, ROOMS)) : rooms;
    }

    /**
     * The compound's ways in: runs of open cells on the ring just outside its inside ground
     * ({@code left..right} by {@code top..bottom}), each as {x, y, width} at its middle, in order round the ring.
     */
    public static List<int[]> gates(NavigationGrid grid, int left, int top, int right, int bottom) {
        List<int[]> ring = new ArrayList<>();
        int l = left - 1, t = top - 1, r = right + 1, b = bottom + 1;
        for (int x = l; x <= r; x++) ring.add(new int[]{x, t});
        for (int y = t + 1; y <= b; y++) ring.add(new int[]{r, y});
        for (int x = r - 1; x >= l; x--) ring.add(new int[]{x, b});
        for (int y = b - 1; y > t; y--) ring.add(new int[]{l, y});
        List<int[]> gates = new ArrayList<>();
        List<int[]> run = new ArrayList<>();
        for (int i = 0; i <= ring.size(); i++) {
            int[] c = i < ring.size() ? ring.get(i) : null;
            if (c != null && grid.inBounds(c[0], c[1]) && grid.isWalkable(c[0], c[1])) {
                run.add(c);
                continue;
            }
            if (!run.isEmpty()) {
                int[] mid = run.get(run.size() / 2);
                gates.add(new int[]{mid[0], mid[1], run.size()});
                run.clear();
            }
        }
        return gates;
    }

    /**
     * Where the compound's flak stands: on open courtyard ground inside the wall ({@code left..right} by
     * {@code top..bottom}), each site open on all four sides and touching no building (so never in a room, nor on a
     * doorstep) and no street (the paved way through the compound the trucks and people use), clear of the truck yard and the ground beside it by a cell, of the staging area and beside it, of
     * where the corridor meets the town ({@link #ROAD_CLEAR}), of every gate and postern and the {@link #GATE_CLEAR}
     * cells in from it, and of the defenders' cells by a cell. Sites are chosen to cover the whole inside: each next
     * the candidate that leaves the farthest inside cell nearest a gun (the inside's cells nearer on the whole between
     * equals), at least {@link #FLAK_MIN} while there are candidates, then more while one brings the farthest cell
     * nearer, until every inside cell is within {@link #FLAK_COVER}, at most {@link #FLAK_MAX}. Pure.
     *
     * @param open    where a cell is open ground a gun or a body could stand on
     * @param indoors where a cell is inside a building
     * @param street  where a cell is street paving
     * @param gates   the compound's ways in, as {@link #gates} gives them: {x, y, width} at each one's middle
     * @param taken   the defenders' cells
     */
    static List<int[]> flakSites(CordonStagingArea.Open open, CordonStagingArea.Open indoors,
                                 CordonStagingArea.Open street, int left, int top, int right, int bottom, List<int[]> gates, CordonStagingArea staging,
                                 CordonGateYard truckYard, int[] corridorEnd, List<int[]> taken) {
        int width = right - left + 1, height = bottom - top + 1;
        if (width <= 0 || height <= 0) return List.of();
        List<int[]> candidates = new ArrayList<>();
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                if (flakSite(open, indoors, street, x, y, gates, staging, truckYard, corridorEnd, taken)) {
                    candidates.add(new int[]{x, y});
                }
            }
        }
        // Each inside cell's distance to its nearest gun so far.
        float[] nearest = new float[width * height];
        Arrays.fill(nearest, Float.MAX_VALUE);
        List<int[]> sites = new ArrayList<>();
        while (sites.size() < FLAK_MAX && !candidates.isEmpty()) {
            float worst = 0f;
            for (float d : nearest) worst = Math.max(worst, d);
            if (sites.size() >= FLAK_MIN && worst <= FLAK_COVER) break;
            int[] best = null;
            float bestWorst = Float.MAX_VALUE;
            double bestSum = Double.MAX_VALUE;
            for (int[] c : candidates) {
                float w = 0f;
                double sum = 0;
                for (int y = 0; y < height && w <= bestWorst; y++) {
                    for (int x = 0; x < width; x++) {
                        float d = Math.min(nearest[y * width + x], (float) Math.hypot(left + x - c[0], top + y - c[1]));
                        if (d > w) w = d;
                        sum += d;
                    }
                }
                if (w < bestWorst || w == bestWorst && sum < bestSum) {
                    bestWorst = w;
                    bestSum = sum;
                    best = c;
                }
            }
            if (sites.size() >= FLAK_MIN && bestWorst >= worst) break;
            sites.add(best);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    nearest[y * width + x] = Math.min(nearest[y * width + x],
                            (float) Math.hypot(left + x - best[0], top + y - best[1]));
                }
            }
            int[] chosen = best;
            candidates.removeIf(c -> chebyshev(chosen, c[0], c[1]) <= 1);
        }
        return sites;
    }

    /** Whether a gun may stand at {@code (x, y)}, by {@link #flakSites}' rules. */
    private static boolean flakSite(CordonStagingArea.Open open, CordonStagingArea.Open indoors,
                                    CordonStagingArea.Open street, int x, int y,
                                    List<int[]> gates, CordonStagingArea staging, CordonGateYard truckYard,
                                    int[] corridorEnd, List<int[]> taken) {
        if (!open.at(x, y)) return false;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (!open.at(x + d[0], y + d[1])) return false;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (indoors.at(x + dx, y + dy) || street.at(x + dx, y + dy)) return false;
                if (truckYard != null && truckYard.contains(x + dx, y + dy)) return false;
            }
        }
        if (truckYard != null) for (int[] b : truckYard.beside()) if (chebyshev(b, x, y) <= 1) return false;
        if (clear(new int[]{x, y}, staging, corridorEnd, truckYard)) return false;
        for (int[] g : gates) if (chebyshev(g, x, y) <= g[2] / 2 + GATE_CLEAR) return false;
        for (int[] t : taken) if (chebyshev(t, x, y) <= 1) return false;
        return true;
    }

    private static int chebyshev(int[] c, int x, int y) {
        return Math.max(Math.abs(c[0] - x), Math.abs(c[1] - y));
    }

    /** A few cells in from a gate on the ring, onto the compound's inside ground. */
    static int[] inside(int[] gate, int left, int top, int right, int bottom) {
        int x = Math.max(left, Math.min(right, gate[0])), y = Math.max(top, Math.min(bottom, gate[1]));
        x += Integer.signum(x - gate[0]) * 2;
        y += Integer.signum(y - gate[1]) * 2;
        return new int[]{Math.max(left, Math.min(right, x)), Math.max(top, Math.min(bottom, y))};
    }

    /** The cell of a room nearest the mean of its cells. */
    static int[] middle(List<int[]> cells) {
        float mx = 0, my = 0;
        for (int[] c : cells) {
            mx += c[0];
            my += c[1];
        }
        mx /= cells.size();
        my /= cells.size();
        int[] best = cells.get(0);
        for (int[] c : cells) {
            if (Math.hypot(c[0] - mx, c[1] - my) < Math.hypot(best[0] - mx, best[1] - my)) best = c;
        }
        return best;
    }

    /**
     * Where the defenders are seated, at most {@code seats}: the posts behind the wall's slits, the side facing the
     * front first and then out along it from the middle, then the open yard from the front row back while seats
     * remain, never on or beside the staging area ({@link #STAGING_CLEAR}) nor within {@link #ROAD_CLEAR} cells of
     * the corridor's end. Pure.
     *
     * @param wallPosts the posts the dressing laid behind the slits
     * @param yard      the open ground inside the wall, front row first
     * @param frontRow  the row inside the wall nearest the front
     */
    static List<int[]> seats(List<int[]> wallPosts, List<int[]> yard, CordonStagingArea staging, int[] corridorEnd,
                             int frontRow, int centreX, int seats) {
        return seats(wallPosts, yard, staging, corridorEnd, null, frontRow, centreX, seats);
    }

    /** The same, and never in the truck yard, where a convoy's trucks drive in and turn. Pure. */
    static List<int[]> seats(List<int[]> wallPosts, List<int[]> yard, CordonStagingArea staging, int[] corridorEnd,
                             CordonGateYard truckYard, int frontRow, int centreX, int seats) {
        List<int[]> posts = new ArrayList<>();
        for (int[] p : wallPosts) if (!clear(p, staging, corridorEnd, truckYard)) posts.add(p);
        posts.sort(Comparator.<int[]>comparingInt(c -> Math.abs(c[1] - frontRow))
                .thenComparingInt(c -> Math.abs(c[0] - centreX)).thenComparingInt(c -> c[1]).thenComparingInt(c -> c[0]));
        if (posts.size() > seats) posts = new ArrayList<>(posts.subList(0, seats));
        for (int[] c : yard) {
            if (posts.size() >= seats) break;
            if (clear(c, staging, corridorEnd, truckYard) || taken(posts, c)) continue;
            posts.add(c);
        }
        return posts;
    }

    /**
     * Whether a cell is kept clear: the staging area and the ground beside it, the truck yard, or where the trucks
     * pull up.
     */
    private static boolean clear(int[] c, CordonStagingArea staging, int[] corridorEnd, CordonGateYard truckYard) {
        return staging.near(c[0], c[1], STAGING_CLEAR) || truckYard != null && truckYard.contains(c[0], c[1])
                || corridorEnd != null
                && Math.max(Math.abs(c[0] - corridorEnd[0]), Math.abs(c[1] - corridorEnd[1])) <= ROAD_CLEAR;
    }

    private static boolean taken(List<int[]> cells, int[] c) {
        for (int[] p : cells) if (p[0] == c[0] && p[1] == c[1]) return true;
        return false;
    }

    private static boolean taken(Iterable<List<int[]>> teams, int[] c) {
        for (List<int[]> team : teams) if (taken(team, c)) return true;
        return false;
    }
}
