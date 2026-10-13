package com.dillon.starsectormarines.battle.battery;

import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.AirFireOrder;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A fortress battery against a warship, in five loops.
 *
 * <ul>
 *   <li>{@code control-no-battery}: the same capital under its doctrine with its station where the other loops put
 *       theirs and no battery on the map. Nothing hurts it and its station is where it was told.</li>
 *   <li>{@code standoff}: the battery stands, and the capital's doctrine is told to hold a station inside its
 *       envelope. The envelope is a live hazard, the station is pushed out beyond it, the ship comes no nearer than
 *       that and the battery fires nothing.</li>
 *   <li>{@code gamble}: the same capital with no doctrine (held where it was put, as a pilot's low pass would be)
 *       inside the envelope. The guns settle for the lock time and then fire; the hull pays.</li>
 *   <li>{@code fire-control-capture}: as the gamble, until a few marines walk up to the fire-control point. The battery
 *       falls silent while it is contested, changes hands when they have held it long enough, and fires on nothing
 *       of the marines' once it is theirs; the Orbital Barrage lock lifts with it.</li>
 *   <li>{@code marine-battery}: the sides reversed. A battery the company holds fires on a defender's ship: nothing
 *       in the rules is written for attackers or defenders.</li>
 * </ul>
 *
 * Every loop keeps a distant marine and defender so the clock keeps running, and plays with the mission-complete
 * check off.
 */
public final class FortressBatteryScene implements BehaviorScene {

    private static final int WIDTH = 260;
    private static final int HEIGHT = 90;
    private static final int TICKS = 3600;
    private static final int BATTERY_X = 60;
    private static final int BATTERY_Y = 45;
    /** Where the capital starts when it has to arrive from outside the envelope. */
    private static final float SHIP_START_X = 145f;
    /** Where the mission asks it to stand: well inside the envelope. */
    private static final float STATION_X = 100f;
    private static final int MARINES_ARRIVE_TICK = 300;

    @Override public String id() { return "fortress-battery"; }

    @Override public String label() {
        return "Fortress battery: a live envelope keeps a capital's station out, its ship-grade guns punish one that stays, "
                + "and taking its fire control silences it and hands it over";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        Run control = playOne("control-no-battery", frames);
        reports.add(report("control-no-battery", control));
        Run standoff = playOne("standoff", frames);
        reports.add(report("standoff", standoff));
        Run gamble = playOne("gamble", frames);
        reports.add(report("gamble", gamble));
        Run capture = playOne("fire-control-capture", frames);
        reports.add(report("fire-control-capture", capture));
        Run reversed = playOne("marine-battery", frames);
        reports.add(report("marine-battery", reversed));
        return reports;
    }

    private record Run(Trace trace, Warship ship, BattleSimulation sim, String barrageLockAtStart) {}

    private Run playOne(String loop, FrameSink frames) throws Exception {
        boolean reversed = loop.equals("marine-battery");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 5, 80);
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, 255, 5);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261014L);
        Faction batterySide = reversed ? Faction.MARINE : Faction.DEFENDER;
        if (!loop.equals("control-no-battery")) {
            sim.spawn(MapTurret.create("battery", batterySide, TurretCatalogRegistry.FORTRESS_BATTERY_STRUCTURE_ID,
                    BATTERY_X, BATTERY_Y));
        }
        Faction shipSide = reversed ? Faction.DEFENDER : Faction.MARINE;
        boolean arrives = loop.equals("standoff") || loop.equals("control-no-battery");
        Warship ship = Warship.fit("onslaught", "onslaught_Standard", shipSide,
                arrives ? SHIP_START_X : STATION_X, BATTERY_Y, 90f);
        ship.stationTo(STATION_X, BATTERY_Y);
        ship.limitFlux();
        ship.airFire(AirFireOrder.OFF);
        ship.shieldAuto(false);
        ship.raiseShield(false);
        if (arrives) ship.useDoctrine();
        sim.addWarship(ship);
        Trace trace = new Trace(ship, loop.equals("fire-control-capture"));
        String lock = sim.orbitalBarrageLock();
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 12, tick -> String.format(Locale.ROOT,
                        "%s / tick %d / hull %.0f%% / shots %d / %s", loop, tick, 100f * ship.hullFraction(),
                        trace.shots(sim), trace.state(sim)))
                .run(TICKS);
        return new Run(trace, ship, sim, lock);
    }

    private SceneReport report(String loop, Run run) {
        Trace t = run.trace();
        Warship ship = run.ship();
        BattleSimulation sim = run.sim();
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case "control-no-battery" -> {
                verdicts.add(Verdict.of("hull-untouched", ship.hullFraction() == 1f,
                        String.format(Locale.ROOT, "hull %.3f", ship.hullFraction())));
                verdicts.add(Verdict.of("station-kept", Math.abs(t.minX - STATION_X) < 3f,
                        String.format(Locale.ROOT, "nearest approach x %.1f against a station at %.1f", t.minX, STATION_X)));
            }
            case "standoff" -> {
                float envelope = t.envelope;
                verdicts.add(Verdict.of("envelope-is-a-hazard", t.hazardTicks > 0 && envelope > 30f,
                        String.format(Locale.ROOT, "hazard registered for %d ticks, radius %.1f cells", t.hazardTicks, envelope)));
                verdicts.add(Verdict.of("never-enters-the-envelope", t.minDistance >= envelope,
                        String.format(Locale.ROOT, "nearest approach %.1f cells against an envelope of %.1f", t.minDistance, envelope)));
                verdicts.add(Verdict.of("station-pushed-out", ship.stationX() - BATTERY_X >= envelope,
                        String.format(Locale.ROOT, "station now %.1f cells from the battery, mission asked for %.1f",
                                ship.stationX() - BATTERY_X, STATION_X - BATTERY_X)));
                verdicts.add(Verdict.of("battery-never-fires", t.shots(sim) == 0 && ship.hullFraction() == 1f,
                        t.shots(sim) + " rounds fired, hull " + ship.hullFraction()));
            }
            case "gamble" -> {
                verdicts.add(Verdict.of("guns-take-time-to-settle", t.firstShotTick >= (int) (FortressBatterySystem.LOCK_SECONDS / BattleSimulation.TICK_DT),
                        "first round at tick " + t.firstShotTick + " after the hull came into reach at tick " + t.firstInReachTick));
                verdicts.add(Verdict.of("every-gun-fires", t.gunsThatFired(sim) == run.sim().fortressBatteries().batteries().get(0).guns().size(),
                        t.gunsThatFired(sim) + " of " + sim.fortressBatteries().batteries().get(0).guns().size() + " guns fired"));
                verdicts.add(Verdict.of("the-hull-pays", ship.hullFraction() < 0.9f,
                        String.format(Locale.ROOT, "hull %.3f after %d rounds", ship.hullFraction(), t.shots(sim))));
                verdicts.add(Verdict.of("barrage-withheld-throughout", run.barrageLockAtStart != null && t.lockedEveryTick,
                        "lock at start: " + run.barrageLockAtStart));
            }
            case "fire-control-capture" -> {
                verdicts.add(Verdict.of("fires-before-the-marines-come", t.shotsBeforeArrival > 0,
                        t.shotsBeforeArrival + " rounds before tick " + MARINES_ARRIVE_TICK));
                verdicts.add(Verdict.of("contest-silences-it", t.contestedTicks > 0 && t.shotsWhileContested == 0,
                        t.contestedTicks + " contested ticks, " + t.shotsWhileContested + " rounds fired in them"));
                verdicts.add(Verdict.of("changes-hands", t.flipTick > MARINES_ARRIVE_TICK && t.ownerAtEnd == Faction.MARINE,
                        "holder at the end " + t.ownerAtEnd + ", flipped at tick " + t.flipTick));
                verdicts.add(Verdict.of("takes-as-long-as-the-rules-say",
                        t.flipTick - t.firstContestTick >= (int) (FireControl.CAPTURE_SECONDS / BattleSimulation.TICK_DT) - 40,
                        "contest began tick " + t.firstContestTick + ", flipped tick " + t.flipTick));
                verdicts.add(Verdict.of("silent-towards-its-new-friends", t.shots(sim) == t.shotsAtFlip && t.hullAtFlip == ship.hullFraction(),
                        String.format(Locale.ROOT, "rounds %d at the flip and %d at the end, hull %.4f then %.4f",
                                t.shotsAtFlip, t.shots(sim), t.hullAtFlip, ship.hullFraction())));
                verdicts.add(Verdict.of("barrage-lock-lifts-with-it", sim.orbitalBarrageLock() == null && t.lockedBeforeFlip,
                        "lock at the end: " + sim.orbitalBarrageLock()));
            }
            default -> {
                verdicts.add(Verdict.of("company-battery-fires-on-their-ship", t.shots(sim) > 0 && ship.hullFraction() < 0.9f,
                        String.format(Locale.ROOT, "%d rounds, hull %.3f", t.shots(sim), ship.hullFraction())));
                verdicts.add(Verdict.of("the-company-is-not-held-off", sim.orbitalBarrageLock() == null,
                        "lock: " + sim.orbitalBarrageLock()));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("hullFraction", Math.round(ship.hullFraction() * 10000f) / 10000f);
        metrics.put("rounds", t.shots(sim));
        metrics.put("firstShotTick", t.firstShotTick);
        metrics.put("hazardTicks", t.hazardTicks);
        metrics.put("minDistance", Math.round(t.minDistance * 10f) / 10f);
        metrics.put("contestedTicks", t.contestedTicks);
        metrics.put("flipTick", t.flipTick);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final Warship ship;
        private final boolean marchesOn;
        float envelope;
        float minDistance = Float.MAX_VALUE;
        float minX = Float.MAX_VALUE;
        int hazardTicks;
        int firstShotTick = -1;
        int firstInReachTick = -1;
        int contestedTicks;
        int firstContestTick = -1;
        int flipTick = -1;
        int shotsBeforeArrival;
        int shotsWhileContested;
        int shotsAtFlip;
        float hullAtFlip = 1f;
        boolean lockedEveryTick = true;
        boolean lockedBeforeFlip;
        Faction ownerAtEnd;
        private int lastShots;

        private Trace(Warship ship, boolean marchesOn) {
            this.ship = ship;
            this.marchesOn = marchesOn;
        }

        int shots(BattleSimulation sim) {
            int n = 0;
            for (FortressBattery b : sim.fortressBatteries().batteries()) {
                for (FortressBattery.Gun g : b.guns()) n += g.shotsFired;
            }
            return n;
        }

        int gunsThatFired(BattleSimulation sim) {
            int n = 0;
            for (FortressBattery b : sim.fortressBatteries().batteries()) {
                for (FortressBattery.Gun g : b.guns()) if (g.shotsFired > 0) n++;
            }
            return n;
        }

        String state(BattleSimulation sim) {
            if (sim.fortressBatteries().isEmpty()) return "no battery";
            FortressBattery b = sim.fortressBatteries().batteries().get(0);
            return b.owner() + (b.fireControl().contested() ? " contested" : b.operational() ? " live" : " silent");
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            if (marchesOn && tick == MARINES_ARRIVE_TICK) {
                // The structure keeps its own mortar, which would clear three riflemen off its doorstep in a few
                // seconds; the scene is about the fire-control rules, so the holders are made to outlast it.
                for (int i = 0; i < 3; i++) {
                    sim.spawn(new EntitySpec("holder-" + i, Faction.MARINE, UnitType.MARINE, BATTERY_X + 3, BATTERY_Y - 1 + i)
                            .health(1_000_000f));
                }
            }
            float dist = (float) Math.hypot(ship.centerX() - BATTERY_X, ship.centerY() - BATTERY_Y);
            minDistance = Math.min(minDistance, dist);
            minX = Math.min(minX, ship.x);
            if (sim.fortressBatteries().isEmpty()) return;
            FortressBattery b = sim.fortressBatteries().batteries().get(0);
            envelope = b.envelopeCells();
            if (!sim.warships().batteryHazards().isEmpty()) hazardTicks++;
            int shots = shots(sim);
            if (firstInReachTick < 0 && ship.distanceTo(b.x(), b.y()) <= b.envelopeCells()) firstInReachTick = tick;
            if (shots > 0 && firstShotTick < 0) firstShotTick = tick;
            boolean fired = shots > lastShots;
            lastShots = shots;
            if (tick < MARINES_ARRIVE_TICK) shotsBeforeArrival = shots;
            boolean contested = b.fireControl().contested();
            if (contested) {
                contestedTicks++;
                if (firstContestTick < 0) firstContestTick = tick;
                if (fired) shotsWhileContested++;
            }
            if (flipTick < 0 && b.owner() == Faction.MARINE && b.fireControl().holder() != Faction.DEFENDER && marchesOn) {
                flipTick = tick;
                shotsAtFlip = shots;
                hullAtFlip = ship.hullFraction();
            }
            if (sim.orbitalBarrageLock() == null && flipTick < 0 && !marchesOn) lockedEveryTick = false;
            if (flipTick < 0 && sim.orbitalBarrageLock() != null) lockedBeforeFlip = true;
            ownerAtEnd = b.owner();
        }
    }
}
