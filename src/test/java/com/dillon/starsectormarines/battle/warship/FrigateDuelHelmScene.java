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
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Slice 2 of the frigate duel ({@code warship-behaviours.md}, ship against ship): the duel helm and the variant's flux fittings. Two Wolf (H) and two
 * Hound (P) start a hundred and twenty cells apart, far out of reach, under {@link ShipFireOrder#ALL} and nothing else.
 *
 * <p>Loops: {@code helm} (the helm flies them: each finds a hull, the outranged Hounds close and the outranging
 * Wolfs hold off between the two reaches and slide) and {@code held} (the same with the helm off: nobody leaves
 * station, so nobody fires). Verdicts are mechanics: the ships find each other and fire, the strongest gun is on the
 * target for most of the time it is in reach, nobody leaves the map, the capacitors and vents are fitted, and the
 * held control neither moves nor fires. Ranges held, hull lost and who fell are readings.
 */
public final class FrigateDuelHelmScene implements BehaviorScene {

    /** The ground grows with hull reach; laid out at a reach multiplier of two. */
    private static final float K = WarshipScale.HULL_REACH_MULTIPLIER / 2f;
    private static final int WIDTH = Math.round(160 * K);
    private static final int HEIGHT = Math.round(68 * K);
    private static final float WOLF_X = 20f * K;
    private static final float HOUND_X = 140f * K;
    private static final float[] LANES = {26f * K, 42f * K};
    private static final int TICKS = Math.round(2700 * K);
    private static final int FRAME_EVERY = 6;
    /** A strongest gun must be on its target for at least this share of the ticks the target is in its reach. */
    private static final float ON_TARGET_SHARE = 0.5f;

    @Override public String id() { return "frigate-duel-helm"; }

    @Override public String label() {
        return "Frigate duel helm: two Wolf (H) and two Hound (P) start out of reach and manoeuvre to fight at the "
                + "range their guns want, on their variants' flux fittings";
    }

    @Override public int frameDelayMillis() { return 100; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("helm", "held")) reports.add(report(loop, playOne(loop, frames)));
        return reports;
    }

    private record Run(Trace trace, BattleSimulation sim) {}

    private Run playOne(String loop, FrameSink frames) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        // One unarmed bystander a side keeps the battle ticking; the duel is the subject.
        builder.squad("west-ground").faction(Faction.MARINE).size(4).at(6, 60).unarmed().stationary().done();
        builder.squad("east-ground").faction(Faction.DEFENDER).size(4).at(112, 60).unarmed().stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261016L);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) sim.getTopology().setGroundKind(x, y, GroundKind.GRASS);
        }
        boolean helm = loop.equals("helm");
        List<Warship> wolves = new ArrayList<>();
        List<Warship> hounds = new ArrayList<>();
        for (float y : LANES) {
            wolves.add(ship("wolf_hegemony", "wolf_hegemony_Assault", Faction.ALLY, WOLF_X, y, 0f, helm));
            hounds.add(ship("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, HOUND_X, y, 180f, helm));
        }
        for (Warship w : wolves) sim.addWarship(w);
        for (Warship h : hounds) sim.addWarship(h);
        Trace trace = new Trace(wolves, hounds);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, FRAME_EVERY, tick -> trace.caption(loop, tick))
                .run(TICKS);
        return new Run(trace, sim);
    }

    private static Warship ship(String hull, String variant, Faction side, float x, float y, float facing,
                                boolean helm) throws Exception {
        Warship ship = Warship.fit(hull, variant, side, x, y, facing);
        ship.stationTo(x, y);
        ship.limitFlux();
        ship.shipFire(ShipFireOrder.ALL);
        ship.duelHelm(helm);
        return ship;
    }

    private SceneReport report(String loop, Run run) {
        Trace t = run.trace();
        WarshipShipFire fire = run.sim().warships().shipFire();
        List<Verdict> verdicts = new ArrayList<>();
        Map<String, Integer> byWeapon = fire.roundsByWeapon();
        if (loop.equals("helm")) {
            // Mechanics, not who wins: they close from far out of reach to inside the longer reach, and a side whose
            // strongest gun had its target in reach fired it. Whether the outranged Hounds live long enough to get
            // their own chainguns into reach against a heavy Wolf holding off is the duel's reading.
            boolean closed = t.closest <= Math.max(WarshipDuelHelm.reach(t.wolves.get(0)), WarshipDuelHelm.reach(t.hounds.get(0)));
            boolean wolvesFired = t.wolfIn == 0 || byWeapon.getOrDefault("pulselaser", 0) > 0;
            boolean houndsFired = t.houndIn == 0 || byWeapon.getOrDefault("chaingun", 0) > 0;
            boolean bothFired = closed && t.wolfIn + t.houndIn > 0 && wolvesFired && houndsFired;
            verdicts.add(Verdict.of("ships-find-each-other-and-fire", bothFired,
                    String.format(Locale.ROOT, "closest approach %.1f cells from 120; rounds %s", t.closest, byWeapon)));
            // A side that never had its target in reach has nothing to measure; at least one side must have.
            boolean wolfOn = t.wolfIn == 0 || t.share(t.wolfOn, t.wolfIn) >= ON_TARGET_SHARE;
            boolean houndOn = t.houndIn == 0 || t.share(t.houndOn, t.houndIn) >= ON_TARGET_SHARE;
            verdicts.add(Verdict.of("the-strongest-gun-is-on-the-target",
                    t.wolfIn + t.houndIn > 0 && wolfOn && houndOn,
                    String.format(Locale.ROOT, "Wolfs %.0f%% of %d ticks in reach, Hounds %.0f%% of %d",
                            100f * t.share(t.wolfOn, t.wolfIn), t.wolfIn, 100f * t.share(t.houndOn, t.houndIn), t.houndIn)));
        } else {
            verdicts.add(Verdict.of("held-ships-neither-move-nor-fire", fire.roundsFired() == 0 && t.maxDrift < 1f,
                    fire.roundsFired() + " rounds, furthest from station " + Math.round(t.maxDrift) + " cells"));
        }
        verdicts.add(Verdict.of("nobody-leaves-the-map", t.offMapTicks == 0, t.offMapTicks + " ship-ticks off the map"));
        verdicts.add(Verdict.of("capacitors-and-vents-are-fitted", t.fittedOk, t.fittedDetail));

        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("roundsAtHulls", fire.roundsFired());
        metrics.put("roundsOnHull", fire.roundsOnHull());
        metrics.put("roundsStrayed", fire.roundsStrayed());
        metrics.put("closestApproachCells", Math.round(t.closest * 10f) / 10f);
        metrics.put("wolfMeanRangeEngaged", Math.round(t.mean(t.wolfRangeSum, t.wolfIn) * 10f) / 10f);
        metrics.put("houndMeanRangeEngaged", Math.round(t.mean(t.houndRangeSum, t.houndIn) * 10f) / 10f);
        metrics.put("wolfReachCells", Math.round(WarshipDuelHelm.reach(t.wolves.get(0)) * 10f) / 10f);
        metrics.put("houndReachCells", Math.round(WarshipDuelHelm.reach(t.hounds.get(0)) * 10f) / 10f);
        metrics.put("wolfHullLost", Math.round(t.hullLost(t.wolves)));
        metrics.put("houndHullLost", Math.round(t.hullLost(t.hounds)));
        metrics.put("wolfShieldHits", t.shieldHits());
        metrics.put("wolfFluxPeakPercent", Math.round(t.wolfFluxPeak * 100f));
        metrics.put("wolfOverloads", t.wolfOverloads());
        metrics.put("wolvesLost", t.lost(t.wolves));
        metrics.put("houndsLost", t.lost(t.hounds));
        metrics.put("firstHullLostTick", t.firstLossTick);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        final List<Warship> wolves;
        final List<Warship> hounds;
        private final Map<Warship, float[]> start = new LinkedHashMap<>();
        float closest = Float.MAX_VALUE;
        int wolfIn;
        int wolfOn;
        int houndIn;
        int houndOn;
        float wolfRangeSum;
        float houndRangeSum;
        float maxDrift;
        int offMapTicks;
        float wolfFluxPeak;
        int firstLossTick = -1;
        boolean fittedOk;
        String fittedDetail = "";

        Trace(List<Warship> wolves, List<Warship> hounds) {
            this.wolves = wolves;
            this.hounds = hounds;
            for (Warship w : wolves) start.put(w, new float[]{w.x, w.y});
            for (Warship h : hounds) start.put(h, new float[]{h.x, h.y});
            Warship wolf = wolves.get(0);
            Warship hound = hounds.get(0);
            // Wolf (H) Assault: 7 capacitors and 10 vents on 2250 / 150; Hound (P) Standard: 9 and 10 on 750 / 100.
            float wolfCap = 2250f + 7 * Warship.FLUX_PER_CAPACITOR;
            float houndCap = 750f + 9 * Warship.FLUX_PER_CAPACITOR;
            fittedOk = Math.abs(wolf.fluxCapacity() - wolfCap) < 0.5f && Math.abs(hound.fluxCapacity() - houndCap) < 0.5f;
            fittedDetail = String.format(Locale.ROOT, "Wolf capacity %.0f (want %.0f), Hound %.0f (want %.0f)",
                    wolf.fluxCapacity(), wolfCap, hound.fluxCapacity(), houndCap);
        }

        String caption(String loop, int tick) {
            StringBuilder s = new StringBuilder(String.format(Locale.ROOT, "%s / tick %d", loop, tick));
            for (Warship w : wolves) s.append(describe("Wolf", w));
            for (Warship h : hounds) s.append(describe("Hound", h));
            return s.toString();
        }

        private static String describe(String name, Warship ship) {
            if (ship.destroyed()) return " / " + name + " down";
            return String.format(Locale.ROOT, " / %s %.0f%% flux %.0f%%%s", name, ship.hullFraction() * 100f,
                    ship.fluxFraction() * 100f, ship.shieldUp() ? " shield" : "");
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            List<Warship> all = new ArrayList<>(wolves);
            all.addAll(hounds);
            for (Warship s : all) {
                if (s.destroyed()) {
                    if (firstLossTick < 0) firstLossTick = tick;
                    continue;
                }
                float[] home = start.get(s);
                maxDrift = Math.max(maxDrift, (float) Math.hypot(s.x - home[0], s.y - home[1]));
                if (s.x < 0f || s.y < 0f || s.x > sim.getGrid().getWidth() || s.y > sim.getGrid().getHeight()) {
                    offMapTicks++;
                }
                Warship target = s.hullTarget();
                if (target == null || target.destroyed()) continue;
                float gap = (float) Math.hypot(target.centerX() - s.centerX(), target.centerY() - s.centerY());
                closest = Math.min(closest, gap);
                float reach = WarshipDuelHelm.reach(s);
                if (gap > reach) continue;
                boolean on = strongestBears(s, target);
                if (wolves.contains(s)) {
                    wolfIn++;
                    wolfRangeSum += gap;
                    if (on) wolfOn++;
                } else {
                    houndIn++;
                    houndRangeSum += gap;
                    if (on) houndOn++;
                }
            }
            for (Warship w : wolves) wolfFluxPeak = Math.max(wolfFluxPeak, w.fluxFraction());
        }

        /** Whether the strongest anti-ship gun bears on where its round would meet the target (its lead point). */
        private static boolean strongestBears(Warship ship, Warship target) {
            Warship.Mount strongest = WarshipShipRules.strongest(ship);
            if (strongest == null) return false;
            float[] aim = WarshipShipRules.aimPoint(ship, target);
            return Warship.bears(strongest, ship.facingDegrees, ship.mountX(strongest), ship.mountY(strongest),
                    aim[0], aim[1], WarshipScale.HULL_REACH_MULTIPLIER);
        }

        float share(int on, int in) {
            return in == 0 ? 0f : (float) on / in;
        }

        float mean(float sum, int n) {
            return n == 0 ? 0f : sum / n;
        }

        float hullLost(List<Warship> ships) {
            float lost = 0f;
            for (Warship s : ships) lost += s.maxHitPoints - Math.max(0f, s.hitPoints());
            return lost;
        }

        int shieldHits() {
            int n = 0;
            for (Warship w : wolves) n += w.shieldHits();
            return n;
        }

        int wolfOverloads() {
            int n = 0;
            for (Warship w : wolves) n += w.overloadCount();
            return n;
        }

        int lost(List<Warship> ships) {
            int n = 0;
            for (Warship s : ships) if (s.destroyed()) n++;
            return n;
        }
    }
}
