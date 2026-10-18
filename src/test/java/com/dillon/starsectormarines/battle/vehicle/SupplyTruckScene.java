package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.logistics.ResupplyCache;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The supply truck's acceptance: a {@link VehicleType#SUPPLY_TRUCK} hands its stock to a spent squad standing beside
 * it, one grenade at a time through the battle's ordinary {@code ResupplySystem}, and only while it is halted.
 *
 * <p>Four loops, one world each. Every loop has two squads of grenadiers whose frag grenades are all thrown before
 * the first tick — a <em>near</em> squad a couple of cells from where the truck stands, and a <em>far</em> squad well
 * outside the service radius — and no enemy anywhere they can see, so nothing they hold is spent during the run and
 * every use they gain was handed to them.
 *
 * <ul>
 *   <li>{@code parked}: the truck stands halted beside the near squad from the first tick.</li>
 *   <li>{@code driving}: the truck is ordered from the west edge to a halt beside the near squad, passing within
 *       reach of it on the way in at speed.</li>
 *   <li>{@code wrecked}: as parked, and the truck is destroyed once a few grenades have changed hands.</li>
 *   <li>{@code control}: the same two spent squads and no truck.</li>
 * </ul>
 *
 * <p>The verdicts are mechanics: who was in reach got stock, who was not got none, the stock fell by exactly what was
 * handed out, nothing came off a moving truck, and nothing came off a wreck. The truck is a production chassis under
 * an ordinary {@link VehicleMoveOrderService} order; nothing here hands a grenade to anybody.
 */
public final class SupplyTruckScene implements BehaviorScene {

    public static final String PARKED = "parked";
    public static final String DRIVING = "driving";
    public static final String WRECKED = "wrecked";
    public static final String CONTROL = "control";

    static final int WIDTH = 64;
    static final int HEIGHT = 40;

    /** Where the truck halts: in every loop that has one. */
    public static final float TRUCK_X = 30.5f;
    public static final float TRUCK_Y = 18.5f;
    /** Where the driving loop's truck starts, facing east along the same row. */
    public static final float DRIVE_START_X = 6.5f;

    /** The near squad's cluster origin: four in a row from {@code (x - 2, y)}, about three cells off the truck. */
    public static final int NEAR_X = 31;
    public static final int NEAR_Y = 21;
    /** The far squad: well outside the four-cell service radius of anywhere the truck goes. */
    public static final int FAR_X = 14;
    public static final int FAR_Y = 31;
    static final int SQUAD_SIZE = 4;

    private static final String NEAR = "near";
    private static final String FAR = "far";
    private static final String RIFLE = "weapon.field-rifle";
    private static final String GRENADE = SpecialEquipmentRegistry.FRAG_GRENADE_ID;
    /** The driving loop's order is given on this tick, so the first frames show the truck standing at the edge. */
    private static final int ORDER_TICK = 10;
    /** The wrecked loop destroys the truck once this many grenades have changed hands. */
    private static final int WRECK_AFTER_TRANSFERS = 4;
    private static final int TICKS = 600;
    private static final int DRIVING_TICKS = 900;

    private final int frameEvery;

    public SupplyTruckScene() {
        this(10);
    }

    public SupplyTruckScene(int frameEvery) {
        this.frameEvery = frameEvery;
    }

    @Override public String id() { return "supply-truck"; }

    @Override public String label() {
        return "Supply truck: a halted pickup hands its crates to a spent squad beside it and to nobody out of reach; "
                + "nothing comes off it while it drives, and nothing after it is wrecked";
    }

    @Override public int frameDelayMillis() { return 80; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        reports.add(report(PARKED, run(PARKED, frames)));
        reports.add(report(DRIVING, run(DRIVING, frames)));
        reports.add(report(WRECKED, run(WRECKED, frames)));
        reports.add(report(CONTROL, run(CONTROL, frames)));
        return reports;
    }

    private Trace run(String loop, FrameSink frames) {
        SceneWorld world = SceneBuilder.openGround(WIDTH, HEIGHT)
                .missionCompletion(false)
                // A sealed box in the far corner: the one defender nobody can see or reach, so the side stays present
                // and nobody on the field has anything to throw a grenade at.
                .wall(54, 30, 62, 30).wall(54, 38, 62, 38).wall(54, 30, 54, 38).wall(62, 30, 62, 38)
                .unit("holdout", Faction.DEFENDER, UnitType.MARINE, 58, 34)
                .squad(NEAR).size(SQUAD_SIZE).at(NEAR_X, NEAR_Y).primary(RIFLE).special(GRENADE, SQUAD_SIZE)
                .stationary().done()
                .squad(FAR).size(SQUAD_SIZE).at(FAR_X, FAR_Y).primary(RIFLE).special(GRENADE, SQUAD_SIZE)
                .stationary().done()
                .build();
        BattleSimulation sim = world.sim();
        // Every grenade already thrown: both squads need supply from the first tick.
        for (long id : world.members(NEAR)) sim.world().setSecondaryAmmo(id, 0);
        for (long id : world.members(FAR)) sim.world().setSecondaryAmmo(id, 0);

        long truck = 0L;
        if (!loop.equals(CONTROL)) {
            float startX = loop.equals(DRIVING) ? DRIVE_START_X : TRUCK_X;
            truck = sim.convoy().spawn(VehicleType.SUPPLY_TRUCK, Faction.MARINE,
                    VehicleMission.deployed(startX, TRUCK_Y));
            // Facing east, the way the driving loop's truck travels.
            sim.convoy().body(truck).teleport(startX, TRUCK_Y, -90f);
            sim.convoy().body(truck).speed = 0f;
        }

        Trace trace = new Trace(loop, world, truck);
        SceneRun run = SceneRun.of(world).observe(trace)
                .frames(frames, loop, frameEvery, tick -> loop + " / tick " + tick + " / " + trace.caption());
        int ticks = loop.equals(DRIVING) ? DRIVING_TICKS : TICKS;
        run.run(ticks);
        trace.observe(sim, ticks);
        trace.ticks = ticks;
        sim.close();
        return trace;
    }

    private SceneReport report(String loop, Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case PARKED -> {
                verdicts.add(resupplied(t));
                verdicts.add(farUntouched(t));
                verdicts.add(stockDrawn(t));
                verdicts.add(Verdict.of("no-gun-no-seats", t.truckShots == 0 && t.truckCapacity == 0
                                && t.truckManifestMax == 0,
                        "truck fired " + t.truckShots + " shots, capacity " + t.truckCapacity + ", most aboard "
                                + t.truckManifestMax));
            }
            case DRIVING -> {
                verdicts.add(Verdict.of("nothing-while-moving", t.movingInReachTicks > 0
                                && t.transfersWhileMoving == 0,
                        t.transfersWhileMoving + " transfers on ticks the truck moved faster than "
                                + SupplyTruckSystem.PARKED_SPEED + " cells/s; it spent " + t.movingInReachTicks
                                + " ticks moving within " + ResupplyCache.SERVICE_RADIUS_CELLS
                                + " cells of a spent marine"));
                verdicts.add(Verdict.of("serves-once-halted", t.haltTick >= 0 && t.firstTransferTick >= t.haltTick
                                && t.transfers > 0,
                        "halted beside the squad at tick " + t.haltTick + ", first transfer at tick "
                                + t.firstTransferTick + ", " + t.transfers + " transfers in all"));
                verdicts.add(resupplied(t));
                verdicts.add(farUntouched(t));
                verdicts.add(stockDrawn(t));
            }
            case WRECKED -> {
                verdicts.add(Verdict.of("wrecked-after-service", t.wreckTick >= 0
                                && t.transfersBeforeWreck >= WRECK_AFTER_TRANSFERS,
                        "wrecked at tick " + t.wreckTick + " after " + t.transfersBeforeWreck + " transfers"));
                verdicts.add(Verdict.of("stock-lost", t.wreckTick >= 0 && t.stockEnd == 0 && !t.servingAtEnd
                                && t.stockAtWreck > 0,
                        t.stockAtWreck + " left aboard when it was wrecked; cache stock afterwards " + t.stockEnd
                                + ", serving " + t.servingAtEnd));
                verdicts.add(Verdict.of("no-transfer-after-wreck", t.wreckTick >= 0 && t.usesGainedAfterWreck == 0,
                        t.usesGainedAfterWreck + " grenades reached either squad after the wreck (tick "
                                + t.wreckTick + "); near squad holds " + t.nearUses + " of " + t.nearMax));
                verdicts.add(farUntouched(t));
            }
            default -> verdicts.add(Verdict.of("nothing-without-truck", t.nearUses == 0 && t.farUses == 0
                            && t.cachesSeen == 0,
                    "no truck: near squad holds " + t.nearUses + ", far squad " + t.farUses + ", caches "
                            + t.cachesSeen));
        }
        Map<String, Number> readings = new LinkedHashMap<>();
        readings.put("nearUses", t.nearUses);
        readings.put("nearMax", t.nearMax);
        readings.put("nearFullTick", t.nearFullTick);
        readings.put("farUses", t.farUses);
        readings.put("transfers", t.transfers);
        readings.put("firstTransferTick", t.firstTransferTick);
        readings.put("lastTransferTick", t.lastTransferTick);
        readings.put("stockStart", t.stockStart);
        readings.put("stockEnd", t.stockEnd);
        readings.put("haltTick", t.haltTick);
        readings.put("movingInReachTicks", t.movingInReachTicks);
        readings.put("transfersWhileMoving", t.transfersWhileMoving);
        readings.put("peakSpeed", round(t.peakSpeed));
        readings.put("wreckTick", t.wreckTick);
        readings.put("stockAtWreck", t.stockAtWreck);
        readings.put("usesGainedAfterWreck", t.usesGainedAfterWreck);
        readings.put("truckShots", t.truckShots);
        readings.put("truckFinalX", round(t.truckX));
        readings.put("truckFinalY", round(t.truckY));
        return new SceneReport(id(), loop, t.ticks, verdicts, readings);
    }

    private static Verdict resupplied(Trace t) {
        return Verdict.of("near-squad-resupplied", t.nearUses == t.nearMax && t.nearMax > 0,
                "near squad holds " + t.nearUses + " of " + t.nearMax + " grenades (started with 0), full at tick "
                        + t.nearFullTick);
    }

    private static Verdict farUntouched(Trace t) {
        return Verdict.of("far-squad-untouched", t.farUses == 0,
                "far squad, more than " + ResupplyCache.SERVICE_RADIUS_CELLS + " cells from anywhere the truck "
                        + "stood, holds " + t.farUses + " grenades");
    }

    private static Verdict stockDrawn(Trace t) {
        int drawn = t.stockStart - t.stockEnd;
        return Verdict.of("stock-drawn", t.stockStart > 0 && drawn == t.nearUses + t.farUses
                        && drawn == t.transfers,
                "truck stock " + t.stockStart + " -> " + t.stockEnd + " (" + drawn + " drawn) against "
                        + (t.nearUses + t.farUses) + " grenades the squads gained");
    }

    private static float round(float v) {
        return Math.round(v * 100f) / 100f;
    }

    private static final class Trace implements TickObserver {
        final String loop;
        final long truck;
        final long[] near;
        final long[] far;
        final int nearMax;
        int truckCapacity;
        int truckManifestMax;
        int ticks;

        int nearUses;
        int farUses;
        int nearFullTick = -1;
        int transfers;
        int firstTransferTick = -1;
        int lastTransferTick = -1;
        int stockStart = -1;
        int stockEnd = -1;
        int previousStock = -1;
        int previousUses;
        boolean servingAtEnd;
        int cachesSeen;
        int haltTick = -1;
        int movingInReachTicks;
        int transfersWhileMoving;
        float peakSpeed;
        int wreckTick = -1;
        int transfersBeforeWreck;
        int stockAtWreck = -1;
        int usesGainedAfterWreck;
        int truckShots;
        float truckX;
        float truckY;
        private String caption = "";

        Trace(String loop, SceneWorld world, long truck) {
            this.loop = loop;
            this.truck = truck;
            this.near = world.members(NEAR);
            this.far = world.members(FAR);
            int max = 0;
            for (long id : near) max += world.sim().world().specialEquipment(id).startingAmmo();
            this.nearMax = max;
            if (truck != 0L) truckCapacity = world.sim().convoy().vehicleType(truck).capacity;
        }

        String caption() {
            return caption;
        }

        private static int uses(World world, long[] ids) {
            int sum = 0;
            for (long id : ids) if (world.isAlive(id)) sum += world.secondaryAmmo(id);
            return sum;
        }

        private ResupplyCache cache(BattleSimulation sim) {
            for (ResupplyCache cache : sim.getResupplyCaches()) {
                if (cache.carried() && cache.carrierId == truck) return cache;
            }
            return null;
        }

        /** Whether a near marine who still wants a grenade stands inside the service radius of the truck. */
        private boolean spentMarineInReach(BattleSimulation sim, float x, float y) {
            World world = sim.world();
            for (long id : near) {
                if (!world.isAlive(id)
                        || world.secondaryAmmo(id) >= world.specialEquipment(id).startingAmmo()) continue;
                if (Math.hypot(world.x(id) - x, world.y(id) - y) <= ResupplyCache.SERVICE_RADIUS_CELLS) return true;
            }
            return false;
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            World world = sim.world();
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (truck != 0L && shot.shooterId == truck) truckShots++;
            }
            int nearNow = uses(world, near);
            int farNow = uses(world, far);
            int gained = nearNow + farNow - previousUses;
            previousUses = nearNow + farNow;
            nearUses = nearNow;
            farUses = farNow;
            if (nearFullTick < 0 && nearMax > 0 && nearNow == nearMax) nearFullTick = tick;
            cachesSeen = Math.max(cachesSeen, sim.getResupplyCaches().size());

            if (wreckTick >= 0 && gained > 0) usesGainedAfterWreck += gained;

            ResupplyCache cache = truck == 0L ? null : cache(sim);
            boolean vehicle = truck != 0L && sim.convoy().isVehicle(truck);
            float speed = vehicle ? Math.abs(sim.convoy().body(truck).speed) : 0f;
            VehicleMission mission = vehicle ? sim.convoy().mission(truck) : null;
            if (vehicle) {
                truckX = sim.convoy().body(truck).x;
                truckY = sim.convoy().body(truck).y;
                truckManifestMax = Math.max(truckManifestMax, sim.transport().manifest(truck).size());
                peakSpeed = Math.max(peakSpeed, speed);
            }
            boolean moving = speed > SupplyTruckSystem.PARKED_SPEED;

            if (cache != null) {
                if (stockStart < 0) {
                    stockStart = cache.maximumStock;
                    previousStock = cache.maximumStock;
                }
                int drawn = previousStock - cache.stock;
                if (drawn > 0 && wreckTick < 0) {
                    transfers += drawn;
                    if (firstTransferTick < 0) firstTransferTick = tick;
                    lastTransferTick = tick;
                    if (moving) transfersWhileMoving += drawn;
                }
                previousStock = cache.stock;
                stockEnd = cache.stock;
                servingAtEnd = cache.serving();
                if (moving && vehicle && spentMarineInReach(sim, truckX, truckY)) movingInReachTicks++;
                if (loop.equals(DRIVING) && haltTick < 0 && tick > ORDER_TICK && !moving
                        && Math.hypot(truckX - TRUCK_X, truckY - TRUCK_Y) < 3f) {
                    haltTick = tick;
                }
                if ((loop.equals(PARKED) || loop.equals(WRECKED)) && haltTick < 0 && !moving) haltTick = tick;
            }

            // The scripted parts: the driving loop's order, and the wrecked loop's wreck.
            if (loop.equals(DRIVING) && tick == ORDER_TICK) {
                // One cell past the halt: a move order is done a cell short of the cell it names.
                sim.getVehicleMoveOrderService().requestMove(truck, (int) TRUCK_X + 1, (int) TRUCK_Y);
            }
            if (loop.equals(WRECKED) && wreckTick < 0 && transfers >= WRECK_AFTER_TRANSFERS && vehicle) {
                transfersBeforeWreck = transfers;
                stockAtWreck = cache == null ? -1 : cache.stock;
                sim.applyDamage(truck, 10000f, 10000f);
                if (sim.convoy().mission(truck).state == VehicleState.WRECKED) wreckTick = tick;
            }

            String state = truck == 0L ? "no truck"
                    : !vehicle ? "truck gone"
                    : mission.state == VehicleState.WRECKED ? "truck wrecked"
                    : moving ? String.format(Locale.ROOT, "truck moving %.1f c/s", speed) : "truck halted";
            caption = String.format(Locale.ROOT, "%s, stock %s / near squad %d of %d grenades / far squad %d",
                    state, cache == null ? "-" : cache.stock + "/" + cache.maximumStock
                            + (cache.serving() ? " open" : " closed"),
                    nearNow, nearMax, farNow);
        }
    }
}
