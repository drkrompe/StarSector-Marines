package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/**
 * Settles the colony's lawn onto the world it stands on: every {@link GroundKind#GRASS} cell where the planet grows
 * nothing becomes that world's own {@link SurfacePalette#inlandGround()}: sand on a desert, stone on a rock, snow on
 * a frozen world. The town stands on the planet's ground, not on an olive island in it.
 *
 * <p>Lawn is laid by many hands — home yards, street verges, parks, a precinct's claim, a mission layout's
 * open ground — and each lays it without asking the planet. Rather than teach every one of them, the closing outdoor
 * pass asks once per cell, after all of them have run. It asks {@link SurfaceField#at}, not the profile, so a living
 * basin on a desert (Rav Bloom country) keeps its green floor while the desert rim around it does not.
 *
 * <p>The town's open dirt lots settle the same way: industrial yards and waste ground laid as {@link GroundKind#DIRT}
 * with no surface of their own become the planet's ground, so a dead world's town is not patched with olive squares.
 * Ground that was worked (a trench's floor, a sandbag apron, a fort yard: {@linkplain MadeGround made ground}, or a
 * cell carrying the {@code floors.dirt} surface) is dug earth, and keeps its dirt.
 *
 * <p>A cell carrying an authored surface (a farm's crop rows and pasture, {@code RuralGround}) draws that surface,
 * not its ground kind, and is left as it was laid.
 *
 * <p>Only the ground kind changes: walkability, surfaces, props and every random stream are untouched, so a living
 * world's map is exactly the map it was and a dead world's is the same map with different ground.
 */
public final class CultivatedGround {

    private CultivatedGround() { }

    /** Which cells a facility claimed as engineered ground ({@code GenContext.isMadeGround}). */
    @FunctionalInterface
    public interface MadeGround {
        boolean at(int x, int y);

        /** No cell is made ground. */
        MadeGround NONE = (x, y) -> false;
    }

    /**
     * Repaints grass the planet would not grow, and unworked dirt lots, as the planet's ground. Returns how many
     * cells changed.
     */
    public static int settle(CellTopology topology, SurfaceField field, MadeGround made) {
        int changed = 0;
        for (int y = 0; y < topology.getHeight(); y++) {
            for (int x = 0; x < topology.getWidth(); x++) {
                if (topology.getSurface(x, y) != 0) continue;
                GroundKind laid = topology.getGroundKind(x, y);
                if (laid != GroundKind.GRASS && (laid != GroundKind.DIRT || made.at(x, y))) continue;
                GroundKind ground = field.at(x, y).inlandGround();
                if (ground == GroundKind.GRASS) continue;
                topology.setGroundKind(x, y, ground);
                changed++;
            }
        }
        return changed;
    }
}
