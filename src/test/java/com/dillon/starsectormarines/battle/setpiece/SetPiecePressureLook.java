package com.dillon.starsectormarines.battle.setpiece;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.evacuation.BorePressure;
import com.dillon.starsectormarines.battle.evacuation.ColonyObjectiveForce;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveBoard;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveResult;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime;
import com.dillon.starsectormarines.battle.objective.optional.PressuredObjective;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.LongStream;

/**
 * A Colony Breach side objective played on its set piece under its own pressure ({@code set-piece-blocks.md},
 * Lanes and pressure), the way {@code loading-pressure-look} plays the loading: the objective forced, a static
 * six-marine guard stood round the place it is about, and the bore's broods sent in down the set piece's lanes.
 * Logs the beat every few seconds and the outcome, renders a frame a second wide enough for the set piece and the
 * bore together for a clip, and a close still every fifteen seconds. Opt-in, a look and pacing question; frames
 * are skipped with {@code -Dbattle.pressureLookFrames=false} for a quick pacing read, and
 * {@code -Dbattle.pressureLookSeed=<n>} plays one seed; {@code -Dbattle.pressureLookScan=<n>} only sets up seeds 1 to n
 * and says which host the objective under pressure.
 */
abstract class SetPiecePressureLook<T extends PressuredObjective.Task> implements SnapshotSuite {

    static final int GUARDS = 6;
    static final float GUARD_RING = 5f;
    static final float LIMIT_SECONDS = 330f;
    static final float AFTER_SECONDS = 20f;
    static final float LOG_SECONDS = 5f;
    static final float FRAME_SECONDS = 1f;
    static final float NEAR = 12f;
    /** Cells kept beyond the set piece and the bore in the clip frame. */
    static final float MARGIN = 12f;
    /** The close look, in cells across, and seconds between its stills. */
    static final float CLOSE_CELLS = 48f, CLOSE_SECONDS = 15f;

    /** The pool key played. */
    abstract String key();

    /** Colony seeds played when no one seed is asked for. */
    abstract long[] seeds();

    /** The task this suite reads, or null when the runtime holds another kind. */
    abstract T task(PressuredObjective.Task task);

    /** Where the guard stands and the close look centres: the place the objective is about. */
    abstract float[] focus(BattleSimulation sim, T task);

    /** How the task stands, for the log. */
    abstract String progress(BattleSimulation sim, T task);

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        boolean frames = !"false".equals(System.getProperty("battle.pressureLookFrames"));
        String only = System.getProperty("battle.pressureLookSeed");
        String scan = System.getProperty("battle.pressureLookScan");
        long[] seeds = only != null ? new long[]{Long.parseLong(only)}
                : scan != null ? LongStream.rangeClosed(1, Long.parseLong(scan)).toArray() : seeds();
        List<SnapshotArtifact> out = new ArrayList<>();
        List<String> outcomes = new ArrayList<>();
        try (ColonyObjectiveForce forced = ColonyObjectiveForce.force(key())) {
            for (long seed : seeds) {
                try (BattleSimulation sim = BattleSetup.createCivilianRescue(seed, Collections.emptyList(), false,
                        RiskLevel.HIGH)) {
                    ObjectiveBoard board = sim.getOptionalObjectives();
                    PressuredObjective objective = board != null && board.runtime(key()) instanceof PressuredObjective o
                            ? o : null;
                    T task = objective == null ? null : task(objective.task());
                    if (task == null || !(objective.pressure() instanceof BorePressure pressure)) {
                        System.out.println("[" + id() + "] seed " + seed + ": no pressured objective (board "
                                + (board == null ? "none" : board.results().stream().map(ObjectiveResult::key).toList())
                                + ", task " + (task != null) + ", pressure "
                                + (objective == null ? "-" : objective.pressure().getClass().getSimpleName()) + ")");
                        outcomes.add("seed " + seed + ": not hosted");
                        continue;
                    }
                    if (scan != null) {
                        System.out.println("[" + id() + "] seed " + seed + ": hosted, pressure site "
                                + pressure.site()[0] + "," + pressure.site()[1]);
                        outcomes.add("seed " + seed + ": hosted");
                        continue;
                    }
                    float[] focus = focus(sim, task);
                    int guards = guard(sim, focus[0], focus[1], seed);
                    int[] site = pressure.site();
                    float fx = (focus[0] + site[0] + .5f) / 2f, fy = (focus[1] + site[1] + .5f) / 2f;
                    System.out.printf(Locale.ROOT, "[%s] seed %d: focus %.0f,%.0f, bore site %d,%d (%.0f away), %d guards%n",
                            id(), seed, focus[0], focus[1], site[0], site[1],
                            Math.hypot(site[0] - focus[0], site[1] - focus[1]), guards);
                    float across = Math.max(BattleReviewFrameRenderer.PLAY_ZOOM_CELLS, Math.max(
                            Math.abs(site[0] - focus[0]) + 2 * MARGIN,
                            (Math.abs(site[1] - focus[1]) + 2 * MARGIN) * 1920f / 1080f));
                    BattleReviewFrameRenderer view = BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, fx, fy,
                            across).withoutRoofs();
                    BattleReviewFrameRenderer close = BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080,
                            focus[0], focus[1], CLOSE_CELLS).withoutRoofs();
                    float t = 0f, endedAt = -1f, nextLog = 0f, nextFrame = 0f, nextClose = 0f;
                    int frame = 0;
                    while (t < LIMIT_SECONDS && (endedAt < 0f || t - endedAt < AFTER_SECONDS)) {
                        if (t >= nextLog) {
                            log(sim, seed, t, board, task, pressure, focus);
                            nextLog += LOG_SECONDS;
                        }
                        if (frames && t >= nextClose) {
                            String f = String.format(Locale.ROOT, "seed%d-close-%03ds.png", seed, Math.round(t));
                            out.add(new SnapshotArtifact(f, close.render(sim, f)));
                            nextClose += CLOSE_SECONDS;
                        }
                        if (frames && t >= nextFrame) {
                            String f = String.format(Locale.ROOT, "clip-seed%d/frame-%03d.png", seed, frame++);
                            out.add(new SnapshotArtifact(f, view.render(sim, f)));
                            nextFrame += FRAME_SECONDS;
                        }
                        run(sim, .5f);
                        t += .5f;
                        if (endedAt < 0f && status(board) != ObjectiveRuntime.Status.ACTIVE) endedAt = t;
                    }
                    log(sim, seed, t, board, task, pressure, focus);
                    outcomes.add(String.format(Locale.ROOT, "seed %d: %s at %.0f s, %s, %d of %d guards standing",
                            seed, status(board), endedAt < 0f ? t : endedAt, progress(sim, task), guardsLeft(sim), guards));
                }
            }
        }
        for (String o : outcomes) System.out.println("[" + id() + "] outcome " + o);
        return out;
    }

    private ObjectiveRuntime.Status status(ObjectiveBoard board) {
        for (ObjectiveResult r : board.results()) if (r.key().equals(key())) return r.status();
        return ObjectiveRuntime.Status.LAPSED;
    }

    private void log(BattleSimulation sim, long seed, float t, ObjectiveBoard board, T task, BorePressure pressure,
                     float[] focus) {
        int bodies = 0, near = 0, field = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long body = sim.liveUnitAt(i);
            boolean close = Math.hypot(sim.world().x(body) - focus[0], sim.world().y(body) - focus[1]) <= 2 * NEAR;
            if (!pressure.fighter(sim, body)) {
                // The breach's own swarm, foraging into town on its own clock.
                if (close && sim.role().role(body) == UnitRole.SWARM_PRESSURE) field++;
                continue;
            }
            bodies++;
            if (Math.hypot(sim.world().x(body) - focus[0], sim.world().y(body) - focus[1]) <= NEAR) near++;
        }
        System.out.printf(Locale.ROOT, "[%s] seed %d t=%3.0f %s %s, bore %s, %d bore bodies (%d steered, %d near), %d of the field's within %.0f, %d guards%n",
                id(), seed, t, status(board), progress(sim, task),
                pressure.opened() ? "open" + borePlace(sim, pressure.number()) : "shut", bodies, pressure.steering(),
                near, field, 2 * NEAR, guardsLeft(sim));
    }

    private static int guardsLeft(BattleSimulation sim) {
        int marines = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            String name = sim.identity().name(sim.liveUnitAt(i));
            if (name != null && name.startsWith("objective-guard-")) marines++;
        }
        return marines;
    }

    private static String borePlace(BattleSimulation sim, int number) {
        for (SwarmBore b : sim.swarmBores().bores()) {
            if (b.number() == number) return " at " + b.x() + "," + b.y() + (b.collapsed() ? " COLLAPSED" : "")
                    + " surges " + b.surges();
        }
        return " (pending)";
    }

    /** A marine squad holding the place round {@code (x, y)}, out in the open. */
    private int guard(BattleSimulation sim, float x0, float y0, long seed) {
        NavigationGrid grid = sim.getGrid();
        List<int[]> cells = new ArrayList<>();
        for (int i = 0; i < GUARDS * 4 && cells.size() < GUARDS; i++) {
            double angle = 2 * Math.PI * i / (GUARDS * 4.0) * 4.0 + i * 0.37;
            int x = Math.round(x0 + (float) Math.cos(angle) * GUARD_RING);
            int y = Math.round(y0 + (float) Math.sin(angle) * GUARD_RING);
            // Out on the ground being held, never in a shop off it.
            if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)
                    || sim.getTopology().getGroundKind(x, y) == CellTopology.GroundKind.INDOOR) continue;
            boolean apart = true;
            for (int[] c : cells) if (Math.abs(c[0] - x) + Math.abs(c[1] - y) < 3) apart = false;
            if (apart) cells.add(new int[]{x, y});
        }
        if (cells.isEmpty()) return 0;
        int squad = sim.mintSquad(Faction.MARINE, UnitType.MARINE_BLUE);
        MarineLoadout[] kit = InfantryLoadoutRolls.playerSquad(cells.size(), new Random(seed));
        for (int i = 0; i < cells.size(); i++) {
            int[] c = cells.get(i);
            EntitySpec spec = new EntitySpec("objective-guard-" + i, Faction.MARINE, UnitType.MARINE_BLUE, c[0], c[1]);
            kit[i].seedInto(spec);
            spec.role(UnitRole.GARRISON).home(c[0], c[1]).squad(squad);
            sim.spawn(spec);
        }
        sim.getSquad(squad).originalSize = cells.size();
        sim.claimSquadCommand(squad, CommandAuthority.GARRISON, id(), "guarding the objective");
        return cells.size();
    }

    private static void run(BattleSimulation sim, float seconds) {
        int ticks = Math.round(seconds / BattleSimulation.TICK_DT);
        for (int t = 0; t < ticks; t++) sim.advance(BattleSimulation.TICK_DT);
    }
}
