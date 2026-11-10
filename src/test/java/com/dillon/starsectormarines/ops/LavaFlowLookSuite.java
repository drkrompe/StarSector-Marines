package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.NaturalVoidZoning;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.precinct.Landform;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.Fonts;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * Lava flows on a volcanic world ({@code voids.md}, Lava flows): a volcanic Assault on a town and its country, its lava river,
 * older flows that broke from it, framed whole ({@code seed<n>-whole.png}) and at play zoom on the flows nearest the
 * map's middle ({@code -play.png}) and on the basalt causeway nearest it ({@code -causeway.png}). Look preset; nothing is played. {@code -Dsnapshot.lavaFlow.seeds=7,11} picks the
 * seeds.
 */
public final class LavaFlowLookSuite implements SnapshotSuite {

    /** The map's design: a drop south to north across the approach, which the volcanic palette skins. */
    private static final Landform DESIGN = new Landform.Drop(false, .62f);

    @Override public String id() { return "lava-flow-look"; }

    @Override public String label() { return "Lava flows on a volcanic world: the river and its older flows, look preset"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            DustLedgerMissionPreviewSuite.installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            List<SnapshotArtifact> out = new ArrayList<>();
            for (String seedText : System.getProperty("snapshot.lavaFlow.seeds", "7,11").split(",")) {
                long seed = Long.parseLong(seedText.trim());
                TargetProfile market = new TargetProfile(3, 5, 2, 1, "independent",
                        EnumSet.of(EconomicFunction.AGRICULTURE, EconomicFunction.HABITATION),
                        SurfacePalette.VOLCANIC, SettlementLink.ROAD);
                try (BattleSimulation sim = BattleSetup.createPlaceholder(seed, BattleSetup.defaultManifest(),
                        false, OperationTier.FULL_STRENGTH, RiskLevel.MEDIUM, MissionType.ASSAULT, market,
                        FlybyRoster.EMPTY, FlybyRoster.EMPTY, PrecinctPlan.Sprawl.BALANCED, DESIGN)) {
                    render(context, sim, String.format(Locale.ROOT, "seed%d", seed), out);
                }
            }
            return out;
        }
    }

    private void render(SnapshotContext context, BattleSimulation sim, String id, List<SnapshotArtifact> out)
            throws Exception {
        CellTopology topology = sim.getTopology();
        int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
        int drop = 0, flows = 0;
        float[] heat = topology.lavaFlows();
        float mx = w / 2f, my = h / 2f, best = Float.MAX_VALUE, px = mx, py = my;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            boolean lava = topology.getGroundKind(x, y) == GroundKind.VOID;
            boolean flow = heat.length > 0 && heat[y * w + x] > 0f;
            if (lava) drop++;
            if (flow) flows++;
            if (lava || flow) {
                // A frame on the flows where there are any, so their root at the melt is in view.
                float d = (x - mx) * (x - mx) + (y - my) * (y - my) + (flow ? 0f : 1e6f);
                if (d < best) { best = d; px = x + .5f; py = y + .5f; }
            }
        }
        // The town's berm: whatever the map raised (the generated volcanic map raises nothing else).
        ElevationMap elevation = sim.getGrid().elevation();
        int raised = 0;
        float bx = 0, by = 0;
        if (elevation != null) {
            for (int i = 0; i < w * h; i++) {
                if (elevation.sightHeightAt(i) <= 0) continue;
                raised++;
                bx += i % w + .5f;
                by += i / w + .5f;
            }
        }
        System.out.printf(Locale.ROOT, "[%s] %s %dx%d lava %d cells, older flows %d cells, raised %d cells, style %s%n",
                id(), id, w, h, drop, flows, raised,
                topology.naturalVoid() == null ? "none" : topology.naturalVoid().style());
        out.add(new SnapshotArtifact(id + "-whole.png", BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080,
                w / 2f, h / 2f, Math.max(w, h * 16f / 9f) + 4f).render(sim, "")));
        out.add(new SnapshotArtifact(id + "-play.png", BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080,
                px, py, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 1.5f).render(sim, "")));
        // The causeway nearest the map's middle: a crossing's basalt deck.
        String deck = NaturalVoidZoning.LAVA_RIVER.deck().surface();
        float cx = -1, cy = -1, near = Float.MAX_VALUE;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (!deck.equals(topology.getSurfaceId(x, y)) || topology.getGroundKind(x, y) == GroundKind.VOID) continue;
            float d = (x - mx) * (x - mx) + (y - my) * (y - my);
            if (d < near) { near = d; cx = x + .5f; cy = y + .5f; }
        }
        if (cx >= 0) {
            out.add(new SnapshotArtifact(id + "-causeway.png", BattleReviewFrameRenderer.look(context.modRoot(), 1920,
                    1080, cx, cy, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).render(sim, "")));
        }
        if (raised > 0) {
            out.add(new SnapshotArtifact(id + "-berm.png", BattleReviewFrameRenderer.look(context.modRoot(), 1920,
                    1080, bx / raised, by / raised, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 2f).render(sim, "")));
        }
    }
}
