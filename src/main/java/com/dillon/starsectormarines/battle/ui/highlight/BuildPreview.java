package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.tender.BuildOption;
import com.dillon.starsectormarines.battle.tender.BuildPlot;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.tender.TenderSystem;

import java.util.ArrayList;
import java.util.List;

/**
 * The finished layout of a staged build under the placement cursor: every piece its stages would lay and the gun's
 * cell, at the facing the order would take if laid there now ({@link TenderSystem#plotFacing}). Presentation only:
 * a placement panel sets it on the {@link HighlightOverlay} each frame it renders and the doodad pass draws it as a
 * ghost; the simulation never reads it.
 *
 * @param pieces      the plot's pad cells, rim and stores
 * @param gunX        the gun's cell
 * @param gunY        the gun's cell
 * @param facingX     the plot's cardinal facing
 * @param facingY     the plot's cardinal facing
 * @param structureId the catalog structure the gun becomes
 * @param valid       whether a click here would lay it
 */
public record BuildPreview(List<BuildPlot.Piece> pieces, int gunX, int gunY, int facingX, int facingY,
                           String structureId, boolean valid) {

    /** The ghost of {@code option} centred on the cell {@code (cx, cy)}, or null when the option has no plot. */
    public static BuildPreview of(BattleSimulation sim, Tender tender, BuildOption option, int cx, int cy,
                                  boolean valid) {
        BuildPlot plot = option == null ? null : option.plot();
        if (plot == null || tender == null) return null;
        int[] facing = TenderSystem.plotFacing(sim, tender, cx + 0.5f, cy + 0.5f);
        List<BuildPlot.Piece> pieces = new ArrayList<>();
        for (BuildPlot.Stage stage : plot.stages()) pieces.addAll(plot.pieces(stage, cx, cy, facing[0], facing[1]));
        int[] gun = plot.gun(cx, cy, facing[0], facing[1]);
        return new BuildPreview(List.copyOf(pieces), gun[0], gun[1], facing[0], facing[1], option.structureId(), valid);
    }
}
