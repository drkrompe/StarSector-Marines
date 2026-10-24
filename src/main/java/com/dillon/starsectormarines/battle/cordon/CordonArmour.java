package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.allied.AlliedCommand;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationWalls;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The allies' light armour on the line ({@code cordon-nouns.md}, Allied armour): cars and
 * technicals under the allied command, each given a redoubt of its own to fight beside.
 *
 * <p>A hull stands beside its redoubt, out past a side wall and a few cells back from the front:
 * from there its gun reaches across the ground in front of the wall. Behind the rear wall it was out
 * of reach of the front (a redoubt is deeper than a heavy machine gun's range) and shot at nothing.
 * Several on one redoubt take turns on the two sides, each a step further back.
 *
 * <p>On the Road the armour also escorts the convoys: while a convoy is on the road near or past the
 * gap, the hulls nearest the place it wants its escort leave their sectors and drive there, a few
 * cells ahead of its lead truck, and go back to their sectors when it is out.
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
    /** The rifle teams in the redoubts' street mouths. */
    private final List<Integer> streetTeams = new ArrayList<>();
    /** The mortar teams in the redoubts' yards. */
    private final List<Integer> mortarTeams = new ArrayList<>();
    /** Each redoubt's street mouths, found once at the first look ({@link #mouths}). */
    private final Map<CordonRedoubt, List<int[]>> mouthsOf = new HashMap<>();
    /** Each redoubt's rear gate drop, found once ({@link #rearGate}). */
    private final Map<CordonRedoubt, int[]> gateOf = new HashMap<>();
    /** Each convoy's escort posts, or {@code null} off the Road. */
    private Function<BattleSimulation, List<int[][]>> escorts;

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
        int radius = VehicleClearance.radiusForWidth(type.visualWidthCells);
        boolean[] main = mainGround.computeIfAbsent(radius, r -> mainGround(sim.getGrid(), r));
        return nearest(sim.getGrid(), main, x, y, SET_DOWN_REACH);
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
     * The largest connected body of ground a footprint of {@code radius} fits on, stepping as the
     * route planner steps (no closed edge crossed, no corner cut). Found once a battle per radius, at
     * setup: walls breached later only add ground, so a cell in it stays in it.
     */
    static boolean[] mainGround(NavigationGrid grid, int radius) {
        VehicleClearance mask = VehicleClearance.erode(grid, radius);
        int w = grid.getWidth(), h = grid.getHeight();
        int[] label = new int[w * h];
        int bestLabel = 0, bestSize = 0, next = 0;
        ArrayDeque<Integer> open = new ArrayDeque<>();
        for (int start = 0; start < w * h; start++) {
            if (label[start] != 0 || !mask.isPassable(start % w, start / w)) continue;
            int id = ++next, size = 0;
            label[start] = id;
            open.add(start);
            while (!open.isEmpty()) {
                int i = open.poll(), x = i % w, y = i / w;
                size++;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if ((dx | dy) == 0 || !mask.isPassable(nx, ny) || label[ny * w + nx] != 0
                                || !grid.canTraverseCellStep(x, y, nx, ny)) continue;
                        label[ny * w + nx] = id;
                        open.add(ny * w + nx);
                    }
                }
            }
            if (size > bestSize) {
                bestSize = size;
                bestLabel = id;
            }
        }
        boolean[] main = new boolean[w * h];
        for (int i = 0; i < w * h; i++) main[i] = bestLabel != 0 && label[i] == bestLabel;
        return main;
    }

    /**
     * One tick: keeps each hull on the flank of its own redoubt. A hull without one, or whose
     * redoubt fell, is given the standing redoubt with the fewest hulls (a calling one first, then the
     * nearest); with none standing it goes home.
     *
     * <p>Sectors, not a reserve that chases calls. On a front hundreds of cells wide a hull sent to
     * whichever redoubt called last spent the battle driving, mostly to calls that had ended by the
     * time it came; one standing beside a redoubt is there when the swarm comes.
     */
    public void advance(BattleSimulation sim, List<CordonRedoubt> redoubts) {
        command.advance(sim);
        if (sim.getSimTickIndex() % REVIEW_TICKS != 0) return;
        advanceMechs(sim, redoubts);
        List<Long> hulls = new ArrayList<>(command.hulls());
        home.keySet().retainAll(hulls);
        answering.keySet().retainAll(hulls);
        answering.values().removeIf(r -> !r.manned());
        Set<Long> escorting = escort(sim, hulls);
        for (long hull : hulls) {
            if (command.carrying(hull) || !home.containsKey(hull) || escorting.contains(hull)) continue;
            CordonRedoubt sector = answering.get(hull);
            if (sector == null) {
                sector = emptiest(sim.convoy().body(hull), redoubts);
                if (sector != null) answering.put(hull, sector);
            }
            int[] post = sector == null ? home.get(hull) : flank(sector, rankIn(hull, sector));
            command.station(sim, hull, post[0], post[1]);
        }
    }

    /**
     * Sends the hulls nearest each convoy's escort posts to them, one a post.
     *
     * @return the hulls sent
     */
    private Set<Long> escort(BattleSimulation sim, List<Long> hulls) {
        Set<Long> sent = new HashSet<>();
        if (escorts == null) return sent;
        for (int[][] posts : escorts.apply(sim)) {
            for (int[] post : posts) {
                long hull = 0L;
                float best = Float.MAX_VALUE;
                for (long h : hulls) {
                    if (command.carrying(h) || !home.containsKey(h) || sent.contains(h)) continue;
                    float d = sim.convoy().body(h).distanceTo(post[0], post[1]);
                    if (d < best) {
                        best = d;
                        hull = h;
                    }
                }
                if (hull == 0L) return sent;
                int[] cell = drivable(sim, sim.convoy().vehicleType(hull), post[0], post[1]);
                if (cell == null) cell = post;
                command.station(sim, hull, cell[0], cell[1]);
                sent.add(hull);
            }
        }
        return sent;
    }

    /**
     * Takes an allied mech standing on the field under the allied command (one the base's shed has finished): it is
     * given the standing redoubt with the fewest mechs at the next look and stands in its street mouths.
     */
    public void adoptMech(BattleSimulation sim, long mech) {
        command.enlistMech(sim, mech);
        mechSector.put(mech, null);
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

    /** Records a rifle team standing in a redoubt's street mouths behind its mechs (set down at setup). */
    public void addStreetTeam(int squad) {
        streetTeams.add(squad);
    }

    /** The rifle teams standing in the redoubts' street mouths, by squad. */
    public List<Integer> streetTeams() {
        return List.copyOf(streetTeams);
    }

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

    /**
     * Keeps each mech in the street mouths of its own redoubt. One without a redoubt, or whose redoubt fell, is given
     * the standing one with the fewest mechs (a calling one first, then the nearest): sectors, as the hulls have.
     */
    private void advanceMechs(BattleSimulation sim, List<CordonRedoubt> redoubts) {
        mechSector.keySet().removeIf(m -> sim.resolveUnit(m) == 0L || !sim.world().isAlive(m));
        for (Map.Entry<Long, CordonRedoubt> e : mechSector.entrySet()) {
            if (e.getValue() != null && e.getValue().manned()) continue;
            e.setValue(null);
            long mech = e.getKey();
            CordonRedoubt best = null;
            float bestScore = Float.MAX_VALUE;
            for (CordonRedoubt r : redoubts) {
                if (!r.manned()) continue;
                float score = mechsAt(r) * 10_000f - (r.state() == CordonRedoubt.State.CALLING ? 5_000f : 0f)
                        + (float) Math.hypot(sim.world().x(mech) - r.centreX(), sim.world().y(mech) - r.centreY());
                if (score < bestScore) {
                    bestScore = score;
                    best = r;
                }
            }
            e.setValue(best);
        }
        Map<CordonRedoubt, Integer> rank = new HashMap<>();
        for (Map.Entry<Long, CordonRedoubt> e : mechSector.entrySet()) {
            if (e.getValue() == null) continue;
            int[] post = mouthPost(sim, e.getValue(), rank.merge(e.getValue(), 1, Integer::sum) - 1);
            command.stationMech(sim, e.getKey(), post[0], post[1]);
        }
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
                    wall[i] = topology.isWall(x, y) && InstallationWalls.PERIMETER.equals(topology.getSurfaceId(x, y));
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

    /** The standing redoubt with the fewest hulls; a calling one, then the nearest, breaks a tie. */
    private CordonRedoubt emptiest(GroundBody body, List<CordonRedoubt> redoubts) {
        CordonRedoubt best = null;
        float bestScore = Float.MAX_VALUE;
        for (CordonRedoubt r : redoubts) {
            if (!r.manned()) continue;
            long held = answering.values().stream().filter(x -> x == r).count();
            float score = held * 10_000f - (r.state() == CordonRedoubt.State.CALLING ? 5_000f : 0f)
                    + body.distanceTo(r.centreX(), r.centreY());
            if (score < bestScore) {
                bestScore = score;
                best = r;
            }
        }
        return best;
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
