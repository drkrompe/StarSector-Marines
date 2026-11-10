package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.creep.CreepGrowth;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.Fonts;

import java.util.ArrayList;
import java.util.List;

/**
 * Hive creep over a stood-up building ({@code voids.md}, buildings in depth; {@code colony-breach-nouns.md}, Creep):
 * the cutaway suite's windowed home in an open verdant town, a creep source set in the street on its windows' side and
 * the field alone ticked (nothing else is played). Young creep a minute in ({@code creep-young.png}), the walls crusted
 * once it has held them ({@code creep-old.png}), the home's roof brought down the way the creep brings it down
 * ({@code MapEditor#destroyRoof}) with its walls left standing, roof side and opened ({@code creep-gutted.png},
 * {@code creep-gutted-open.png}), then the source removed and the creep dying back ({@code creep-dying.png}). Look preset.
 */
public final class BuildingCreepLookSuite implements SnapshotSuite {

    /** Cells the view stands off the home, on its windows' side. */
    private static final float OFF = 14f;
    /** Cells out from the home's side the source is set. */
    private static final int SOURCE_OUT = 5;

    @Override public String id() { return "building-creep-look"; }

    @Override public String label() { return "Hive creep over a stood-up building, young to gutted to dying, look preset"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            DustLedgerMissionPreviewSuite.installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            List<SnapshotArtifact> out = new ArrayList<>();
            try (BattleSimulation sim = BuildingCutawayLookSuite.create(7L)) {
                Building home = BuildingCutawayLookSuite.home(sim);
                if (home == null) return out;
                float cx = (home.minX + home.maxX + 1) / 2f, cy = (home.minY + home.maxY + 1) / 2f;
                List<int[]> windows = BuildingCutawayLookSuite.windows(sim, home);
                float ox = 0f, oy = 0f;
                for (int[] w : windows) { ox += w[2] - w[0]; oy += w[3] - w[1]; }
                float len = (float) Math.sqrt(ox * ox + oy * oy);
                if (len < 1e-3f) { ox = 1f; oy = 0f; len = 1f; }
                ox /= len; oy /= len;
                float vx = cx + ox * OFF, vy = cy + oy * OFF;
                int[] at = source(sim, home, ox, oy);
                if (at == null) return out;
                System.out.println("    [creep] home " + home.id + " source at " + at[0] + "," + at[1]);
                CreepField field = sim.creepField();
                field.putSource(1L, at[0], at[1], new CreepGrowth(3f, 40f));
                int tick = 0;
                home.currentAlpha = home.targetAlpha = 1f;
                home.currentFog = home.targetFog = 0f;
                tick = run(field, tick, 30 * 20);
                out.add(shot(context, sim, "creep-young.png", vx, vy));
                tick = run(field, tick, 30 * 120);
                out.add(shot(context, sim, "creep-old.png", vx, vy));
                for (int i = 0; i < home.cellsX.length; i++) sim.mapEditor().destroyRoof(home.cellsX[i], home.cellsY[i]);
                out.add(shot(context, sim, "creep-gutted.png", vx, vy));
                home.currentAlpha = home.targetAlpha = 0f;
                out.add(shot(context, sim, "creep-gutted-open.png", vx, vy));
                home.currentAlpha = home.targetAlpha = 1f;
                field.removeSource(1L);
                run(field, tick, 30 * 25);
                out.add(shot(context, sim, "creep-dying.png", vx, vy));
            }
            return out;
        }
    }

    private static int run(CreepField field, int tick, int ticks) {
        for (int i = 0; i < ticks; i++) field.tick(tick++);
        return tick;
    }

    private static SnapshotArtifact shot(SnapshotContext context, BattleSimulation sim, String name, float vx, float vy)
            throws Exception {
        return new SnapshotArtifact(name, BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, vx, vy,
                BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).render(sim, ""));
    }

    /**
     * A walkable street cell near the home's windows' side: the nearest to the point {@link #SOURCE_OUT} cells out
     * from its middle that way, among the cells a few cells round it.
     */
    private static int[] source(BattleSimulation sim, Building home, float ox, float oy) {
        float cx = (home.minX + home.maxX + 1) / 2f, cy = (home.minY + home.maxY + 1) / 2f;
        float half = Math.abs(ox) * (home.maxX - home.minX + 1) / 2f + Math.abs(oy) * (home.maxY - home.minY + 1) / 2f;
        float px = cx + ox * (half + SOURCE_OUT), py = cy + oy * (half + SOURCE_OUT);
        int[] best = null;
        float bestD = Float.MAX_VALUE;
        for (int y = (int) py - 8; y <= (int) py + 8; y++) {
            for (int x = (int) px - 8; x <= (int) px + 8; x++) {
                if (!sim.getGrid().inBounds(x, y) || !sim.getGrid().isWalkable(x, y)
                        || sim.getTopology().getBuildingId(x, y) != 0) continue;
                float d = (x + .5f - px) * (x + .5f - px) + (y + .5f - py) * (y + .5f - py);
                if (d < bestD) { bestD = d; best = new int[]{x, y}; }
            }
        }
        return best;
    }
}
