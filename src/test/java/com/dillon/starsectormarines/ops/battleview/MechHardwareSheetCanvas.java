package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Review sheet for the Mech Lab hardware language: every weapon, internal and missile loader
 * as its coloured brick piece with its painted icon, all at one brick scale so footprints
 * compare, then an empty 2x2 socket of each type. Snapshot evidence only.
 */
public final class MechHardwareSheetCanvas implements CanvasProducer {
    private static final float BRICK_HEIGHT = 44f;
    private static final float GAP = 2f;
    private static final int COLUMNS = 8;
    private static final float CELL_WIDTH = 235f;
    private static final float CELL_HEIGHT = 200f;
    private static final Color NAME = new Color(0xd3d0c5);
    private static final Color DETAIL = new Color(0x91988e);

    private record Piece(String name, String detail, int columns, int rows, Color tone, Painter art) { }

    private interface Painter {
        void paint(CanvasContext c, float x, float y, float width, float height);
    }

    public MechHardwareSheetCanvas() { HardwareArt.preload(); }

    @Override
    public void draw(CanvasContext c) {
        float width = c.metrics().surfaceWidth(), height = c.metrics().surfaceHeight();
        c.fillRect(0, 0, width, height, new Color(0x0c, 0x10, 0x0f));
        c.text(Fonts.ORBITRON_20_BOLD, "MECH LAB HARDWARE", 30, 18, new Color(0xedc273));
        List<Piece> pieces = new ArrayList<>();
        for (MechWeaponComponent weapon : MechWeaponComponent.values()) {
            pieces.add(new Piece(weapon.displayName(), weapon.hardpointType + "  " + weapon.footprintColumns + "x"
                    + weapon.footprintRows, weapon.footprintColumns, weapon.footprintRows,
                    HardpointBricks.tone(weapon.hardpointType),
                    (cc, x, y, w, h) -> HardwareArt.draw(cc, weapon, x, y, w, h, 255)));
        }
        for (MechInternal internal : MechInternal.values()) {
            if (internal.role == MechInternal.Role.PLATING) continue;
            pieces.add(new Piece(internal.displayName(), internal.role + "  " + internal.footprintColumns + "x"
                    + internal.footprintRows, internal.footprintColumns, internal.footprintRows,
                    HardpointBricks.tone(internal.role),
                    (cc, x, y, w, h) -> HardwareArt.draw(cc, internal, x, y, w, h, 255)));
        }
        for (int i = 0; i < pieces.size(); i++) {
            Piece piece = pieces.get(i);
            float cx = 30 + (i % COLUMNS) * CELL_WIDTH, cy = 56 + (i / COLUMNS) * CELL_HEIGHT;
            HardpointBricks.Layout bricks = piece(cx, cy, piece.columns(), piece.rows());
            HardpointBricks.drawFilled(c, bricks, 0, 0, bricks.columns(), bricks.rows(), piece.tone());
            piece.art().paint(c, bricks.left(), bricks.top(), bricks.width(), bricks.height());
            c.text(Fonts.ORBITRON_12_BOLD, piece.name().toUpperCase(), cx, cy + 3 * BRICK_HEIGHT + 12, NAME);
            c.text(Fonts.ORBITRON_12_BOLD, piece.detail(), cx, cy + 3 * BRICK_HEIGHT + 28, DETAIL);
        }
        // The empty sockets follow the pieces, two to a cell.
        SocketType[] sockets = SocketType.values();
        for (int i = 0; i < sockets.length; i++) {
            int cell = pieces.size() + i / 2;
            float cx = 36 + (cell % COLUMNS) * CELL_WIDTH + (i % 2) * CELL_WIDTH * .5f;
            float cy = 56 + (cell / COLUMNS) * CELL_HEIGHT;
            HardpointBricks.Layout bricks = piece(cx, cy, 2, 2);
            Color tone = HardpointBricks.tone(sockets[i]);
            HardpointBricks.drawEmpty(c, bricks, tone);
            HardpointBricks.rim(c, bricks.left() - 6, bricks.top() - 6, bricks.width() + 12, bricks.height() + 12,
                    7f, tone, 2f);
            c.text(Fonts.ORBITRON_12_BOLD, sockets[i].toString(), cx, cy + 3 * BRICK_HEIGHT + 12, DETAIL);
        }
    }

    private static HardpointBricks.Layout piece(float x, float y, int columns, int rows) {
        return HardpointBricks.at(x, y, columns, rows, BRICK_HEIGHT, GAP);
    }
}
