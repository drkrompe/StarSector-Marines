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
    private static final String CARBINE = "weapon.smg";

    @Override public String id() {
        return "tender-build";
    }

    @Override public String label() {
        return "Drone tender: it flies to a site, its borer drones build a turret there, its defence drones screen the work "
                + "against a raid, and with the screen recalled the same raid is not stopped";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("build", "raid", "undefended", "overrun")) {
            reports.add(report(loop, playOne(loop, frames)));
        }
        return reports;
    }

    private record Run(Trace trace, Tender tender) {}

    private static int raiders(String loop) {
        return loop.equals("overrun") ? ASSAULT : PATROL;
    }

    private Run playOne(String loop, FrameSink frames) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        if (!loop.equals("build")) {
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
        if (loop.equals("undefended")) tender.defenceOut(false);
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
                verdicts.add(Verdict.of("spends-one-charge",
                        tender.stock() == Tender.DEFAULT_STOCK - BuildOption.VULCAN_TURRET.cost(),
                        tender.stock() + " of " + Tender.DEFAULT_STOCK + " charges left"));
                verdicts.add(Verdict.of("builders-come-home",
                        tender.buildersOut() == 0 && tender.builderReserve() == tender.builderComplement(),
                        tender.buildersOut() + " still out, " + tender.builderReserve() + " in the bay"));
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
                verdicts.add(Verdict.of("cancelling-returns-the-charge",
                        t.order.phase() == ConstructionOrder.Phase.CANCELLED && tender.stock() == Tender.DEFAULT_STOCK
                                && tender.orders().isEmpty(),
                        "order " + t.order.phase() + ", " + tender.stock() + " of " + Tender.DEFAULT_STOCK + " charges"));
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
        metrics.put("charges", tender.stock());
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
        private float lastFraction;
        private boolean turret;
        private int workTicks;

        private Trace(SceneWorld world, Tender tender, ConstructionOrder order) {
            this.world = world;
            this.tender = tender;
            this.order = order;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            ConstructionOrder.Phase phase = order.phase();
            if (phase == ConstructionOrder.Phase.WORKING) {
                if (workStart < 0) workStart = tick;
                workTicks++;
                if (order.contested()) {
                    contestedTicks++;
                    lastContested = tick;
                    if (order.fraction() > lastFraction + 1e-6f) workedWhileContested++;
                }
                lastFraction = order.fraction();
            }
            if (phase == ConstructionOrder.Phase.COMPLETE && completeTick < 0) completeTick = tick;
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
