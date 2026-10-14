package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.airsupport.AirPoolService.Event;
import com.dillon.starsectormarines.battle.airsupport.AirPoolService.EventType;
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
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.InstalledWeaponSlots;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Air strikes called by comms operators from a side's regenerating air pool.
 *
 * <p>Six loops of one small battlefield, the same observers on the west and the same garrison on the east, differing
 * in what stands round the garrison and who asks:
 * <ul>
 *   <li>{@code light-target}: nothing but infantry in the open. The side's own director designates the concentration
 *       and a bomber crosses the map, bombs it and goes home.</li>
 *   <li>{@code fort-aa-forced}: the garrison ringed by emplacements, two of them dedicated anti-air, and a strike
 *       forced onto it. The bomber meets the AA on the way in: it is driven off or shot down, and the garrison
 *       takes less than the open one did.</li>
 *   <li>{@code fort-declined}: the same fort, no forcing. The director's own estimate, built from the guns its units
 *       have sighted, says the pass would cost more than an airframe, and it does not send one.</li>
 *   <li>{@code operator-killed}: the open garrison, but the operator is killed while the call is going out. No
 *       aircraft leaves and the pool keeps its airframe.</li>
 *   <li>{@code company-plan}: a lone nest, and a company's plan step for nest suppression asks the director for a
 *       strike through its observer; the nest is bombed out before anyone on the ground can reach it.</li>
 *   <li>{@code pool-drain}: strikes forced repeatedly into the fort. The pool visibly drains as airframes are lost
 *       and refills as the side replaces them.</li>
 * </ul>
 *
 * <p>Judged from the pool's own ledger and the aircraft's state, not from the garrison alone: a strike that killed
 * the garrison by some other means would answer nothing about the pool. Every figure is UNTUNED.
 */
public final class AirStrikeScene implements BehaviorScene {

    static final int WIDTH = 100;
    static final int HEIGHT = 50;
    private static final int TICKS = 2700;
    private static final int DRAIN_TICKS = 5400;
    private static final String CARBINE = "weapon.smg";
    private static final int GARRISON = 8;
    private static final int GARRISON_X = 50;
    private static final int GARRISON_Y = 25;
    private static final int OBSERVER_X = 23;
    private static final int FORCE_TICK = 150;

    /** Emplacements ringing the fort garrison: x, y, structure. Two are dedicated anti-air. */
    private static final Object[][] FORT = {
            {47, 22, "structure.turret-heavy-mg"},
            {53, 28, "structure.turret-dual-flak"},
            {52, 23, TurretCatalogRegistry.ANTI_AIR_STRUCTURE_ID},
            {52, 27, TurretCatalogRegistry.ANTI_AIR_STRUCTURE_ID},
    };

    @Override public String id() { return "air-strike"; }

    @Override public String label() {
        return "Air strike: a comms operator directs a bomber from the side's regenerating pool onto ground it can see; "
                + "anti-air over a strong defence drives it off or shoots it down and visibly drains the pool, "
                + "a light target is bombed and the airframe returns, and a dead operator calls no one";
    }

    @Override public int frameDelayMillis() { return 400; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledWeaponSlots.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        try {
            Result light = playOne("light-target", frames, null);
            reports.add(light.report);
            for (String loop : List.of("fort-aa-forced", "fort-declined", "operator-killed", "company-plan", "pool-drain")) {
                reports.add(playOne(loop, frames, light).report);
            }
        } finally {
            TurretSlotResolver.useHullSpecs(null);
        }
        return reports;
    }

    private record Result(SceneReport report, int garrisonDead) {}

    private static boolean fort(String loop) {
        return loop.startsWith("fort") || loop.equals("pool-drain");
    }

    private Result playOne(String loop, FrameSink frames, Result light) {
        boolean plan = loop.equals("company-plan");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        builder.squad("observers").faction(Faction.MARINE).size(plan ? 6 : 3).at(OBSERVER_X, GARRISON_Y).primary(CARBINE)
                .special("special.comms-radio", 1).stationary()
                .assigned(id -> ObjectiveAssignment.defendTrack(id, OBSERVER_X, GARRISON_Y)).done();
        if (!plan) {
            builder.squad("garrison").faction(Faction.DEFENDER).size(GARRISON).at(GARRISON_X, GARRISON_Y)
                    .primary(CARBINE).stationary()
                    .assigned(id -> ObjectiveAssignment.defendTrack(id, GARRISON_X, GARRISON_Y)).done();
        } else {
            // A defender squad far off, so the simulation has both sides and keeps ticking; it is not the target.
            builder.squad("garrison").faction(Faction.DEFENDER).size(2).at(WIDTH - 4, 4)
                    .primary(CARBINE).stationary()
                    .assigned(id -> ObjectiveAssignment.defendTrack(id, WIDTH - 4, 4)).done();
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261015L);

        List<Long> guns = new ArrayList<>();
        if (fort(loop)) {
            for (Object[] g : FORT) guns.add(spawnGun(sim, (int) g[0], (int) g[1], (String) g[2]));
        } else if (plan) {
            guns.add(spawnGun(sim, GARRISON_X, GARRISON_Y, "structure.turret-heavy-mg"));
        }

        boolean forced = loop.equals("fort-aa-forced");
        boolean drain = loop.equals("pool-drain");
        AirPoolStock bombers = drain ? new AirPoolStock(3, 3, 20f) : new AirPoolStock(2, 3, 40f);
        AirPoolAllowance allowance = AirPoolAllowance.of(bombers, null, /*aiDirected*/ !plan && !drain);
        if (forced) allowance = allowance.strikeAtTick(FORCE_TICK);
        sim.setAirPool(new AirPoolService(AirPoolDeclaration.NONE.with(Faction.MARINE, allowance)));

        Trace trace = new Trace(world, loop, guns, plan, drain);
        int ticks = drain ? DRAIN_TICKS : TICKS;
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 60, tick -> trace.caption(loop, tick))
                .run(ticks);
        return new Result(report(loop, trace, sim, light, ticks), trace.garrisonDead);
    }

    private static long spawnGun(BattleSimulation sim, int x, int y, String structure) {
        sim.getGrid().setWalkable(x, y, false);
        return sim.spawn(MapTurret.create("gun-" + x + "-" + y, Faction.DEFENDER, structure, x, y));
    }

    private SceneReport report(String loop, Trace t, BattleSimulation sim, Result light, int ticks) {
        List<Verdict> verdicts = new ArrayList<>();
        AirPoolService pool = sim.airPool();
        int launched = t.count(EventType.LAUNCHED);
        int returned = t.count(EventType.RETURNED);
        int lost = t.count(EventType.LOST);
        int aborted = t.count(EventType.ABORTED);
        int dropped = t.count(EventType.CALL_DROPPED);
        int bombersNow = pool.available(Faction.MARINE, AirPoolKind.BOMBER);
        switch (loop) {
            case "light-target" -> {
                verdicts.add(Verdict.of("director-calls-the-strike-through-an-operator",
                        t.firstCalledTick >= 0 && launched >= 1,
                        "called at tick " + t.firstCalledTick + ", launched " + launched));
                verdicts.add(Verdict.of("bombs-the-garrison", t.garrisonDead >= GARRISON - 2,
                        "garrison dead " + t.garrisonDead + " of " + GARRISON + ", first at tick " + t.firstDeathTick));
                verdicts.add(Verdict.of("the-bomber-comes-home-to-the-pool",
                        returned >= 1 && lost == 0 && aborted == 0,
                        "returned " + returned + ", lost " + lost + ", driven off " + aborted
                                + ", bombers in stock " + bombersNow));
                verdicts.add(Verdict.of("enters-from-off-the-map", t.entryOffMap,
                        String.format(Locale.ROOT, "first seen at (%.1f, %.1f), map %dx%d", t.entryX, t.entryY, WIDTH, HEIGHT)));
            }
            case "fort-aa-forced" -> {
                verdicts.add(Verdict.of("the-forced-strike-flies", launched >= 1,
                        "launched " + launched + " (forced at tick " + FORCE_TICK + ")"));
                verdicts.add(Verdict.of("anti-air-makes-it-pay", lost + aborted >= 1,
                        "lost " + lost + ", driven off " + aborted + ", returned " + returned
                                + ", hull low point " + String.format(Locale.ROOT, "%.0f%%", t.minHullShare * 100f)));
                verdicts.add(Verdict.of("the-aircraft-is-hurt-on-the-way",
                        t.minHullShare < 0.6f,
                        String.format(Locale.ROOT, "hull low point %.0f%%; garrison dead %d against %d in the open",
                                t.minHullShare * 100f, t.garrisonDead, light == null ? -1 : light.garrisonDead())));
            }
            case "fort-declined" -> {
                float risk = AirStrikeCapability.aaRisk(sim, Faction.MARINE, GARRISON_X, GARRISON_Y);
                verdicts.add(Verdict.of("believed-anti-air-is-too-strong",
                        risk > AirStrikeDirector.RISK_CEILING && sim.airPool().director(Faction.MARINE).knownGuns().size() >= 3,
                        String.format(Locale.ROOT, "believed loss share %.2f over a ceiling of %.2f, %d guns sighted",
                                risk, AirStrikeDirector.RISK_CEILING, sim.airPool().director(Faction.MARINE).knownGuns().size())));
                verdicts.add(Verdict.of("no-strike-is-sent", t.firstCalledTick < 0 && launched == 0 && bombersNow >= 2,
                        "called " + (t.firstCalledTick < 0 ? "never" : "at tick " + t.firstCalledTick)
                                + ", bombers in stock " + bombersNow + " (none spent)"));
                verdicts.add(Verdict.of("the-garrison-is-left-to-the-ground-force", t.garrisonDead == 0,
                        "garrison dead " + t.garrisonDead));
            }
            case "operator-killed" -> {
                verdicts.add(Verdict.of("the-call-was-started", t.firstCalledTick >= 0,
                        "called at tick " + t.firstCalledTick + ", operator killed at tick " + t.operatorKilledTick));
                verdicts.add(Verdict.of("a-dead-operator-calls-no-one", dropped >= 1 && launched == 0 && t.airborneSeen == 0,
                        "calls dropped " + dropped + ", launched " + launched + ", aircraft ever seen " + t.airborneSeen));
                verdicts.add(Verdict.of("the-airframe-stays-in-the-pool", bombersNow >= 2,
                        "bombers in stock " + bombersNow + " of 2, garrison dead " + t.garrisonDead));
            }
            case "company-plan" -> {
                verdicts.add(Verdict.of("the-plan-asked-through-an-observer",
                        t.planRequestTick >= 0 && t.firstCalledTick >= t.planRequestTick,
                        "plan step at tick " + t.planRequestTick + ", outcome " + t.planOutcome));
                verdicts.add(Verdict.of("the-nest-is-bombed-out", t.gunsDead >= 1,
                        "nests dead " + t.gunsDead + " of " + t.guns.size() + ", first at tick " + t.firstGunDeathTick
                                + ", the company never left its post: " + t.companyAlive + " alive"));
                verdicts.add(Verdict.of("the-bomber-returns", returned >= 1 && lost == 0,
                        "returned " + returned + ", lost " + lost + ", driven off " + aborted));
            }
            case "pool-drain" -> {
                verdicts.add(Verdict.of("anti-air-drains-the-pool", lost >= 1 && t.minAvailable < 3,
                        "lost " + lost + ", driven off " + aborted + ", launched " + launched
                                + ", stock fell to " + t.minAvailable + " of 3"));
                verdicts.add(Verdict.of("the-side-replaces-losses-over-time",
                        t.regenAfterLoss >= 1,
                        "stock rose by " + t.regenAfterLoss + " after the first loss (replacement every 20 s), final stock "
                                + bombersNow));
                verdicts.add(Verdict.of("never-above-the-establishment", t.maxHeld <= 3,
                        "most airframes held or flying at once " + t.maxHeld + " of 3"));
            }
            default -> throw new IllegalStateException(loop);
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("launched", launched);
        metrics.put("returned", returned);
        metrics.put("lost", lost);
        metrics.put("aborted", aborted);
        metrics.put("callsDropped", dropped);
        metrics.put("garrisonDead", t.garrisonDead);
        metrics.put("gunsDead", t.gunsDead);
        metrics.put("minHullPercent", Math.round(t.minHullShare * 100f));
        metrics.put("minAvailable", t.minAvailable);
        metrics.put("firstCalledTick", t.firstCalledTick);
        metrics.put("bombersAtEnd", bombersNow);
        metrics.put("secondsInsideTheGunsBubble", Math.round(t.bubbleTicks / 3f) / 10f);
        metrics.put("topSpeedCellsPerSecond", Math.round(t.topSpeed * 10f) / 10f);
        return new SceneReport(id(), loop, ticks, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        private final String loop;
        final List<Long> guns;
        private final boolean plan;
        private final boolean drain;
        int firstCalledTick = -1;
        int firstDeathTick = -1;
        int firstGunDeathTick = -1;
        int garrisonDead;
        int gunsDead;
        int companyAlive;
        int airborneSeen;
        boolean entryOffMap;
        float entryX;
        float entryY;
        float minHullShare = 1f;
        int operatorKilledTick = -1;
        int planRequestTick = -1;
        String planOutcome = "none";
        int bubbleTicks;
        float topSpeed;
        int minAvailable = Integer.MAX_VALUE;
        int maxHeld;
        int regenAfterLoss;
        private boolean lossSeen;
        private int seenEvents;
        private final List<Event> events = new ArrayList<>();
        private boolean entrySeen;
        private ShuttleMission mission;
        private long craft;

        private Trace(SceneWorld world, String loop, List<Long> guns, boolean plan, boolean drain) {
            this.world = world;
            this.loop = loop;
            this.guns = guns;
            this.plan = plan;
            this.drain = drain;
        }

        int count(EventType type) {
            int n = 0;
            for (Event e : events) {
                if (e.type() == type) n++;
            }
            return n;
        }

        String caption(String loop, int tick) {
            AirPoolService pool = world.sim().airPool();
            String state = mission == null ? "" : " / " + mission.state.name().toLowerCase(Locale.ROOT)
                    + (mission.aborted ? " (driven off)" : "");
            return loop + " / tick " + tick + " / bombers " + pool.available(Faction.MARINE, AirPoolKind.BOMBER)
                    + " in stock, " + pool.outFlying(Faction.MARINE, AirPoolKind.BOMBER) + " out, "
                    + pool.lost(Faction.MARINE, AirPoolKind.BOMBER) + " lost" + state;
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            AirPoolService pool = sim.airPool();
            // The ledger, line by line.
            List<Event> all = pool.events();
            while (seenEvents < all.size()) {
                Event e = all.get(seenEvents++);
                events.add(e);
                if (e.type() == EventType.CALLED && firstCalledTick < 0) firstCalledTick = tick;
                if (e.type() == EventType.LOST) lossSeen = true;
                if (e.type() == EventType.REPLACED && lossSeen) regenAfterLoss++;
            }
            int available = pool.available(Faction.MARINE, AirPoolKind.BOMBER);
            minAvailable = Math.min(minAvailable, available);
            int held = available + pool.outFlying(Faction.MARINE, AirPoolKind.BOMBER);
            maxHeld = Math.max(maxHeld, held);

            // Garrison and guns.
            int dead = 0;
            for (long u : world.members("garrison")) {
                if (!sim.getRoster().isAliveById(u)) dead++;
            }
            if (!plan) garrisonDead = dead;
            if (garrisonDead > 0 && firstDeathTick < 0) firstDeathTick = tick;
            int gd = 0;
            for (long g : guns) {
                if (!sim.getRoster().isAliveById(g)) gd++;
            }
            gunsDead = gd;
            if (gd > 0 && firstGunDeathTick < 0) firstGunDeathTick = tick;
            int alive = 0;
            for (long u : world.members("observers")) {
                if (sim.getRoster().isAliveById(u)) alive++;
            }
            companyAlive = alive;

            // The aircraft.
            long[] air = sim.getAirEntityIds();
            if (air.length > 0) {
                airborneSeen = Math.max(airborneSeen, air.length);
                if (mission == null) {
                    craft = air[0];
                    mission = sim.world().mission(craft);
                }
                if (!entrySeen) {
                    entrySeen = true;
                    entryX = sim.world().x(craft);
                    entryY = sim.world().y(craft);
                    entryOffMap = entryX < 0 || entryY < 0 || entryX > WIDTH || entryY > HEIGHT;
                }
                for (long id : air) {
                    ShuttleMission m = sim.world().mission(id);
                    if (m != null && m.poolSortie && sim.world().airframe(id) != null && !m.destroyed) {
                        minHullShare = Math.min(minHullShare,
                                sim.world().hp(id) / sim.world().airframe(id).maxHp());
                    }
                }
            }
            boolean flying = false;
            for (long id : air) flying |= id == craft;
            if (mission != null && flying) {
                var body = sim.world().kinematics(craft);
                topSpeed = Math.max(topSpeed, body.speed());
                for (long g : guns) {
                    if (sim.getRoster().isAliveById(g)
                            && Math.hypot(sim.world().x(g) - body.x, sim.world().y(g) - body.y) < AaRisk.THREAT_RADIUS_CELLS) {
                        bubbleTicks++;
                        break;
                    }
                }
            }
            if (mission != null && mission.state == ShuttleState.GONE) mission = null;

            // Scripted: the operator killed while the call goes out.
            if (loop.equals("operator-killed") && firstCalledTick >= 0 && operatorKilledTick < 0
                    && tick > firstCalledTick + 20) {
                operatorKilledTick = tick;
                for (long u : world.members("observers")) {
                    if (CommsKit.carried(sim.world(), u)) sim.applyDamage(u, 10_000f, 100f);
                }
            }
            // Scripted: a company's plan step for nest suppression asks for a strike through its observer.
            if (plan && planRequestTick < 0 && tick >= 90) {
                planRequestTick = tick;
                var outcome = pool.director(Faction.MARINE).strike(sim, pool, GARRISON_X + 0.5f, GARRISON_Y + 0.5f, false);
                planOutcome = outcome.name();
            }
            // Scripted: repeated strikes forced into the fort, as a commander who would not be told.
            if (drain && tick >= FORCE_TICK && tick % 90 == 0) {
                pool.director(Faction.MARINE).strike(sim, pool, GARRISON_X + 0.5f, GARRISON_Y + 0.5f, true);
            }
        }
    }
}
