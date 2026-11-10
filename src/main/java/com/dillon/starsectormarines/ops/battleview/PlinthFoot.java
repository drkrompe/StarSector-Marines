package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.TurretStateService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.StructureFootprint;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.Arrays;

/**
 * Where a hardpoint's painted plinth stands inside its footprint, and so the block its sun shadow is cast from.
 *
 * <p>Every plinth base is cut from one guide ({@code art-source/ground-turrets/make_guide.py}, {@code PLINTH_OUT}):
 * the battered walls' outer foot runs from {@link #NEAR_CELLS} to {@link #FAR_CELLS} of a
 * {@link #GUIDE_SPAN_CELLS}-cell footprint, measured from its west and north edges, and the margin past the far foot
 * held the painted drop shadow the art was made with. Raised over the whole footprint, the relief block's shadow
 * began at the footprint's east and south edges, a band of lit ground away from the painted walls. Raised over the
 * foot, it meets them.
 *
 * <p>Gathered once a frame for the height pass: the foot of each plinth still standing, with the cell its height is
 * read from ({@link MacroReliefField#plinthTopMeters}).
 */
final class PlinthFoot {

    /** The foot's west and north edges, in guide cells in from the footprint's west and north edges. */
    static final float NEAR_CELLS = 0.12f;
    /** The foot's east and south edges, in guide cells in from the footprint's west and north edges. */
    static final float FAR_CELLS = 2.72f;
    /** The footprint the guide paints the plinth in; a larger or smaller footprint scales the foot with it. */
    static final float GUIDE_SPAN_CELLS = 3f;

    /** Floats per gathered foot: {@code x0, y0, x1, y1} in cells, then the anchor cell. */
    static final int STRIDE = 6;

    private float[] feet = new float[STRIDE * 4];
    private int count;

    /**
     * The painted foot of the plinth whose footprint's south-west cell is {@code (minX, minY)}, written as
     * {@code x0, y0, x1, y1} in cell coordinates (y runs north, up the screen; the art's north is its top edge) into
     * {@code out} at {@code at}.
     */
    static void rect(int minX, int minY, int spanX, int spanY, float[] out, int at) {
        float sx = spanX / GUIDE_SPAN_CELLS;
        float sy = spanY / GUIDE_SPAN_CELLS;
        out[at] = minX + NEAR_CELLS * sx;
        out[at + 1] = minY + spanY - FAR_CELLS * sy;
        out[at + 2] = minX + FAR_CELLS * sx;
        out[at + 3] = minY + spanY - NEAR_CELLS * sy;
    }

    /** Collects the foot of every plinth standing in {@code sim}; nothing when no plinth stands. */
    void gather(BattleSimulation sim) {
        count = 0;
        CellTopology topology = sim.getTopology();
        if (topology.highestPlinthTiers() <= 0) return;
        TurretStateService turrets = sim.turretState();
        World world = sim.world();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            if (!turrets.isTurret(u)) continue;
            StructureDef structure = turrets.structure(u);
            if (structure == null || structure.plinthTiers <= 0) continue;
            int x = world.cellX(u);
            int y = world.cellY(u);
            if (topology.getPlinthTiers(x, y) <= 0) continue;
            if ((count + 1) * STRIDE > feet.length) feet = Arrays.copyOf(feet, feet.length * 2);
            int at = count * STRIDE;
            rect(StructureFootprint.minX(structure, x), StructureFootprint.minY(structure, y),
                    structure.footprintCellsX, structure.footprintCellsY, feet, at);
            feet[at + 4] = x;
            feet[at + 5] = y;
            count++;
        }
    }

    int count() { return count; }

    /** The gathered feet, {@link #STRIDE} floats each; read the first {@link #count()}. */
    float[] feet() { return feet; }
}
