package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.ui.retained.CanvasContext;

/**
 * The Mech Lab's hardware as it is carried: a weapon or an internal (loaders are internals),
 * drawn as its brick piece at the doll's brick size, for the room's
 * {@link com.dillon.starsectormarines.ops.fitting.FittingDragGhost}, as {@link BoatHardware}
 * draws the Boat Deck's.
 */
public final class MechHardware {

    private MechHardware() { }

    /** Draws a carried weapon with its top-left at the point, at the size it has in its socket. */
    public static void drawCarried(CanvasContext c, MechWeaponComponent component, float x, float y) {
        HardpointBricks.Layout bricks = bricks(x, y, component.footprintColumns, component.footprintRows);
        HardpointBricks.drawFilled(c, bricks, 0, 0, bricks.columns(), bricks.rows(),
                HardpointBricks.tone(component.hardpointType));
        HardwareArt.draw(c, component, bricks.left(), bricks.top(), bricks.width(), bricks.height(), 255);
    }

    /** Draws a carried internal with its top-left at the point, at the size it has in its socket. */
    public static void drawCarried(CanvasContext c, MechInternal internal, float x, float y) {
        HardpointBricks.Layout bricks = bricks(x, y, internal.footprintColumns, internal.footprintRows);
        HardpointBricks.drawFilled(c, bricks, 0, 0, bricks.columns(), bricks.rows(),
                HardpointBricks.tone(internal.role));
        HardwareArt.draw(c, internal, bricks.left(), bricks.top(), bricks.width(), bricks.height(), 255);
    }

    private static HardpointBricks.Layout bricks(float x, float y, int columns, int rows) {
        return HardpointBricks.at(x, y, columns, rows, MechSocketConsole.BRICK_HEIGHT, MechSocketConsole.BRICK_GAP);
    }
}
