package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The militarybase Raid site, framed as {@link RaidSiteLookSuite} frames heavy industry's, but for its close-up: the
 * base is the motor pool and the garrison's parade square together, so the frame stands between the two.
 */
public final class RaidMilitarySiteLookSuite extends RaidSiteLookSuite {

    public RaidMilitarySiteLookSuite() {
        super("raid-military-site-look", "militarybase", EconomicFunction.MILITARY);
    }

    @Override
    protected float[] closeUp(BattleSimulation sim, float machinesX, float machinesY) {
        float best = Float.MAX_VALUE, px = 0, py = 0;
        for (Doodad d : sim.getDoodads()) {
            if (!"doodad.parade-centre".equals(d.definitionId())) continue;
            float dx = d.cellX + 1 - machinesX, dy = d.cellY + 1 - machinesY;
            if (dx * dx + dy * dy < best) {
                best = dx * dx + dy * dy;
                px = d.cellX + 1;
                py = d.cellY + 1;
            }
        }
        if (best == Float.MAX_VALUE) return super.closeUp(sim, machinesX, machinesY);
        float span = Math.max(Math.abs(px - machinesX), Math.abs(py - machinesY) * 16f / 9f);
        float width = Math.max(BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 1.6f, span * 1.3f + 24f);
        return new float[]{(px + machinesX) / 2f, (py + machinesY) / 2f, width};
    }

    /** Cells across a structure's close-up: about forty pixels a cell at 1920, so a one-cell prop reads. */
    private static final float STRUCTURE_WIDTH = 48f;

    /**
     * A close-up of each of the base's structures, each centred on the structure so it can be found at the middle of
     * the frame: the command post's sat comms dish, its radar and the generator sets (the nearest to the command
     * post), the fuel depot's tanks and the workshops' machine tools (the nearest to the works), the armoury and the
     * medical post (their buildings' middles).
     */
    @Override
    protected List<Shot> extraShots(BattleSimulation sim, float machinesX, float machinesY) {
        List<Shot> out = new ArrayList<>();
        System.out.printf(Locale.ROOT, "[raid-military-site-look] map %d x %d%n", sim.getGrid().getWidth(), sim.getGrid().getHeight());
        for (DefensePost post : sim.getDefensePosts()) {
            System.out.printf(Locale.ROOT, "[raid-military-site-look] post %s %d,%d facing %s%n", post.tier,
                    post.anchorX, post.anchorY, post.restFacing == null ? "none" : String.format(Locale.ROOT, "%.0f", post.restFacing));
        }
        float[] hq = middleOf(sim.getTopology(), RoomPurpose.KEEP_THRONE, machinesX, machinesY);
        float hx = hq == null ? machinesX : hq[0], hy = hq == null ? machinesY : hq[1];
        // The set pieces: each close-up rings the whole set piece, not its one prop.
        add(out, sim, "satcom", List.of("doodad.listening-dish"), hx, hy, new int[]{13, 8});
        add(out, sim, "radar", List.of("doodad.radar-tower-lattice", "doodad.radar-tower"), hx, hy,
                new int[]{13, 10});
        add(out, sim, "generators", List.of("ship-engineering.generator", "doodad.industrial-generator"), hx, hy,
                new int[]{13, 9});
        add(out, sim, "fuel-depot", List.of("doodad.fuel-tank-vertical", "doodad.oxygen-sphere"),
                machinesX, machinesY, new int[]{14, 10});
        add(out, sim, "workshop", List.of("doodad.ruins-workshop-hoist"), hx, hy, null);
        float[] armoury = middleOf(sim.getTopology(), RoomPurpose.ARMORY, hx, hy);
        if (armoury != null) {
            out.add(new Shot("armoury", armoury[0], armoury[1], STRUCTURE_WIDTH));
            mark("armoury", armoury[2], armoury[3]);
        }
        float[] medical = middleOf(sim.getTopology(), RoomPurpose.PATIENT_WARD, machinesX, machinesY);
        if (medical != null) {
            out.add(new Shot("medical", medical[0], medical[1], STRUCTURE_WIDTH));
            mark("medical", medical[2], medical[3]);
        }
        return out;
    }

    /** States the size of what a close-up is centred on, cells, so the review sheet can ring it. */
    private static void mark(String name, float w, float h) {
        System.out.printf(Locale.ROOT, "[raid-military-site-look] mark %s %.0f x %.0f%n", name, w, h);
    }

    /**
     * A close-up centred on the {@code ids} prop nearest {@code (x, y)}, none where no such prop stands; ringed at the
     * size of its set piece {@code piece} (cells along its gate side and deep, as {@code MilitarySetPieces} lays it),
     * or of the prop where it is null. A set piece's props may stand indoors (the
     * generator plant's sets in their shed); a lone prop is the outdoor one, never a furnishing.
     */
    private static void add(List<Shot> out, BattleSimulation sim, String name, List<String> ids, float x, float y,
                            int[] piece) {
        Doodad best = null;
        float far = Float.MAX_VALUE;
        for (Doodad d : sim.getDoodads()) {
            if (!ids.contains(d.definitionId())) continue;
            if (piece == null && sim.getTopology().getGroundKind(d.cellX, d.cellY) == CellTopology.GroundKind.INDOOR) {
                continue;
            }
            float dx = d.cellX - x, dy = d.cellY - y;
            if (dx * dx + dy * dy < far) {
                far = dx * dx + dy * dy;
                best = d;
            }
        }
        if (best != null) {
            out.add(new Shot(name, best.cellX + best.footprintCellsX / 2f, best.cellY + best.footprintCellsY / 2f,
                    STRUCTURE_WIDTH));
            if (piece == null) mark(name, best.footprintCellsX, best.footprintCellsY);
            else mark(name, piece[0], piece[1]);
        }
    }

    /**
     * The middle of the room of {@code purpose} nearest {@code (x, y)}: the bounds of that purpose's cells within a
     * building's reach of the nearest one; null where the map has none.
     */
    private static float[] middleOf(CellTopology topology, RoomPurpose purpose, float x, float y) {
        int w = topology.getWidth(), h = topology.getHeight();
        int nx = -1, ny = -1;
        float far = Float.MAX_VALUE;
        for (int cx = 0; cx < w; cx++) {
            for (int cy = 0; cy < h; cy++) {
                if (topology.getRoomPurpose(cx, cy) != purpose) continue;
                float dx = cx - x, dy = cy - y;
                if (dx * dx + dy * dy < far) {
                    far = dx * dx + dy * dy;
                    nx = cx;
                    ny = cy;
                }
            }
        }
        if (nx < 0) return null;
        // The room the nearest cell is in: its cells of that purpose joined side to side.
        int x0 = nx, y0 = ny, x1 = nx, y1 = ny;
        boolean[][] seen = new boolean[w][h];
        ArrayDeque<int[]> open = new ArrayDeque<>();
        open.add(new int[]{nx, ny});
        seen[nx][ny] = true;
        while (!open.isEmpty()) {
            int[] cell = open.poll();
            x0 = Math.min(x0, cell[0]); y0 = Math.min(y0, cell[1]);
            x1 = Math.max(x1, cell[0]); y1 = Math.max(y1, cell[1]);
            int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] step : steps) {
                int ax = cell[0] + step[0], ay = cell[1] + step[1];
                if (ax < 0 || ay < 0 || ax >= w || ay >= h || seen[ax][ay]) continue;
                if (topology.getRoomPurpose(ax, ay) != purpose) continue;
                seen[ax][ay] = true;
                open.add(new int[]{ax, ay});
            }
        }
        return new float[]{(x0 + x1 + 1) / 2f, (y0 + y1 + 1) / 2f, x1 - x0 + 1, y1 - y0 + 1};
    }
}
