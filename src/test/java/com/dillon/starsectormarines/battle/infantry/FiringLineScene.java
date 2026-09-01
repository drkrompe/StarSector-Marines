package com.dillon.starsectormarines.battle.infantry;

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
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Six marines in column in a three-cell corridor, four defenders at the end of
 * it, and one question: <b>does a marine with his own man in front of him step
 * out of the lane instead of shooting through him?</b>
 *
 * <p>The fault is old and was never on the decision side. Every line test
 * bottoms out in the navigation grid, which holds terrain and no units, so a
 * marine with a squadmate directly in front reads his lane as clear and fires
 * into their back; {@code BallisticResolver} has always modelled the other half
 * of it, giving that squadmate about a third of a chance of catching the round
 * at half damage. Two attempts have answered it by <em>switching target</em>
 * and both lost Conquest captures, for a reason that generalises: a blocked
 * lane usually means a squadmate is between this marine and the enemy the squad
 * is already engaging, which is a firing line working correctly. So the verb
 * here is the other one — {@link LaneSidestep} — and this is what measures it.
 *
 * <h2>The corridor is the instrument, not the setting</h2>
 * <p>On open ground a squad under a defend order spreads into a perimeter and
 * unmasks itself, and a control loop with no friendly fire in it measures
 * nothing at all. The corridor is what keeps the squad in column.
 *
 * <p><b>It is five cells wide, and the first version was three.</b> Three looks
 * right — a file down the middle with a lane either side of it — and it
 * recorded not one sidestep in nine hundred ticks. A firing lane is
 * {@link com.dillon.starsectormarines.battle.combat.FiringLane#CLEARANCE_CELLS}
 * of half-width, so leaving one takes more than a cell of lateral movement, and
 * a three-cell corridor offers exactly one. The arena was answering the
 * question by removing the option, and the reading — no sidesteps, friendly
 * fire identical in both loops — was indistinguishable from a reflex that did
 * not work. Five cells is the narrowest passage in which stepping aside is a
 * move that exists.
 *
 * <p>The squad is issued a uniform kit rather than the builder's default roll.
 * The default seed hands out one pulse rifle and five submachine guns, whose
 * reach differs by ten cells, so half the file would be walking forward to get
 * into range and the recording would be about a formation rather than about a
 * lane. Every marine here carries the same weapon, so who is masked is a
 * question of geometry.
 *
 * <h2>What it found</h2>
 * <p>Three things, and the first was about the reflex rather than about the
 * squad. Gated on the pursuit target alone the sidestep could never fire here
 * at all: a planted squad runs {@code OverwatchPosture}, which deliberately
 * holds no pursuit target and leaves the shooting to the dispatcher's
 * opportunity pass, so the marines were trading fire with nobody they were
 * <em>engaging</em>. {@link LaneSidestep} asks for the registered threat when
 * there is no pursuit target, which is the id the firing system will actually
 * let a round go at.
 *
 * <p>The second is that <b>a planted step throws the move away on the tick it
 * is authored</b>. Once the reflex stopped consuming the tick, three steps that
 * plant a marine who can fire from where he stands — {@code OverwatchPosture}
 * and {@code DefendArea}'s two motion branches — cleared the path out from
 * under it. Measured: without the guards that teach them
 * {@link LaneSidestep#isStepping}, nine sidesteps were authored and only three
 * covered any ground; with them, every one does. That is why
 * {@code sidesteps-complete} counts moves that <em>went somewhere</em> rather
 * than moves that started, and why an earlier, weaker form of that verdict —
 * "at least one of them moved" — passed on the broken version.
 *
 * <p>The third is that the picture stays the same either way, which is what
 * makes the first two dangerous. Friendly fire was nought and damage on the
 * enemy identical in the broken configuration too: the reflex fires more often
 * to make up for its own cancelled moves, and every headline reading looks
 * right. Only the completion count says which is happening.
 *
 * <p><b>Exactly one caller may move a marine per tick.</b> Handing the tick
 * back means the plan step advances him, so the reflex must not — and a marine
 * advanced twice simply arrives in half the ticks, which no other reading here
 * would catch. {@code one-mover-per-tick} measures the busiest sidestep tick
 * against one tick of the marine's own travel; it reads 1.00.
 *
 * <h2>What each loop changes</h2>
 * <p>Exactly one thing: {@code sidestep} plays with the reflex on and
 * {@code control} with it off, through
 * {@link LaneSidestep#setEnabledForEvidence}. Same seed, same walls, same
 * bodies in the same cells. The first verdict a reader should look at is
 * {@code control-has-friendly-fire}, which is the instrument checking itself —
 * if the control does not shoot its own men, nothing below it means anything
 * and the geometry is what wants fixing.
 */
public final class FiringLineScene implements BehaviorScene {

    private static final int WIDTH = 48;
    private static final int HEIGHT = 24;

    /** Thirty seconds: long enough for the file to settle, engage, and take losses. */
    private static final int TICKS = 900;
    private static final int FRAME_EVERY_TICKS = 15;

    /** The corridor's centre row, with two lanes either side of it. */
    private static final int LANE_Y = 12;
    private static final int CORRIDOR_TOP = LANE_Y - 2;
    private static final int CORRIDOR_BOTTOM = LANE_Y + 2;
    private static final int CORRIDOR_WEST = 4;
    private static final int CORRIDOR_EAST = 40;

    private static final int SQUAD_SIZE = 6;
    private static final int FILE_HEAD_X = 8;

    /**
     * A roll in which every marine carries a pulse rifle, 24 cells of reach
     * apiece. Named rather than borrowed from {@link SceneBuilder#DEFAULT_KIT_SEED}
     * because that one is mixed and this scene needs the file to be one weapon.
     */
    private static final long UNIFORM_RIFLE_SEED = 20260913L;

    /**
     * The defenders' file. Nineteen cells from the rearmost marine — about
     * four fifths of the rifle's 24 — so the whole column is in range from
     * where it stands and nobody has to walk forward to join in.
     */
    private static final int DEFENDER_SIZE = 4;
    private static final int DEFENDER_HEAD_X = 27;

    /** The sealed pocket that keeps the defender side present once the four are down. */
    private static final int POCKET_X = 45;
    private static final int POCKET_Y = 3;

    private static final String MARINES = "marines";
    private static final String DEFENDERS = "defenders";
    private static final String FAR = "far";

    /** The sidestep loop must cut friendly fire to at most this share of the control's. */
    private static final float FRIENDLY_FIRE_BAR = 0.5f;

    /** And must keep at least this share of the control's damage on the enemy. */
    private static final float CONCENTRATION_BAR = 0.9f;

    /**
     * The most of one tick's travel a mid-sidestep marine may cover. A single
     * mover cannot exceed one, and the slack is for the last step of a path,
     * which is clamped to the destination rather than to the speed. Two movers
     * would read about two.
     */
    private static final float DOUBLE_ADVANCE_BAR = 1.25f;

    @Override public String id() { return "firing-line"; }

    @Override
    public String label() {
        return "Firing line: does a marine with his own man in the lane step aside"
                + " rather than shoot through him?";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        boolean previous = LaneSidestep.isEnabled();
        try {
            LaneSidestep.setEnabledForEvidence(true);
            LaneTrace sidestep = playOne(frames, "sidestep");
            LaneSidestep.setEnabledForEvidence(false);
            LaneTrace control = playOne(frames, "control");
            return List.of(report("sidestep", sidestep, control),
                    report("control", control, null));
        } finally {
            LaneSidestep.setEnabledForEvidence(previous);
        }
    }

    /**
     * The world both loops share.
     *
     * <p>Everything outside the corridor is wall, bar one sealed three-by-three
     * pocket in the north-east. That pocket holds the far defender, which is
     * present so the defender side is never absent — the simulation returns
     * immediately once a side is gone and a scene that ends on tick one records
     * nothing — and sealed so it is never a reason for anybody to go anywhere.
     */
    private static SceneWorld build() {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT)
                .missionCompletion(false)
                // North of the corridor, leaving the pocket open.
                .wall(0, 0, POCKET_X - 2, CORRIDOR_TOP - 1)
                .wall(POCKET_X - 1, 0, WIDTH - 1, POCKET_Y - 2)
                .wall(WIDTH - 1, POCKET_Y - 1, WIDTH - 1, POCKET_Y + 1)
                .wall(POCKET_X - 1, POCKET_Y + 2, WIDTH - 1, CORRIDOR_TOP - 1)
                // South of it, and the two ends of it.
                .wall(0, CORRIDOR_BOTTOM + 1, WIDTH - 1, HEIGHT - 1)
                .wall(0, CORRIDOR_TOP, CORRIDOR_WEST - 1, CORRIDOR_BOTTOM)
                .wall(CORRIDOR_EAST + 1, CORRIDOR_TOP, WIDTH - 1, CORRIDOR_BOTTOM);

        builder.squad(MARINES)
                .faction(Faction.MARINE)
                .type(UnitType.MARINE)
                .size(SQUAD_SIZE)
                .kit(UNIFORM_RIFLE_SEED)
                .inFile(FILE_HEAD_X, LANE_Y, 1, 0)
                // Persistent, and a MISSION-bucket order: it outranks survival,
                // so the file holds its ground and fights instead of reading an
                // unfavourable local balance and withdrawing out of the frame.
                .assigned(id -> ObjectiveAssignment.defendArea(id,
                        FILE_HEAD_X + SQUAD_SIZE / 2, LANE_Y))
                .done();
        builder.squad(DEFENDERS)
                .faction(Faction.DEFENDER)
                .type(UnitType.MARINE_RED)
                .size(DEFENDER_SIZE)
                .inFile(DEFENDER_HEAD_X, LANE_Y, 1, 0)
                .stationary()
                .done();
        builder.unit(FAR, Faction.DEFENDER, UnitType.MARINE, POCKET_X, POCKET_Y,
                spec -> spec.moveSpeed(0f));
        return builder.build();
    }

    private LaneTrace playOne(FrameSink frames, String loopId) {
        SceneWorld world = build();
        LaneTrace trace = new LaneTrace(world.members(MARINES), world.members(DEFENDERS));
        SceneRun.of(world)
                .observe(trace)
                .frames(frames, loopId, FRAME_EVERY_TICKS,
                        tick -> "firing line / " + loopId + "  t=" + tick
                                + "  sidesteps=" + trace.sidesteps())
                .run(TICKS);
        return trace;
    }

    /**
     * Judges one loop. The control judges only itself — whether the geometry
     * produced the fault at all — because every other question here is a
     * comparison and a comparison needs the other loop.
     */
    private SceneReport report(String loopId, LaneTrace loop, LaneTrace control) {
        List<Verdict> verdicts = new ArrayList<>();
        if (control == null) {
            verdicts.add(Verdict.of("control-has-friendly-fire", loop.friendlyFire() > 0f,
                    String.format(Locale.ROOT,
                            "the marines put %.1f HP into their own men with the reflex off"
                                    + " (wanted more than 0, or the corridor is not producing"
                                    + " the fault this scene exists to measure)",
                            loop.friendlyFire())));
            verdicts.add(Verdict.of("no-sidesteps", loop.sidesteps() == 0,
                    loop.sidesteps() + " sidestep(s) taken with the reflex off (wanted 0)"));
        } else {
            float bar = control.friendlyFire() * FRIENDLY_FIRE_BAR;
            verdicts.add(Verdict.of("sidestep-reduces-friendly-fire",
                    loop.friendlyFire() <= bar,
                    String.format(Locale.ROOT,
                            "%.1f HP of friendly fire against the control's %.1f"
                                    + " (wanted at most %.1f)",
                            loop.friendlyFire(), control.friendlyFire(), bar)));
            float concentration = control.hostileDamage() * CONCENTRATION_BAR;
            verdicts.add(Verdict.of("fire-stays-concentrated",
                    loop.hostileDamage() >= concentration,
                    String.format(Locale.ROOT,
                            "%.1f HP onto the enemy against the control's %.1f"
                                    + " (wanted at least %.1f — stepping aside must not"
                                    + " cost the squad its own shooting)",
                            loop.hostileDamage(), control.hostileDamage(), concentration)));
            verdicts.add(Verdict.of("sidestepped", loop.sidesteps() > 0,
                    loop.sidesteps() + " sidestep(s) taken with the reflex on, "
                            + control.sidesteps() + " with it off (wanted some, then none)"));
            // A start that never finishes is a move a plan step threw away on
            // the tick it was authored, which is exactly what happens when a
            // planted posture clears the path out from under it. Cells covered
            // is what says the marine actually went somewhere.
            verdicts.add(Verdict.of("sidesteps-complete",
                    loop.sidestepsThatMoved() == loop.sidesteps(),
                    loop.sidestepsCompleted() + " of " + loop.sidesteps()
                            + " sidestep(s) ran to completion and "
                            + loop.sidestepsThatMoved() + " covered ground, the longest "
                            + loop.maxSidestepMove() + " cell(s)"
                            + " (wanted every one of them to move)"));
            verdicts.add(Verdict.of("one-mover-per-tick",
                    loop.worstStepRatio() <= DOUBLE_ADVANCE_BAR,
                    String.format(Locale.ROOT,
                            "the busiest sidestep tick moved a marine %.2f of one tick's"
                                    + " travel (wanted at most %.2f — above that the reflex"
                                    + " and the plan step are both advancing him)",
                            loop.worstStepRatio(), DOUBLE_ADVANCE_BAR)));
            verdicts.add(Verdict.of("intact", loop.marinesAlive() >= control.marinesAlive(),
                    loop.marinesAlive() + " marines alive against the control's "
                            + control.marinesAlive() + " (wanted no fewer)"));
        }

        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("friendlyFireDamage", loop.friendlyFire());
        metrics.put("hostileDamageDealt", loop.hostileDamage());
        metrics.put("defendersKilled", loop.defendersKilled());
        metrics.put("marinesAlive", loop.marinesAlive());
        metrics.put("sidesteps", loop.sidesteps());
        metrics.put("sidestepsCompleted", loop.sidestepsCompleted());
        metrics.put("sidestepsThatMoved", loop.sidestepsThatMoved());
        metrics.put("maxSidestepMoveCells", loop.maxSidestepMove());
        metrics.put("worstTickTravelRatio", loop.worstStepRatio());
        metrics.put("maxDisplacementCells", loop.maxDisplacement());
        return new SceneReport(id(), loopId, TICKS, verdicts, metrics);
    }

    /**
     * What the file did to itself, to the enemy, and to its own footing.
     *
     * <p>Every reading is sampled per tick and carried forward rather than read
     * once at the end: a marine who dies stops being resolvable, and his
     * telemetry — which is most of what this scene is about — would go with
     * him. The last value seen while he was alive is the honest one.
     */
    private static final class LaneTrace implements TickObserver {

        private final long[] marines;
        private final long[] defenders;
        private final float[] friendlyFire;
        private final float[] hostileDamage;
        private final int[] firstFireX;
        private final int[] firstFireY;
        private final boolean[] steppingLastTick;
        private final int[] stepStartX;
        private final int[] stepStartY;
        private final float[] lastX;
        private final float[] lastY;
        private int sidesteps;
        private int sidestepsCompleted;
        private int sidestepsThatMoved;
        private int maxSidestepMove;
        private float worstStepRatio;
        private int maxDisplacement;
        private int marinesAlive;
        private int defendersAlive;

        private LaneTrace(long[] marines, long[] defenders) {
            this.marines = marines;
            this.defenders = defenders;
            this.friendlyFire = new float[marines.length];
            this.hostileDamage = new float[marines.length];
            this.firstFireX = new int[marines.length];
            this.firstFireY = new int[marines.length];
            this.steppingLastTick = new boolean[marines.length];
            this.stepStartX = new int[marines.length];
            this.stepStartY = new int[marines.length];
            this.lastX = new float[marines.length];
            this.lastY = new float[marines.length];
            this.marinesAlive = marines.length;
            this.defendersAlive = defenders.length;
            Arrays.fill(firstFireX, Integer.MIN_VALUE);
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            World world = sim.world();
            CombatTelemetryService telemetry = sim.telemetry();
            int alive = 0;
            for (int i = 0; i < marines.length; i++) {
                long marine = marines[i];
                if (sim.resolveUnit(marine) == 0L) continue;
                alive++;
                if (telemetry.isRecorded(marine)) {
                    friendlyFire[i] = telemetry.friendlyFireDamage(marine);
                    hostileDamage[i] = telemetry.damageDealt(marine);
                    trackFooting(world, telemetry, marine, i);
                }
                countSidestep(sim, world, marine, i);
            }
            marinesAlive = alive;
            int standing = 0;
            for (long defender : defenders) {
                if (sim.resolveUnit(defender) != 0L) standing++;
            }
            defendersAlive = standing;
        }

        /**
         * How far a marine has drifted from the cell he was standing in when he
         * first pulled a trigger. A sidestep is supposed to be a step; a marine
         * who has walked half the corridor has been given the wandering
         * behaviour the reflex was written not to be.
         */
        private void trackFooting(World world, CombatTelemetryService telemetry,
                                  long marine, int i) {
            if (firstFireX[i] == Integer.MIN_VALUE) {
                if (telemetry.roundsFired(marine) == 0) return;
                firstFireX[i] = world.cellX(marine);
                firstFireY[i] = world.cellY(marine);
                return;
            }
            int displacement = Math.max(Math.abs(world.cellX(marine) - firstFireX[i]),
                    Math.abs(world.cellY(marine) - firstFireY[i]));
            if (displacement > maxDisplacement) maxDisplacement = displacement;
        }

        /**
         * Counts the starts and the finishes of sidesteps, and how far one
         * actually carried its marine.
         *
         * <p><b>Read off the sidestep timer, not off the reflex chain's
         * breadcrumb.</b> {@code lastReflex} names the reflex that
         * <em>consumed</em> a tick, and this one deliberately does not consume
         * any: it authors the move and declines so the marine keeps shooting
         * while he walks. So the breadcrumb is empty on every tick of every
         * sidestep, and the first run under that contract reported zero
         * sidesteps beside a friendly-fire figure of nought — an instrument
         * measuring the old design. The timer is what the reflex actually
         * writes, so the timer is the record.
         *
         * <p>The completion figure is the point of the pair. A start says the
         * reflex fired; only a finish, with cells covered between the two, says
         * the move survived the plan step it handed the tick back to.
         */
        private void countSidestep(BattleSimulation sim, World world, long marine, int i) {
            if (!world.hasAiState(marine)) return;
            boolean stepping = world.sidestepTimer(marine) > 0f;
            if (stepping && !steppingLastTick[i]) {
                sidesteps++;
                stepStartX[i] = world.cellX(marine);
                stepStartY[i] = world.cellY(marine);
            } else if (!stepping && steppingLastTick[i]) {
                sidestepsCompleted++;
                int moved = Math.max(Math.abs(world.cellX(marine) - stepStartX[i]),
                        Math.abs(world.cellY(marine) - stepStartY[i]));
                if (moved > 0) sidestepsThatMoved++;
                if (moved > maxSidestepMove) maxSidestepMove = moved;
            }
            if (steppingLastTick[i]) measureLastTickStep(sim, marine, i);
            steppingLastTick[i] = stepping;
            lastX[i] = world.x(marine);
            lastY[i] = world.y(marine);
        }

        /**
         * How far the tick that just ran actually carried a marine who was
         * mid-sidestep, as a fraction of what one tick of his move speed
         * allows.
         *
         * <p>This is the check that the reflex and the plan step are not
         * <em>both</em> advancing him. The reflex hands the tick back
         * deliberately so the step can move him, which is only safe while
         * exactly one of them does it; a ratio near two is two callers, and it
         * would otherwise be invisible — a marine who arrives in half the ticks
         * still arrives, and every other reading in this scene would look
         * right.
         */
        private void measureLastTickStep(BattleSimulation sim, long marine, int i) {
            float allowed = sim.movement().moveSpeed(marine) * BattleSimulation.TICK_DT;
            if (!(allowed > 0f)) return;
            float dx = sim.world().x(marine) - lastX[i];
            float dy = sim.world().y(marine) - lastY[i];
            float ratio = (float) Math.sqrt(dx * dx + dy * dy) / allowed;
            if (ratio > worstStepRatio) worstStepRatio = ratio;
        }

        float friendlyFire() { return sum(friendlyFire); }

        float hostileDamage() { return sum(hostileDamage); }

        int sidesteps() { return sidesteps; }

        int sidestepsCompleted() { return sidestepsCompleted; }

        int sidestepsThatMoved() { return sidestepsThatMoved; }

        int maxSidestepMove() { return maxSidestepMove; }

        float worstStepRatio() { return worstStepRatio; }

        int maxDisplacement() { return maxDisplacement; }

        int marinesAlive() { return marinesAlive; }

        int defendersKilled() { return defenders.length - defendersAlive; }

        private static float sum(float[] values) {
            float total = 0f;
            for (float value : values) total += value;
            return total;
        }
    }
}
