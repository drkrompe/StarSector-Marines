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
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What a hull fight's hits look like ({@link WarshipHitFx}): one target hull under the guns of two destroyers whose
 * fits between them carry all four of the base game's damage types (an Enforcer's sabots, reapers, heavy autocannons
 * and mortar; a Sunder's pulse lasers, autopulse, needlers and vulcan), held close enough that every round is in the
 * frame.
 * <ul>
 *   <li>{@code omni-held}: a Medusa holding its omni shield up until the pool fills: hits on the bubble, the shield
 *       straining, its collapse in the overload, the arcs of the overload, and then the hull.</li>
 *   <li>{@code front-auto}: a Dominator on its automatic front shield, facing its attackers: hits on the arc until the
 *       shield locks itself out at its drop level, then on the plate.</li>
 *   <li>{@code hull-breach}: a Hammerhead with its shield held down, under way across the attackers' front: hull hits,
 *       breaches, mounts lost, the holes venting and the smoke it trails.</li>
 * </ul>
 * Verdicts are the presentation's mechanics: every round a shield absorbs leaves a mark on its bubble and none on the
 * hull, every round that reaches the plate leaves one on the hull, a breach mark only where a cell was holed, and a
 * collapse or lockout mark when a shield goes. Damage, flux, who fires and when are the simulation's and are only
 * read; how far the fight gets in the window is a reading.
 */
public final class WarshipHitFxScene implements BehaviorScene {

    /** {@code -Dbattle.scene.warshipHitFx.ticks=N} plays every loop for N ticks instead of its own length. */
    private static final int TICKS_OVERRIDE = Integer.getInteger("battle.scene.warshipHitFx.ticks", 0);
    private static final int FRAME_EVERY = 6;
    private static final int WIDTH = 300;
    private static final int HEIGHT = 200;
    /** Attackers stand at this share of their longest reach from the target, so every gun has it. */
    private static final float STANDOFF_SHARE = 0.55f;

    @Override public String id() { return "warship-hit-fx"; }

    @Override public String label() {
        return "Warship hit effects: rounds on a shield light the bubble and skip off it, rounds on the plate burn,"
                + " shatter and hole it, and a shield under strain shows it";
    }

    @Override public int frameDelayMillis() {
        return 150;
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : loops()) reports.add(playOne(loop, frames));
        return reports;
    }

    static List<String> loops() {
        String only = System.getProperty("battle.scene.warshipHitFx.only", "");
        List<String> all = List.of("omni-held", "front-auto", "hull-breach");
        if (only.isBlank()) return all;
        List<String> picked = new ArrayList<>();
        for (String l : all) if (List.of(only.split(",")).contains(l)) picked.add(l);
        return picked;
    }

    /** Where the target starts, for a close view that follows it. */
    static float[] targetStart() {
        return new float[]{WIDTH * 0.58f, HEIGHT * 0.5f};
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        Warship target = switch (loop) {
            case "omni-held" -> Warship.fit("medusa", "medusa_CS", Faction.DEFENDER, 0f, 0f, 270f);
            case "front-auto" -> Warship.fit("dominator", "dominator_Assault", Faction.DEFENDER, 0f, 0f, 90f);
            default -> Warship.fit("hammerhead", "hammerhead_Balanced", Faction.DEFENDER, 0f, 0f, 0f);
        };
        Warship enforcer = Warship.fit("enforcer", "enforcer_Assault", Faction.ALLY, 0f, 0f, 270f);
        Warship sunder = Warship.fit("sunder", "sunder_Assault", Faction.ALLY, 0f, 0f, 270f);
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        builder.squad("ally-ground").faction(Faction.MARINE).size(1).at(3, 3).unarmed().stationary().done();
        builder.squad("defender-ground").faction(Faction.DEFENDER).size(1).at(WIDTH - 4, HEIGHT - 4).unarmed()
                .stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261019L);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) sim.getTopology().setGroundKind(x, y, GroundKind.GRASS);
        }
        float[] at = targetStart();
        place(target, at[0], at[1]);
        // The attackers stand off to the west, one either side of the target's line, so hits come in from two bearings.
        float reachE = WarshipShipFire.longestReach(enforcer) * STANDOFF_SHARE;
        float reachS = WarshipShipFire.longestReach(sunder) * STANDOFF_SHARE;
        place(enforcer, at[0] - reachE * 0.94f, at[1] + reachE * 0.34f);
        place(sunder, at[0] - reachS * 0.94f, at[1] - reachS * 0.34f);
        switch (loop) {
            case "omni-held" -> {
                target.raiseShield(true);
                target.fluxScale(Warship.BRAWLER_FLUX_SCALE);
            }
            case "front-auto" -> {
                target.shieldAuto(true);
                target.fluxScale(Warship.BRAWLER_FLUX_SCALE);
            }
            default -> {
                target.raiseShield(false);
                target.duelHelm(false);
                // Under way across the attackers' front, so the smoke it trails is seen.
                target.stationTo(at[0] + 4f, at[1] + 70f);
            }
        }
        sim.addWarship(target);
        sim.addWarship(enforcer);
        sim.addWarship(sunder);
        Trace trace = new Trace(target);
        int ticks = TICKS_OVERRIDE > 0 ? TICKS_OVERRIDE : ticks(loop);
        // Frames only from just before the loop's first event, so the recording is the part worth watching.
        int from = recordFrom(loop);
        FrameSink late = new FrameSink() {
            @Override public void frame(String id, BattleSimulation s, int tick, String caption) {
                frames.frame(id, s, tick, caption);
            }

            @Override public void advanced(String id, BattleSimulation s, int tick) {
                frames.advanced(id, s, tick);
            }

            @Override public boolean records(String id, BattleSimulation s, int tick, int every) {
                return tick >= from && frames.records(id, s, tick, every);
            }
        };
        SceneRun.of(world).observe(trace)
                .frames(late, loop, FRAME_EVERY, tick -> String.format(Locale.ROOT,
                        "%s / %s shield %s / flux %d%% / hull %d%% / breaches %d / tick %d", loop, target.hullId,
                        target.shieldState(), Math.round(target.fluxFraction() * 100f),
                        Math.round(target.hullFraction() * 100f),
                        target.plating() == null ? 0 : target.plating().breachCount(), tick))
                .run(ticks);
        return report(loop, trace, ticks);
    }

    private static void place(Warship ship, float x, float y) {
        ship.x = x;
        ship.y = y;
        ship.stationTo(x, y);
        ship.limitFlux();
        ship.shipFire(ShipFireOrder.ALL);
        ship.groundFire(Warship.GroundFire.HOLD);
    }

    /** How long a loop plays: past its shield's collapse or lockout and some plate hits, or past its first breaches. */
    static int ticks(String loop) {
        return switch (loop) {
            case "omni-held" -> 630;
            case "front-auto" -> 540;
            default -> 900;
        };
    }

    /** The first tick a loop records: the first rounds arrive about tick 90; the Hammerhead's plate gives near 500. */
    static int recordFrom(String loop) {
        return loop.equals("hull-breach") ? 360 : 60;
    }

    private SceneReport report(String loop, Trace t, int ticks) {
        List<Verdict> verdicts = new ArrayList<>();
        Warship s = t.target;
        verdicts.add(Verdict.of("every-absorbed-round-marks-the-shield",
                t.shieldMarks == t.shieldHits && (t.shieldMarks > 0 || !loop.equals("omni-held")),
                t.shieldMarks + " shield marks for " + t.shieldHits + " absorbed hits"));
        verdicts.add(Verdict.of("shield-marks-lie-on-the-bubble", t.offBubble == 0,
                t.offBubble + " of " + t.shieldMarks + " shield marks off the bubble's radius"));
        verdicts.add(Verdict.of("every-plate-hit-marks-the-hull", t.hullMarks == t.hullHits,
                t.hullMarks + " hull and breach marks for " + t.hullHits + " hits past the shield"));
        verdicts.add(Verdict.of("breach-marks-only-where-a-cell-was-holed",
                t.breachMarks <= t.breachEvents && (t.breachEvents == 0 || t.breachMarks > 0),
                t.breachMarks + " breach marks for " + t.breachEvents + " cells holed"));
        if (s.shieldCollapses() > 0) {
            verdicts.add(Verdict.of("a-collapse-shatters-the-shield", t.byKind.get(WarshipHitFx.Kind.SHIELD_COLLAPSE)
                            >= 1,
                    t.byKind.get(WarshipHitFx.Kind.SHIELD_COLLAPSE) + " collapse marks, " + s.shieldCollapses()
                            + " collapses"));
        }
        if (t.lockouts > 0) {
            verdicts.add(Verdict.of("a-lockout-gutters-the-shield",
                    t.byKind.get(WarshipHitFx.Kind.SHIELD_LOCKOUT) >= 1,
                    t.byKind.get(WarshipHitFx.Kind.SHIELD_LOCKOUT) + " lockout marks for " + t.lockouts
                            + " lockouts"));
        }
        if (loop.equals("hull-breach")) {
            verdicts.add(Verdict.of("a-holed-ship-under-way-trails-smoke",
                    t.breachEvents == 0 || t.maxPuffs > 0,
                    t.breachEvents == 0 ? "the hull was never holed in the window (a reading, not a failure)"
                            : t.maxPuffs + " puffs at most in the air"));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        for (Map.Entry<WarshipHitFx.Kind, Integer> e : t.byKind.entrySet()) {
            metrics.put("marks." + e.getKey().name().toLowerCase(Locale.ROOT), e.getValue());
        }
        for (Map.Entry<WarshipHitFx.Character, Integer> e : t.byCharacter.entrySet()) {
            metrics.put("character." + e.getKey().name().toLowerCase(Locale.ROOT), e.getValue());
        }
        metrics.put("firstShieldMarkTick", t.firstShield);
        metrics.put("firstHullMarkTick", t.firstHull);
        metrics.put("firstBreachTick", t.firstBreach);
        metrics.put("collapseTick", t.collapseTick);
        metrics.put("lockoutTick", t.lockoutTick);
        metrics.put("maxLiveMarks", t.maxMarks);
        metrics.put("maxPuffs", t.maxPuffs);
        metrics.put("hullFraction", s.hullFraction());
        metrics.put("breaches", s.plating() == null ? 0 : s.plating().breachCount());
        metrics.put("mountsKnockedOut", s.mountsKnockedOut());
        return new SceneReport(id(), loop, ticks, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        final Warship target;
        private final Set<WarshipHitFx.Mark> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<WarshipHitFx.Kind, Integer> byKind = new EnumMap<>(WarshipHitFx.Kind.class);
        final Map<WarshipHitFx.Character, Integer> byCharacter = new EnumMap<>(WarshipHitFx.Character.class);
        int shieldMarks;
        int hullMarks;
        /** The target's own counts at the last look, which saw the marks of the same ticks. */
        int shieldHits;
        int hullHits;
        int breachMarks;
        /** Cells holed in the window, counted as the plating reports them. */
        int breachEvents;
        int offBubble;
        int lockouts;
        int firstShield = -1;
        int firstHull = -1;
        int firstBreach = -1;
        int collapseTick = -1;
        int lockoutTick = -1;
        int maxMarks;
        int maxPuffs;
        private int lastBreaches;
        private boolean wasLocked;

        Trace(Warship target) {
            this.target = target;
            for (WarshipHitFx.Kind k : WarshipHitFx.Kind.values()) byKind.put(k, 0);
            for (WarshipHitFx.Character c : WarshipHitFx.Character.values()) byCharacter.put(c, 0);
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            WarshipHitFx fx = sim.warships().hitFx();
            shieldHits = target.shieldHits();
            hullHits = target.hullHits();
            maxMarks = Math.max(maxMarks, fx.marks().size());
            maxPuffs = Math.max(maxPuffs, fx.puffs().size());
            for (WarshipHitFx.Mark m : fx.marks()) {
                if (m.ship() != target || !seen.add(m)) continue;
                byKind.merge(m.kind(), 1, Integer::sum);
                switch (m.kind()) {
                    case SHIELD -> {
                        shieldMarks++;
                        byCharacter.merge(m.character(), 1, Integer::sum);
                        if (firstShield < 0) firstShield = tick;
                        if (!onBubble(m)) offBubble++;
                    }
                    case HULL, BREACH -> {
                        hullMarks++;
                        byCharacter.merge(m.character(), 1, Integer::sum);
                        if (firstHull < 0) firstHull = tick;
                        if (m.kind() == WarshipHitFx.Kind.BREACH) {
                            breachMarks++;
                            if (firstBreach < 0) firstBreach = tick;
                        }
                    }
                    case SHIELD_COLLAPSE -> {
                        if (collapseTick < 0) collapseTick = tick;
                    }
                    case SHIELD_LOCKOUT -> {
                        if (lockoutTick < 0) lockoutTick = tick;
                    }
                    default -> { }
                }
            }
            int breaches = target.plating() == null ? 0 : target.plating().breachCount();
            // One round may hole several cells, never fewer than one: so no more breach marks than cells holed.
            if (breaches > lastBreaches) breachEvents += breaches - lastBreaches;
            lastBreaches = breaches;
            boolean locked = target.shieldLockedOut();
            if (locked && !wasLocked) lockouts++;
            wasLocked = locked;
        }

        /** Whether a shield mark, put back in the world at the hull's present heading, lies on its bubble. */
        private boolean onBubble(WarshipHitFx.Mark m) {
            double r = Math.toRadians(target.facingDegrees);
            float wx = target.centerX() + m.localX() * (float) Math.cos(r) - m.localY() * (float) Math.sin(r);
            float wy = target.centerY() + m.localX() * (float) Math.sin(r) + m.localY() * (float) Math.cos(r);
            float d = (float) Math.hypot(wx - target.shieldCentreX(), wy - target.shieldCentreY());
            return d <= target.shieldRadiusCells() * 1.001f + 0.01f;
        }
    }
}
