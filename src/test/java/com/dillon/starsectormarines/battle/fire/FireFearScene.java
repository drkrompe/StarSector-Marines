package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.evacuation.SwarmMovement;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.FireTeamMorale;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Who gets out of the way of fire (`fire-nouns.md`, Pathing). One loop per mover:
 * <ul>
 *   <li>{@code step-out}: a squad holding a meadow as a grass fire runs at it, with
 *   bare ground a few cells beyond the meadow's edge. The marines leave the fire's
 *   way for ground that cannot burn rather than stand in it to hold.</li>
 *   <li>{@code walk-away}, {@code run-away}, {@code run-through}, {@code shaken-run}: a player's squad in a
 *   meadow a front is crossing, ordered on once the fire is on it, away from the fire or through it, walking
 *   or running, and in {@code shaken-run} with every fire team broken. The order is carried out.</li>
 *   <li>{@code beside}: a player's squad on bare ground beside a stand that burns keeps its nerve and takes
 *   the next order.</li>
 *   <li>{@code swarm-round}: runners on the prey flow field after marines beyond a
 *   burning band of brush that has a way round its south end. The field charges
 *   burning cells, so the runners go round the band rather than through it.</li>
 * </ul>
 * Verdicts are the mechanics, each switched off with its own control
 * ({@code -Dbattle.fire.stepOut=false}, {@code -Dbattle.pathfinding.fireRouteCost=false}),
 * under which the loop reports what it measured and passes. Time in fire, health
 * lost and arrivals are metrics to read against the control.
 */
public final class FireFearScene implements BehaviorScene {

    static final int WIDTH = 100, HEIGHT = 40;
    private static final long SEED = 20261022L;
    /** The longest a marine may stand in one stretch in burning cells: time to cross a front's depth. */
    private static final int LONGEST_IN_FIRE_TICKS = 75;
    /** Where the squad holds: in the meadow, bare ground beyond its east edge in reach. */
    private static final int HOLD_X = 39;
    private static final int BAND_FROM = 44, BAND_TO = 51, BAND_MAX_Y = 29;
    private static final int RUNNERS = 24;
    private static final int[][] PREY = {{92, 12}, {92, 13}, {93, 12}};

    /** Where the ordered squad stands: in the meadow, a front lit at its west edge running at it. */
    private static final int ORDERED_X = 34;
    private static final int ORDER_EAST_X = 90, ORDER_WEST_X = 6;
    private static final int ORDER_TICK = 90, RUN_TICK = 100;

    @Override public String id() { return "fire-fear"; }

    @Override public String label() { return "Marines walk out of fire, and the swarm goes round it"; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        return List.of(stepOut(frames), swarmRound(frames),
                ordered("walk-away", frames, ORDER_EAST_X, false, false),
                ordered("run-away", frames, ORDER_EAST_X, true, false),
                ordered("run-through", frames, ORDER_WEST_X, true, false),
                ordered("shaken-run", frames, ORDER_WEST_X, true, true),
                beside(frames));
    }

    private static NavigationGrid ground(CellTopology topology, int fuelFrom, int fuelTo, int fuelMaxY, boolean brush) {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        int shrub = -1;
        TileRegistry registry = TileRegistry.installed();
        if (brush && registry != null && registry.has("nature.shrub-1")) shrub = registry.indexOf("nature.shrub-1");
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                boolean fuel = x >= fuelFrom && x <= fuelTo && y <= fuelMaxY;
                topology.setGroundKind(x, y, fuel ? CellTopology.GroundKind.GRASS : CellTopology.GroundKind.DIRT);
                if (fuel && shrub >= 0) topology.setNatureOverlayIndex(x, y, shrub);
            }
        }
        return grid;
    }

    private SceneReport stepOut(FrameSink frames) throws Exception {
        int ticks = 30 * 50;
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        NavigationGrid grid = ground(topology, 20, 45, HEIGHT, false);
        SceneBuilder builder = SceneBuilder.over(grid, topology).seed(SEED).missionCompletion(false);
        builder.squad("holding").at(HOLD_X, 16).inFile(HOLD_X, 16, 0, 2).size(6).unarmed()
                .assigned(id -> ObjectiveAssignment.defendArea(id, HOLD_X, 17)).done();
        builder.unit("far", Faction.DEFENDER, UnitType.MARINE, 96, 38);
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            long[] members = world.members("holding");
            int[] stretch = new int[members.length];
            int longest = 0, ticksInFire = 0, caught = 0;
            boolean[] wasCaught = new boolean[members.length];
            float hpBefore = 0f;
            for (long m : members) hpBefore += sim.world().hp(m);
            for (int tick = 0; tick < ticks; tick++) {
                if (tick == 30) for (int y = 0; y < HEIGHT; y++) sim.fireField().addHeat(20, y, 3000);
                sim.advance(BattleSimulation.TICK_DT);
                FireField fire = sim.fireField();
                for (int i = 0; i < members.length; i++) {
                    if (!sim.getRoster().isAliveById(members[i])) { stretch[i] = 0; continue; }
                    boolean in = fire.isBurning(sim.world().cellX(members[i]), sim.world().cellY(members[i]));
                    if (in) {
                        ticksInFire++;
                        if (!wasCaught[i]) { wasCaught[i] = true; caught++; }
                        longest = Math.max(longest, ++stretch[i]);
                    } else {
                        stretch[i] = 0;
                    }
                }
                if (tick % 30 == 0) frames.frame("step-out", sim, tick, "step-out / in fire " + ticksInFire + " / tick " + tick);
            }
            int deaths = 0;
            float hpAfter = 0f;
            for (long m : members) {
                if (sim.getRoster().isAliveById(m)) hpAfter += sim.world().hp(m);
                else deaths++;
            }
            boolean on = !"false".equalsIgnoreCase(System.getProperty(FireEscape.PROPERTY));
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("walks-out-of-fire", !on || longest <= LONGEST_IN_FIRE_TICKS,
                    "longest stretch a marine stood in burning cells: " + longest + " ticks, of "
                            + caught + " the fire caught" + (on ? "" : " (control: step-out off)")));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("ticksInFire", ticksInFire);
            metrics.put("longestStretch", longest);
            metrics.put("caught", caught);
            metrics.put("hpLost", Math.round(hpBefore - hpAfter));
            metrics.put("deaths", deaths);
            return new SceneReport(id(), "step-out", ticks, verdicts, metrics);
        }
    }

    /**
     * A player's squad in a meadow a front is crossing, ordered on by the player once the fire is on it: east,
     * away from the fire, or west, through it into the burnt ground behind, and told to run when {@code run}.
     * A {@code shaken} squad has every fire team broken as the order is given: the run is a forced march,
     * and takes it through all the same.
     */
    private SceneReport ordered(String loop, FrameSink frames, int goalX, boolean run, boolean shaken) throws Exception {
        int ticks = 30 * 40;
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        NavigationGrid grid = ground(topology, 20, 60, HEIGHT, false);
        SceneBuilder builder = SceneBuilder.over(grid, topology).seed(SEED).missionCompletion(false);
        builder.squad("ordered").faction(Faction.MARINE).type(UnitType.MARINE).size(6).unarmed()
                .inFile(ORDERED_X, 14, 0, 2).done();
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            int squadId = world.squadId("ordered");
            long[] members = world.members("ordered");
            SquadMoveOrderService orders = sim.getSquadMoveOrderService();
            int arrived = -1, brokenTicks = 0, brokenRunning = 0, backwardTicks = 0, ticksInFire = 0, longest = 0;
            float lowestMorale = 1f, hpBefore = 0f;
            for (long m : members) hpBefore += sim.world().hp(m);
            boolean toggled = false;
            int[] stretch = new int[members.length];
            float[] lastX = new float[members.length];
            for (int i = 0; i < members.length; i++) lastX[i] = sim.world().x(members[i]);
            for (int tick = 0; tick < ticks; tick++) {
                if (tick == 0) for (int y = 0; y < HEIGHT; y++) sim.fireField().addHeat(20, y, 3000);
                if (tick == ORDER_TICK) orders.requestMove(squadId, goalX, 15);
                if (run && tick == RUN_TICK) toggled = orders.toggleRun(squadId);
                if (shaken && tick == ORDER_TICK) {
                    for (FireTeamMorale team : sim.getSquad(squadId).fireTeamMorale()) {
                        team.morale = 0.1f;
                        team.broken = true;
                    }
                }
                sim.advance(BattleSimulation.TICK_DT);
                FireField fire = sim.fireField();
                Squad squad = sim.getSquad(squadId);
                for (int i = 0; i < members.length; i++) {
                    long m = members[i];
                    if (!sim.getRoster().isAliveById(m)) { stretch[i] = 0; continue; }
                    if (squad != null && squad.fireTeamBroken(sim.squad().fireTeamIndex(m))) {
                        brokenTicks++;
                        if (orders.isRunning(squadId)) brokenRunning++;
                    }
                    if (squad != null) lowestMorale = Math.min(lowestMorale, squad.fireTeamMorale(sim.squad().fireTeamIndex(m)).morale);
                    float x = sim.world().x(m);
                    if (tick > RUN_TICK && Math.signum(x - lastX[i]) == -Math.signum(goalX - ORDERED_X)
                            && Math.abs(x - lastX[i]) > 1e-3f) backwardTicks++;
                    lastX[i] = x;
                    if (fire.isBurning(sim.world().cellX(m), sim.world().cellY(m))) {
                        ticksInFire++;
                        longest = Math.max(longest, ++stretch[i]);
                    } else {
                        stretch[i] = 0;
                    }
                }
                if (tick > ORDER_TICK + 1 && arrived < 0 && orders.activeOrder(squadId) == null) arrived = tick;
                if (tick % 15 == 0) frames.frame(loop, sim, tick, loop + " / tick " + tick
                        + (orders.isRunning(squadId) ? " / RUN" : ""));
            }
            int deaths = 0;
            float hpAfter = 0f;
            for (long m : members) if (!sim.getRoster().isAliveById(m)) deaths++; else hpAfter += sim.world().hp(m);
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("obeys-the-order", arrived >= 0,
                    "the order was over at tick " + arrived + (run ? ", run toggled " + toggled : "")));
            if (run) verdicts.add(Verdict.of("forced-march", toggled && brokenRunning == 0,
                    brokenRunning + " marine-ticks a fire team stood broken while its squad ran"));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("arrivedTick", arrived);
            metrics.put("brokenMarineTicks", brokenTicks);
            metrics.put("brokenWhileRunning", brokenRunning);
            metrics.put("backwardMarineTicks", backwardTicks);
            metrics.put("ticksInFire", ticksInFire);
            metrics.put("longestStretch", longest);
            metrics.put("deaths", deaths);
            metrics.put("lowestMorale", lowestMorale);
            metrics.put("hpLost", Math.round(hpBefore - hpAfter));
            return new SceneReport(id(), loop, ticks, verdicts, metrics);
        }
    }

    /**
     * A player's squad ordered onto bare ground beside a stand the fire then burns: out of the fire, burnt a
     * tenth of a burning neighbour's heat. Does it keep its nerve and take the order that follows?
     */
    private SceneReport beside(FrameSink frames) throws Exception {
        int ticks = 30 * 40;
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        NavigationGrid grid = ground(topology, 20, 60, HEIGHT, false);
        SceneBuilder builder = SceneBuilder.over(grid, topology).seed(SEED).missionCompletion(false);
        builder.squad("beside").faction(Faction.MARINE).type(UnitType.MARINE).size(6).unarmed()
                .inFile(66, 14, 0, 2).done();
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            int squadId = world.squadId("beside");
            long[] members = world.members("beside");
            SquadMoveOrderService orders = sim.getSquadMoveOrderService();
            int brokenTicks = 0, firstBroken = -1, arrived = -1, besideTicks = 0;
            float lowest = 1f, hpBefore = 0f;
            for (long m : members) hpBefore += sim.world().hp(m);
            for (int tick = 0; tick < ticks; tick++) {
                if (tick == 0) {
                    for (int y = 0; y < HEIGHT; y++) sim.fireField().addHeat(58, y, 3000);
                    orders.requestMove(squadId, 61, 19);
                }
                if (tick == 600) orders.requestMove(squadId, 90, 19);
                sim.advance(BattleSimulation.TICK_DT);
                Squad squad = sim.getSquad(squadId);
                for (long m : members) {
                    if (!sim.getRoster().isAliveById(m)) continue;
                    int team = sim.squad().fireTeamIndex(m);
                    lowest = Math.min(lowest, squad.fireTeamMorale(team).morale);
                    if (FireHarm.exposure(sim.fireField(), sim.world().cellX(m), sim.world().cellY(m)) > 0f) besideTicks++;
                    if (squad.fireTeamBroken(team)) {
                        brokenTicks++;
                        if (firstBroken < 0) firstBroken = tick;
                    }
                }
                if (tick > 601 && arrived < 0 && orders.activeOrder(squadId) == null) arrived = tick;
                if (tick % 15 == 0) frames.frame("beside", sim, tick, "beside / tick " + tick);
            }
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("keeps-its-nerve", brokenTicks == 0,
                    "lowest team morale " + lowest + "; first broken at tick " + firstBroken));
            verdicts.add(Verdict.of("takes-the-next-order", arrived >= 0, "the second order was over at tick " + arrived));
            Map<String, Number> metrics = new LinkedHashMap<>();
            float hpAfter = 0f;
            for (long m : members) if (sim.getRoster().isAliveById(m)) hpAfter += sim.world().hp(m);
            metrics.put("lowestMorale", lowest);
            metrics.put("exposedMarineTicks", besideTicks);
            metrics.put("hpLost", Math.round(hpBefore - hpAfter));
            metrics.put("brokenMarineTicks", brokenTicks);
            metrics.put("firstBrokenTick", firstBroken);
            metrics.put("arrivedTick", arrived);
            return new SceneReport(id(), "beside", ticks, verdicts, metrics);
        }
    }

    private SceneReport swarmRound(FrameSink frames) throws Exception {
        int ticks = 30 * 30;
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        NavigationGrid grid = ground(topology, BAND_FROM, BAND_TO, BAND_MAX_Y, true);
        SceneBuilder builder = SceneBuilder.over(grid, topology).seed(SEED).missionCompletion(false);
        for (int i = 0; i < PREY.length; i++) {
            builder.unit("prey" + i, Faction.MARINE, UnitType.MARINE, PREY[i][0], PREY[i][1], spec -> spec.health(100_000f));
        }
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            for (int y = 0; y <= BAND_MAX_Y; y++)
                for (int x = BAND_FROM; x <= BAND_TO; x++) sim.fireField().addHeat(x, y, 3000);
            long[] prey = new long[PREY.length];
            for (int i = 0; i < PREY.length; i++) prey[i] = world.unit("prey" + i);
            long[] runners = new long[RUNNERS];
            for (int i = 0; i < RUNNERS; i++) {
                runners[i] = sim.spawn(new EntitySpec("Runner " + i, Faction.DEFENDER, UnitType.SWARM_RUNNER,
                        6 + (i % 6), 8 + (i / 6) * 3).role(UnitRole.SWARM_PRESSURE));
            }
            int inFire = 0, arrived = 0, firstBurningTick = -1;
            int[] arrival = new int[RUNNERS];
            for (int tick = 0; tick < ticks; tick++) {
                for (int i = 0; i < prey.length; i++) {
                    if (sim.resolveUnit(prey[i]) == 0L) continue;
                    sim.world().setCellPos(prey[i], PREY[i][0], PREY[i][1]);
                    sim.world().setHp(prey[i], sim.world().maxHp(prey[i]));
                }
                sim.advance(BattleSimulation.TICK_DT);
                FireField fire = sim.fireField();
                if (firstBurningTick < 0 && fire.burningCount() > 0) firstBurningTick = tick;
                for (int i = 0; i < RUNNERS; i++) {
                    if (sim.resolveUnit(runners[i]) == 0L) continue;
                    float x = sim.world().x(runners[i]), y = sim.world().y(runners[i]);
                    if (arrival[i] == 0 && Math.hypot(x - PREY[0][0], y - PREY[0][1]) <= SwarmMovement.LOCAL_PURSUIT_RANGE) {
                        arrival[i] = tick;
                        arrived++;
                    }
                    if (fire.isBurning((int) Math.floor(x), (int) Math.floor(y))) inFire++;
                }
                if (tick % 15 == 0) frames.frame("swarm-round", sim, tick, "swarm-round / runner ticks in fire " + inFire + " / tick " + tick);
            }
            boolean on = !"false".equalsIgnoreCase(System.getProperty(FireRouteCost.PROPERTY));
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("goes-round-the-fire", !on || inFire == 0,
                    inFire + " runner ticks in burning cells, with a way round the band's south end"
                            + (on ? "" : " (control: fire route cost off)")));
            verdicts.add(Verdict.of("band-was-burning", firstBurningTick >= 0 && firstBurningTick < 30,
                    "the band caught on tick " + firstBurningTick));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("runnerTicksInFire", inFire);
            metrics.put("arrived", arrived);
            return new SceneReport(id(), "swarm-round", ticks, verdicts, metrics);
        }
    }
}
