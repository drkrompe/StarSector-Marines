package com.dillon.starsectormarines.battle.warship;

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
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * An Onslaught working an assault: three knots of defenders, an objective, and
 * marines standing where they must not be shelled.
 *
 * <p>The objective knot (A) is where the assault squad has been sent; the decoy
 * (B) is denser and elsewhere; a third (C) is the densest of all and has the
 * company's own people standing in it. {@code control} is the shipped director
 * (no stance): it takes the densest ground and shells its own. {@code attacker}
 * is stance ATTACK: it takes the objective first, never the dirty knot, and
 * holds fire for friendlies once that is all that is left, saying so.
 * {@code danger-close} is the same world with the mission's flag raised: the
 * only difference is the flag, and it fires into its own people.
 * {@code stance-flip} has two equal knots, one the assault's objective and one
 * the ground a holding squad defends, and the mission flips the stance from
 * ATTACK to DEFEND half way: where the ship fires must follow.
 */
public final class WarshipSupportFireScene implements BehaviorScene {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 90;
    private static final int TICKS = 3600;
    private static final float SHIP_X = 38f;
    private static final float SHIP_Y = 40f;
    private static final float AREA_RADIUS = 8f;
    /** The objective knot, the decoy, the dirty knot. */
    private static final int[] A = {80, 20};
    private static final int[] B = {80, 60};
    private static final int[] C = {100, 40};
    private static final int FLIP_TICK = 300;
    private static final int SETTLE_TICKS = 90;
    /** Defenders in each of the two big knots of the flip loop, laid out twenty to a row. */
    private static final int BIG_KNOT = 400;
    private static final float NEAR = 14f;

    @Override public String id() { return "warship-support-fire"; }

    @Override public String label() {
        return "Warship support fire: an attacking ship takes the objective before a denser decoy, never shells its own, "
                + "holds fire when only dirty ground is left, fires on danger-close, and follows a stance flip";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("control", "attacker", "danger-close", "stance-flip")) {
            reports.add(report(loop, playOne(loop, frames)));
        }
        return reports;
    }

    private record Run(Trace trace, Warship ship) {}

    private static void knot(SceneBuilder builder, String prefix, int[] centre, int count, Faction faction, int perRow) {
        for (int i = 0; i < count; i++) {
            builder.unit(prefix + i, faction, UnitType.MARINE, centre[0] + (i % perRow) - perRow / 2, centre[1] + (i / perRow) - count / perRow / 2);
        }
    }

    private Run playOne(String loop, FrameSink frames) throws Exception {
        boolean flip = loop.equals("stance-flip");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        // The company's own squads: where the assault was sent, and ground a holding squad defends.
        builder.squad("assault").faction(Faction.MARINE).size(2).at(12, 20).unarmed().stationary()
                .assigned(id -> ObjectiveAssignment.attackMove(id, A[0], A[1])).done();
        builder.squad("holding").faction(Faction.MARINE).size(2).at(12, 60).unarmed().stationary()
                .assigned(id -> ObjectiveAssignment.defendTrack(id, B[0], B[1])).done();
        if (flip) {
            knot(builder, "a", A, BIG_KNOT, Faction.DEFENDER, 20);
            knot(builder, "b", B, BIG_KNOT, Faction.DEFENDER, 20);
        } else {
            knot(builder, "a", A, 10, Faction.DEFENDER, 6);
            knot(builder, "b", B, 24, Faction.DEFENDER, 6);
            knot(builder, "c", C, 30, Faction.DEFENDER, 6);
            // The company's own, standing in the middle of the dirty knot.
            for (int i = 0; i < 4; i++) {
                builder.unit("f" + i, Faction.MARINE, UnitType.MARINE, C[0] - 1 + (i % 2) * 2, C[1] - 1 + (i / 2) * 2);
            }
        }
        builder.unit("far-enemy", Faction.DEFENDER, UnitType.MARINE, 150, 85);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261011L);
        Warship ship = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, SHIP_X, SHIP_Y, -90f);
        ship.stationTo(SHIP_X, SHIP_Y);
        ship.direct(AREA_RADIUS, true, 1);
        if (!loop.equals("control")) {
            ship.useDoctrine();
            ship.stance(MissionStance.ATTACK);
        }
        if (loop.equals("danger-close")) ship.dangerClose(true);
        sim.addWarship(ship);
        Trace trace = new Trace(world, ship, flip);
        TickObserver script = (s, tick) -> {
            if (flip && tick == FLIP_TICK) ship.stance(MissionStance.DEFEND);
        };
        SceneRun.of(world).observe(script, trace)
                .frames(frames, loop, 6, tick -> loop + " / " + ship.posture().label() + " / tick " + tick)
                .run(TICKS);
        return new Run(trace, ship);
    }

    private SceneReport report(String loop, Run run) {
        Trace t = run.trace();
        Warship ship = run.ship();
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case "control" -> {
                verdicts.add(Verdict.of("takes-the-densest-ground", t.firstAreaNear(C) ,
                        "first area " + t.firstArea() + ", dirty knot at " + C[0] + "," + C[1]));
                verdicts.add(Verdict.of("shells-its-own", t.friendlyDeaths() > 0,
                        t.friendlyDeaths() + " of 4 friendlies killed"));
                verdicts.add(Verdict.of("no-doctrine-no-posture-change", ship.postureTransitions() == 0,
                        ship.postureTransitions() + " transitions"));
            }
            case "attacker" -> {
                verdicts.add(Verdict.of("is-in-support", ship.posture() == WarshipPosture.SUPPORT,
                        ship.posture() + ": " + ship.postureReason()));
                verdicts.add(Verdict.of("fires-on-the-objective-first", t.firstAreaNear(A),
                        "first area " + t.firstArea() + ", objective at " + A[0] + "," + A[1]));
                verdicts.add(Verdict.of("objective-before-the-denser-decoy",
                        t.firstDeath("a") >= 0 && (t.firstDeath("b") < 0 || t.firstDeath("a") < t.firstDeath("b")),
                        "first death at the objective tick " + t.firstDeath("a") + ", at the decoy tick " + t.firstDeath("b")));
                verdicts.add(Verdict.of("kills-no-friendly", t.friendlyDeaths() == 0,
                        t.friendlyDeaths() + " friendlies killed"));
                verdicts.add(Verdict.of("leaves-the-dirty-knot-alone", t.deaths("c") == 0,
                        t.deaths("c") + " of 30 killed in the knot with friendlies in it"));
                verdicts.add(Verdict.of("holds-fire-and-says-why",
                        ship.holdingForFriendlies() && ship.friendlyRefusals() > 0
                                && DebugWarship.statusLine(ship).contains("held for friendlies"),
                        ship.friendlyRefusals() + " refusals; " + DebugWarship.statusLine(ship)));
                verdicts.add(Verdict.of("clears-the-clean-ground", t.deaths("a") >= 9 && t.deaths("b") >= 20,
                        t.deaths("a") + " of 10 at the objective, " + t.deaths("b") + " of 24 at the decoy"));
            }
            case "danger-close" -> {
                verdicts.add(Verdict.of("still-takes-the-objective-first", t.firstAreaNear(A),
                        "first area " + t.firstArea()));
                verdicts.add(Verdict.of("fires-into-the-dirty-knot", t.deaths("c") > 0,
                        t.deaths("c") + " of 30 killed there"));
                verdicts.add(Verdict.of("the-flag-is-the-only-difference", t.friendlyDeaths() > 0,
                        t.friendlyDeaths() + " friendlies killed, against none without the flag"));
                verdicts.add(Verdict.of("not-held-for-friendlies", !ship.holdingForFriendlies(),
                        "holding: " + ship.holdingForFriendlies()));
            }
            default -> {
                float before = t.shareLandingNear(A, SETTLE_TICKS, FLIP_TICK);
                float after = t.shareLandingNear(B, FLIP_TICK + SETTLE_TICKS, FLIP_TICK + SETTLE_TICKS + 240);
                verdicts.add(Verdict.of("attacking-fires-on-the-objective", before >= 0.8f,
                        String.format(Locale.ROOT, "%.0f%% of rounds before the flip landed near the objective", before * 100f)));
                verdicts.add(Verdict.of("defending-fires-on-the-held-ground", after >= 0.8f,
                        String.format(Locale.ROOT, "%.0f%% of the rounds in the four seconds after the flip settled landed near the defended ground", after * 100f)));
                verdicts.add(Verdict.of("posture-follows-the-stance",
                        t.postureAt(FLIP_TICK - 1) == WarshipPosture.SUPPORT && ship.posture() == WarshipPosture.INTERCEPT,
                        t.postureAt(FLIP_TICK - 1) + " then " + ship.posture()));
                verdicts.add(Verdict.of("no-flapping", ship.postureTransitions() == 2,
                        ship.postureTransitions() + " transitions"));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("deathsA", t.deaths("a"));
        metrics.put("deathsB", t.deaths("b"));
        metrics.put("deathsC", t.deaths("c"));
        metrics.put("friendlyDeaths", t.friendlyDeaths());
        metrics.put("refusals", ship.friendlyRefusals());
        metrics.put("firstShellTick", t.landings.isEmpty() ? -1 : (int) t.landings.get(0)[0]);
        metrics.put("firstDeathA", t.firstDeath("a"));
        metrics.put("lastDeathA", t.lastDeath("a"));
        metrics.put("firstDeathB", t.firstDeath("b"));
        metrics.put("lastDeathB", t.lastDeath("b"));
        metrics.put("firstDeathC", t.firstDeath("c"));
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        private final Warship ship;
        private final boolean flip;
        private final Map<String, Integer> deaths = new LinkedHashMap<>();
        private final Set<WarshipShell> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        final List<float[]> landings = new ArrayList<>();
        private final WarshipPosture[] posture = new WarshipPosture[TICKS + 2];
        private float[] firstArea;

        private Trace(SceneWorld world, Warship ship, boolean flip) {
            this.world = world;
            this.ship = ship;
            this.flip = flip;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            posture[Math.min(tick, TICKS + 1)] = ship.posture();
            if (firstArea == null && !ship.areas().isEmpty()) {
                WarshipOrder.FireOnArea a = ship.areas().get(0);
                firstArea = new float[]{a.x(), a.y()};
            }
            String[] prefixes = flip ? new String[]{"a", "b"} : new String[]{"a", "b", "c", "f"};
            for (String prefix : prefixes) {
                int n = flip ? BIG_KNOT : switch (prefix) {
                    case "a" -> 10;
                    case "b" -> 24;
                    case "c" -> 30;
                    default -> 4;
                };
                for (int i = 0; i < n; i++) {
                    String key = prefix + i;
                    if (deaths.containsKey(key) || sim.getRoster().isAliveById(world.unit(key))) continue;
                    deaths.put(key, tick);
                }
            }
            for (WarshipShell shell : sim.warships().shells()) {
                if (seen.add(shell)) landings.add(new float[]{tick, shell.toX, shell.toY});
            }
        }

        WarshipPosture postureAt(int tick) {
            return posture[Math.min(tick, TICKS + 1)];
        }

        String firstArea() {
            return firstArea == null ? "none" : String.format(Locale.ROOT, "(%.0f, %.0f)", firstArea[0], firstArea[1]);
        }

        boolean firstAreaNear(int[] at) {
            return firstArea != null && Math.hypot(firstArea[0] - at[0], firstArea[1] - at[1]) <= NEAR;
        }

        int deaths(String prefix) {
            int n = 0;
            for (String key : deaths.keySet()) if (key.startsWith(prefix)) n++;
            return n;
        }

        int friendlyDeaths() {
            return deaths("f");
        }

        int firstDeath(String prefix) {
            int first = -1;
            for (Map.Entry<String, Integer> e : deaths.entrySet()) {
                if (e.getKey().startsWith(prefix) && (first < 0 || e.getValue() < first)) first = e.getValue();
            }
            return first;
        }

        int lastDeath(String prefix) {
            int last = -1;
            for (Map.Entry<String, Integer> e : deaths.entrySet()) {
                if (e.getKey().startsWith(prefix) && e.getValue() > last) last = e.getValue();
            }
            return last;
        }

        float shareLandingNear(int[] at, int fromTick, int toTick) {
            int total = 0;
            int near = 0;
            for (float[] l : landings) {
                if (l[0] < fromTick || l[0] >= toTick) continue;
                total++;
                if (Math.hypot(l[1] - at[0], l[2] - at[1]) <= NEAR) near++;
            }
            return total == 0 ? 0f : (float) near / total;
        }
    }
}
