package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.control.DirectControlAbility;
import com.dillon.starsectormarines.battle.control.DirectControlSession;
import com.dillon.starsectormarines.battle.control.ManualIntent;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
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
 * A drone tender flown by the player through the one direct-control session: it flies east under
 * the keys, is refused a site on ground nobody has seen, has two laid with the left button, works
 * the first from where it was flown (not from a standoff the ship picks for itself), waits for
 * the pilot to close on the second, recalls and redeploys its defence drones with the right
 * button, cancels with X, lays a third and is handed back in the middle of it.
 *
 * <p>It asks what a pilot's hands on a tender must keep true. A build starts only once the
 * pilot has flown into reach, and nothing builds itself. The tender stays where it is flown
 * rather than being pulled to the site. At handback no pilot order survives: the unfinished site
 * is withdrawn, the builders go home, and the ship flies back to its station.
 */
public final class TenderPilotScene implements BehaviorScene {

    private static final int WIDTH = 140;
    private static final int HEIGHT = 80;
    private static final int TICKS = 2600;
    private static final float START_X = 14f;
    private static final float START_Y = 40f;
    private static final float STOP_X = 46f;
    private static final float[] SITE_A = {60.5f, 40.5f};
    private static final float[] SITE_B = {60.5f, 54.5f};
    private static final float[] SITE_C = {52.5f, 34.5f};
    private static final float[] DARK_SITE = {110.5f, 40.5f};

    // The pilot's script, in ticks.
    private static final int DARK_AT = 20;
    private static final int LAY_A_AT = 60;
    private static final int LAY_B_AT = 80;
    private static final int RECALL_AT = 1000;
    private static final int DEPLOY_AT = 1060;
    private static final int CANCEL_AT = 1300;
    private static final int LAY_C_AT = 1350;
    private static final int RELEASE_AT = 1450;

    @Override public String id() {
        return "tender-pilot";
    }

    @Override public String label() {
        return "Tender pilot: flown with the keys, sites laid with the left button, drones with the right, handed back mid-build";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("control", "piloted")) reports.add(playOne(loop, frames));
        return reports;
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        boolean piloted = loop.equals("piloted");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        // Somebody on each side must stay alive for the clock to keep running.
        builder.unit("far-enemy", Faction.DEFENDER, UnitType.MARINE, 135, 75);
        builder.unit("far-friend", Faction.MARINE, UnitType.MARINE, 3, 3);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261012L);
        sim.directControl().layableGround((x, y) -> x < 100);
        Tender tender = Tender.fit(Faction.MARINE, START_X, START_Y, -90f);
        tender.ship().stationTo(START_X, START_Y);
        sim.tenders().add(sim, tender);
        Trace trace = new Trace(tender, piloted);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 20, tick -> loop + " / " + tender.statusLine() + " / tick " + tick)
                .run(TICKS);
        return report(loop, tender, trace);
    }

    private SceneReport report(String loop, Tender tender, Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        if (loop.equals("control")) {
            verdicts.add(Verdict.of("nothing-builds-itself", t.peakTurrets == 0 && t.peakSites == 0,
                    t.peakTurrets + " turrets, " + t.peakSites + " sites with nobody at the helm"));
            verdicts.add(Verdict.of("stays-on-station",
                    Math.hypot(tender.ship().x - START_X, tender.ship().y - START_Y) < 1.5f,
                    "ended at (" + tender.ship().x + ", " + tender.ship().y + ")"));
        } else {
            verdicts.add(Verdict.of("enters", t.entered, "the session took the helm at tick 0"));
            verdicts.add(Verdict.of("unseen-ground-is-refused", t.darkRefused && t.sitesAfterDark == 0,
                    "a site on ground the fog hides: " + (t.darkRefused ? "refused" : "laid")));
            verdicts.add(Verdict.of("left-button-lays-sites", t.sitesLaid >= 2,
                    t.sitesLaid + " sites on the line after the two clicks"));
            verdicts.add(Verdict.of("builders-wait-for-the-pilot",
                    t.firstBuilderOut >= 0 && t.xWhenFirstBuilderOut >= SITE_A[0] - TenderSystem.PILOT_REACH_CELLS - 0.6f,
                    "first builder out at tick " + t.firstBuilderOut + " with the ship at x=" + t.xWhenFirstBuilderOut
                            + " (reach starts at x=" + (SITE_A[0] - TenderSystem.PILOT_REACH_CELLS) + ")"));
            verdicts.add(Verdict.of("tender-stays-where-it-is-flown",
                    t.maxDriftWhileBuilding < 1.5f && t.firstTurretTick >= 0,
                    String.format(Locale.ROOT, "drifted %.2f cells from where the pilot stopped while building",
                            t.maxDriftWhileBuilding)));
            verdicts.add(Verdict.of("first-site-completes", t.firstTurretTick >= 0,
                    "turret stood at tick " + t.firstTurretTick));
            verdicts.add(Verdict.of("a-site-out-of-reach-waits-for-the-pilot",
                    t.siteBWaitedInTransit && !t.siteBWorkedBeforeCancel,
                    "second site sat in transit with no builder out until cancelled: " + t.siteBWaitedInTransit));
            verdicts.add(Verdict.of("right-button-recalls-and-redeploys",
                    t.defenceAfterRecall == 0 && t.defenceAfterDeploy == tender.defenceComplement(),
                    t.defenceAfterRecall + " out after the first press, " + t.defenceAfterDeploy + " after the second"));
            verdicts.add(Verdict.of("x-withdraws-the-unfinished-site", t.sitesAfterCancel == 0,
                    t.sitesAfterCancel + " live sites after X"));
            verdicts.add(Verdict.of("handback-withdraws-the-site-in-hand",
                    t.exited && t.sitesAfterRelease == 0 && !tender.piloted(),
                    "released at tick " + RELEASE_AT + " with a site half built; live sites after: " + t.sitesAfterRelease));
            verdicts.add(Verdict.of("unfinished-site-never-became-a-turret", t.peakTurrets == 1,
                    t.peakTurrets + " turrets at the end (the first site's)"));
            verdicts.add(Verdict.of("builders-go-home", tender.buildersOut() == 0
                            && tender.builderReserve() == tender.builderComplement(),
                    tender.buildersOut() + " still out, " + tender.builderReserve() + " in the bay"));
            verdicts.add(Verdict.of("handback-returns-to-station",
                    Math.hypot(tender.ship().x - START_X, tender.ship().y - START_Y) < 1.5f,
                    "ended at (" + tender.ship().x + ", " + tender.ship().y + ") of station ("
                            + START_X + ", " + START_Y + ")"));
            verdicts.add(Verdict.of("drones-are-as-the-pilot-found-them", tender.defenceOut(),
                    "defence drones wanted out: " + tender.defenceOut()));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("firstTurretTick", t.firstTurretTick);
        metrics.put("firstBuilderOutTick", t.firstBuilderOut);
        metrics.put("peakTurrets", t.peakTurrets);
        metrics.put("sitesLaid", t.sitesLaid);
        metrics.put("dronesLost", tender.dronesLost());
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    /** The pilot's hands and the instruments, in one observer: it acts, then looks, before each tick. */
    private static final class Trace implements TickObserver {
        private final Tender tender;
        private final boolean piloted;
        boolean entered;
        boolean exited;
        boolean darkRefused;
        int sitesAfterDark = -1;
        int sitesLaid;
        int sitesAfterCancel = -1;
        int sitesAfterRelease = -1;
        int defenceAfterRecall = -1;
        int defenceAfterDeploy = -1;
        int firstBuilderOut = -1;
        float xWhenFirstBuilderOut;
        int firstTurretTick = -1;
        int peakTurrets;
        int peakSites;
        float stoppedX = Float.NaN;
        float stoppedY = Float.NaN;
        float maxDriftWhileBuilding;
        boolean siteBWaitedInTransit;
        boolean siteBWorkedBeforeCancel;
        private ConstructionOrder siteB;

        Trace(Tender tender, boolean piloted) {
            this.tender = tender;
            this.piloted = piloted;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            peakSites = Math.max(peakSites, TenderPilot.liveSites(tender));
            peakTurrets = Math.max(peakTurrets, turrets(sim));
            if (firstTurretTick < 0 && turrets(sim) > 0) firstTurretTick = tick;
            if (firstBuilderOut < 0 && tender.buildersOut() > 0) {
                firstBuilderOut = tick;
                xWhenFirstBuilderOut = tender.ship().x;
            }
            if (!piloted) return;
            DirectControlSession control = sim.directControl();
            if (tick == 0) entered = control.enterWarship(tender.ship());
            if (tick == DARK_AT + 2) {
                darkRefused = tender.orders().isEmpty()
                        && control.tenderPad().notice() == TenderPilot.Notice.UNSEEN;
                sitesAfterDark = TenderPilot.liveSites(tender);
            }
            if (tick == LAY_B_AT + 2) {
                sitesLaid = TenderPilot.liveSites(tender);
                for (ConstructionOrder o : tender.orders()) if (o.siteY() == SITE_B[1]) siteB = o;
            }
            boolean arrived = tender.ship().x >= STOP_X;
            if (arrived && Float.isNaN(stoppedX) && tender.ship().speed() < 0.2f) {
                stoppedX = tender.ship().x;
                stoppedY = tender.ship().y;
            }
            ConstructionOrder current = tender.current();
            if (current != null && current.phase() == ConstructionOrder.Phase.WORKING && !Float.isNaN(stoppedX)) {
                maxDriftWhileBuilding = Math.max(maxDriftWhileBuilding,
                        (float) Math.hypot(tender.ship().x - stoppedX, tender.ship().y - stoppedY));
            }
            if (siteB != null && tick < CANCEL_AT) {
                if (siteB.phase() == ConstructionOrder.Phase.WORKING) siteBWorkedBeforeCancel = true;
                if (tick >= 900 && siteB.phase() == ConstructionOrder.Phase.TRANSIT && firstTurretTick >= 0
                        && tender.current() == siteB) {
                    siteBWaitedInTransit = true;
                }
            }
            if (tick == RECALL_AT + 40) defenceAfterRecall = tender.defendersOut();
            if (tick == DEPLOY_AT + 240) defenceAfterDeploy = tender.defendersOut();
            if (tick == CANCEL_AT + 2) sitesAfterCancel = TenderPilot.liveSites(tender);
            if (tick == RELEASE_AT) {
                control.exit();
                exited = !control.active();
                sitesAfterRelease = TenderPilot.liveSites(tender);
            }
            if (!control.active()) return;
            float moveX = tender.ship().x < STOP_X ? 1f : 0f;
            boolean left = false;
            boolean right = false;
            float aimX = 40f;
            float aimY = 40f;
            if (tick == DARK_AT) {
                left = true;
                aimX = DARK_SITE[0];
                aimY = DARK_SITE[1];
            } else if (tick == LAY_A_AT) {
                left = true;
                aimX = SITE_A[0];
                aimY = SITE_A[1];
            } else if (tick == LAY_B_AT) {
                left = true;
                aimX = SITE_B[0];
                aimY = SITE_B[1];
            } else if (tick == LAY_C_AT) {
                left = true;
                aimX = SITE_C[0];
                aimY = SITE_C[1];
            }
            if (tick == RECALL_AT || tick == DEPLOY_AT) right = true;
            if (tick == CANCEL_AT) control.requestAbility(DirectControlAbility.CANCEL_BUILDS, new PointFireAim(aimX, aimY));
            control.submit(new ManualIntent(moveX, 0f, aimX, aimY, left, false, right));
        }

        private static int turrets(BattleSimulation sim) {
            UnitRosterService roster = sim.getRoster();
            long[] dense = roster.denseArray();
            int n = 0;
            for (int i = 0, count = roster.liveCount(); i < count; i++) {
                long u = dense[i];
                if (roster.identity().type(u).isTurret() && roster.identity().faction(u) == Faction.MARINE) n++;
            }
            return n;
        }
    }
}
