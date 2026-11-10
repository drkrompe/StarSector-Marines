package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
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
 * Lava flows on a volcanic world ({@code voids.md}, Lava flows): the same remote volcanic Assault drawn once per
 * {@code battle.mapgen.lavaFlow} option (the 40 m rift, the 4 m river, the river with its older flows, the flows
 * alone), each framed whole ({@code <option>-<seed>-whole.png}) and at play zoom on the channel's nearest stretch to
 * the map's middle ({@code -play.png}). Look preset; nothing is played. {@code -Dsnapshot.lavaFlow.seeds=7,11} picks
 * the seeds, {@code -Dsnapshot.lavaFlow.options=river,flows} the options.
 */
public final class LavaFlowLookSuite implements SnapshotSuite {

    /** The map's design: a drop south to north across the approach, which the volcanic palette skins. */
    private static final Landform DESIGN = new Landform.Drop(false, .62f);

    @Override public String id() { return "lava-flow-look"; }

    @Override public String label() { return "Lava flows on a volcanic world, one frame set per option, look preset"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        String wasFlow = System.getProperty("battle.mapgen.lavaFlow");
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            DustLedgerMissionPreviewSuite.installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            List<SnapshotArtifact> out = new ArrayList<>();
            String[] options = System.getProperty("snapshot.lavaFlow.options", "rift,river,flows,field").split(",");
            for (String seedText : System.getProperty("snapshot.lavaFlow.seeds", "7").split(",")) {
                long seed = Long.parseLong(seedText.trim());
                for (String option : options) {
                    System.setProperty("battle.mapgen.lavaFlow", option.trim());
                    String id = String.format(Locale.ROOT, "%s-seed%d", option.trim(), seed);
                    TargetProfile market = new TargetProfile(3, 5, 2, 1, "independent",
                            EnumSet.of(EconomicFunction.AGRICULTURE, EconomicFunction.HABITATION),
                            SurfacePalette.VOLCANIC, SettlementLink.ROAD);
                    try (BattleSimulation sim = BattleSetup.createPlaceholder(seed, BattleSetup.defaultManifest(),
                            false, OperationTier.ESTABLISHED, RiskLevel.MEDIUM, MissionType.ASSAULT, market,
                            FlybyRoster.EMPTY, FlybyRoster.EMPTY, PrecinctPlan.Sprawl.REMOTE, DESIGN)) {
                        render(context, sim, id, out);
                    }
                }
            }
            return out;
        } finally {
            restore("battle.mapgen.lavaFlow", wasFlow);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
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
        System.out.printf(Locale.ROOT, "[%s] %s %dx%d lava %d cells, older flows %d cells, style %s%n", id(), id, w,
                h, drop, flows, topology.naturalVoid() == null ? "none" : topology.naturalVoid().style());
        out.add(new SnapshotArtifact(id + "-whole.png", BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080,
                w / 2f, h / 2f, Math.max(w, h * 16f / 9f) + 4f).render(sim, "")));
        out.add(new SnapshotArtifact(id + "-play.png", BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080,
                px, py, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 1.5f).render(sim, "")));
    }
}
