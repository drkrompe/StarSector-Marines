package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.marine.StoreSlot;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.List;

/**
 * A company store slot's symbol, drawn by the same brick code the squad view and the Mech Lab
 * draw their hardware with ({@link HardpointBricks#drawFilled}), so a change to how a brick
 * looks reaches the store with no art to redraw. Each slot is the footprint and tone of the
 * piece it teaches: a marine weapon's yellow 1x2 and an armor's grey one ({@link KitBrick}),
 * a mech weapon's 2x2, the three internal roles side by side, a chassis, a vehicle, a kit.
 */
public final class StoreBrickCanvas implements CanvasProducer {

    private record Piece(int column, int row, int columns, int rows, Color tone) { }

    private final int columns;
    private final int rows;
    private final List<Piece> pieces;

    private StoreBrickCanvas(int columns, int rows, List<Piece> pieces) {
        this.columns = columns;
        this.rows = rows;
        this.pieces = pieces;
    }

    /** Whether a slot has a symbol: the shuttle parts are not drawn as bricks. */
    public static boolean has(StoreSlot slot) {
        return slot != null && slot.section() != StoreSlot.Section.BOATS;
    }

    /** The symbol for a slot, or null for one without. */
    public static StoreBrickCanvas of(StoreSlot slot) {
        if (!has(slot)) return null;
        return switch (slot) {
            case WEAPON_LOADOUT -> single(KitBrick.COLUMNS, KitBrick.ROWS, HardpointBricks.tone(KitBrick.Kind.BALLISTIC));
            case ARMOR_LOADOUT -> single(KitBrick.COLUMNS, KitBrick.ROWS, HardpointBricks.tone(KitBrick.Kind.ARMOR));
            case MECH_WEAPON -> single(2, 2, HardpointBricks.tone(KitBrick.Kind.ENERGY));
            case MECH_INTERNAL -> new StoreBrickCanvas(3, 1, List.of(
                    new Piece(0, 0, 1, 1, HardpointBricks.tone(MechInternal.Role.ENGINE)),
                    new Piece(1, 0, 1, 1, HardpointBricks.tone(MechInternal.Role.CAPACITOR_BANK)),
                    new Piece(2, 0, 1, 1, HardpointBricks.tone(MechInternal.Role.VENT_ARRAY))));
            case MECH_CHASSIS -> single(2, 3, HardpointBricks.OMNI);
            case VEHICLE -> single(3, 2, HardpointBricks.OMNI);
            case VEHICLE_KIT -> single(2, 1, HardpointBricks.tone(KitBrick.Kind.UTILITY));
            default -> null;
        };
    }

    private static StoreBrickCanvas single(int columns, int rows, Color tone) {
        return new StoreBrickCanvas(columns, rows, List.of(new Piece(0, 0, columns, rows, tone)));
    }

    @Override
    public void draw(CanvasContext context) {
        HardpointBricks.Layout layout = HardpointBricks.fit(0f, 0f, context.metrics().surfaceWidth(),
                context.metrics().surfaceHeight(), columns, rows, HardpointBricks.BRICK_GAP);
        for (Piece piece : pieces) {
            HardpointBricks.drawFilled(context, layout, piece.column(), piece.row(),
                    piece.columns(), piece.rows(), piece.tone());
        }
    }
}
