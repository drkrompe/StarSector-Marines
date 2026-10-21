package com.dillon.starsectormarines.battle.creep;

import com.dillon.starsectormarines.battle.evacuation.ColonyBreachSequence;
import com.dillon.starsectormarines.battle.evacuation.ColonyEruptionPlan;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.BattleReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The look of hive creep on the canonical colony (`hive-creep.md`).
 *
 * <p>The colony fixture is stood up and every eruption site opens on the breach
 * schedule as a creep source; only the creep field is advanced, so the town,
 * its people and the company stay where setup put them and the frames are
 * about the growth. Whole-map stills at two, four and six minutes into the
 * outbreak (the key image is the last: the colony must read as infested),
 * the same with roofs on as the game draws it, close views of a bore and of a
 * spreading edge, the pulse running toward its source, and a bore's creep
 * dying back once its source is gone.
 */
public final class HiveCreepSnapshotSuite implements SnapshotSuite {

    private static final String FIXTURE = "/battle-fixtures/colony-breach-v1.json";
    private static final int FRAME_W = 1920, FRAME_H = 1080;
    private static final int TICKS_PER_MINUTE = 1800;

    @Override public String id() { return "hive-creep"; }

    @Override public String label() { return "Hive creep: the colony infested, close views, pulse and dying-back"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        // The renderers install the tile catalogs the colony's generator places props from.
        BattleReviewFrameRenderer whole = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H);
        BattleReviewFrameRenderer roofs = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H).withRoofs();
        BattleSimulation sim = colony();
        List<ColonyEruptionPlan.Site> sites = sim.getColonyBreach().plan().sites();
        Outbreak outbreak = new Outbreak(sim, sites);
        ReviewAnnotations marks = BattleReviewAnnotations.forBattle(sim);
        List<SnapshotArtifact> out = new ArrayList<>();

        outbreak.runTo(2 * TICKS_PER_MINUTE);
        out.add(still("01-whole-map-2min.png", whole.render(sim, outbreak.caption("whole map"))));
        outbreak.runTo(4 * TICKS_PER_MINUTE);
        out.add(still("02-whole-map-4min.png", whole.render(sim, outbreak.caption("whole map"))));
        outbreak.runTo(6 * TICKS_PER_MINUTE);
        out.add(still("03-whole-map-6min.png", whole.render(sim, outbreak.caption("whole map"))));
        out.add(still("04-whole-map-6min-roofs.png", roofs.render(sim, outbreak.caption("whole map, roofs as in game"))));
        out.add(still("05-whole-map-6min-annotated.png", whole.render(sim, outbreak.caption("whole map, sites marked"), marks)));

        ColonyEruptionPlan.Site corridor = sites.get(0);
        BattleReviewFrameRenderer close = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H,
                corridor.x() + 0.5f, corridor.y() + 0.5f, 72f);
        out.add(still("06-close-bore-1.png", close.render(sim, outbreak.caption("bore 1, 72 cells across"))));
        int[] edge = outbreak.edgeNear(corridor);
        BattleReviewFrameRenderer play = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H,
                edge[0] + 0.5f, edge[1] + 0.5f, 60f);
        out.add(still("07-play-zoom-edge.png", play.render(sim, outbreak.caption("spreading edge at play zoom, 32 px a cell"))));
        BattleReviewFrameRenderer battle = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H,
                edge[0] + 0.5f, edge[1] + 0.5f, 36f);
        out.add(still("08-close-edge.png", battle.render(sim, outbreak.caption("spreading edge, 36 cells across"))));

        List<BufferedImage> pulse = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            float clock = i * 0.1f;
            pulse.add(battle.atClock(clock).render(sim, outbreak.caption(String.format(Locale.ROOT, "pulse, clock %.1fs", clock))));
        }
        battle.atClock(0f);
        out.add(SnapshotArtifact.colorAnimation("09-pulse.gif", pulse, 100));

        sim.creepField().removeSource(ColonyCreep.SITE_SOURCE_BASE + corridor.number());
        BattleReviewFrameRenderer recede = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H,
                corridor.x() + 0.5f, corridor.y() + 0.5f, 110f);
        List<BufferedImage> dying = new ArrayList<>();
        int collapsed = outbreak.tick;
        for (int i = 0; i <= 45; i++) {
            outbreak.runTo(collapsed + i * 75);
            dying.add(recede.render(sim, outbreak.caption(String.format(Locale.ROOT,
                    "bore 1 collapsed %ds ago: its creep dies back", (outbreak.tick - collapsed) / 30))));
        }
        out.add(SnapshotArtifact.colorAnimation("10-dying-back.gif", dying, 150));
        System.out.println("[hive-creep] " + outbreak.tickCost());
        return out;
    }

    private static SnapshotArtifact still(String name, BufferedImage image) {
        return new SnapshotArtifact(name, image);
    }

    /** The breach schedule replayed on the creep field alone, from the tremor at tick 0. */
    private static final class Outbreak {
        final BattleSimulation sim;
        final List<ColonyEruptionPlan.Site> sites;
        final boolean[] opened;
        int tick;
        long tickNanos, worstTickNanos;
        int ticks;

        Outbreak(BattleSimulation sim, List<ColonyEruptionPlan.Site> sites) {
            this.sim = sim;
            this.sites = sites;
            this.opened = new boolean[sites.size()];
        }

        void runTo(int target) {
            CreepField field = sim.creepField();
            for (; tick < target; tick++) {
                for (int i = 0; i < sites.size(); i++) {
                    if (opened[i] || tick < ColonyEruptionPlan.eruptionTick(sites.get(i), 0)) continue;
                    opened[i] = true;
                    ColonyCreep.siteErupted(sim, sites.get(i), tick);
                }
                long start = System.nanoTime();
                field.tick(tick);
                long spent = System.nanoTime() - start;
                tickNanos += spent;
                worstTickNanos = Math.max(worstTickNanos, spent);
                ticks++;
            }
        }

        /** What the field's own tick cost over the outbreak so far: mean and worst, in milliseconds. */
        String tickCost() {
            return String.format(Locale.ROOT, "creep tick over %d ticks: mean %.3f ms, worst %.3f ms",
                    ticks, tickNanos / 1e6 / Math.max(1, ticks), worstTickNanos / 1e6);
        }

        String caption(String view) {
            CreepSnapshot s = sim.creepField().snapshot();
            return String.format(Locale.ROOT, "hive creep | %s | %d:%02d after the tremor | %d cells creeped, %d dying, %d sources",
                    view, tick / TICKS_PER_MINUTE, (tick % TICKS_PER_MINUTE) / 30, s.coveredCells(), s.dyingCells(), s.sources().size());
        }

        /** A creeped cell beside open ground, walking out from the site: where the edge is. */
        int[] edgeNear(ColonyEruptionPlan.Site site) {
            CreepField field = sim.creepField();
            for (int r = 1; r < 120; r++) {
                int x = site.x() + r, y = site.y();
                if (field.isCreeped(x, y) && !field.isCreeped(x + 1, y) && field.coverageAt(x + 2, y) == 0) return new int[]{x, y};
            }
            return new int[]{site.x(), site.y()};
        }
    }

    private static BattleSimulation colony() throws Exception {
        String json;
        try (InputStream stream = HiveCreepSnapshotSuite.class.getResourceAsStream(FIXTURE)) {
            if (stream == null) throw new IllegalStateException("Missing fixture " + FIXTURE);
            json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        BattleFixture parsed = BattleFixtureJson.fromJson(new JSONObject(json));
        BattleSimulation sim = (parsed instanceof BattleLaunchFixture launch ? launch.construction() : parsed).build();
        ColonyBreachSequence breach = sim.getColonyBreach();
        if (breach == null) throw new IllegalStateException("The colony fixture installs no breach");
        return sim;
    }
}
