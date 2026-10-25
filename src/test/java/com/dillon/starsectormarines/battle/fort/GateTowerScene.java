package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.BuildingFloodFill;
import com.dillon.starsectormarines.battle.world.gen.bsp.FortStage;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortRoom;
import com.dillon.starsectormarines.battle.world.gen.fortress.GateTowers;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * A gate tower is a place a squad holds and fights from (`fort-works.md`): a production battery fort on a bare field
 * with its guns left out, a defender squad on its main gate's first tower, and a file of unarmed, unmoving targets out
 * on the approach the tower overlooks. Each loop changes one thing.
 *
 * <ul>
 *   <li>{@code garrison}: the squad starts in the yard, ordered to hold the tower as a building's envelope. It walks in,
 *       stands to at the slits and hits the approach.</li>
 *   <li>{@code pinned}: the squad stands fixed on the tower's stand cells, the slits open. It hits the approach.</li>
 *   <li>{@code sealed}: the control for {@code pinned}. Every slit is walled up before the battle stands, so the same
 *       squad on the same cells sees and shoots nothing outside: what the targets lose in {@code pinned} came through
 *       the slits.</li>
 * </ul>
 */
public final class GateTowerScene implements BehaviorScene {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 200;
    private static final int FORT_X = 80;
    private static final int FORT_Y = 80;
    private static final int HOLDERS = 3;
    private static final int TARGETS = 3;
    private static final float TOUGH = 1_000_000f;
    private static final int TICKS = 1800;
    /** Long enough inside the tower to have fired: a few seconds of a garrison at its slits. */
    private static final int MIN_INSIDE_TICKS = 150;

    @Override public String id() { return "gate-tower"; }

    @Override public String label() {
        return "A squad ordered to hold a fort's gate tower walks in, stands at its slits and hits the approach; "
                + "with the slits walled up it hits nothing";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("garrison", "pinned", "sealed")) reports.add(playOne(loop, frames));
        return reports;
    }

    private record Terrain(NavigationGrid grid, CellTopology topology, GenContext ctx, FortStage.Placed fort) {}

    private static Terrain stamp() {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
            }
        }
        GenContext ctx = new GenContext(grid, topology, new Random(7L), WIDTH, HEIGHT, 7L);
        ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(grid, topology, null));
        ctx.put(BspKeys.BUILDINGS, BuildingFloodFill.populate(topology, 7L));
        ctx.put(BspKeys.MARKET_PROFILE, TargetProfile.NEUTRAL);
        FortStage stage = FortStage.pinned(List.of(new FortStage.Pin(FortKind.BATTERY_FORT, FORT_X, FORT_Y, FORT_X, HEIGHT - 1)));
        stage.run(ctx);
        return new Terrain(grid, topology, ctx, stage.placed().get(0));
    }

    /** Walls up every slit of every gate tower, as the stage would have left a tower with none. */
    private static void seal(Terrain terrain) {
        FortStage.Placed placed = terrain.fort();
        for (FortRoom tower : placed.plan().towers()) {
            for (int[] slit : GateTowers.slits(tower, placed.plan()::isWorks)) {
                int[] cell = placed.placement().cell(slit[0], slit[1]);
                terrain.grid().setWalkable(cell[0], cell[1], false);
                terrain.topology().setWall(cell[0], cell[1], true);
            }
        }
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        Terrain terrain = stamp();
        if (loop.equals("sealed")) seal(terrain);
        // The fort's guns are left out: the only fire on the approach is the tower's.
        SceneWorld world = SceneBuilder.over(terrain.grid(), terrain.topology())
                .doodads(List.copyOf(terrain.ctx().doodads)).missionCompletion(false).build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261024L);
        CompoundLayout.Placement at = terrain.fort().placement();
        FortFrame frame = new FortFrame(terrain.fort().plan(), at.x(), at.y(), at.pose().quarterTurns());
        GateTowerGarrison.Manned manned = GateTowerGarrison.man(sim, frame, HOLDERS, TARGETS, TOUGH, "weapon.smg",
                !loop.equals("garrison"));
        Trace trace = new Trace(manned);
        SceneRun.of(world).observe(trace::observe)
                .frames(frames, loop, 30, tick -> String.format(Locale.ROOT, "%s / tick %d / inside %d, hit %.0f",
                        loop, tick, trace.insideNow, trace.hitWhileInside))
                .run(TICKS);
        return report(loop, trace);
    }

    private SceneReport report(String loop, Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("ticksAllInside", t.ticksAllInside);
        metrics.put("firstAllInsideTick", t.firstAllInside);
        metrics.put("hitWhileInside", t.hitWhileInside);
        metrics.put("hitTotal", t.hitTotal);
        metrics.put("holdersInsideAtEnd", t.insideNow);
        metrics.put("lastAllInsideTick", t.lastAllInside);
        switch (loop) {
            case "garrison" -> {
                verdicts.add(Verdict.of("the squad garrisons the tower", t.ticksAllInside >= MIN_INSIDE_TICKS,
                        "every holder in the tower for " + t.ticksAllInside + " ticks, first at tick " + t.firstAllInside));
                verdicts.add(Verdict.of("it stays", t.insideNow == HOLDERS,
                        t.insideNow + " of " + HOLDERS + " holders in the tower at the end"));
                verdicts.add(Verdict.of("it fires out over the approach", t.hitWhileInside > 0f,
                        String.format(Locale.ROOT, "targets lost %.0f hp while the squad was in the tower", t.hitWhileInside)));
            }
            case "pinned" -> verdicts.add(Verdict.of("a manned tower hits the approach", t.hitTotal > 0f,
                    String.format(Locale.ROOT, "targets lost %.0f hp", t.hitTotal)));
            default -> verdicts.add(Verdict.of("a sealed tower hits nothing", t.hitTotal == 0f,
                    String.format(Locale.ROOT, "targets lost %.0f hp", t.hitTotal)));
        }
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    /** How long the holders are all inside, and what the targets lose while they are. */
    private static final class Trace {
        private final GateTowerGarrison.Manned manned;
        private final float[] lastHp;
        int ticksAllInside;
        int firstAllInside = -1;
        int lastAllInside = -1;
        int insideNow;
        float hitWhileInside;
        float hitTotal;

        Trace(GateTowerGarrison.Manned manned) {
            this.manned = manned;
            this.lastHp = new float[manned.targets().length];
            for (int i = 0; i < lastHp.length; i++) lastHp[i] = Float.NaN;
        }

        void observe(BattleSimulation sim, int tick) {
            int inside = 0;
            for (long id : manned.holders()) {
                long unit = sim.resolveUnit(id);
                if (unit == 0L || !sim.world().isAlive(unit)) continue;
                if (manned.inside(sim.world().cellX(unit), sim.world().cellY(unit))) inside++;
            }
            insideNow = inside;
            boolean allInside = inside == manned.holders().length;
            if (allInside) {
                ticksAllInside++;
                if (firstAllInside < 0) firstAllInside = tick;
                lastAllInside = tick;
            }
            for (int i = 0; i < lastHp.length; i++) {
                long unit = sim.resolveUnit(manned.targets()[i]);
                float hp = unit == 0L ? 0f : sim.world().hp(unit);
                if (!Float.isNaN(lastHp[i]) && hp < lastHp[i]) {
                    hitTotal += lastHp[i] - hp;
                    if (allInside) hitWhileInside += lastHp[i] - hp;
                }
                lastHp[i] = hp;
            }
        }
    }
}
