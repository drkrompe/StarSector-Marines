package com.dillon.starsectormarines.battle.staffed;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.evacuation.BorePressure;
import com.dillon.starsectormarines.battle.evacuation.ColonyObjectiveForce;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveBoard;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
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

/**
 * A freight-yard loading under its own pressure ({@code set-piece-blocks.md}, Lanes and pressure): a
 * marine squad guarding the yard while the loading's bore, opened down the street, sends its broods in by
 * the gate and the side gap, then rushes the road out once the truck leaves. Logs the beat every few
 * seconds, renders a frame a second wide enough for the yard and the bore together for a clip, and a
 * close still of the yard every fifteen seconds. Opt-in, a look and pacing question; frames
 * are skipped with {@code -Dbattle.pressureLookFrames=false} for a quick pacing read, and
 * {@code -Dbattle.pressureLookSeed=<n>} plays one seed.
 */
public final class LoadingPressureLookSuite implements SnapshotSuite {

    static final long[] SEEDS = seeds();
    static final int GUARDS = 6;
    static final float GUARD_RING = 5f;
    static final float LIMIT_SECONDS = 300f;
    static final float AFTER_DEPART_SECONDS = 30f;
    static final float LOG_SECONDS = 5f;
    static final float FRAME_SECONDS = 1f;
    static final float NEAR = 12f;
    /** Cells kept beyond the truck and the bore in the clip frame. */
    static final float MARGIN = 12f;
    /** The close look at the yard, in cells across, and seconds between its stills. */
    static final float CLOSE_CELLS = 48f, CLOSE_SECONDS = 15f;
    private static final String ISSUER = "loading-pressure-look";

    private static long[] seeds() {
        String only = System.getProperty("battle.pressureLookSeed");
        return only == null ? new long[]{2L, 11L, 14L} : new long[]{Long.parseLong(only)};
    }

    @Override public String id() { return "loading-pressure-look"; }

    @Override public String label() { return "Freight yard loading under its own bore's pressure"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (ColonyObjectiveForce forced = ColonyObjectiveForce.force(LoadingObjective.KEY)) {
            return renderLoading(context);
        }
    }

    private List<SnapshotArtifact> renderLoading(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        boolean frames = !"false".equals(System.getProperty("battle.pressureLookFrames"));
        List<SnapshotArtifact> out = new ArrayList<>();
        for (long seed : SEEDS) {
            try (BattleSimulation sim = BattleSetup.createCivilianRescue(seed, Collections.emptyList(), false,
                    RiskLevel.HIGH)) {
                ObjectiveBoard board = sim.getOptionalObjectives();
                LoadingObjective objective = board != null && board.runtime(LoadingObjective.KEY) instanceof LoadingObjective o
                        ? o : null;
                if (objective == null || !(objective.pressure() instanceof BorePressure pressure)) {
                    System.out.println("[" + id() + "] seed " + seed + ": no pressured loading");
                    continue;
                }
                Loading loading = objective.loading();
                GroundBody truck = sim.convoy().body(loading.truck());
                float tx = truck.x, ty = truck.y;
                int guards = guard(sim, tx, ty, seed);
                int[] site = pressure.site();
                float fx = (tx + site[0] + .5f) / 2f, fy = (ty + site[1] + .5f) / 2f;
                System.out.printf(Locale.ROOT, "[%s] seed %d: truck %.0f,%.0f at bay %b, bore site %d,%d (%.0f away), %d guards%n",
                        id(), seed, tx, ty, loading.atBay(), site[0], site[1], Math.hypot(site[0] - tx, site[1] - ty), guards);
                // Wide enough to hold the yard and the bore together, with the yard's half-width beyond each.
                float across = Math.max(BattleReviewFrameRenderer.PLAY_ZOOM_CELLS, Math.max(
                        Math.abs(site[0] - tx) + 2 * MARGIN, (Math.abs(site[1] - ty) + 2 * MARGIN) * 1920f / 1080f));
                BattleReviewFrameRenderer view = BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, fx, fy,
                        across).withoutRoofs();
                BattleReviewFrameRenderer close = BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, tx, ty,
                        CLOSE_CELLS).withoutRoofs();
                float t = 0f, departedAt = -1f, nextLog = 0f, nextFrame = 0f, nextClose = 0f;
                int frame = 0;
                while (t < LIMIT_SECONDS && (departedAt < 0f || t - departedAt < AFTER_DEPART_SECONDS)) {
                    if (t >= nextLog) {
                        log(sim, seed, t, loading, pressure, tx, ty);
                        nextLog += LOG_SECONDS;
                    }
                    if (frames && t >= nextClose) {
                        String f = String.format(Locale.ROOT, "seed%d-yard-%03ds.png", seed, Math.round(t));
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
                    if (departedAt < 0f && loading.phase() != Loading.Phase.LOADING) departedAt = t;
                }
                log(sim, seed, t, loading, pressure, tx, ty);
            }
        }
        return out;
    }

    private void log(BattleSimulation sim, long seed, float t, Loading loading, BorePressure pressure, float tx, float ty) {
        int bodies = 0, near = 0, marines = 0, others = 0, crew = 0;
        for (long c : loading.building().crew()) if (sim.getRoster().isLive(c)) crew++;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long body = sim.liveUnitAt(i);
            String name = sim.identity().name(body);
            if (name == null) continue;
            if (name.startsWith("loading-guard-")) marines++;
            if (!pressure.fighter(sim, body)) {
                if (sim.role().role(body) == UnitRole.SWARM_PRESSURE
                        && Math.hypot(sim.world().x(body) - tx, sim.world().y(body) - ty) <= NEAR) others++;
                continue;
            }
            bodies++;
            if (Math.hypot(sim.world().x(body) - tx, sim.world().y(body) - ty) <= NEAR) near++;
        }
        System.out.printf(Locale.ROOT, "[%s] seed %d t=%3.0f %s %d/%d aboard, bore %s, %d bore bodies (%d steered, %d near truck), %d other swarm near truck, %d crew, %d guards%n",
                id(), seed, t, loading.phase(), loading.delivered(), loading.target(),
                pressure.opened() ? "open" + borePlace(sim) : "shut", bodies, pressure.steering(), near, others, crew, marines);
    }

    private static String borePlace(BattleSimulation sim) {
        for (SwarmBore b : sim.swarmBores().bores()) if (b.number() == BorePressure.NUMBER) return " at " + b.x() + "," + b.y() + (b.collapsed() ? " COLLAPSED" : "") + " surges " + b.surges();
        return " (pending)";
    }

    /** A marine squad holding the yard around the truck. */
    private static int guard(BattleSimulation sim, float tx, float ty, long seed) {
        NavigationGrid grid = sim.getGrid();
        List<int[]> cells = new ArrayList<>();
        for (int i = 0; i < GUARDS * 4 && cells.size() < GUARDS; i++) {
            double angle = 2 * Math.PI * i / (GUARDS * 4.0) * 4.0 + i * 0.37;
            int x = Math.round(tx + (float) Math.cos(angle) * GUARD_RING);
            int y = Math.round(ty + (float) Math.sin(angle) * GUARD_RING);
            if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) continue;
            boolean apart = true;
            for (int[] c : cells) if (Math.abs(c[0] - x) + Math.abs(c[1] - y) < 3) apart = false;
            if (apart) cells.add(new int[]{x, y});
        }
        if (cells.isEmpty()) return 0;
        int squad = sim.mintSquad(Faction.MARINE, UnitType.MARINE_BLUE);
        MarineLoadout[] kit = InfantryLoadoutRolls.playerSquad(cells.size(), new Random(seed));
        for (int i = 0; i < cells.size(); i++) {
            int[] c = cells.get(i);
            EntitySpec spec = new EntitySpec("loading-guard-" + i, Faction.MARINE, UnitType.MARINE_BLUE, c[0], c[1]);
            kit[i].seedInto(spec);
            spec.role(UnitRole.GARRISON).home(c[0], c[1]).squad(squad);
            sim.spawn(spec);
        }
        sim.getSquad(squad).originalSize = cells.size();
        sim.claimSquadCommand(squad, CommandAuthority.GARRISON, ISSUER, "guarding the loading");
        return cells.size();
    }

    private static void run(BattleSimulation sim, float seconds) {
        int ticks = Math.round(seconds / BattleSimulation.TICK_DT);
        for (int t = 0; t < ticks; t++) sim.advance(BattleSimulation.TICK_DT);
    }
}
