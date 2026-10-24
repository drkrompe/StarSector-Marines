package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
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

    static final float BRICK_GAP = HardpointBricks.BRICK_GAP;
    private static final float HEADER = 22f;
    /** Below the bricks: a margin, not a caption. A part's name is the board tooltip's. */
    private static final float FOOT = 10f;
    private static final float SIDE = 12f;
    /** The header's own room: its tick and inset before the name, and its right margin. */
    private static final float HEADER_INSET = 16f;
    private static final float HEADER_END = 10f;
    /** What a header keeps beside its name: the tick and inset before it, a warning mark after. */
    private static final float LABEL_RESERVE = 40f;
    /** A fitted part's removal X, and its gap from the part it hangs beside. */
    static final float STRIP_SIZE = 14f;
    private static final float STRIP_GAP = 3f;
    /** The column right of the bricks every card keeps for its parts' X. */
    private static final float STRIP_ROOM = STRIP_GAP + STRIP_SIZE;
    private static final float SEALED_HEIGHT = 48f;
    private static final float CUT = 6f;
    private static final Color PLATE = new Color(0x08, 0x0b, 0x09, 196);
    private static final Color MISSING_PLATE = new Color(0x1c, 0x08, 0x08, 214);

    private MechSocketConsole() { }

    /**
     * The card a socket needs at the one brick size: its bricks with the column their parts'
     * X stand in, or its header where that is wider. How full it is the bricks show, not the header.
     */
    static float width(SocketDef socket) {
        float header = Fonts.ORBITRON_12_BOLD.measureWidth(socket.label()) + LABEL_RESERVE;
        if (socket.factoryLocked()) return Math.max(header, HEADER_INSET + Fonts.ORBITRON_12_BOLD.measureWidth("SEALED") + HEADER_END);
        return Math.max(header, bricks(socket, 0, 0).width() + STRIP_ROOM + SIDE * 2f);
    }

    static float height(SocketDef socket) {
        if (socket.factoryLocked()) return SEALED_HEIGHT;
        return HEADER + bricks(socket, 0, 0).height() + FOOT;
    }

    /**
     * A card's bricks where they are drawn: under the header, centred together with the column
     * on their right that their parts' X stand in.
     */
    private static HardpointBricks.Layout placed(MechLabDollCanvas.SocketDropTarget t, SocketDef socket) {
        float width = bricks(socket, 0, 0).width();
        return bricks(socket, t.left() + (t.width() - width - STRIP_ROOM) * .5f, t.top() + HEADER);
    }

    /**
     * Where a fitted part's removal X stands, as {left, top}: just right of the part's own box,
     * level with its top. One rule for every part, whether its socket holds one or several.
     */
    static float[] stripAt(float[] box) {
        return new float[] {box[2] + STRIP_GAP, box[1]};
    }

    /**
     * The box the part fitted in a single-part socket is drawn in, as {left, top, right,
     * bottom}: a weapon centred on the socket grid, an internal from its first brick.
     */
    static float[] fittedBox(MechLabDollCanvas.SocketDropTarget t, SocketDef socket,
                             MechWeaponComponent weapon, MechInternal internal) {
        HardpointBricks.Layout bricks = placed(t, socket);
        int[] block = block(socket, bricks, weapon, internal);
        float left = bricks.brickX(block[0]);
        float top = bricks.brickY(block[1]);
        return new float[] {left, top, left + bricks.span(block[2], bricks.brickWidth()),
                top + bricks.span(block[3], bricks.brickHeight())};
    }

    /** The bricks a single-part socket's part covers, as {column, row, columns, rows}. */
    private static int[] block(SocketDef socket, HardpointBricks.Layout bricks,
                               MechWeaponComponent weapon, MechInternal internal) {
        if (weapon != null) {
            int[] footprint = oriented(socket, weapon.footprintColumns, weapon.footprintRows);
            return new int[] {(bricks.columns() - footprint[0]) / 2, (bricks.rows() - footprint[1]) / 2,
                    footprint[0], footprint[1]};
        }
        int[] footprint = oriented(socket, internal.footprintColumns, internal.footprintRows);
        return new int[] {0, 0, footprint[0], footprint[1]};
    }

    private static HardpointBricks.Layout bricks(SocketDef socket, float x, float y) {
        return HardpointBricks.at(x, y, socket.gridColumns(), socket.gridRows(), HardpointBricks.brickHeight(), BRICK_GAP);
    }

    /**
     * A mount card: a quiet plate carrying the socket's name, its bricks at the doll's fixed
     * size, and what is fitted. The plate only keeps the workshop floor from showing through;
     * the bricks and the hardpoint colour carry the meaning.
     *
     * @param internal the engine fitted in the core, drawn as its footprint on the socket grid and
     *                 named in the caption; null otherwise
     * @param parts    the whole auxiliary compartment's parts at their packed places, across every
     *                 bay (the card draws those in its own bay); null for any other socket
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
        boolean compartment = socket.id().compartment();
        List<MechCompartment.Placement> here = compartment ? MechCompartment.in(parts, socket.id()) : null;
        boolean empty = installed == null && preview == null && internal == null
                && (here == null || here.isEmpty());
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
        c.text(Fonts.ORBITRON_12_BOLD, fit(socket.label(), w - LABEL_RESERVE), x + 16,
                y + (HEADER - Fonts.ORBITRON_12_BOLD.getLineHeight()) * .5f, SILVER);
        if (missing) drawWarningBadge(c, x + w - 26f, y + 5f);

        if (fixed) {
            c.text(Fonts.ORBITRON_12_BOLD, rejected ? "CANNOT FIT" : "SEALED", x + 16, y + HEADER,
                    rejected ? RED : MUTED);
        } else {
            HardpointBricks.Layout bricks = placed(t, socket);
            HardpointBricks.drawEmpty(c, bricks, socketTone);
            MechWeaponComponent shown = preview != null ? preview : installed;
            int[] shownBlock = null;
            if (shown != null) {
                shownBlock = block(socket, bricks, shown, null);
                HardpointBricks.drawFilled(c, bricks, shownBlock[0], shownBlock[1], shownBlock[2], shownBlock[3],
                        HardpointBricks.tone(shown.hardpointType));
            } else if (internal != null) {
                int[] block = block(socket, bricks, null, internal);
                HardpointBricks.drawFilled(c, bricks, block[0], block[1], block[2], block[3],
                        HardpointBricks.tone(internal.role));
                HardwareArt.draw(c, internal, bricks.brickX(block[0]), bricks.brickY(block[1]),
                        bricks.span(block[2], bricks.brickWidth()),
                        bricks.span(block[3], bricks.brickHeight()), 255);
            } else if (here != null) {
                for (MechCompartment.Placement part : here) {
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


    /**
     * Where one compartment part is drawn on the card {@code t}, as {left, top, right, bottom}:
     * the doll hangs each part's X just right of it ({@link #stripAt}).
     */
    static float[] partBox(MechLabDollCanvas.SocketDropTarget t, SocketDef socket, MechCompartment.Placement part) {
        HardpointBricks.Layout bricks = placed(t, socket);
        float left = bricks.brickX(part.column());
        float top = bricks.brickY(part.row());
        return new float[] {left, top, left + bricks.span(part.columns(), bricks.brickWidth()),
                top + bricks.span(part.rows(), bricks.brickHeight())};
    }

    /**
     * A vehicle's card ({@link VehicleDoll}): sealed, its bricks filled as one factory piece under its
     * mark and a SEALED tag; or the kit rack, one kit to a row, each with an X for the caller to hang.
     */
    static void drawVehicle(CanvasContext c, MechLabDollCanvas.SocketDropTarget t, SocketDef socket,
                            Color tone, String art, List<VehicleKit> kits,
                            boolean selected, boolean hovered, double time) {
        boolean rack = socket.id() == SocketId.AUXILIARY;
        float pulse = (float) (.5 + .5 * Math.sin(time * 3.5));
        float x = t.left(), y = t.top(), w = t.width(), h = t.height();
        plate(c, x, y, w, h, PLATE);
        HardpointBricks.rim(c, x, y, w, h, CUT, rack && kits.isEmpty() ? tone
                : new Color(tone.getRed(), tone.getGreen(), tone.getBlue(), 70), 1.2f);
        c.fillRect(x + 8, y + 7, 3, 9, rack ? tone : MUTED);
        c.text(Fonts.ORBITRON_12_BOLD, fit(socket.label(), w - 24), x + 16,
                y + (HEADER - Fonts.ORBITRON_12_BOLD.getLineHeight()) * .5f, SILVER);
        // A sealed card says so by its muted rim and its one factory piece; how full the rack is
        // its bricks show.
        HardpointBricks.Layout bricks = placed(t, socket);
        HardpointBricks.drawEmpty(c, bricks, tone);
        if (rack) {
            for (int row = 0; row < kits.size() && row < bricks.rows(); row++) {
                HardpointBricks.drawFilled(c, bricks, 0, row, 2, 1, VehicleDoll.tone(kits.get(row)));
                HardwareArt.draw(c, kits.get(row), bricks.left(), bricks.brickY(row), bricks.width(),
                        bricks.brickHeight(), 255);
            }
        } else {
            HardpointBricks.drawFilled(c, bricks, 0, 0, bricks.columns(), bricks.rows(),
                    HardpointBricks.mix(tone, 0x101311, .35f));
            HardwareArt.drawVehicle(c, art, bricks.left(), bricks.top(), bricks.width(), bricks.height(), 255);
        }
        if (selected || hovered) {
            MechLabDollCanvas.drawCornerBrackets(c, x - 2, y - 2, w + 4, h + 4,
                    new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), (int) (180 + pulse * 65)),
                    selected ? 2.4f : 1.4f, 17, 5);
        }
    }

    /** One kit's row on the rack card {@code t}, as {left, top, right, bottom}: its X stands just right of it. */
    static float[] kitBox(MechLabDollCanvas.SocketDropTarget t, SocketDef socket, int row) {
        HardpointBricks.Layout bricks = placed(t, socket);
        float top = bricks.brickY(row);
        return new float[] {bricks.left(), top, bricks.left() + bricks.width(), top + bricks.brickHeight()};
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
