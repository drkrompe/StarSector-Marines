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
import com.dillon.starsectormarines.battle.warship.MissionStance;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An unpiloted tender left to build for itself, by the stance its hull was given.
 *
 * <p>{@code hold-control} is the same world with the default HOLD stance: a squad sent to take
 * ground and a tender that is never directed, so nothing is laid. {@code attack} gives the tender
 * ATTACK: with a squad advancing on a picket it lays quick turrets on cleared ground behind the
 * lead and never inside the threat radius of the enemy. {@code defend} gives it DEFEND over a held
 * point with an enemy still far off: it raises a heavy turret on the side the enemy will come
 * from, before the enemy gets there.
 */
public final class TenderStanceScene implements BehaviorScene {

    private static final int WIDTH = 150;
    private static final int HEIGHT = 80;
    private static final int TICKS = 3600;
    private static final String CARBINE = "weapon.smg";
    private static final float ATTACK_PICKET_X = 112f;
    private static final float HELD_X = 40f;
    private static final float HELD_Y = 40f;

    @Override public String id() {
        return "tender-stance";
    }

    @Override public String label() {
        return "Drone tender on a stance: held to HOLD it builds nothing, on ATTACK it raises turrets behind the advance "
                + "on cleared ground, on DEFEND it raises a heavy turret on the enemy's approach before they arrive";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("hold-control", "attack", "defend")) {
            reports.add(report(loop, playOne(loop, frames)));
        }
        return reports;
    }

    private record Run(Trace trace, Tender tender) {}

    private Run playOne(String loop, FrameSink frames) throws Exception {
        boolean defend = loop.equals("defend");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        if (defend) {
            builder.squad("friends").faction(Faction.MARINE).size(4).at((int) HELD_X, (int) HELD_Y).primary(CARBINE)
                    .stationary().assigned(id -> ObjectiveAssignment.defendTrack(id, (int) HELD_X, (int) HELD_Y)).done();
            builder.squad("enemy").faction(Faction.DEFENDER).size(3).at(140, 40).primary(CARBINE)
                    .assigned(id -> ObjectiveAssignment.attackMove(id, (int) HELD_X, (int) HELD_Y)).done();
        } else {
            builder.squad("friends").faction(Faction.MARINE).size(4).at(30, 40).primary(CARBINE)
                    .assigned(id -> ObjectiveAssignment.attackMove(id, (int) ATTACK_PICKET_X, 40)).done();
            builder.squad("enemy").faction(Faction.DEFENDER).size(3).at((int) ATTACK_PICKET_X, 40).primary(CARBINE)
                    .stationary().assigned(id -> ObjectiveAssignment.defendTrack(id, (int) ATTACK_PICKET_X, 40)).done();
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261012L);
        Tender tender = Tender.fit(Faction.MARINE, defend ? 24f : 14f, 40f, -90f);
        if (!loop.equals("hold-control")) {
            tender.ship().stance(defend ? MissionStance.DEFEND : MissionStance.ATTACK);
        }
        sim.tenders().add(sim, tender);
        Trace trace = new Trace(world, tender);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 15, tick -> loop + " / " + tender.statusLine() + " / tick " + tick)
                .run(TICKS);
        return new Run(trace, tender);
    }

    private SceneReport report(String loop, Run run) {
        Trace t = run.trace();
        Tender tender = run.tender();
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case "hold-control" -> {
                verdicts.add(Verdict.of("a-held-tender-lays-nothing", t.laid.isEmpty(),
                        t.laid.size() + " sites laid with the squad advancing under an assault order"));
                verdicts.add(Verdict.of("a-held-tender-builds-nothing", tender.built() == 0,
                        tender.built() + " turrets built"));
            }
            case "attack" -> {
                verdicts.add(Verdict.of("builds-forward-positions", tender.built() >= 1,
                        tender.built() + " turrets built, " + t.laid.size() + " sites laid, first laid at tick " + t.firstLay));
                verdicts.add(Verdict.of("every-site-was-clear-of-the-enemy-when-laid",
                        !t.laid.isEmpty() && t.minHostileAtLay > TenderSystem.THREAT_CELLS,
                        String.format(Locale.ROOT, "closest hostile to any site at the moment it was laid: %.1f cells "
                                + "(threat radius %.0f)", t.minHostileAtLay, TenderSystem.THREAT_CELLS)));
                verdicts.add(Verdict.of("sites-lie-no-further-than-a-step-past-the-lead",
                        !t.laid.isEmpty() && t.maxOvershoot <= TenderDirector.AHEAD_CELLS + 1f,
                        String.format(Locale.ROOT, "furthest any site lay ahead of the lead at its laying: %.1f cells "
                                + "(limit %.0f)", t.maxOvershoot, TenderDirector.AHEAD_CELLS)));
                verdicts.add(Verdict.of("the-first-site-was-laid-while-the-picket-stood",
                        t.firstLayLive,
                        String.format(Locale.ROOT, "first site at tick %d x %.1f against a live picket at x %.0f; picket "
                                + "cleared at tick %d; %d of %d sites laid with a live picket", t.firstLay, t.firstSiteX,
                                ATTACK_PICKET_X, t.picketDeadTick, t.laidWithLiveEnemy, t.laid.size())));
                verdicts.add(Verdict.of("the-quickest-option-is-chosen", !t.laid.isEmpty() && t.allVulcan,
                        "options laid: " + t.optionNames));
                verdicts.add(Verdict.of("no-site-was-stood-down-for-contest", t.contestedTicks == 0,
                        t.contestedTicks + " contested working ticks"));
            }
            default -> {
                verdicts.add(Verdict.of("a-heavy-turret-is-built", tender.built() >= 1 && t.allMortar && !t.laid.isEmpty(),
                        tender.built() + " built, options laid: " + t.optionNames));
                float approach = t.firstSiteX - HELD_X;
                verdicts.add(Verdict.of("it-stands-on-the-enemys-approach-side", !t.laid.isEmpty() && approach > 2f,
                        String.format(Locale.ROOT, "first site %.1f cells %s of the held point (enemy comes from the east)",
                                Math.abs(approach), approach > 0 ? "east" : "west")));
                verdicts.add(Verdict.of("it-stands-before-the-enemy-arrives",
                        t.firstComplete >= 0 && t.enemyDistAtComplete > 20f
                                && (t.enemyArrival < 0 || t.firstComplete < t.enemyArrival),
                        String.format(Locale.ROOT, "first turret complete at tick %d with the nearest enemy %.1f cells from the "
                                + "held point; enemy within 20 cells at tick %d; enemies alive at end %d, closest approach %.1f",
                                t.firstComplete, t.enemyDistAtComplete, t.enemyArrival, t.enemiesAlive, t.minEnemyDist)));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("sitesLaid", t.laid.size());
        metrics.put("built", tender.built());
        metrics.put("firstLayTick", t.firstLay);
        metrics.put("firstCompleteTick", t.firstComplete);
        metrics.put("minHostileAtLay", t.laid.isEmpty() ? -1f : t.minHostileAtLay);
        metrics.put("contestedTicks", t.contestedTicks);
        metrics.put("enemyArrivalTick", t.enemyArrival);
        metrics.put("picketClearedTick", t.picketDeadTick);
        metrics.put("minEnemyDistToHeld", t.minEnemyDist);
        metrics.put("enemiesAliveAtEnd", t.enemiesAlive);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        private final Tender tender;
        final Map<ConstructionOrder, Integer> laid = new IdentityHashMap<>();
        final List<String> optionNames = new ArrayList<>();
        int firstLay = -1;
        int firstComplete = -1;
        int enemyArrival = -1;
        int contestedTicks;
        float minHostileAtLay = Float.MAX_VALUE;
        float maxSiteX = -Float.MAX_VALUE;
        float firstSiteX;
        float leadXAtLay;
        boolean allVulcan = true;
        boolean allMortar = true;
        float maxOvershoot = -Float.MAX_VALUE;
        int picketDeadTick = -1;
        int laidWithLiveEnemy;
        boolean firstLayLive;
        float enemyDistAtComplete = -1f;
        float minEnemyDist = Float.MAX_VALUE;
        int enemiesAlive;

        private Trace(SceneWorld world, Tender tender) {
            this.world = world;
            this.tender = tender;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            for (ConstructionOrder o : tender.orders()) {
                if (laid.containsKey(o)) continue;
                laid.put(o, tick);
                if (firstLay < 0) {
                    firstLay = tick;
                    firstSiteX = o.siteX();
                }
                optionNames.add(o.option().id());
                allVulcan &= o.option() == BuildOption.VULCAN_TURRET;
                allMortar &= o.option() == BuildOption.MORTAR_TURRET;
                maxSiteX = Math.max(maxSiteX, o.siteX());
                float lead = -Float.MAX_VALUE;
                for (long u : world.members("friends")) {
                    if (sim.getRoster().isAliveById(u)) lead = Math.max(lead, sim.world().x(u));
                }
                leadXAtLay = Math.max(leadXAtLay, lead);
                maxOvershoot = Math.max(maxOvershoot, o.siteX() - lead);
                boolean live = false;
                for (long u : world.members("enemy")) {
                    if (!sim.getRoster().isAliveById(u)) continue;
                    live = true;
                    minHostileAtLay = Math.min(minHostileAtLay, (float) Math.hypot(
                            sim.world().x(u) - o.siteX(), sim.world().y(u) - o.siteY()));
                }
                if (live) laidWithLiveEnemy++;
                if (firstLay == tick) firstLayLive = live;
            }
            int alive = 0;
            float nearest = Float.MAX_VALUE;
            for (long u : world.members("enemy")) {
                if (!sim.getRoster().isAliveById(u)) continue;
                alive++;
                nearest = Math.min(nearest, (float) Math.hypot(sim.world().x(u) - HELD_X, sim.world().y(u) - HELD_Y));
            }
            enemiesAlive = alive;
            minEnemyDist = Math.min(minEnemyDist, nearest);
            if (alive == 0 && picketDeadTick < 0) picketDeadTick = tick;
            for (ConstructionOrder o : laid.keySet()) {
                if (o.phase() == ConstructionOrder.Phase.WORKING && o.contested()) contestedTicks++;
                if (o.phase() == ConstructionOrder.Phase.COMPLETE && firstComplete < 0) {
                    firstComplete = tick;
                    enemyDistAtComplete = nearest;
                }
            }
            if (enemyArrival < 0) {
                for (long u : world.members("enemy")) {
                    if (sim.getRoster().isAliveById(u) && Math.hypot(sim.world().x(u) - HELD_X,
                            sim.world().y(u) - HELD_Y) <= 20f) {
                        enemyArrival = tick;
                        break;
                    }
                }
            }
        }
    }
}
