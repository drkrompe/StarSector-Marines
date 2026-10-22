package com.dillon.starsectormarines.battle.creep;

import com.dillon.starsectormarines.battle.evacuation.ColonyEruptionPlan;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hive creep on a green world (`colony-breach-nouns.md`, Creep): the canonical
 * colony stood up on a verdant surface, so its outskirts carry groves, grass and
 * fields. The town's bores open on the breach schedule and, because they do not
 * reach the groves in a few minutes, a bore opens at tick 0 under every
 * {@link #GROVE_STRIDE}th tree. Only the creep and the rules that read it
 * (roofs, trees) are advanced. Whole-map stills before, at three and at six
 * minutes, and the densest grove at play zoom and close up. The same outbreak is
 * {@code renderEvidence}'s green colony.
 */
public final class CreepVegetationSnapshotSuite implements SnapshotSuite {

    private static final String FIXTURE = "/battle-fixtures/colony-breach-v1.json";
    private static final int FRAME_W = 1920, FRAME_H = 1080;
    private static final int TICKS_PER_SECOND = 30, TICKS_PER_MINUTE = 1800;
    private static final long ROOF_SEED = 20261021L;
    /** One grove bore for this many trees. */
    static final int GROVE_STRIDE = 4;
    private static final long GROVE_SOURCE_BASE = 0x6E0E_0000L;

    @Override public String id() { return "hive-creep-vegetation"; }

    @Override public String label() { return "Hive creep on a green colony: groves, fields and grass overgrown"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        BattleReviewFrameRenderer whole = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H);
        BattleSimulation sim = verdantColony();
        Outbreak outbreak = new Outbreak(sim);
        int[] grove = densestGrove(sim);
        BattleReviewFrameRenderer play = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H,
                grove[0] + 0.5f, grove[1] + 0.5f, 60f);
        BattleReviewFrameRenderer close = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H,
                grove[0] + 0.5f, grove[1] + 0.5f, 20f);
        List<SnapshotArtifact> out = new ArrayList<>();
        out.add(new SnapshotArtifact("00-whole-map-before.png", whole.render(sim, outbreak.caption("whole map, before"))));
        outbreak.runTo(3 * TICKS_PER_MINUTE);
        out.add(new SnapshotArtifact("01-whole-map-3min.png", whole.render(sim, outbreak.caption("whole map"))));
        out.add(new SnapshotArtifact("02-grove-3min.png", play.render(sim, outbreak.caption("densest grove, 32 px a cell"))));
        outbreak.runTo(6 * TICKS_PER_MINUTE);
        out.add(new SnapshotArtifact("03-whole-map-6min.png", whole.render(sim, outbreak.caption("whole map"))));
        out.add(new SnapshotArtifact("04-grove-6min.png", play.render(sim, outbreak.caption("densest grove, 32 px a cell"))));
        out.add(new SnapshotArtifact("05-grove-6min-close.png", close.render(sim, outbreak.caption("densest grove, 96 px a cell"))));
        return out;
    }

    /** The breach schedule and the grove bores, replayed on the creep and the rules that read it. */
    public static final class Outbreak {
        final BattleSimulation sim;
        final List<ColonyEruptionPlan.Site> sites;
        final boolean[] opened;
        final CreepRoofCollapse roofs;
        int tick;

        public Outbreak(BattleSimulation sim) {
            this.sim = sim;
            this.sites = sim.getColonyBreach().plan().sites();
            this.opened = new boolean[sites.size()];
            this.roofs = new CreepRoofCollapse(ROOF_SEED);
            List<int[]> groves = groveSources(sim);
            for (int i = 0; i < groves.size(); i++) {
                sim.creepField().putSource(GROVE_SOURCE_BASE + i, groves.get(i)[0], groves.get(i)[1], CreepGrowth.BORE);
            }
        }

        public void runTo(int target) {
            CreepField field = sim.creepField();
            for (; tick < target; tick++) {
                for (int i = 0; i < sites.size(); i++) {
                    if (opened[i] || tick < ColonyEruptionPlan.eruptionTick(sites.get(i), 0)) continue;
                    opened[i] = true;
                    ColonyCreep.siteErupted(sim, sites.get(i), tick);
                }
                field.tick(tick);
                if (tick % CreepRoofCollapse.CHECK_TICKS == 0) roofs.look(sim, tick);
                if (tick % CreepVegetation.CHECK_TICKS == 0) sim.creepVegetation().look(sim, tick);
            }
        }

        String caption(String view) {
            CreepSnapshot s = sim.creepField().snapshot();
            String trees = sim.creepVegetationIfPresent() == null ? "" : " | " + sim.creepVegetation().summary();
            return String.format(Locale.ROOT, "hive creep, green colony | %s | %d:%02d | %d cells creeped%s",
                    view, tick / TICKS_PER_MINUTE, (tick % TICKS_PER_MINUTE) / TICKS_PER_SECOND, s.coveredCells(), trees);
        }
    }

    /** The open cell nearest every {@link #GROVE_STRIDE}th tree, in the order the map lists them. */
    static List<int[]> groveSources(BattleSimulation sim) {
        List<int[]> out = new ArrayList<>();
        int n = 0;
        for (Doodad d : sim.getDoodads()) {
            if (CreepVegetation.canopy(d.definitionId()) == null) continue;
            if (n++ % GROVE_STRIDE != 0) continue;
            out.add(nearestWalkable(sim.getGrid(), d.cellX + d.footprintCellsX / 2, d.cellY + d.footprintCellsY / 2));
        }
        return out;
    }

    /** The centre of the tree with the most trees round it. */
    static int[] densestGrove(BattleSimulation sim) {
        List<Doodad> trees = new ArrayList<>();
        for (Doodad d : sim.getDoodads()) if (CreepVegetation.canopy(d.definitionId()) != null) trees.add(d);
        if (trees.isEmpty()) throw new IllegalStateException("The verdant colony grew no trees");
        int[] best = null;
        int bestCount = -1;
        for (Doodad d : trees) {
            int count = 0;
            for (Doodad o : trees) if (Math.abs(o.cellX - d.cellX) <= 14 && Math.abs(o.cellY - d.cellY) <= 14) count++;
            if (count > bestCount) {
                bestCount = count;
                best = new int[]{d.cellX + d.footprintCellsX / 2, d.cellY + d.footprintCellsY / 2};
            }
        }
        return best;
    }

    private static int[] nearestWalkable(NavigationGrid grid, int x, int y) {
        for (int r = 0; r < 24; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if (grid.inBounds(x + dx, y + dy) && grid.isWalkable(x + dx, y + dy)) return new int[]{x + dx, y + dy};
                }
            }
        }
        return new int[]{x, y};
    }

    /** The canonical colony, on a verdant world. */
    public static BattleSimulation verdantColony() throws Exception {
        String json;
        try (InputStream stream = CreepVegetationSnapshotSuite.class.getResourceAsStream(FIXTURE)) {
            if (stream == null) throw new IllegalStateException("Missing fixture " + FIXTURE);
            json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        JSONObject encoded = new JSONObject(json);
        encoded.getJSONObject("targetProfile").put("surface", "VERDANT");
        BattleFixture parsed = BattleFixtureJson.fromJson(encoded);
        BattleSimulation sim = (parsed instanceof BattleLaunchFixture launch ? launch.construction() : parsed).build();
        if (sim.getColonyBreach() == null) throw new IllegalStateException("The colony fixture installs no breach");
        return sim;
    }
}
