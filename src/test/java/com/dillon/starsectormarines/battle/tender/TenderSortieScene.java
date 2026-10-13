package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.drone.SortieFlight;
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
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A drone tender's wing as a living swarm tied to a moving ship.
 *
 * <p>{@code sortie} launches the wing from the bay, lets it settle into its loose ring
 * round the hull while the hull gets under way, recalls it (it must fly home and dock,
 * not vanish) and sends it out again. {@code escort} puts a raider on the hull and a
 * bystander just beyond the leash: the wing engages what comes into the hull's reach,
 * does not chase the bystander, and comes back to the ring when it is over. {@code limp}
 * hurts one drone badly: it should leave the fight for the bay, stay there while it is
 * repaired, and then go out again.
 */
public final class TenderSortieScene implements BehaviorScene {

    private static final int WIDTH = 140;
    private static final int HEIGHT = 80;
    private static final float START_X = 30f;
    private static final float START_Y = 40f;
    private static final String CARBINE = "weapon.smg";
    /** Ticks the escort has after the launches to settle before its spacing is judged. */
    private static final int SETTLE_TICKS = 150;
    /** When the sortie loop gets the hull under way, recalls the wing, and sends it out again. */
    private static final int MOVE_TICK = 600;
    private static final int RECALL_TICK = 780;
    private static final int REDEPLOY_TICK = 1250;
    private static final int HURT_TICK = 300;

    @Override public String id() {
        return "tender-sortie";
    }

    @Override public String label() {
        return "Drone tender wing: launches from the bay in a line, rides a loose ring round a moving hull, flies home on "
                + "recall, engages what comes into reach without chasing past the leash, and a badly hurt drone limps home";
    }

    private static int ticks(String loop) {
        return switch (loop) {
            case "sortie" -> 1700;
            case "escort" -> 2400;
            default -> 1500;
        };
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("sortie", "escort", "limp")) reports.add(report(loop, playOne(loop, frames)));
        return reports;
    }

    private record Run(Trace trace, Tender tender, SceneWorld world) {}

    private Run playOne(String loop, FrameSink frames) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        if (loop.equals("escort")) {
            builder.squad("raid").faction(Faction.DEFENDER).size(1).at(62, 38).primary(CARBINE)
                    .assigned(id -> ObjectiveAssignment.attackMove(id, (int) START_X, (int) START_Y)).done();
            // Beyond the leash and the reach beyond it: nothing the wing should go after.
            builder.unit("bystander", Faction.DEFENDER, UnitType.MARINE, 30, 79);
        }
        builder.unit("far-enemy", Faction.DEFENDER, UnitType.MARINE, 135, 75);
        builder.unit("far-friend", Faction.MARINE, UnitType.MARINE, 3, 3);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261013L);
        Tender tender = Tender.fit(Faction.MARINE, START_X, START_Y, -90f);
        sim.tenders().add(sim, tender);
        Trace trace = new Trace(world, tender);
        TickObserver script = (s, tick) -> {
            if (loop.equals("sortie")) {
                if (tick == MOVE_TICK) tender.ship().stationTo(95f, 42f);
                if (tick == RECALL_TICK) {
                    trace.noteRecall(s, tick);
                    tender.defenceOut(false);
                }
                if (tick == REDEPLOY_TICK) tender.defenceOut(true);
            }
            if (loop.equals("limp") && tick == HURT_TICK && !tender.defenders.isEmpty()) {
                long victim = tender.defenders.get(0);
                trace.hurt = victim;
                trace.hurtTick = tick;
                s.applyExternalDamage(victim, 14f, 0f);
            }
        };
        SceneRun.of(world).observe(script, trace)
                .frames(frames, loop, 15, tick -> loop + " / " + tender.statusLine() + " / tick " + tick)
                .run(ticks(loop));
        return new Run(trace, tender, world);
    }

    private SceneReport report(String loop, Run run) {
        Trace t = run.trace();
        Tender tender = run.tender();
        List<Verdict> verdicts = new ArrayList<>();
        float leashSlack = TenderFlight.LEASH_CELLS + 4f;
        switch (loop) {
            case "sortie" -> {
                verdicts.add(Verdict.of("launches-are-staggered",
                        t.launchTicks.size() >= tender.defenceComplement() && t.minLaunchGap() >= 20,
                        "launched at ticks " + t.launchTicks.values() + ", smallest gap " + t.minLaunchGap() + " ticks"));
                verdicts.add(Verdict.of("launched-drones-fly-out-of-the-bay", t.minFlyOut() >= 3.5f,
                        String.format(Locale.ROOT, "each reached at least %.1f cells from the bay within three seconds of "
                                + "leaving it (the exit point is %.0f out)", t.minFlyOut(), SortieFlight.EXIT_CELLS)));
                verdicts.add(Verdict.of("escort-stays-within-the-leash", t.maxHullDistance <= leashSlack,
                        String.format(Locale.ROOT, "furthest %.1f cells from a hull that moved %.0f cells, leash %.0f",
                                t.maxHullDistance, t.hullTravel(), TenderFlight.LEASH_CELLS)));
                verdicts.add(Verdict.of("escort-rides-a-ring-round-the-hull",
                        t.settledSamples > 100 && t.meanHullDistance() >= 2.5f && t.meanHullDistance() <= 10f,
                        String.format(Locale.ROOT, "mean %.1f cells from the hull over %d settled samples",
                                t.meanHullDistance(), t.settledSamples)));
                verdicts.add(Verdict.of("drones-do-not-stack", t.minPair >= 0.6f,
                        String.format(Locale.ROOT, "closest pair %.2f cells once settled", t.minPair)));
                verdicts.add(Verdict.of("recall-takes-flight-time-and-docks",
                        t.recallDocks.size() == tender.defenceComplement() && t.recallFlightRatio() >= 1f
                                && t.distinctRecallDockTicks(),
                        "docked at ticks " + t.recallDocks.values() + " after a recall at " + RECALL_TICK
                                + String.format(Locale.ROOT, ", least flight time against half the full-speed time %.2f", t.recallFlightRatio())));
                verdicts.add(Verdict.of("the-wing-goes-out-again",
                        tender.defendersOut() == tender.defenceComplement() && tender.defendersLost() == 0,
                        tender.defendersOut() + " of " + tender.defenceComplement() + " out, " + tender.defendersLost()
                                + " lost"));
            }
            case "escort" -> {
                verdicts.add(Verdict.of("the-wing-engages-what-comes-into-reach",
                        t.firstRaiderHurt >= 0 && t.raidersAlive() == 0,
                        "first raider hurt at tick " + t.firstRaiderHurt + ", " + t.raidersAlive() + " of 1 still alive, "
                                + tender.defendersLost() + " drones lost"));
                verdicts.add(Verdict.of("the-wing-focuses-the-threat-nearest-the-hull",
                        t.targetSamples >= 30 && t.focusShare() >= 0.6f,
                        String.format(Locale.ROOT, "%.0f%% of %d drone-ticks with a target were on the hostile nearest the hull",
                                100f * t.focusShare(), t.targetSamples)));
                verdicts.add(Verdict.of("escort-stays-within-the-leash", t.maxHullDistance <= leashSlack,
                        String.format(Locale.ROOT, "furthest %.1f cells from the hull, leash %.0f", t.maxHullDistance,
                                TenderFlight.LEASH_CELLS)));
                verdicts.add(Verdict.of("the-wing-does-not-chase-past-the-leash", t.bystanderUnharmed(),
                        "bystander 39 cells off, hit points " + t.bystanderHp()));
                verdicts.add(Verdict.of("drones-do-not-stack", t.minPair >= 0.5f,
                        String.format(Locale.ROOT, "closest pair %.2f cells at tick %d", t.minPair, t.minPairTick)));
                verdicts.add(Verdict.of("the-wing-breaks-off-and-returns-to-the-ring",
                        t.threatSeen >= 0 && t.endHullDistance <= 11f,
                        String.format(Locale.ROOT, "threat first seen at tick %d, the wing ends %.1f cells out at its furthest drone %s",
                                t.threatSeen, t.endHullDistance, t.endState)));
            }
            default -> {
                verdicts.add(Verdict.of("a-hurt-drone-limps-home-alive",
                        t.hurtDockTick >= 0 && tender.defendersLost() == 0,
                        "hurt at tick " + t.hurtTick + ", docked at tick " + t.hurtDockTick + ", "
                                + tender.defendersLost() + " lost"));
                verdicts.add(Verdict.of("the-limp-takes-flight-time", t.hurtDockTick - t.hurtTick >= 10,
                        (t.hurtDockTick - t.hurtTick) + " ticks from the hit to the bay"));
                verdicts.add(Verdict.of("it-is-repaired-before-it-flies-again",
                        t.relaunchAfterHurt >= 0 && t.relaunchAfterHurt - t.hurtDockTick
                                >= (int) (TenderFlight.REPAIR_SECONDS * 30f) - 3,
                        "docked at tick " + t.hurtDockTick + ", relaunched at tick " + t.relaunchAfterHurt
                                + ", repair takes " + (int) (TenderFlight.REPAIR_SECONDS * 30f) + " ticks"));
                verdicts.add(Verdict.of("the-rest-of-the-wing-stays-out", t.minOut >= tender.defenceComplement() - 1,
                        "fewest out at once " + t.minOut + " of " + tender.defenceComplement()));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("maxHullDistance", t.maxHullDistance);
        metrics.put("meanHullDistance", t.meanHullDistance());
        metrics.put("minPair", t.minPair);
        metrics.put("launches", t.launchTicks.size());
        metrics.put("dronesLost", tender.dronesLost());
        return new SceneReport(id(), loop, ticks(loop), verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        private final Tender tender;
        final Map<Long, Integer> launchTicks = new LinkedHashMap<>();
        final Map<Long, Integer> recallDocks = new LinkedHashMap<>();
        private final Map<Long, Float> recallDistance = new LinkedHashMap<>();
        private final Map<Long, Float> flyOut = new LinkedHashMap<>();
        private List<Long> previous = new ArrayList<>();
        private int previousLost;
        private int recallTick = -1;
        float maxHullDistance;
        float minPair = Float.MAX_VALUE;
        int minPairTick = -1;
        private float hullDistanceSum;
        int settledSamples;
        private float startX = Float.NaN;
        private float startY;
        private float endX;
        private float endY;
        String endState = "";
        int firstRaiderHurt = -1;
        int threatSeen = -1;
        float endHullDistance;
        int targetSamples;
        private int focusHits;
        long hurt;
        int hurtTick = -1;
        int hurtDockTick = -1;
        int relaunchAfterHurt = -1;
        int minOut = Integer.MAX_VALUE;
        private BattleSimulation sim;

        private Trace(SceneWorld world, Tender tender) {
            this.world = world;
            this.tender = tender;
        }

        void noteRecall(BattleSimulation sim, int tick) {
            recallTick = tick;
            for (Long id : tender.defenders) {
                recallDistance.put(id, (float) Math.hypot(sim.world().x(id) - tender.bayX(),
                        sim.world().y(id) - tender.bayY()));
            }
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            this.sim = sim;
            if (Float.isNaN(startX)) {
                startX = tender.ship().x;
                startY = tender.ship().y;
            }
            endX = tender.ship().x;
            endY = tender.ship().y;
            List<Long> now = new ArrayList<>(tender.defenders);
            for (Long id : now) {
                if (launchTicks.putIfAbsent(id, tick) == null && hurtDockTick >= 0 && relaunchAfterHurt < 0) {
                    relaunchAfterHurt = tick;
                }
                int launched = launchTicks.get(id);
                if (tick - launched <= 90) {
                    float d = (float) Math.hypot(sim.world().x(id) - tender.bayX(), sim.world().y(id) - tender.bayY());
                    flyOut.merge(id, d, Math::max);
                }
            }
            if (tender.defendersLost() == previousLost) {
                for (Long id : previous) {
                    if (now.contains(id)) continue;
                    if (recallTick >= 0 && tick > recallTick && recallDocks.size() < tender.defenceComplement()
                            && !recallDocks.containsKey(id)) {
                        recallDocks.put(id, tick);
                    }
                    if (id == hurt && hurtDockTick < 0) hurtDockTick = tick;
                }
            }
            previousLost = tender.defendersLost();
            previous = now;
            if (hurtTick >= 0) minOut = Math.min(minOut, tender.defendersOut());

            float worst = 0f;
            for (Long id : now) {
                float d = (float) Math.hypot(sim.world().x(id) - tender.ship().x, sim.world().y(id) - tender.ship().y);
                maxHullDistance = Math.max(maxHullDistance, d);
                worst = Math.max(worst, d);
            }
            if (!now.isEmpty()) endHullDistance = worst;
            StringBuilder sb = new StringBuilder();
            for (Long id : now) {
                TenderFlight.Sortie so = tender.flight.of(id);
                sb.append(String.format(Locale.ROOT, "[%d d=%.1f %s tgt=%d] ", id,
                        Math.hypot(sim.world().x(id) - tender.ship().x, sim.world().y(id) - tender.ship().y),
                        so == null ? "-" : so.leg, sim.world().targetId(id)));
            }
            endState = sb.toString();
            boolean settled = launchTicks.size() >= tender.defenceComplement()
                    && tick >= Collections_max(launchTicks) + SETTLE_TICKS;
            boolean atEase = recallTick < 0 || tick < recallTick - 60;
            if (settled && atEase && now.size() == tender.defenceComplement()) {
                for (Long id : now) {
                    hullDistanceSum += (float) Math.hypot(sim.world().x(id) - tender.ship().x,
                            sim.world().y(id) - tender.ship().y);
                    settledSamples++;
                }
                for (int i = 0; i < now.size(); i++) {
                    for (int j = i + 1; j < now.size(); j++) {
                        float pairGap = (float) Math.hypot(
                                sim.world().x(now.get(i)) - sim.world().x(now.get(j)),
                                sim.world().y(now.get(i)) - sim.world().y(now.get(j)));
                        if (pairGap < minPair) minPairTick = tick;
                        minPair = Math.min(minPair, (float) Math.hypot(
                                sim.world().x(now.get(i)) - sim.world().x(now.get(j)),
                                sim.world().y(now.get(i)) - sim.world().y(now.get(j))));
                    }
                }
            }
            if (threatSeen < 0 && tender.threatened()) threatSeen = tick;
            trackRaid(sim, tick, now);
        }

        private static int Collections_max(Map<Long, Integer> m) {
            int best = 0;
            for (int v : m.values()) best = Math.max(best, v);
            return best;
        }

        private void trackRaid(BattleSimulation sim, int tick, List<Long> now) {
            if (!hasRaid()) return;
            if (firstRaiderHurt < 0) {
                for (long u : world.members("raid")) {
                    if (!sim.getRoster().isAliveById(u) || sim.world().hp(u) < sim.world().maxHp(u)) {
                        firstRaiderHurt = tick;
                        break;
                    }
                }
            }
            long nearest = 0L;
            float best = TenderFlight.REACH_CELLS;
            UnitRosterService roster = sim.getRoster();
            long[] dense = roster.denseArray();
            for (int i = 0, n = roster.liveCount(); i < n; i++) {
                long u = dense[i];
                if (!Faction.MARINE.hostileTo(roster.identity().faction(u))) continue;
                float d = (float) Math.hypot(sim.world().x(u) - tender.ship().x, sim.world().y(u) - tender.ship().y);
                if (d < best) {
                    best = d;
                    nearest = u;
                }
            }
            if (nearest == 0L) return;
            for (Long id : now) {
                long target = sim.world().targetId(id);
                if (target == 0L) continue;
                targetSamples++;
                if (target == nearest) focusHits++;
            }
        }

        private boolean hasRaid() {
            try {
                world.squad("raid");
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        int raidersAlive() {
            if (!hasRaid() || sim == null) return 0;
            int alive = 0;
            for (long u : world.members("raid")) if (sim.getRoster().isAliveById(u)) alive++;
            return alive;
        }

        float focusShare() {
            return targetSamples == 0 ? 0f : (float) focusHits / targetSamples;
        }

        float meanHullDistance() {
            return settledSamples == 0 ? 0f : hullDistanceSum / settledSamples;
        }

        float hullTravel() {
            return (float) Math.hypot(endX - startX, endY - startY);
        }

        int minLaunchGap() {
            int gap = Integer.MAX_VALUE;
            Integer last = null;
            for (int tick : launchTicks.values()) {
                if (last != null) gap = Math.min(gap, tick - last);
                last = tick;
            }
            return gap;
        }

        float minFlyOut() {
            float least = Float.MAX_VALUE;
            for (float d : flyOut.values()) least = Math.min(least, d);
            return least;
        }

        /** The smallest ratio of a drone's real flight home to half of what its distance needs at full speed. */
        float recallFlightRatio() {
            float ratio = Float.MAX_VALUE;
            for (Map.Entry<Long, Integer> e : recallDocks.entrySet()) {
                Float d0 = recallDistance.get(e.getKey());
                if (d0 == null) continue;
                float needed = Math.max(0f, d0 - SortieFlight.DOCK_CELLS) / 3.5f * 30f;
                float took = e.getValue() - recallTick;
                ratio = Math.min(ratio, needed < 1f ? 1f : took / (0.5f * needed));
            }
            return ratio == Float.MAX_VALUE ? 0f : ratio;
        }

        boolean distinctRecallDockTicks() {
            return recallDocks.values().stream().distinct().count() == recallDocks.size();
        }

        boolean bystanderUnharmed() {
            return bystanderHp() >= bystanderMax();
        }

        float bystanderHp() {
            return world.sim().world().hp(unit("bystander"));
        }

        private float bystanderMax() {
            return world.sim().world().maxHp(unit("bystander"));
        }

        private long unit(String name) {
            return world.unit(name);
        }
    }
}
