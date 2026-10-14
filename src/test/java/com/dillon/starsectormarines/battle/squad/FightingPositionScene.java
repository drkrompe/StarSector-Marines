package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.ScriptedPlayer;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A squad in a trench under rifle fire: <b>does digging in buy composure, and only composure?</b>
 *
 * <p>Fighting positions pay out in one thing cover does not, which is the morale a hit or a near miss
 * costs the marine's fire team (see {@link DigInSystem}). A claim that the bonus is "composure and not
 * protection" is a claim about a comparison, so the scene is built as one: the same trench, the same
 * twelve marines standing on the same cells, the same rooted rifle line firing at them, played once with
 * fighting positions on ({@code subject}) and once with them off ({@code control}).
 *
 * <h2>The comparison has to be fair, or it measures the ground</h2>
 * <p>With the toggle off there are no claims, so a squad ordered to defend an area would not choose the
 * trench by preference and the loops would differ by where the marines stood rather than by the bonus.
 * Both loops therefore <em>spawn</em> the squad on the trench cells; {@code same-ground} then measures the
 * share of marine-ticks spent on position cells in each and refuses the comparison if they disagree by
 * more than a few percent. {@code damage-parity} is the other half: the bonus is not allowed to change how
 * much damage is taken.
 *
 * <h2>A controlled isolation, not a battle outcome</h2>
 * <p>The {@code subject}/{@code control} pair is an instrument for separating the morale bonus from everything
 * else, and it says so by construction: the marines are rooted and carry 1000 health so that neither movement nor
 * casualties can differ between the loops, and the enemy is a rooted cluster played at six placements. What it
 * establishes is that, other things held equal, dug-in fire teams break less. It makes no claim about who wins a
 * battle, how many marines survive, or how much damage a real squad takes; {@code under-fire-free} is the loop
 * with no such supports.
 *
 * <h2>The other loops</h2>
 * <p>{@code under-fire-free} spawns the squad off the trench with ordinary health, free to move, and orders it onto
 * the trench with the enemy firing: after its first dig-in, composed marines must stay on position cells.
 * {@code fill} spawns the squad off the trench and orders it to defend the area on it with nobody
 * firing: every marine should end on a distinct claimed position cell, dug in. {@code overflow} runs a
 * twelve-cell run with a two-marine squad (the lower squad id, so it claims first) and a twelve-marine
 * squad ordered onto the same circle: the claims must be disjoint, ten cells' worth of marines dug in, and
 * the two left over standing on ordinary ground.
 */
public final class FightingPositionScene implements BehaviorScene {

    private static final int WIDTH = 46;
    private static final int HEIGHT = 24;

    /** Thirty seconds: the squad settles, digs in, and takes sustained fire. */
    private static final int TICKS = 900;
    private static final int FRAME_EVERY_TICKS = 15;

    private static final int TRENCH_Y = 12;
    private static final int LONG_TRENCH_X0 = 2;
    private static final int LONG_TRENCH_X1 = 17;
    private static final int SHORT_TRENCH_X0 = 4;
    private static final int SHORT_TRENCH_X1 = 15;

    private static final int SQUAD_SIZE = 12;
    private static final int GUARD_SIZE = 2;
    private static final int OFF_TRENCH_Y = 19;

    /**
     * The enemy rifle lines the under-fire pair is played against, each as {centre x, first row y, size}, a cluster four marines across. One line is one
     * sample, and a single run is dominated by who happens to die first (a death drains a fire team by 0.30,
     * unscaled), so the comparison is read over all of them. Only the first is recorded as frames.
     */
    private static final int[][] ENEMY_LINES = {
        {26, 9, 20}, {27, 9, 20}, {25, 10, 16}, {28, 8, 24}, {26, 9, 18}, {27, 8, 22},
    };

    private static final int POCKET_X = 43;
    private static final int POCKET_Y = 3;

    /**
     * Enough health that nobody dies in the under-fire pair: the comparison is about nerve, and a death drains
     * a fire team by a fixed amount the bonus does not touch.
     */
    private static final float MARINE_HEALTH = 1000f;

    /**
     * The enemy cluster of the free loop: four long-rifle marines on the east edge, 19 to 22 cells from the trench.
     * They are the instrument, rooted and given {@link #ENEMY_HEALTH}, so they are still standing and firing for the
     * whole hold window. Further out (41) they acquired no target at all; nearer (30) the squad broke them.
     */
    private static final int FREE_ENEMY_SIZE = 4;
    private static final int FREE_ENEMY_X = 36;
    private static final int FREE_ENEMY_Y = 10;

    /**
     * The enemy cluster is the instrument here, not the subject, so it is given enough health to still be
     * standing and firing when the squad has dug in; the marines keep ordinary health and freedom.
     */
    private static final float ENEMY_HEALTH = 3000f;

    /** The enemy must be firing at the squad on at least this share of the hold window's ticks. */
    private static final float UNDER_FIRE_BAR = 0.70f;

    /** An enemy round within this many ticks (two seconds) counts as the squad being under fire. */
    private static final int RECENT_FIRE_TICKS = 60;

    /** Share of live, composed marine-ticks after dig-in that must be spent on position cells. */
    private static final float HOLDS_POSITION_BAR = 0.85f;

    private static final String PULSE_RIFLE = "weapon.pulse-rifle";

    /** Reach 32 against the pulse rifle's 24: the free loop's enemy shoots from beyond the marines' own reach, so it keeps firing for as long as it lives. */
    private static final String LONG_RIFLE = "weapon.dmr";

    private static final String MARINES = "marines";
    private static final String GUARD = "guard";
    private static final String ENEMY = "enemy";
    private static final String FAR = "far";

    /** Marines dug in by the end of the subject loop, of twelve. */
    private static final int DUG_IN_BAR = 10;

    /** Subject and control may differ by this much in the share of marine-ticks spent on position cells. */
    private static final float SAME_GROUND_TOLERANCE = 0.05f;

    /**
     * The ground comparison is read over the opening of the fight. Once fire teams start breaking the loops
     * legitimately part company, because a team that keeps its nerve keeps its cell — that is the bonus working,
     * and measuring it as "different ground" would call the effect a confound.
     */
    private static final int SAME_GROUND_WINDOW_TICKS = 300;

    /** The bonus must not change the damage taken by more than this share of the control's. */
    private static final float DAMAGE_PARITY_TOLERANCE = 0.25f;

    /** The loop has to be stressed before a composure comparison means anything: the control breaks this often at least. */
    private static final int CONTROL_STRESS_BAR = 1;

    @Override public String id() { return "fighting-position"; }

    @Override
    public String label() {
        return "Fighting position: does a dug-in squad hold its composure under fire, take the same damage,"
                + " claim distinct cells, and leave its overflow in ordinary cover?";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        boolean previous = DigInSystem.isEnabled();
        try {
            DigInSystem.setEnabledForEvidence(true);
            List<Trace> subject = new ArrayList<>();
            List<Trace> control = new ArrayList<>();
            for (int[] line : ENEMY_LINES) {
                FrameSink sink = subject.isEmpty() ? frames : FrameSink.NONE;
                DigInSystem.setEnabledForEvidence(true);
                subject.add(playUnderFire(sink, "subject", line));
                DigInSystem.setEnabledForEvidence(false);
                control.add(playUnderFire(sink, "control", line));
            }
            DigInSystem.setEnabledForEvidence(true);
            Trace fill = playFill(frames, "fill");
            Trace free = playUnderFireFree(frames, "under-fire-free");
            Trace overflow = playOverflow(frames, "overflow");
            return List.of(reportSubject(subject, control), reportControl(control),
                    reportFill(fill), reportFree(free), reportOverflow(overflow));
        } finally {
            DigInSystem.setEnabledForEvidence(previous);
        }
    }

    // ---- worlds ----

    private static SceneBuilder arena() {
        // A sealed pocket for the far defender, present so the defender side never empties.
        return SceneBuilder.openGround(WIDTH, HEIGHT)
                .missionCompletion(false)
                .wall(POCKET_X - 2, POCKET_Y - 2, POCKET_X + 2, POCKET_Y - 2)
                .wall(POCKET_X - 2, POCKET_Y + 2, POCKET_X + 2, POCKET_Y + 2)
                .wall(POCKET_X - 2, POCKET_Y - 1, POCKET_X - 2, POCKET_Y + 1)
                .wall(POCKET_X + 2, POCKET_Y - 1, POCKET_X + 2, POCKET_Y + 1);
    }

    private static void far(SceneBuilder builder) {
        builder.unit(FAR, Faction.DEFENDER, UnitType.MARINE, POCKET_X, POCKET_Y, spec -> spec.moveSpeed(0f));
    }

    /** A straight trench, each cell joined to its east and west neighbours (mask N1 E2 S4 W8). */
    private static void digTrench(SceneWorld world, int x0, int x1) {
        TileRegistry registry = TileRegistry.installed();
        for (int x = x0; x <= x1; x++) {
            int mask = (x < x1 ? 2 : 0) | (x > x0 ? 8 : 0);
            DoodadDef def = registry.doodad("doodad.fort-trench-" + mask);
            if (def == null) throw new IllegalStateException("no trench piece for mask " + mask);
            world.sim().addDoodad(new Doodad(x, TRENCH_Y, def));
        }
        FightingPositionIndex index = world.sim().fightingPositions();
        int cells = x1 - x0 + 1;
        int run = index.runIdAt(x0, TRENCH_Y);
        if (run < 0 || index.runSize(run) != cells) {
            throw new IllegalStateException("The trench did not come out as one fighting position of "
                    + cells + " cells (run " + run + ", size "
                    + (run < 0 ? 0 : index.runSize(run)) + ")");
        }
    }

    private static SceneWorld buildUnderFire(int[] line) {
        SceneBuilder builder = arena();
        builder.squad(MARINES)
                .faction(Faction.MARINE).type(UnitType.MARINE).size(SQUAD_SIZE)
                .primary(PULSE_RIFLE)
                .inFile(SHORT_TRENCH_X0, TRENCH_Y, 1, 0)
                .stationary()
                .health(MARINE_HEALTH)
                .done();
        builder.squad(ENEMY)
                .faction(Faction.DEFENDER).type(UnitType.MARINE_RED).size(line[2])
                .primary(PULSE_RIFLE)
                .at(line[0], line[1])
                .stationary()
                .assigned(id -> ObjectiveAssignment.defendArea(id, line[0], line[1] + line[2] / 8))
                .done();
        far(builder);
        SceneWorld world = builder.build();
        digTrench(world, SHORT_TRENCH_X0, SHORT_TRENCH_X1);
        return world;
    }

    private Trace playUnderFire(FrameSink frames, String loopId, int[] line) {
        SceneWorld world = buildUnderFire(line);
        Trace trace = new Trace(world, MARINES);
        ScriptedPlayer player = ScriptedPlayer.on(world)
                .defendArea(1, MARINES, (SHORT_TRENCH_X0 + SHORT_TRENCH_X1) / 2, TRENCH_Y);
        SceneRun.of(world).player(player).observe(trace)
                .frames(frames, loopId, FRAME_EVERY_TICKS,
                        tick -> "fighting position / " + loopId + "  t=" + tick + "  " + trace.caption())
                .run(TICKS);
        return trace;
    }

    private Trace playFill(FrameSink frames, String loopId) {
        SceneBuilder builder = arena();
        builder.squad(MARINES)
                .faction(Faction.MARINE).type(UnitType.MARINE).size(SQUAD_SIZE)
                .primary(PULSE_RIFLE)
                .inFile(LONG_TRENCH_X0 + 2, OFF_TRENCH_Y, 1, 0)
                .done();
        far(builder);
        SceneWorld world = builder.build();
        digTrench(world, LONG_TRENCH_X0, LONG_TRENCH_X1);
        Trace trace = new Trace(world, MARINES);
        ScriptedPlayer player = ScriptedPlayer.on(world)
                .defendArea(1, MARINES, (LONG_TRENCH_X0 + LONG_TRENCH_X1) / 2, TRENCH_Y);
        SceneRun.of(world).player(player).observe(trace)
                .frames(frames, loopId, FRAME_EVERY_TICKS,
                        tick -> "fighting position / " + loopId + "  t=" + tick + "  " + trace.caption())
                .run(TICKS);
        return trace;
    }

    /**
     * The squad as it would really arrive: spawned off the trench, ordinary health, free to move, ordered onto
     * it with the enemy already firing. No crutches; this is the loop that says whether a claimed marine stays
     * in his position under fire.
     */
    private Trace playUnderFireFree(FrameSink frames, String loopId) {
        SceneBuilder builder = arena();
        builder.squad(MARINES)
                .faction(Faction.MARINE).type(UnitType.MARINE).size(SQUAD_SIZE)
                .primary(PULSE_RIFLE)
                .inFile(SHORT_TRENCH_X0, OFF_TRENCH_Y, 1, 0)
                .done();
        builder.squad(ENEMY)
                .faction(Faction.DEFENDER).type(UnitType.MARINE_RED).size(FREE_ENEMY_SIZE)
                .primary(LONG_RIFLE)
                .at(FREE_ENEMY_X, FREE_ENEMY_Y)
                .stationary()
                .health(ENEMY_HEALTH)
                .done();
        far(builder);
        SceneWorld world = builder.build();
        digTrench(world, SHORT_TRENCH_X0, SHORT_TRENCH_X1);
        Trace trace = new Trace(world, MARINES);
        trace.watchEnemy(world.members(ENEMY));
        ScriptedPlayer player = ScriptedPlayer.on(world)
                .defendArea(1, MARINES, (SHORT_TRENCH_X0 + SHORT_TRENCH_X1) / 2, TRENCH_Y);
        SceneRun.of(world).player(player).observe(trace)
                .frames(frames, loopId, FRAME_EVERY_TICKS,
                        tick -> "fighting position / " + loopId + "  t=" + tick + "  " + trace.caption())
                .run(TICKS);
        return trace;
    }

    private Trace playOverflow(FrameSink frames, String loopId) {
        SceneBuilder builder = arena();
        // Declared first so it holds the lower squad id and claims first.
        builder.squad(GUARD)
                .faction(Faction.MARINE).type(UnitType.MARINE).size(GUARD_SIZE)
                .primary(PULSE_RIFLE)
                .inFile((SHORT_TRENCH_X0 + SHORT_TRENCH_X1) / 2, TRENCH_Y, 1, 0)
                .done();
        builder.squad(MARINES)
                .faction(Faction.MARINE).type(UnitType.MARINE).size(SQUAD_SIZE)
                .primary(PULSE_RIFLE)
                .inFile(SHORT_TRENCH_X0, OFF_TRENCH_Y, 1, 0)
                .done();
        far(builder);
        SceneWorld world = builder.build();
        digTrench(world, SHORT_TRENCH_X0, SHORT_TRENCH_X1);
        Trace trace = new Trace(world, MARINES, GUARD);
        int centreX = (SHORT_TRENCH_X0 + SHORT_TRENCH_X1) / 2;
        ScriptedPlayer player = ScriptedPlayer.on(world)
                .defendArea(1, GUARD, centreX, TRENCH_Y)
                .defendArea(1, MARINES, centreX, TRENCH_Y);
        SceneRun.of(world).player(player).observe(trace)
                .frames(frames, loopId, FRAME_EVERY_TICKS,
                        tick -> "fighting position / " + loopId + "  t=" + tick + "  " + trace.caption())
                .run(TICKS);
        return trace;
    }

    // ---- verdicts ----

    private SceneReport reportSubject(List<Trace> subject, List<Trace> control) {
        Pool s = new Pool(subject);
        Pool c = new Pool(control);
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("dug-in", s.minDugInAtPeak() >= DUG_IN_BAR,
                "peak dug in per line " + s.peaks() + " of " + SQUAD_SIZE + ", first at tick "
                        + s.earliestDugIn() + " (wanted at least " + DUG_IN_BAR + " on every line)"));
        float gap = Math.abs(s.windowShare() - c.windowShare());
        verdicts.add(Verdict.of("same-ground", gap <= SAME_GROUND_TOLERANCE,
                String.format(Locale.ROOT, "%.1f%% of marine-ticks on position cells over the first %d ticks"
                                + " against the control's %.1f%% (wanted within %.0f points, or the loops differ"
                                + " by ground and not by bonus)",
                        100f * s.windowShare(), SAME_GROUND_WINDOW_TICKS, 100f * c.windowShare(),
                        100f * SAME_GROUND_TOLERANCE)));
        verdicts.add(Verdict.of("composure", s.brokenTeamTicks() < c.brokenTeamTicks()
                        && s.meanMorale() > c.meanMorale(),
                String.format(Locale.ROOT, "controlled isolation (rooted, 1000 health) over %d enemy lines: broken fire-team ticks %d against the control's"
                                + " %d (per line %s against %s); mean team morale %.3f against %.3f"
                                + " (wanted fewer broken ticks and higher morale)",
                        subject.size(), s.brokenTeamTicks(), c.brokenTeamTicks(), s.broken(), c.broken(),
                        s.meanMorale(), c.meanMorale())));
        float ratio = c.damageTaken() > 0f ? s.damageTaken() / c.damageTaken() : 0f;
        verdicts.add(Verdict.of("damage-parity",
                c.damageTaken() > 0f && Math.abs(ratio - 1f) <= DAMAGE_PARITY_TOLERANCE,
                String.format(Locale.ROOT, "%.1f HP taken against the control's %.1f (ratio %.2f, wanted within"
                                + " %.0f%% — the bonus is composure, not protection; a controlled isolation, not a battle outcome); marines alive %d against %d",
                        s.damageTaken(), c.damageTaken(), ratio, 100f * DAMAGE_PARITY_TOLERANCE,
                        s.alive(), c.alive())));
        return new SceneReport("fighting-position", "subject", TICKS, verdicts, s.metrics());
    }

    private SceneReport reportControl(List<Trace> control) {
        Pool c = new Pool(control);
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("control-never-dug-in", c.maxPeakDugIn() == 0,
                c.maxPeakDugIn() + " marine(s) dug in at the peak with fighting positions off (wanted 0)"));
        verdicts.add(Verdict.of("control-stressed", c.brokenTeamTicks() >= CONTROL_STRESS_BAR,
                c.brokenTeamTicks() + " broken fire-team ticks across the control lines (wanted at least "
                        + CONTROL_STRESS_BAR + ", or the fire is too light for composure to differ)"));
        verdicts.add(Verdict.of("control-survives", c.alive() >= control.size() * SQUAD_SIZE / 2,
                c.alive() + " of " + control.size() * SQUAD_SIZE + " marines alive at the end of the control lines"
                        + " (wanted at least half, or the fire wiped the squads)"));
        return new SceneReport("fighting-position", "control", TICKS, verdicts, c.metrics());
    }

    private SceneReport reportFill(Trace fill) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("fill-distinct",
                fill.distinctClaimedAtEnd == SQUAD_SIZE && fill.claimsOnPositionAtEnd == SQUAD_SIZE,
                fill.distinctClaimedAtEnd + " distinct claimed cells for " + SQUAD_SIZE + " marines, "
                        + fill.claimsOnPositionAtEnd + " of the marines standing on a position cell"
                        + " (wanted all of them)"));
        verdicts.add(Verdict.of("dug-in", fill.dugInAtEnd() == SQUAD_SIZE,
                fill.dugInAtEnd() + " of " + SQUAD_SIZE + " dug in at the end, the first at tick "
                        + fill.firstDugInTick + " (wanted all of them)"));
        return new SceneReport("fighting-position", "fill", TICKS, verdicts, fill.metrics());
    }

    private SceneReport reportFree(Trace free) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("under-fire", free.underFireShare() >= UNDER_FIRE_BAR && free.enemiesAtEnd > 0,
                String.format(Locale.ROOT, "the enemy fired a round within the previous two seconds on %d of %d hold-window ticks"
                                + " (%.1f%%, wanted at least %.0f%%); %d enemy rounds in the window; enemy alive %d of %d"
                                + " at first dig-in (tick %d) and %d at the end, last enemy death at tick %d; %.1f HP taken by the squad in the window",
                        free.underFireTicks, free.holdWindowTicks, 100f * free.underFireShare(),
                        100f * UNDER_FIRE_BAR, free.enemyRoundsInWindow, free.enemiesAtDigIn, FREE_ENEMY_SIZE,
                        free.firstDugInTick, free.enemiesAtEnd, free.lastEnemyDeathTick,
                        free.damageTaken() - Math.max(0f, free.damageAtDigIn))));
        verdicts.add(Verdict.of("holds-position", free.holdMarineTicks > 0 && free.holdShare() >= HOLDS_POSITION_BAR,
                String.format(Locale.ROOT, "%.1f%% of %d live, composed marine-ticks after the first dig-in (tick %d)"
                                + " were on position cells; %d of %d alive at the end, %d dug in, %d broken team-ticks,"
                                + " %.1f HP taken (wanted at least %.0f%%; ordinary health, free to move)",
                        100f * free.holdShare(), free.holdMarineTicks, free.firstDugInTick, free.alive, SQUAD_SIZE,
                        free.dugInAtEnd(), free.brokenTeamTicks, free.damageTaken(), 100f * HOLDS_POSITION_BAR)));
        return new SceneReport("fighting-position", "under-fire-free", TICKS, verdicts, free.metrics());
    }

    private SceneReport reportOverflow(Trace overflow) {
        int run = SHORT_TRENCH_X1 - SHORT_TRENCH_X0 + 1;
        int free = run - GUARD_SIZE;
        int left = SQUAD_SIZE - free;
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("overflow-disjoint",
                !overflow.claimCollision && overflow.maxClaimedAtOnce == run,
                overflow.maxClaimedAtOnce + " cells claimed at the peak across both squads of a "
                        + run + "-cell run, collision " + overflow.claimCollision
                        + " (wanted every cell held once, none twice)"));
        verdicts.add(Verdict.of("overflow-capped", overflow.dugInAtEnd() <= free
                        && overflow.dugInAtEnd() >= free - 1,
                overflow.dugInAtEnd() + " of the " + SQUAD_SIZE + "-marine squad dug in at the end (wanted "
                        + (free - 1) + ".." + free + ")"));
        verdicts.add(Verdict.of("overflow-in-cover", overflow.unclaimedOnPositionAtEnd == 0
                        && overflow.unclaimedAtEnd == left,
                overflow.unclaimedAtEnd + " marines hold no claim (wanted " + left + "), of whom "
                        + overflow.unclaimedOnPositionAtEnd + " stand on a position cell (wanted 0)"));
        return new SceneReport("fighting-position", "overflow", TICKS, verdicts, overflow.metrics());
    }

    // ---- instrument ----

    /** The under-fire traces of one loop, one per enemy line, read as a single sample. */
    private static final class Pool {

        private final List<Trace> traces;

        Pool(List<Trace> traces) { this.traces = traces; }

        int brokenTeamTicks() { int n = 0; for (Trace t : traces) n += t.brokenTeamTicks; return n; }

        List<Integer> broken() { List<Integer> l = new ArrayList<>(); for (Trace t : traces) l.add(t.brokenTeamTicks); return l; }

        List<Integer> peaks() { List<Integer> l = new ArrayList<>(); for (Trace t : traces) l.add(t.peakDugIn); return l; }

        int minDugInAtPeak() { int n = Integer.MAX_VALUE; for (Trace t : traces) n = Math.min(n, t.peakDugIn); return n; }

        int maxPeakDugIn() { int n = 0; for (Trace t : traces) n = Math.max(n, t.peakDugIn); return n; }

        int earliestDugIn() { int n = -1; for (Trace t : traces) if (t.firstDugInTick >= 0 && (n < 0 || t.firstDugInTick < n)) n = t.firstDugInTick; return n; }

        int alive() { int n = 0; for (Trace t : traces) n += t.alive; return n; }

        float damageTaken() { float n = 0f; for (Trace t : traces) n += t.damageTaken(); return n; }

        float windowShare() {
            long on = 0; long all = 0;
            for (Trace t : traces) { on += t.windowOnPositionTicks; all += t.windowMarineTicks; }
            return all == 0 ? 0f : (float) on / all;
        }

        float meanMorale() {
            double sum = 0; long n = 0;
            for (Trace t : traces) { sum += t.moraleSum; n += t.teamTicks; }
            return n == 0 ? 0f : (float) (sum / n);
        }

        Map<String, Number> metrics() {
            Map<String, Number> m = new LinkedHashMap<>();
            m.put("enemyLines", traces.size());
            m.put("minPeakDugIn", minDugInAtPeak());
            m.put("earliestDugInTick", earliestDugIn());
            m.put("marinesAlive", alive());
            m.put("onPositionShareFirst" + SAME_GROUND_WINDOW_TICKS, windowShare());
            m.put("brokenTeamTicks", brokenTeamTicks());
            m.put("meanTeamMorale", meanMorale());
            m.put("damageTaken", damageTaken());
            for (int i = 0; i < traces.size(); i++) {
                m.put("line" + i + "BrokenTeamTicks", traces.get(i).brokenTeamTicks);
                m.put("line" + i + "MarinesAlive", traces.get(i).alive);
                m.put("line" + i + "DamageTaken", traces.get(i).damageTaken());
            }
            return m;
        }
    }

    /**
     * What the squad did to itself and what was done to it. Sampled per tick; a marine who dies stops
     * resolving, so damage is carried forward from the last tick he was alive.
     */
    private static final class Trace implements TickObserver {

        private final SceneWorld world;
        private final String key;
        private final long[] marines;
        private final long[] others;
        private final float[] damage;

        int alive;
        int marineTicks;
        int onPositionTicks;
        int windowMarineTicks;
        int windowOnPositionTicks;
        int brokenTeamTicks;
        int teamTicks;
        double moraleSum;
        float minMorale = 1f;
        int peakDugIn;
        int firstDugInTick = -1;
        int dugInNow;
        int distinctClaimedAtEnd;
        int claimsOnPositionAtEnd;
        int unclaimedAtEnd;
        int unclaimedOnPositionAtEnd;
        int maxClaimedAtOnce;
        long[] enemies = new long[0];
        int enemiesAtDigIn = -1;
        int enemiesAtEnd;
        int lastEnemyDeathTick = -1;
        int holdWindowTicks;
        int underFireTicks;
        int enemyRoundsInWindow;
        int enemyRounds;
        float damageAtDigIn = -1f;
        int enemyRoundsAtDigIn = -1;
        int lastEnemyRoundTick = -1;
        int holdMarineTicks;
        int holdOnPositionTicks;
        boolean claimCollision;

        Trace(SceneWorld world, String key, String... otherKeys) {
            this.world = world;
            this.key = key;
            this.marines = world.members(key);
            List<Long> rest = new ArrayList<>();
            for (String other : otherKeys) for (long id : world.members(other)) rest.add(id);
            this.others = new long[rest.size()];
            for (int i = 0; i < others.length; i++) others[i] = rest.get(i);
            this.damage = new float[marines.length];
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            FightingPositionIndex index = sim.fightingPositions();
            CombatTelemetryService telemetry = sim.telemetry();
            int live = 0;
            int dug = 0;
            int onPositionNow = 0;
            int unclaimed = 0;
            int unclaimedOn = 0;
            Set<Integer> cells = new HashSet<>();
            if (enemies.length > 0) {
                int standing = 0;
                for (long e : enemies) if (sim.resolveUnit(e) != 0L) standing++;
                if (standing < enemiesAtEnd) lastEnemyDeathTick = tick;
                enemiesAtEnd = standing;
                if (firstDugInTick >= 0 && enemiesAtDigIn < 0) enemiesAtDigIn = standing;
                if (firstDugInTick >= 0) {
                    holdWindowTicks++;
                    // Rounds the enemy has fired, from its own telemetry: a shot list sampled per tick misses most
                    // of them, and a round fired is the evidence that the enemy has a target and is shooting.
                    int rounds = 0;
                    for (long e : enemies) {
                        if (telemetry.isRecorded(e)) rounds += telemetry.roundsFired(e);
                    }
                    if (rounds > enemyRounds) lastEnemyRoundTick = tick;
                    if (lastEnemyRoundTick >= 0 && tick - lastEnemyRoundTick <= RECENT_FIRE_TICKS) underFireTicks++;
                    if (enemyRoundsAtDigIn < 0) enemyRoundsAtDigIn = rounds;
                    if (damageAtDigIn < 0f) damageAtDigIn = damageTaken();
                    enemyRounds = rounds;
                    enemyRoundsInWindow = rounds - enemyRoundsAtDigIn;
                }
            }
            int width = world.width();
            for (int i = 0; i < marines.length; i++) {
                long id = marines[i];
                if (sim.resolveUnit(id) == 0L) continue;
                live++;
                if (telemetry.isRecorded(id)) damage[i] = telemetry.damageTaken(id);
                boolean on = index.isFightingPositionCell(sim.world().cellX(id), sim.world().cellY(id));
                if (on) onPositionNow++;
                if (sim.isDugIn(id)) dug++;
                if (firstDugInTick >= 0 && !squadOf(sim).fireTeamBroken(sim.squad().fireTeamIndex(id))) {
                    holdMarineTicks++;
                    if (on) holdOnPositionTicks++;
                }
                int[] held = sim.fightingPositionClaims().cellOf(id);
                if (held == null) {
                    unclaimed++;
                    if (on) unclaimedOn++;
                } else if (!cells.add(held[1] * width + held[0])) {
                    claimCollision = true;
                }
            }
            int claimedByOthers = 0;
            for (long id : others) {
                if (sim.resolveUnit(id) == 0L) continue;
                int[] held = sim.fightingPositionClaims().cellOf(id);
                if (held == null) continue;
                claimedByOthers++;
                if (!cells.add(held[1] * width + held[0])) claimCollision = true;
            }
            alive = live;
            dugInNow = dug;
            marineTicks += live;
            onPositionTicks += onPositionNow;
            if (tick < SAME_GROUND_WINDOW_TICKS) {
                windowMarineTicks += live;
                windowOnPositionTicks += onPositionNow;
            }
            peakDugIn = Math.max(peakDugIn, dug);
            if (dug > 0 && firstDugInTick < 0) firstDugInTick = tick;
            distinctClaimedAtEnd = cells.size() - claimedByOthers;
            claimsOnPositionAtEnd = onPositionNow;
            unclaimedAtEnd = unclaimed;
            unclaimedOnPositionAtEnd = unclaimedOn;
            maxClaimedAtOnce = Math.max(maxClaimedAtOnce, cells.size());

            Squad squad = world.squad(key);
            for (FireTeamMorale team : squad.fireTeamMorale()) {
                if (team.aliveMembers <= 0) continue;
                teamTicks++;
                moraleSum += team.morale;
                minMorale = Math.min(minMorale, team.morale);
                if (team.broken) brokenTeamTicks++;
            }
        }

        private Squad squadOf(BattleSimulation sim) { return world.squad(key); }

        float holdShare() { return holdMarineTicks == 0 ? 0f : (float) holdOnPositionTicks / holdMarineTicks; }

        void watchEnemy(long[] ids) { this.enemies = ids; this.enemiesAtEnd = ids.length; }

        float underFireShare() { return holdWindowTicks == 0 ? 0f : (float) underFireTicks / holdWindowTicks; }

        int dugInAtEnd() { return dugInNow; }

        float windowShare() {
            return windowMarineTicks == 0 ? 0f : (float) windowOnPositionTicks / windowMarineTicks;
        }

        float onPositionShare() { return marineTicks == 0 ? 0f : (float) onPositionTicks / marineTicks; }

        float meanMorale() { return teamTicks == 0 ? 0f : (float) (moraleSum / teamTicks); }

        float damageTaken() {
            float total = 0f;
            for (float d : damage) total += d;
            return total;
        }

        String caption() {
            return "dug in " + dugInNow + "  alive " + alive + "  broken team-ticks " + brokenTeamTicks;
        }

        Map<String, Number> metrics() {
            Map<String, Number> m = new LinkedHashMap<>();
            m.put("dugInAtEnd", dugInNow);
            m.put("peakDugIn", peakDugIn);
            m.put("firstDugInTick", firstDugInTick);
            m.put("marinesAlive", alive);
            m.put("onPositionShare", onPositionShare());
            m.put("onPositionShareFirst300", windowShare());
            m.put("holdShareAfterDigIn", holdShare());
            m.put("holdMarineTicks", holdMarineTicks);
            m.put("enemiesAtFirstDigIn", enemiesAtDigIn);
            m.put("enemiesAtEnd", enemiesAtEnd);
            m.put("lastEnemyDeathTick", lastEnemyDeathTick);
            m.put("holdWindowTicks", holdWindowTicks);
            m.put("underFireTicks", underFireTicks);
            m.put("damageTakenInHoldWindow", damageAtDigIn < 0f ? 0f : damageTaken() - damageAtDigIn);
            m.put("enemyRoundsInWindow", enemyRoundsInWindow);
            m.put("brokenTeamTicks", brokenTeamTicks);
            m.put("meanTeamMorale", meanMorale());
            m.put("minTeamMorale", minMorale);
            m.put("damageTaken", damageTaken());
            m.put("distinctClaimedAtEnd", distinctClaimedAtEnd);
            m.put("maxClaimedAtOnce", maxClaimedAtOnce);
            m.put("unclaimedAtEnd", unclaimedAtEnd);
            m.put("unclaimedOnPositionAtEnd", unclaimedOnPositionAtEnd);
            return m;
        }
    }
}
