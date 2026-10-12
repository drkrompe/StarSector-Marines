package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
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
import java.util.Locale;
import java.util.Map;

/**
 * An Onslaught's shield against a battery of anti-ship rounds, and what it does to
 * the ship's guns: the shield-versus-guns trade on one flux pool.
 *
 * <p>The ship is a FRONT-shield hull pointing north. Every battery loop drops the
 * same rounds, 300-damage, 60-penetration shells that each take 273 hull points
 * from bare armour, onto the hull from a stated side.
 * <ul>
 *   <li>{@code front-arc-held}: the shield is held up and the battery lands in the
 *       arc. The hull must be untouched while flux climbs (nothing vents while the
 *       shield is up) until the pool fills, the shield drops and the ship overloads;
 *       only then does the hull take damage.</li>
 *   <li>{@code rear-battery}: the same shield held up and the same battery from
 *       astern. A front shield does not cover it, so the hull is hit at once and
 *       the shield takes on nothing but its upkeep.</li>
 *   <li>{@code shield-down}: the control. No shield; the hull takes the same
 *       damage the rear loop did.</li>
 *   <li>{@code auto-threat}: an automatic shield. It is down until rounds are in
 *       the air toward the hull, absorbs the whole volley, then drops once the
 *       threat has been quiet for its hold and vents the flux it took.</li>
 *   <li>{@code guns-shield-up} and {@code guns-shield-down}: a directed ship
 *       shelling a block of defenders under a thin trickle of hostile fire from the
 *       front, shield held and shield not. The raised shield stops the pool
 *       venting, so the guns see a fuller pool and fire fewer rounds: the
 *       trade between protection and guns.</li>
 *   <li>{@code guns-full-pool}: the same ground fight with no shield on the hull
 *       table's own, unhalved pool, which is why a directed ship runs at
 *       {@link Warship#BRAWLER_FLUX_SCALE}: at the full pool its guns never come
 *       near the soft limit and never rest.</li>
 * </ul>
 */
public final class WarshipShieldScene implements BehaviorScene {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 80;
    private static final float SHIP_X = 60f;
    private static final float SHIP_Y = 40f;
    /** Pointing along +y, which is north on the review frame. */
    private static final float HEADING = 0f;
    private static final float FLIGHT_SECONDS = 1.2f;
    private static final float DAMAGE = 300f;
    private static final float PENETRATION = 60f;
    private static final float BLAST = 3f;
    private static final int BATTERY_START = 60;
    private static final int BATTERY_INTERVAL = 30;
    private static final int BATTERY_PIECES = 4;
    private static final int TICKS = 900;
    private static final int AUTO_TICKS = 600;
    /** The exposed hulls are shelled for fifteen seconds, short of destroying them. */
    private static final int EXPOSED_TICKS = 450;
    private static final int GUNS_TICKS = 1500;
    private static final int GUNS_INTERVAL = 45;
    private static final float GUNS_AREA_X = 60f;
    private static final float GUNS_AREA_Y = 66f;
    private static final float GUNS_AREA_RADIUS = 14f;

    private enum Side { FRONT, REAR }

    @Override public String id() { return "warship-shields"; }

    @Override public String label() {
        return "Warship shields: a raised shield absorbs the arc as flux until it overloads, covers nothing astern, and costs the guns their venting";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        Map<String, Trace> runs = new LinkedHashMap<>();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("front-arc-held", "rear-battery", "shield-down", "auto-threat",
                "guns-shield-down", "guns-shield-up", "guns-full-pool")) {
            Trace trace = playOne(loop, frames);
            runs.put(loop, trace);
            reports.add(report(loop, trace, runs));
        }
        return reports;
    }

    private Trace playOne(String loop, FrameSink frames) throws Exception {
        boolean guns = loop.startsWith("guns-");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        if (guns) {
            // Thick enough that the guns have something to shoot for the whole run.
            for (int row = 0; row < 20; row++) {
                for (int col = 0; col < 20; col++) {
                    builder.unit("d" + row + "-" + col, Faction.DEFENDER, UnitType.MARINE,
                            (int) GUNS_AREA_X - 10 + col, (int) GUNS_AREA_Y - 10 + row);
                }
            }
        }
        builder.unit("far-enemy", Faction.DEFENDER, UnitType.MARINE, 150, 40);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261011L);
        Warship ship = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, SHIP_X, SHIP_Y, HEADING);
        ship.stationTo(SHIP_X, SHIP_Y);
        ship.limitFlux();
        ship.fluxScale(loop.equals("guns-full-pool") ? 1f : Warship.BRAWLER_FLUX_SCALE);
        if (guns) {
            ship.order(new WarshipOrder.FireOnArea(GUNS_AREA_X, GUNS_AREA_Y, GUNS_AREA_RADIUS, false));
        }
        switch (loop) {
            case "front-arc-held", "rear-battery", "guns-shield-up" -> ship.raiseShield(true);
            case "shield-down", "guns-shield-down", "guns-full-pool" -> ship.raiseShield(false);
            default -> ship.shieldAuto(true);
        }
        sim.addWarship(ship);
        Side side = loop.equals("rear-battery") ? Side.REAR : Side.FRONT;
        int ticks = guns ? GUNS_TICKS : loop.equals("auto-threat") ? AUTO_TICKS
                : loop.equals("front-arc-held") ? TICKS : EXPOSED_TICKS;
        Trace trace = new Trace(ship, loop, side, ticks);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, guns ? 15 : 6, tick -> String.format(Locale.ROOT,
                        "%s / shield %s / flux %d%% / hull %d%% / tick %d", loop, ship.shieldState(),
                        Math.round(ship.fluxFraction() * 100f), Math.round(ship.hullFraction() * 100f), tick))
                .run(ticks);
        return trace;
    }

    private SceneReport report(String loop, Trace t, Map<String, Trace> runs) {
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case "front-arc-held" -> {
                verdicts.add(Verdict.of("overloads-under-sustained-fire",
                        t.overloadStart > 0 && t.ship.overloadCount() >= 1 && t.ship.shieldCollapses() >= 1,
                        t.ship.overloadCount() + " overloads, the first at tick " + t.overloadStart
                                + "; the shield collapsed " + t.ship.shieldCollapses() + " times"));
                verdicts.add(Verdict.of("hull-untouched-while-the-shield-holds",
                        t.overloadStart > 0 && t.hullBeforeOverload >= 0.999f && t.shieldHitsAtOverload > 0,
                        "hull " + pct(t.hullBeforeOverload) + " the tick before the overload, after "
                                + t.shieldHitsAtOverload + " absorbed hits"));
                verdicts.add(Verdict.of("flux-climbs-without-venting",
                        t.overloadStart > 0 && t.monotoneBeforeOverload && t.fluxAt(BATTERY_START) > 0f,
                        "flux never fell before the overload; " + Math.round(t.fluxAt(BATTERY_START))
                                + " at the first volley from upkeep alone"));
                verdicts.add(Verdict.of("hull-hit-only-once-the-shield-is-down",
                        t.firstHullDamage >= t.overloadStart && t.overloadStart > 0 && t.ship.hullFraction() < 0.999f,
                        "first hull damage at tick " + t.firstHullDamage + ", overload at " + t.overloadStart
                                + ", hull " + pct(t.ship.hullFraction()) + " at the end"));
            }
            case "rear-battery" -> {
                int landing = BATTERY_START + Math.round(FLIGHT_SECONDS * 30f) + 3;
                verdicts.add(Verdict.of("rear-hits-reach-the-hull",
                        t.firstHullDamage > 0 && t.firstHullDamage <= landing + 2,
                        "first hull damage at tick " + t.firstHullDamage + ", first round lands about " + landing));
                verdicts.add(Verdict.of("shield-absorbs-nothing-from-astern",
                        t.ship.shieldHits() == 0 && t.ship.shieldAbsorbedFlux() == 0f,
                        t.ship.shieldHits() + " hits absorbed"));
                float upkeep = t.ship.shieldModel().upkeep() * t.ship.fluxPool().dissipation() * t.ticks / 30f;
                verdicts.add(Verdict.of("flux-is-upkeep-alone",
                        t.ship.overloadCount() == 0 && t.ship.flux() <= upkeep * 1.02f && t.ship.flux() >= upkeep * 0.9f,
                        Math.round(t.ship.flux()) + " flux against " + Math.round(upkeep) + " of upkeep"));
            }
            case "shield-down" -> {
                Trace rear = runs.get("rear-battery");
                verdicts.add(Verdict.of("hull-hit-at-once",
                        t.firstHullDamage > 0 && t.firstHullDamage <= BATTERY_START + 40,
                        "first hull damage at tick " + t.firstHullDamage));
                verdicts.add(Verdict.of("no-flux-no-absorption",
                        t.maxFlux == 0f && t.ship.shieldHits() == 0 && !t.everUp,
                        "peak flux " + t.maxFlux + ", " + t.ship.shieldHits() + " hits absorbed"));
                verdicts.add(Verdict.of("as-exposed-as-astern",
                        Math.abs(t.ship.hullFraction() - rear.ship.hullFraction()) <= 0.01f,
                        "hull " + pct(t.ship.hullFraction()) + " against " + pct(rear.ship.hullFraction())
                                + " with the shield held up astern"));
            }
            case "auto-threat" -> {
                int firstLanding = BATTERY_START + 90 + Math.round(FLIGHT_SECONDS * 30f);
                verdicts.add(Verdict.of("down-until-threatened", t.firstUp > BATTERY_START && t.firstUp < firstLanding,
                        "first raised at tick " + t.firstUp + ", rounds launched at " + (BATTERY_START + 90)));
                verdicts.add(Verdict.of("absorbs-the-volleys",
                        t.ship.shieldHits() >= 2 * BATTERY_PIECES && t.ship.hullFraction() >= 0.999f,
                        t.ship.shieldHits() + " hits absorbed, hull " + pct(t.ship.hullFraction())));
                verdicts.add(Verdict.of("drops-after-the-threat-has-passed",
                        t.dropTick > t.lastLanding && !t.ship.shieldUp()
                                && t.dropTick - t.lastLanding <= Math.round(WarshipShield.HOLD_SECONDS * 30f) + 90,
                        "last round landed at tick " + t.lastLanding + ", shield down at " + t.dropTick));
                verdicts.add(Verdict.of("vents-once-down",
                        t.ship.overloadCount() == 0 && t.ship.flux() < t.maxFlux * 0.7f,
                        "peak flux " + Math.round(t.maxFlux) + ", " + Math.round(t.ship.flux()) + " at the end"));
            }
            case "guns-shield-down" -> {
                verdicts.add(Verdict.of("fires", t.rounds() > 0,
                        t.rounds() + " rounds in " + t.ticks / 30 + " seconds"));
                verdicts.add(Verdict.of("hull-takes-the-hits",
                        t.ship.hullFraction() < 0.999f && t.ship.shieldHits() == 0,
                        "hull " + pct(t.ship.hullFraction())));
            }
            case "guns-full-pool" -> {
                Trace half = runs.get("guns-shield-down");
                verdicts.add(Verdict.of("fires", t.rounds() > 0, t.rounds() + " rounds"));
                verdicts.add(Verdict.of("full-pool-never-rests",
                        t.maxFlux < WarshipFlux.SOFT_LIMIT * t.ship.fluxCapacity() * 0.75f,
                        String.format(Locale.ROOT, "peak %.0f%% of the hull table's own pool, against %.0f%% of the half pool",
                                t.maxFlux / t.ship.fluxCapacity() * 100f, half.maxFlux / half.ship.fluxCapacity() * 100f)));
            }
            default -> {
                Trace control = runs.get("guns-shield-down");
                verdicts.add(Verdict.of("shield-and-guns-together-overload",
                        t.ship.overloadCount() >= 1 && control.ship.overloadCount() == 0,
                        t.ship.overloadCount() + " overloads, the first at tick " + t.overloadStart
                                + ", against the unshielded " + control.ship.overloadCount()));
                verdicts.add(Verdict.of("never-out-fires-the-bare-hull",
                        t.rounds() > 0 && t.rounds() <= control.rounds(),
                        t.rounds() + " rounds against the unshielded " + control.rounds()));
                verdicts.add(Verdict.of("carries-more-flux-than-the-bare-hull",
                        t.meanFlux() > control.meanFlux() * 1.2f,
                        String.format(Locale.ROOT, "mean flux %.0f against %.0f", t.meanFlux(), control.meanFlux())));
                verdicts.add(Verdict.of("hull-spared-while-the-shield-lasts",
                        t.ship.shieldHits() > 0 && t.ship.hullFraction() > control.ship.hullFraction(),
                        t.ship.shieldHits() + " hits absorbed; hull " + pct(t.ship.hullFraction())
                                + " against " + pct(control.ship.hullFraction())));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("hullFraction", t.ship.hullFraction());
        metrics.put("peakFlux", t.maxFlux);
        metrics.put("meanFlux", t.meanFlux());
        metrics.put("overloads", t.ship.overloadCount());
        metrics.put("overloadStart", t.overloadStart);
        metrics.put("shieldHits", t.ship.shieldHits());
        metrics.put("shieldAbsorbedFlux", t.ship.shieldAbsorbedFlux());
        metrics.put("rounds", t.rounds());
        return new SceneReport(id(), loop, t.ticks, verdicts, metrics);
    }

    private static String pct(float fraction) {
        return Math.round(fraction * 100f) + "%";
    }

    private static final class Trace implements TickObserver {
        final Warship ship;
        final String loop;
        final Side side;
        final int ticks;
        private final float[] flux;
        float maxFlux;
        private double fluxSum;
        private int samples;
        int overloadStart = -1;
        float hullBeforeOverload = -1f;
        private float hullLastTick = 1f;
        int shieldHitsAtOverload;
        boolean monotoneBeforeOverload = true;
        int firstHullDamage = -1;
        int firstUp = -1;
        boolean everUp;
        int dropTick = -1;
        int lastLanding = -1;
        private int lastHits;
        private boolean wasUp;

        Trace(Warship ship, String loop, Side side, int ticks) {
            this.ship = ship;
            this.loop = loop;
            this.side = side;
            this.ticks = ticks;
            this.flux = new float[ticks + 2];
        }

        float fluxAt(int tick) {
            return flux[Math.min(tick, flux.length - 1)];
        }

        int rounds() {
            int n = 0;
            for (Warship.Mount m : ship.mounts()) if (!m.pointDefence()) n += m.shotsFired;
            return n;
        }

        float meanFlux() {
            return samples == 0 ? 0f : (float) (fluxSum / samples);
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            int t = Math.min(tick, ticks + 1);
            flux[t] = ship.flux();
            maxFlux = Math.max(maxFlux, ship.flux());
            fluxSum += ship.flux();
            samples++;
            if (overloadStart < 0 && ship.overloaded()) {
                overloadStart = tick;
                hullBeforeOverload = hullLastTick;
                shieldHitsAtOverload = ship.shieldHits();
            }
            hullLastTick = ship.hullFraction();
            if (overloadStart < 0 && t > 0 && flux[t] + 1e-3f < flux[t - 1]) monotoneBeforeOverload = false;
            if (firstHullDamage < 0 && ship.hullFraction() < 0.9999f) firstHullDamage = tick;
            boolean up = ship.shieldUp();
            if (up) {
                everUp = true;
                if (firstUp < 0) firstUp = tick;
            }
            if (wasUp && !up) dropTick = tick;
            wasUp = up;
            if (ship.shieldHits() > lastHits) {
                lastHits = ship.shieldHits();
                lastLanding = tick;
            }
            fireBattery(sim, tick);
        }

        private void fireBattery(BattleSimulation sim, int tick) {
            if (loop.startsWith("guns-")) {
                // A thin trickle from the front: one round now and then.
                if (tick >= BATTERY_START && (tick - BATTERY_START) % GUNS_INTERVAL == 0) launch(sim, 1);
                return;
            }
            if (loop.equals("auto-threat")) {
                // Two volleys, the second a second after the first; then quiet.
                if (tick == BATTERY_START + 90 || tick == BATTERY_START + 120) launch(sim, BATTERY_PIECES);
                return;
            }
            if (tick >= BATTERY_START && (tick - BATTERY_START) % BATTERY_INTERVAL == 0 && !ship.destroyed()) {
                launch(sim, BATTERY_PIECES);
            }
        }

        private void launch(BattleSimulation sim, int pieces) {
            float sign = side == Side.FRONT ? 1f : -1f;
            // Inside the bow or the stern, so the blast is on the hull's footprint.
            float toX = ship.x;
            float toY = ship.y + sign * ship.lengthCells * 0.3f;
            float fromX = ship.x - 25f;
            float fromY = ship.y + sign * 32f;
            for (int i = 0; i < pieces; i++) {
                PendingDetonation det = new PendingDetonation(0L, toX, toY, 0f, BLAST, DAMAGE, PENETRATION, 0,
                        Faction.DEFENDER, false, 0f, false, false, false);
                sim.queueProjectile(new Projectile(fromX, fromY, toX, toY, false, 0f, Faction.DEFENDER, false,
                        FLIGHT_SECONDS, det));
            }
        }
    }
}
