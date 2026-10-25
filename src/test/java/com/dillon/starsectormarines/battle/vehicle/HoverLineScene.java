package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.drone.DroneRole;
import com.dillon.starsectormarines.battle.drone.DroneWing;
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
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Tri-Tachyon hover line's acceptance: what a skirt crosses, whom it shoves, and the drone skimmer's tether.
 *
 * <ul>
 *   <li>{@code river}: a six-cell river runs edge to edge. A {@link VehicleType#HOVER_SCOUT} and a wheeled
 *       {@link VehicleType#HEAVY_APC} are both given real player move orders to the far bank through
 *       {@link VehicleMoveOrderService}, so proof, rolling planner and physical sweep are production. The scout is
 *       accepted and floats across; the APC is refused for want of a route.</li>
 *   <li>{@code crowd} / {@code crowd-wheels}: a file of four standing marines across a hull's road. The hover scout
 *       drives through them and pushes nobody; the control is the spotter pickup on the same road, which shoves them
 *       aside. The verdict needs the hull to have actually overlapped somebody, and says so when it did not.</li>
 *   <li>{@code skimmer}: a {@link VehicleType#DRONE_SKIMMER} parked within reach of a three-rifle picket. Its bay
 *       launches its complement one at a time, the drones stay on their leash and fight, and the hull itself fires
 *       nothing. The strike drone goes at the picket, detonates, and the bay rebuilds it. Who wins the exchange is a
 *       reading, not a verdict.</li>
 *   <li>{@code skimmer-loss}: the same hull with nobody to fight, destroyed once its drones are out: every drone
 *       still flying comes down with it, and none launches afterwards.</li>
 *   <li>{@code sensors}: a hover scout parked behind a wall with a defender beyond it. Its sensor pair holds station
 *       ahead of the hull, over the wall, and the side's picture records the defender the hull cannot see.</li>
 *   <li>{@code halo}: a drone skimmer under a stream of rockets with no shooter to answer. Its halo drones orbit
 *       tight round it and their point-defence lasers take rounds out of the air.</li>
 * </ul>
 */
public final class HoverLineScene implements BehaviorScene {

    public static final String RIVER = "river";
    public static final String CROWD = "crowd";
    public static final String CROWD_WHEELS = "crowd-wheels";
    public static final String SKIMMER = "skimmer";
    public static final String SKIMMER_LOSS = "skimmer-loss";
    public static final String SENSORS = "sensors";
    public static final String HALO = "halo";

    static final int WIDTH = 64;
    static final int HEIGHT = 48;
    static final int RIVER_Y0 = 20;
    static final int RIVER_Y1 = 25;
    static final float SCOUT_X = 20.5f, SCOUT_Y = 9.5f;
    static final float APC_X = 42.5f, APC_Y = 9.5f;
    static final int SCOUT_GOAL_X = 20, SCOUT_GOAL_Y = 38;
    static final int APC_GOAL_X = 42, APC_GOAL_Y = 38;

    static final float ROAD_Y = 24.5f;
    static final float CROWD_START_X = 8.5f;
    static final int CROWD_GOAL_X = 54;
    static final int CROWD_X0 = 26;

    static final float SKIMMER_X = 16.5f, SKIMMER_Y = 24.5f;
    static final int PICKET_X = 34, PICKET_Y = 23;
    static final int SKIMMER_LOSS_TICK = 300;
    static final int SKIMMER_Y_CELL = 24;
    static final int SENSOR_WALL_X = 21;
    /** West edge of the sealed holdout box; nothing west of it is wall except the sensors loop's own. */
    static final int HOLDOUT_X0 = 54;
    static final int LURKER_X = 32;
    static final int HALO_BARRAGE_TICK = 240;

    private static final String RIFLE = "weapon.field-rifle";
    private static final int TICKS = 1500;
    private static final float THROUGH_Y = RIVER_Y1 + 5f;

    private final int frameEvery;

    public HoverLineScene() { this(15); }

    public HoverLineScene(int frameEvery) { this.frameEvery = frameEvery; }

    @Override public String id() { return "hover-line"; }

    @Override public String label() {
        return "Tri-Tachyon hover line: a hover scout floats across a river a wheeled APC is refused, drives through "
                + "standing marines without shoving them (control: the spotter pickup), and a drone skimmer's tethered "
                + "drones launch, fight on their leash and fall with the hull";
    }

    @Override public int frameDelayMillis() { return 90; }

    @Override public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        reports.add(river(frames));
        reports.add(crowd(CROWD, VehicleType.HOVER_SCOUT, frames));
        reports.add(crowd(CROWD_WHEELS, VehicleType.SCOUT_CAR, frames));
        reports.add(skimmer(SKIMMER, true, -1, frames));
        reports.add(skimmer(SKIMMER_LOSS, false, SKIMMER_LOSS_TICK, frames));
        reports.add(sensors(frames));
        reports.add(halo(frames));
        return reports;
    }

    /** The river: open water edge to edge, stamped as generation stamps water. */
    static void stampRiver(NavigationGrid grid, CellTopology topology) {
        for (int y = RIVER_Y0; y <= RIVER_Y1; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkable(x, y, false);
                grid.setEdgeCoverSuppressed(x, y, true);
                topology.setGroundKind(x, y, GroundKind.WATER);
            }
        }
    }

    /** Open ground is dirt, as a generated map's is, so a hull raises dust and a skirt its haze; water stays water. */
    static void dirt(CellTopology topology) {
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (topology.getGroundKind(x, y) == GroundKind.VOID) topology.setGroundKind(x, y, GroundKind.DIRT);
            }
        }
    }

    /** A sealed box in the far corner holding the one defender that keeps the battle ticking. */
    private static SceneBuilder holdout(SceneBuilder builder) {
        return builder.missionCompletion(false)
                .wall(HOLDOUT_X0, 40, 62, 40).wall(HOLDOUT_X0, 47, 62, 47).wall(HOLDOUT_X0, 40, HOLDOUT_X0, 47).wall(62, 40, 62, 47)
                .unit("holdout", Faction.DEFENDER, UnitType.MARINE, 58, 44);
    }

    private SceneReport river(FrameSink frames) {
        NavigationGrid field = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology ground = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) field.setWalkableFloor(x, y);
        stampRiver(field, ground);
        SceneWorld world = holdout(SceneBuilder.over(field, ground)).build();
        dirt(world.sim().getTopology());
        BattleSimulation sim = world.sim();
        NavigationGrid grid = sim.getGrid();
        NavigationGrid skirtView = HoverSkirt.hoverView(grid, sim.getTopology());

        VehicleMoveOrderService orders = sim.getVehicleMoveOrderService();
        long scout = spawn(sim, VehicleType.HOVER_SCOUT, SCOUT_X, SCOUT_Y);
        long apc = spawn(sim, VehicleType.HEAVY_APC, APC_X, APC_Y);
        orders.requestMove(scout, SCOUT_GOAL_X, SCOUT_GOAL_Y);
        orders.requestMove(apc, APC_GOAL_X, APC_GOAL_Y);
        sim.advance(BattleSimulation.TICK_DT);
        boolean scoutAccepted = orders.activeOrder(scout) != null;
        VehicleMoveOrderService.RefusedOrder refused = orders.refusal(apc);
        boolean apcRefused = refused != null && refused.reason() == VehicleMoveOrderService.Refusal.NO_ROUTE
                && orders.activeOrder(apc) == null;

        GroundBody scoutBody = sim.convoy().body(scout);
        GroundBody apcBody = sim.convoy().body(apc);
        int waterTicks = 0, throughTick = -1;
        boolean scoutLegal = true, apcDry = true;
        float apcMaxY = apcBody.y;
        for (int tick = 0; tick < TICKS; tick++) {
            sim.advance(BattleSimulation.TICK_DT);
            frames.advanced(RIVER, sim, tick);
            int cx = (int) Math.floor(scoutBody.x), cy = (int) Math.floor(scoutBody.y);
            if (grid.inBounds(cx, cy) && sim.getTopology().getGroundKind(cx, cy) == GroundKind.WATER) waterTicks++;
            if (throughTick < 0 && scoutBody.y > THROUGH_Y) throughTick = tick;
            scoutLegal &= VehicleFootprint.isPoseFeasible(scoutBody.x, scoutBody.y, scoutBody.facingDegrees,
                    VehicleType.HOVER_SCOUT.visualLengthCells, VehicleType.HOVER_SCOUT.visualWidthCells, skirtView);
            apcDry &= VehicleFootprint.isPoseFeasible(apcBody.x, apcBody.y, apcBody.facingDegrees,
                    VehicleType.HEAVY_APC.visualLengthCells, VehicleType.HEAVY_APC.visualWidthCells, grid);
            apcMaxY = Math.max(apcMaxY, apcBody.y);
            if (tick % frameEvery == 0) {
                frames.frame(RIVER, sim, tick, String.format(Locale.ROOT,
                        "river / tick %d / hover scout y %.1f / APC %s", tick, scoutBody.y,
                        apcRefused ? "refused: no route" : "ordered"));
            }
        }

        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("scout-route-proved", scoutAccepted,
                "the hover scout's order to the far bank is accepted over water its skirt floats on"));
        verdicts.add(Verdict.of("scout-crossed", throughTick >= 0 && waterTicks > 0,
                "the scout's centre was over open water for " + waterTicks + " ticks and reached the far bank at tick "
                        + throughTick));
        verdicts.add(Verdict.of("scout-pose-legal", scoutLegal,
                "every applied scout pose clears the hover view: water open, nothing else"));
        verdicts.add(Verdict.of("wheels-refused", apcRefused,
                "the APC's order across the river is refused for want of a route"));
        verdicts.add(Verdict.of("wheels-stay-dry", apcDry && apcMaxY < RIVER_Y0,
                "every applied APC pose clears the live grid; furthest south y="
                        + String.format(Locale.ROOT, "%.1f", apcMaxY)));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("scoutWaterTicks", waterTicks);
        metrics.put("scoutThroughTick", throughTick);
        metrics.put("scoutFinalX", scoutBody.x);
        metrics.put("scoutFinalY", scoutBody.y);
        metrics.put("apcMaxY", apcMaxY);
        sim.close();
        return new SceneReport(id(), RIVER, TICKS, verdicts, metrics);
    }

    private SceneReport crowd(String loop, VehicleType type, FrameSink frames) {
        SceneWorld world = holdout(SceneBuilder.openGround(WIDTH, HEIGHT))
                .squad("crowd").size(4).inFile(CROWD_X0, (int) ROAD_Y, 4, 0).primary(RIFLE).stationary().done()
                .build();
        dirt(world.sim().getTopology());
        BattleSimulation sim = world.sim();
        long[] crowd = world.members("crowd");
        long hull = spawn(sim, type, CROWD_START_X, ROAD_Y);
        sim.convoy().body(hull).teleport(CROWD_START_X, ROAD_Y, -90f);
        long pushesBefore = sim.vehicleRepulsion().pushes();
        sim.getVehicleMoveOrderService().requestMove(hull, CROWD_GOAL_X, (int) ROAD_Y);

        GroundBody body = sim.convoy().body(hull);
        Set<Long> overlapped = new HashSet<>();
        int overlapTicks = 0;
        for (int tick = 0; tick < TICKS / 2; tick++) {
            sim.advance(BattleSimulation.TICK_DT);
            frames.advanced(loop, sim, tick);
            for (long id : crowd) {
                if (!sim.world().isAlive(id)) continue;
                if (inside(body, type, sim.world().x(id), sim.world().y(id))) {
                    overlapped.add(id);
                    overlapTicks++;
                }
            }
            if (tick % frameEvery == 0) {
                frames.frame(loop, sim, tick, String.format(Locale.ROOT, "%s / tick %d / %s x %.1f / bodies shoved %d",
                        loop, tick, type.name(), body.x, sim.vehicleRepulsion().pushes() - pushesBefore));
            }
        }
        long pushes = sim.vehicleRepulsion().pushes() - pushesBefore;

        List<Verdict> verdicts = new ArrayList<>();
        boolean drove = body.x > CROWD_X0 + 12f;
        if (type.isHover()) {
            verdicts.add(Verdict.of("pushes-nobody", pushes == 0,
                    "the hover scout displaced " + pushes + " bodies; it drove "
                            + (overlapped.isEmpty() ? "past without ever overlapping a marine, so this says little"
                            : "over " + overlapped.size() + " standing marines for " + overlapTicks + " body-ticks")));
            verdicts.add(Verdict.of("drove-through", drove && !overlapped.isEmpty(),
                    "the scout passed through the file: final x " + String.format(Locale.ROOT, "%.1f", body.x)
                            + ", " + overlapped.size() + " marines under the skirt at some tick"));
        } else {
            verdicts.add(Verdict.of("wheels-shove", pushes > 0,
                    "the wheeled control displaced " + pushes + " bodies out of its way"));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("pushes", pushes);
        metrics.put("marinesOverlapped", overlapped.size());
        metrics.put("overlapBodyTicks", overlapTicks);
        metrics.put("hullFinalX", body.x);
        sim.close();
        return new SceneReport(id(), loop, TICKS / 2, verdicts, metrics);
    }

    private SceneReport skimmer(String loop, boolean picketed, int lossTick, FrameSink frames) {
        SceneBuilder builder = holdout(SceneBuilder.openGround(WIDTH, HEIGHT));
        if (picketed) {
            builder.squad("picket").faction(Faction.DEFENDER).size(3).inFile(PICKET_X, PICKET_Y, 0, 2)
                    .primary(RIFLE).stationary().done();
        }
        SceneWorld world = builder.build();
        dirt(world.sim().getTopology());
        BattleSimulation sim = world.sim();
        long hull = spawn(sim, VehicleType.DRONE_SKIMMER, SKIMMER_X, SKIMMER_Y);
        sim.convoy().body(hull).teleport(SKIMMER_X, SKIMMER_Y, -90f);
        long[] picket = picketed ? world.members("picket") : new long[0];
        float[] picketFull = new float[picket.length];
        for (int i = 0; i < picket.length; i++) picketFull[i] = durability(sim, picket[i]);
        int complement = VehicleType.DRONE_SKIMMER.droneComplement();
        // With nobody in reach the strike drone has no target, so it stays in the bay.
        int held = picketed ? 0 : (int) VehicleType.DRONE_SKIMMER.droneBay().stream()
                .filter(r -> r == DroneRole.STRIKE).count();
        int ticks = lossTick > 0 ? lossTick + 120 : TICKS;

        Set<Long> drones = new HashSet<>();
        int allOutTick = -1, droneShots = 0, hullShots = 0, aliveAfterLoss = -1, flyingAtLoss = 0, dronesAtLoss = 0;
        int firstLossTick = -1;
        int strikeLaunchTick = -1, strikeSpentTick = -1, rebuiltTick = -1;
        Set<Long> strikers = new HashSet<>();
        float maxStray = 0f;
        GroundBody body = sim.convoy().body(hull);
        for (int tick = 0; tick < ticks; tick++) {
            if (tick == lossTick) {
                for (long id : drones) if (sim.world().isAlive(id)) flyingAtLoss++;
                dronesAtLoss = drones.size();
                sim.applyDamage(hull, 1.0e6f, 1.0e6f);
            }
            sim.advance(BattleSimulation.TICK_DT);
            frames.advanced(loop, sim, tick);
            DroneWing wing = sim.droneBays().wing(hull);
            if (wing != null) {
                drones.addAll(wing.drones());
                if (allOutTick < 0 && wing.inBay() <= held) allOutTick = tick;
                long striker = wing.strikeDrone();
                if (striker != 0L && strikers.add(striker) && strikers.size() == 1) strikeLaunchTick = tick;
                if (strikeSpentTick >= 0 && rebuiltTick < 0 && (wing.strikesReady() > 0 || strikers.size() > 1)) {
                    rebuiltTick = tick;
                }
                if (strikeSpentTick < 0 && sim.droneBays().strikesDetonated() > 0) strikeSpentTick = tick;
            }
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (drones.contains(shot.shooterId)) droneShots++;
                if (shot.shooterId == hull) hullShots++;
            }
            boolean hullAlive = lossTick < 0 || tick < lossTick;
            int alive = 0;
            for (long id : drones) {
                if (!sim.world().isAlive(id)) continue;
                alive++;
                if (hullAlive && !strikers.contains(id)) {
                    maxStray = Math.max(maxStray, (float) Math.hypot(sim.world().x(id) - body.x,
                            sim.world().y(id) - body.y));
                }
            }
            if (hullAlive && firstLossTick < 0 && alive < drones.size()) firstLossTick = tick;
            if (lossTick > 0 && tick == lossTick + 2) aliveAfterLoss = alive;
            if (tick % frameEvery == 0) {
                frames.frame(loop, sim, tick, String.format(Locale.ROOT,
                        "%s / tick %d / drones launched %d of %d / flying %d / hull %s", loop, tick, drones.size(),
                        complement, alive, hullAlive ? "alive" : "destroyed"));
            }
        }

        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("complement-launched", drones.size() >= complement - held && allOutTick >= 0,
                drones.size() + " of " + complement + " drones launched (" + held + " strike held for want of a"
                        + " target), the bay done at tick " + allOutTick
                        + " (one every " + DroneWing.LAUNCH_INTERVAL + " s)"));
        verdicts.add(Verdict.of("hull-unarmed", hullShots == 0, "the hull itself fired " + hullShots + " shots"));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("dronesLaunched", drones.size());
        metrics.put("bayEmptyTick", allOutTick);
        metrics.put("droneShots", droneShots);
        metrics.put("maxStrayCells", maxStray);
        if (picketed) {
            int dronesLost = 0;
            for (long id : drones) if (!sim.world().isAlive(id)) dronesLost++;
            float damage = 0f;
            int dead = 0;
            for (int i = 0; i < picket.length; i++) {
                if (!sim.world().isAlive(picket[i])) dead++;
                damage += picketFull[i] - (sim.world().isAlive(picket[i]) ? durability(sim, picket[i]) : 0f);
            }
            int detonations = sim.droneBays().strikesDetonated();
            verdicts.add(Verdict.of("strike-launched", strikeLaunchTick >= 0,
                    "the strike drone left the bay at tick " + strikeLaunchTick + " with the picket in reach"));
            verdicts.add(Verdict.of("strike-detonated", detonations > 0,
                    detonations + " strike drones detonated on their target, the first at tick " + strikeSpentTick));
            float expected = DroneWing.STRIKE_REBUILD_SECONDS / BattleSimulation.TICK_DT;
            verdicts.add(Verdict.of("strike-rebuilt", strikeSpentTick >= 0 && rebuiltTick >= 0
                            && Math.abs(rebuiltTick - strikeSpentTick - expected) <= 3f,
                    "spent at tick " + strikeSpentTick + ", a new strike drone ready or away at tick " + rebuiltTick
                            + " (rebuild " + DroneWing.STRIKE_REBUILD_SECONDS + " s)"));
            metrics.put("strikeLaunchTick", strikeLaunchTick);
            metrics.put("strikesDetonated", detonations);
            verdicts.add(Verdict.of("drones-tethered", maxStray <= DroneWing.LEASH_CELLS + 4f,
                    "while the hull lived no drone but the striker strayed beyond " + String.format(Locale.ROOT, "%.1f", maxStray)
                            + " cells of it (leash " + DroneWing.LEASH_CELLS + ")"));
            verdicts.add(Verdict.of("drones-fight", droneShots > 0,
                    "the drones fired " + droneShots + " shots at the picket"));
            metrics.put("dronesLost", dronesLost);
            metrics.put("firstDroneLostTick", firstLossTick);
            metrics.put("picketDamageTaken", damage);
            metrics.put("picketDead", dead);
        } else {
            verdicts.add(Verdict.of("fall-with-hull", flyingAtLoss > 0 && aliveAfterLoss == 0
                            && drones.size() == dronesAtLoss,
                    flyingAtLoss + " drones were flying when the hull was destroyed; two ticks later " + aliveAfterLoss
                            + " still flew, and " + (drones.size() - dronesAtLoss) + " launched afterwards"));
            metrics.put("flyingAtLoss", flyingAtLoss);
        }
        sim.close();
        return new SceneReport(id(), loop, ticks, verdicts, metrics);
    }

    private SceneReport sensors(FrameSink frames) {
        SceneWorld world = holdout(SceneBuilder.openGround(WIDTH, HEIGHT))
                .wall(SENSOR_WALL_X, SKIMMER_Y_CELL - 6, SENSOR_WALL_X, SKIMMER_Y_CELL + 6)
                .unit("lurker", Faction.DEFENDER, UnitType.MARINE, LURKER_X, SKIMMER_Y_CELL)
                .build();
        dirt(world.sim().getTopology());
        BattleSimulation sim = world.sim();
        long hull = spawn(sim, VehicleType.HOVER_SCOUT, SKIMMER_X, SKIMMER_Y);
        sim.convoy().body(hull).teleport(SKIMMER_X, SKIMMER_Y, -90f);
        long lurker = world.unit("lurker");
        GroundBody body = sim.convoy().body(hull);
        float halfLength = VehicleType.HOVER_SCOUT.visualLengthCells * .5f;
        int ticks = 600, hiddenSightings = 0, firstHiddenSighting = -1, aheadSamples = 0, stationed = 0, hiddenReveals = 0;
        Set<Long> sensors = new HashSet<>();
        for (int tick = 0; tick < ticks; tick++) {
            sim.advance(BattleSimulation.TICK_DT);
            frames.advanced(SENSORS, sim, tick);
            DroneWing wing = sim.droneBays().wing(hull);
            if (wing != null) sensors.addAll(wing.drones());
            boolean lurking = sim.world().isAlive(lurker);
            boolean hullSees = lurking && HullSightings.sees(sim.getGrid(), body.x, body.y, sim.world().x(lurker),
                    sim.world().y(lurker), VehicleType.HOVER_SCOUT.sightCells());
            boolean sighted = lurking && sim.hullSightings().sighted(Faction.MARINE, lurker);
            boolean revealed = lurking && sim.getFogOfWar().isCellRevealed(
                    (int) Math.floor(sim.world().x(lurker)), (int) Math.floor(sim.world().y(lurker)));
            if (revealed && !hullSees) hiddenReveals++;
            if (sighted && !hullSees) {
                hiddenSightings++;
                if (firstHiddenSighting < 0) firstHiddenSighting = tick;
            }
            if (tick >= ticks - 300) {
                for (long id : sensors) {
                    if (!sim.world().isAlive(id)) continue;
                    aheadSamples++;
                    // The hull faces +X: how far beyond its bow the sensor flies.
                    if (sim.world().x(id) - body.x >= halfLength + DroneWing.SENSOR_AHEAD_CELLS - 3f) stationed++;
                }
            }
            if (tick % frameEvery == 0) {
                frames.frame(SENSORS, sim, tick, String.format(Locale.ROOT,
                        "%s / tick %d / sensors out %d / hull sees the lurker: %s / side's picture: %s", SENSORS,
                        tick, sensors.size(), hullSees ? "yes" : "no", sighted ? "sighted" : "-"));
            }
        }
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("sensor-pair-launched", sensors.size() == 2,
                sensors.size() + " sensor drones left the scout's bay"));
        verdicts.add(Verdict.of("sensors-hold-ahead", aheadSamples > 0 && stationed >= aheadSamples * .8f,
                stationed + " of " + aheadSamples + " sensor samples over the last 300 ticks were at least "
                        + (DroneWing.SENSOR_AHEAD_CELLS - 3f) + " cells beyond the bow"));
        verdicts.add(Verdict.of("sensors-see-past-the-wall", hiddenSightings > 0,
                "the side's picture held the lurker the hull could not see on " + hiddenSightings
                        + " ticks, the first at tick " + firstHiddenSighting));
        verdicts.add(Verdict.of("sensors-reveal-past-the-wall", hiddenReveals > 0,
                "the player's fog held the lurker's cell, which the hull could not see, on " + hiddenReveals + " ticks"));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("hiddenRevealTicks", hiddenReveals);
        metrics.put("hiddenSightingTicks", hiddenSightings);
        metrics.put("firstHiddenSighting", firstHiddenSighting);
        metrics.put("lurkerAlive", sim.world().isAlive(lurker) ? 1 : 0);
        sim.close();
        return new SceneReport(id(), SENSORS, ticks, verdicts, metrics);
    }

    private SceneReport halo(FrameSink frames) {
        SceneWorld world = holdout(SceneBuilder.openGround(WIDTH, HEIGHT)).build();
        dirt(world.sim().getTopology());
        BattleSimulation sim = world.sim();
        long hull = spawn(sim, VehicleType.DRONE_SKIMMER, SKIMMER_X, SKIMMER_Y);
        sim.convoy().body(hull).teleport(SKIMMER_X, SKIMMER_Y, -90f);
        int ticks = 900, fired = 0, intercepted = 0, haloShots = 0;
        List<Projectile> rounds = new ArrayList<>();
        Set<Long> halos = new HashSet<>();
        for (int tick = 0; tick < ticks; tick++) {
            if (tick >= HALO_BARRAGE_TICK && tick % 15 == 0) {
                float spread = ((tick / 15) % 5 - 2) * .6f;
                Projectile round = new Projectile(SKIMMER_X + 16f, SKIMMER_Y + 8f + spread, SKIMMER_X + spread,
                        SKIMMER_Y, false, 1.5f, Faction.DEFENDER, false, 1.6f, null,
                        "weapon.mech-pioneer-rocket", true);
                sim.queueProjectile(round);
                rounds.add(round);
                fired++;
            }
            sim.advance(BattleSimulation.TICK_DT);
            frames.advanced(HALO, sim, tick);
            DroneWing wing = sim.droneBays().wing(hull);
            if (wing != null) {
                for (long id : wing.drones()) if (wing.role(id) == DroneRole.HALO) halos.add(id);
            }
            for (ShotEvent shot : sim.getShotsThisFrame()) if (halos.contains(shot.shooterId)) haloShots++;
            if (tick % frameEvery == 0) {
                int stopped = 0;
                for (Projectile r : rounds) if (r.intercepted) stopped++;
                frames.frame(HALO, sim, tick, String.format(Locale.ROOT,
                        "%s / tick %d / halo drones %d / rockets in %d / taken out of the air %d", HALO, tick,
                        halos.size(), fired, stopped));
            }
        }
        for (Projectile r : rounds) if (r.intercepted) intercepted++;
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("halo-launched", halos.size() == 2, halos.size() + " halo drones left the bay"));
        verdicts.add(Verdict.of("halo-engages", haloShots > 0,
                "the halo lasers fired " + haloShots + " point-defence bursts at incoming rockets"));
        verdicts.add(Verdict.of("halo-intercepts", intercepted > 0,
                intercepted + " of " + fired + " rockets were taken out of the air"));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("rocketsFired", fired);
        metrics.put("rocketsIntercepted", intercepted);
        metrics.put("haloShots", haloShots);
        sim.close();
        return new SceneReport(id(), HALO, ticks, verdicts, metrics);
    }

    private static float durability(BattleSimulation sim, long id) {
        return sim.world().hp(id) + (sim.world().hasArmor(id) ? sim.world().armor(id) : 0f);
    }

    private static long spawn(BattleSimulation sim, VehicleType type, float x, float y) {
        long id = sim.convoy().spawn(type, Faction.MARINE, VehicleMission.deployed(x, y));
        sim.convoy().body(id).teleport(x, y, 0f);
        return id;
    }

    /** Whether a point lies under the hull's oriented footprint. */
    static boolean inside(GroundBody body, VehicleType type, float px, float py) {
        double a = Math.toRadians(body.facingDegrees);
        float dx = px - body.x, dy = py - body.y;
        // Facing 0 is +Y; forward is (-sin, cos).
        float forward = (float) (-dx * Math.sin(a) + dy * Math.cos(a));
        float side = (float) (dx * Math.cos(a) + dy * Math.sin(a));
        return Math.abs(forward) <= type.visualLengthCells * .5f && Math.abs(side) <= type.visualWidthCells * .5f;
    }
}
