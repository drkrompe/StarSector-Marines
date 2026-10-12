package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.control.ManualIntent;
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
 * An Onslaught flown by the player through the one direct-control session: it flies north under
 * the keys, lays its guns on one cluster with the left button and its missiles on another with
 * the right, holds fire, and is handed back to its director and its station.
 *
 * <p>It asks what a controlled ship must keep true. The hull moves at its own table speed and
 * points where it travels. It fires on nothing the pilot has not laid, and the ground it lays
 * stays laid when the button is let go. Each button reaches only its own battery. And at handback
 * the ship is the ship it was: its director back, no pilot order standing, and its station
 * flown back to.
 */
public final class WarshipPilotScene implements BehaviorScene {

    private static final int WIDTH = 180;
    private static final int HEIGHT = 110;
    private static final int TICKS = 4800;
    private static final float START_X = 22f;
    private static final float START_Y = 40f;
    private static final float CLUSTER_Y = 80f;
    private static final String[] A = {"a1", "a2", "a3", "a4", "a5"};
    private static final String[] B = {"b1", "b2", "b3", "b4", "b5"};
    private static final String[] C = {"c1", "c2", "c3"};

    // The pilot's script, in ticks.
    private static final int FLY_UNTIL = 1000;
    private static final int GUNS_FROM = 1100;
    private static final int GUNS_UNTIL = 1160;
    private static final int GUNS_CHECK = 1300;
    private static final int MISSILES_FROM = 2900;
    private static final int MISSILES_UNTIL = 2960;
    private static final int HOLD_FIRE_AT = 3700;
    private static final int RELEASE_AT = 3760;

    @Override public String id() { return "warship-pilot"; }

    @Override public String label() {
        return "Warship pilot: flown with the keys, ground laid with each button, handed back to its director";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("control", "piloted")) {
            reports.add(playOne(loop, frames));
        }
        return reports;
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        boolean piloted = loop.equals("piloted");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        int[][] a = {{52, 80}, {53, 82}, {51, 78}, {55, 80}, {54, 83}};
        int[][] b = {{76, 80}, {77, 82}, {75, 78}, {79, 80}, {78, 83}};
        int[][] c = {{165, 20}, {166, 22}, {164, 21}};
        for (int i = 0; i < A.length; i++) builder.unit(A[i], Faction.DEFENDER, UnitType.MARINE, a[i][0], a[i][1]);
        for (int i = 0; i < B.length; i++) builder.unit(B[i], Faction.DEFENDER, UnitType.MARINE, b[i][0], b[i][1]);
        for (int i = 0; i < C.length; i++) builder.unit(C[i], Faction.DEFENDER, UnitType.MARINE, c[i][0], c[i][1]);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261012L);
        sim.directControl().layableGround((x, y) -> true);
        Warship ship = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, START_X, START_Y, -90f);
        ship.stationTo(START_X, START_Y);
        if (piloted) ship.direct(10f, true);
        sim.addWarship(ship);
        Trace trace = new Trace(world, ship, piloted);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 20, tick -> loop + " / pilot / tick " + tick)
                .run(TICKS);
        return report(loop, ship, trace);
    }

    private SceneReport report(String loop, Warship ship, Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        if (loop.equals("control")) {
            verdicts.add(Verdict.of("nobody-dies", t.deaths.isEmpty(), "deaths with nobody at the helm: " + t.deaths));
        } else {
            verdicts.add(Verdict.of("enters", t.entered, "the session took the helm at tick 0"));
            verdicts.add(Verdict.of("flies-at-the-hulls-speed",
                    t.peakSpeed <= ship.maxSpeed * 1.02f && t.peakSpeed > ship.maxSpeed * 0.5f,
                    "peak " + t.peakSpeed + " cells/s against the hull's " + ship.maxSpeed));
            verdicts.add(Verdict.of("goes-where-it-is-flown", t.yAtFlyEnd > START_Y + 15f,
                    "north from y=" + START_Y + " to y=" + t.yAtFlyEnd));
            verdicts.add(Verdict.of("points-along-its-travel", Math.abs(WarshipFacing.wrap(t.facingAtFlyEnd)) < 25f,
                    "heading " + t.facingAtFlyEnd + " after flying north (zero is north)"));
            verdicts.add(Verdict.of("silent-until-laid", t.deathsAtFirstLay == 0,
                    t.deathsAtFirstLay + " dead before any ground was laid"));
            verdicts.add(Verdict.of("ground-stays-laid-when-the-button-is-let-go", t.gunsStillLaid,
                    "guns area still held at tick " + GUNS_CHECK + ", after the left button was released at " + GUNS_UNTIL));
            verdicts.add(Verdict.of("guns-clear-their-ground", t.allDeadBy(A, MISSILES_FROM),
                    "cluster A dead by tick " + MISSILES_FROM + ": " + t.deathsOf(A)));
            verdicts.add(Verdict.of("the-other-battery-spares-the-rest", t.noneDeadBy(B, MISSILES_FROM),
                    "cluster B untouched before the right button: " + t.deathsOf(B)));
            verdicts.add(Verdict.of("missiles-answer-the-right-button", t.anyDeadBy(B, HOLD_FIRE_AT),
                    "cluster B hit by tick " + HOLD_FIRE_AT + ": " + t.deathsOf(B)));
            verdicts.add(Verdict.of("never-fires-on-what-was-not-laid", t.deathsOf(C).isEmpty(),
                    "the far cluster lives: " + t.deathsOf(C)));
            verdicts.add(Verdict.of("fires-on-the-ships-flux", t.peakFlux > 0f && t.peakFlux <= ship.fluxCapacity() * 1.001f,
                    "peak flux " + t.peakFlux + " of " + ship.fluxCapacity()));
            verdicts.add(Verdict.of("hold-fire-clears-the-ground", t.areasAfterHoldFire == 0,
                    t.areasAfterHoldFire + " areas held after the hold-fire key"));
            verdicts.add(Verdict.of("handback-leaves-no-pilot-order",
                    t.exited && !ship.piloted() && ship.laidArea(Warship.FireGroup.GUNS) == null
                            && ship.laidArea(Warship.FireGroup.MISSILES) == null,
                    "released at tick " + RELEASE_AT));
            verdicts.add(Verdict.of("handback-restores-the-director", ship.isDirected(),
                    "the ship's own director is on again"));
            verdicts.add(Verdict.of("handback-returns-to-station",
                    Math.hypot(ship.x - START_X, ship.y - START_Y) < 0.5, "ended at (" + ship.x + ", " + ship.y
                            + ") of station (" + START_X + ", " + START_Y + ")"));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("deaths", t.deaths.size());
        metrics.put("peakSpeed", t.peakSpeed);
        metrics.put("peakFlux", t.peakFlux);
        int gunRounds = 0;
        int missileRounds = 0;
        for (Warship.Mount m : ship.mounts()) {
            if (m.pointDefence()) continue;
            if (m.group() == Warship.FireGroup.MISSILES) missileRounds += m.shotsFired;
            else gunRounds += m.shotsFired;
        }
        metrics.put("gunRounds", gunRounds);
        metrics.put("missileRounds", missileRounds);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    /** The pilot's hands and the instruments, in one observer: it acts, then looks, before each tick. */
    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        private final Warship ship;
        private final boolean piloted;
        private final Map<String, Integer> deaths = new LinkedHashMap<>();
        boolean entered;
        boolean exited;
        boolean gunsStillLaid;
        float peakSpeed;
        float peakFlux;
        float yAtFlyEnd;
        float facingAtFlyEnd;
        int deathsAtFirstLay = -1;
        int areasAfterHoldFire = -1;

        Trace(SceneWorld world, Warship ship, boolean piloted) {
            this.world = world;
            this.ship = ship;
            this.piloted = piloted;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            for (String key : keys()) {
                if (!deaths.containsKey(key) && !sim.getRoster().isAliveById(world.unit(key))) deaths.put(key, tick);
            }
            peakSpeed = Math.max(peakSpeed, ship.speed());
            peakFlux = Math.max(peakFlux, ship.flux());
            if (!piloted) return;
            var control = sim.directControl();
            if (tick == 0) entered = control.enterWarship(ship);
            if (tick == FLY_UNTIL) {
                yAtFlyEnd = ship.y;
                facingAtFlyEnd = ship.facingDegrees;
            }
            if (tick == GUNS_FROM) deathsAtFirstLay = deaths.size();
            if (tick == GUNS_CHECK) gunsStillLaid = ship.laidArea(Warship.FireGroup.GUNS) != null;
            if (tick == HOLD_FIRE_AT) {
                control.selectWeapon(0);
                areasAfterHoldFire = ship.areas().size();
            }
            if (tick == RELEASE_AT) {
                control.exit();
                exited = !control.active();
            }
            if (!control.active()) return;
            float moveY = tick < FLY_UNTIL ? 1f : 0f;
            if (tick >= GUNS_FROM && tick < GUNS_UNTIL) {
                control.submit(new ManualIntent(0f, 0f, 53f, 80f, true, false, false));
            } else if (tick >= MISSILES_FROM && tick < MISSILES_UNTIL) {
                control.submit(new ManualIntent(0f, 0f, 77f, 80f, false, false, true));
            } else {
                control.submit(new ManualIntent(0f, moveY, 150f, 60f, false, false, false));
            }
        }

        private List<String> keys() {
            List<String> all = new ArrayList<>(List.of(A));
            all.addAll(List.of(B));
            all.addAll(List.of(C));
            return all;
        }

        List<String> deathsOf(String[] group) {
            List<String> out = new ArrayList<>();
            for (String k : group) if (deaths.containsKey(k)) out.add(k + "@" + deaths.get(k));
            return out;
        }

        boolean allDeadBy(String[] group, int tick) {
            for (String k : group) if (deaths.getOrDefault(k, Integer.MAX_VALUE) > tick) return false;
            return true;
        }

        boolean noneDeadBy(String[] group, int tick) {
            for (String k : group) if (deaths.getOrDefault(k, Integer.MAX_VALUE) <= tick) return false;
            return true;
        }

        boolean anyDeadBy(String[] group, int tick) {
            for (String k : group) if (deaths.getOrDefault(k, Integer.MAX_VALUE) <= tick) return true;
            return false;
        }
    }
}
