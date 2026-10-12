package com.dillon.starsectormarines.battle.warship;

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
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The counterplay budget of a rarely-used superweapon: it does not stay forever
 * and it does not shoot forever.
 *
 * <p>Both loops are the same Onslaught shelling the same scatter of defenders.
 * The {@code departs} loop gives it a bounded time on station; it must fire only
 * until the clock runs out, then hover off the map and be gone. The
 * {@code ordnance-budget} loop gives it a small pool of rounds; the guns must go
 * silent on exactly the last one and the ground still standing afterwards must
 * be left alone.
 */
public final class WarshipBudgetScene implements BehaviorScene {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 80;
    private static final int TICKS = 3600;
    private static final float SHIP_X = 38f;
    private static final float SHIP_Y = 40f;
    private static final float START_HEADING = -120f;
    private static final float AREA_X = 68f;
    private static final float AREA_Y = 40f;
    private static final float AREA_RADIUS = 14f;
    private static final float STAY_SECONDS = 15f;
    private static final int BUDGET_ROUNDS = 40;
    /** Ticks after the last round fired in which its shell may still land; later deaths would be a gun still firing. */
    private static final int LANDING_WINDOW_TICKS = 300;

    @Override public String id() { return "warship-budget"; }

    @Override public String label() {
        return "Warship budget: a capital leaves when its station time is spent and falls silent when its rounds are";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("departs", "ordnance-budget")) {
            reports.add(playOne(loop, frames));
        }
        return reports;
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        boolean departs = loop.equals("departs");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        for (int row = 0; row < 12; row++) {
            for (int col = 0; col < 12; col++) {
                builder.unit("d" + row + "-" + col, Faction.DEFENDER, UnitType.MARINE, 56 + col * 2, 29 + row * 2);
            }
        }
        builder.unit("far-enemy", Faction.DEFENDER, UnitType.MARINE, 150, 40);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261011L);
        Warship ship = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE,
                SHIP_X, SHIP_Y, START_HEADING);
        ship.stationTo(SHIP_X, SHIP_Y);
        ship.order(new WarshipOrder.FireOnArea(AREA_X, AREA_Y, AREA_RADIUS, true));
        if (departs) ship.stayFor(STAY_SECONDS); else ship.limitOrdnance(BUDGET_ROUNDS);
        sim.addWarship(ship);
        Trace trace = new Trace(world, ship);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 6, tick -> loop + " / onslaught / tick " + tick)
                .run(TICKS);
        return departs ? reportDeparts(trace, sim) : reportBudget(trace, sim);
    }

    private SceneReport reportDeparts(Trace t, BattleSimulation sim) {
        List<Verdict> verdicts = new ArrayList<>();
        int due = Math.round(STAY_SECONDS * 30f);
        verdicts.add(Verdict.of("fired-while-on-station", t.shotsAtDeparture > 0,
                t.shotsAtDeparture + " rounds fired before it began to leave"));
        verdicts.add(Verdict.of("departs-when-the-clock-runs-out",
                t.departingTick > 0 && Math.abs(t.departingTick - due) <= 2,
                "began to leave at tick " + t.departingTick + ", station time spent at tick " + due));
        verdicts.add(Verdict.of("fires-only-before-departing",
                t.departingTick > 0 && t.shotsEnd == t.shotsAtDeparture,
                (t.shotsEnd - t.shotsAtDeparture) + " rounds fired after it began to leave"));
        verdicts.add(Verdict.of("leaves-the-map-and-is-removed",
                t.removedTick > t.departingTick && !sim.warships().ships().contains(t.ship)
                        && t.ship.departed(),
                "removed at tick " + t.removedTick + " from (" + t.ship.x + ", " + t.ship.y + ")"));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("departingTick", t.departingTick);
        metrics.put("removedTick", t.removedTick);
        metrics.put("shots", t.shotsEnd);
        metrics.put("liveDefenders", sim.getRoster().liveCount());
        return new SceneReport(id(), "departs", TICKS, verdicts, metrics);
    }

    private SceneReport reportBudget(Trace t, BattleSimulation sim) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("spends-the-whole-budget",
                t.shotsEnd == BUDGET_ROUNDS && t.ship.ordnanceRemaining() == 0,
                t.shotsEnd + " rounds fired of " + BUDGET_ROUNDS + ", " + t.ship.ordnanceRemaining() + " left"));
        verdicts.add(Verdict.of("silent-after-the-last-round",
                t.lastShotTick > 0 && t.shotsEnd == t.shotsAtLastShot,
                "last round at tick " + t.lastShotTick + ", ship still on station: "
                        + sim.warships().ships().contains(t.ship)));
        int killedLate = t.deathsAfter(t.lastShotTick + LANDING_WINDOW_TICKS);
        long inArea = t.liveInArea(sim);
        verdicts.add(Verdict.of("ground-left-unshot",
                killedLate == 0 && inArea > 0 && sim.warships().ships().contains(t.ship),
                inArea + " defenders still standing in the ordered area, " + killedLate
                        + " deaths after the last shell could have landed"));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("shots", t.shotsEnd);
        metrics.put("lastShotTick", t.lastShotTick);
        metrics.put("liveDefenders", sim.getRoster().liveCount());
        return new SceneReport(id(), "ordnance-budget", TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        final Warship ship;
        private final SceneWorld world;
        private final Map<String, Integer> deaths = new LinkedHashMap<>();
        int departingTick = -1;
        int removedTick = -1;
        int shotsAtDeparture;
        int shotsEnd;
        int lastShotTick = -1;
        int shotsAtLastShot;

        private Trace(SceneWorld world, Warship ship) {
            this.world = world;
            this.ship = ship;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            int shots = 0;
            for (Warship.Mount m : ship.mounts()) {
                if (!m.pointDefence()) shots += m.shotsFired;
            }
            if (shots > shotsEnd) {
                lastShotTick = tick;
                shotsAtLastShot = shots;
            }
            shotsEnd = shots;
            if (departingTick < 0 && ship.departing()) {
                departingTick = tick;
                shotsAtDeparture = shots;
            }
            if (removedTick < 0 && !sim.warships().ships().contains(ship)) removedTick = tick;
            for (int row = 0; row < 12; row++) {
                for (int col = 0; col < 12; col++) {
                    String key = "d" + row + "-" + col;
                    if (!deaths.containsKey(key) && !sim.getRoster().isAliveById(world.unit(key))) {
                        deaths.put(key, tick);
                    }
                }
            }
        }

        int deathsAfter(int tick) {
            return (int) deaths.values().stream().filter(d -> d > tick).count();
        }

        long liveInArea(BattleSimulation sim) {
            return 144 - deaths.size();
        }
    }
}
