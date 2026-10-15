package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirIntercept;
import com.dillon.starsectormarines.battle.air.AirLoss;
import com.dillon.starsectormarines.battle.air.AirOrdnance;
import com.dillon.starsectormarines.battle.air.AirToAirGun;
import com.dillon.starsectormarines.battle.air.CoverDuty;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.airsupport.AirPoolService.Outcome;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
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
import com.dillon.starsectormarines.testsupport.InstalledHullEngines;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.InstalledWeaponSlots;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Fighters in the air pool: gun runs a comms operator calls on sighted ground, and combat air patrol over friendly ground.
 *
 * <p>Nine loops of one wide arena, a radio on the west side and a garrison or a raid from the east:
 * <ul>
 *   <li>{@code strafe-open}: infantry in the open, the side's own director with fighters in stock and no bombers. It
 *       designates the group through an operator and a fighter flies a real gun run, in from off the map.</li>
 *   <li>{@code nest-bombed}: the control. One heavy-gun nest, a bomber asked for explicitly.</li>
 *   <li>{@code nest-strafed}: the same nest, a fighter asked for explicitly. It does less to the position than the bomber.</li>
 *   <li>{@code nest-declined}: the same nest, the director's own judgement with only fighters in stock. It sends none:
 *       a cannon is the wrong tool and it waits for a bomber.</li>
 *   <li>{@code cap-none}: the control. Two enemy bombers raid a company with nothing overhead.</li>
 *   <li>{@code cap-intercept}: the same raid, a fighter on cover over the company asked for by the side's commander.</li>
 *   <li>{@code cap-under-aa}: cover whose ring passes anti-air the side has not sighted. The fighter pays for it.</li>
 *   <li>{@code cap-ground}: no aircraft about, a hostile group the company has sighted standing off its flank. The patrol
 *       works it from its station; and with the company near enough to be hit ({@code cap-ground-close}) it holds fire.</li>
 *   <li>{@code cap-vs-cap}: both sides have fighters and each puts one over its own ground. They fight.</li>
 * </ul>
 *
 * <p>Judged from the pool's own ledger, the aircraft's state and the guns' round counts, not from casualties alone: a
 * raid that failed for some other reason would answer nothing about cover. Every figure is UNTUNED.
 */
public final class FighterCoverScene implements BehaviorScene {

    static final int WIDTH = 140;
    static final int HEIGHT = 70;
    private static final int TICKS = 3000;
    private static final String CARBINE = "weapon.smg";
    private static final int MID = HEIGHT / 2;
    private static final int OBSERVER_X = 45;
    private static final int GARRISON_X = 72;
    private static final int COMPANY = 12;
    private static final int RAID_TICK = 600;
    private static final int RAID_GAP_TICKS = 450;
    private static final int COVER_TICK = 30;
    /** The seed the recorded loops play; the seed loops play a spread of others. */
    private static final long DEFAULT_SEED = 20261015L;
    /** Seeds the sweep loops play. */
    private static final int SWEEP_SEEDS = 12;
    /** Ticks the raid's start may slip by seed, so the fighter meets it at different points of its ring. */
    private static final int RAID_JITTER_TICKS = 240;
    /** Cells the raiders' aim may slip across the company by seed. */
    private static final float RAID_LATERAL_CELLS = 8f;

    @Override public String id() { return "fighter-cover"; }

    @Override public String label() {
        return "Fighters: a radio calls a gun run on sighted soft ground and the director leaves hardened works to a bomber; "
                + "a fighter on cover over friendly ground chases hostile aircraft with its nose guns, takes a gun pass at "
                + "ground its side has sighted when its own people are clear, and pays for flying through anti-air";
    }

    @Override public int frameDelayMillis() { return 400; }

    private record Result(SceneReport report, Trace trace) {}

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledHullEngines.install();
        InstalledWeaponSlots.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        try {
            Result bombed = playOne("nest-bombed", frames, null);
            Result none = playOne("cap-none", frames, null);
            Result strafe = playOne("strafe-open", frames, null);
            reports.add(strafe.report);
            reports.add(bombed.report);
            reports.add(playOne("nest-strafed", frames, bombed).report);
            reports.add(playOne("nest-declined", frames, bombed).report);
            reports.add(none.report);
            reports.add(playOne("cap-intercept", frames, none).report);
            reports.add(playOne("cap-under-aa", frames, none).report);
            Result ground = playOne("cap-ground", frames, null);
            reports.add(ground.report);
            reports.add(playOne("cap-ground-close", frames, ground).report);
            reports.add(playOne("cap-vs-cap", frames, none).report);
            reports.add(sweepIntercept(false));
            reports.add(sweepIntercept(true));
            reports.add(sweepVersus(false));
            reports.add(sweepVersus(true));
        } finally {
            TurretSlotResolver.useHullSpecs(null);
            InstalledHullEngines.uninstall();
        }
        return reports;
    }

    /**
     * {@code cap-intercept} again across {@link #SWEEP_SEEDS} seeds, each paired with the same raid and nothing overhead.
     * One reading of a chase is one geometry; this is the rate. The verdicts are the mechanics every seed must show; how
     * often a raider goes down, and whether before it drops, are metrics, because they are findings and not promises.
     */
    private SceneReport sweepIntercept(boolean headOn) {
        headOnControl = headOn;
        int launched = 0;
        int ledgerBreaks = 0;
        int fired = 0;
        int hit = 0;
        int raiders = 0;
        int down = 0;
        int before = 0;
        int during = 0;
        int after = 0;
        int drivenOff = 0;
        int seedsFiring = 0;
        int seedsDown = 0;
        int seedsBeforeRelease = 0;
        int dead = 0;
        int deadControl = 0;
        int coverLost = 0;
        int ticks = 0;
        for (int i = 0; i < SWEEP_SEEDS; i++) {
            long seed = DEFAULT_SEED + 1 + i;
            Result none = playOne("cap-none", FrameSink.NONE, null, seed);
            Result run = playOne("cap-intercept", FrameSink.NONE, none, seed);
            Map<String, Number> m = run.report.metrics();
            Trace t = run.trace;
            launched += m.get("coverLaunched").intValue();
            coverLost += m.get("coverLost").intValue();
            if (m.get("coverLaunched").intValue() != m.get("coverReturned").intValue() + m.get("coverLost").intValue() + m.get("coverStillOut").intValue()) ledgerBreaks++;
            fired += m.get("airToAirRoundsFired").intValue();
            hit += m.get("airToAirRoundsHit").intValue();
            raiders += t.raiders;
            down += t.raidersDown;
            before += t.downBeforeRelease();
            during += t.downInRelease();
            after += t.downAfterRelease();
            drivenOff += t.raidersDrivenOff;
            if (m.get("airToAirRoundsFired").intValue() >= 10) seedsFiring++;
            if (t.raidersDown >= 1) seedsDown++;
            if (t.downBeforeRelease() >= 1) seedsBeforeRelease++;
            dead += t.companyDead;
            deadControl += none.trace.companyDead;
            ticks = run.report.ticks();
        }
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("every-seed-puts-a-fighter-up", launched >= SWEEP_SEEDS,
                "cover fighters launched " + launched + " over " + SWEEP_SEEDS + " seeds"));
        verdicts.add(Verdict.of("the-ledger-balances-in-every-seed", ledgerBreaks == 0,
                "seeds whose cover ledger did not balance " + ledgerBreaks));
        if (!headOn) verdicts.add(Verdict.of("the-fighter-fires-in-some-seed", seedsFiring >= 1,
                "seeds in which the guns fired ten rounds or more " + seedsFiring + " of " + SWEEP_SEEDS
                        + "; rounds " + fired + ", hit " + hit));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("seeds", SWEEP_SEEDS);
        metrics.put("raiders", raiders);
        metrics.put("raidersDown", down);
        metrics.put("downBeforeRelease", before);
        metrics.put("downInRelease", during);
        metrics.put("downAfterRelease", after);
        metrics.put("raidersDrivenOff", drivenOff);
        metrics.put("seedsWithADownedRaider", seedsDown);
        metrics.put("seedsDownedBeforeRelease", seedsBeforeRelease);
        metrics.put("airToAirRoundsFired", fired);
        metrics.put("airToAirRoundsHit", hit);
        metrics.put("coverLost", coverLost);
        metrics.put("companyDeadWithCover", dead);
        metrics.put("companyDeadWithoutCover", deadControl);
        headOnControl = false;
        return new SceneReport(id(), headOn ? "cap-intercept-seeds-head-on" : "cap-intercept-seeds", ticks, verdicts, metrics);
    }

    /** {@code cap-vs-cap} across the same seeds, the defender's call slipping so the two rings meet at different phases. */
    private SceneReport sweepVersus(boolean headOn) {
        headOnControl = headOn;
        int bothUp = 0;
        int fought = 0;
        int fired = 0;
        int hit = 0;
        int marineKills = 0;
        int defenderKills = 0;
        int seedsWithAKill = 0;
        int ticks = 0;
        for (int i = 0; i < SWEEP_SEEDS; i++) {
            long seed = DEFAULT_SEED + 1 + i;
            Result run = playOne("cap-vs-cap", FrameSink.NONE, null, seed);
            Map<String, Number> m = run.report.metrics();
            Trace t = run.trace;
            if (m.get("coverLaunched").intValue() >= 1 && m.get("defenderCoverLaunched").intValue() >= 1) bothUp++;
            fired += m.get("airToAirRoundsFired").intValue();
            hit += m.get("airToAirRoundsHit").intValue();
            if (m.get("airToAirRoundsFired").intValue() >= 10) fought++;
            marineKills += t.airToAirLossesMarine;
            defenderKills += t.airToAirLosses;
            if (t.airToAirLossesMarine + t.airToAirLosses > 0) seedsWithAKill++;
            ticks = run.report.ticks();
        }
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("both-sides-put-a-fighter-up-in-every-seed", bothUp == SWEEP_SEEDS,
                "seeds in which both fighters launched " + bothUp + " of " + SWEEP_SEEDS));
        if (!headOn) verdicts.add(Verdict.of("they-fight-in-some-seed", fought >= 1,
                "seeds in which the guns fired ten rounds or more " + fought + "; rounds " + fired + ", hit " + hit));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("seeds", SWEEP_SEEDS);
        metrics.put("seedsWhereTheyFought", fought);
        metrics.put("seedsWithAKill", seedsWithAKill);
        metrics.put("airToAirRoundsFired", fired);
        metrics.put("airToAirRoundsHit", hit);
        metrics.put("marineFightersShotDown", marineKills);
        metrics.put("defenderFightersShotDown", defenderKills);
        headOnControl = false;
        return new SceneReport(id(), headOn ? "cap-vs-cap-seeds-head-on" : "cap-vs-cap-seeds", ticks, verdicts, metrics);
    }

    private static boolean nestLoop(String loop) {
        return loop.startsWith("nest-");
    }

    private static boolean capLoop(String loop) {
        return loop.startsWith("cap-");
    }

    /** The control sweeps fly their cover fighters the old head-on pursuit; per-instance, set by the sweep that wants it. */
    private boolean headOnControl;

    private Result playOne(String loop, FrameSink frames, Result control) {
        return playOne(loop, frames, control, DEFAULT_SEED);
    }

    private Result playOne(String loop, FrameSink frames, Result control, long seed) {
        boolean nest = nestLoop(loop);
        boolean raid = loop.equals("cap-none") || loop.equals("cap-intercept") || loop.equals("cap-under-aa");
        boolean ground = loop.startsWith("cap-ground");
        boolean versus = loop.equals("cap-vs-cap");
        boolean company = capLoop(loop);
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        int observerX = versus ? 50 : OBSERVER_X;
        builder.squad("observers").faction(Faction.MARINE).size(company ? 3 : 3).at(observerX, MID).primary(CARBINE)
                .special("special.comms-radio", 1).stationary()
                .assigned(id -> ObjectiveAssignment.defendTrack(id, observerX, MID)).done();
        if (company) {
            int cx = observerX - 4;
            builder.squad("company").faction(Faction.MARINE).size(COMPANY).at(cx, MID).primary(CARBINE).stationary()
                    .assigned(id -> ObjectiveAssignment.defendTrack(id, cx, MID)).done();
        }
        if (nest || raid) {
            // Far off, so the simulation has both sides and keeps ticking; the nest loops add the nest itself.
            builder.squad("garrison").faction(Faction.DEFENDER).size(2).at(WIDTH - 4, 4).primary(CARBINE).stationary()
                    .assigned(id -> ObjectiveAssignment.defendTrack(id, WIDTH - 4, 4)).done();
        } else if (ground) {
            int gx = loop.equals("cap-ground-close") ? observerX + 6 : GARRISON_X - 8;
            builder.squad("garrison").faction(Faction.DEFENDER).size(8).at(gx, MID).primary(CARBINE).stationary()
                    .assigned(id -> ObjectiveAssignment.defendTrack(id, gx, MID)).done();
        } else if (versus) {
            builder.squad("garrison").faction(Faction.DEFENDER).size(8).at(88, MID).primary(CARBINE).stationary()
                    .assigned(id -> ObjectiveAssignment.defendTrack(id, 88, MID)).done();
            builder.squad("defender-observers").faction(Faction.DEFENDER).size(3).at(92, MID).primary(CARBINE)
                    .special("special.comms-radio", 1).stationary()
                    .assigned(id -> ObjectiveAssignment.defendTrack(id, 92, MID)).done();
        } else {
            builder.squad("garrison").faction(Faction.DEFENDER).size(10).at(GARRISON_X, MID).primary(CARBINE).stationary()
                    .assigned(id -> ObjectiveAssignment.defendTrack(id, GARRISON_X, MID)).done();
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(seed);

        List<Long> guns = new ArrayList<>();
        if (nest) {
            guns.add(spawnGun(sim, GARRISON_X, MID, "structure.turret-heavy-mg"));
        }
        if (loop.equals("cap-under-aa")) {
            // Beyond the company's own sight (it sights thirty cells), inside the ring's reach: a surprise.
            for (int dy : new int[]{-5, 0, 5}) {
                guns.add(spawnGun(sim, observerX + 36, MID + dy, TurretCatalogRegistry.ANTI_AIR_STRUCTURE_ID));
            }
        }

        AirPoolDeclaration declaration = declare(loop);
        sim.setAirPool(new AirPoolService(declaration));
        sim.airPool().useHeadOnCoverPursuit(headOnControl);
        Trace trace = new Trace(world, loop, guns, seed);
        int ticks = nest ? 1500 : loop.equals("strafe-open") ? 1500 : ground ? 1800 : TICKS;
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, Math.max(30, ticks / 24), tick -> trace.caption(loop, tick))
                .run(ticks);
        return new Result(report(loop, trace, sim, control, ticks), trace);
    }

    private static AirPoolDeclaration declare(String loop) {
        AirPoolStock none = null;
        AirPoolStock fighters = new AirPoolStock(3, 3, 40f);
        AirPoolStock bombers = new AirPoolStock(2, 2, 60f);
        AirPoolDeclaration declaration = AirPoolDeclaration.NONE;
        switch (loop) {
            case "strafe-open", "nest-declined" ->
                    declaration = declaration.with(Faction.MARINE, AirPoolAllowance.of(none, fighters, true));
            case "nest-bombed", "nest-strafed" ->
                    declaration = declaration.with(Faction.MARINE, AirPoolAllowance.of(bombers, fighters, false));
            case "cap-vs-cap" -> {
                declaration = declaration.with(Faction.MARINE, AirPoolAllowance.of(none, fighters, false));
                declaration = declaration.with(Faction.DEFENDER, AirPoolAllowance.of(none, fighters, false));
            }
            default -> declaration = declaration.with(Faction.MARINE, AirPoolAllowance.of(none, fighters, false));
        }
        return declaration;
    }

    private static long spawnGun(BattleSimulation sim, int x, int y, String structure) {
        sim.getGrid().setWalkable(x, y, false);
        return sim.spawn(MapTurret.create("gun-" + x + "-" + y, Faction.DEFENDER, structure, x, y));
    }

    private SceneReport report(String loop, Trace t, BattleSimulation sim, Result control, int ticks) {
        List<Verdict> verdicts = new ArrayList<>();
        AirPoolService pool = sim.airPool();
        AirPoolService.MissionTally strafe = pool.tally(Faction.MARINE, AirMission.STRAFE);
        AirPoolService.MissionTally bomb = pool.tally(Faction.MARINE, AirMission.BOMB);
        AirPoolService.MissionTally cover = pool.tally(Faction.MARINE, AirMission.COVER);
        switch (loop) {
            case "strafe-open" -> {
                verdicts.add(Verdict.of("director-calls-a-fighter-for-soft-ground",
                        strafe.launched() >= 1 && bomb.called() == 0,
                        "strafes called " + strafe.called() + ", launched " + strafe.launched() + ", bombs " + bomb.called()
                                + ", first call at tick " + t.firstCallTick));
                verdicts.add(Verdict.of("flies-a-real-gun-run",
                        t.states.contains(ShuttleState.ATTACK_RUN) && t.entryOffMap,
                        "phases seen " + t.states + ", first seen at (" + String.format(Locale.ROOT, "%.0f, %.0f", t.entryX, t.entryY) + ")"));
                verdicts.add(Verdict.of("the-sortie-resolves-through-the-pool",
                        strafe.returned() + strafe.lost() + strafe.aborted() >= 1,
                        "returned " + strafe.returned() + ", lost " + strafe.lost() + ", driven off " + strafe.aborted()
                                + ", fighters in stock " + pool.available(Faction.MARINE, AirPoolKind.FIGHTER)));
            }
            case "nest-bombed" -> {
                verdicts.add(Verdict.of("the-bomber-flies", bomb.launched() >= 1,
                        "bombs called " + bomb.called() + ", launched " + bomb.launched()));
                verdicts.add(Verdict.of("the-bomb-damages-the-nest", t.nestHullShare < 1f,
                        "nest hull left " + pct(t.nestHullShare) + ", first dead at tick " + t.firstGunDeathTick));
            }
            case "nest-strafed" -> {
                verdicts.add(Verdict.of("the-fighter-flies", strafe.launched() >= 1,
                        "strafes called " + strafe.called() + ", launched " + strafe.launched()));
                verdicts.add(Verdict.of("it-does-less-to-a-hardened-nest-than-a-bomber",
                        t.nestHullShare > control.trace.nestHullShare,
                        "nest hull left " + pct(t.nestHullShare) + " against " + pct(control.trace.nestHullShare)
                                + " after the bomber; nest dead " + (t.gunsDead >= 1)));
            }
            case "nest-declined" -> {
                verdicts.add(Verdict.of("the-nest-is-sighted", sim.airPool().director(Faction.MARINE).knownGuns().size() >= 1,
                        "guns sighted " + sim.airPool().director(Faction.MARINE).knownGuns().size()));
                verdicts.add(Verdict.of("no-fighter-is-spent-on-it",
                        strafe.called() == 0 && pool.available(Faction.MARINE, AirPoolKind.FIGHTER) == 3,
                        "strafes called " + strafe.called() + ", fighters in stock " + pool.available(Faction.MARINE, AirPoolKind.FIGHTER)
                                + " of 3, nest hull left " + pct(t.nestHullShare)));
            }
            case "cap-none" -> {
                verdicts.add(Verdict.of("the-raid-reaches-the-company-unopposed",
                        t.raidersBombing == t.raiders && t.raidersDown == 0,
                        "raiders launched " + t.raiders + ", down " + t.raidersDown + ", reached their bomb run " + t.raidersBombing));
            }
            case "cap-intercept" -> {
                verdicts.add(Verdict.of("the-commander-asks-and-a-fighter-goes",
                        cover.launched() >= 1 && t.coverOutcome == Outcome.CALLING,
                        "request " + t.coverOutcome + ", launched " + cover.launched()));
                verdicts.add(Verdict.of("it-holds-a-station", t.states.contains(ShuttleState.ORBIT) && t.onStationSeconds >= 20f,
                        String.format(Locale.ROOT, "seconds on the ring %.0f, phases %s", t.onStationSeconds, t.states)));
                verdicts.add(Verdict.of("it-chases-and-fires",
                        t.states.contains(ShuttleState.INTERCEPT) && sim.airToAir().roundsFired() >= 10,
                        "rounds fired " + sim.airToAir().roundsFired() + ", hit " + sim.airToAir().roundsHit()
                                + ", chases " + t.chases));
                verdicts.add(Verdict.of("the-guns-are-brought-to-bear-from-behind",
                        t.windowTicks > 0 && t.rearWindowTicks * 2 >= t.windowTicks,
                        "ticks a raider stood in the guns' window " + t.windowTicks + ", of them from its rear quarter "
                                + t.rearWindowTicks + "; raiders down before dropping " + t.downBeforeRelease()
                                + ", mid-drop " + t.downInRelease() + ", after " + t.downAfterRelease()));
                // Reported, not judged: one raider shot down on its run still lets the other drop, and the bomb scatter
                // moves the company toll by a couple of marines from seed to seed.
                verdicts.add(Verdict.of("the-control-lost-no-raider",
                        control.trace.raidersDown == 0,
                        "company dead " + t.companyDead + " against " + control.trace.companyDead + " with nothing overhead; raiders on their bomb run "
                                + t.raidersBombing + " against " + control.trace.raidersBombing));
                verdicts.add(Verdict.of("the-ledger-balances",
                        cover.launched() == cover.returned() + cover.lost() + pool.coversOut(Faction.MARINE),
                        "launched " + cover.launched() + ", returned " + cover.returned() + ", lost " + cover.lost()
                                + ", still out " + pool.coversOut(Faction.MARINE) + ", fighters in stock "
                                + pool.available(Faction.MARINE, AirPoolKind.FIGHTER)));
            }
            case "cap-under-aa" -> {
                verdicts.add(Verdict.of("the-cover-goes-up-unwarned",
                        cover.launched() >= 1 && sim.airPool().director(Faction.MARINE).knownGuns().isEmpty(),
                        "request " + t.coverOutcome + ", launched " + cover.launched()
                                + ", guns sighted " + sim.airPool().director(Faction.MARINE).knownGuns().size()));
                verdicts.add(Verdict.of("anti-air-makes-the-patrol-pay",
                        t.minHullShare < 1f || cover.lost() + cover.aborted() >= 1,
                        "fighter hull low point " + pct(t.minHullShare) + ", lost " + cover.lost()
                                + ", driven off " + cover.aborted()));
                verdicts.add(Verdict.of("it-costs-the-pool-an-airframe-for-a-while",
                        t.minFighters < 3 || cover.lost() >= 1,
                        "fighters in stock fell to " + t.minFighters + " of 3"));
            }
            case "cap-ground" -> {
                verdicts.add(Verdict.of("the-patrol-takes-a-gun-pass-on-sighted-ground",
                        t.groundPasses >= 1 && t.states.contains(ShuttleState.ATTACK_RUN),
                        "ground passes " + t.groundPasses + ", phases " + t.states));
                verdicts.add(Verdict.of("it-spares-its-own-side", t.companyDead == 0 && t.friendlyDamage == 0f,
                        "company dead " + t.companyDead));
                verdicts.add(Verdict.of("it-goes-back-to-the-ring", t.states.contains(ShuttleState.ORBIT) && cover.returned() + pool.coversOut(Faction.MARINE) >= 1,
                        "returned " + cover.returned() + ", out " + pool.coversOut(Faction.MARINE)));
            }
            case "cap-ground-close" -> {
                verdicts.add(Verdict.of("no-pass-is-flown-over-its-own-people", t.groundPasses == 0,
                        "ground passes " + t.groundPasses + " with the company six cells from the group"));
                verdicts.add(Verdict.of("the-company-is-unharmed-by-the-patrol", t.friendlyDamage == 0f,
                        "company dead " + t.companyDead));
            }
            case "cap-vs-cap" -> {
                verdicts.add(Verdict.of("both-sides-put-a-fighter-up",
                        cover.launched() >= 1 && pool.tally(Faction.DEFENDER, AirMission.COVER).launched() >= 1,
                        "marine launched " + cover.launched() + ", defender launched "
                                + pool.tally(Faction.DEFENDER, AirMission.COVER).launched()));
                verdicts.add(Verdict.of("they-fight-in-the-air",
                        sim.airToAir().roundsFired() >= 10 && sim.airToAir().roundsHit() >= 1,
                        "rounds fired " + sim.airToAir().roundsFired() + ", hit " + sim.airToAir().roundsHit()));
            }
            default -> throw new IllegalStateException(loop);
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("strafeLaunched", strafe.launched());
        metrics.put("bombLaunched", bomb.launched());
        metrics.put("coverLaunched", cover.launched());
        metrics.put("coverReturned", cover.returned());
        metrics.put("coverLost", cover.lost());
        metrics.put("coverStillOut", pool.coversOut(Faction.MARINE));
        metrics.put("strafeReturned", strafe.returned());
        metrics.put("strafeLost", strafe.lost());
        metrics.put("coverDrivenOff", cover.aborted());
        metrics.put("defenderCoverLost", pool.tally(Faction.DEFENDER, AirMission.COVER).lost());
        metrics.put("defenderCoverLaunched", pool.tally(Faction.DEFENDER, AirMission.COVER).launched());
        metrics.put("airToAirRoundsFired", sim.airToAir().roundsFired());
        metrics.put("airToAirRoundsHit", sim.airToAir().roundsHit());
        metrics.put("airToAirKills", t.airToAirLosses);
        metrics.put("chases", t.chases);
        metrics.put("groundPasses", t.groundPasses);
        metrics.put("raiders", t.raiders);
        metrics.put("raidersDown", t.raidersDown);
        metrics.put("raidersDrivenOff", t.raidersDrivenOff);
        metrics.put("raidersDownBeforeRelease", t.downBeforeRelease());
        metrics.put("raidersDownInRelease", t.downInRelease());
        metrics.put("raidersDownAfterRelease", t.downAfterRelease());
        metrics.put("gunWindowTicks", t.windowTicks);
        metrics.put("gunWindowTicksFromBehind", t.rearWindowTicks);
        metrics.put("companyDead", t.companyDead);
        metrics.put("garrisonDead", t.garrisonDead);
        metrics.put("nestHullPercent", Math.round(t.nestHullShare * 100f));
        metrics.put("minFighterHullPercent", Math.round(t.minHullShare * 100f));
        metrics.put("onStationSeconds", Math.round(t.onStationSeconds));
        metrics.put("fighterTopSpeed", Math.round(t.fighterTopSpeed * 10f) / 10f);
        metrics.put("raiderTopSpeed", Math.round(t.raiderTopSpeed * 10f) / 10f);
        return new SceneReport(id(), loop, ticks, verdicts, metrics);
    }

    private static String pct(float share) {
        return String.format(Locale.ROOT, "%.0f%%", share * 100f);
    }

    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        private final String loop;
        private final List<Long> guns;
        final Set<ShuttleState> states = EnumSet.noneOf(ShuttleState.class);
        int firstCallTick = -1;
        int firstDeathTick = -1;
        int firstGunDeathTick = -1;
        int garrisonSize;
        int garrisonDead;
        int companyDead;
        float friendlyDamage;
        int gunsDead;
        float nestHullShare = 1f;
        float minHullShare = 1f;
        int minFighters = Integer.MAX_VALUE;
        boolean entryOffMap;
        float entryX;
        float entryY;
        boolean entrySeen;
        float onStationSeconds;
        float fighterTopSpeed;
        float raiderTopSpeed;
        int chases;
        int groundPasses;
        int raiders;
        int raidersDown;
        int raidersDrivenOff;
        int raidersBombing;
        int airToAirLosses;
        int airToAirLossesMarine;
        Outcome coverOutcome;
        private final List<Long> raiderIds = new ArrayList<>();
        private final Set<Long> drivenOff = new HashSet<>();
        private final Set<Long> bombingIds = new HashSet<>();
        private int seenEvents;
        private boolean forced;
        private boolean coverAsked;
        private boolean defenderCoverAsked;
        private int companyAliveStart = -1;
        private final int[] ticks = new int[1];

        private Trace(SceneWorld world, String loop, List<Long> guns, long seed) {
            this.world = world;
            this.loop = loop;
            this.guns = guns;
            // The recorded seed meets the raid on the schedule the loop was written with; every other seed slips it.
            if (seed != DEFAULT_SEED) {
                Random slip = new Random(seed * 31L + 7L);
                raidJitter = slip.nextInt(RAID_JITTER_TICKS);
                raidLateralA = (slip.nextFloat() * 2f - 1f) * RAID_LATERAL_CELLS;
                raidLateralB = (slip.nextFloat() * 2f - 1f) * RAID_LATERAL_CELLS;
            }
        }

        /** Ticks the cover fighter's guns had a raider inside their window, and how many of them it was in the raider's rear quarter. */
        int windowTicks;
        int rearWindowTicks;

        private void trackWindow(BattleSimulation sim) {
            long fighter = 0L;
            for (long id : sim.getAirEntityIds()) {
                ShuttleMission m = sim.world().mission(id);
                if (m != null && m.cover != null && !m.destroyed && sim.world().airFaction(id) == Faction.MARINE) fighter = id;
            }
            if (fighter == 0L) return;
            AirBody f = sim.world().kinematics(fighter);
            for (long r : raiderIds) {
                ShuttleMission rm = sim.world().mission(r);
                AirBody b = sim.world().kinematics(r);
                if (rm == null || b == null || rm.destroyed) continue;
                float range = (float) Math.hypot(b.x - f.x, b.y - f.y);
                if (!AirToAirGun.inWindow(range, AirToAirGun.offNoseDegrees(f, b.x, b.y))) continue;
                windowTicks++;
                if (AirIntercept.isRearQuarter(f.x, f.y, b.x, b.y, b.vx, b.vy)) rearWindowTicks++;
            }
        }

        /** What became of one raider: whether the bomber had begun dropping when it went down. */
        enum Fate { SURVIVED, DOWN_BEFORE_RELEASE, DOWN_IN_RELEASE, DOWN_AFTER_RELEASE }

        private final Map<Long, Boolean> released = new LinkedHashMap<>();
        private final Map<Long, Fate> fates = new LinkedHashMap<>();
        private int raidJitter;
        private float raidLateralA;
        private float raidLateralB;

        /** Raiders shot down (by anything) before they had dropped a bomb. */
        int downBeforeRelease() { return count(Fate.DOWN_BEFORE_RELEASE); }

        /** Raiders shot down with their stick partly dropped. */
        int downInRelease() { return count(Fate.DOWN_IN_RELEASE); }

        /** Raiders shot down after dropping the whole stick. */
        int downAfterRelease() { return count(Fate.DOWN_AFTER_RELEASE); }

        private int count(Fate fate) {
            int n = 0;
            for (Fate f : fates.values()) if (f == fate) n++;
            return n;
        }

        private void classifyRaiders(BattleSimulation sim) {
            for (long id : raiderIds) {
                ShuttleMission m = sim.world().mission(id);
                if (m == null || fates.containsKey(id)) continue;
                boolean started = released.getOrDefault(id, false);
                if (m.state == ShuttleState.ATTACK_RUN && m.ordnance != null && m.roundsLeftThisPass < m.ordnance.roundsPerPass
                        && !m.aborted) {
                    started = true;
                    released.put(id, true);
                }
                if (!m.destroyed) continue;
                Fate fate = !started ? Fate.DOWN_BEFORE_RELEASE
                        : m.roundsLeftThisPass > 0 ? Fate.DOWN_IN_RELEASE : Fate.DOWN_AFTER_RELEASE;
                fates.put(id, fate);
            }
        }

        String caption(String loop, int tick) {
            AirPoolService pool = world.sim().airPool();
            return loop + " / tick " + tick + " / fighters " + pool.available(Faction.MARINE, AirPoolKind.FIGHTER)
                    + " in stock, " + pool.outFlying(Faction.MARINE, AirPoolKind.FIGHTER) + " out, "
                    + pool.lost(Faction.MARINE, AirPoolKind.FIGHTER) + " lost";
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            ticks[0] = tick;
            AirPoolService pool = sim.airPool();
            for (; seenEvents < pool.events().size(); seenEvents++) {
                AirPoolService.Event e = pool.events().get(seenEvents);
                if (e.type() == AirPoolService.EventType.CALLED && e.side() == Faction.MARINE && firstCallTick < 0) firstCallTick = tick;
            }
            minFighters = Math.min(minFighters, pool.available(Faction.MARINE, AirPoolKind.FIGHTER));

            garrisonSize = world.members("garrison").length;
            int dead = 0;
            for (long u : world.members("garrison")) if (!sim.getRoster().isAliveById(u)) dead++;
            garrisonDead = dead;
            if (dead > 0 && firstDeathTick < 0) firstDeathTick = tick;
            int lost = 0;
            if (loop.startsWith("cap-")) {
                long[] company = world.members("company");
                int alive = 0;
                for (long u : company) if (sim.getRoster().isAliveById(u)) alive++;
                if (companyAliveStart < 0) companyAliveStart = company.length;
                lost = company.length - alive;
            }
            companyDead = lost;
            friendlyDamage = loop.startsWith("cap-ground") ? lost : 0f;
            int gd = 0;
            for (long g : guns) {
                if (!sim.getRoster().isAliveById(g)) gd++;
            }
            gunsDead = gd;
            if (gd > 0 && firstGunDeathTick < 0) firstGunDeathTick = tick;
            if (!guns.isEmpty() && nestLoop(loop)) {
                long g = guns.get(0);
                nestHullShare = sim.getRoster().isAliveById(g) ? sim.world().hp(g) / Math.max(1f, sim.world().maxHp(g)) : 0f;
            }

            observeAircraft(sim, tick, pool);
            classifyRaiders(sim);
            trackWindow(sim);
            script(sim, tick, pool);
        }

        private void observeAircraft(BattleSimulation sim, int tick, AirPoolService pool) {
            ArrayList<Long> alive = new ArrayList<>();
            for (long id : sim.getAirEntityIds()) alive.add(id);
            for (long id : alive) {
                ShuttleMission m = sim.world().mission(id);
                Faction faction = sim.world().airFaction(id);
                AirBody body = sim.world().kinematics(id);
                if (m == null || body == null) continue;
                boolean mine = faction == Faction.MARINE && m.poolSortie;
                boolean raider = raiderIds.contains(id);
                if (mine || (loop.equals("cap-vs-cap") && m.poolSortie)) {
                    if (!entrySeen) {
                        entrySeen = true;
                        entryX = body.x;
                        entryY = body.y;
                        entryOffMap = body.x < 0 || body.y < 0 || body.x > WIDTH || body.y > HEIGHT;
                    }
                    if (faction == Faction.MARINE) {
                        states.add(m.state);
                        fighterTopSpeed = Math.max(fighterTopSpeed, body.speed());
                        if (m.state == ShuttleState.ORBIT) onStationSeconds += BattleSimulation.TICK_DT;
                        if (!m.destroyed && sim.world().airframe(id) != null) {
                            minHullShare = Math.min(minHullShare, sim.world().hp(id) / sim.world().airframe(id).maxHp());
                        }
                        CoverDuty duty = m.cover;
                        if (duty != null) {
                            chases = Math.max(chases, duty.chases);
                            groundPasses = Math.max(groundPasses, duty.groundPassesFlown);
                        }
                    }
                }
                if (raider) {
                    raiderTopSpeed = Math.max(raiderTopSpeed, body.speed());
                    if (m.state == ShuttleState.ATTACK_RUN && bombingIds.add(id)) raidersBombing = bombingIds.size();
                }
            }
            for (long id : raiderIds) {
                ShuttleMission m = sim.world().mission(id);
                if (m != null && m.aborted && !drivenOff.contains(id)) drivenOff.add(id);
            }
            int down = 0;
            for (AirLoss loss : sim.getAirLosses()) {
                if (loss.faction() == Faction.DEFENDER) down++;
            }
            raidersDown = down;
            raidersDrivenOff = drivenOff.size();
            int kills = 0;
            for (AirLoss loss : sim.getAirLosses()) {
                if ("air-to-air".equals(loss.cause()) && loss.faction() == Faction.DEFENDER) kills++;
            }
            airToAirLosses = kills;
            int marineKills = 0;
            for (AirLoss loss : sim.getAirLosses()) {
                if ("air-to-air".equals(loss.cause()) && loss.faction() == Faction.MARINE) marineKills++;
            }
            airToAirLossesMarine = marineKills;
        }


        private void script(BattleSimulation sim, int tick, AirPoolService pool) {
            switch (loop) {
                case "nest-bombed", "nest-strafed" -> {
                    if (!forced && tick >= 90) {
                        forced = true;
                        AirPoolKind kind = loop.equals("nest-bombed") ? AirPoolKind.BOMBER : AirPoolKind.FIGHTER;
                        pool.request(sim, Faction.MARINE, kind, GARRISON_X + 0.5f, MID + 0.5f, true);
                    }
                }
                case "cap-none", "cap-intercept", "cap-under-aa" -> {
                    if (!loop.equals("cap-none") && !coverAsked && tick >= COVER_TICK) {
                        coverAsked = true;
                        coverOutcome = pool.director(Faction.MARINE).cover(sim, pool, OBSERVER_X - 4 + 0.5f, MID + 0.5f, u -> true);
                    }
                    if (raiderIds.isEmpty() && tick >= RAID_TICK + raidJitter) {
                        raiderIds.add(raider(sim, OBSERVER_X - 4f, MID - 4f + raidLateralA));
                        raiders++;
                    } else if (raiderIds.size() == 1 && tick >= RAID_TICK + raidJitter + RAID_GAP_TICKS) {
                        raiderIds.add(raider(sim, OBSERVER_X - 4f, MID + 4f + raidLateralB));
                        raiders++;
                    }
                }
                case "cap-ground", "cap-ground-close" -> {
                    if (!coverAsked && tick >= COVER_TICK) {
                        coverAsked = true;
                        coverOutcome = pool.director(Faction.MARINE).cover(sim, pool, OBSERVER_X + 0.5f, MID + 0.5f, u -> true);
                    }
                }
                case "cap-vs-cap" -> {
                    if (!coverAsked && tick >= COVER_TICK) {
                        coverAsked = true;
                        coverOutcome = pool.director(Faction.MARINE).cover(sim, pool, 46.5f, MID + 0.5f, u -> true);
                    }
                    if (!defenderCoverAsked && tick >= COVER_TICK + raidJitter) {
                        defenderCoverAsked = true;
                        pool.director(Faction.DEFENDER).cover(sim, pool, 88.5f, MID + 0.5f, u -> true);
                    }
                }
                default -> { }
            }
        }

        /** An enemy bomber on a raid over the company, flown the way a called strike is. */
        private long raider(BattleSimulation sim, float lzX, float lzY) {
            long id = sim.spawnSortie(FighterProfile.DAGGER, Faction.DEFENDER, lzX, lzY,
                    WIDTH + 8, lzY, WIDTH + 8, lzY, 0f);
            ShuttleMission m = sim.world().mission(id);
            m.strikeSortie = true;
            m.poolSortie = true;
            m.passesLeft = 1;
            m.ordnance = AirOrdnance.STRIKE_BOMBS;
            return id;
        }
    }
}
