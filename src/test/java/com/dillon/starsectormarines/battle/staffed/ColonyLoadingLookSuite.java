package com.dillon.starsectormarines.battle.staffed;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.evacuation.ColonyObjectiveForce;
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
 * "Secure the loading" in Colony Breach ({@code staffed-buildings.md}): the colony's warehouse
 * crew carrying its stores out to a truck at the door while a marine squad stands guard over the
 * yard, then the truck driving out. Look-preset stills at close and play zoom, and two runs of
 * close frames (the loading, then the truck leaving) for clips. Opt-in, a look question.
 */
public final class ColonyLoadingLookSuite implements SnapshotSuite {

    static final long[] SEEDS = {42L, 7L};
    static final float CLOSE_CELLS = 34f;
    static final int GUARDS = 6;
    /** Guards stand this far from the truck, in cells: across the yard, not on the tailgate. */
    static final float GUARD_RING = 7f;
    static final float SETTLE_SECONDS = 10f;
    static final float STILL_GAP_SECONDS = 25f;
    static final int LOADING_STILLS = 3;
    static final int CLIP_FRAMES = 120;
    static final int DEPART_FRAMES = 90;
    static final float CLIP_FRAME_SECONDS = 1f / 6f;
    /** Battle seconds the loading is given to fill the truck before the suite gives up waiting. */
    static final float LOADING_LIMIT_SECONDS = 360f;
    private static final String ISSUER = "colony-loading-look";

    @Override public String id() { return "colony-loading-look"; }

    @Override public String label() { return "Colony Breach: securing the loading"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (ColonyObjectiveForce forced = ColonyObjectiveForce.force(LoadingObjective.KEY)) {
            return renderLoading(context);
        }
    }

    private List<SnapshotArtifact> renderLoading(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        boolean clipped = false;
        for (long seed : SEEDS) {
            try (BattleSimulation sim = BattleSetup.createCivilianRescue(seed, Collections.emptyList(), false,
                    RiskLevel.HIGH)) {
                ObjectiveBoard board = sim.getOptionalObjectives();
                Loading loading = board != null && board.runtime(LoadingObjective.KEY) instanceof LoadingObjective o
                        ? o.loading() : null;
                if (loading == null) {
                    System.out.println("[" + id() + "] seed " + seed + ": no loading");
                    continue;
                }
                GroundBody truck = sim.convoy().body(loading.truck());
                float tx = truck.x, ty = truck.y;
                int guards = guard(sim, tx, ty, seed);
                float[] yard = yard(sim, loading, tx, ty);
                System.out.printf(Locale.ROOT, "[%s] seed %d: truck at %.0f,%.0f, %d guards%n", id(), seed, tx, ty, guards);

                run(sim, SETTLE_SECONDS);
                String stem = "seed" + seed;
                out.add(new SnapshotArtifact(stem + "-play.png", BattleReviewFrameRenderer.look(context.modRoot(), 1920,
                        1080, yard[0], yard[1], BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).withoutRoofs().render(sim, stem)));
                for (int still = 0; still < LOADING_STILLS; still++) {
                    String name = String.format(Locale.ROOT, "%s-loading-%02d", stem, still + 1);
                    out.add(new SnapshotArtifact(name + ".png", BattleReviewFrameRenderer.look(context.modRoot(), 1920,
                            1080, yard[0], yard[1], CLOSE_CELLS).withoutRoofs().render(sim, name)));
                    System.out.printf(Locale.ROOT, "[%s] %s: %s, %d/%d aboard%n", id(), name, loading.phase(),
                            loading.delivered(), loading.target());
                    if (!clipped && still == 0) {
                        BattleReviewFrameRenderer clip = BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080,
                                yard[0], yard[1], CLOSE_CELLS).withoutRoofs();
                        for (int frame = 0; frame < CLIP_FRAMES; frame++) {
                            String f = String.format(Locale.ROOT, "clip-loading-seed%d/frame-%03d.png", seed, frame);
                            out.add(new SnapshotArtifact(f, clip.render(sim, f)));
                            run(sim, CLIP_FRAME_SECONDS);
                        }
                    } else {
                        run(sim, STILL_GAP_SECONDS);
                    }
                }
                float waited = 0f;
                while (loading.phase() == Loading.Phase.LOADING && waited < LOADING_LIMIT_SECONDS) {
                    run(sim, 1f);
                    waited += 1f;
                }
                System.out.printf(Locale.ROOT, "[%s] seed %d: %s after %.0f s more, %d/%d aboard%n", id(), seed,
                        loading.phase(), waited, loading.delivered(), loading.target());
                if (loading.phase() != Loading.Phase.DEPARTING) continue;
                run(sim, 2f);
                String name = stem + "-departing";
                out.add(new SnapshotArtifact(name + ".png", BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080,
                        yard[0], yard[1], CLOSE_CELLS).withoutRoofs().render(sim, name)));
                if (!clipped) {
                    BattleReviewFrameRenderer follow = BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080,
                            yard[0], yard[1], CLOSE_CELLS + 10f).withoutRoofs();
                    for (int frame = 0; frame < DEPART_FRAMES && loading.phase() == Loading.Phase.DEPARTING; frame++) {
                        GroundBody body = sim.convoy().body(loading.truck());
                        if (body != null) follow = follow.refocus(body.x, body.y, CLOSE_CELLS + 10f);
                        String f = String.format(Locale.ROOT, "clip-departing-seed%d/frame-%03d.png", seed, frame);
                        out.add(new SnapshotArtifact(f, follow.render(sim, f)));
                        run(sim, CLIP_FRAME_SECONDS);
                    }
                    clipped = true;
                }
            }
        }
        return out;
    }

    /** A marine squad holding the yard around the truck, rifles seeded, as the company would stand it. */
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

    /** Between the truck and the warehouse door, so both the tailgate and the crew's walk are in frame. */
    private static float[] yard(BattleSimulation sim, Loading loading, float tx, float ty) {
        float x = 0f, y = 0f;
        int n = 0;
        for (long c : loading.building().crew()) {
            if (!sim.getRoster().isLive(c)) continue;
            x += sim.world().x(c);
            y += sim.world().y(c);
            n++;
        }
        return n == 0 ? new float[]{tx, ty} : new float[]{(tx + x / n) / 2f, (ty + y / n) / 2f};
    }

    private static void run(BattleSimulation sim, float seconds) {
        int ticks = Math.round(seconds / BattleSimulation.TICK_DT);
        for (int t = 0; t < ticks; t++) sim.advance(BattleSimulation.TICK_DT);
    }
}
