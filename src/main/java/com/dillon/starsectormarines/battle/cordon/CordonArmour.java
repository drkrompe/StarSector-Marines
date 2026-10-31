package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.allied.AlliedCommand;
import com.dillon.starsectormarines.battle.command.allied.EscortPace;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleMoveOrderService;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationWalls;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * The allies' light armour on the line ({@code cordon-nouns.md}, Allied armour): cars and
 * technicals under the allied command, each given a redoubt of its own to fight beside.
 *
 * <p>A hull stands beside its redoubt, out past a side wall and a few cells back from the front:
 * from there its gun reaches across the ground in front of the wall. Behind the rear wall it was out
 * of reach of the front (a redoubt is deeper than a heavy machine gun's range) and shot at nothing.
 * Several on one redoubt take turns on the two sides, each a step further back.
 *
 * <p>On the Road the armour also escorts the convoys, and on either line an ammunition truck or a beat's dozer on
 * the road: the hulls nearest the place it wants its escort leave their sectors and drive there, out of the surplus
 * only ({@link CordonAlliedRules#escorts}: above their sector's floor, one a sector, never a calling redoubt's last),
 * and go back to their sectors when it is out. In a city, which fields no hulls, a road hull takes a walking escort out
 * of the same surplus instead: a sector's mech above its pair, or a street mouth's rifle team while its pair stands.
 * A road hull with none is not Escorted, and the company walks with it.
 */
public final class CordonArmour {

    /** Ticks between looks at which redoubts are calling. */
    static final int REVIEW_TICKS = 30;
    /** Cells behind a redoubt's rear wall a carrier sets its squad down. */
    static final int BEHIND_WALL = 4;
    /** Cells out past a redoubt's side wall a hull answering its call stands. */
    static final int FLANK_OUT = 4;
    /** Cells back from the redoubt's front wall the first hull on a flank stands. */
    static final int FLANK_BACK = 5;
    /** Cells further back each later hull on the same flank stands. */
    static final int FLANK_STEP = 4;
    /** Cells a hull set down at setup may be moved to find open ground it fits on. */
    static final int SET_DOWN_REACH = 24;
    /** Cells in front of a redoubt's front wall its street mouths are read, and its mechs stand. */
    static final int MOUTH_OUT = 6;
    /** Cells either side of a redoubt's side walls a street mouth may lie. */
    static final int MOUTH_REACH = 12;
    /** Open ground wider than this along the mouths' row is open country, not a street: a mech stands on it nearest the redoubt. */
    static final int MOUTH_WIDEST = 12;
    /** Cells across between two mechs sharing one mouth. */
    static final int MOUTH_PAIR = 4;
    /** Cells in front of the front wall a mouth's rifle team stands, behind its mechs. */
    static final int TEAM_OUT = 2;
    /** Cells in front of the front wall the middle of a yard mortar's fire mission lies: past the mouths. */
    public static final int MISSION_OUT = 22;
    /** How far from its middle a yard mortar's fire mission reaches. */
    public static final int MISSION_RADIUS = 18;

    private final AlliedCommand command;
    private final int frontward;
    private final Map<Long, int[]> home = new HashMap<>();
    private final Map<Long, CordonRedoubt> answering = new LinkedHashMap<>();
    /** The main body of hull ground per clearance radius, found at the first set-down. */
    private final Map<Integer, boolean[]> mainGround = new HashMap<>();
    /** The allies' mechs on the line, in the order they came, and the redoubt each stands in front of. */
    private final Map<Long, CordonRedoubt> mechSector = new LinkedHashMap<>();
    /** The rifle teams in the redoubts' street mouths, each with its redoubt and the ground it holds. */
    private final List<StreetTeam> streetTeams = new ArrayList<>();
    /** The street teams sent to escort a road hull, by squad, until they are sent back. */
    private final Set<Integer> teamsOut = new HashSet<>();
    /** Whether a road hull with no hull free may take a walking escort (a city's: {@link CordonAlliedRules#walkingEscorts}). */
    private boolean walkingEscorts;
    /** The mortar teams in the redoubts' yards. */
    private final List<Integer> mortarTeams = new ArrayList<>();
    /** Each redoubt's street mouths, found once at the first look ({@link #mouths}). */
    private final Map<CordonRedoubt, List<int[]>> mouthsOf = new HashMap<>();
    /** Each redoubt's rear gate drop, found once ({@link #rearGate}). */
    private final Map<CordonRedoubt, int[]> gateOf = new HashMap<>();
    /** Each convoy's escort posts, or {@code null} off the Road. */
    private Function<BattleSimulation, List<int[][]>> escorts;
    /** The hulls on the road that want an escort beside them: ammunition trucks, a beat's dozer; or {@code null}. */
    private Function<BattleSimulation, List<Long>> hullEscorts;
    /** Machines each manned redoubt keeps on its posts ({@link CordonAlliedRules#coverFloor}). */
    private int floor = 1;
    /** Whether hulls keep to the streets (a town): every flank post moved to the nearest street cell in reach. */
    private boolean roadbound;
    /** Street ground a footprint of each clearance radius fits on and can drive away from, found once. */
    private final Map<Integer, boolean[]> streetGround = new HashMap<>();
    /** Redoubts whose sector has had a machine at some look: the radio's Uncovered needs a sector that had cover. */
    private final Set<CordonRedoubt> everCovered = new HashSet<>();
    /** Each redoubt's Uncovered at the last look. */
    private final Set<CordonRedoubt> uncoveredNow = new HashSet<>();
    /** Ticks each redoubt has stood uncovered, by number, for evidence. */
    private final Map<Integer, Integer> uncoveredTicks = new LinkedHashMap<>();
    /** The road hulls an armed allied hull stood alongside at the last look (the radio's Escorted). */
    private final Set<Long> escortedHulls = new HashSet<>();
    /** Each escort hull sent beside a road hull at the last look, and the road hull it is with. */
    private final Map<Long, Long> escortOf = new LinkedHashMap<>();
    /** The side of each road hull its escort keeps: 1 its right, -1 its left, 0 behind it. */
    private final Map<Long, Integer> escortSide = new HashMap<>();
    /** Road hulls that ever wanted an escort, and those that ever had one, for evidence. */
    private final Set<Long> hullsWanting = new HashSet<>(), hullsEscorted = new HashSet<>();
    /** Escort places a look filled and left short, summed over looks, for evidence. */
    private long escortLooksTaken, escortLooksShort;
    /** Looks a road hull had an armed allied hull alongside, and looks it had none, for evidence. */
    private long alongsideLooks, aloneLooks;
    /** Each convoy's escort at the last look, in the order its posts were asked: {posts wanted, hulls taken}. */
    private final List<int[]> convoyEscorts = new ArrayList<>();
    /** Each escort on its way to a road hull, and how it is closing on it. */
    private final Map<Long, Closing> closing = new HashMap<>();
    /** Hulls that were sent to escort and did not close on their road hull, and the tick they may be sent again. */
    private final Map<Long, Integer> benched = new HashMap<>();

    /** @param frontward which way the front lies from the line: +1 toward high y, -1 toward low */
    public CordonArmour(AlliedCommand command, int frontward) {
        this.command = command;
        this.frontward = frontward;
    }

    public AlliedCommand command() { return command; }

    /** Has the armour escort convoys: each convoy's posts want one hull each. */
    public void escortConvoys(Function<BattleSimulation, List<int[][]>> escorts) {
        this.escorts = escorts;
    }

    /** Has the armour escort single hulls on the road (a truck, a dozer): each wants one hull beside it. */
    public void escortHulls(Function<BattleSimulation, List<Long>> hulls) {
        this.hullEscorts = hulls;
    }

    /** Lets a road hull take a walking escort where no hull is free ({@link CordonAlliedRules#walkingEscorts}). */
    public void setWalkingEscorts(boolean walking) {
        this.walkingEscorts = walking;
    }

    /** Sets the cover each manned redoubt keeps, {@link CordonAlliedRules#coverFloor} for the density. */
    public void setCoverFloor(int floor) {
        this.floor = floor;
    }

    /**
     * Holds the hulls to the streets ({@code cordon-allied-command.md}, rule 5: a town's): each flank post is moved to
     * the nearest street cell within {@link #STREET_REACH} its hull fits on, and stays where it was where none is.
     */
    public void setRoadbound(boolean roadbound) {
        this.roadbound = roadbound;
    }

    /**
     * Whether an armed allied hull stood within {@link #ESCORTED_REACH} of a road hull at the last look: the radio's
     * Escorted. Alongside, not merely assigned: an escort still driving to its truck does not make it escorted.
     */
    public boolean escorted(long hull) {
        return escortedHulls.contains(hull);
    }

    /**
     * The hull sent to escort a road hull at the last look, or {@code 0} where none was free: what the road hull keeps
     * to ({@link EscortPace}), and why the company need not walk with it while that escort comes.
     */
    public long escortOf(long roadHull) {
        for (Map.Entry<Long, Long> e : escortOf.entrySet()) if (e.getValue() == roadHull) return e.getKey();
        return 0L;
    }

    /**
     * The escort the convoy whose posts came {@code index}th at the last look was given, {posts wanted, hulls taken}, or
     * {@code null} where no such convoy was asked for. A convoy given fewer hulls than it has posts went short, and the
     * company walks with it ({@link CordonAlliedRules#convoyEscorted}).
     */
    public int[] convoyEscort(int index) {
        return index >= 0 && index < convoyEscorts.size() ? convoyEscorts.get(index).clone() : null;
    }

    /** Each hull escorting a road hull at the last look, and the road hull it is with, for review frames. */
    public Map<Long, Long> roadEscorts() {
        return Map.copyOf(escortOf);
    }

    /** Whether a redoubt's sector had machines and has none left, at the last look (the radio's Uncovered). */
    public boolean uncovered(CordonRedoubt r) {
        return uncoveredNow.contains(r);
    }

    /** Seconds each redoubt has stood uncovered so far, by number. */
    public Map<Integer, Float> uncoveredSeconds() {
        Map<Integer, Float> out = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> e : uncoveredTicks.entrySet()) out.put(e.getKey(), e.getValue() * BattleSimulation.TICK_DT);
        return out;
    }

    /**
     * Escorts for evidence: {@code [road hulls that wanted one, road hulls that ever had one alongside, hull-seconds of
     * escort taken, hull-seconds gone short, road-hull seconds with an escort alongside, road-hull seconds with none]},
     * the third and fourth summed over convoys' posts and road hulls alike.
     */
    public float[] escortReadings() {
        float perLook = REVIEW_TICKS * BattleSimulation.TICK_DT;
        return new float[]{hullsWanting.size(), hullsEscorted.size(), escortLooksTaken * perLook,
                escortLooksShort * perLook, alongsideLooks * perLook, aloneLooks * perLook};
    }

    /**
     * Takes a hull standing on the field under the allied command, with a home it goes back to
     * between calls: a car or technical rolled out of the base's shed.
     */
    public void adopt(BattleSimulation sim, long hull, int homeX, int homeY) {
        command.enlist(sim, hull);
        home.put(hull, new int[]{homeX, homeY});
    }

    /** Sets a hull down at the nearest open ground to a cell; that place is its home between calls. */
    public long deploy(BattleSimulation sim, VehicleType type, int x, int y) {
        int[] cell = drivable(sim, type, x, y);
        if (cell == null) return 0L;
        long hull = command.deploy(sim, type, cell[0], cell[1], frontward > 0 ? 0f : 180f);
        home.put(hull, cell);
        return hull;
    }

    /**
     * The nearest cell to {@code (x, y)} a hull of this type stands clear on and can drive away from,
     * out to {@link #SET_DOWN_REACH}: on the main body of the map's ground for its clearance, not in a
     * pocket. The nearest cell that merely fit put the base's first apron slot inside a walled pen of
     * twenty-odd cells on four maps in seventeen, and every carrier and hull mustered there stood in it
     * all battle.
     */
    int[] drivable(BattleSimulation sim, VehicleType type, int x, int y) {
        return nearest(sim.getGrid(), mainGround(sim, type), x, y, SET_DOWN_REACH);
    }

    /** The map's main body of ground a hull of this type's footprint fits on ({@link VehicleClearance#mainGround(NavigationGrid, int)}). */
    boolean[] mainGround(BattleSimulation sim, VehicleType type) {
        int radius = VehicleClearance.radiusForWidth(type.visualWidthCells);
        return mainGround.computeIfAbsent(radius, r -> VehicleClearance.mainGround(sim.getGrid(), r));
    }

    /** The nearest cell to {@code (x, y)} in {@code ground}, out to {@code reach}, or {@code null}. */
    static int[] nearest(NavigationGrid grid, boolean[] ground, int x, int y, int reach) {
        int w = grid.getWidth();
        for (int r = 0; r <= reach; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int cx = x + dx, cy = y + dy;
                    if (grid.inBounds(cx, cy) && ground[cy * w + cx]) return new int[]{cx, cy};
                }
            }
        }
        return null;
    }

    /**
     * One tick: keeps each hull on the flank of its own redoubt and each mech in its street mouths
     * ({@code cordon-allied-command.md}, The allied line command's rules). A machine without a redoubt, or whose
     * redoubt fell, is given the one {@link CordonAlliedRules#nextPost} names: furthest under its floor, an uncovered
     * one first, then a calling one, then the fewest machines, the nearest breaking a tie. With none standing a hull
     * goes home. Escorts are taken out of the surplus ({@link CordonAlliedRules#escorts}).
     *
     * <p>Sectors, not a reserve that chases calls. On a front hundreds of cells wide a hull sent to
     * whichever redoubt called last spent the battle driving, mostly to calls that had ended by the
     * time it came; one standing beside a redoubt is there when the swarm comes.
     */
    public void advance(BattleSimulation sim, List<CordonRedoubt> redoubts) {
        command.advance(sim);
        if (sim.getSimTickIndex() % REVIEW_TICKS != 0) return;
        List<Long> hulls = new ArrayList<>(command.hulls());
        home.keySet().retainAll(hulls);
        answering.keySet().retainAll(hulls);
        answering.values().removeIf(r -> !r.manned());
        mechSector.keySet().removeIf(m -> sim.resolveUnit(m) == 0L || !sim.world().isAlive(m));
        for (Map.Entry<Long, CordonRedoubt> e : mechSector.entrySet()) {
            if (e.getValue() != null && !e.getValue().manned()) e.setValue(null);
        }
        Map<CordonRedoubt, Integer> count = new HashMap<>();
        for (CordonRedoubt r : answering.values()) count.merge(r, 1, Integer::sum);
        for (CordonRedoubt r : mechSector.values()) if (r != null) count.merge(r, 1, Integer::sum);
        // New machines first, so the floor and the escorts read where they are going.
        for (Map.Entry<Long, CordonRedoubt> e : mechSector.entrySet()) {
            if (e.getValue() != null) continue;
            CordonRedoubt to = nextPost(sim, redoubts, count, sim.world().x(e.getKey()), sim.world().y(e.getKey()));
            e.setValue(to);
            if (to != null) count.merge(to, 1, Integer::sum);
        }
        for (long hull : hulls) {
            if (command.carrying(hull) || !home.containsKey(hull) || answering.containsKey(hull)) continue;
            GroundBody body = sim.convoy().body(hull);
            CordonRedoubt to = nextPost(sim, redoubts, count, body.x, body.y);
            if (to == null) continue;
            answering.put(hull, to);
            count.merge(to, 1, Integer::sum);
        }
        for (CordonRedoubt r : redoubts) {
            CordonAlliedRules.Post post = post(sim, r, count);
            if (post.machines() > 0) everCovered.add(r);
            if (CordonAlliedRules.uncovered(post)) {
                uncoveredNow.add(r);
                uncoveredTicks.merge(r.number(), REVIEW_TICKS, Integer::sum);
            } else {
                uncoveredNow.remove(r);
            }
        }
        Set<Long> escorting = escort(sim, hulls, redoubts, count);
        Map<CordonRedoubt, Integer> rank = new HashMap<>();
        for (Map.Entry<Long, CordonRedoubt> e : mechSector.entrySet()) {
            if (e.getValue() == null) continue;
            int[] post = mouthPost(sim, e.getValue(), rank.merge(e.getValue(), 1, Integer::sum) - 1);
            if (!escorting.contains(e.getKey())) command.stationMech(sim, e.getKey(), post[0], post[1]);
        }
        homeTeams(sim, escorting);
        for (long hull : hulls) {
            if (command.carrying(hull) || !home.containsKey(hull) || escorting.contains(hull)) continue;
            CordonRedoubt sector = answering.get(hull);
            int[] post = sector == null ? home.get(hull) : post(sim, hull, sector, rankIn(hull, sector));
            command.station(sim, hull, post[0], post[1]);
        }
    }

    /** The redoubt a new machine at {@code (x, y)} goes to ({@link CordonAlliedRules#nextPost}), or {@code null}. */
    private CordonRedoubt nextPost(BattleSimulation sim, List<CordonRedoubt> redoubts, Map<CordonRedoubt, Integer> count,
                                  float x, float y) {
        List<CordonRedoubt> byDistance = new ArrayList<>(redoubts);
        // nextPost keeps the first of equals, so the nearest breaks a tie.
        byDistance.sort(Comparator.comparingDouble(r -> Math.hypot(r.centreX() - x, r.centreY() - y)));
        List<CordonAlliedRules.Post> posts = new ArrayList<>();
        for (CordonRedoubt r : byDistance) posts.add(post(sim, r, count));
        int number = CordonAlliedRules.nextPost(posts, floor);
        for (CordonRedoubt r : redoubts) if (r.number() == number) return r;
        return null;
    }

    /** A redoubt as the allied line command's rules see it, with {@code count} machines in its sector. */
    private CordonAlliedRules.Post post(BattleSimulation sim, CordonRedoubt r, Map<CordonRedoubt, Integer> count) {
        int machines = count.getOrDefault(r, 0);
        return new CordonAlliedRules.Post(r.number(), r.manned(), r.state() == CordonRedoubt.State.CALLING,
                CordonSequence.standing(sim, r), machines, machines > 0 || everCovered.contains(r));
    }

    /**
     * Sends escorts out of the surplus ({@code cordon-allied-command.md}, rule 3): a convoy's posts first, then each
     * road hull, each taking the nearest hulls above their sector's floor, at most one from any one sector and never
     * a calling redoubt's last ({@link CordonAlliedRules#escorts}). What it cannot take that way it goes without.
     *
     * @return the hulls sent
     */
    private Set<Long> escort(BattleSimulation sim, List<Long> hulls, List<CordonRedoubt> redoubts,
                             Map<CordonRedoubt, Integer> count) {
        Set<Long> sent = new HashSet<>();
        escortedHulls.clear();
        // A road hull keeps its escort while that one is still free to take: re-picked every look, the nearest
        // changed hands as hulls moved, and the road hull waited or held for each new one in turn.
        Map<Long, Long> kept = new HashMap<>();
        for (Map.Entry<Long, Long> e : escortOf.entrySet()) kept.put(e.getValue(), e.getKey());
        escortOf.clear();
        convoyEscorts.clear();
        if (escorts == null && hullEscorts == null) return sent;
        List<int[][]> needs = new ArrayList<>(escorts == null ? List.of() : escorts.apply(sim));
        int convoyNeeds = needs.size();
        List<Long> roadHulls = hullEscorts == null ? List.of() : hullEscorts.apply(sim);
        escortSide.keySet().retainAll(roadHulls);
        for (long h : roadHulls) needs.add(new int[][]{escortPlace(sim, h)});
        Map<CordonRedoubt, Integer> left = new HashMap<>(count);
        for (int n = 0; n < needs.size(); n++) {
            int[][] posts = needs.get(n);
            if (posts.length == 0) {
                if (n < convoyNeeds) convoyEscorts.add(new int[]{0, 0});
                continue;
            }
            List<CordonAlliedRules.Escort> free = new ArrayList<>();
            for (long h : hulls) {
                if (command.carrying(h) || !home.containsKey(h) || sent.contains(h) || !sim.convoy().isArmed(h)
                        || benched(sim, h)) continue;
                CordonRedoubt sector = answering.get(h);
                GroundBody body = sim.convoy().body(h);
                free.add(new CordonAlliedRules.Escort(h, body.x, body.y, sector == null ? -1 : sector.number()));
            }
            long wanting = n >= convoyNeeds ? roadHulls.get(n - convoyNeeds) : 0L;
            // A city fields no hulls: its road hulls take a walking escort out of the same surplus.
            List<CordonAlliedRules.Escort> walkers = wanting != 0L && walkingEscorts ? walkers(sim, sent) : List.of();
            List<CordonAlliedRules.Post> lines = new ArrayList<>();
            for (CordonRedoubt r : redoubts) lines.add(post(sim, r, left));
            List<CordonAlliedRules.Escort> got = List.of();
            long had = kept.getOrDefault(wanting, 0L);
            for (List<CordonAlliedRules.Escort> pool : List.of(free, walkers)) {
                for (CordonAlliedRules.Escort h : pool) {
                    if (wanting != 0L && h.id() == had) got = CordonAlliedRules.escorts(posts[0][0], posts[0][1], 1,
                            List.of(h), lines, floor);
                }
            }
            if (got.isEmpty()) {
                got = CordonAlliedRules.escorts(posts[0][0], posts[0][1], posts.length, free, lines, floor);
            }
            if (got.isEmpty() && !walkers.isEmpty()) {
                // Hulls first: a walker holds its road hull to a walker's pace.
                got = CordonAlliedRules.escorts(posts[0][0], posts[0][1], posts.length, walkers, lines, floor);
            }
            escortLooksTaken += got.size();
            escortLooksShort += posts.length - got.size();
            if (n < convoyNeeds) convoyEscorts.add(new int[]{posts.length, got.size()});
            if (wanting != 0L) hullsWanting.add(wanting);
            List<CordonAlliedRules.Escort> unplaced = new ArrayList<>(got);
            for (int[] post : posts) {
                if (unplaced.isEmpty()) break;
                CordonAlliedRules.Escort nearest = unplaced.get(0);
                for (CordonAlliedRules.Escort h : unplaced) {
                    if (Math.hypot(h.x() - post[0], h.y() - post[1])
                            < Math.hypot(nearest.x() - post[0], nearest.y() - post[1])) nearest = h;
                }
                unplaced.remove(nearest);
                send(sim, nearest, post);
                sent.add(nearest.id());
                if (wanting != 0L) escortOf.put(nearest.id(), wanting);
                CordonRedoubt sector = sectorOf(nearest);
                if (sector != null) left.merge(sector, -nearest.kind().cover(), Integer::sum);
            }
        }
        closing(sim);
        for (long h : roadHulls) {
            if (alongside(sim, h)) {
                escortedHulls.add(h);
                hullsEscorted.add(h);
                alongsideLooks++;
            } else {
                aloneLooks++;
            }
        }
        return sent;
    }

    /** Whether an escort was benched for not closing on its road hull and may not be sent again yet. */
    private boolean benched(BattleSimulation sim, long id) {
        return benched.getOrDefault(id, 0) > sim.getSimTickIndex();
    }

    /**
     * The walking escorts free to take: each sector's mechs and each street mouth's rifle team (by its leader), not
     * sent this look nor benched.
     */
    private List<CordonAlliedRules.Escort> walkers(BattleSimulation sim, Set<Long> sent) {
        List<CordonAlliedRules.Escort> out = new ArrayList<>();
        for (Map.Entry<Long, CordonRedoubt> e : mechSector.entrySet()) {
            long mech = e.getKey();
            if (e.getValue() == null || sent.contains(mech) || benched(sim, mech) || !unitAlive(sim, mech)) continue;
            out.add(new CordonAlliedRules.Escort(mech, sim.world().x(mech), sim.world().y(mech), e.getValue().number(),
                    CordonAlliedRules.Kind.MECH));
        }
        for (StreetTeam t : streetTeams) {
            long leader = leader(sim, t);
            if (leader == 0L || !t.redoubt().manned() || sent.contains(leader) || benched(sim, leader)) continue;
            out.add(new CordonAlliedRules.Escort(leader, sim.world().x(leader), sim.world().y(leader),
                    t.redoubt().number(), CordonAlliedRules.Kind.TEAM));
        }
        return out;
    }

    /** Sends an escort to its place beside a road hull or at a convoy's post: a hull drives, a mech walks, a team escorts. */
    private void send(BattleSimulation sim, CordonAlliedRules.Escort escort, int[] post) {
        switch (escort.kind()) {
            case HULL -> {
                int[] cell = drivable(sim, sim.convoy().vehicleType(escort.id()), post[0], post[1]);
                if (cell == null) cell = post;
                command.station(sim, escort.id(), cell[0], cell[1]);
            }
            case MECH -> command.stationMech(sim, escort.id(), post[0], post[1]);
            case TEAM -> {
                StreetTeam team = teamOf(sim, escort.id());
                if (team == null) return;
                teamsOut.add(team.squad());
                sim.assignSquadCommand(ObjectiveAssignment.escort(team.squad(), post[0], post[1]),
                        CommandAuthority.GARRISON, CordonGarrisons.ISSUER, "street team escorts a truck");
            }
        }
    }

    /** The redoubt whose sector an escort holds, or {@code null}. */
    private CordonRedoubt sectorOf(CordonAlliedRules.Escort escort) {
        return switch (escort.kind()) {
            case HULL -> answering.get(escort.id());
            case MECH -> mechSector.get(escort.id());
            case TEAM -> {
                CordonRedoubt out = null;
                for (StreetTeam t : streetTeams) if (t.redoubt().number() == escort.sector()) out = t.redoubt();
                yield out;
            }
        };
    }

    /** Sends back to its street mouth every rifle team that went to escort and is not escorting now. */
    private void homeTeams(BattleSimulation sim, Set<Long> escorting) {
        for (StreetTeam t : streetTeams) {
            if (!teamsOut.contains(t.squad())) continue;
            long leader = leader(sim, t);
            if (leader != 0L && escorting.contains(leader)) continue;
            teamsOut.remove(t.squad());
            if (leader == 0L) continue;
            sim.assignSquadCommand(ObjectiveAssignment.defendArea(t.squad(), t.x(), t.y(), t.radius()),
                    CommandAuthority.GARRISON, CordonGarrisons.ISSUER, "street team back to its mouth");
        }
    }

    /** A street team's leader, or {@code 0} where the team is gone. */
    private static long leader(BattleSimulation sim, StreetTeam t) {
        Squad squad = sim.getSquad(t.squad());
        if (squad == null || squad.leaderId == 0L || !unitAlive(sim, squad.leaderId)) return 0L;
        return squad.leaderId;
    }

    /** The street team a leader leads, or {@code null}. */
    private StreetTeam teamOf(BattleSimulation sim, long leader) {
        for (StreetTeam t : streetTeams) if (leader(sim, t) == leader) return t;
        return null;
    }

    private static boolean unitAlive(BattleSimulation sim, long unit) {
        return sim.resolveUnit(unit) != 0L && sim.world().isAlive(unit);
    }

    /** Where an escort stands: a hull's body, or a walker's place. */
    private static float[] at(BattleSimulation sim, long id) {
        if (sim.convoy().isVehicle(id)) return new float[]{sim.convoy().body(id).x, sim.convoy().body(id).y};
        return new float[]{sim.world().x(id), sim.world().y(id)};
    }

    /**
     * Benches an escort that is not closing on its road hull: still farther than {@link EscortPace#ALONGSIDE} and no
     * {@link #ESCORT_PROGRESS} cells nearer in {@link #ESCORT_STALL_TICKS}. It goes back to its sector for
     * {@link #ESCORT_BENCH_TICKS}, and the road hull is given the next one, or none. A hull boxed in at the base's door
     * was the nearest free one at every look, and its truck waited for it.
     */
    private void closing(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        closing.keySet().retainAll(escortOf.keySet());
        for (Map.Entry<Long, Long> e : escortOf.entrySet()) {
            long escort = e.getKey(), road = e.getValue();
            GroundBody b = sim.convoy().body(road);
            float[] a = at(sim, escort);
            float d = b.distanceTo(a[0], a[1]);
            Closing c = closing.get(escort);
            if (c == null || c.road != road) {
                closing.put(escort, new Closing(road, d, tick));
            } else if (d <= EscortPace.ALONGSIDE || d < c.nearest - ESCORT_PROGRESS) {
                c.nearest = d;
                c.since = tick;
            } else if (tick - c.since >= ESCORT_STALL_TICKS) {
                benched.put(escort, tick + ESCORT_BENCH_TICKS);
                closing.remove(escort);
            }
        }
    }

    /** An escort on its way to a road hull: the nearest it has come, and when. */
    private static final class Closing {
        final long road;
        float nearest;
        int since;

        Closing(long road, float nearest, int since) {
            this.road = road;
            this.nearest = nearest;
            this.since = since;
        }
    }

    /** Cells nearer its road hull an escort must come to count as closing. */
    static final float ESCORT_PROGRESS = 4f;
    /** Ticks an escort may go without closing on its road hull before it is benched. */
    static final int ESCORT_STALL_TICKS = Math.round(20f / BattleSimulation.TICK_DT);
    /** Ticks a benched escort is not sent to escort again. */
    static final int ESCORT_BENCH_TICKS = Math.round(120f / BattleSimulation.TICK_DT);

    /**
     * Whether an armed allied unit stands within {@link #ESCORTED_REACH} of a road hull: an armed hull, or one of the
     * walkers the line escorts with (a mech, a street team's leader).
     */
    private boolean alongside(BattleSimulation sim, long roadHull) {
        GroundBody body = sim.convoy().body(roadHull);
        for (int i = 0, n = sim.convoy().vehicleCount(); i < n; i++) {
            long hull = sim.convoy().vehicleAt(i);
            if (hull == roadHull || sim.convoy().faction(hull) != Faction.ALLY || !sim.convoy().isArmed(hull)
                    || sim.convoy().structure(hull) <= 0f) continue;
            GroundBody other = sim.convoy().body(hull);
            if (body.distanceTo(other.x, other.y) <= ESCORTED_REACH) return true;
        }
        for (long mech : mechSector.keySet()) {
            if (unitAlive(sim, mech)
                    && body.distanceTo(sim.world().x(mech), sim.world().y(mech)) <= ESCORTED_REACH) return true;
        }
        for (StreetTeam t : streetTeams) {
            long leader = leader(sim, t);
            if (leader != 0L
                    && body.distanceTo(sim.world().x(leader), sim.world().y(leader)) <= ESCORTED_REACH) return true;
        }
        return false;
    }

    /**
     * Where a road hull's escort keeps: beside it ({@link #besidePoint}), on its right, else its left, where the ground the
     * escort would stand on there is off the road hull's way; else {@link #ESCORT_TRAIL} cells behind it. In a lane
     * too narrow for two abreast the ground nearest the place beside it was the lane ahead, and the escort stood
     * across the way it was escorting (town Line rock 8: the ammunition truck was refused its way past its own escort
     * from tick 10200 to the end, and redoubt 3's guns stayed dry). The escort keeps the side it took while that
     * side still has room: tried right first at every look, the place swapped sides as the hull crept along a lane
     * whose right came and went, and the escort turned about for each swap and fell behind.
     */
    private int[] escortPlace(BattleSimulation sim, long roadHull) {
        GroundBody body = sim.convoy().body(roadHull);
        VehicleType type = sim.convoy().vehicleType(roadHull);
        VehicleMoveOrderService.ActiveOrder order = sim.getVehicleMoveOrderService().activeOrder(roadHull);
        int[] place = escortPlace(body, order == null ? null : order.routeXs(), order == null ? null : order.routeYs(),
                p -> drivable(sim, type, p[0], p[1]), escortSide.getOrDefault(roadHull, 1));
        escortSide.put(roadHull, place[2]);
        return new int[]{place[0], place[1]};
    }

    /**
     * {@link #escortPlace(BattleSimulation, long)} given the road hull's way ({@code null} where it has none yet: the
     * way is then the hull's heading) and the ground an escort sent to a place would stand on ({@code null} for none),
     * trying {@code side} first (1 the right, -1 the left, 0 the right as for a new escort). Returns the place's cell
     * and the side it is on, 0 for behind.
     */
    static int[] escortPlace(GroundBody body, float[] wayX, float[] wayY, UnaryOperator<int[]> ground, int side) {
        int first = side == -1 ? -1 : 1;
        for (int s : new int[]{first, -first}) {
            double[] at = besidePoint(body, s);
            int[] place = {(int) Math.round(at[0]), (int) Math.round(at[1])};
            int[] stand = ground.apply(place);
            if (stand == null) continue;
            // Where the ground takes the place itself the escort stands there; elsewhere at the cell it is moved to.
            boolean kept = stand[0] == place[0] && stand[1] == place[1];
            float x = kept ? (float) at[0] : stand[0] + .5f, y = kept ? (float) at[1] : stand[1] + .5f;
            if (offWay(body, wayX, wayY, x, y)) return new int[]{place[0], place[1], s};
        }
        double rad = Math.toRadians(body.facingDegrees);
        return new int[]{(int) Math.round(body.x + Math.sin(rad) * ESCORT_TRAIL),
                (int) Math.round(body.y - Math.cos(rad) * ESCORT_TRAIL), 0};
    }

    /** Whether {@code (x, y)} stands more than {@link #ESCORT_CLEAR} cells off a road hull's way. */
    static boolean offWay(GroundBody body, float[] wayX, float[] wayY, float x, float y) {
        if (wayX == null || wayX.length < 2) {
            double rad = Math.toRadians(body.facingDegrees);
            wayX = new float[]{body.x, (float) (body.x - Math.sin(rad) * ESCORT_WAY_AHEAD)};
            wayY = new float[]{body.y, (float) (body.y + Math.cos(rad) * ESCORT_WAY_AHEAD)};
        }
        for (int i = 1; i < wayX.length; i++) {
            float ax = wayX[i - 1], ay = wayY[i - 1], dx = wayX[i] - ax, dy = wayY[i] - ay;
            float length2 = dx * dx + dy * dy;
            float t = length2 < 1e-6f ? 0f : Math.max(0f, Math.min(1f, ((x - ax) * dx + (y - ay) * dy) / length2));
            if (Math.hypot(x - (ax + t * dx), y - (ay + t * dy)) <= ESCORT_CLEAR) return false;
        }
        return true;
    }

    /**
     * The point {@link #ESCORT_BESIDE} cells to a road hull's right ({@code side} 1) or left ({@code -1}) and
     * {@link #ESCORT_LEAD} ahead, by its heading. Beside it as it turns, rather than a fixed step east of it, which
     * put the escort ahead of a truck driving east and behind one driving west.
     */
    static double[] besidePoint(GroundBody body, int side) {
        double rad = Math.toRadians(body.facingDegrees);
        // Forward is (-sin, cos) for a hull's facing; its right is (cos, sin).
        double fx = -Math.sin(rad), fy = Math.cos(rad);
        return new double[]{body.x + side * Math.cos(rad) * ESCORT_BESIDE + fx * ESCORT_LEAD,
                body.y + side * Math.sin(rad) * ESCORT_BESIDE + fy * ESCORT_LEAD};
    }

    /** Cells to the side of a road hull its escort keeps. */
    static final int ESCORT_BESIDE = 4;
    /** Cells ahead of a road hull its escort keeps, so it closes up rather than trailing. */
    static final int ESCORT_LEAD = 2;
    /** Cells behind a road hull its escort keeps where neither side has room off its way. */
    static final int ESCORT_TRAIL = 6;
    /**
     * Cells off a road hull's way its escort must stand: half an escort's length (it may stand at any heading), half
     * the road hull's width, and a cell between.
     */
    static final float ESCORT_CLEAR = 3.5f;
    /** Cells of a road hull's heading taken as its way while it has no route yet. */
    static final float ESCORT_WAY_AHEAD = 12f;
    /** Cells from a road hull an armed allied hull is alongside it: the radio's Escorted. */
    static final float ESCORTED_REACH = EscortPace.FALLEN_BEHIND;

    /**
     * Takes an allied mech standing on the field under the allied command (one the base's shed has finished): it is
     * given the standing redoubt with the fewest mechs at the next look and stands in its street mouths.
     */
    public void adoptMech(BattleSimulation sim, long mech) {
        command.enlistMech(sim, mech);
        mechSector.put(mech, null);
    }

    /**
     * Sends up to {@code count} new hulls of {@code type} out from the base's dispatch cell to a redoubt's sector, which
     * is theirs from now on (a request's armour pair). Returns those set down: none where the ground there is taken.
     */
    public List<Long> sendHulls(BattleSimulation sim, VehicleType type, int count, int[] from, CordonRedoubt to) {
        List<Long> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int[] cell = drivable(sim, type, from[0] + (i - (count - 1) / 2) * SEND_SPACING, from[1]);
            if (cell == null || crowded(sim, cell)) continue;
            long hull = command.deploy(sim, type, cell[0], cell[1], frontward > 0 ? 0f : 180f);
            home.put(hull, behind(to));
            answering.put(hull, to);
            int[] post = post(sim, hull, to, rankIn(hull, to));
            command.station(sim, hull, post[0], post[1]);
            out.add(hull);
        }
        return out;
    }

    /** Sends up to {@code count} new Hounds walking out from the base's dispatch cell to a redoubt's street mouths. */
    public List<Long> sendMechs(BattleSimulation sim, int count, int[] from, CordonRedoubt to, String name) {
        List<Long> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long mech = command.deployMech(sim, MechVariant.HOUND, from[0] + i * SEND_SPACING, from[1], name + " " + (i + 1));
            if (mech == 0L) continue;
            mechSector.put(mech, to);
            int[] post = mouthPost(sim, to, mechsAt(to) - 1);
            command.stationMech(sim, mech, post[0], post[1]);
            out.add(mech);
        }
        return out;
    }

    /** Whether a machine sent to a redoubt is gone or has reached its sector ({@link #SECTOR_REACH} cells of its wall). */
    public boolean arrivedOrGone(BattleSimulation sim, long machine, CordonRedoubt to) {
        float x, y;
        if (sim.convoy().isVehicle(machine)) {
            if (sim.convoy().structure(machine) <= 0f) return true;
            x = sim.convoy().body(machine).x;
            y = sim.convoy().body(machine).y;
        } else {
            if (sim.resolveUnit(machine) == 0L || !sim.world().isAlive(machine)) return true;
            x = sim.world().x(machine);
            y = sim.world().y(machine);
        }
        return !to.manned() || to.contains((int) x, (int) y, SECTOR_REACH);
    }

    /** Cells between machines of one request, set down side by side. */
    static final int SEND_SPACING = 5;
    /** Cells from a redoubt's wall a machine sent to it has arrived in its sector. */
    static final int SECTOR_REACH = 16;

    /** Whether another hull stands too near this cell to set one down on it. */
    private static boolean crowded(BattleSimulation sim, int[] at) {
        for (long other : sim.convoy().entityIds()) {
            if (sim.convoy().structure(other) <= 0f) continue;
            if (sim.convoy().body(other).distanceTo(at[0] + .5f, at[1] + .5f) < SEND_SPACING - 1) return true;
        }
        return false;
    }

    /** Sets a new Hound down in a redoubt's street mouths: that redoubt is its sector. Returns 0 where no ground near takes one. */
    public long deployMech(BattleSimulation sim, CordonRedoubt redoubt, String name) {
        int[] post = mouthPost(sim, redoubt, mechsAt(redoubt));
        long mech = command.deployMech(sim, MechVariant.HOUND, post[0], post[1], name);
        if (mech == 0L) return 0L;
        mechSector.put(mech, redoubt);
        command.stationMech(sim, mech, post[0], post[1]);
        return mech;
    }

    /**
     * Records a rifle team standing in a redoubt's street mouths behind its mechs (set down at setup), holding
     * {@code radius} round {@code (x, y)}: where it goes back to after escorting a truck.
     */
    public void addStreetTeam(int squad, CordonRedoubt redoubt, int x, int y, int radius) {
        streetTeams.add(new StreetTeam(squad, redoubt, x, y, radius));
    }

    /** The rifle teams standing in the redoubts' street mouths, by squad. */
    public List<Integer> streetTeams() {
        List<Integer> out = new ArrayList<>();
        for (StreetTeam t : streetTeams) out.add(t.squad());
        return out;
    }

    /** A street mouth's rifle team: its redoubt and the ground it holds there. */
    private record StreetTeam(int squad, CordonRedoubt redoubt, int x, int y, int radius) { }

    /** Records a mortar team standing in a redoubt's yard (set down at setup). */
    public void addMortarTeam(int squad) {
        mortarTeams.add(squad);
    }

    /** The mortar teams in the redoubts' yards, by squad. */
    public List<Integer> mortarTeams() {
        return List.copyOf(mortarTeams);
    }

    /**
     * The ground a redoubt's mortar team is given as its standing fire mission: centred {@link #MISSION_OUT} cells in
     * front of the front wall, past the street mouths, {@link #MISSION_RADIUS} across.
     */
    public int[] mortarGround(CordonRedoubt r) {
        return new int[]{r.centreX(), (frontward > 0 ? r.bottom() : r.top()) + frontward * MISSION_OUT};
    }

    /** Live allied mechs on the line, in the order they came. */
    public List<Long> mechs() {
        return new ArrayList<>(mechSector.keySet());
    }

    private int mechsAt(CordonRedoubt r) {
        int n = 0;
        for (CordonRedoubt at : mechSector.values()) if (at == r) n++;
        return n;
    }

    /**
     * The {@code rank}th mech's place in front of a redoubt: one to each street mouth, nearest the redoubt first,
     * then the next beside it ({@link #across}).
     */
    int[] mouthPost(BattleSimulation sim, CordonRedoubt r, int rank) {
        List<int[]> mouths = mouths(sim, r);
        int[] mouth = mouths.get(rank % mouths.size());
        return new int[]{mouth[0] + across(rank / mouths.size(), mouths.size() == 1), mouth[1]};
    }

    /**
     * Cells across its mouth the {@code k}th mech in it stands from the mouth's middle. A mouth that is the redoubt's
     * only one takes its pair either side of the middle; otherwise the first stands in the middle and later ones
     * either side of it.
     */
    static int across(int k, boolean only) {
        if (only) return (k % 2 == 0 ? -1 : 1) * (MOUTH_PAIR / 2 + (k / 2) * MOUTH_PAIR);
        return k == 0 ? 0 : (k % 2 == 1 ? 1 : -1) * MOUTH_PAIR * ((k + 1) / 2);
    }

    /** Where a mouth's rifle team stands: behind the redoubt's first two mouths, between them, {@link #TEAM_OUT} cells out. */
    public int[] teamPost(BattleSimulation sim, CordonRedoubt r) {
        List<int[]> mouths = mouths(sim, r);
        int x = mouths.size() > 1 ? (mouths.get(0)[0] + mouths.get(1)[0]) / 2 : mouths.get(0)[0];
        return new int[]{x, (frontward > 0 ? r.bottom() : r.top()) + frontward * TEAM_OUT};
    }

    /**
     * A redoubt's street mouths: where the streets leave the ground in front of it toward the front, read along the
     * row {@link #MOUTH_OUT} cells in front of its front wall, nearest the redoubt's middle first; any open ground
     * where no street crosses that row, and the front wall's middle where nothing is open.
     */
    public List<int[]> mouths(BattleSimulation sim, CordonRedoubt r) {
        return mouthsOf.computeIfAbsent(r, k -> {
            NavigationGrid grid = sim.getGrid();
            int row = (frontward > 0 ? r.bottom() : r.top()) + frontward * MOUTH_OUT;
            int x0 = r.left() - MOUTH_REACH;
            boolean[] street = new boolean[r.right() - r.left() + 1 + 2 * MOUTH_REACH];
            boolean[] open = new boolean[street.length];
            for (int i = 0; i < street.length; i++) {
                int x = x0 + i;
                if (!grid.inBounds(x, row) || !grid.isWalkable(x, row)) continue;
                open[i] = true;
                CellTopology.GroundKind kind = sim.getTopology().getGroundKind(x, row);
                street[i] = kind == CellTopology.GroundKind.STREET || kind == CellTopology.GroundKind.SIDEWALK;
            }
            List<Integer> at = mouths(street, r.centreX() - x0, MOUTH_WIDEST);
            if (at.isEmpty()) at = mouths(open, r.centreX() - x0, MOUTH_WIDEST);
            List<int[]> out = new ArrayList<>();
            for (int i : at) out.add(new int[]{x0 + i, row});
            if (out.isEmpty()) out.add(new int[]{r.centreX(), row});
            return out;
        });
    }

    /**
     * The mouths along one row of ground: the middle of each run of open cells, or, for a run wider than
     * {@code widest} (open country rather than a street), its cell nearest {@code centre}; nearest {@code centre}
     * first, the lower index first between two as near.
     */
    static List<Integer> mouths(boolean[] open, int centre, int widest) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < open.length; ) {
            if (!open[i]) {
                i++;
                continue;
            }
            int a = i;
            while (i < open.length && open[i]) i++;
            int b = i - 1;
            out.add(b - a + 1 <= widest ? (a + b) / 2 : Math.max(a + 2, Math.min(b - 2, centre)));
        }
        out.sort(Comparator.comparingInt((Integer p) -> Math.abs(p - centre)).thenComparingInt(p -> p));
        return out;
    }

    /**
     * Where a roadbound carrier sets down at a redoubt and waits ({@code cordon-allied-forces.md}, Town): outside its
     * rear gate, the gap in its rear wall nearest the wall's middle, as far back as {@link #behind} sets down. Behind
     * the rear wall's middle where the wall has no gate.
     */
    public int[] rearGate(BattleSimulation sim, CordonRedoubt r) {
        return gateOf.computeIfAbsent(r, k -> {
            CellTopology topology = sim.getTopology();
            int rear = frontward > 0 ? r.top() : r.bottom();
            boolean[] wall = new boolean[r.right() - r.left() + 3];
            for (int i = 0; i < wall.length; i++) {
                int x = r.left() - 1 + i;
                // The rear wall's band: a row inside the ground to two outside it.
                for (int d = -1; d <= 2 && !wall[i]; d++) {
                    int y = rear - frontward * d;
                    // A compound's rear wall is its perimeter; a redoubt built from what stood there is walled by
                    // its buildings and whatever its builders raised between them.
                    wall[i] = topology.isWall(x, y) && (!r.construction().generated()
                            || InstallationWalls.PERIMETER.equals(topology.getSurfaceId(x, y)));
                }
            }
            int gate = gateAlong(wall, r.centreX() - (r.left() - 1));
            int[] behind = behind(r);
            return gate < 0 ? behind : new int[]{r.left() - 1 + gate, behind[1]};
        });
    }

    /**
     * The gate along a wall: the middle of the gap between wall cells nearest {@code centre}, or -1 where the wall
     * runs unbroken. A gap open at either end is not a gate: the wall has not begun or has ended there.
     */
    static int gateAlong(boolean[] wall, int centre) {
        int best = -1;
        for (int i = 0; i < wall.length; ) {
            if (wall[i]) {
                i++;
                continue;
            }
            int a = i;
            while (i < wall.length && !wall[i]) i++;
            int b = i - 1;
            if (a == 0 || b == wall.length - 1) continue;
            int mid = (a + b) / 2;
            if (best < 0 || Math.abs(mid - centre) < Math.abs(best - centre)) best = mid;
        }
        return best;
    }

    /** A hull's place among its sector's hulls, in the order they were enlisted. */
    private int rankIn(long hull, CordonRedoubt sector) {
        int rank = 0;
        for (Map.Entry<Long, CordonRedoubt> e : answering.entrySet()) {
            if (e.getKey() == hull) return rank;
            if (e.getValue() == sector) rank++;
        }
        return rank;
    }

    static CordonRedoubt nearestCalling(GroundBody body, List<CordonRedoubt> redoubts) {
        CordonRedoubt best = null;
        float bestDistance = Float.MAX_VALUE;
        for (CordonRedoubt r : redoubts) {
            if (r.state() != CordonRedoubt.State.CALLING) continue;
            float d = body.distanceTo(r.centreX(), r.centreY());
            if (d < bestDistance) {
                bestDistance = d;
                best = r;
            }
        }
        return best;
    }

    /**
     * Where a hull holds its sector: the {@code rank}th place on the redoubt's flanks ({@link #flank}), moved onto the
     * nearest street its footprint fits where hulls are roadbound. Roadbound, the sector's first hull stands on the street
     * through or past the redoubt instead ({@link #throughStreet}, {@code cordon-allied-forces.md}, Town), and the
     * others take the flanks in turn; where no street runs within reach of the redoubt it takes a flank too.
     */
    int[] post(BattleSimulation sim, long hull, CordonRedoubt r, int rank) {
        if (!roadbound) return flank(r, rank);
        VehicleType type = sim.convoy().vehicleType(hull);
        if (type == null) return flank(r, rank);
        int radius = VehicleClearance.radiusForWidth(type.visualWidthCells);
        boolean[] street = streetGround.computeIfAbsent(radius, k -> {
            boolean[] main = mainGround.computeIfAbsent(k, m -> VehicleClearance.mainGround(sim.getGrid(), m));
            return streets(sim.getTopology(), main, sim.getGrid().getWidth(), sim.getGrid().getHeight());
        });
        int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
        int[] through = throughStreet(street, w, h, r.left(), r.top(), r.right(), r.bottom(), THROUGH_REACH);
        int flankRank = through == null ? rank : rank - 1;
        if (flankRank < 0) return through;
        int[] flank = flank(r, flankRank);
        int[] cell = nearest(sim.getGrid(), street, flank[0], flank[1], STREET_REACH);
        return cell == null ? flank : cell;
    }

    /**
     * The street through or past a redoubt, where a town's first hull in its sector stands: the cell of {@code street}
     * nearest the redoubt's middle within its box grown by {@code reach} on every side, the first in row order between
     * two as near; {@code null} where none is.
     */
    static int[] throughStreet(boolean[] street, int width, int height, int left, int top, int right, int bottom,
                               int reach) {
        int cx = (left + right) / 2, cy = (top + bottom) / 2;
        int[] best = null;
        long bestD = Long.MAX_VALUE;
        for (int y = Math.max(0, top - reach); y <= Math.min(height - 1, bottom + reach); y++) {
            for (int x = Math.max(0, left - reach); x <= Math.min(width - 1, right + reach); x++) {
                if (!street[y * width + x]) continue;
                long d = (long) (x - cx) * (x - cx) + (long) (y - cy) * (y - cy);
                if (d < bestD) {
                    bestD = d;
                    best = new int[]{x, y};
                }
            }
        }
        return best;
    }

    /** Cells past a redoubt's walls a street runs within to count as running past it. */
    static final int THROUGH_REACH = 12;

    /** The cells of {@code ground} that are street. */
    static boolean[] streets(CellTopology topology, boolean[] ground, int width, int height) {
        boolean[] out = new boolean[ground.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                out[y * width + x] = ground[y * width + x]
                        && topology.getGroundKind(x, y) == CellTopology.GroundKind.STREET;
            }
        }
        return out;
    }

    /** Cells from its flank place a roadbound hull's post may be moved to reach a street. */
    static final int STREET_REACH = 16;

    /**
     * The {@code rank}th place on a redoubt's flanks: out past one side wall, a few cells back from
     * the front, the sides taken in turn and each pair a step further back.
     */
    int[] flank(CordonRedoubt r, int rank) {
        int x = rank % 2 == 0 ? r.left() - FLANK_OUT : r.right() + FLANK_OUT;
        int front = frontward > 0 ? r.bottom() : r.top();
        int y = front - frontward * (FLANK_BACK + (rank / 2) * FLANK_STEP);
        return new int[]{x, y};
    }

    /** Where to set a squad down behind a redoubt: its rear wall's middle, a few cells back. */
    int[] behind(CordonRedoubt r) {
        int y = frontward > 0 ? r.top() - BEHIND_WALL : r.bottom() + BEHIND_WALL;
        return new int[]{r.centreX(), y};
    }
}
