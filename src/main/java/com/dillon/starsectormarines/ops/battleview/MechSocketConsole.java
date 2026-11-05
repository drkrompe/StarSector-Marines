package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
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
    private static final float CUT = 6f;
    private static final Color PLATE = new Color(0x08, 0x0b, 0x09, 196);
    private static final Color PLATE_SOLID = new Color(0x08, 0x0b, 0x09);
    private static final Color MISSING_PLATE = new Color(0x1c, 0x08, 0x08, 214);
    /** A part in hand snapped into a socket: a little translucent, as it is not fitted yet. */
    private static final int PREVIEW_ALPHA = 195;

    private MechSocketConsole() { }

    /**
     * The card a socket needs at the one brick size: its bricks, or its header where that is
     * wider. How full it is the bricks show, not the header.
     */
    static float width(SocketDef socket) {
        float header = Fonts.ORBITRON_12_BOLD.measureWidth(socket.label()) + LABEL_RESERVE;
        return Math.max(header, bricks(socket, 0, 0).width() + SIDE * 2f);
    }

    static float height(SocketDef socket) {
        return HEADER + bricks(socket, 0, 0).height() + FOOT;
    }

    /** A card's bricks where they are drawn: centred under the header. */
    private static HardpointBricks.Layout placed(MechLabDollCanvas.SocketDropTarget t, SocketDef socket) {
        float width = bricks(socket, 0, 0).width();
        return bricks(socket, t.left() + (t.width() - width) * .5f, t.top() + HEADER);
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
     * What a card warns of. Red is a part the machine cannot go without, yellow one it is better for.
     *
     * @param shown        false draws every socket as merely empty, for a pattern shown for sale rather than a machine being fitted
     * @param bankRequired the machine carries a gun, so a compartment with no capacitor bank keeps it off the manifest
     * @param noWeapon     the machine has no weapon anywhere, so each empty weapon point warns
     */
    record Warnings(boolean shown, boolean bankRequired, boolean noWeapon) { }

    /**
     * What a card holds that is built into the machine rather than fitted: its drawing and its colour. A
     * {@linkplain SocketDef#factoryLocked locked} socket wears a lock too; an older save's own gun looks the same but a
     * weapon can replace it, so it does not.
     */
    record Sealed(String art, Color tone) { }

    /**
     * A mount card on any machine: a quiet plate carrying the socket's name, its bricks at the doll's
     * fixed size, and what is fitted. The plate only keeps the workshop floor from showing through;
     * the bricks and the hardpoint colour carry the meaning.
     *
     * @param internal the engine fitted in the core, drawn as its footprint on the socket grid and
     *                 named in the caption; null otherwise
     * @param internalPreview whether {@code internal} is one in hand, snapped in but not yet fitted
     * @param parts    the whole auxiliary compartment's parts at their packed places, across every
     *                 bay (the card draws those in its own bay); null for any other socket
     * @param previewPart the one of {@code parts} that is in hand, snapped in but not yet fitted; or null
     * @param sealed   what is built in here, drawn over all its bricks; null where the socket is fitted
     */
    static void draw(CanvasContext c, MechLabDollCanvas.SocketDropTarget t,
                     SocketDef socket, MechWeaponComponent installed,
                     MechWeaponComponent preview, MechInternal internal, boolean internalPreview,
                     List<MechCompartment.Placement> parts, MechCompartment.Placement previewPart,
                     LayeredMechAssets assets,
                     boolean selected, boolean hovered, boolean compatible,
                     boolean rejected, boolean draggingHere, double time, Warnings warnings, Sealed sealed) {
        boolean fixed = socket.factoryLocked();
        Color accent = rejected ? RED : compatible ? FITS : selected ? AMBER : MUTED;
        Color socketTone = sealed != null ? sealed.tone() : HardpointBricks.tone(socket.type());
        boolean compartment = socket.id().compartment();
        List<MechCompartment.Placement> here = compartment ? MechCompartment.in(parts, socket.id()) : null;
        boolean empty = sealed == null && installed == null && preview == null && internal == null
                && (here == null || here.isEmpty());
        // An empty engine well, or a compartment with no capacitor bank on a machine with a gun, keeps it off the drop
        // manifest: red. An empty armor point does not, but the machine goes in with no armor: yellow. So does a machine
        // with no weapon on it at all, on each of its empty weapon points.
        boolean missing = warnings.shown() && !fixed && (socket.id() == SocketId.CORE ? empty
                : compartment && warnings.bankRequired() && !holds(parts, MechInternal.Role.CAPACITOR_BANK));
        boolean recommended = warnings.shown() && !fixed && empty
                && (socket.id() == SocketId.PLATING || warnings.noWeapon() && socket.type().weaponPoint());
        boolean warns = missing || recommended;
        Color warning = recommended ? HardpointBricks.RECOMMENDED : RED;
        float pulse = (float) (.5 + .5 * Math.sin(time * 3.5));
        float x = t.left(), y = t.top(), w = t.width(), h = t.height();
        plate(c, x, y, w, h, missing ? MISSING_PLATE : recommended ? HardpointBricks.RECOMMENDED_PLATE : PLATE);
        // An empty socket wears its hardpoint colour on the rim, so what it takes reads at a glance.
        HardpointBricks.rim(c, x, y, w, h, CUT, warns ? new Color(warning.getRed(), warning.getGreen(), warning.getBlue(),
                (int) (150 + pulse * 105)) : empty && !fixed ? socketTone
                : new Color(socketTone.getRed(), socketTone.getGreen(), socketTone.getBlue(), 70), warns ? 2f : 1.2f);
        c.fillRect(x + 8, y + 7, 3, 9, fixed || sealed != null ? MUTED : warns ? warning : socketTone);
        c.text(Fonts.ORBITRON_12_BOLD, fit(socket.label(), w - LABEL_RESERVE), x + 16,
                y + (HEADER - Fonts.ORBITRON_12_BOLD.getLineHeight()) * .5f, SILVER);
        if (warns) drawWarningBadge(c, x + w - 26f, y + 5f, warning);
        if (fixed) drawLock(c, x + w - 22f, y + 5f);

        HardpointBricks.Layout bricks = placed(t, socket);
        HardpointBricks.drawEmpty(c, bricks, socketTone);
        if (sealed != null) {
            // Its lock, its muted rim and its one factory piece say it is sealed.
            HardpointBricks.drawFilled(c, bricks, 0, 0, bricks.columns(), bricks.rows(),
                    HardpointBricks.mix(socketTone, 0x101311, .35f));
            HardwareArt.drawVehicle(c, sealed.art(), bricks.left(), bricks.top(), bricks.width(), bricks.height(), 255);
        } else if (!fixed) {
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
                        bricks.span(block[3], bricks.brickHeight()), internalPreview ? PREVIEW_ALPHA : 255);
            } else if (here != null) {
                for (MechCompartment.Placement part : here) {
                    HardpointBricks.drawFilled(c, bricks, part.column(), part.row(), part.columns(), part.rows(),
                            HardpointBricks.tone(part.part().role));
                    HardwareArt.draw(c, part.part(), bricks.brickX(part.column()), bricks.brickY(part.row()),
                            bricks.span(part.columns(), bricks.brickWidth()),
                            bricks.span(part.rows(), bricks.brickHeight()), part == previewPart ? PREVIEW_ALPHA : 255);
                }
            }
            if (shown != null && !HardwareArt.draw(c, shown, MechLabDollCanvas.mountFor(socket.id()),
                    bricks.brickX(shownBlock[0]), bricks.brickY(shownBlock[1]),
                    bricks.span(shownBlock[2], bricks.brickWidth()), bricks.span(shownBlock[3], bricks.brickHeight()),
                    preview != null ? PREVIEW_ALPHA : 255) && assets != null) {
                LayeredSpriteCache sprite = MechEquipmentGridCanvas.spriteFor(assets, shown);
                if (sprite != null) {
                    float scale = Math.min(bricks.width() * .9f / sprite.pxWidth, bricks.height() * .9f / sprite.pxHeight);
                    c.sprite(sprite.sourcePath, sprite.sprite, bricks.centerX(), bricks.centerY(),
                            sprite.pxWidth * scale, sprite.pxHeight * scale, 0,
                            new Color(255, 255, 255, preview != null ? PREVIEW_ALPHA : 255));
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
     * the box the doll lifts it off by.
     */
    static float[] partBox(MechLabDollCanvas.SocketDropTarget t, SocketDef socket, MechCompartment.Placement part) {
        HardpointBricks.Layout bricks = placed(t, socket);
        float left = bricks.brickX(part.column());
        float top = bricks.brickY(part.row());
        return new float[] {left, top, left + bricks.span(part.columns(), bricks.brickWidth()),
                top + bricks.span(part.rows(), bricks.brickHeight())};
    }

    /** A padlock, 14 px wide and 16 tall from its top-left corner: built in, and no part can go here or come off. */
    private static void drawLock(CanvasContext c, float left, float top) {
        // The shackle, then the body with its keyhole.
        c.fillRect(left + 3f, top, 8f, 2f, SILVER);
        c.fillRect(left + 3f, top, 2f, 8f, SILVER);
        c.fillRect(left + 9f, top, 2f, 8f, SILVER);
        c.fillRect(left, top + 7f, 14f, 9f, SILVER);
        c.fillRect(left + 6f, top + 10f, 2f, 3f, PLATE_SOLID);
    }

    /** A warning triangle with an exclamation mark, 18 px square at its top-left corner: red required, yellow recommended. */
    private static void drawWarningBadge(CanvasContext c, float left, float top, Color tone) {
        float size = 18f;
        c.fillQuad(left + size * .5f, top, left + size, top + size, left + size, top + size, left, top + size, tone);
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
