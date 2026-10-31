package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.creep.CreepGrowth;
import com.dillon.starsectormarines.battle.evacuation.BoreTier;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.IntPredicate;

/**
 * The front as the company finds it ({@code cordon-nouns.md}, The map): the
 * bloom's bores already standing in front of the line at the tiers weeks of
 * feeding left them, their creep grown out over the ground they would have
 * taken since.
 *
 * <p>The front is cut across the map's width into one slot per bore, west to
 * east, so the pressure lies along the whole line rather than down one
 * corridor; a bore's number is its slot. Alternate slots stand near the line
 * and deep in the front. A bore is opened through the ordinary seam
 * ({@code SwarmBores.open}) and grown to its tier through the feed seam, as
 * the biomass it has eaten, so its age mix is that of a bore that was fed.
 */
public final class CordonFront {

    /** The front's bores when nothing says otherwise: the bloom some weeks on. */
    public static final List<BoreTier> DEFAULT_TIERS = List.of(
            BoreTier.BORE, BoreTier.MAW, BoreTier.BORE, BoreTier.BORE, BoreTier.MAW, BoreTier.BORE);
    /** Nearest a bore stands to the line, in cells: a bore-tier creep (fifty cells' reach) stops short of the redoubts, so the creep's edge is in front of the line, not behind it. */
    static final int NEAR = 60;
    /** The near slots' band beyond {@link #NEAR}, and where the deep slots begin. */
    static final int MID = 100;
    /** Deepest a bore stands in front of the line. */
    static final int DEEP = 165;
    /** Cells kept from the map edge. */
    static final int EDGE = 10;
    /** Cells a bore keeps from the Road's corridor: a bore on the road would close it. */
    static final int OFF_CORRIDOR = 14;
    /** Cells a bore keeps from the cut-off town's compound: the town is besieged, not yet eaten. */
    static final int OFF_COMPOUND = 70;
    /** Candidate cells are looked at on this pitch. */
    private static final int PITCH = 3;
    private static final long SEED_SALT = 0x46524F4E54424F52L;
    private static final Logger LOG = Global.getLogger(CordonFront.class);
    private static final long ERUPTION_SALT = 0x45525550544E4557L;
    /** Cells either side of a redoubt an eruption in front of it may open. */
    static final int ERUPTION_SPREAD = 40;
    /**
     * Cells beyond its creep's full reach a bore keeps from every redoubt's middle: about a
     * redoubt's half-width and a little ground in front of its wall. Measured from each redoubt,
     * not from the line's mean row, since one redoubt can stand twenty cells in front of the rest;
     * and by the bore's own reach, since a maw's creep runs eighty cells to a bore's fifty. A near
     * maw at the line's mean distance grew its creep over the redoubt in front of it, and the swarm
     * began the battle at its walls.
     */
    static final int REDOUBT_CLEAR = 24;

    private CordonFront() {}

    /**
     * Opens the front's bores at their tiers and matures their creep. Returns
     * the bores opened, west to east; a slot with no open ground is skipped.
     */
    public static List<SwarmBore> grow(BattleSimulation sim, PrecinctPlan plan, List<int[]> corridor,
                                       List<BoreTier> tiers, long seed) {
        return grow(sim, plan, corridor, tiers, seed, null);
    }

    /**
     * As above, the cut-off town's ground kept clear when {@code town} carries its warren ({@link CordonTownHold}):
     * the Road's town starts with its streets showing and the front's creep retakes it over the battle. A
     * {@code null} layout, or one with no warren, holds nothing.
     */
    public static List<SwarmBore> grow(BattleSimulation sim, PrecinctPlan plan, List<int[]> corridor,
                                       List<BoreTier> tiers, long seed, LaidLayout town) {
        int[][] sites = sites(sim.getGrid(), sim.getTopology(), plan, corridor, tiers, seed);
        List<SwarmBore> opened = new ArrayList<>();
        for (int i = 0; i < sites.length; i++) {
            if (sites[i] == null) continue;
            SwarmBore bore = sim.swarmBores().open(sim, i + 1, false, sites[i][0], sites[i][1]);
            if (bore == null) continue;
            BoreTier want = tiers.get(i);
            // One body at a time, so a bore stops at the tier asked for rather than past it.
            for (int guard = 0; bore.tier().ordinal() < want.ordinal() && guard < 64; guard++) {
                sim.swarmBores().feed(sim, bore, 1f);
            }
            opened.add(bore);
        }
        sim.matureCreep(townHold(sim, plan, town, seed));
        return opened;
    }

    /** The town's held ground on this battle's map, or {@code null} when there is no warren to hold. */
    private static IntPredicate townHold(BattleSimulation sim, PrecinctPlan plan, LaidLayout town, long seed) {
        Precinct compound = town == null ? null : CordonCorridor.compound(plan);
        if (compound == null) return null;
        int[] box = CordonTownHold.extent(town, compound.seedX(), compound.seedY());
        if (box == null) return null;
        List<int[]> mouths = new ArrayList<>();
        for (SwarmBore b : sim.swarmBores().bores()) if (!b.collapsed()) mouths.add(new int[]{b.x(), b.y()});
        int width = sim.getGrid().getWidth();
        IntPredicate held = CordonTownHold.held(width, box, mouths, seed);
        int count = 0;
        for (int y = Math.max(0, box[1]); y <= Math.min(sim.getGrid().getHeight() - 1, box[3]); y++) {
            for (int x = Math.max(0, box[0]); x <= Math.min(width - 1, box[2]); x++) if (held.test(y * width + x)) count++;
        }
        LOG.info("Cordon town held clear of creep: box " + box[0] + "," + box[1] + " to " + box[2] + "," + box[3]
                + ", " + count + " cells held, " + mouths.size() + " bores standing");
        return held;
    }

    /** The y of the line: the redoubts' mean seed row. */
    public static float lineY(PrecinctPlan plan) {
        List<Precinct> redoubts = CordonPlan.redoubts(plan);
        float sum = 0f;
        for (Precinct r : redoubts) sum += r.seedY();
        return redoubts.isEmpty() ? 0f : sum / redoubts.size();
    }

    /** The row of the rear edge: where the country road leaves the map behind the line. */
    public static int rearRow(PrecinctPlan plan, int height) {
        return frontward(plan) > 0 ? 1 : height - 2;
    }

    /** Which way the front lies from the line: +1 toward high y, -1 toward low. */
    public static int frontward(PrecinctPlan plan) {
        return plan.attackerFrom() != null && plan.attackerFrom().y0() == 0f ? 1 : -1;
    }

    /** One site per slot, west to east; {@code null} where the slot has no open ground. */
    static int[][] sites(NavigationGrid grid, CellTopology topology, PrecinctPlan plan, List<int[]> corridor,
                         List<BoreTier> tiers, long seed) {
        int count = tiers.size();
        SplittableRandom rng = new SplittableRandom(seed ^ SEED_SALT);
        int width = grid.getWidth();
        int[][] out = new int[count][];
        for (int slot = 0; slot < count; slot++) {
            int x0 = Math.max(EDGE, slot * width / count + EDGE), x1 = Math.min(width - 1 - EDGE, (slot + 1) * width / count - EDGE);
            boolean near = slot % 2 == 0;
            int keepOff = Math.round(CreepGrowth.forTier(tiers.get(slot).ordinal()).maxReachCells()) + REDOUBT_CLEAR;
            out[slot] = site(grid, topology, plan, corridor, x0, x1, near ? NEAR : MID, near ? MID : DEEP, keepOff, rng);
            // A near slot whose band is all too close for its tier's creep stands deep instead.
            if (out[slot] == null && near) out[slot] = site(grid, topology, plan, corridor, x0, x1, MID, DEEP, keepOff, rng);
        }
        return out;
    }

    /**
     * Where the bloom breaks out anew ({@code cordon-nouns.md}, Eruption): one
     * site per eruption, in the near band in front of a redoubt, each in front
     * of a different redoubt in a seeded order. {@code null} where a redoubt's
     * stretch has no open ground.
     */
    public static int[][] eruptionSites(NavigationGrid grid, CellTopology topology, PrecinctPlan plan,
                                        List<int[]> corridor, int count, long seed) {
        SplittableRandom rng = new SplittableRandom(seed ^ ERUPTION_SALT);
        List<Precinct> redoubts = CordonPlan.redoubts(plan);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < redoubts.size(); i++) order.add(i);
        for (int i = order.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            order.set(i, order.set(j, order.get(i)));
        }
        int[][] out = new int[count][];
        for (int n = 0; n < count && !order.isEmpty(); n++) {
            Precinct redoubt = redoubts.get(order.get(n % order.size()));
            out[n] = site(grid, topology, plan, corridor, redoubt.seedX() - ERUPTION_SPREAD,
                    redoubt.seedX() + ERUPTION_SPREAD, NEAR, MID, 0, rng);
        }
        return out;
    }

    /**
     * A seeded open site between {@code x0} and {@code x1}, {@code d0} to {@code d1} cells in front
     * of the line, and at least {@code keepOff} cells from every redoubt's middle.
     */
    private static int[] site(NavigationGrid grid, CellTopology topology, PrecinctPlan plan, List<int[]> corridor,
                              int x0, int x1, int d0, int d1, int keepOff, SplittableRandom rng) {
        Precinct compound = CordonCorridor.compound(plan);
        List<Precinct> redoubts = CordonPlan.redoubts(plan);
        int line = Math.round(lineY(plan));
        int dir = frontward(plan);
        int width = grid.getWidth(), height = grid.getHeight();
        x0 = Math.max(EDGE, x0);
        x1 = Math.min(width - 1 - EDGE, x1);
        List<int[]> candidates = new ArrayList<>();
        for (int d = d0; d <= d1; d += PITCH) {
            int y = line + dir * d;
            if (y < EDGE || y >= height - EDGE) continue;
            for (int x = x0; x <= x1; x += PITCH) {
                if (!open(grid, topology, x, y) || near(corridor, x, y, OFF_CORRIDOR)) continue;
                if (compound != null && Math.hypot(x - compound.seedX(), y - compound.seedY()) < OFF_COMPOUND) continue;
                if (nearRedoubt(redoubts, x, y, keepOff)) continue;
                candidates.add(new int[]{x, y});
            }
        }
        return candidates.isEmpty() ? null : candidates.get(rng.nextInt(candidates.size()));
    }

    private static boolean nearRedoubt(List<Precinct> redoubts, int x, int y, int within) {
        for (Precinct r : redoubts) if (Math.hypot(x - r.seedX(), y - r.seedY()) < within) return true;
        return false;
    }

    private static boolean near(List<int[]> cells, int x, int y, int within) {
        if (cells == null) return false;
        for (int[] c : cells) if (Math.abs(c[0] - x) <= within && Math.abs(c[1] - y) <= within) return true;
        return false;
    }

    /** Open ground with room round it: walkable, outdoors, nothing solid within two cells. */
    private static boolean open(NavigationGrid grid, CellTopology topology, int x, int y) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int nx = x + dx, ny = y + dy;
                if (!grid.inBounds(nx, ny) || !grid.isWalkable(nx, ny) || topology.isWall(nx, ny)
                        || topology.isRoofIntact(nx, ny)) return false;
            }
        }
        return true;
    }
}
