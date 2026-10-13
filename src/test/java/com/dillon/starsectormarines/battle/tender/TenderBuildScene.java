package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A drone tender asked to put up a turret, with and without a raid on the site.
 *
 * <p>{@code build} is the plain job: the tender flies to a standoff point short of
 * the site, its builders fly out and work it for the time a full crew needs, the
 * turret stands, and the builders go home. {@code raid} is the same job with four
 * armed defenders walking onto the site: the tender's own drones are the only
 * thing between them and the builders, the work stops while the site is
 * contested, and the question is whether the screen holds. {@code undefended} is
 * that raid with the defence drones recalled into the bay: the control that says
 * the screen, and not the map, did the work.
 */
public final class TenderBuildScene implements BehaviorScene {

    private static final int WIDTH = 140;
    private static final int HEIGHT = 80;
    private static final int TICKS = 2400;
    private static final float START_X = 14f;
    private static final float START_Y = 40f;
    private static final float SITE_X = 60.5f;
    private static final float SITE_Y = 40.5f;
    /** A patrol the screen can stop, and an assault it cannot. */
    private static final int PATROL = 1;
    private static final int ASSAULT = 4;
    /** When the overrun loop's player gives the job up. */
    private static final int CANCEL_TICK = 1800;
    private static final int RAID_X = 88;
    /** Ticks after the last builder arrives before stations are expected to be clean of the arrival traffic. */
    private static final int SETTLE_TICKS = 90;
    private static final String CARBINE = "weapon.smg";
    /** Thirty-two cells of reach: outranges the hull's and the builders' twenty cells of sight. */
    private static final String MARKSMAN_RIFLE = "weapon.dmr";
    /** Twenty-seven cells north of the site: beyond the tender's sight, inside the rifle's reach of the builders. */
    private static final int MARKSMAN_X = 60;
    private static final int MARKSMAN_Y = 67;

    @Override public String id() {
        return "tender-build";
    }

    @Override public String label() {
        return "Drone tender: it flies to a site, its borer drones build a turret there, its defence drones screen the work "
                + "against a raid, and with the screen recalled the same raid is not stopped; an unseen marksman is found only "
                + "by the hits on the builders, where the all-seeing control stood the site down before any shot";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("build", "raid", "undefended", "overrun", "ambush", "ambush-omniscient-control")) {
            reports.add(report(loop, playOne(loop, frames)));
        }
        return reports;
    }

    private record Run(Trace trace, Tender tender) {}

    private static int raiders(String loop) {
        return loop.equals("overrun") ? ASSAULT : PATROL;
    }

    private static boolean ambush(String loop) {
        return loop.startsWith("ambush");
    }

    private Run playOne(String loop, FrameSink frames) throws Exception {
        String previous = System.getProperty(TenderThreat.PROPERTY);
        if (loop.equals("ambush-omniscient-control")) System.setProperty(TenderThreat.PROPERTY, "false");
        try {
            return playWorld(loop, frames);
        } finally {
            if (previous == null) System.clearProperty(TenderThreat.PROPERTY);
            else System.setProperty(TenderThreat.PROPERTY, previous);
        }
    }

    private Run playWorld(String loop, FrameSink frames) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        if (ambush(loop)) {
            // One rifleman who holds his ground: nobody sees him until he fires.
            builder.squad("raid").faction(Faction.DEFENDER).size(1).at(MARKSMAN_X, MARKSMAN_Y).primary(MARKSMAN_RIFLE)
                    .stationary().done();
        } else if (!loop.equals("build")) {
            builder.squad("raid").faction(Faction.DEFENDER).size(raiders(loop)).at(RAID_X, 38).primary(CARBINE)
                    .assigned(id -> ObjectiveAssignment.attackMove(id, (int) SITE_X, (int) SITE_Y)).done();
        }
        // Somebody on each side must stay alive for the clock to keep running.
        builder.unit("far-enemy", Faction.DEFENDER, UnitType.MARINE, 135, 75);
        builder.unit("far-friend", Faction.MARINE, UnitType.MARINE, 3, 3);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261012L);
        Tender tender = Tender.fit(Faction.MARINE, START_X, START_Y, -90f);
        // The ambush loops recall the screen: left out it would fly to the shooter and kill him, and the
        // question is what the builders alone do about a marksman they cannot see.
        if (loop.equals("undefended") || ambush(loop)) tender.defenceOut(false);
        sim.tenders().add(sim, tender);
        ConstructionOrder order = tender.lay(BuildOption.VULCAN_TURRET, SITE_X, SITE_Y, sim.getGrid());
        if (order == null) throw new IllegalStateException("the scene's site was refused");
        Trace trace = new Trace(world, tender, order);
        TickObserver script = (s, tick) -> {
            if (loop.equals("overrun") && tick == CANCEL_TICK) tender.cancelAll();
        };
        SceneRun.of(world).observe(script, trace)
                .frames(frames, loop, 15, tick -> loop + " / " + tender.statusLine() + " / tick " + tick)
                .run(TICKS);
        return new Run(trace, tender);
    }

    private SceneReport report(String loop, Run run) {
        Trace t = run.trace();
        Tender tender = run.tender();
        float fullSeconds = BuildOption.VULCAN_TURRET.buildSeconds();
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case "build" -> {
                verdicts.add(Verdict.of("the-work-is-heard-once-not-per-builder",
                        t.cueCount(TenderCues.Kind.WELD) > 0 && t.cueCount(TenderCues.Kind.WELD) <= t.workTicksSeen / TenderCues.WELD_GAP_TICKS + 1,
                        t.cueCount(TenderCues.Kind.WELD) + " weld sounds over " + t.workTicksSeen + " working ticks"));
                verdicts.add(Verdict.of("completion-is-cued-once",
                        t.cueCount(TenderCues.Kind.COMPLETE) == 1, t.cueCount(TenderCues.Kind.COMPLETE) + " completion cues"));
                verdicts.add(Verdict.of("arrives-and-starts-work", t.workStart >= 0,
                        "work began at tick " + t.workStart));
                verdicts.add(Verdict.of("builders-fly-out-and-reach-the-site",
                        t.peakBuildersOut == tender.builderComplement() && t.peakOnSite >= tender.builderComplement(),
                        t.peakBuildersOut + " out, " + t.peakOnSite + " at the site, of " + tender.builderComplement()));
                float seconds = t.workSeconds();
                verdicts.add(Verdict.of("full-crew-takes-the-authored-time",
                        t.completeTick >= 0 && seconds >= fullSeconds * 0.95f && seconds <= fullSeconds * 1.6f,
                        String.format(Locale.ROOT, "%.1fs of work against %.1fs for a full crew", seconds, fullSeconds)));
                verdicts.add(Verdict.of("the-structure-stands", t.turretAlive(),
                        "turret at the site: " + t.turretAlive() + ", completed at tick " + t.completeTick));
                verdicts.add(Verdict.of("building-is-free",
                        !tender.metered() && tender.stock() == Tender.UNLIMITED,
                        tender.metered() ? tender.stock() + " charges left" : "unmetered; nothing was drawn"));
                verdicts.add(Verdict.of("builders-come-home",
                        tender.buildersOut() == 0 && tender.builderReserve() == tender.builderComplement(),
                        tender.buildersOut() + " still out, " + tender.builderReserve() + " in the bay"));
                verdicts.add(Verdict.of("builders-leave-the-bay-in-a-line",
                        t.launchTicks.size() == tender.builderComplement() && t.minLaunchGap() >= 12,
                        "launched at ticks " + t.launchTicks.values() + ", smallest gap " + t.minLaunchGap() + " ticks"));
                verdicts.add(Verdict.of("builders-fly-to-the-site-rather-than-appear-there",
                        t.arrivalTicks.size() == tender.builderComplement() && t.minTransit() >= 20,
                        "shortest bay-to-site flight " + t.minTransit() + " ticks over " + t.arrivalTicks.size() + " builders"));
                verdicts.add(Verdict.of("builders-hold-distinct-stations",
                        t.fullCrewTicks > 60 && t.minStationGap >= 1.2f,
                        "closest pair " + String.format(Locale.ROOT, "%.2f", t.minStationGap) + " cells over "
                                + t.fullCrewTicks + " ticks settled at the site"));
                verdicts.add(Verdict.of("torches-are-lit-at-the-stations", t.weldTicks >= 60,
                        t.weldTicks + " builder-ticks at a station with the torch on the site"));
                verdicts.add(Verdict.of("builders-fly-home-in-order-and-take-time",
                        t.dockTicks.size() == tender.builderComplement() && t.dockedInOrder() && t.minHomeFlight() >= 15,
                        "docked at ticks " + t.dockTicks.values() + " after completion at " + t.completeTick
                                + ", shortest flight home " + t.minHomeFlight() + " ticks"));
                verdicts.add(Verdict.of("defence-drones-stay-out",
                        tender.defendersOut() == tender.defenceComplement(),
                        tender.defendersOut() + " of " + tender.defenceComplement() + " screening"));
            }
            case "raid" -> {
                verdicts.add(Verdict.of("drones-hurt-the-raid-before-the-turret-exists",
                        t.firstRaiderHurt >= 0 && (t.completeTick < 0 || t.firstRaiderHurt < t.completeTick),
                        "first raider hurt at tick " + t.firstRaiderHurt + ", turret at tick " + t.completeTick));
                verdicts.add(Verdict.of("a-contested-site-is-not-worked", t.contestedTicks > 0 && t.workedWhileContested == 0,
                        t.contestedTicks + " contested ticks, " + t.workedWhileContested + " of them advanced the build"));
                verdicts.add(Verdict.of("the-screen-clears-the-raid", t.raidersAlive() == 0,
                        t.raidersAlive() + " of " + PATROL + " raiders alive, " + tender.defendersLost()
                                + " defence drones lost"));
                verdicts.add(Verdict.of("builders-stand-down-rather-than-die", tender.buildersLost() == 0,
                        tender.buildersLost() + " builders lost, " + t.contestedTicks + " ticks stood down"));
                verdicts.add(Verdict.of("the-job-resumes-and-finishes", t.completeTick > t.lastContested && t.turretAlive(),
                        "last contested tick " + t.lastContested + ", turret at tick " + t.completeTick));
            }
            case "overrun" -> {
                verdicts.add(Verdict.of("an-assault-overruns-the-screen", tender.defendersLost() == tender.defenceComplement(),
                        tender.defendersLost() + " of " + tender.defenceComplement() + " defence drones lost against "
                                + ASSAULT + " raiders, " + t.raidersAlive() + " still alive"));
                verdicts.add(Verdict.of("builders-stay-home-and-the-job-stalls",
                        tender.buildersLost() == 0 && t.completeTick < 0 && t.contestedTicks > 0,
                        tender.buildersLost() + " builders lost, build " + (t.completeTick >= 0 ? "finished" : "unfinished")
                                + ", " + t.contestedTicks + " contested ticks"));
                verdicts.add(Verdict.of("cancelling-withdraws-the-order",
                        t.order.phase() == ConstructionOrder.Phase.CANCELLED && tender.orders().isEmpty(),
                        "order " + t.order.phase() + ", " + tender.orders().size() + " still on the line"));
            }
            case "ambush" -> {
                verdicts.add(Verdict.of("the-stand-down-is-cued",
                        t.cueCount(TenderCues.Kind.ALERT) >= 1,
                        t.cueCount(TenderCues.Kind.ALERT) + " stand-down pings, " + t.cueCount(TenderCues.Kind.STUN) + " stun bursts"));
                int blockTicks = Math.round(TenderThreat.BLOCK_SECONDS / BattleSimulation.TICK_DT);
                verdicts.add(Verdict.of("builders-are-shot-by-someone-unseen", t.firstHit >= 0 && t.firstRaiderSeen < 0,
                        "first builder hit at tick " + t.firstHit + ", " + t.builderDamage + " HP of builder damage, marksman "
                                + "ever within sight of the tender: " + (t.firstRaiderSeen >= 0)));
                verdicts.add(Verdict.of("not-stood-down-before-the-first-hit",
                        t.firstHit >= 0 && t.workTicksBeforeHit > 0 && (t.firstContested < 0 || t.firstContested >= t.firstHit),
                        t.workTicksBeforeHit + " ticks worked before the hit at tick " + t.firstHit
                                + ", first contested tick " + t.firstContested));
                verdicts.add(Verdict.of("a-hit-puts-the-site-under-fire",
                        t.firstUnderFire >= 0 && t.firstUnderFire >= t.firstHit,
                        "first underFire tick " + t.firstUnderFire + " after the hit at " + t.firstHit));
                verdicts.add(Verdict.of("builders-go-home-after-the-hit",
                        t.homeAfterHit >= 0 && t.homeAfterHit - t.firstHit <= 300,
                        "no builder out at tick " + t.homeAfterHit + ", " + (t.homeAfterHit - t.firstHit) + " ticks after the hit"));
                // Either the crew survives and the work resumes only after the block, or the volley
                // that hit them killed them all and the order ends for want of builders. Both are
                // honest outcomes of an unseen shooter; the detail says which one this world gave.
                boolean resumed = t.firstResume >= 0 && t.resumeGap >= blockTicks;
                boolean crewLost = t.firstResume < 0 && tender.buildersLost() == tender.builderComplement()
                        && t.order.phase() == ConstructionOrder.Phase.FAILED;
                verdicts.add(Verdict.of("work-resumes-after-the-block-or-the-crew-is-gone", resumed || crewLost,
                        resumed ? "first progress after the hit at tick " + t.firstResume + ", " + t.resumeGap
                                + " ticks after the most recent hit (block is " + blockTicks + ")"
                                : "no progress after the hit: " + tender.buildersLost() + " of " + tender.builderComplement()
                                + " builders lost (lost at ticks " + t.lossTicks + ", builders flew out again at tick " + t.relaunch + "), order " + t.order.phase() + " \""
                                + t.order.failure() + "\" at " + Math.round(t.order.fraction() * 100f) + "%"));
                verdicts.add(Verdict.of("no-work-while-under-fire", t.workedWhileUnderFire == 0,
                        t.underFireTicks + " under-fire ticks, " + t.workedWhileUnderFire + " of them advanced the build"));
            }
            case "ambush-omniscient-control" -> {
                verdicts.add(Verdict.of("stood-down-before-any-builder-is-hit",
                        t.firstContested >= 0 && (t.firstHit < 0 || t.firstContested < t.firstHit) && t.builderDamage == 0f,
                        "first contested tick " + t.firstContested + " with the marksman unseen, first hit tick " + t.firstHit
                                + ", " + t.builderDamage + " HP of builder damage"));
                verdicts.add(Verdict.of("never-under-fire-because-never-hit", t.underFireTicks == 0 && tender.buildersLost() == 0,
                        t.underFireTicks + " under-fire ticks, " + tender.buildersLost() + " builders lost"));
                verdicts.add(Verdict.of("the-job-stalls", t.completeTick < 0 && t.contestedTicks > 0,
                        "build " + (t.completeTick >= 0 ? "finished at tick " + t.completeTick : "unfinished")
                                + ", " + t.contestedTicks + " contested ticks"));
            }
            default -> {
                verdicts.add(Verdict.of("no-screen-is-out", tender.defendersOut() == 0 && t.defenceEverOut == 0,
                        tender.defendersOut() + " defence drones out"));
                verdicts.add(Verdict.of("raiders-are-unharmed", t.firstRaiderHurt < 0,
                        "first raider hurt at tick " + t.firstRaiderHurt));
                verdicts.add(Verdict.of("the-job-stalls-while-the-threat-stands",
                        t.completeTick < 0 && t.contestedTicks > 0,
                        "build " + (t.completeTick >= 0 ? "finished at tick " + t.completeTick : "unfinished")
                                + ", " + t.contestedTicks + " contested ticks"));
                verdicts.add(Verdict.of("builders-are-kept-safe", tender.buildersLost() == 0,
                        tender.buildersLost() + " builders lost"));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("workStartTick", t.workStart);
        metrics.put("completeTick", t.completeTick);
        metrics.put("workSeconds", t.workSeconds());
        metrics.put("contestedTicks", t.contestedTicks);
        metrics.put("workedWhileContested", t.workedWhileContested);
        metrics.put("peakBuildersOut", t.peakBuildersOut);
        metrics.put("peakOnSite", t.peakOnSite);
        metrics.put("dronesLost", tender.dronesLost());
        metrics.put("raidersAlive", t.raidersAlive());
        metrics.put("firstRaiderHurtTick", t.firstRaiderHurt);
        metrics.put("firstBuilderHitTick", t.firstHit);
        for (TenderCues.Kind kind : TenderCues.Kind.values()) metrics.put("cue" + kind, t.cueCount(kind));
        metrics.put("builderDamage", t.builderDamage);
        metrics.put("buildersLost", tender.buildersLost());
        metrics.put("underFireTicks", t.underFireTicks);
        metrics.put("finalFraction", t.order.fraction());
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        private final Tender tender;
        final ConstructionOrder order;
        int workStart = -1;
        int completeTick = -1;
        int contestedTicks;
        int lastContested = -1;
        int workedWhileContested;
        int peakBuildersOut;
        int peakOnSite;
        int defenceEverOut;
        int firstRaiderHurt = -1;
        int firstHit = -1;
        int workTicksSeen;
        private final int[] cues = new int[TenderCues.Kind.values().length];
        final List<Integer> lossTicks = new ArrayList<>();
        int relaunch = -1;
        int lastHit = -1;
        float builderDamage;
        int workTicksBeforeHit;
        int firstContested = -1;
        int firstUnderFire = -1;
        int underFireTicks;
        int workedWhileUnderFire;
        int homeAfterHit = -1;
        int firstResume = -1;
        int resumeGap = -1;
        int firstRaiderSeen = -1;
        private final Map<Long, Float> hp = new LinkedHashMap<>();
        private int lostSeen;
        final Map<Long, Integer> launchTicks = new LinkedHashMap<>();
        final Map<Long, Integer> arrivalTicks = new LinkedHashMap<>();
        final Map<Long, Integer> dockTicks = new LinkedHashMap<>();
        private List<Long> previousBuilders = new ArrayList<>();
        private int previousBuildersLost;
        int fullCrewTicks;
        float minStationGap = Float.MAX_VALUE;
        int weldTicks;
        private float lastFraction;
        private boolean turret;
        private int workTicks;

        private Trace(SceneWorld world, Tender tender, ConstructionOrder order) {
            this.world = world;
            this.tender = tender;
            this.order = order;
        }

        int cueCount(TenderCues.Kind kind) {
            return cues[kind.ordinal()];
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            ConstructionOrder.Phase phase = order.phase();
            for (TenderCues.Cue cue : sim.tenders().cues().cuesThisFrame()) cues[cue.kind().ordinal()]++;
            if (phase == ConstructionOrder.Phase.WORKING) workTicksSeen++;
            if (phase == ConstructionOrder.Phase.WORKING) {
                if (workStart < 0) workStart = tick;
                workTicks++;
                if (firstHit < 0) workTicksBeforeHit++;
                if (order.contested() && firstContested < 0) firstContested = tick;
                if (order.underFire()) {
                    underFireTicks++;
                    if (firstUnderFire < 0) firstUnderFire = tick;
                    if (order.fraction() > lastFraction + 1e-6f) workedWhileUnderFire++;
                } else if (firstHit >= 0 && firstResume < 0 && !order.contested()
                        && order.fraction() > lastFraction + 1e-6f) {
                    firstResume = tick;
                    resumeGap = tick - lastHit;
                }
                if (order.contested()) {
                    contestedTicks++;
                    lastContested = tick;
                    if (order.fraction() > lastFraction + 1e-6f) workedWhileContested++;
                }
                lastFraction = order.fraction();
            }
            if (phase == ConstructionOrder.Phase.COMPLETE && completeTick < 0) completeTick = tick;
            boolean hit = false;
            for (Long id : tender.builders) {
                float now = sim.world().hp(id);
                Float before = hp.put(id, now);
                if (before != null && now < before) {
                    builderDamage += before - now;
                    hit = true;
                }
            }
            if (tender.buildersLost() > lostSeen) {
                int lostBefore = lostSeen;
                lostSeen = tender.buildersLost();
                for (int k = lostBefore; k < lostSeen; k++) lossTicks.add(tick);
                hit = true;
            }
            if (hit) {
                if (firstHit < 0) firstHit = tick;
                lastHit = tick;
            }
            if (firstHit >= 0 && homeAfterHit < 0 && tick > firstHit && tender.buildersOut() == 0) homeAfterHit = tick;
            if (homeAfterHit >= 0 && relaunch < 0 && tick > homeAfterHit && tender.buildersOut() > 0) relaunch = tick;
            if (firstRaiderSeen < 0 && hasRaid() && withinSight(sim)) firstRaiderSeen = tick;
            peakBuildersOut = Math.max(peakBuildersOut, tender.buildersOut());
            defenceEverOut = Math.max(defenceEverOut, tender.defendersOut());
            int onSite = 0;
            for (Long id : tender.builders) {
                if (sim.resolveUnit(id) == 0L) continue;
                if (Math.hypot(sim.world().x(id) - order.siteX(), sim.world().y(id) - order.siteY())
                        <= TenderSystem.WORK_RADIUS) {
                    onSite++;
                }
            }
            peakOnSite = Math.max(peakOnSite, onSite);
            trackSorties(sim, tick);
            UnitRosterService roster = sim.getRoster();
            turret = false;
            long[] dense = roster.denseArray();
            for (int i = 0, n = roster.liveCount(); i < n; i++) {
                long u = dense[i];
                if (roster.identity().type(u).isTurret() && roster.identity().faction(u) == Faction.MARINE) turret = true;
            }
            if (firstRaiderHurt < 0 && hasRaid()) {
                for (long u : world.members("raid")) {
                    boolean alive = sim.getRoster().isAliveById(u);
                    if (!alive || sim.world().hp(u) < sim.world().maxHp(u)) {
                        firstRaiderHurt = tick;
                        break;
                    }
                }
            }
            this.sim = sim;
        }

        private BattleSimulation sim;

        /** Whether a raider is inside the tender's own sight: the hull, or any drone it has out. */
        private boolean withinSight(BattleSimulation sim) {
            float sight = TenderThreat.OVERHEAD_SIGHT_CELLS;
            for (long u : world.members("raid")) {
                if (!sim.getRoster().isAliveById(u)) continue;
                float x = sim.world().x(u);
                float y = sim.world().y(u);
                if (Math.hypot(x - tender.ship().x, y - tender.ship().y) <= sight) return true;
                List<Long> eyes = new ArrayList<>(tender.builders);
                eyes.addAll(tender.defenders);
                for (Long id : eyes) {
                    if (Math.hypot(x - sim.world().x(id), y - sim.world().y(id)) <= sight) return true;
                }
            }
            return false;
        }

        private void trackSorties(BattleSimulation sim, int tick) {
            List<Long> now = new ArrayList<>(tender.builders);
            for (Long id : now) {
                launchTicks.putIfAbsent(id, tick);
                if (!arrivalTicks.containsKey(id) && Math.hypot(sim.world().x(id) - order.siteX(),
                        sim.world().y(id) - order.siteY()) <= TenderSystem.WORK_RADIUS) {
                    arrivalTicks.put(id, tick);
                }
            }
            if (tender.buildersLost() == previousBuildersLost) {
                for (Long id : previousBuilders) if (!now.contains(id)) dockTicks.put(id, tick);
            }
            previousBuildersLost = tender.buildersLost();
            previousBuilders = now;
            List<Long> onSite = new ArrayList<>();
            for (Long id : now) {
                float x = sim.world().x(id);
                float y = sim.world().y(id);
                if (Math.hypot(x - order.siteX(), y - order.siteY()) <= TenderSystem.WORK_RADIUS) onSite.add(id);
                if (tender.welding(id, order, x, y)) weldTicks++;
            }
            int settled = arrivalTicks.size() < tender.builderComplement() ? Integer.MAX_VALUE
                    : Collections.max(arrivalTicks.values()) + SETTLE_TICKS;
            if (onSite.size() == tender.builderComplement() && tick >= settled
                    && order.phase() == ConstructionOrder.Phase.WORKING && !order.contested()) {
                fullCrewTicks++;
                for (int i = 0; i < onSite.size(); i++) {
                    for (int j = i + 1; j < onSite.size(); j++) {
                        float gap = (float) Math.hypot(sim.world().x(onSite.get(i)) - sim.world().x(onSite.get(j)),
                                sim.world().y(onSite.get(i)) - sim.world().y(onSite.get(j)));
                        minStationGap = Math.min(minStationGap, gap);
                    }
                }
            }
        }

        int minLaunchGap() {
            int gap = Integer.MAX_VALUE;
            Integer previous = null;
            for (int t : launchTicks.values()) {
                if (previous != null) gap = Math.min(gap, t - previous);
                previous = t;
            }
            return gap;
        }

        int minTransit() {
            int shortest = Integer.MAX_VALUE;
            for (Map.Entry<Long, Integer> e : arrivalTicks.entrySet()) {
                shortest = Math.min(shortest, e.getValue() - launchTicks.get(e.getKey()));
            }
            return shortest;
        }

        boolean dockedInOrder() {
            int last = -1;
            for (Long id : launchTicks.keySet()) {
                Integer dock = dockTicks.get(id);
                if (dock == null || dock <= last) return false;
                last = dock;
            }
            return true;
        }

        int minHomeFlight() {
            int shortest = Integer.MAX_VALUE;
            for (int dock : dockTicks.values()) shortest = Math.min(shortest, dock - completeTick);
            return shortest;
        }

        private boolean hasRaid() {
            try {
                world.squad("raid");
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        float workSeconds() {
            return workTicks * BattleSimulation.TICK_DT;
        }

        boolean turretAlive() {
            return turret;
        }

        int raidersAlive() {
            if (!hasRaid() || sim == null) return 0;
            int alive = 0;
            for (long u : world.members("raid")) if (sim.getRoster().isAliveById(u)) alive++;
            return alive;
        }
    }
}
