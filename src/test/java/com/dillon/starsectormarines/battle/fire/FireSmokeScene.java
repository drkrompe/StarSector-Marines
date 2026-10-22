package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Smoke over a fire (`fire-nouns.md`, Smoke): a hedge burning between two
 * watchers on bare ground. Verdicts: the watchers see each other before it is
 * lit, the burning hedge cuts their sight while it burns, and sight comes back
 * once it is out. How long the screen stood is a metric.
 */
public final class FireSmokeScene implements BehaviorScene {

    static final int WIDTH = 60, HEIGHT = 20;
    static final int HEDGE_X0 = 28, HEDGE_X1 = 31;
    static final int WATCH_A = 10, WATCH_B = 50, WATCH_Y = 10;
    private static final long SEED = 20261022L;
    private static final int LIGHT_TICK = 30;
    private static final int TICKS = 30 * 40;

    @Override public String id() { return "fire-smoke"; }

    @Override public String label() { return "A burning hedge screens sight while it burns and gives it back when it is out"; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        int shrub = -1;
        TileRegistry registry = TileRegistry.installed();
        if (registry != null && registry.has("nature.shrub-1")) shrub = registry.indexOf("nature.shrub-1");
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                boolean hedge = x >= HEDGE_X0 && x <= HEDGE_X1 && y >= 2 && y < HEIGHT - 2;
                topology.setGroundKind(x, y, hedge ? CellTopology.GroundKind.GRASS : CellTopology.GroundKind.DIRT);
                if (hedge && shrub >= 0) topology.setNatureOverlayIndex(x, y, shrub);
            }
        }
        SceneWorld world = SceneBuilder.over(grid, topology).seed(SEED).missionCompletion(false).build();
        try (BattleSimulation sim = world.sim()) {
            boolean[] seenBefore = {false};
            int[] blocked = {0};
            int[] outTick = {-1};
            boolean[] seenAfter = {false};
            SceneRun.of(world).observe((s, tick) -> {
                boolean sees = grid.hasLineOfSight(WATCH_A, WATCH_Y, WATCH_B, WATCH_Y);
                if (tick < LIGHT_TICK) {
                    seenBefore[0] = sees;
                    return;
                }
                if (tick == LIGHT_TICK) {
                    for (int y = 2; y < HEIGHT - 2; y++) s.fireField().addHeat(HEDGE_X0, y, 2000);
                    return;
                }
                FireField f = s.fireField();
                if (f.burningCount() > 0) {
                    if (!sees) blocked[0]++;
                } else if (f.burntCount() > 0) {
                    if (outTick[0] < 0) outTick[0] = tick;
                    seenAfter[0] = sees;
                }
            }).frames(frames, "hedge", 30, tick -> "hedge / tick " + tick).run(TICKS);
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("sight-before-the-fire", seenBefore[0], "the watchers " + (seenBefore[0] ? "saw" : "did not see") + " each other before it was lit"));
            verdicts.add(Verdict.of("burning-hedge-screens", blocked[0] > 0, "sight was cut for " + blocked[0] + " ticks while the hedge burned"));
            verdicts.add(Verdict.of("sight-returns", outTick[0] >= 0 && seenAfter[0],
                    outTick[0] < 0 ? "the hedge was still burning at the end" : "the watchers " + (seenAfter[0] ? "saw" : "did not see") + " each other once it was out"));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("ticksScreened", blocked[0]);
            metrics.put("fireOutTick", outTick[0]);
            return List.of(new SceneReport(id(), "hedge", TICKS, verdicts, metrics));
        }
    }
}
