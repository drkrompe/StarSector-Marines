package com.dillon.starsectormarines.battle.creep;

import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenRecipe;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A bore beside a market hall on open ground, and a second hall far out of the
 * creep's reach: the creep fills the near hall and its roof caves in under the
 * weight ({@link CreepRoofCollapse}). One loop per case:
 * <ul>
 *   <li>{@code infested}: the bore left alone; the near hall's roof sags open from its walls inward.</li>
 *   <li>{@code withered}: the bore destroyed once the cave-in has begun; the creep dies back and the falls stop,
 *   while what fell stays fallen.</li>
 * </ul>
 * Verdicts are the rule's mechanics: a roof falls only on a cell that has held
 * living creep for the minimum hold, never on the hall the creep did not reach,
 * and only beside a crusted wall or a roof that fell before it. A held cell
 * beside a crusted wall past the longest hold has fallen. When the first roof
 * fell and how many fell are metrics.
 */
public final class CreepRoofCollapseScene implements BehaviorScene {

    static final int WIDTH = 150, HEIGHT = 48;
    static final int NEAR_LEFT = 12, FAR_LEFT = 128, HALL_BOTTOM = 20;
    static final int BORE_X = 5, BORE_Y = 24;
    private static final BuildingStamp HALL = BuildingStamp.MARKET_HALL;
    /** The market hall's plan, unturned. */
    private static final int HALL_WIDTH = 13, HALL_HEIGHT = 8;
    private static final long SEED = 20261021L;
    private static final int TICKS = 7200;
    private static final int KILL_TICK = 3300;
    private static final int FRAME_EVERY = 150;

    @Override public String id() { return "creep-roof-collapse"; }

    @Override public String label() {
        return "Creep standing in a building brings its roof down under the weight, from the walls inward";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        return List.of(playOne("infested", false, frames), playOne("withered", true, frames));
    }

    /** The near hall's centre and a frame across it: {@code {x, y, cellsAcross}}. */
    static float[] nearHallView() {
        return new float[]{NEAR_LEFT + HALL_WIDTH / 2f, HALL_BOTTOM + HALL_HEIGHT / 2f, 30f};
    }

    private SceneReport playOne(String loop, boolean kill, FrameSink frames) throws Exception {
        GenContext ctx = new GenContext(new NavigationGrid(WIDTH, HEIGHT), new CellTopology(WIDTH, HEIGHT),
                new Random(SEED), WIDTH, HEIGHT, SEED);
        new GenRecipe("Creep roof collapse", List.of(
                CreepRoofCollapseScene::openGround,
                c -> HALL.place(c, NEAR_LEFT, HALL_BOTTOM, 0),
                c -> HALL.place(c, FAR_LEFT, HALL_BOTTOM, 0),
                new FinalizeStage())).run(ctx);
        SceneWorld world = SceneBuilder.over(ctx.grid, ctx.topology).seed(SEED).missionCompletion(false).build();
        try (BattleSimulation sim = world.sim()) {
            for (Doodad doodad : ctx.doodads) sim.addDoodad(doodad);
            sim.setBuildings(ctx.get(BspKeys.BUILDINGS));
            SwarmBore bore = sim.swarmBores().open(sim, 1, false, BORE_X, BORE_Y);
            if (bore == null) throw new IllegalStateException("open ground refused a bore");
            Watch watch = new Watch(sim, bore, kill);
            SceneRun.of(world).observe(watch)
                    .frames(frames, loop, FRAME_EVERY, tick -> loop + " / " + bore.tier() + " bore / "
                            + watch.fallen + " roofs down / tick " + tick)
                    .run(TICKS);
            int minHold = Math.round(CreepRoofCollapse.HOLD_SECONDS * CreepRoofCollapse.TICKS_PER_SECOND);
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("roofs-fall-only-after-the-hold", watch.early == 0,
                    watch.early + " of " + watch.fallen + " roofs fell on a cell that had held living creep under "
                            + minHold + " ticks"));
            verdicts.add(Verdict.of("uncreeped-building-keeps-its-roof", watch.farFallen == 0 && watch.offCreep == 0,
                    watch.farFallen + " roofs fell in the hall the creep never reached, " + watch.offCreep
                            + " on cells without living creep"));
            verdicts.add(Verdict.of("cave-in-spreads-from-the-wall", watch.unanchored == 0,
                    watch.unanchored + " of " + watch.fallen
                            + " roofs fell beside neither a crusted wall nor a roof already down"));
            verdicts.add(Verdict.of("held-roof-beside-crust-falls", watch.overdue == 0,
                    watch.overdue + " cells beside a crusted wall still roofed after the longest hold"));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("roofsFallen", watch.fallen);
            metrics.put("firstFallTick", watch.firstFallTick);
            metrics.put("lastFallTick", watch.lastFallTick);
            metrics.put("nearHallCells", watch.nearCells);
            if (kill) metrics.put("fallsAfterKill", watch.fallsAfterKill);
            return new SceneReport(id(), loop, TICKS, verdicts, metrics);
        }
    }

    private static void openGround(GenContext c) {
        for (int y = 0; y < c.height; y++) {
            for (int x = 0; x < c.width; x++) {
                c.grid.setWalkableFloor(x, y);
                c.topology.setGroundKind(x, y, CellTopology.GroundKind.DIRT);
            }
        }
    }

    private static final class Watch implements TickObserver {
        private final SwarmBore bore;
        private final boolean kill;
        private final int width;
        private final int[] buildingCells;
        /** Consecutive ticks each cell has stood under living creep. */
        private final int[] held;
        /** Tick each roof was seen down, or -1. */
        private final int[] fellAt;
        private final boolean[] counted;
        private final int minHold = Math.round(CreepRoofCollapse.HOLD_SECONDS * CreepRoofCollapse.TICKS_PER_SECOND);
        private final int maxHold = Math.round((CreepRoofCollapse.HOLD_SECONDS + CreepRoofCollapse.HOLD_JITTER_SECONDS)
                * CreepRoofCollapse.TICKS_PER_SECOND);
        int fallen, early, farFallen, offCreep, unanchored, overdue, nearCells;
        int firstFallTick = -1, lastFallTick = -1, fallsAfterKill;

        Watch(BattleSimulation sim, SwarmBore bore, boolean kill) {
            this.bore = bore;
            this.kill = kill;
            CellTopology topology = sim.getTopology();
            this.width = sim.getGrid().getWidth();
            int height = sim.getGrid().getHeight();
            List<Integer> found = new ArrayList<>();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (topology.getBuildingId(x, y) == 0) continue;
                    found.add(y * width + x);
                    if (x < FAR_LEFT) nearCells++;
                }
            }
            buildingCells = found.stream().mapToInt(Integer::intValue).toArray();
            held = new int[width * height];
            fellAt = new int[width * height];
            counted = new boolean[width * height];
            Arrays.fill(fellAt, -1);
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            CreepField field = sim.creepFieldIfPresent();
            CellTopology topology = sim.getTopology();
            List<Integer> fellNow = new ArrayList<>();
            for (int idx : buildingCells) {
                if (fellAt[idx] >= 0) continue;
                int x = idx % width, y = idx / width;
                boolean creeped = field != null && field.isCreeped(x, y);
                if (topology.isRoofDestroyed(x, y)) {
                    fellNow.add(idx);
                    continue;
                }
                held[idx] = creeped ? held[idx] + 1 : 0;
                // Two looks of slack: one for the rule's hold counting in looks, one for the crust turning between them.
                if (creeped && !counted[idx] && held[idx] > maxHold + 2 * CreepRoofCollapse.CHECK_TICKS
                        && besideCrust(field, topology, x, y)) {
                    counted[idx] = true;
                    overdue++;
                }
            }
            for (int idx : fellNow) {
                int x = idx % width, y = idx / width;
                fallen++;
                if (firstFallTick < 0) firstFallTick = tick;
                lastFallTick = tick;
                if (kill && tick > KILL_TICK) fallsAfterKill++;
                if (x >= FAR_LEFT) farFallen++;
                if (field == null || !field.isCreeped(x, y)) offCreep++;
                // The rule counts in looks of CHECK_TICKS, the watch in ticks: one look of slack.
                if (held[idx] + 1 < minHold - CreepRoofCollapse.CHECK_TICKS) early++;
                if (!besideCrust(field, topology, x, y) && !besideEarlierFall(x, y, tick)) unanchored++;
            }
            for (int idx : fellNow) fellAt[idx] = tick;
            if (kill && tick == KILL_TICK) sim.applyDamage(bore.entityId(), 1_000_000f, 100f);
        }

        private boolean besideCrust(CreepField field, CellTopology topology, int x, int y) {
            if (field == null) return false;
            return crusted(field, topology, x - 1, y) || crusted(field, topology, x + 1, y)
                    || crusted(field, topology, x, y - 1) || crusted(field, topology, x, y + 1);
        }

        private boolean crusted(CreepField field, CellTopology topology, int x, int y) {
            if (x < 0 || y < 0 || x >= field.width() || y >= field.height() || !topology.isWall(x, y)) return false;
            int idx = y * field.width() + x;
            return field.coverageAt(idx) >= CreepRoofCollapse.CRUST && field.vitalityAt(idx) >= CreepField.CREEPED;
        }

        private boolean besideEarlierFall(int x, int y, int tick) {
            return earlier(x - 1, y, tick) || earlier(x + 1, y, tick) || earlier(x, y - 1, tick) || earlier(x, y + 1, tick);
        }

        private boolean earlier(int x, int y, int tick) {
            if (x < 0 || y < 0 || x >= width || y * width + x >= fellAt.length) return false;
            int at = fellAt[y * width + x];
            return at >= 0 && at < tick;
        }
    }
}
