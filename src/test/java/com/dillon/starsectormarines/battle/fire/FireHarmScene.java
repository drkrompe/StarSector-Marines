package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.component.BattleComponents;
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
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What fire does to people (`fire-nouns.md`, Damage and Pathing). One loop per case:
 * <ul>
 *   <li>{@code caught}: a squad standing in a meadow as a grass fire runs into it,
 *   with a dead runner lying in its path.</li>
 *   <li>{@code crossing}: a squad attack-moving across a field where a band of brush
 *   is burning, with a way round the band's end.</li>
 * </ul>
 * Verdicts are the mechanics: a living body in a burning cell loses health every
 * fire step, a swarm body lying in fire is consumed, and a published route cost
 * charges a burning cell more than bare ground. Health lost, deaths and the
 * crossing squad's time in fire are metrics; compare {@code crossing} against
 * {@code -Dbattle.pathfinding.fireRouteCost=false} to read the detour.
 */
public final class FireHarmScene implements BehaviorScene {

    static final int WIDTH = 100, HEIGHT = 40;
    private static final long SEED = 20261022L;
    private static final int TICKS = 30 * 60;

    @Override public String id() { return "fire-harm"; }

    @Override public String label() { return "Fire hurts what stands in it, consumes the swarm's dead, and is routed round"; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        return List.of(caught(frames), crossing(frames));
    }

    private static NavigationGrid ground(CellTopology topology, int grassFrom, int grassTo, int grassMaxY, boolean brush) {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        int shrub = -1;
        TileRegistry registry = TileRegistry.installed();
        if (brush && registry != null && registry.has("nature.shrub-1")) shrub = registry.indexOf("nature.shrub-1");
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                boolean grass = x >= grassFrom && x <= grassTo && y <= grassMaxY;
                topology.setGroundKind(x, y, grass ? CellTopology.GroundKind.GRASS : CellTopology.GroundKind.DIRT);
                if (grass && shrub >= 0) topology.setNatureOverlayIndex(x, y, shrub);
            }
        }
        return grid;
    }

    private SceneReport caught(FrameSink frames) throws Exception {
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        NavigationGrid grid = ground(topology, 20, 45, HEIGHT, false);
        SceneBuilder builder = SceneBuilder.over(grid, topology).seed(SEED).missionCompletion(false);
        builder.squad("caught").at(32, 16).inFile(32, 16, 0, 2).size(6).unarmed().stationary().done();
        builder.unit("dead-runner", Faction.DEFENDER, UnitType.SWARM_RUNNER, 28, 30);
        builder.unit("far", Faction.DEFENDER, UnitType.MARINE, 96, 38);
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            long runner = world.unit("dead-runner");
            Watch watch = new Watch(world.members("caught"), runner);
            SceneRun.of(world).observe((s, tick) -> {
                if (tick == 2) s.applyDamage(runner, 1_000_000f, 100f);
                if (tick == 30) for (int y = 0; y < HEIGHT; y++) s.fireField().addHeat(20, y, 3000);
                watch.observe(s, tick);
            }).frames(frames, "caught", 30, tick -> "caught / " + watch.alive + " standing / hp lost "
                    + Math.round(watch.hpLost) + " / tick " + tick).run(TICKS);
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("in-fire-is-hurt", watch.unhurtInFire == 0,
                    watch.unhurtInFire + " of " + watch.inFireSteps + " fire steps left a living body in a burning cell unhurt"));
            verdicts.add(Verdict.of("swarm-dead-consumed", watch.corpseLingered == 0,
                    watch.corpseLingered + " looks found the dead runner still lying in fire a step after it caught"
                            + (watch.corpseBurned ? "" : " (the fire never reached it)")));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("hpLost", watch.hpLost);
            metrics.put("deaths", watch.deaths);
            metrics.put("inFireSteps", watch.inFireSteps);
            metrics.put("maxStepDamage", watch.maxStep);
            return new SceneReport(id(), "caught", TICKS, verdicts, metrics);
        }
    }

    private SceneReport crossing(FrameSink frames) throws Exception {
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        NavigationGrid grid = ground(topology, 44, 51, 29, true);
        SceneBuilder builder = SceneBuilder.over(grid, topology).seed(SEED).missionCompletion(false);
        builder.squad("crossing").at(6, 12).inFile(6, 12, 0, 2).size(6).unarmed()
                .assigned(id -> ObjectiveAssignment.attackMove(id, 92, 12)).done();
        builder.unit("far", Faction.DEFENDER, UnitType.MARINE, 96, 38);
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            Watch watch = new Watch(world.members("crossing"), 0L);
            float[] costAt = {0f, 0f};
            int[] lit = {-1};
            long[] squad = world.members("crossing");
            SceneRun.of(world).observe((s, tick) -> {
                // Lit as the squad closes on it, so the band is burning when it gets there.
                if (lit[0] < 0 && leadX(s, squad) >= 30) {
                    lit[0] = tick;
                    for (int y = 0; y <= 29; y++) for (int x = 44; x <= 51; x++) s.fireField().addHeat(x, y, 3000);
                }
                if (lit[0] >= 0 && tick == lit[0] + 30) {
                    var cost = s.getRouteCostField(Faction.MARINE);
                    costAt[0] = cost == null ? 1f : cost.costAt(12 * WIDTH + 47);
                    costAt[1] = cost == null ? 1f : cost.costAt(12 * WIDTH + 20);
                }
                watch.observe(s, tick);
            }).frames(frames, "crossing", 30, tick -> "crossing / " + watch.alive + " alive / ticks in fire "
                    + watch.ticksInFire + " / tick " + tick).run(TICKS);
            List<Verdict> verdicts = new ArrayList<>();
            boolean costed = !"false".equalsIgnoreCase(System.getProperty(FireRouteCost.PROPERTY));
            verdicts.add(Verdict.of("burning-ground-costs-more", !costed || costAt[0] > costAt[1],
                    "route cost on the burning band " + costAt[0] + " against " + costAt[1] + " on bare ground"
                            + (costed ? "" : " (control: fire route cost off)")));
            verdicts.add(Verdict.of("in-fire-is-hurt", watch.unhurtInFire == 0,
                    watch.unhurtInFire + " of " + watch.inFireSteps + " fire steps left a living body in a burning cell unhurt"));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("ticksInFire", watch.ticksInFire);
            metrics.put("hpLost", watch.hpLost);
            metrics.put("deaths", watch.deaths);
            metrics.put("arrivedEast", watch.arrivedEast);
            metrics.put("litTick", lit[0]);
            return new SceneReport(id(), "crossing", TICKS, verdicts, metrics);
        }
    }

    private static int leadX(BattleSimulation sim, long[] members) {
        int lead = -1;
        for (long m : members) if (sim.getRoster().isAliveById(m)) lead = Math.max(lead, sim.world().cellX(m));
        return lead;
    }

    private static final class Watch implements TickObserver {
        private final long[] members;
        private final long corpse;
        private final Map<Long, Float> lastHp = new HashMap<>();
        private final Map<Long, Boolean> wasInFire = new HashMap<>();
        int alive, deaths, unhurtInFire, inFireSteps, ticksInFire, corpseLingered, arrivedEast;
        float hpLost, maxStep;
        boolean corpseBurned;
        private boolean corpseInFireLastStep;

        Watch(long[] members, long corpse) {
            this.members = members;
            this.corpse = corpse;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            FireField fire = sim.fireFieldIfPresent();
            alive = 0;
            arrivedEast = 0;
            boolean step = Math.floorMod(tick, FireField.STEP_TICKS) == 0;
            for (long m : members) {
                if (!sim.getRoster().isAliveById(m)) {
                    if (lastHp.containsKey(m)) { deaths++; lastHp.remove(m); }
                    continue;
                }
                alive++;
                int cx = sim.world().cellX(m), cy = sim.world().cellY(m);
                if (cx > 80) arrivedEast++;
                boolean inFire = fire != null && fire.isBurning(cx, cy);
                if (inFire) ticksInFire++;
                if (!step) continue;
                float hp = sim.world().hp(m);
                Float before = lastHp.get(m);
                if (before != null) {
                    float lost = before - hp;
                    if (lost > 0f) { hpLost += lost; maxStep = Math.max(maxStep, lost); }
                    if (inFire) {
                        inFireSteps++;
                        if (lost <= 0f) unhurtInFire++;
                    }
                }
                lastHp.put(m, hp);
                wasInFire.put(m, inFire);
            }
            if (corpse != 0L && fire != null && step) {
                BattleComponents c = sim.getBattleComponents();
                int sprite = sim.getEntityWorld().getInt(corpse, c.SPRITE, BattleComponents.SPRITE_INDEX);
                boolean inFire = fire.isBurning((int) Math.floor(sim.world().x(corpse)), (int) Math.floor(sim.world().y(corpse)));
                if (inFire) corpseBurned = true;
                if (inFire && corpseInFireLastStep && sprite >= 0) corpseLingered++;
                corpseInFireLastStep = inFire;
            }
        }
    }
}
