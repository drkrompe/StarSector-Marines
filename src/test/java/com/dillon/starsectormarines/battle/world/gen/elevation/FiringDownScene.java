package com.dillon.starsectormarines.battle.world.gen.elevation;

import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vision.FiringExposure;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A holder back from the rim fires down and gives itself away
 * ({@link FiringExposure}, {@code elevation-nouns.md}).
 *
 * <p>One defender stands two cells back from the overlook's west rim, where
 * the skyline rule hides it from below; four marines stand on the low ground
 * in its reach and hold. The verdicts assert the mechanic: until its first shot
 * down it is never in the marines' sight and nothing is fired at it; once a
 * shot down has exposed it (and the fog has had its recast to catch up) the
 * marines see it, and they fire only while it is exposed. Who is hurt, and how
 * many rounds go each way, are readings.
 */
public final class FiringDownScene implements BehaviorScene {

    private static final int TICKS = 900;
    /** Ticks the fog may trail an exposure by: it recasts every third tick. */
    private static final int FOG_LAG_TICKS = 3;

    @Override public String id() { return "firing-down"; }

    @Override public String label() { return "A holder back from the rim fires down and is seen"; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        MapResult map = ElevationTestMaps.overlookMap(1, CellTopology.GroundKind.GRASS);
        ElevationMap elevation = map.grid.elevation();
        int w = elevation.width();
        int[] rim = westmostRim(elevation);
        int[] holder = HighGroundScene.nearest(elevation, rim[0] + 2, rim[1], false);
        int[] below = lowGround(elevation, rim[0] - 7, rim[1]);
        SceneWorld scene = SceneBuilder.over(map.grid, map.topology)
                .seed(20261022L)
                .squad("below").faction(Faction.MARINE).size(4).inFile(below[0], below[1] - 2, 0, 1).stationary().done()
                .squad("holder").faction(Faction.DEFENDER).size(1).inFile(holder[0], holder[1], 0, 1).stationary().done()
                .build();
        try (BattleSimulation sim = scene.sim()) {
            long[] marines = scene.members("below");
            long holderId = scene.members("holder")[0];
            int firstExposed = -1, seenBeforeExposed = 0, firedBeforeExposed = 0;
            int exposedRun = 0, settledTicks = 0, settledSeen = 0, firedWhileExposed = 0, firedWhileHidden = 0;
            int sinceExposed = Integer.MAX_VALUE;
            int[] rounds = new int[marines.length];
            for (int tick = 0; tick < TICKS; tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                if (!sim.world().isAlive(holderId)) break;
                boolean exposed = sim.firingExposure().isExposed(holderId);
                if (exposed && firstExposed < 0) firstExposed = tick;
                exposedRun = exposed ? exposedRun + 1 : 0;
                sinceExposed = exposed ? 0 : sinceExposed == Integer.MAX_VALUE ? sinceExposed : sinceExposed + 1;
                boolean seen = sim.getFogOfWar().isCellRevealed(sim.world().cellX(holderId), sim.world().cellY(holderId));
                int fired = 0;
                for (int i = 0; i < marines.length; i++) {
                    if (!sim.world().isAlive(marines[i])) continue;
                    int now = sim.getRoster().telemetry().roundsFired(marines[i]);
                    fired += now - rounds[i];
                    rounds[i] = now;
                }
                if (firstExposed < 0) {
                    if (seen) seenBeforeExposed++;
                    firedBeforeExposed += fired;
                } else if (exposed || sinceExposed <= FOG_LAG_TICKS) {
                    firedWhileExposed += fired;
                } else {
                    firedWhileHidden += fired;
                }
                if (exposedRun > FOG_LAG_TICKS) {
                    settledTicks++;
                    if (seen) settledSeen++;
                }
                if (tick % 10 == 0) frames.frame("exchange", sim, tick, "firing down / " + tick);
            }
            List<Verdict> verdicts = new ArrayList<>();
            boolean skyline = elevation.sightRule() == ElevationMap.SightRule.SKYLINE;
            verdicts.add(Verdict.of("hidden-until-it-fires", !skyline || seenBeforeExposed == 0,
                    "seen from below on " + seenBeforeExposed + " ticks before its first shot down ("
                            + elevation.sightRule() + ")"));
            verdicts.add(Verdict.of("nothing-fired-at-it-unseen", !skyline || firedBeforeExposed + firedWhileHidden == 0,
                    (firedBeforeExposed + firedWhileHidden) + " marine rounds fired while it was hidden"));
            verdicts.add(Verdict.of("seen-while-exposed", settledTicks == 0 || settledSeen == settledTicks,
                    settledTicks == 0 ? "it never fired down (conditional: nothing to judge)"
                            : "seen on " + settledSeen + " of " + settledTicks + " ticks exposed by its own fire"));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("firstExposedTick", firstExposed);
            metrics.put("exposedTicks", settledTicks);
            metrics.put("marineRoundsWhileExposed", firedWhileExposed);
            metrics.put("holderAlive", sim.world().isAlive(holderId) ? 1 : 0);
            int alive = 0;
            for (long m : marines) if (sim.world().isAlive(m)) alive++;
            metrics.put("marinesAlive", alive);
            List<SceneReport> reports = new ArrayList<>();
            reports.add(new SceneReport(id(), "exchange", TICKS, verdicts, metrics));
            return reports;
        }
    }

    /** The rim cell farthest west, on the plateau's first tier. */
    private static int[] westmostRim(ElevationMap elevation) {
        int w = elevation.width(), bestX = Integer.MAX_VALUE, bestY = 0;
        for (int i = 0; i < w * elevation.height(); i++) {
            if (!elevation.isRimAt(i) || elevation.sightHeightAt(i) != 1) continue;
            // Clear of the west ramp's corridor: the holder must stand behind a face, not above a slope.
            if (nearRamp(elevation, i % w, i / w, 4)) continue;
            if (i % w < bestX) {
                bestX = i % w;
                bestY = i / w;
            }
        }
        return new int[]{bestX, bestY};
    }

    private static boolean nearRamp(ElevationMap elevation, int x, int y, int radius) {
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (elevation.isRamp(x + dx, y + dy)) return true;
            }
        }
        return false;
    }

    /** The low-ground cell nearest a point, with room for a short file north of it. */
    private static int[] lowGround(ElevationMap elevation, int x, int y) {
        for (int r = 0; r < 20; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    boolean clear = true;
                    for (int k = -2; k <= 2 && clear; k++) {
                        int cx = x + dx, cy = y + dy + k;
                        clear = elevation.inBounds(cx, cy) && elevation.tier(cx, cy) == 0
                                && !elevation.isFace(cx, cy) && !elevation.isRamp(cx, cy);
                    }
                    if (clear) return new int[]{x + dx, y + dy};
                }
            }
        }
        throw new IllegalStateException("No low ground near " + x + "," + y);
    }
}
