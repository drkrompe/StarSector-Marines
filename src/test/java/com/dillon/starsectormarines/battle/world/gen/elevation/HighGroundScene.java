package com.dillon.starsectormarines.battle.world.gen.elevation;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A squad takes held high ground by its ramp ({@code elevation-nouns.md}).
 *
 * <p>Six marines on the overlook's low ground are ordered onto the plateau
 * past its west ramp, held by one defender on the rim above the ramp and one
 * back from the edge. The verdicts assert the mechanics: nobody changes tier
 * except through a ramp cell, nobody stands on a face, and while every marine
 * is below, the defender back from the rim is never in the marines' sight
 * (the skyline rule). Who wins, and when the first marine stands on top, are
 * readings. Path cost against the same meadow with no plateau is a metric.
 */
public final class HighGroundScene implements BehaviorScene {

    private static final int TICKS = 1500;
    private static final int[] START = {4, 19};
    /** Ticks the fog may trail an exposure by: it recasts every third tick. */
    private static final int FOG_LAG_TICKS = 3;
    private static final int[] GOAL = {32, 19};

    @Override public String id() { return "high-ground"; }

    @Override public String label() { return "A squad takes a held plateau by its ramp"; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        MapResult map = ElevationTestMaps.overlookMap(1, CellTopology.GroundKind.GRASS);
        ElevationMap elevation = map.grid.elevation();
        int[] rim = nearest(elevation, 22, 21, true);
        int[] back = nearest(elevation, 33, 19, false);
        SceneWorld scene = SceneBuilder.over(map.grid, map.topology)
                .seed(20261022L)
                .squad("assault").faction(Faction.MARINE).size(6).inFile(START[0], START[1] - 3, 0, 1)
                .assigned(id -> ObjectiveAssignment.attackMove(id, GOAL[0], GOAL[1])).done()
                .unit("rim", Faction.DEFENDER, UnitType.MARINE, rim[0], rim[1], spec -> spec.moveSpeed(0f))
                .unit("back", Faction.DEFENDER, UnitType.MARINE, back[0], back[1], spec -> spec.moveSpeed(0f))
                .build();
        int w = elevation.width();
        try (BattleSimulation sim = scene.sim()) {
            long[] marines = scene.members("assault");
            long backDefender = scene.unit("back");
            int[] previous = new int[marines.length];
            boolean[] usedRamp = new boolean[marines.length];
            for (int i = 0; i < marines.length; i++) previous[i] = cell(sim, marines[i], w);
            List<String> tierJumps = new ArrayList<>(), onFace = new ArrayList<>();
            int belowTicks = 0, backSeenFromBelow = 0, firstOnTop = -1, rimSeenFromBelow = 0;
            // The fog recasts every third tick, so it trails an exposure starting and ending by up to that.
            int sinceExposed = Integer.MAX_VALUE, exposedRun = 0, exposedTicks = 0, exposedSeen = 0;
            for (int tick = 0; tick < TICKS; tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                boolean allBelow = true;
                for (int i = 0; i < marines.length; i++) {
                    if (!sim.world().isAlive(marines[i])) continue;
                    int now = cell(sim, marines[i], w), was = previous[i];
                    if (elevation.isFaceAt(now)) onFace.add(marines[i] + "@" + now % w + "," + now / w);
                    if (elevation.isRampAt(now)) usedRamp[i] = true;
                    if (now != was && !elevation.isRampAt(now) && !elevation.isRampAt(was)
                            && elevation.sightHeightAt(now) != elevation.sightHeightAt(was)) {
                        tierJumps.add(was % w + "," + was / w + "->" + now % w + "," + now / w);
                    }
                    if (elevation.eyeTier(now % w, now / w) > 0) {
                        allBelow = false;
                        if (firstOnTop < 0) firstOnTop = tick;
                    }
                    previous[i] = now;
                }
                boolean exposed = sim.firingExposure().isExposed(backDefender);
                sinceExposed = exposed ? 0 : sinceExposed == Integer.MAX_VALUE ? sinceExposed : sinceExposed + 1;
                exposedRun = exposed ? exposedRun + 1 : 0;
                if (allBelow && sim.world().isAlive(backDefender)) {
                    belowTicks++;
                    boolean seen = revealed(sim, backDefender);
                    if (seen && sinceExposed > FOG_LAG_TICKS) backSeenFromBelow++;
                    if (exposedRun > FOG_LAG_TICKS) {
                        exposedTicks++;
                        if (seen) exposedSeen++;
                    }
                    if (sim.world().isAlive(scene.unit("rim"))
                            && revealed(sim, scene.unit("rim"))) rimSeenFromBelow++;
                }
                if (tick % 20 == 0) frames.frame("assault", sim, tick, "high ground / " + tick);
            }
            boolean anyOnTop = firstOnTop >= 0;
            boolean everyClimberUsedRamp = true;
            for (int i = 0; i < marines.length; i++) {
                int at = previous[i];
                if (elevation.eyeTier(at % w, at / w) > 0 && !usedRamp[i]) everyClimberUsedRamp = false;
            }
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("tier-changes-only-by-ramp", tierJumps.isEmpty(),
                    tierJumps.isEmpty() ? "every step between tiers passed through a ramp cell"
                            : "stepped between tiers off a ramp: " + tierJumps.subList(0, Math.min(5, tierJumps.size()))));
            verdicts.add(Verdict.of("never-on-a-face", onFace.isEmpty(),
                    onFace.isEmpty() ? "no marine stood on a cliff face"
                            : "on a face: " + onFace.subList(0, Math.min(5, onFace.size()))));
            verdicts.add(Verdict.of("up-by-the-ramp", !anyOnTop || everyClimberUsedRamp,
                    anyOnTop ? "every marine on top had stood on the ramp"
                            : "no marine reached the top (conditional: nothing to judge)"));
            verdicts.add(Verdict.of("back-from-the-rim-unseen-from-below",
                    elevation.sightRule() != ElevationMap.SightRule.SKYLINE || backSeenFromBelow == 0,
                    "defender back from the rim visible on " + backSeenFromBelow + " of " + belowTicks
                            + " ticks with every marine below and no shot down of its own just before ("
                            + elevation.sightRule() + ")"));
            // A shot down exposes the holder: the fog shows it to the ground it fired on (FiringExposure).
            // Seen by whoever it could see down to and whose sight reaches it: at least once while exposed.
            verdicts.add(Verdict.of("firing-down-reveals-the-shooter",
                    exposedTicks == 0 || exposedSeen > 0,
                    exposedTicks == 0 ? "the holder back from the rim never fired down (conditional: nothing to judge)"
                            : "exposed by its own fire on " + exposedTicks + " ticks with every marine below, seen on "
                            + exposedSeen));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("firstOnTopTick", firstOnTop);
            metrics.put("ticksAllBelow", belowTicks);
            metrics.put("rimDefenderSeenFromBelowTicks", rimSeenFromBelow);
            metrics.put("backDefenderExposedTicks", exposedTicks);
            metrics.put("backDefenderSeenWhileExposedTicks", exposedSeen);
            metrics.put("marinesAlive", alive(sim, marines));
            metrics.put("defendersAlive", alive(sim, new long[]{scene.unit("rim"), backDefender}));
            pathCost(metrics, map.grid, ElevationTestMaps.meadow(CellTopology.GroundKind.GRASS).grid);
            List<SceneReport> reports = new ArrayList<>();
            reports.add(new SceneReport(id(), "assault", TICKS, verdicts, metrics));
            return reports;
        }
    }

    /**
     * Infantry routes on the overlook and on the same meadow without it: onto
     * the top by the ramp and past the plateau to the low ground beyond. Path
     * length and the median time of a search.
     */
    private static void pathCost(Map<String, Number> metrics, NavigationGrid raised, NavigationGrid flat) {
        // Onto the top by the ramp, and past the plateau to the low ground beyond it.
        int[][] routes = {{START[0], START[1], GOAL[0], GOAL[1]}, {START[0], START[1], 60, 19}};
        for (int pass = 0; pass < 4; pass++) {
            NavigationGrid grid = pass % 2 == 0 ? raised : flat;
            int[] r = routes[pass / 2];
            String key = (pass / 2 == 0 ? "onto" : "past") + (pass % 2 == 0 ? "Overlook" : "FlatControl");
            int[] path = GridPathfinder.findPath(grid, r[0], r[1], r[2], r[3]);
            long[] nanos = new long[201];
            for (int i = 0; i < nanos.length; i++) {
                long t0 = System.nanoTime();
                GridPathfinder.findPath(grid, r[0], r[1], r[2], r[3]);
                nanos[i] = System.nanoTime() - t0;
            }
            Arrays.sort(nanos);
            metrics.put(key + "PathLength", path == null ? -1 : path.length);
            metrics.put(key + "SearchMicros", nanos[nanos.length / 2] / 1000.0);
        }
    }

    private static int cell(BattleSimulation sim, long id, int width) {
        return (int) sim.world().y(id) * width + (int) sim.world().x(id);
    }

    private static int alive(BattleSimulation sim, long[] ids) {
        int n = 0;
        for (long id : ids) if (sim.world().isAlive(id)) n++;
        return n;
    }

    /**
     * Whether the unit's cell is revealed to the marines. The cell, not the
     * per-unit visibility, which is keyed by roster slot and reads a
     * neighbour's value for the tick a death reorders the roster.
     */
    private static boolean revealed(BattleSimulation sim, long unit) {
        return sim.getFogOfWar().isCellRevealed(sim.world().cellX(unit), sim.world().cellY(unit));
    }

    /** The top cell nearest a point that is (or, with {@code onRim} false, is not) on the rim. */
    static int[] nearest(ElevationMap elevation, int x, int y, boolean onRim) {
        int w = elevation.width(), best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < w * elevation.height(); i++) {
            if (elevation.kindAt(i) != ElevationMap.GROUND || elevation.sightHeightAt(i) != 1) continue;
            if (elevation.isRimAt(i) != onRim) continue;
            double d = Math.hypot(i % w - x, i / w - y);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return new int[]{best % w, best / w};
    }
}
