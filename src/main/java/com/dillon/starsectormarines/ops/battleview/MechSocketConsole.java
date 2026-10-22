package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.CanvasContext;

import java.awt.Color;

/** Mechanical equipment wells; the caller owns geometry, selection and refit authority. */
final class MechSocketConsole {
    private static final Color SILVER = new Color(0xd3d0c5);
    private static final Color MUTED = new Color(0x91988e);
    private static final Color AMBER = new Color(0xedc273);
    private static final Color GREEN = new Color(0x78d494);
    /** Drag compatibility; kept clear of the hardpoint colours so a fit never reads as "missile". */
    private static final Color FITS = new Color(0xc8eef4);
    private static final Color RED = new Color(0xe98b83);

    /** One brick's height on the doll; every socket uses it, so card size follows capacity. */
    static final float BRICK_HEIGHT = 46f;
    static final float BRICK_GAP = 2f;
    private static final float HEADER = 22f;
    private static final float CAPTION = 22f;
    private static final float SIDE = 12f;
    private static final float MIN_WIDTH = 180f;
    private static final float SEALED_HEIGHT = 48f;
    private static final float CUT = 6f;
    private static final Color PLATE = new Color(0x08, 0x0b, 0x09, 196);

    private MechSocketConsole() { }

    /** The card a socket needs at the doll's fixed brick size: its bricks plus a label and caption. */
    static float width(SocketDef socket) {
        if (socket.factoryLocked()) return MIN_WIDTH;
        return Math.max(MIN_WIDTH, bricks(socket, 0, 0).width() + SIDE * 2f);
    }

    static float height(SocketDef socket) {
        if (socket.factoryLocked()) return SEALED_HEIGHT;
        return HEADER + bricks(socket, 0, 0).height() + CAPTION;
    }

    private static HardpointBricks.Layout bricks(SocketDef socket, float x, float y) {
        return HardpointBricks.at(x, y, socket.gridColumns(), socket.gridRows(), BRICK_HEIGHT, BRICK_GAP);
    }

    /**
     * A mount card: a quiet plate carrying the socket's name, its bricks at the doll's fixed
     * size, and what is fitted. The plate only keeps the workshop floor from showing through;
     * the bricks and the hardpoint colour carry the meaning.
     *
     * @param internal the engine, capacitor bank or vent array fitted in this well, drawn as its
     *                 footprint on the socket grid and named in the caption; null otherwise
     */
    static void draw(CanvasContext c, MechLabDollCanvas.SocketDropTarget t,
                     SocketDef socket, MechWeaponComponent installed,
                     MechWeaponComponent preview, MissileReplenisherComponent replenisher,
                     MechInternal internal, LayeredMechAssets assets,
                     boolean selected, boolean hovered, boolean compatible,
                     boolean rejected, boolean draggingHere, double time) {
        boolean fixed = socket.factoryLocked();
        Color accent = rejected ? RED : compatible ? FITS : selected ? AMBER : MUTED;
        Color socketTone = HardpointBricks.tone(socket.type());
        boolean empty = installed == null && preview == null && internal == null
                && !(socket.type() == SocketType.UTILITY && replenisher != null);
        float x = t.left(), y = t.top(), w = t.width(), h = t.height();
        plate(c, x, y, w, h, PLATE);
        // An empty socket wears its hardpoint colour on the rim, so what it takes reads at a glance.
        HardpointBricks.rim(c, x, y, w, h, CUT, empty && !fixed ? socketTone
                : new Color(socketTone.getRed(), socketTone.getGreen(), socketTone.getBlue(), 70), 1.2f);
        c.fillRect(x + 8, y + 7, 3, 9, fixed ? MUTED : socketTone);
        c.text(Fonts.ORBITRON_12_BOLD, fit(socket.label(), w - 40), x + 16,
                y + (HEADER - Fonts.ORBITRON_12_BOLD.getLineHeight()) * .5f, SILVER);

        if (fixed) {
            c.text(Fonts.ORBITRON_12_BOLD, rejected ? "CANNOT FIT" : "SEALED", x + 16, y + HEADER,
                    rejected ? RED : MUTED);
        } else {
            HardpointBricks.Layout bricks = bricks(socket, 0, 0);
            bricks = bricks(socket, x + (w - bricks.width()) * .5f, y + HEADER);
            HardpointBricks.drawEmpty(c, bricks, socketTone);
            MechWeaponComponent shown = preview != null ? preview : installed;
            int[] shownBlock = null;
            if (shown != null) {
                int[] footprint = oriented(socket, shown.footprintColumns, shown.footprintRows);
                shownBlock = new int[] { (bricks.columns() - footprint[0]) / 2,
                        (bricks.rows() - footprint[1]) / 2, footprint[0], footprint[1] };
                HardpointBricks.drawFilled(c, bricks, shownBlock[0], shownBlock[1], shownBlock[2], shownBlock[3],
                        HardpointBricks.tone(shown.hardpointType));
            } else if (internal != null) {
                int[] footprint = oriented(socket, internal.footprintColumns, internal.footprintRows);
                HardpointBricks.drawFilled(c, bricks, 0, 0, footprint[0], footprint[1], HardpointBricks.INTERNAL);
            } else if (socket.type() == SocketType.UTILITY && replenisher != null) {
                HardpointBricks.drawFilled(c, bricks, 0, 0, bricks.columns(), bricks.rows(), HardpointBricks.INTERNAL);
            }
            if (shown == null && internal != null) {
                internalGlyph(c, internal.role, bricks.centerX(), bricks.centerY(), SILVER);
            } else if (shown != null && HardwareArt.draw(c, shown,
                    bricks.brickX(shownBlock[0]), bricks.brickY(shownBlock[1]),
                    bricks.span(shownBlock[2], bricks.brickWidth()), bricks.span(shownBlock[3], bricks.brickHeight()),
                    preview != null ? 195 : 255)) {
                // Painted icon drawn.
            } else if (shown != null && assets != null) {
                LayeredSpriteCache sprite = MechEquipmentGridCanvas.spriteFor(assets, shown);
                if (sprite != null) {
                    float scale = Math.min(bricks.width() * .9f / sprite.pxWidth, bricks.height() * .9f / sprite.pxHeight);
                    c.sprite(sprite.sourcePath, sprite.sprite, bricks.centerX(), bricks.centerY(),
                            sprite.pxWidth * scale, sprite.pxHeight * scale, 0,
                            new Color(255, 255, 255, preview != null ? 195 : 255));
                }
            } else if (socket.type() == SocketType.UTILITY && replenisher != null) {
                MechReplenisherArt.draw(c, replenisher, bricks.centerX(), bricks.centerY(),
                        Math.min(bricks.width(), bricks.height()) * .9f);
            }
            String caption = draggingHere ? rejected ? "CANNOT FIT" : "RELEASE TO FIT"
                    : internal != null ? internal.displayName()
                    : socket.type() == SocketType.UTILITY ? "SELECT TO REFIT"
                    : installed != null ? installed.displayName() : socket.type().label() + " / "
                    + socket.gridColumns() + "x" + socket.gridRows();
            c.text(Fonts.ORBITRON_12_BOLD, fit(caption, w - 20), x + 10,
                    y + h - CAPTION + (CAPTION - Fonts.ORBITRON_12_BOLD.getLineHeight()) * .5f,
                    draggingHere ? accent : installed != null || internal != null ? SILVER : AMBER);
        }
        if (selected || hovered || compatible || rejected) {
            float pulse = (float) (.5 + .5 * Math.sin(time * 3.5));
            MechLabDollCanvas.drawCornerBrackets(c, x - 2, y - 2, w + 4, h + 4,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
                            (int) (180 + pulse * 65)), selected || draggingHere ? 2.4f : 1.4f, 17, 5);
        }
    }

    /** A footprint in the orientation this socket accepts it, clamped to the socket grid. */
    private static int[] oriented(SocketDef socket, int columns, int rows) {
        boolean upright = columns <= socket.gridColumns() && rows <= socket.gridRows();
        return new int[] {
                Math.min(socket.gridColumns(), upright ? columns : rows),
                Math.min(socket.gridRows(), upright ? rows : columns) };
    }

    private static void plate(CanvasContext c, float x, float y, float w, float h, Color fill) {
        c.fillRect(x + CUT, y, w - CUT * 2f, h, fill);
        c.fillQuad(x, y + CUT, x + CUT, y, x + CUT, y + h, x, y + h - CUT, fill);
        c.fillQuad(x + w - CUT, y, x + w, y + CUT, x + w, y + h - CUT, x + w - CUT, y + h, fill);
    }

    /** A line drawing per role: a turbine, a fin stack, or a fan, so the well reads as fitted. */
    private static void internalGlyph(CanvasContext c, MechInternal.Role role, float x, float y, Color color) {
        switch (role) {
            case ENGINE -> {
                c.strokeRect(x - 20, y - 12, 40, 24, color, 1.4f);
                c.line(x - 20, y, x + 20, y, color, 1.2f);
                for (int i = -1; i <= 1; i++) c.line(x + i * 10, y - 12, x + i * 10, y + 12, color, 1f);
                c.fillRect(x - 3, y - 3, 6, 6, AMBER);
            }
            case CAPACITOR_BANK, VENT_ARRAY -> {
                for (int i = -2; i <= 2; i++) c.line(x + i * 7, y - 12, x + i * 7, y + 12, color, 1.6f);
                c.line(x - 18, y + 12, x + 18, y + 12, color, 1.4f);
                if (role == MechInternal.Role.VENT_ARRAY) c.strokeRect(x - 6, y - 6, 12, 12, GREEN, 1.2f);
            }
            case PLATING -> c.strokeRect(x - 18, y - 12, 36, 24, color, 2f);
        }
    }

    private static String fit(String text, float width) {
        if (Fonts.ORBITRON_12_BOLD.measureWidth(text) <= width) return text;
        int end = text.length();
        while (end > 1 && Fonts.ORBITRON_12_BOLD.measureWidth(text.substring(0, end) + "...") > width) end--;
        return text.substring(0, end) + "...";
    }
}
