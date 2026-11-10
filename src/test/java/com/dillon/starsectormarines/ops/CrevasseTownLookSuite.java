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
 * The crevasse town ({@code voids.md}, the crevasse town): maps whose design shelters the town in a cleft
 * ({@code Landform.Shelter}, its walls along the long edges), the settlement on the floor and its terrace houses set
 * into the walls' feet. On a frozen world a great crevasse in ice; the verdant case is the same design in the
 * ravine's rock. An Assault on a remote garrison and on a balanced town, each framed whole
 * with its mission markers ({@code <case>-whole.png}), on the town's floor ({@code <case>-floor.png}) and at play zoom
 * at the foot of a wall nearest the map's middle ({@code <case>-wall-foot.png}). Look preset; nothing is played.
 * {@code -Dsnapshot.crevasseTown.seeds=7,11} picks the seeds.
 */
public final class CrevasseTownLookSuite implements SnapshotSuite {

    private record Case(String id, int marketSize, PrecinctPlan.Sprawl sprawl, SurfacePalette palette) { }

    private static final List<Case> CASES = List.of(
            new Case("remote", 3, PrecinctPlan.Sprawl.REMOTE, SurfacePalette.FROZEN),
            new Case("town", 5, PrecinctPlan.Sprawl.BALANCED, SurfacePalette.FROZEN),
            new Case("town-verdant", 5, PrecinctPlan.Sprawl.BALANCED, SurfacePalette.VERDANT));

    @Override public String id() { return "crevasse-town-look"; }

    @Override public String label() { return "A frozen town on the floor of a great crevasse, look preset"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            DustLedgerMissionPreviewSuite.installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            List<SnapshotArtifact> out = new ArrayList<>();
            for (String seedText : System.getProperty("snapshot.crevasseTown.seeds", "7").split(",")) {
                long seed = Long.parseLong(seedText.trim());
                for (Case c : CASES) {
                    String id = String.format(Locale.ROOT, "%s-seed%d", c.id(), seed);
                    TargetProfile market = new TargetProfile(c.marketSize(), 5, 2, 1, "independent",
                            EnumSet.of(EconomicFunction.HABITATION, EconomicFunction.MINING), c.palette(),
                            SettlementLink.ROAD);
                    try (BattleSimulation sim = BattleSetup.createPlaceholder(seed, BattleSetup.defaultManifest(),
                            false, OperationTier.ESTABLISHED, RiskLevel.MEDIUM, MissionType.ASSAULT, market,
                            FlybyRoster.EMPTY, FlybyRoster.EMPTY, c.sprawl(), new Landform.Shelter(true))) {
                        render(context, sim, id, out);
                    }
                }
            }
            return out;
        }
    }

    private void render(SnapshotContext context, BattleSimulation sim, String id, List<SnapshotArtifact> out)
            throws Exception {
        CellTopology topology = sim.getTopology();
        int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
        int cells = 0;
        float mx = w / 2f, my = h / 2f, best = Float.MAX_VALUE, fx = mx, fy = my;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (topology.getGroundKind(x, y) != GroundKind.VOID) continue;
            cells++;
            // A wall's foot: rise next to walkable floor.
            boolean foot = false;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = x + d[0], ny = y + d[1];
                foot |= nx >= 0 && ny >= 0 && nx < w && ny < h && sim.getGrid().isWalkable(nx, ny);
            }
            if (!foot) continue;
            float dd = (x - mx) * (x - mx) + (y - my) * (y - my);
            if (dd < best) { best = dd; fx = x + .5f; fy = y + .5f; }
        }
        System.out.printf(Locale.ROOT, "[%s] %s %dx%d walls %d cells, rise %s%n", id(), id, w, h, cells,
                topology.naturalRise() == null ? "none" : topology.naturalRise().style());
        out.add(new SnapshotArtifact(id + "-whole.png", BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080,
                w / 2f, h / 2f, Math.max(w, h * 16f / 9f) + 4f).withMissionMarkers().render(sim, "")));
        if (cells == 0) return;
        out.add(new SnapshotArtifact(id + "-floor.png", BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080,
                mx, my, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 2f).render(sim, "")));
        // Toward the floor from the wall, so the foot sits low in the frame with the town before it.
        float tx = fx + (mx - fx) * .25f, ty = fy + (my - fy) * .25f;
        out.add(new SnapshotArtifact(id + "-wall-foot.png", BattleReviewFrameRenderer.look(context.modRoot(), 1920,
                1080, tx, ty, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).render(sim, "")));
    }
}
