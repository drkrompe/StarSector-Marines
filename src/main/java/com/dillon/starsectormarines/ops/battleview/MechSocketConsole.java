package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MechCompartment;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.CanvasContext;

import java.awt.Color;
import java.util.List;

/** Mechanical equipment wells; the caller owns geometry, selection and refit authority. */
final class MechSocketConsole {
    private static final Color SILVER = new Color(0xd3d0c5);
    private static final Color MUTED = new Color(0x91988e);
    private static final Color AMBER = new Color(0xedc273);
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
    private static final Color MISSING_PLATE = new Color(0x1c, 0x08, 0x08, 214);

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
     * @param internal the engine fitted in the core, drawn as its footprint on the socket grid and
     *                 named in the caption; null otherwise
     * @param parts    the auxiliary compartment's parts at their packed places; null for any other
     *                 socket
     */
    static void draw(CanvasContext c, MechLabDollCanvas.SocketDropTarget t,
                     SocketDef socket, MechWeaponComponent installed,
                     MechWeaponComponent preview, MechInternal internal,
                     List<MechCompartment.Placement> parts, LayeredMechAssets assets,
                     boolean selected, boolean hovered, boolean compatible,
                     boolean rejected, boolean draggingHere, double time) {
        boolean fixed = socket.factoryLocked();
        Color accent = rejected ? RED : compatible ? FITS : selected ? AMBER : MUTED;
        Color socketTone = HardpointBricks.tone(socket.type());
        boolean compartment = socket.id() == SocketId.AUXILIARY;
        boolean empty = installed == null && preview == null && internal == null
                && (parts == null || parts.isEmpty());
        // An empty engine well, or a compartment with no capacitor bank, keeps the mech off the
        // drop manifest; it warns.
        boolean missing = !fixed && (socket.id() == SocketId.CORE ? empty
                : compartment && !holds(parts, MechInternal.Role.CAPACITOR_BANK));
        float pulse = (float) (.5 + .5 * Math.sin(time * 3.5));
        float x = t.left(), y = t.top(), w = t.width(), h = t.height();
        plate(c, x, y, w, h, missing ? MISSING_PLATE : PLATE);
        // An empty socket wears its hardpoint colour on the rim, so what it takes reads at a glance.
        HardpointBricks.rim(c, x, y, w, h, CUT, missing ? new Color(RED.getRed(), RED.getGreen(), RED.getBlue(),
                (int) (150 + pulse * 105)) : empty && !fixed ? socketTone
                : new Color(socketTone.getRed(), socketTone.getGreen(), socketTone.getBlue(), 70), missing ? 2f : 1.2f);
        c.fillRect(x + 8, y + 7, 3, 9, fixed ? MUTED : missing ? RED : socketTone);
        c.text(Fonts.ORBITRON_12_BOLD, fit(socket.label(), w - 40), x + 16,
                y + (HEADER - Fonts.ORBITRON_12_BOLD.getLineHeight()) * .5f, SILVER);
        if (missing) drawWarningBadge(c, x + w - 26f, y + 5f);

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
                HardpointBricks.drawFilled(c, bricks, 0, 0, footprint[0], footprint[1],
                        HardpointBricks.tone(internal.role));
                HardwareArt.draw(c, internal, bricks.left(), bricks.top(),
                        bricks.span(footprint[0], bricks.brickWidth()),
                        bricks.span(footprint[1], bricks.brickHeight()), 255);
            } else if (parts != null) {
                for (MechCompartment.Placement part : parts) {
                    HardpointBricks.drawFilled(c, bricks, part.column(), part.row(), part.columns(), part.rows(),
                            HardpointBricks.tone(part.part().role));
                    HardwareArt.draw(c, part.part(), bricks.brickX(part.column()), bricks.brickY(part.row()),
                            bricks.span(part.columns(), bricks.brickWidth()),
                            bricks.span(part.rows(), bricks.brickHeight()), 255);
                }
            }
            if (shown != null && !HardwareArt.draw(c, shown,
                    bricks.brickX(shownBlock[0]), bricks.brickY(shownBlock[1]),
                    bricks.span(shownBlock[2], bricks.brickWidth()), bricks.span(shownBlock[3], bricks.brickHeight()),
                    preview != null ? 195 : 255) && assets != null) {
                LayeredSpriteCache sprite = MechEquipmentGridCanvas.spriteFor(assets, shown);
                if (sprite != null) {
                    float scale = Math.min(bricks.width() * .9f / sprite.pxWidth, bricks.height() * .9f / sprite.pxHeight);
                    c.sprite(sprite.sourcePath, sprite.sprite, bricks.centerX(), bricks.centerY(),
                            sprite.pxWidth * scale, sprite.pxHeight * scale, 0,
                            new Color(255, 255, 255, preview != null ? 195 : 255));
                }
            }
            String caption = draggingHere ? rejected ? "CANNOT FIT" : "RELEASE TO FIT"
                    : missing ? Strings.get("mechLabSocketRequired")
                    : internal != null ? internal.displayName()
                    : compartment ? Strings.format("mechLabCompartmentUsed", used(parts), socket.capacity())
                    : installed != null ? installed.displayName() : socket.type().label() + " / "
                    + socket.gridColumns() + "x" + socket.gridRows();
            c.text(Fonts.ORBITRON_12_BOLD, fit(caption, w - 20), x + 10,
                    y + h - CAPTION + (CAPTION - Fonts.ORBITRON_12_BOLD.getLineHeight()) * .5f,
                    draggingHere ? accent : missing ? RED : empty ? AMBER : SILVER);
        }
        if (selected || hovered || compatible || rejected) {
            MechLabDollCanvas.drawCornerBrackets(c, x - 2, y - 2, w + 4, h + 4,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
                            (int) (180 + pulse * 65)), selected || draggingHere ? 2.4f : 1.4f, 17, 5);
        }
    }

    /** Whether the compartment holds a part of {@code role}. */
    private static boolean holds(List<MechCompartment.Placement> parts, MechInternal.Role role) {
        if (parts == null) return false;
        for (MechCompartment.Placement part : parts) if (part.part().role == role) return true;
        return false;
    }

    private static int used(List<MechCompartment.Placement> parts) {
        int cells = 0;
        if (parts != null) for (MechCompartment.Placement part : parts) cells += part.columns() * part.rows();
        return cells;
    }

    /**
     * Where one compartment part is drawn on the card {@code t}, as {left, top, right, bottom}:
     * the doll hangs each part's X off its top-right corner.
     */
    static float[] partBox(MechLabDollCanvas.SocketDropTarget t, SocketDef socket, MechCompartment.Placement part) {
        HardpointBricks.Layout bricks = bricks(socket, 0, 0);
        bricks = bricks(socket, t.left() + (t.width() - bricks.width()) * .5f, t.top() + HEADER);
        float left = bricks.brickX(part.column());
        float top = bricks.brickY(part.row());
        return new float[] {left, top, left + bricks.span(part.columns(), bricks.brickWidth()),
                top + bricks.span(part.rows(), bricks.brickHeight())};
    }

    /** A red warning triangle with an exclamation mark, 18 px square at its top-left corner. */
    private static void drawWarningBadge(CanvasContext c, float left, float top) {
        float size = 18f;
        c.fillQuad(left + size * .5f, top, left + size, top + size, left + size, top + size, left, top + size, RED);
        c.fillRect(left + size * .5f - 1f, top + 6f, 2f, 6f, MISSING_PLATE);
        c.fillRect(left + size * .5f - 1f, top + 14f, 2f, 2f, MISSING_PLATE);
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

    private static String fit(String text, float width) {
        if (Fonts.ORBITRON_12_BOLD.measureWidth(text) <= width) return text;
        int end = text.length();
        while (end > 1 && Fonts.ORBITRON_12_BOLD.measureWidth(text.substring(0, end) + "...") > width) end--;
        return text.substring(0, end) + "...";
    }
}
