package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.marine.BoatGun;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * The Boat Deck's hardware in the Mech Lab's language: each boat socket is a
 * stack of bricks, and each fitting or door gun is a piece of a given
 * footprint, with its drawing over it. A fitting is drawn in its socket's
 * colour; a gun in its family's, as a mech weapon is (ballistic yellow, energy
 * red-orange, missile green), on a hardpoint in omni grey, since any family
 * goes on any hardpoint.
 *
 * <p>Presentation only. The campaign knows a fitting by id; which bricks it
 * covers and what it looks like are this class's, so a boat socket reads the
 * way a mech's does without the boat model acquiring a grid.
 */
public final class BoatHardware {

    private static final String ROOT = "graphics/ui/mech-hardware/";
    private static final Map<String, SvgAsset> ART = new HashMap<>();
    /** The shield emitter's cyan, the shield's own colour; the lab has no socket of this kind. */
    private static final Color SHIELD = new Color(0x6e, 0xbe, 0xe6);
    /** A socket's spare bricks once a part is in: shut, not empty ({@link #drawClosed}). */
    private static final Color CLOSED = new Color(0x02, 0x03, 0x03, 230);
    private static final Color HATCH = new Color(0x3a, 0x40, 0x3c, 200);

    /** A piece: its art, its footprint in bricks, and the colour it is drawn in. */
    record Piece(String art, int columns, int rows, Color tone) { }

    private BoatHardware() { }

    /** The piece's drawing, for a card or a filter chip that shows it outside its bricks; null for none. */
    public static String iconPath(BoatDoll.Kind kind, String itemId) {
        String art = piece(kind, itemId).art();
        return art == null ? null : ROOT + art + ".svg";
    }

    /** The socket kind's colour as the stores' markup names it. */
    public static String toneHex(BoatDoll.Kind kind) {
        Color tone = tone(kind);
        return String.format("#%02x%02x%02x", tone.getRed(), tone.getGreen(), tone.getBlue());
    }

    /** Columns and rows of bricks the piece covers. */
    public static int[] footprint(BoatDoll.Kind kind, String itemId) {
        Piece piece = piece(kind, itemId);
        return new int[]{piece.columns(), piece.rows()};
    }

    /** Columns and rows of bricks a socket of this kind holds. */
    public static int[] socket(BoatDoll.Kind kind) {
        return capacity(kind);
    }

    /** Draws a carried piece with its top-left at the point, at the size it has in its socket. */
    public static void drawCarried(CanvasContext c, BoatDoll.Kind kind, String itemId, float x, float y) {
        Piece piece = piece(kind, itemId);
        HardpointBricks.Layout bricks = HardpointBricks.at(x, y, piece.columns(), piece.rows(),
                HardpointBricks.brickHeight(), HardpointBricks.BRICK_GAP);
        draw(c, bricks, piece, 255);
    }

    /** The bricks a socket of this kind holds. */
    static int[] capacity(BoatDoll.Kind kind) {
        return switch (kind) {
            // Nothing is wider than two bricks, in either room.
            case HARDPOINT, PLATING, DRIVE, LIFT, CABIN, EMITTER, POWER -> new int[]{2, 2};
        };
    }

    static Color tone(BoatDoll.Kind kind) {
        return switch (kind) {
            // Any family goes on any hardpoint, so the socket is the lab's omni grey.
            case HARDPOINT -> HardpointBricks.OMNI;
            case PLATING -> HardpointBricks.OMNI;
            case DRIVE -> HardpointBricks.ENGINE;
            case LIFT -> HardpointBricks.UTILITY;
            case CABIN -> HardpointBricks.AUXILIARY;
            case EMITTER -> SHIELD;
            case POWER -> HardpointBricks.FLUX;
        };
    }

    /** A gun's family colour; the hardpoint's own omni grey for an id the catalog does not know. */
    static Color gunTone(String gunId) {
        BoatGun gun = gunId == null ? null : BoatGun.findById(gunId);
        if (gun == null) return tone(BoatDoll.Kind.HARDPOINT);
        return switch (gun.family()) {
            case BALLISTIC -> HardpointBricks.BALLISTIC;
            case ENERGY -> HardpointBricks.ENERGY;
            case MISSILE -> HardpointBricks.MISSILE;
        };
    }

    /** The piece a fitting or gun id is drawn as; an id this does not know fills its socket plainly. */
    static Piece piece(BoatDoll.Kind kind, String itemId) {
        Color tone = kind == BoatDoll.Kind.HARDPOINT ? gunTone(itemId) : tone(kind);
        String id = itemId == null ? "" : itemId;
        return switch (id) {
            case "door_heavy_mg" -> new Piece("boat-door-heavy-mg", 2, 1, tone);
            case "door_grenade_launcher" -> new Piece("boat-door-grenade", 2, 1, tone);
            case "door_arbalest" -> new Piece("boat-door-arbalest", 2, 2, tone);
            case "door_hephaestus" -> new Piece("boat-door-hephaestus", 2, 2, tone);
            case "boat_pulse_laser" -> new Piece("pulse-laser", 2, 1, tone);
            case "boat_burst_laser" -> new Piece("shoulder-laser", 2, 2, tone);
            case "boat_srm_pod" -> new Piece("srm-5", 2, 1, tone);
            case "boat_lrm_rack" -> new Piece("lrm-5", 2, 2, tone);
            case "standard_plating" -> new Piece("boat-plating-standard", 2, 1, tone);
            case "reinforced_plating" -> new Piece("boat-plating-reinforced", 2, 2, tone);
            case "armoured_plating" -> new Piece("boat-plating-armoured", 2, 2, tone);
            case "standard_drive" -> new Piece("engine-industrial", 2, 1, tone);
            case "tuned_drive" -> new Piece("engine-uprated", 2, 2, tone);
            case "uprated_drive" -> new Piece("engine-military", 2, 2, tone);
            case "standard_lift" -> new Piece("boat-hold-standard", 2, 2, tone);
            case "lift_cradle" -> new Piece("boat-hold-cradle", 2, 2, tone);
            case "heavy_cradle" -> new Piece("boat-hold-heavy-cradle", 2, 2, tone);
            case "heavy_lift_frame" -> new Piece("weight-heavy", 2, 2, tone);
            case "standard_cabin" -> new Piece("boat-hold-standard", 2, 1, tone);
            case "standard_emitter" -> new Piece("kit-sensor-pair", 2, 1, tone);
            case "wide_emitter" -> new Piece("kit-sensor-pair", 2, 2, tone);
            case "assault_emitter" -> new Piece("vehicle-systems-mast", 2, 2, tone);
            case "standard_power" -> new Piece("capacitor-factory", 2, 1, tone);
            case "capacitor_bank" -> new Piece("capacitor-double", 2, 2, tone);
            case "military_capacitors" -> new Piece("capacitor-military", 2, 2, tone);
            default -> {
                int[] whole = capacity(kind);
                yield new Piece(null, whole[0], whole[1], tone);
            }
        };
    }

    /** Draws a piece over its bricks, placed at the top-left of the layout's grid. */
    static void draw(CanvasContext c, HardpointBricks.Layout bricks, Piece piece, int alpha) {
        int columns = Math.min(bricks.columns(), piece.columns());
        int rows = Math.min(bricks.rows(), piece.rows());
        int column = (bricks.columns() - columns) / 2;
        int row = (bricks.rows() - rows) / 2;
        HardpointBricks.drawFilled(c, bricks, column, row, columns, rows, piece.tone());
        SvgAsset art = art(piece.art());
        if (art != null) {
            art.draw(c, bricks.brickX(column), bricks.brickY(row),
                    bricks.span(columns, bricks.brickWidth()), bricks.span(rows, bricks.brickHeight()),
                    new Color(255, 255, 255, alpha));
        }
    }

    /**
     * Closes the bricks a fitted piece leaves over in its socket: a socket takes one part, so the
     * spare capacity of a 2x2 socket holding a 2x1 part is shut, darker than an empty brick and
     * hatched, never left reading as room for a second part.
     */
    static void drawClosed(CanvasContext c, HardpointBricks.Layout bricks, Piece piece) {
        int columns = Math.min(bricks.columns(), piece.columns());
        int rows = Math.min(bricks.rows(), piece.rows());
        int startColumn = (bricks.columns() - columns) / 2;
        int startRow = (bricks.rows() - rows) / 2;
        float w = bricks.brickWidth(), h = bricks.brickHeight();
        for (int row = 0; row < bricks.rows(); row++) {
            for (int column = 0; column < bricks.columns(); column++) {
                if (row >= startRow && row < startRow + rows && column >= startColumn && column < startColumn + columns) continue;
                float x = bricks.brickX(column), y = bricks.brickY(row);
                c.fillRect(x + 1f, y + 1f, w - 2f, h - 2f, CLOSED);
                for (float d = -h; d < w; d += 6f) {
                    float x0 = Math.max(x + 2f, x + d), y0 = y + 2f + (x0 - (x + d));
                    float x1 = Math.min(x + w - 2f, x + d + h - 4f), y1 = y + 2f + (x1 - (x + d));
                    if (x1 > x0 && y0 < y + h - 2f) c.line(x0, y0, x1, Math.min(y1, y + h - 2f), HATCH, 1f);
                }
            }
        }
    }

    private static synchronized SvgAsset art(String name) {
        if (name == null) return null;
        return ART.computeIfAbsent(name, stem -> SvgAsset.load(ROOT + stem + ".svg"));
    }
}
