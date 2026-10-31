package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.power.WalkingBarrage;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The Burn's <b>bore growth</b> (`heart-nouns.md`, Bore growth): while the battle goes on, new bores open on the
 * country the burn has not yet reached, out on the flanks of the approach's middle and back, so the pressure keeps up
 * while the company and the guns push through its centre.
 *
 * <p>Cadence: the first about {@link #FIRST_SECONDS} in, then one every {@link #EVERY_SECONDS}, while fewer than
 * {@link #MAX_LIVING} grown bores live. A grown bore is an ordinary satellite opened through
 * {@link SwarmBores#open}, at the smallest tier (a fissure, unfed): it grows, surges, sprouts its colonies and spreads
 * its creep as any bore does, and dies only to ordinary damage. Nothing here collapses one.
 *
 * <p>Siting ({@link #site}): past the near ride, on the south or north flank band, the flanks taken in turn (the
 * first from the seed); never within {@link #SCORCH_REACH} of ground the fire holds against creep (burning, or burnt
 * and not yet recovered); never within
 * {@link #FRIEND_CLEARANCE} of a body of the company's side or its allies (the battery's guns included); on clear
 * ground the start line reaches on foot ({@link HeartBores#clearGround}, {@link HeartBores#reachableFrom}); outside
 * the barrage's firing and next lines' band; and at least {@link #SPACING} from every living bore. Deterministic from
 * the battle seed.
 */
public final class HeartBoreGrowth {

    /** The first bore grows about this long into the battle. */
    static final float FIRST_SECONDS = 120f;
    /** Then one every this long. */
    static final float EVERY_SECONDS = 75f;
    /** At most this many grown bores live at once; the clock waits while they do. */
    static final int MAX_LIVING = 4;
    /** A due growth that found no ground tries again this much later. */
    static final float RETRY_SECONDS = 15f;
    /** No grown bore deeper than this: the heart's own ground round the workings is the mothers'. */
    static final float DEPTH_TO = 0.80f;
    /** How far past the near ride's far edge, as a depth, the first ground a bore may grow on lies. */
    static final float PAST_RIDE = 0.02f;
    /** Each flank band's width across the front, from its edge's margin inward. */
    static final float FLANK_WIDTH = 0.26f;
    /** No grown bore nearer a friendly body than this: it opens out of sight of the line, not in its face. */
    static final int FRIEND_CLEARANCE = 28;
    /** No grown bore nearer another living bore than this. */
    static final int SPACING = 28;
    /**
     * No cell the fire holds against creep (burning, or burnt within {@link FireField#CREEP_REFUSAL_TICKS}) within this
     * of the site: a bore grows where the burn has not reached, or where its ground has recovered. Every burnt cell
     * refused once refused nearly the whole flank by five minutes in, and growth stopped at three.
     */
    static final int SCORCH_REACH = SwarmBores.MOUTH_RADIUS + 3;
    /** Depth either side of the barrage's firing and next lines that no bore grows in. */
    static final float BARRAGE_BAND = 0.05f;
    private static final long SALT = 0x4752_4F57_4E42_4F52L;

    /** The ground a site is judged on: hand-made in a test, the battle's grid, fire and reach in play. */
    public interface Ground {
        int width();
        int height();
        /** 0 at the start edge, 1 at the heart's. */
        float depth(int x, int y);
        /** 0 at the south edge, 1 at the north. */
        float across(int x, int y);
        /** Reachable on foot from the start line, and clear ground a bore can open on. */
        boolean standable(int x, int y);
        /** Burning, or burnt and still refusing creep ({@link FireField#holdsCreepBack}). */
        boolean scorched(int x, int y);
    }

    /**
     * What else is on the field when a bore grows: friendly bodies and living bores as {@code {x, y}} cell centres,
     * and the depths of the barrage lines no bore grows beside.
     */
    public record Situation(List<float[]> friends, List<float[]> bores, float[] barrageDepths) { }

    private final Form form;
    private final int[] start;
    private final float depthFrom;
    private final SplittableRandom rng;
    private boolean north;
    private int nextTick = Math.round(FIRST_SECONDS / BattleSimulation.TICK_DT);
    private int nextNumber = -1;
    /** The numbers of the bores grown so far, in order. */
    private final List<Integer> grown = new ArrayList<>();

    /**
     * @param start a cell on the start line, from which a site must be reachable on foot
     */
    public HeartBoreGrowth(Form form, int[] start, long seed) {
        this.form = form;
        this.start = start.clone();
        this.depthFrom = HeartCountry.burnFirebreaks()[0] + PAST_RIDE;
        this.rng = new SplittableRandom(seed ^ SALT);
        this.north = rng.nextBoolean();
    }

    /** The numbers of the bores grown so far, in the order they grew. */
    public List<Integer> grown() { return List.copyOf(grown); }

    /** The siting rules a cell of a flank band can fail, in {@link #refusals} order. */
    static final String[] RULES = {"barrage", "ground", "bores", "friends", "scorched"};
    /**
     * Over every due growth that found no site: the scans, the flank-band cells judged, how many cells each rule
     * refused, and how many each rule alone refused (the ground relaxing that one rule would open).
     */
    private final long[] refusals = new long[2 + 2 * RULES.length];

    /** The refusal readout, for evidence: why due growths found no site. */
    public String refusals() {
        StringBuilder out = new StringBuilder("scans=").append(refusals[0]).append(" cells=").append(refusals[1]);
        for (int r = 0; r < RULES.length; r++) out.append(' ').append(RULES[r]).append('=').append(refusals[2 + r]);
        out.append(" | alone:");
        for (int r = 0; r < RULES.length; r++) out.append(' ').append(RULES[r]).append('=').append(refusals[2 + RULES.length + r]);
        return out.toString();
    }

    /**
     * Grows a bore when one is due and there is ground for it, and returns its number, else -1. Run in the heart's
     * simulation half, after the bores' own tick.
     */
    public int tick(BattleSimulation sim, WalkingBarrage barrage) {
        int tick = sim.getSimTickIndex();
        SwarmBores bores = sim.swarmBores();
        if (!due(tick, nextTick, living(bores))) return -1;
        if (nextNumber < 0) {
            nextNumber = 1;
            for (SwarmBore bore : bores.bores()) if (!bore.mother()) nextNumber = Math.max(nextNumber, bore.number() + 1);
        }
        Ground ground = ground(sim);
        Situation situation = situation(sim, barrage);
        boolean flank = north;
        int[] cell = site(ground, situation, flank, depthFrom, rng);
        if (cell == null) cell = site(ground, situation, flank = !flank, depthFrom, rng);
        if (cell == null) {
            tally(ground, situation, true, depthFrom, refusals);
            tally(ground, situation, false, depthFrom, refusals);
            nextTick = tick + Math.round(RETRY_SECONDS / BattleSimulation.TICK_DT);
            return -1;
        }
        int number = nextNumber++;
        bores.open(sim, number, false, cell[0], cell[1]);
        grown.add(number);
        north = !flank;
        nextTick = tick + Math.round(EVERY_SECONDS / BattleSimulation.TICK_DT);
        return number;
    }

    /** Whether a bore may grow on {@code tick}: its time has come and fewer than {@link #MAX_LIVING} grown ones live. */
    static boolean due(int tick, int nextTick, int living) {
        return tick >= nextTick && living < MAX_LIVING;
    }

    /** Grown bores living, or still waiting to open. */
    private int living(SwarmBores bores) {
        int n = 0;
        for (int number : grown) {
            SwarmBore bore = null;
            for (SwarmBore b : bores.bores()) if (!b.mother() && b.number() == number) bore = b;
            if (bore == null || !bore.collapsed()) n++;
        }
        return n;
    }

    /**
     * A site on the {@code north} or south flank band from {@code depthFrom} to {@link #DEPTH_TO}, by every siting
     * rule, or {@code null} when the flank has none: every fitting cell is found, and {@code rng} picks one.
     */
    static int[] site(Ground ground, Situation situation, boolean north, float depthFrom, SplittableRandom rng) {
        int width = ground.width(), height = ground.height();
        float edge = HeartBores.EDGE_MARGIN / (float) Math.max(1, height - 1);
        List<int[]> fit = new ArrayList<>();
        for (int y = HeartBores.EDGE_MARGIN; y < height - HeartBores.EDGE_MARGIN; y++) {
            for (int x = HeartBores.EDGE_MARGIN; x < width - HeartBores.EDGE_MARGIN; x++) {
                float depth = ground.depth(x, y), across = ground.across(x, y);
                if (depth < depthFrom || depth > DEPTH_TO) continue;
                if (north ? across < 1f - edge - FLANK_WIDTH : across > edge + FLANK_WIDTH) continue;
                if (inBarrage(situation.barrageDepths(), depth) || !ground.standable(x, y)) continue;
                if (near(situation.bores(), x, y, SPACING) || near(situation.friends(), x, y, FRIEND_CLEARANCE)) continue;
                if (!scorchedNear(ground, x, y)) fit.add(new int[]{x, y});
            }
        }
        return fit.isEmpty() ? null : fit.get(rng.nextInt(fit.size()));
    }

    /** Adds one failed scan of a flank band to {@code into}: each rule's refusals, and each rule's refusals alone. */
    static void tally(Ground ground, Situation situation, boolean north, float depthFrom, long[] into) {
        int width = ground.width(), height = ground.height();
        float edge = HeartBores.EDGE_MARGIN / (float) Math.max(1, height - 1);
        into[0]++;
        boolean[] failed = new boolean[RULES.length];
        for (int y = HeartBores.EDGE_MARGIN; y < height - HeartBores.EDGE_MARGIN; y++) {
            for (int x = HeartBores.EDGE_MARGIN; x < width - HeartBores.EDGE_MARGIN; x++) {
                float depth = ground.depth(x, y), across = ground.across(x, y);
                if (depth < depthFrom || depth > DEPTH_TO) continue;
                if (north ? across < 1f - edge - FLANK_WIDTH : across > edge + FLANK_WIDTH) continue;
                into[1]++;
                failed[0] = inBarrage(situation.barrageDepths(), depth);
                failed[1] = !ground.standable(x, y);
                failed[2] = near(situation.bores(), x, y, SPACING);
                failed[3] = near(situation.friends(), x, y, FRIEND_CLEARANCE);
                failed[4] = scorchedNear(ground, x, y);
                int count = 0, only = -1;
                for (int r = 0; r < RULES.length; r++) if (failed[r]) { into[2 + r]++; count++; only = r; }
                if (count == 1) into[2 + RULES.length + only]++;
            }
        }
    }

    private static boolean inBarrage(float[] depths, float depth) {
        for (float line : depths) if (Math.abs(line - depth) < BARRAGE_BAND) return true;
        return false;
    }

    private static boolean near(List<float[]> points, int x, int y, int reach) {
        float cx = x + 0.5f, cy = y + 0.5f;
        for (float[] p : points) {
            float dx = p[0] - cx, dy = p[1] - cy;
            if (dx * dx + dy * dy < (float) reach * reach) return true;
        }
        return false;
    }

    private static boolean scorchedNear(Ground ground, int x, int y) {
        for (int dy = -SCORCH_REACH; dy <= SCORCH_REACH; dy++) {
            for (int dx = -SCORCH_REACH; dx <= SCORCH_REACH; dx++) {
                int cx = x + dx, cy = y + dy;
                if (cx < 0 || cy < 0 || cx >= ground.width() || cy >= ground.height()) continue;
                if (ground.scorched(cx, cy)) return true;
            }
        }
        return false;
    }

    /** The battle's ground now: its grid, what the start line reaches on foot, and the ground the fire holds back. */
    private Ground ground(BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        FireField fire = sim.fireFieldIfPresent();
        boolean[] reach = HeartBores.reachableFrom(grid, start);
        int width = grid.getWidth(), height = grid.getHeight();
        return new Ground() {
            @Override public int width() { return width; }
            @Override public int height() { return height; }
            @Override public float depth(int x, int y) { return form.depth(x, y); }
            @Override public float across(int x, int y) { return form.across(x, y); }
            @Override public boolean standable(int x, int y) {
                return reach != null && reach[y * width + x] && HeartBores.clearGround(grid, topology, x, y);
            }
            @Override public boolean scorched(int x, int y) {
                return fire != null && fire.holdsCreepBack(y * width + x);
            }
        };
    }

    /** Every friendly body, every living bore, and the barrage's firing and next lines. */
    private static Situation situation(BattleSimulation sim, WalkingBarrage barrage) {
        List<float[]> friends = new ArrayList<>();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            Faction side = sim.identity().faction(unit);
            if (side != Faction.MARINE && side != Faction.ALLY) continue;
            if (!sim.world().hasPosition(unit) || sim.isRiding(unit)) continue;
            friends.add(new float[]{sim.world().x(unit), sim.world().y(unit)});
        }
        List<float[]> bores = new ArrayList<>();
        for (SwarmBore bore : sim.swarmBores().bores()) {
            if (!bore.collapsed()) bores.add(new float[]{bore.x() + 0.5f, bore.y() + 0.5f});
        }
        return new Situation(friends, bores, barrageDepths(barrage == null ? null : barrage.plan(),
                sim.getSimTickIndex() * BattleSimulation.TICK_DT));
    }

    /** The depths of the line firing at {@code seconds} and the next to open; none once the plan is done. */
    static float[] barrageDepths(WalkingBarrage.Plan plan, float seconds) {
        if (plan == null) return new float[0];
        List<WalkingBarrage.Line> lines = plan.lines();
        List<Float> out = new ArrayList<>();
        int firing = plan.firingAt(seconds);
        if (firing >= 0) out.add(lines.get(firing).depth());
        for (WalkingBarrage.Line line : lines) {
            if (line.openSeconds() > seconds) { out.add(line.depth()); break; }
        }
        float[] depths = new float[out.size()];
        for (int i = 0; i < depths.length; i++) depths[i] = out.get(i);
        return depths;
    }
}
