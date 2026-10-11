package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
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
 * An Onslaught shelling a scatter of defenders, and what the ground can do back.
 *
 * <p>Two loops differ only in the ordnance sent at the hull. The rifle loop lands
 * small-arms-grade detonations (18 damage, 5 penetration) on the hull, a volley
 * every second; a rifle cannot even target a ship, so this is the most generous
 * reading of small arms, and the hull must not notice. The battery loop lands
 * salvos of anti-ship-grade detonations (600 damage, 60 penetration) every three
 * seconds from tick 150, and the hull must come apart in stages, then go up and
 * stop firing.
 *
 * <p>Every detonation is an ordinary {@link PendingDetonation} from the
 * defenders' faction landing on the hull's footprint, the same path a mortar or a
 * mech cannon's shell takes, so the evidence is of the wiring and not of a
 * shortcut to {@link Warship#takeDamage}.
 */
public final class WarshipAnswerabilityScene implements BehaviorScene {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 80;
    private static final int TICKS = 1200;
    private static final float SHIP_X = 38f;
    private static final float SHIP_Y = 40f;
    private static final float START_HEADING = -120f;
    private static final float AREA_X = 68f;
    private static final float AREA_Y = 40f;
    private static final float AREA_RADIUS = 14f;
    private static final int BATTERY_START_TICK = 60;
    private static final int BATTERY_INTERVAL_TICKS = 30;
    private static final int BATTERY_PIECES = 6;
    private static final int RIFLE_INTERVAL_TICKS = 30;
    private static final int RIFLES = 12;
    /** The window before destruction in which the ship must still have been firing. */
    private static final int LIVE_WINDOW_TICKS = 60;

    @Override public String id() { return "warship-answerability"; }

    @Override public String label() {
        return "Warship answerability: rifles glance off a capital hull; an anti-ship battery destroys it and silences its guns";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("rifle-fire", "heavy-battery")) {
            reports.add(playOne(loop, frames));
        }
        return reports;
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
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
        Warship onslaught = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE,
                SHIP_X, SHIP_Y, START_HEADING);
        onslaught.order(new WarshipOrder.FireOnArea(AREA_X, AREA_Y, AREA_RADIUS, true));
        sim.addWarship(onslaught);
        Trace trace = new Trace(onslaught, loop.equals("heavy-battery"));
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 3, tick -> loop + " / onslaught / tick " + tick)
                .run(TICKS);
        return report(loop, trace, sim);
    }

    private SceneReport report(String loop, Trace trace, BattleSimulation sim) {
        List<Verdict> verdicts = new ArrayList<>();
        Warship ship = trace.ship;
        if (loop.equals("rifle-fire")) {
            verdicts.add(Verdict.of("hull-unchanged", ship.hullFraction() >= 0.999f,
                    trace.hits + " rifle-grade detonations on the hull left it at "
                            + ship.hitPoints() + " of " + ship.maxHitPoints));
            verdicts.add(Verdict.of("kept-firing", trace.totalShots() >= 50 && !ship.destroyed(),
                    trace.totalShots() + " rounds fired, the last at tick " + trace.lastShotTick));
            verdicts.add(Verdict.of("still-on-station", sim.warships().ships().contains(ship),
                    "the ship is still over the battle"));
        } else {
            verdicts.add(Verdict.of("destroyed", ship.destroyed() && trace.destroyedTick > 0,
                    "destroyed at tick " + trace.destroyedTick + " after " + trace.hits + " battery hits"));
            verdicts.add(Verdict.of("comes-apart-in-stages",
                    trace.halfTick > 0 && trace.destroyedTick > trace.halfTick,
                    "half the hull gone at tick " + trace.halfTick + ", all of it at " + trace.destroyedTick));
            verdicts.add(Verdict.of("fired-until-the-end",
                    trace.shotsBetween(trace.destroyedTick - LIVE_WINDOW_TICKS, trace.destroyedTick) > 0,
                    trace.shotsBetween(trace.destroyedTick - LIVE_WINDOW_TICKS, trace.destroyedTick)
                            + " rounds in the " + LIVE_WINDOW_TICKS + " ticks before it died"));
            verdicts.add(Verdict.of("silent-afterwards",
                    trace.destroyedTick > 0 && trace.shotsAfter(trace.destroyedTick) == 0
                            && !sim.warships().ships().contains(ship),
                    trace.shotsAfter(trace.destroyedTick) + " rounds fired after destruction; ship removed: "
                            + !sim.warships().ships().contains(ship)));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("hullFraction", ship.hullFraction());
        metrics.put("hits", trace.hits);
        metrics.put("destroyedTick", trace.destroyedTick);
        metrics.put("shots", trace.totalShots());
        metrics.put("lastShotTick", trace.lastShotTick);
        metrics.put("liveDefenders", sim.getRoster().liveCount());
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        final Warship ship;
        private final boolean battery;
        private final int[] shotsByTick = new int[TICKS + 2];
        private int lastTick = -1;
        int lastShotTick = -1;
        int hits;
        int halfTick = -1;
        int destroyedTick = -1;

        private Trace(Warship ship, boolean battery) {
            this.ship = ship;
            this.battery = battery;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            int shots = 0;
            for (Warship.Mount m : ship.mounts()) shots += m.shotsFired;
            for (int t = Math.max(0, lastTick + 1); t <= Math.min(tick, shotsByTick.length - 1); t++) {
                shotsByTick[t] = shots;
            }
            lastTick = tick;
            if (tick > 0 && shots > shotsByTick[tick - 1]) lastShotTick = tick;
            if (ship.destroyed()) {
                if (destroyedTick < 0) destroyedTick = tick;
                return;
            }
            if (halfTick < 0 && ship.hullFraction() <= 0.5f) halfTick = tick;
            if (battery) {
                if (tick >= BATTERY_START_TICK && (tick - BATTERY_START_TICK) % BATTERY_INTERVAL_TICKS == 0) {
                    for (int i = 0; i < BATTERY_PIECES; i++) land(sim, 600f, 60f, 2f);
                }
            } else if (tick % RIFLE_INTERVAL_TICKS == 0) {
                for (int i = 0; i < RIFLES; i++) land(sim, 18f, 5f, 0.6f);
            }
        }

        /** One round of the defenders' landing on the hull's centre. */
        private void land(BattleSimulation sim, float damage, float penetration, float blast) {
            hits++;
            sim.detonateNow(new PendingDetonation(0L, ship.x, ship.y, 0f, blast, damage, penetration,
                    0, Faction.DEFENDER, false, 0f, false, false, false));
        }

        int totalShots() {
            return shotsByTick[lastObserved()];
        }

        int shotsBetween(int fromTick, int toTick) {
            int from = Math.max(0, fromTick);
            int to = Math.min(lastObserved(), toTick);
            return shotsByTick[to] - shotsByTick[from];
        }

        int shotsAfter(int tick) {
            return shotsByTick[lastObserved()] - shotsByTick[Math.min(Math.max(0, tick), lastObserved())];
        }

        private int lastObserved() {
            return Math.max(0, Math.min(lastTick, shotsByTick.length - 1));
        }
    }
}
