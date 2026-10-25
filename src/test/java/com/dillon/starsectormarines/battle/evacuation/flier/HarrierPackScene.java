package com.dillon.starsectormarines.battle.evacuation.flier;

import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.HitLog;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretRole;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A pack of Rav fliers attacks a gun line (`colony-breach-nouns.md`, Harrier).
 *
 * <p>The line stands at the west of an open field: two heavy cannon (which cannot track a flier), a heavy machine gun
 * between them, and a six-marine rifle section in front. Ten Harriers are released forty cells east. They sense the
 * line, loiter out of its reach, and dive.
 *
 * <ul>
 *   <li>{@code with-aa}: a flak emplacement stands behind the line.</li>
 *   <li>{@code no-aa}: the same line without it, the control. The rule that a pack only flies over a map with
 *       anti-air binds missions; this loop breaks it on purpose.</li>
 * </ul>
 *
 * <p>Verdicts assert the mechanics: every dive's strikes land in the place the pack committed to (within
 * {@link HarrierRules#SPREAD_RADIUS} of the body it chose, each diver on its own body there); a flier that
 * went low climbed back out; nothing whose weapon cannot track a flier ever held one as a target or hit one; and,
 * with anti-air, the flak hit a flier once one came into its reach. Who won, how many fliers died and what the line
 * lost are metrics.
 */
public final class HarrierPackScene implements BehaviorScene {

    static final int W = 90;
    static final int H = 60;
    static final int LINE_X = 22;
    static final int LINE_Y = 30;
    static final int PACK = 10;
    /** How far a struck body may have moved from the place the pack chose by the time it is bitten. */
    static final float AREA_SLACK_CELLS = 3f;
    static final int TICKS = 1800;
    static final String FLAK = "structure.turret-anti-air-flak";
    static final String CANNON = "structure.hardpoint-cannon";
    static final String MG = "structure.turret-heavy-mg";

    private final HarrierLook look;

    public HarrierPackScene() {
        this(HarrierLook.DEFAULT);
    }

    HarrierPackScene(HarrierLook look) {
        this.look = look;
    }

    @Override public String id() { return "harrier-pack"; }

    @Override public String label() { return "A Harrier pack loiters, commits and dives on a gun line, with and without anti-air"; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        return List.of(run("with-aa", true, frames), run("no-aa", false, frames));
    }

    /** The line's emplacements: two heavy cannon, a machine gun between them, and the flak behind when asked. */
    static long[] spawnGuns(BattleSimulation sim, float x, float y, boolean antiAir) {
        List<Long> guns = new ArrayList<>();
        guns.add(sim.spawn(MapTurret.create("cannon-n", Faction.MARINE, CANNON, (int) x, (int) y - 6)));
        guns.add(sim.spawn(MapTurret.create("mg", Faction.MARINE, MG, (int) x, (int) y)));
        guns.add(sim.spawn(MapTurret.create("cannon-s", Faction.MARINE, CANNON, (int) x, (int) y + 6)));
        if (antiAir) guns.add(sim.spawn(MapTurret.create("flak", Faction.MARINE, FLAK, (int) x - 5, (int) y)));
        long[] out = new long[guns.size()];
        for (int i = 0; i < out.length; i++) out[i] = guns.get(i);
        return out;
    }

    private SceneReport run(String loop, boolean antiAir, FrameSink frames) {
        SceneBuilder builder = SceneBuilder.openGround(W, H).seed(20261023L).missionCompletion(false);
        builder.squad("rifles").faction(Faction.MARINE).size(6).at(LINE_X + 4, LINE_Y).primary("weapon.field-rifle")
                .stationary().done();
        SceneWorld scene = builder.build();
        try (BattleSimulation sim = scene.sim()) {
            long[] guns = spawnGuns(sim, LINE_X, LINE_Y, antiAir);
            HitLog log = sim.telemetry().hitLog();
            log.start();
            HarrierPack pack = sim.swarmFliers().release(sim, look, LINE_X + 40, LINE_Y, PACK, 0L);
            Trace trace = new Trace(sim, guns);
            for (int tick = 0; tick < TICKS; tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                trace.observe(sim);
                if (tick % 10 == 0) {
                    frames.frame(loop, sim, tick, "harrier pack / " + loop + " / " + tick + " / "
                            + phaseOf(sim));
                }
            }
            return trace.report(id(), loop, antiAir, sim, log);
        }
    }

    private static String phaseOf(BattleSimulation sim) {
        StringBuilder b = new StringBuilder();
        for (HarrierPack p : sim.swarmFliers().packs()) b.append(p.phase()).append(' ').append(p.members.size()).append(' ');
        return b.toString().trim();
    }

    /** What the run did, read off the fliers' own events and the hit log. */
    static final class Trace {
        final long[] guns;
        final Map<Long, Integer> lowSince = new HashMap<>();
        int lows, recoveries, stuckLow;
        int heldByNonTracker;
        long flak;
        float flakNearest = Float.MAX_VALUE;
        float flakRange;

        Trace(BattleSimulation sim, long[] guns) {
            this.guns = guns;
            for (long g : guns) {
                if (sim.turretState().structure(g).targetRole == TurretRole.AA) {
                    flak = g;
                    flakRange = sim.turretState().structure(g).mount.weapon.range;
                }
            }
        }

        /** Events read so far, where each pack's dive was aimed, and the bodies each dive struck. */
        int eventsRead;
        final Map<Integer, float[]> aimedAt = new HashMap<>();
        final Map<Integer, Set<Long>> struckThisDive = new HashMap<>();
        int strikesOutside, dives, spreadDives;

        void observe(BattleSimulation sim) {
            int tick = sim.getSimTickIndex();
            List<SwarmFliers.Event> events = sim.swarmFliers().events();
            for (; eventsRead < events.size(); eventsRead++) {
                SwarmFliers.Event e = events.get(eventsRead);
                switch (e.kind()) {
                    case COMMIT -> {
                        aimedAt.put(e.pack(), new float[]{sim.world().x(e.target()), sim.world().y(e.target())});
                        struckThisDive.put(e.pack(), new HashSet<>());
                    }
                    case STRIKE -> {
                        float[] at = aimedAt.get(e.pack());
                        float d = at == null ? Float.MAX_VALUE
                                : (float) Math.hypot(sim.world().x(e.target()) - at[0], sim.world().y(e.target()) - at[1]);
                        if (d > HarrierRules.SPREAD_RADIUS + AREA_SLACK_CELLS) strikesOutside++;
                        Set<Long> struck = struckThisDive.get(e.pack());
                        if (struck != null) struck.add(e.target());
                    }
                    case PULL_UP_DONE -> {
                        Set<Long> struck = struckThisDive.remove(e.pack());
                        if (struck != null && !struck.isEmpty()) {
                            dives++;
                            if (struck.size() > 1) spreadDives++;
                        }
                    }
                    default -> { }
                }
            }
            for (HarrierPack p : sim.swarmFliers().packs()) {
                for (HarrierFlight m : p.members) {
                    Integer since = lowSince.get(m.id);
                    if (m.altitude <= HarrierRules.LOW_ALTITUDE && since == null) {
                        lowSince.put(m.id, tick);
                        lows++;
                    } else if (since != null && m.altitude >= 0.9f) {
                        lowSince.remove(m.id);
                        recoveries++;
                    }
                    if (flak != 0L && sim.world().isAlive(flak)) {
                        float d = (float) Math.hypot(m.body.x - sim.world().x(flak), m.body.y - sim.world().y(flak));
                        flakNearest = Math.min(flakNearest, d);
                    }
                }
            }
            for (long g : guns) {
                if (!sim.world().isAlive(g)) continue;
                long t = sim.targetOf(g);
                if (t != 0L && sim.identity().has(t) && sim.identity().type(t).isFlier() && !tracks(sim, g)) {
                    heldByNonTracker++;
                }
            }
            for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                long u = sim.liveUnitAt(i);
                if (sim.identity().faction(u) != Faction.MARINE || sim.turretState().isTurret(u)) continue;
                long t = sim.targetOf(u);
                if (t != 0L && sim.identity().has(t) && sim.identity().type(t).isFlier() && !tracks(sim, u)) {
                    heldByNonTracker++;
                }
            }
        }

        static boolean tracks(BattleSimulation sim, long shooter) {
            if (sim.turretState().isTurret(shooter)) {
                var s = sim.turretState().structure(shooter);
                return s.targetRole == TurretRole.AA || s.mount.weapon.tracksFliers();
            }
            WeaponDef w = sim.combat().primaryWeapon(shooter);
            return w != null && w.tracksFliers();
        }

        SceneReport report(String sceneId, String loop, boolean antiAir, BattleSimulation sim, HitLog log) {
            List<SwarmFliers.Event> events = sim.swarmFliers().events();
            int commits = 0, strikes = 0;
            for (SwarmFliers.Event e : events) {
                if (e.kind() == SwarmFliers.Event.Kind.COMMIT) commits++;
                if (e.kind() == SwarmFliers.Event.Kind.STRIKE) strikes++;
            }
            int limitTicks = Math.round((HarrierRules.MAX_DIVE_SECONDS + HarrierRules.PULL_UP_SECONDS + 3f) * 30f);
            int now = sim.getSimTickIndex();
            for (Map.Entry<Long, Integer> e : lowSince.entrySet()) {
                if (sim.world().isAlive(e.getKey()) && now - e.getValue() > limitTicks) stuckLow++;
            }
            int hitsOnFliers = 0, hitsByNonTracker = 0, aaHits = 0, killed = 0, killedByAa = 0;
            float lineDamage = 0f;
            for (HitLog.Hit h : log.hits()) {
                boolean onFlier = h.targetLabel().endsWith(UnitType.SWARM_HARRIER.name());
                if (onFlier) {
                    hitsOnFliers++;
                    boolean aa = h.attacker() == flak;
                    if (aa) aaHits++;
                    if (h.died()) { killed++; if (aa) killedByAa++; }
                    // A shooter that has since died has no weapon left to ask about.
                    if (sim.combat().has(h.attacker()) && !tracks(sim, h.attacker())) hitsByNonTracker++;
                } else if (h.attackerLabel().endsWith(UnitType.SWARM_HARRIER.name())) {
                    lineDamage += h.applied();
                }
            }
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("strikes-where-it-committed", commits > 0 && strikesOutside == 0,
                    commits + " commits, " + strikes + " strikes, " + strikesOutside
                            + " outside the place the pack chose"));
            verdicts.add(Verdict.of("dives-and-pulls-up", lows > 0 && stuckLow == 0,
                    lows + " fliers went low, " + recoveries + " climbed back to loiter height, "
                            + stuckLow + " stayed low past a dive and a pull-up"));
            verdicts.add(Verdict.of("only-air-capable-weapons", heldByNonTracker == 0 && hitsByNonTracker == 0,
                    heldByNonTracker + " ticks a non-tracking weapon held a flier, " + hitsByNonTracker
                            + " hits on fliers from one"));
            if (antiAir) {
                boolean inReach = flakNearest <= flakRange;
                verdicts.add(Verdict.of("aa-hits-it", !inReach || aaHits > 0,
                        "nearest flier came " + Math.round(flakNearest) + " cells from the flak (reach "
                                + Math.round(flakRange) + "); " + aaHits + " flak hits on fliers"));
            }
            int alive = 0;
            for (HarrierPack p : sim.swarmFliers().packs()) alive += p.members.size();
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("commits", commits);
            metrics.put("strikes", strikes);
            metrics.put("divesThatStruck", dives);
            metrics.put("divesSpreadOverSeveralBodies", spreadDives);
            metrics.put("hitsOnFliers", hitsOnFliers);
            metrics.put("aaHits", aaHits);
            metrics.put("fliersKilled", killed);
            metrics.put("fliersKilledByAa", killedByAa);
            metrics.put("fliersLeft", alive);
            metrics.put("packsLeft", sim.swarmFliers().packs().size());
            metrics.put("lineDamageTaken", lineDamage);
            int gunsLeft = 0;
            for (long g : guns) if (sim.world().isAlive(g)) gunsLeft++;
            metrics.put("gunsLeft", gunsLeft);
            return new SceneReport(sceneId, loop, TICKS, verdicts, metrics);
        }
    }
}
