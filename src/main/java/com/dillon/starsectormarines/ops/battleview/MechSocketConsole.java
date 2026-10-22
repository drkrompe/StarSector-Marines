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
    private static final Color WELL = new Color(0x0c1110);

    private MechSocketConsole() { }

    /**
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
        // Opaque backing isolates equipment from the moving, patterned workshop floor.
        bevel(c, x + 3, y + 4, w, h, new Color(0, 0, 0, 145));
        bevel(c, x, y, w, h, new Color(0x30342f));
        c.line(x + 8, y, x + w - 8, y, new Color(0x969b8e), 1.2f);
        c.line(x, y + 8, x, y + h - 8, new Color(0x707569), 1.2f);
        c.line(x + 8, y + h, x + w - 8, y + h, new Color(0x070a08), 2f);
        c.fillRect(x + 8, y + 24, w - 16, h - 44, WELL);
        c.line(x + 8, y + 24, x + w - 8, y + 24, new Color(0x070a08), 2f);
        // An empty socket wears its hardpoint colour on the rim, so what it takes reads at a glance.
        if (empty && !fixed) HardpointBricks.rim(c, x, y, w, h, 7f, socketTone, 2f);
        c.fillRect(x + 9, y + 9, 4, 7, fixed ? MUTED : socketTone);
        c.text(Fonts.ORBITRON_12_BOLD, fit(socket.label(), w - 34), x + 19, y + (24f - Fonts.ORBITRON_12_BOLD.getLineHeight()) * .5f, SILVER);

        if (fixed) {
            c.text(Fonts.ORBITRON_12_BOLD, rejected ? "CANNOT FIT" : "SEALED", x + 12, y + 31,
                    rejected ? RED : MUTED);
            for (int i = 0; i < 3; i++) c.line(x + w - 29 + i * 5, y + 30,
                    x + w - 26 + i * 5, y + 36, MUTED, 1f);
        } else {
            float iy = y + 29, ih = h - 55, iw = w - 24;
            HardpointBricks.Layout bricks = HardpointBricks.fit(x + 12, iy, iw, ih,
                    socket.gridColumns(), socket.gridRows(), 2f);
            HardpointBricks.drawEmpty(c, bricks, socketTone);
            MechWeaponComponent shown = preview != null ? preview : installed;
            if (shown != null) {
                int[] footprint = oriented(socket, shown.footprintColumns, shown.footprintRows);
                HardpointBricks.drawFilled(c, bricks, (bricks.columns() - footprint[0]) / 2,
                        (bricks.rows() - footprint[1]) / 2, footprint[0], footprint[1],
                        HardpointBricks.tone(shown.hardpointType));
            } else if (internal != null) {
                int[] footprint = oriented(socket, internal.footprintColumns, internal.footprintRows);
                HardpointBricks.drawFilled(c, bricks, 0, 0, footprint[0], footprint[1], HardpointBricks.INTERNAL);
            } else if (socket.type() == SocketType.UTILITY && replenisher != null) {
                HardpointBricks.drawFilled(c, bricks, 0, 0, bricks.columns(), bricks.rows(), HardpointBricks.INTERNAL);
            }
            if (shown == null && internal != null) {
                internalGlyph(c, internal.role, bricks.centerX(), bricks.centerY(), SILVER);
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
            c.text(Fonts.ORBITRON_12_BOLD, fit(caption, w - 23), x + 12, y + h - 20f + (20f - Fonts.ORBITRON_12_BOLD.getLineHeight()) * .5f,
                    draggingHere ? accent : installed != null || internal != null ? SILVER : AMBER);
        }
        if (selected || hovered || compatible || rejected) {
            float pulse = (float) (.5 + .5 * Math.sin(time * 3.5));
            MechLabDollCanvas.drawCornerBrackets(c, x + 2, y + 2, w - 4, h - 4,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
                            (int) (180 + pulse * 65)), selected || draggingHere ? 2.4f : 1.4f, 17, 5);
            if (compatible && !fixed) {
                float scanY = y + 28 + (float) ((time * .32) % 1) * (h - 52);
                c.line(x + 10, scanY, x + w - 10, scanY, new Color(200, 238, 244, 70), 1f);
            }
        }
        // Physical screw slots and contact teeth keep the silhouette from reading as a tooltip.
        c.line(x + w - 14, y + 9, x + w - 10, y + 13, MUTED, 1f);
        for (int i = 0; i < 4; i++) c.fillRect(x + w - 29 + i * 5, y + h - 3, 2, 3,
                selected ? AMBER : new Color(0x697160));
    }

    /** A footprint in the orientation this socket accepts it, clamped to the socket grid. */
    private static int[] oriented(SocketDef socket, int columns, int rows) {
        boolean upright = columns <= socket.gridColumns() && rows <= socket.gridRows();
        return new int[] {
                Math.min(socket.gridColumns(), upright ? columns : rows),
                Math.min(socket.gridRows(), upright ? rows : columns) };
    }

    private static void bevel(CanvasContext c, float x, float y, float w, float h, Color fill) {
        c.fillRect(x + 7, y, w - 14, h, fill);
        c.fillQuad(x, y + 7, x + 7, y, x + 7, y + h, x, y + h - 7, fill);
        c.fillQuad(x + w - 7, y, x + w, y + 7, x + w, y + h - 7, x + w - 7, y + h, fill);
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
