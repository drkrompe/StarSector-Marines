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
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Slice 7 of the frigate duel ({@code warship-behaviours.md}, ship against ship): the duel ledger. One fight with every way a hull can end it: a
 * Salvage Rig climbing out over the east edge, a battered Hound (P) the doctrine will withdraw, a second Hound
 * fighting over a hostile squad, and two Wolf (H) on a mission's visit that leave once the air is quiet. Unarmed
 * units stand under the whole fight to catch stray rounds and wreckage.
 *
 * <p>Loops: {@code ledger} (the fight, with the ground squads) and {@code empty-ground} (the same fight with every
 * ground unit far from it: the ledger must count no casualties). The verdicts compare the ledger with what the scene
 * itself saw: each hull's outcome, where a lost hull burst, and every ground unit hurt or killed, since nothing else
 * in the scene hurts the ground. The outcomes themselves are readings.
 */
public final class FrigateLedgerScene implements BehaviorScene {

    private static final int WIDTH = 140;
    private static final int HEIGHT = 68;
    private static final int TICKS = 2700;
    private static final int FRAME_EVERY = 8;
    private static final int CLIMB_TICK = 30;

    @Override public String id() { return "frigate-ledger"; }

    @Override public String label() {
        return "Frigate ledger: a rig escapes, a Hound (P) breaks off, another falls, two Wolf (H) end their visit; "
                + "the ledger's outcomes and ground casualties against what the scene saw";
    }

    @Override public int frameDelayMillis() { return 100; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("ledger", "empty-ground")) reports.add(report(loop, playOne(loop, frames)));
        return reports;
    }

    private record Run(Trace trace, BattleSimulation sim) {}

    private Run playOne(String loop, FrameSink frames) throws Exception {
        boolean ground = loop.equals("ledger");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        List<String> keys = new ArrayList<>();
        if (ground) {
            // Unarmed ground units spread under the whole fight, where misses and wrecks come down.
            for (int gx = 10; gx <= 100; gx += 6) {
                for (int gy = 14; gy <= 54; gy += 5) {
                    String key = "g" + gx + "-" + gy;
                    builder.unit(key, gx < 40 ? Faction.MARINE : Faction.DEFENDER, UnitType.MARINE, gx, gy);
                    keys.add(key);
                }
            }
        }
        builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 2, 66);
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, 137, 66);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261016L);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) sim.getTopology().setGroundKind(x, y, GroundKind.GRASS);
        }
        Warship rig = Warship.fit("crig", "crig_Standard", Faction.DEFENDER, 116f, 34f, 90f);
        rig.stationTo(116f, 34f);
        rig.limitFlux();
        Warship battered = ship("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, 96f, 20f, 180f);
        battered.takeDamage(battered.maxHitPoints * 0.8f, 1000f);
        Warship line = ship("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, 60f, 44f, 180f);
        List<Warship> wolves = new ArrayList<>();
        for (float y : new float[]{24f, 44f}) {
            Warship wolf = ship("wolf_hegemony", "wolf_hegemony_Assault", Faction.ALLY, 22f, y, 0f);
            wolf.groundFire(Warship.GroundFire.HOLD);
            wolf.arriveFromAbove();
            wolf.visit(8f);
            wolves.add(wolf);
        }
        List<Warship> all = new ArrayList<>(List.of(rig, battered, line));
        all.addAll(wolves);
        for (Warship s : all) sim.addWarship(s);
        List<Long> units = new ArrayList<>();
        for (String k : keys) units.add(world.unit(k));
        Trace trace = new Trace(all, rig, units);
        TickObserver script = (s, tick) -> {
            if (tick == CLIMB_TICK) rig.climbOut(new float[][]{{139f, 30f}}, WIDTH, HEIGHT);
        };
        SceneRun.of(world).observe(script).observe(trace)
                .frames(frames, loop, FRAME_EVERY, tick -> trace.caption(loop, tick))
                .run(TICKS);
        return new Run(trace, sim);
    }

    private static Warship ship(String hull, String variant, Faction side, float x, float y, float facing)
            throws Exception {
        Warship ship = Warship.fit(hull, variant, side, x, y, facing);
        ship.stationTo(x, y);
        ship.limitFlux();
        ship.shipFire(ShipFireOrder.ALL);
        // Nobody fires at the ground on purpose, so everything the ground suffers is the duel's doing.
        ship.groundFire(Warship.GroundFire.HOLD);
        ship.useDoctrine();
        return ship;
    }

    private SceneReport report(String loop, Run run) {
        Trace t = run.trace();
        WarshipDuelLedger ledger = run.sim().warships().duelLedger();
        List<Verdict> verdicts = new ArrayList<>();
        List<WarshipDuelLedger.Entry> entries = ledger.entries();
        verdicts.add(Verdict.of("every-hull-has-its-line", entries.size() == t.ships.size(),
                entries.size() + " lines for " + t.ships.size() + " hulls"));
        StringBuilder disagreements = new StringBuilder();
        StringBuilder outcomes = new StringBuilder();
        for (Warship s : t.ships) {
            WarshipDuelLedger.Entry e = ledger.entry(s);
            WarshipDuelLedger.Outcome seen = t.seen(s);
            outcomes.append(e == null ? s.hullId + " missing; " : String.format(Locale.ROOT,
                    "%s#%d %s at %.1fs (%.0f,%.0f)%s; ", s.hullId, s.serial(), e.outcome(), e.seconds(), e.x(), e.y(),
                    Float.isNaN(e.struckX()) ? "" : String.format(Locale.ROOT, " struck (%.0f,%.0f)",
                            e.struckX(), e.struckY())));
            if (e == null || e.outcome() != seen) {
                disagreements.append(String.format(Locale.ROOT, "%s#%d ledger %s, seen %s; ", s.hullId, s.serial(),
                        e == null ? "none" : e.outcome(), seen));
                continue;
            }
            if (seen == WarshipDuelLedger.Outcome.DESTROYED) {
                float[] at = t.burstAt.get(s);
                boolean where = at != null && Math.hypot(e.x() - at[0], e.y() - at[1]) <= 1f;
                boolean struck = !Float.isNaN(e.struckX()) || !t.wreckStruck(s, run.sim());
                if (!where || !struck) {
                    disagreements.append(String.format(Locale.ROOT, "%s#%d burst at %.1f,%.1f (seen %s), struck %s; ",
                            s.hullId, s.serial(), e.x(), e.y(), at == null ? "none"
                                    : String.format(Locale.ROOT, "%.1f,%.1f", at[0], at[1]),
                            Float.isNaN(e.struckX()) ? "not recorded" : "recorded"));
                }
            }
        }
        verdicts.add(Verdict.of("the-ledger-says-what-became-of-each-hull", disagreements.length() == 0,
                disagreements.length() == 0 ? outcomes.toString() : disagreements.toString()));
        int ledgerHits = 0;
        int ledgerKilled = 0;
        for (WarshipDuelLedger.Cause c : WarshipDuelLedger.Cause.values()) {
            for (Faction f : Faction.values()) {
                ledgerHits += ledger.hurt(c, f) + ledger.killed(c, f);
                ledgerKilled += ledger.killed(c, f);
            }
        }
        verdicts.add(Verdict.of("every-ground-casualty-is-in-the-ledger",
                ledgerHits >= t.hurtTicks && ledgerKilled == t.deaths
                        && (loop.equals("ledger") ? t.hurtTicks > 0 || t.deaths > 0 : ledgerHits == 0),
                String.format(Locale.ROOT, "scene saw %d unit-ticks of hurt and %d deaths; ledger counts %d hits, %d "
                        + "of them deaths; deaths %s", t.hurtTicks, t.deaths, ledgerHits, ledgerKilled, t.deathLog)));

        Map<String, Number> metrics = new LinkedHashMap<>();
        for (WarshipDuelLedger.Outcome o : WarshipDuelLedger.Outcome.values()) {
            int n = 0;
            for (WarshipDuelLedger.Entry e : entries) if (e.outcome() == o) n++;
            metrics.put("hulls" + o.name(), n);
        }
        for (WarshipDuelLedger.Cause c : WarshipDuelLedger.Cause.values()) {
            for (Faction f : new Faction[]{Faction.MARINE, Faction.DEFENDER}) {
                metrics.put(c.name() + "_" + f.name() + "_hurt", ledger.hurt(c, f));
                metrics.put(c.name() + "_" + f.name() + "_killed", ledger.killed(c, f));
            }
        }
        metrics.put("roundsStrayed", run.sim().warships().shipFire().roundsStrayed());
        metrics.put("warheadsStrayed", run.sim().warships().shipFire().warheadsStrayed());
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        final List<Warship> ships;
        final Warship rig;
        final List<Long> units;
        final Map<Warship, float[]> burstAt = new IdentityHashMap<>();
        private final Map<Warship, float[]> lastAlive = new IdentityHashMap<>();
        private final Map<Warship, Boolean> withdrewAtLeaving = new IdentityHashMap<>();
        private final Map<Long, Float> hp = new HashMap<>();
        private final Map<Long, Boolean> alive = new HashMap<>();
        final StringBuilder deathLog = new StringBuilder();
        private final Map<Long, Float> lastX = new HashMap<>();
        private final Map<Long, Float> lastY = new HashMap<>();
        int hurtTicks;
        int deaths;

        Trace(List<Warship> ships, Warship rig, List<Long> units) {
            this.ships = ships;
            this.rig = rig;
            this.units = units;
        }

        String caption(String loop, int tick) {
            StringBuilder s = new StringBuilder(String.format(Locale.ROOT, "%s / tick %d", loop, tick));
            for (Warship w : ships) {
                String name = w.hullId;
                if (w.destroyed()) s.append(" / ").append(name).append(" down");
                else if (w.departed()) s.append(" / ").append(name).append(" gone");
                else if (w.departing()) s.append(" / ").append(name).append(" leaving");
                else s.append(String.format(Locale.ROOT, " / %s %.0f%%", name, w.hullFraction() * 100f));
            }
            return s.toString();
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            for (Warship s : ships) {
                if (!s.destroyed()) {
                    lastAlive.put(s, new float[]{s.x, s.y});
                    if (s.departing() && !withdrewAtLeaving.containsKey(s)) {
                        withdrewAtLeaving.put(s, s.posture() == WarshipPosture.WITHDRAW);
                    }
                } else if (!burstAt.containsKey(s)) {
                    // A hull taken to nothing bursts where it was on the tick it was lost.
                    burstAt.put(s, new float[]{s.x, s.y});
                }
            }
            for (long u : units) {
                boolean live = sim.getRoster().isAliveById(u);
                float h = live ? sim.world().hp(u) : 0f;
                Float before = hp.get(u);
                Boolean wasAlive = alive.get(u);
                if (before != null && wasAlive) {
                    if (!live) {
                        deaths++;
                        deathLog.append(String.format(Locale.ROOT, "t%d(%.0f,%.0f) ", tick, lastX.get(u), lastY.get(u)));
                    }
                    else if (h < before) hurtTicks++;
                }
                hp.put(u, h);
                if (live) {
                    lastX.put(u, sim.world().x(u));
                    lastY.put(u, sim.world().y(u));
                }
                alive.put(u, live);
            }
        }

        /** What the scene saw become of a hull, in the ledger's words. */
        WarshipDuelLedger.Outcome seen(Warship s) {
            if (s.destroyed()) return WarshipDuelLedger.Outcome.DESTROYED;
            if (!s.departed()) return WarshipDuelLedger.Outcome.OVER_THE_BATTLE;
            if (s == rig && s.physicallyDeparted()) return WarshipDuelLedger.Outcome.ESCAPED;
            if (Boolean.TRUE.equals(withdrewAtLeaving.get(s))) return WarshipDuelLedger.Outcome.BROKE_OFF;
            return WarshipDuelLedger.Outcome.DEPARTED;
        }

        /** Whether this lost hull's wreck has struck by now (the struck point is only owed once it has). */
        boolean wreckStruck(Warship s, BattleSimulation sim) {
            for (WarshipWreck w : sim.warships().wrecks()) {
                if (w.ship == s) return w.phase() != WarshipWreck.Phase.FALLING;
            }
            return false;
        }
    }
}
