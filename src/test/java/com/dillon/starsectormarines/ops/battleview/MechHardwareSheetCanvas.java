package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.List;

/**
 * Review sheet for the Mech Lab hardware language: every weapon as its coloured brick piece
 * with its painted icon, all at one brick scale so footprints compare, then an empty 2x2
 * socket of each type. Snapshot evidence only.
 */
public final class MechHardwareSheetCanvas implements CanvasProducer {
    private static final float BRICK_HEIGHT = 58f;
    private static final float GAP = 2f;
    private static final int COLUMNS = 6;
    private static final float CELL_WIDTH = 310f;
    private static final float CELL_HEIGHT = 250f;

    public MechHardwareSheetCanvas() { HardwareArt.preload(); }

    @Override
    public void draw(CanvasContext c) {
        float width = c.metrics().surfaceWidth(), height = c.metrics().surfaceHeight();
        c.fillRect(0, 0, width, height, new Color(0x0c, 0x10, 0x0f));
        c.text(Fonts.ORBITRON_20_BOLD, "MECH LAB HARDWARE", 30, 18, new Color(0xedc273));
        MechWeaponComponent[] weapons = MechWeaponComponent.values();
        for (int i = 0; i < weapons.length; i++) {
            MechWeaponComponent weapon = weapons[i];
            float cx = 30 + (i % COLUMNS) * CELL_WIDTH, cy = 56 + (i / COLUMNS) * CELL_HEIGHT;
            HardpointBricks.Layout bricks = piece(cx, cy, weapon.footprintColumns, weapon.footprintRows);
            HardpointBricks.drawFilled(c, bricks, 0, 0, bricks.columns(), bricks.rows(),
                    HardpointBricks.tone(weapon.hardpointType));
            HardwareArt.draw(c, weapon, bricks.left(), bricks.top(), bricks.width(), bricks.height(), 255);
            c.text(Fonts.ORBITRON_12_BOLD, weapon.displayName().toUpperCase(), cx, cy + 3 * BRICK_HEIGHT + 14,
                    new Color(0xd3d0c5));
            c.text(Fonts.ORBITRON_12_BOLD, weapon.hardpointType + "  " + weapon.footprintColumns + "x"
                    + weapon.footprintRows, cx, cy + 3 * BRICK_HEIGHT + 30, new Color(0x91988e));
        }
        List<SocketType> sockets = List.of(SocketType.BALLISTIC, SocketType.MISSILE, SocketType.OMNI, SocketType.CORE);
        // The sockets share the last weapon row, two to a cell.
        float startX = 30 + (weapons.length % COLUMNS) * CELL_WIDTH;
        float cy = 56 + (weapons.length / COLUMNS) * CELL_HEIGHT;
        for (int i = 0; i < sockets.size(); i++) {
            float cx = startX + 6 + i * CELL_WIDTH * .5f;
            HardpointBricks.Layout bricks = piece(cx, cy, 2, 2);
            Color tone = HardpointBricks.tone(sockets.get(i));
            HardpointBricks.drawEmpty(c, bricks, tone);
            HardpointBricks.rim(c, bricks.left() - 6, bricks.top() - 6, bricks.width() + 12, bricks.height() + 12,
                    7f, tone, 2f);
            c.text(Fonts.ORBITRON_12_BOLD, "EMPTY " + sockets.get(i), cx, cy + 3 * BRICK_HEIGHT + 14,
                    new Color(0x91988e));
        }
    }

    private static HardpointBricks.Layout piece(float x, float y, int columns, int rows) {
        float brickWidth = BRICK_HEIGHT * HardpointBricks.BRICK_ASPECT;
        return HardpointBricks.fit(x, y, columns * brickWidth + (columns - 1) * GAP,
                rows * BRICK_HEIGHT + (rows - 1) * GAP, columns, rows, GAP);
    }
}
