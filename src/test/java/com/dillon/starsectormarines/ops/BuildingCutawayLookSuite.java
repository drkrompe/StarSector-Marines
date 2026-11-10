package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.precinct.Landform;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
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
 * A squad inside a stood-up building as its roof is cut away ({@code voids.md}, buildings in depth): the windowed
 * building with the most windows (homes first) in an open verdant town, seen from its windows' side, with a garrison squad set in its rooms and three more in the street behind it, at play zoom with the roof on
 * ({@code cutaway-roof.png}), half faded ({@code cutaway-half.png}) and gone ({@code cutaway-open.png}),
 * with every window shot out and the roof on ({@code cutaway-windows-out.png}), then a pan past it cut away ({@code cutaway-pan-NN.png}). The roof's fade is set on the building as the visibility pass would;
 * nothing is played. Look preset.
 */
public final class BuildingCutawayLookSuite implements SnapshotSuite {

    private static final int PAN_FRAMES = 60;
    /** Cells the view stands off the home, on its windows' side, so those walls turn to the camera. */
    private static final float OFF_X = 17f;
    private static final float PAN_CELLS = 12f;

    @Override public String id() { return "building-cutaway-look"; }

    @Override public String label() { return "A squad inside a stood-up building as its roof is cut away, look preset"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            DustLedgerMissionPreviewSuite.installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            long seed = Long.getLong("snapshot.buildingCutaway.seed", 7L);
            List<SnapshotArtifact> out = new ArrayList<>();
            try (BattleSimulation sim = create(seed)) {
                Building home = home(sim);
                if (home == null) return out;
                place(sim, home);
                float cx = (home.minX + home.maxX + 1) / 2f, cy = (home.minY + home.maxY + 1) / 2f;
                // Stand the view off on the side most of its windows look out of, so those faces turn to the camera.
                List<int[]> windows = windows(sim, home);
                float ox = 0f, oy = 0f;
                for (int[] wdw : windows) { ox += wdw[2] - wdw[0]; oy += wdw[3] - wdw[1]; }
                float len = (float) Math.sqrt(ox * ox + oy * oy);
                if (len < 1e-3f) { ox = -1f; oy = -.5f; len = (float) Math.sqrt(1.25f); }
                float vx = cx + ox / len * OFF_X, vy = cy + oy / len * OFF_X;
                System.out.println("    [cutaway] " + windows.size() + " windows");
                System.out.println("    [cutaway] home " + home.id + " " + home.kind + " " + home.cellsX.length
                        + " cells at " + cx + "," + cy);
                String[] names = {"roof", "half", "open"};
                float[] alphas = {1f, .5f, 0f};
                for (int k = 0; k < names.length; k++) {
                    home.currentAlpha = home.targetAlpha = alphas[k];
                    home.currentFog = home.targetFog = 0f;
                    out.add(new SnapshotArtifact("cutaway-" + names[k] + ".png", BattleReviewFrameRenderer.look(
                            context.modRoot(), 1920, 1080, vx, vy, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).render(sim, "")));
                }
                // Every window shot out: the frames stand empty, open to a body as the simulation now has them.
                for (int[] wdw : windows) {
                    sim.damageEdgeBarrier(wdw[0], wdw[1], direction(wdw[2] - wdw[0], wdw[3] - wdw[1]), 100000);
                }
                home.currentAlpha = home.targetAlpha = 1f;
                out.add(new SnapshotArtifact("cutaway-windows-out.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        1920, 1080, vx, vy, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).render(sim, "")));
                home.currentAlpha = home.targetAlpha = 0f;
                for (int f = 0; f < PAN_FRAMES; f++) {
                    float t = f / (PAN_FRAMES - 1f) * 2f - 1f;
                    out.add(new SnapshotArtifact(String.format(Locale.ROOT, "cutaway-pan-%02d.png", f),
                            BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, vx + t * PAN_CELLS,
                                    vy + t * PAN_CELLS * .3f, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS)
                                    .render(sim, "")));
                }
            }
            return out;
        }
    }

    private static Direction direction(int dx, int dy) {
        return dx > 0 ? Direction.E : dx < 0 ? Direction.W : dy > 0 ? Direction.N : Direction.S;
    }

    static BattleSimulation create(long seed) {
        TargetProfile market = new TargetProfile(5, 5, 2, 1, "independent",
                EnumSet.of(EconomicFunction.HABITATION, EconomicFunction.MINING), SurfacePalette.VERDANT,
                SettlementLink.ROAD);
        return BattleSetup.createPlaceholder(seed, BattleSetup.defaultManifest(), false, OperationTier.ESTABLISHED,
                RiskLevel.MEDIUM, MissionType.ASSAULT, market, FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                PrecinctPlan.Sprawl.BALANCED, (Landform) null);
    }

    /** The windowed building nearest the map's middle with the most windows, homes first; else the nearest home. */
    static Building home(BattleSimulation sim) {
        Building windowed = home(sim, true);
        return windowed != null ? windowed : home(sim, false);
    }

    private static Building home(BattleSimulation sim, boolean needWindows) {
        int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
        Building best = null;
        float bestScore = -Float.MAX_VALUE;
        for (Building b : sim.getBuildings().all()) {
            if (b.kind == BuildingKind.INDUSTRIAL) continue;
            if (b.cellsX.length < 12 || b.cellsX.length > 400) continue;
            if (b.minX < 6 || b.minY < 6 || b.maxX > w - 6 || b.maxY > h - 6) continue;
            int windows = windows(sim, b).size();
            if (needWindows && windows == 0) continue;
            float dx = (b.minX + b.maxX) / 2f - w / 2f, dy = (b.minY + b.maxY) / 2f - h / 2f;
            float score = windows * 40f - (float) Math.sqrt(dx * dx + dy * dy)
                    + (b.kind == BuildingKind.RESIDENTIAL ? 1e4f : 0f);
            if (score > bestScore) { bestScore = score; best = b; }
        }
        return best;
    }

    /** A building's windows: each as the cell inside it, the cell it looks out on, and its edge facing out. */
    static List<int[]> windows(BattleSimulation sim, Building b) {
        List<int[]> out = new ArrayList<>();
        for (SharedEdgeBarrier e : sim.getGrid().getEdgeBarriers()) {
            if (e.kind() != SharedEdgeBarrier.Kind.WINDOW) continue;
            int ax = e.cellX(), ay = e.cellY(), bx = ax + e.direction().dx, by = ay + e.direction().dy;
            if (sim.getTopology().inBounds(ax, ay) && within(b, ax, ay)) out.add(new int[]{ax, ay, bx, by});
            else if (sim.getTopology().inBounds(bx, by) && within(b, bx, by)) out.add(new int[]{bx, by, ax, ay});
        }
        return out;
    }

    private static boolean within(Building b, int x, int y) {
        return x >= b.minX - 1 && x <= b.maxX + 1 && y >= b.minY - 1 && y <= b.maxY + 1;
    }

    /** Eight of the garrison spread over the home's floor, and three in the street just past its far side. */
    private static void place(BattleSimulation sim, Building home) {
        World world = sim.world();
        NavigationGrid grid = sim.getGrid();
        // The company is still aboard before it lands, so the garrison stands in: a squad's worth of defenders.
        long[] squad = sim.getRoster().factionDenseArray(Faction.DEFENDER);
        List<int[]> inside = new ArrayList<>();
        for (int i = 0; i < home.cellsX.length; i++) {
            int x = home.cellsX[i], y = home.cellsY[i];
            if (grid.isWalkable(x, y) && x > home.minX && y > home.minY) inside.add(new int[]{x, y});
        }
        List<int[]> behind = new ArrayList<>();
        for (int y = home.minY; y <= home.maxY && behind.size() < 3; y += 3) {
            for (int x = home.maxX + 1; x <= home.maxX + 3; x++) {
                if (grid.inBounds(x, y) && grid.isWalkable(x, y) && sim.getTopology().getBuildingId(x, y) == 0) {
                    behind.add(new int[]{x, y});
                    break;
                }
            }
        }
        int used = 0;
        for (long id : squad) {
            if (!world.isAlive(id)) continue;
            int[] at;
            if (used < 8 && !inside.isEmpty()) at = inside.get((used * inside.size() / 8 + inside.size() / 16) % inside.size());
            else if (used - 8 < behind.size()) at = behind.get(used - 8);
            else break;
            world.setPos(id, at[0] + .5f, at[1] + .5f);
            used++;
        }
        System.out.println("    [cutaway] placed " + used + " of " + inside.size() + " floor cells, "
                + behind.size() + " behind");
    }
}
