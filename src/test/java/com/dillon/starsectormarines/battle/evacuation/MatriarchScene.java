package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.evacuation.MatriarchActions.Action;
import com.dillon.starsectormarines.battle.evacuation.MatriarchActions.Kind;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Matriarch ({@code colony-breach-nouns.md}, Matriarch) against what its two weapons are for, one loop
 * each. Verdicts assert what its actions do; who wins is a metric.
 * <ul>
 *   <li>{@code reap}: a knot of marines in front of it, too tough to die, holding their ground. It roars,
 *       closes and reaps, right scythe then left; each reap cuts the bodies in its arc after its wind-up,
 *       a handful at a time.</li>
 *   <li>{@code mech-lance}: three Bulwarks fight it with their own guns. It takes the crown to a mech,
 *       never a scythe.</li>
 *   <li>{@code fort-wall}: marines in a building whose walls stand at a fort building's strength, more
 *       than a Brute could break, with a door on the far side. It breaks in through the near wall.</li>
 *   <li>{@code walk-off}: a marine who let it come within its run's band walks straight away at a Marine's
 *       pace once it has roared. It stands crown-down for its tell, runs, and catches the marine.</li>
 *   <li>{@code far-off}: the same walk from well out of the band. It never runs, and the marine gets away.</li>
 *   <li>{@code corpses}: four Matriarchs killed where they stand, each laid in one of the four corpse poses.</li>
 * </ul>
 */
public final class MatriarchScene implements BehaviorScene {

    private static final float DEEP = 50_000f;
    /** A fort building's wall: above a Brute's light-wall limit, inside the Matriarch's. */
    private static final int FORT_BUILDING_WALL = 300;

    @Override public String id() { return "matriarch"; }

    @Override public String label() {
        return "Matriarch: the roar, the reap on infantry, the crown on mechs and walls, the run";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("reap", "mech-lance", "fort-wall", "walk-off", "far-off")) {
            reports.add(playOne(loop, frames));
        }
        reports.add(corpses(frames));
        return reports;
    }

    /** Where the {@code corpses} loop lays its four Matriarchs, and the tick it kills them. */
    static final int[][] CORPSE_CELLS = {{6, 12}, {13, 12}, {20, 12}, {27, 12}};
    private static final int KILL_TICK = 20;

    private SceneReport corpses(FrameSink frames) throws Exception {
        NavigationGrid grid = new NavigationGrid(34, 24);
        for (int y = 0; y < 24; y++) for (int x = 0; x < 34; x++) grid.setWalkableFloor(x, y);
        SceneWorld world = SceneBuilder.over(grid, new CellTopology(34, 24)).seed(20261021L)
                .missionCompletion(false).build();
        try (BattleSimulation sim = world.sim()) {
            long[] bodies = new long[CORPSE_CELLS.length];
            for (int i = 0; i < bodies.length; i++) {
                bodies[i] = sim.spawn(SwarmStrains.spec("Matriarch " + (i + 1), UnitType.SWARM_MATRIARCH,
                        CORPSE_CELLS[i][0], CORPSE_CELLS[i][1]));
            }
            TickObserver kill = (s, tick) -> {
                if (tick == KILL_TICK) {
                    for (long body : bodies) s.applyDamage(body, 0L, 1_000_000f, 100f, 0f);
                } else if (tick == KILL_TICK + 1) {
                    // A death pose is picked off the body; the review lays each in its own, in sheet order.
                    BattleComponents c = s.getBattleComponents();
                    for (int i = 0; i < bodies.length; i++) {
                        if (s.getEntityWorld().has(bodies[i], c.SPRITE)) {
                            s.getEntityWorld().setInt(bodies[i], c.SPRITE, BattleComponents.SPRITE_INDEX, i);
                        }
                    }
                }
            };
            int ticks = 90;
            SceneRun.of(world).observe(kill)
                    .frames(frames, "corpses", 3, tick -> "corpses / matriarch / tick " + tick)
                    .run(ticks);
            int lying = 0;
            for (long body : bodies) {
                if (!sim.getRoster().isAliveById(body)
                        && sim.getEntityWorld().has(body, sim.getBattleComponents().CORPSE)) lying++;
            }
            List<Verdict> verdicts = List.of(Verdict.of("lies-where-it-fell", lying == bodies.length,
                    lying + " of " + bodies.length + " killed Matriarchs left a corpse"));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("corpses", lying);
            return new SceneReport(id(), "corpses", ticks, verdicts, metrics);
        }
    }

    /** Where the walkers start, cells ahead of the Matriarch's: inside her run's band, and well out of it. */
    private static final int WALK_OFF_X = 13, FAR_OFF_X = 30;
    /** The walkers stop here, short of the edge of the ground. */
    private static final float WALK_END_X = 86f;

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        boolean building = loop.equals("fort-wall");
        boolean walking = loop.equals("walk-off") || loop.equals("far-off");
        int width = walking ? 90 : 44;
        SwarmStrainGround ground = building
                ? new SwarmStrainGround(44, 28, 26, 10, 33, 17, FORT_BUILDING_WALL).door(34, 14) : null;
        NavigationGrid grid = building ? ground.grid : new NavigationGrid(width, 24);
        CellTopology topology = building ? ground.topology : new CellTopology(width, 24);
        if (!building) {
            for (int y = 0; y < 24; y++) for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        int lane = building ? 13 : 12;
        int ticks = switch (loop) {
            case "reap" -> 600;
            case "mech-lance" -> 1800;
            case "walk-off", "far-off" -> 900;
            default -> 1200;
        };
        SceneBuilder builder = SceneBuilder.over(grid, topology).seed(20261021L).missionCompletion(false);
        switch (loop) {
            case "reap" -> builder.squad("knot").faction(Faction.MARINE).size(5).at(20, lane)
                    .stationary().health(DEEP).done();
            case "mech-lance" -> builder.squad("lance").faction(Faction.MARINE).size(3).at(26, lane)
                    .mech(MechVariant.BULWARK, MechRole.ASSAULT).done();
            case "walk-off", "far-off" -> builder.squad("walker").faction(Faction.MARINE).size(1)
                    .at(loop.equals("walk-off") ? WALK_OFF_X : FAR_OFF_X, lane).stationary().unarmed()
                    .health(DEEP).done();
            default -> builder
                    .unit("m1", Faction.MARINE, UnitType.MARINE, 29, 12, spec -> spec.health(DEEP))
                    .unit("m2", Faction.MARINE, UnitType.MARINE, 29, 15, spec -> spec.health(DEEP));
        }
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            float health = loop.equals("mech-lance") ? UnitType.SWARM_MATRIARCH.maxHp : DEEP;
            long matriarch = sim.spawn(SwarmStrains.spec("Matriarch", UnitType.SWARM_MATRIARCH, 6, lane)
                    .health(health));
            long[] watched = switch (loop) {
                case "reap" -> world.members("knot");
                case "mech-lance" -> world.members("lance");
                case "walk-off", "far-off" -> world.members("walker");
                default -> new long[]{world.unit("m1"), world.unit("m2")};
            };
            Trace trace = new Trace(matriarch, watched, ground, sim);
            SceneRun run = SceneRun.of(world);
            if (walking) run.observe(walkAway(watched[0], matriarch));
            run.observe(trace)
                    .frames(frames, loop, 3, tick -> loop + " / matriarch / tick " + tick)
                    .run(ticks);
            return report(loop, ticks, trace, sim);
        }
    }

    /**
     * Walks {@code walker} straight away from the Matriarch at a Marine's pace once she has roared, as a player
     * pulling a man back would; thrown, it gets up and walks on.
     */
    private static TickObserver walkAway(long walker, long matriarch) {
        float step = UnitType.MARINE.moveSpeed * BattleSimulation.TICK_DT;
        return (sim, tick) -> {
            if (!sim.getRoster().isAliveById(walker) || sim.stagger().isStaggered(walker)) return;
            if (sim.matriarchActions().kind(matriarch, sim.getSimTickIndex()) == Kind.ROAR) return;
            float x = sim.world().x(walker);
            if (x < WALK_END_X) sim.world().setPos(walker, Math.min(WALK_END_X, x + step), sim.world().y(walker));
        };
    }

    private SceneReport report(String loop, int ticks, Trace trace, BattleSimulation sim) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("roars-on-emergence", !trace.kinds.isEmpty() && trace.kinds.get(0) == Kind.ROAR,
                "first action " + (trace.kinds.isEmpty() ? "none" : trace.kinds.get(0))));
        verdicts.add(Verdict.of("blow-after-wind-up", trace.earlyLandings == 0,
                trace.landings + " blows landed, " + trace.earlyLandings + " before their wind-up"));
        verdicts.add(Verdict.of("holds-still-in-an-action", trace.movedInAction <= 1e-3f,
                "largest move during an action " + trace.movedInAction + " cells"));
        switch (loop) {
            case "reap" -> {
                verdicts.add(Verdict.of("reaps-infantry", trace.reaps > 0, trace.reaps + " reaps"));
                verdicts.add(Verdict.of("alternates-scythes", trace.alternates,
                        "reap order " + trace.reapOrder));
                // A reap cuts more than one only when more than one stands in its arc; when they do, it does.
                verdicts.add(Verdict.of("cuts-the-arc", trace.multiArcReaps == 0 || trace.multiCutReaps > 0,
                        trace.multiArcReaps == 0 ? "no reap found two in its arc"
                                : trace.multiCutReaps + " of " + trace.multiArcReaps
                                + " reaps with two or more in the arc cut two or more"));
                // Whoever lives through a cut is thrown: the knot stands still otherwise.
                verdicts.add(Verdict.of("throws-the-reaped", trace.bodiesCut == 0 || trace.furthestThrown >= 1f,
                        "furthest a reaped body was carried " + trace.furthestThrown + " cells"));
                verdicts.add(Verdict.of("cuts-at-most-a-handful", trace.mostCut <= SwarmMatriarchBehavior.REAP_MAX_BODIES,
                        "most cut by one reap " + trace.mostCut));
            }
            case "mech-lance" -> {
                verdicts.add(Verdict.of("crown-not-scythe-on-mechs", trace.reaps == 0,
                        trace.reaps + " reaps, " + trace.crowns + " crown blows"));
                verdicts.add(Verdict.of("crown-lands-on-a-mech", trace.crowns == 0 || trace.crownHits > 0,
                        trace.crowns == 0 ? "no crown blow (never reached a mech)"
                                : trace.crownHits + " of " + trace.crowns + " crown blows hurt a mech"));
            }
            case "walk-off" -> {
                verdicts.add(Verdict.of("tells-before-it-runs", trace.tells > 0 && !trace.ranUntold,
                        trace.tells + " tells, ran without one " + trace.ranUntold));
                verdicts.add(Verdict.of("outruns-a-walking-marine", trace.topSpeed > UnitType.MARINE.moveSpeed,
                        "top speed " + trace.topSpeed + " cells/s against a Marine's " + UnitType.MARINE.moveSpeed));
                verdicts.add(Verdict.of("catches-the-walker", trace.reaps > 0 && trace.damageDealt > 0f,
                        trace.reaps + " reaps, " + trace.damageDealt + " damage to the walker, first at tick "
                                + trace.firstHitTick));
            }
            case "far-off" -> {
                verdicts.add(Verdict.of("never-runs-at-it", trace.tells == 0, trace.tells + " tells"));
                verdicts.add(Verdict.of("the-walker-escapes", trace.damageDealt == 0f,
                        trace.damageDealt + " damage to the walker"));
            }
            default -> {
                // A crown blow at a wall lands on it: a wall too heavy for a Brute gives way to it.
                verdicts.add(Verdict.of("crowns-the-wall", trace.crowns > 0, trace.crowns + " crown blows"));
                verdicts.add(Verdict.of("breaks-what-a-brute-cannot",
                        trace.crowns == 0 || trace.ringLost() > 0,
                        "wall hp lost " + trace.ringLost() + " from walls of " + FORT_BUILDING_WALL));
                verdicts.add(Verdict.of("never-takes-the-door", !trace.usedDoor,
                        "stood in the doorway " + trace.usedDoor));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("reaps", trace.reaps);
        metrics.put("crowns", trace.crowns);
        metrics.put("bodiesCut", trace.bodiesCut);
        metrics.put("furthestThrown", trace.furthestThrown);
        metrics.put("mostCutByOneReap", trace.mostCut);
        metrics.put("matriarchHpLeft", pool0(trace.matriarch, sim));
        metrics.put("matriarchDiedTick", trace.diedTick);
        metrics.put("watchedDead", trace.watchedDead(sim));
        metrics.put("damageToWatched", trace.damageDealt);
        metrics.put("runTells", trace.tells);
        metrics.put("runTicks", trace.runTicks);
        metrics.put("topSpeed", trace.topSpeed);
        metrics.put("firstHitTick", trace.firstHitTick);
        return new SceneReport(id(), loop, ticks, verdicts, metrics);
    }

    /** What is left of a body: its health and its plate. */
    private static float pool0(long id, BattleSimulation sim) {
        if (!sim.getRoster().isAliveById(id)) return 0f;
        return sim.world().hp(id) + (sim.world().hasArmor(id) ? sim.world().armor(id) : 0f);
    }

    /** Watches the Matriarch's action record and its prey's health tick by tick. */
    private static final class Trace implements TickObserver {
        private final long matriarch;
        private final SwarmStrainGround ground;
        private final long[] watched;
        private final float[] pool;
        private final float[][] start;
        private float furthestThrown;
        private final int[] ringHp;
        private final List<Kind> kinds = new ArrayList<>();
        private final List<String> reapOrder = new ArrayList<>();
        private int lastStart = Integer.MIN_VALUE;
        private boolean lastLanded;
        private int landings, earlyLandings, reaps, crowns, crownHits, bodiesCut, mostCut;
        private int multiArcReaps, multiCutReaps;
        private int tells, runTicks, firstHitTick = -1;
        private boolean ranUntold, wasRunning;
        private float topSpeed;
        private boolean alternates = true, usedDoor;
        private float movedInAction, damageDealt;
        private float lastX = Float.NaN, lastY;
        private int diedTick = -1;

        Trace(long matriarch, long[] watched, SwarmStrainGround ground, BattleSimulation sim) {
            this.matriarch = matriarch;
            this.watched = watched;
            this.ground = ground;
            pool = new float[watched.length];
            start = new float[watched.length][];
            for (int i = 0; i < watched.length; i++) {
                pool[i] = pool(watched[i], sim);
                start[i] = new float[]{sim.world().x(watched[i]), sim.world().y(watched[i])};
            }
            if (ground != null) {
                int w = ground.grid.getWidth();
                ringHp = new int[w * ground.grid.getHeight()];
                for (int y = 0; y < ground.grid.getHeight(); y++) {
                    for (int x = 0; x < w; x++) ringHp[y * w + x] = ground.grid.getWallHp(x, y);
                }
            } else {
                ringHp = null;
            }
        }

        private float pool(long id, BattleSimulation sim) {
            return pool0(id, sim);
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            if (!sim.getRoster().isAliveById(matriarch)) {
                if (diedTick < 0) diedTick = tick;
                return;
            }
            int now = sim.getSimTickIndex();
            Action action = sim.matriarchActions().of(matriarch);
            float x = sim.world().x(matriarch), y = sim.world().y(matriarch);
            boolean acting = sim.matriarchActions().kind(matriarch, now) != Kind.NONE;
            if (acting && action.startTick != now && !Float.isNaN(lastX)) {
                movedInAction = Math.max(movedInAction, (float) Math.hypot(x - lastX, y - lastY));
            }
            if (!Float.isNaN(lastX)) {
                topSpeed = Math.max(topSpeed, (float) Math.hypot(x - lastX, y - lastY) / BattleSimulation.TICK_DT);
            }
            if (action.running) {
                runTicks++;
                // a run begins only out of its tell
                if (!wasRunning && (kinds.isEmpty() || kinds.get(kinds.size() - 1) != Kind.RUN_TELL)) ranUntold = true;
            }
            wasRunning = action.running;
            lastX = x;
            lastY = y;
            if (ground != null && sim.world().cellX(matriarch) == 34 && sim.world().cellY(matriarch) == 14) {
                usedDoor = true;
            }
            if (action.kind != Kind.NONE && action.startTick != lastStart) {
                lastStart = action.startTick;
                lastLanded = false;
                kinds.add(action.kind);
                if (action.kind == Kind.REAP_LEFT || action.kind == Kind.REAP_RIGHT) {
                    String side = action.kind == Kind.REAP_RIGHT ? "R" : "L";
                    if (!reapOrder.isEmpty() && reapOrder.get(reapOrder.size() - 1).equals(side)) alternates = false;
                    reapOrder.add(side);
                    reaps++;
                } else if (action.kind == Kind.CROWN) {
                    crowns++;
                } else if (action.kind == Kind.RUN_TELL) {
                    tells++;
                }
            }
            for (int i = 0; i < watched.length; i++) {
                if (!sim.getRoster().isAliveById(watched[i])) continue;
                furthestThrown = Math.max(furthestThrown, (float) Math.hypot(
                        sim.world().x(watched[i]) - start[i][0], sim.world().y(watched[i]) - start[i][1]));
            }
            int cut = 0;
            float[] now0 = new float[watched.length];
            for (int i = 0; i < watched.length; i++) {
                now0[i] = pool(watched[i], sim);
                if (now0[i] < pool[i] - 1e-3f) {
                    cut++;
                    if (firstHitTick < 0) firstHitTick = tick;
                    damageDealt += pool[i] - now0[i];
                }
            }
            if (action.landed && !lastLanded && action.startTick == lastStart) {
                lastLanded = true;
                landings++;
                float age = MatriarchActions.age(action, now);
                if (age + 1e-4f < action.kind.hitSeconds) earlyLandings++;
                if (action.kind == Kind.CROWN && action.breach == null && cut > 0) crownHits++;
                if (action.kind == Kind.REAP_LEFT || action.kind == Kind.REAP_RIGHT) {
                    // what stood in the arc as it cut: the bodies the reap could reach this tick
                    int inArc = SwarmMatriarchBehavior.reaped(matriarch, action.axisX, action.axisY, sim).length;
                    bodiesCut += cut;
                    mostCut = Math.max(mostCut, cut);
                    if (inArc >= 2) {
                        multiArcReaps++;
                        if (cut >= 2) multiCutReaps++;
                    }
                }
            }
            System.arraycopy(now0, 0, pool, 0, pool.length);
        }

        int ringLost() {
            if (ground == null) return 0;
            int lost = 0, w = ground.grid.getWidth();
            for (int y = 0; y < ground.grid.getHeight(); y++) {
                for (int x = 0; x < w; x++) {
                    int before = ringHp[y * w + x];
                    if (before > 0) lost += before - Math.max(0, ground.grid.getWallHp(x, y));
                }
            }
            return lost;
        }

        int watchedDead(BattleSimulation sim) {
            int dead = 0;
            for (long id : watched) if (!sim.getRoster().isAliveById(id)) dead++;
            return dead;
        }
    }
}
