package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
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
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Where a hull's sensor drones look ({@link SensorSearch}): they search the ground round an LRM carrier, find what it
 * cannot see, and shadow a moving target so the carrier keeps a spotter's call to fire on.
 *
 * <p>Three loops, each an {@link VehicleType#LRM_CARRIER} facing east with a {@link MechInternal#SENSOR_PAIR} behind a
 * short screening wall. {@code search}: three stationary Defender riflemen far to the carrier's west, behind the
 * wall's line from the hull but open to a drone standing off its end; nothing else on the Marine side looks at them.
 * The drones begin on the quadrants the hull faces, which are empty, and must go round to the west to find them.
 * {@code search-control} is the same world with no kit. {@code shadow}: a Defender tank drives a road beyond a long
 * wall; the hull has no line to any of it, and the drone that first sees it must keep it in sight as it moves so the
 * carrier can fire.
 *
 * <p>Proof that it works, not a balance reading: each verdict is a mechanism that is there or not.
 */
public final class SensorSearchScene implements BehaviorScene {

    public static final String SEARCH = "search";
    public static final String SEARCH_CONTROL = "search-control";
    public static final String SHADOW = "shadow";

    // ---- search world ----
    static final int SEARCH_WIDTH = 110;
    static final int SEARCH_HEIGHT = 60;
    static final float SEARCH_CARRIER_X = 60.5f;
    static final float SEARCH_CARRIER_Y = 30.5f;
    /** The screening wall west of the carrier: x = 50, y 24..36. */
    static final int SCREEN_X = 50;
    static final int SCREEN_Y0 = 24;
    static final int SCREEN_Y1 = 36;
    /** Three riflemen about 32 cells west of the carrier, in a file two cells apart. */
    static final int MEN_X = 26;
    static final int MEN_Y = 30;

    // ---- shadow world ----
    static final int SHADOW_WIDTH = 130;
    static final int SHADOW_HEIGHT = 70;
    static final float SHADOW_CARRIER_X = 20.5f;
    static final float SHADOW_CARRIER_Y = 35.5f;
    /** The long wall east of the carrier: x = 28, y 22..48. */
    static final int WALL_X = 28;
    static final int WALL_Y0 = 22;
    static final int WALL_Y1 = 48;
    /** The road: x = 58.5, the tank driving from y = 62.5 north to y = 8.5. */
    static final float ROAD_X = 58.5f;

    private static final int SEARCH_TICKS = 900;
    private static final int SHADOW_TICKS = 1500;
    private static final String RIFLE = "weapon.field-rifle";
    /** The carrier faces east, which the sensor pair's old station and the search's lean both read. */
    private static final float FACING_EAST = -90f;

    private final int frameEvery;

    public SensorSearchScene() {
        this(15);
    }

    public SensorSearchScene(int frameEvery) {
        this.frameEvery = frameEvery;
    }

    @Override public String id() { return "sensor-search"; }

    @Override public String label() {
        return "Sensor search: a carrier's drones go round to find riflemen the hull cannot see, and one shadows a "
                + "moving tank so the carrier keeps a spotter's call";
    }

    @Override public int frameDelayMillis() { return 90; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        Trace search = runSearch(SEARCH, List.of(MechInternal.SENSOR_PAIR), frames);
        Trace control = runSearch(SEARCH_CONTROL, List.of(), frames);
        Trace shadow = runShadow(frames);
        List<SceneReport> reports = new ArrayList<>();
        reports.add(reportSearch(search));
        reports.add(reportControl(control));
        reports.add(reportShadow(shadow));
        return reports;
    }

    private static SceneBuilder holdouts(SceneBuilder builder, int width, int height) {
        return builder
                .wall(width - 10, height - 10, width - 2, height - 10).wall(width - 10, height - 2, width - 2, height - 2)
                .wall(width - 10, height - 10, width - 10, height - 2).wall(width - 2, height - 10, width - 2, height - 2)
                .unit("defender-holdout", Faction.DEFENDER, UnitType.MARINE, width - 6, height - 6)
                .wall(width - 10, 1, width - 2, 1).wall(width - 10, 9, width - 2, 9)
                .wall(width - 10, 1, width - 10, 9).wall(width - 2, 1, width - 2, 9)
                .unit("marine-holdout", Faction.MARINE, UnitType.MARINE, width - 6, 5);
    }

    private Trace runSearch(String loop, List<MechInternal> kits, FrameSink frames) {
        SceneBuilder builder = SceneBuilder.openGround(SEARCH_WIDTH, SEARCH_HEIGHT)
                .missionCompletion(false)
                .wall(SCREEN_X, SCREEN_Y0, SCREEN_X, SCREEN_Y1)
                .squad("men").faction(Faction.DEFENDER).size(3).inFile(MEN_X, MEN_Y, 2, 0)
                        .primary(RIFLE).stationary().done();
        holdouts(builder, SEARCH_WIDTH, SEARCH_HEIGHT);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        long carrier = sim.convoy().spawnFitted(VehicleType.LRM_CARRIER, kits, Faction.MARINE,
                VehicleMission.deployed(SEARCH_CARRIER_X, SEARCH_CARRIER_Y));
        sim.convoy().body(carrier).teleport(SEARCH_CARRIER_X, SEARCH_CARRIER_Y, FACING_EAST);

        Trace trace = new Trace(world, carrier, 0L);
        SceneRun run = SceneRun.of(world).observe(trace)
                .frames(frames, loop, frameEvery, tick -> loop + " / tick " + tick + " / " + trace.caption());
        run.run(SEARCH_TICKS);
        sim.close();
        return trace;
    }

    private Trace runShadow(FrameSink frames) {
        SceneBuilder builder = SceneBuilder.openGround(SHADOW_WIDTH, SHADOW_HEIGHT)
                .missionCompletion(false)
                .wall(WALL_X, WALL_Y0, WALL_X, WALL_Y1);
        holdouts(builder, SHADOW_WIDTH, SHADOW_HEIGHT);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        long carrier = sim.convoy().spawnFitted(VehicleType.LRM_CARRIER, List.of(MechInternal.SENSOR_PAIR), Faction.MARINE,
                VehicleMission.deployed(SHADOW_CARRIER_X, SHADOW_CARRIER_Y));
        sim.convoy().body(carrier).teleport(SHADOW_CARRIER_X, SHADOW_CARRIER_Y, FACING_EAST);
        // A Defender hull takes no move order, so the tank drives the road as an ordinary delivery with no passengers.
        float[] inX = {ROAD_X, ROAD_X, ROAD_X};
        float[] inY = {62.5f, 40.5f, 8.5f};
        float[] outX = {ROAD_X, ROAD_X};
        float[] outY = {8.5f, -8f};
        long tank = sim.convoy().spawn(VehicleType.BREAKTHROUGH_TANK, Faction.DEFENDER,
                new VehicleMission(inX, inY, outX, outY, 0f, 0));

        Trace trace = new Trace(world, carrier, tank);
        SceneRun run = SceneRun.of(world).observe(trace)
                .frames(frames, SHADOW, frameEvery, tick -> SHADOW + " / tick " + tick + " / " + trace.caption());
        run.run(SHADOW_TICKS);
        sim.close();
        return trace;
    }

    // ---- reports ----

    private SceneReport reportSearch(Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("hull-blind", t.hullSawMenTicks == 0,
                "the carrier had a line to the riflemen on " + t.hullSawMenTicks + " ticks; the wall stands in it"));
        verdicts.add(Verdict.of("sensors-out", t.sensorsOut == 2, t.sensorsOut + " sensor drones flew"));
        verdicts.add(Verdict.of("quadrants-searched", t.quadrantsVisited.size() >= 3,
                "the pair held station in " + t.quadrantsVisited.size() + " of 4 quadrants round the hull: "
                        + t.quadrantsVisited));
        verdicts.add(Verdict.of("found-the-hidden", t.menSighted.size() == t.men.length && t.firstSightedTick >= 0,
                t.menSighted.size() + " of " + t.men.length + " riflemen in the side's picture, the first at tick "
                        + t.firstSightedTick + " after the pair began facing the empty east"));
        return new SceneReport(id(), SEARCH, SEARCH_TICKS, verdicts, readings(t));
    }

    private SceneReport reportControl(Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("nothing-found", t.menSighted.isEmpty(),
                "no kit: " + t.menSighted.size() + " riflemen in the side's picture"));
        return new SceneReport(id(), SEARCH_CONTROL, SEARCH_TICKS, verdicts, readings(t));
    }

    private SceneReport reportShadow(Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("hull-blind", t.hullSawTankTicks == 0,
                "the carrier had a line to the tank on " + t.hullSawTankTicks + " ticks while it was on the road"));
        verdicts.add(Verdict.of("shadow-taken", t.shadowTicks >= 120,
                "a sensor held the tank as its shadow target on " + t.shadowTicks + " ticks, first at tick "
                        + t.firstShadowTick));
        verdicts.add(Verdict.of("shadow-follows", t.shadowFollowedCells >= 12f,
                "the shadowing drone's station moved " + round(t.shadowFollowedCells) + " cells as the tank drove"));
        boolean inReach = t.reachTicks > 0;
        verdicts.add(Verdict.of("vision-held", inReach && t.spottedInReachTicks * 100 >= t.reachTicks * 60,
                t.spottedInReachTicks + " of " + t.reachTicks + " ticks with the tank inside the carrier's reach had "
                        + "a sensor drone looking at it"));
        verdicts.add(Verdict.of("carrier-fired", t.roundsAtTank > 0 && t.tankLost > 0f,
                t.roundsAtTank + " carrier rounds at the tank, which lost " + round(t.tankLost)
                        + " integrity, with the hull blind to it on the road"));
        return new SceneReport(id(), SHADOW, SHADOW_TICKS, verdicts, readings(t));
    }

    private static Map<String, Number> readings(Trace t) {
        Map<String, Number> m = new LinkedHashMap<>();
        m.put("sensorsOut", t.sensorsOut);
        m.put("quadrantsVisited", t.quadrantsVisited.size());
        m.put("menSighted", t.menSighted.size());
        m.put("firstSightedTick", t.firstSightedTick);
        m.put("hullSawTicks", t.hullSawMenTicks + t.hullSawTankTicks);
        m.put("shadowTicks", t.shadowTicks);
        m.put("firstShadowTick", t.firstShadowTick);
        m.put("shadowFollowedCells", round(t.shadowFollowedCells));
        m.put("reachTicks", t.reachTicks);
        m.put("spottedInReachTicks", t.spottedInReachTicks);
        m.put("roundsAtTank", t.roundsAtTank);
        m.put("tankLost", round(t.tankLost));
        m.put("dronesLost", t.dronesLost);
        return m;
    }

    private static float round(float v) {
        return Math.round(v * 100f) / 100f;
    }

    private static float integrity(BattleSimulation sim, long id) {
        if (!sim.world().isAlive(id)) return 0f;
        return Math.max(0f, sim.world().hp(id)) + (sim.world().hasArmor(id) ? Math.max(0f, sim.world().armor(id)) : 0f);
    }

    private static final class Trace implements TickObserver {
        final long carrier;
        final long tank;
        final long[] men;
        final Set<Long> menSighted = new HashSet<>();
        final Set<Integer> quadrantsVisited = new HashSet<>();
        final Set<Long> everSensors = new HashSet<>();
        final float range;
        int sensorsOut;
        int dronesLost;
        int firstSightedTick = -1;
        int hullSawMenTicks;
        int hullSawTankTicks;
        int shadowTicks;
        int firstShadowTick = -1;
        float shadowFollowedCells;
        private float lastShadowX = Float.NaN, lastShadowY = Float.NaN;
        int reachTicks;
        int spottedInReachTicks;
        int roundsAtTank;
        float tankStart = -1f;
        float tankLost;
        private float lastTx, lastTy;
        private String caption = "";

        Trace(SceneWorld world, long carrier, long tank) {
            this.carrier = carrier;
            this.tank = tank;
            BattleSimulation sim = world.sim();
            this.men = tank == 0L ? world.members("men") : new long[0];
            this.range = sim.convoy().vehicleType(carrier).turretStructure().mount.weapon.range;
            if (tank != 0L) {
                GroundBody tb = sim.convoy().body(tank);
                lastTx = tb.x;
                lastTy = tb.y;
            }
        }

        String caption() {
            return caption;
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            NavigationGrid grid = sim.getGrid();
            GroundBody hull = sim.convoy().body(carrier);
            int hx = (int) Math.floor(hull.x), hy = (int) Math.floor(hull.y);

            DroneWing wing = sim.droneBays().wing(carrier);
            if (wing != null) {
                for (Long d : wing.drones()) {
                    if (everSensors.add(d) && everSensors.size() > sensorsOut) sensorsOut = everSensors.size();
                    if (sim.resolveUnit(d) == 0L) continue;
                    double angle = Math.toDegrees(Math.atan2(sim.world().y(d) - hull.y, sim.world().x(d) - hull.x));
                    double a = (angle + 360.0) % 360.0;
                    if (Math.hypot(sim.world().x(d) - hull.x, sim.world().y(d) - hull.y) > 6.0) {
                        quadrantsVisited.add((int) (a / 90.0));
                    }
                }
                dronesLost = 0;
                for (Long d : everSensors) if (sim.resolveUnit(d) == 0L) dronesLost++;
            }

            for (long man : men) {
                if (!sim.world().isAlive(man)) continue;
                if (grid.hasLineOfSight(hx, hy, sim.world().cellX(man), sim.world().cellY(man))) hullSawMenTicks++;
                if (sim.hullSightings().sighted(Faction.MARINE, man) && menSighted.add(man) && firstSightedTick < 0) {
                    firstSightedTick = tick;
                }
            }

            if (tank != 0L) observeTank(sim, grid, hull, hx, hy, tick, wing);
            caption = String.format(Locale.ROOT, "sensors out %d / quadrants %d / shadow ticks %d", sensorsOut,
                    quadrantsVisited.size(), shadowTicks);
        }

        private void observeTank(BattleSimulation sim, NavigationGrid grid, GroundBody hull, int hx, int hy, int tick,
                                 DroneWing wing) {
            GroundBody tb = sim.convoy().body(tank);
            boolean tankUp = tb != null && sim.convoy().isVehicle(tank) && sim.convoy().isTargetable(tank)
                    && sim.convoy().structure(tank) > 0f;
            if (tb != null) {
                lastTx = tb.x;
                lastTy = tb.y;
            }
            if (tankStart < 0f) tankStart = integrity(sim, tank);
            if (tankUp) tankLost = Math.max(tankLost, tankStart - integrity(sim, tank));
            VehicleMission mission = sim.convoy().isVehicle(tank) ? sim.convoy().mission(tank) : null;
            if (mission != null && mission.state == VehicleState.WRECKED) tankLost = tankStart;
            boolean hullSees = tankUp && grid.hasLineOfSight(hx, hy, (int) Math.floor(lastTx), (int) Math.floor(lastTy));
            // The hull may drive to a better place (a stand-off hull does); only the road is asked of it.
            if (hullSees && lastTy > 9f) hullSawTankTicks++;
            float distance = (float) Math.hypot(lastTx - hull.x, lastTy - hull.y);
            if (tankUp && distance <= range) {
                reachTicks++;
                if (sim.hullSightings().extraSees(grid, Faction.MARINE, lastTx, lastTy)) spottedInReachTicks++;
            }
            SensorSearch search = sim.droneBays().search(carrier);
            if (search != null && search.shadowTarget() == tank && tankUp) {
                shadowTicks++;
                if (firstShadowTick < 0) firstShadowTick = tick;
                long d = search.shadowDrone();
                if (d != 0L && sim.resolveUnit(d) != 0L) {
                    float sx = sim.world().x(d), sy = sim.world().y(d);
                    if (!Float.isNaN(lastShadowX)) shadowFollowedCells += (float) Math.hypot(sx - lastShadowX, sy - lastShadowY);
                    lastShadowX = sx;
                    lastShadowY = sy;
                }
            }
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (shot.shooterId != carrier) continue;
                GroundTurret turret = sim.convoy().turret(carrier);
                long target = turret == null ? 0L : turret.burstTargetId != 0L ? turret.burstTargetId : turret.targetId;
                if (target == tank) roundsAtTank++;
            }
        }
    }
}
