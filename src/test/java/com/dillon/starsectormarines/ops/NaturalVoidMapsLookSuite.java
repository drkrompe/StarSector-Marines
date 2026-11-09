package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
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
 * Natural drops on generated maps ({@code voids.md}, placement in generated maps): an Assault on a remote
 * market of each palette (a garrison in the country round it), with {@code battle.mapgen.naturalVoids=1} so every map that fits is cut, the drop's look picked
 * by the palette ({@code NaturalVoidZoning}). Each case framed whole with its mission markers
 * ({@code <case>-whole.png}), on the drop ({@code <case>-drop.png}) and at play zoom on a crossing
 * ({@code <case>-crossing.png}). Look preset; nothing is played. {@code -Dsnapshot.naturalVoids.seeds=7,11} picks the
 * seeds.
 */
public final class NaturalVoidMapsLookSuite implements SnapshotSuite {

    @Override public String id() { return "natural-void-maps-look"; }

    @Override public String label() { return "Natural drops on generated maps, one per palette, look preset"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        String was = System.getProperty("battle.mapgen.naturalVoids");
        System.setProperty("battle.mapgen.naturalVoids", "1");
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            DustLedgerMissionPreviewSuite.installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            List<SnapshotArtifact> out = new ArrayList<>();
            for (String seedText : System.getProperty("snapshot.naturalVoids.seeds", "7").split(",")) {
                long seed = Long.parseLong(seedText.trim());
                for (SurfacePalette palette : SurfacePalette.values()) {
                    String id = String.format(Locale.ROOT, "%s-seed%d", palette.name().toLowerCase(Locale.ROOT), seed);
                    TargetProfile market = new TargetProfile(3, 5, 2, 1, "independent",
                            EnumSet.of(EconomicFunction.AGRICULTURE, EconomicFunction.HABITATION), palette,
                            SettlementLink.ROAD);
                    try (BattleSimulation sim = BattleSetup.createPlaceholder(seed, BattleSetup.defaultManifest(),
                            false, OperationTier.ESTABLISHED, RiskLevel.MEDIUM, MissionType.ASSAULT, market,
                            FlybyRoster.EMPTY, FlybyRoster.EMPTY, PrecinctPlan.Sprawl.REMOTE)) {
                        render(context, sim, id, out);
                    }
                }
            }
            return out;
        } finally {
            if (was == null) System.clearProperty("battle.mapgen.naturalVoids");
            else System.setProperty("battle.mapgen.naturalVoids", was);
        }
    }

    private void render(SnapshotContext context, BattleSimulation sim, String id, List<SnapshotArtifact> out)
            throws Exception {
        CellTopology topology = sim.getTopology();
        int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
        int cells = 0;
        // The drop's cell nearest the map's middle, and the crossing nearest it: walkable ground with drop
        // both sides of it, along a row or a column.
        float mx = w / 2f, my = h / 2f, best = Float.MAX_VALUE, bestCross = Float.MAX_VALUE;
        float dx = mx, dy = my, cx = -1, cy = -1;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (topology.getGroundKind(x, y) == GroundKind.VOID) {
                cells++;
                float d = (x - mx) * (x - mx) + (y - my) * (y - my);
                if (d < best) { best = d; dx = x + .5f; dy = y + .5f; }
            } else if (sim.getGrid().isWalkable(x, y) && crossing(topology, x, y, w, h)) {
                float d = (x - mx) * (x - mx) + (y - my) * (y - my);
                if (d < bestCross) { bestCross = d; cx = x + .5f; cy = y + .5f; }
            }
        }
        System.out.printf(Locale.ROOT, "[%s] %s %dx%d drop %d cells, style %s%n", id(), id, w, h, cells,
                topology.naturalVoid() == null ? "none" : topology.naturalVoid().style());
        out.add(new SnapshotArtifact(id + "-whole.png", BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080,
                w / 2f, h / 2f, Math.max(w, h * 16f / 9f) + 4f).withMissionMarkers().render(sim, "")));
        if (cells == 0) return;
        out.add(new SnapshotArtifact(id + "-drop.png", BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080,
                dx, dy, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 2f).render(sim, "")));
        if (cx < 0) return;
        out.add(new SnapshotArtifact(id + "-crossing.png", BattleReviewFrameRenderer.look(context.modRoot(), 1920,
                1080, cx, cy, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).render(sim, "")));
    }

    /** Ground with drop within four cells on both sides of it, along its row or its column. */
    private static boolean crossing(CellTopology topology, int x, int y, int w, int h) {
        return dropWithin(topology, x, y, -1, 0, w, h) && dropWithin(topology, x, y, 1, 0, w, h)
                || dropWithin(topology, x, y, 0, -1, w, h) && dropWithin(topology, x, y, 0, 1, w, h);
    }

    private static boolean dropWithin(CellTopology topology, int x, int y, int sx, int sy, int w, int h) {
        for (int i = 1; i <= 4; i++) {
            int nx = x + sx * i, ny = y + sy * i;
            if (nx < 0 || ny < 0 || nx >= w || ny >= h) return false;
            if (topology.getGroundKind(nx, ny) == GroundKind.VOID) return true;
        }
        return false;
    }
}
