package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.ops.fitting.BoardHover;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.CanvasContext;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Boat Deck's doll: the Mech Lab's socket schematic over a boat.
 *
 * <p>Each slot is a socket console — a plate with the slot's name, its
 * capacity as a stack of bricks at the lab's fixed brick size, and the piece
 * fitted there drawn over them in the socket's colour. The airframe is the
 * core every socket is wired to: door guns across the top, port to starboard
 * in the order their mounts sit on the hull, plating and drive on either flank
 * and the hold beneath. Where a gun actually sits is shown on the boat in her
 * berth, not here; this is the parts board.
 */
final class BoatDollPanel {

    /** The share of the canvas the board takes; the camera stands the boat in the rest. */
    static final float SHARE = 0.56f;

    private static final float MARGIN = 18f;
    private static final float GAP_X = 18f;
    private static final float GAP_Y = 30f;
    /** @see HardpointBricks#BRICK_HEIGHT */
    static final float BRICK_HEIGHT = HardpointBricks.BRICK_HEIGHT;
    static final float BRICK_GAP = HardpointBricks.BRICK_GAP;
    private static final float HEADER = 22f;
    /** Below the bricks: a margin, not a caption. A part's name is the board tooltip's. */
    private static final float FOOT = 10f;
    private static final float SIDE = 12f;
    private static final float HEADER_INSET = 16f;
    private static final float HEADER_END = 10f;
    private static final float CORE_WIDTH = 150f;
    private static final float CORE_HEIGHT = 96f;
    private static final float CUT = 6f;

    private static final Color BOARD = new Color(0x05, 0x08, 0x0a, 214);
    private static final Color GRID = new Color(0x5e, 0x96, 0xd2, 20);
    private static final Color EDGE = new Color(0x8e, 0x9a, 0x8c, 120);
    private static final Color PLATE = new Color(0x08, 0x0b, 0x09, 196);
    private static final Color SILVER = new Color(0xd3, 0xd0, 0xc5);
    private static final Color MUTED = new Color(0x91, 0x98, 0x8e);
    private static final Color AMBER = new Color(0xed, 0xc2, 0x73);
    private static final Color SHADOW = new Color(0, 0, 0, 190);
    private static final Color TAKES = new Color(0x7e, 0xe0, 0x9a);
    private static final Color REFUSES = new Color(0xe8, 0x5a, 0x4a);

    /** A piece being carried over a socket: which socket, and whether it would take it. */
    record Drop(String key, boolean valid) { }

    private BoatDollPanel() { }

    /** One socket as drawn this frame, for hit-testing. */
    record CardTarget(String key, float left, float top, float width, float height) {
        boolean contains(float x, float y) {
            return x >= left && x <= left + width && y >= top && y <= top + height;
        }
        float centerX() { return left + width * 0.5f; }
        float centerY() { return top + height * 0.5f; }
        float right() { return left + width; }
        float bottom() { return top + height; }
    }

    /**
     * Draws the board over the left of the canvas and returns its sockets.
     *
     * @param hovered the key of the socket under the pointer, or null
     * @param drop    the socket a carried piece is over, or null
     */
    static List<CardTarget> draw(CanvasContext c, BoatDoll doll, float canvasWidth, float canvasHeight,
                                 String hovered, Drop drop, BoardHover hover, double seconds) {
        float width = canvasWidth * SHARE;
        board(c, width, canvasHeight);

        List<BoatDoll.Slot> guns = new ArrayList<>();
        BoatDoll.Slot plating = null, drive = null, lift = null;
        for (BoatDoll.Slot slot : doll.slots()) {
            switch (slot.kind()) {
                case HARDPOINT -> guns.add(slot);
                case PLATING -> plating = slot;
                case DRIVE -> drive = slot;
                case LIFT -> lift = slot;
            }
        }
        // Port guns to the left of starboard ones, so the board reads the way the boat is built.
        guns.sort(Comparator.comparingDouble(BoatDoll.Slot::localX));

        float gunHeight = guns.isEmpty() ? 0f : height(BoatDoll.Kind.HARDPOINT);
        float flankHeight = Math.max(drive == null ? CORE_HEIGHT : 0f,
                Math.max(plating != null ? height(plating.kind()) : 0f, drive != null ? height(drive.kind()) : 0f));
        float liftHeight = lift != null ? height(lift.kind()) : 0f;
        float total = gunHeight + (gunHeight > 0f ? GAP_Y : 0f) + flankHeight
                + (liftHeight > 0f ? GAP_Y + liftHeight : 0f);
        float top = Math.max(MARGIN + 26f, (canvasHeight - total) * 0.5f);
        float centerX = width * 0.5f;
        float flankY = top + gunHeight + (gunHeight > 0f ? GAP_Y : 0f) + flankHeight * 0.5f;

        // The drive is the board's centre, as the engine is on every machine's doll. Only a boat
        // with no drive keeps a plain airframe plate there for the other sockets to wire into.
        CardTarget core = new CardTarget(null, centerX - CORE_WIDTH * 0.5f, flankY - CORE_HEIGHT * 0.5f,
                CORE_WIDTH, CORE_HEIGHT);
        boolean plainCore = drive == null;
        List<Placed> placed = new ArrayList<>();
        if (drive != null) {
            float w = width(drive), h = height(drive.kind());
            core = new CardTarget(drive.key(), centerX - w * 0.5f, flankY - h * 0.5f, w, h);
            placed.add(new Placed(drive, core));
        }
        float gunWidth = 0f;
        for (BoatDoll.Slot gun : guns) gunWidth = Math.max(gunWidth, width(gun));
        float gunSpan = gunWidth * guns.size() + GAP_X * Math.max(0, guns.size() - 1);
        for (int i = 0; i < guns.size(); i++) {
            float left = centerX - gunSpan * 0.5f + i * (gunWidth + GAP_X);
            placed.add(new Placed(guns.get(i), new CardTarget(guns.get(i).key(), left, top, gunWidth, gunHeight)));
        }
        if (plating != null) {
            float w = width(plating), h = height(plating.kind());
            placed.add(new Placed(plating, new CardTarget(plating.key(), core.left() - GAP_X - w,
                    flankY - h * 0.5f, w, h)));
        }
        if (lift != null) {
            float w = width(lift), h = height(lift.kind());
            placed.add(new Placed(lift, new CardTarget(lift.key(), centerX - w * 0.5f,
                    core.bottom() + GAP_Y, w, h)));
        }

        for (int i = 0; i < placed.size(); i++) {
            if (placed.get(i).target() != core) wire(c, placed.get(i), core, seconds, i);
        }
        if (plainCore) core(c, core, doll);
        List<CardTarget> targets = new ArrayList<>(placed.size());
        BoatDoll.Slot pointedAt = null;
        for (Placed socket : placed) {
            console(c, socket.target(), socket.slot(), socket.slot().key().equals(hovered), seconds);
            if (drop != null && socket.slot().key().equals(drop.key())) dropMark(c, socket.target(), drop.valid());
            if (socket.slot().key().equals(hovered)) pointedAt = socket.slot();
            targets.add(socket.target());
        }
        // The fitted part's name is a hover away, as on the Mech Lab's board, not a caption.
        if (pointedAt != null) {
            BoardTooltip.draw(c, hover, canvasWidth, canvasHeight, pointedAt.fitted(),
                    pointedAt.title() + "  ·  " + pointedAt.tier());
        }
        return List.copyOf(targets);
    }

    private record Placed(BoatDoll.Slot slot, CardTarget target) { }

    private static HardpointBricks.Layout bricks(BoatDoll.Kind kind, float x, float y) {
        int[] capacity = BoatHardware.capacity(kind);
        return HardpointBricks.at(x, y, capacity[0], capacity[1], BRICK_HEIGHT, BRICK_GAP);
    }

    /** A socket's card: its bricks, or its header where that is wider, as on the Mech Lab's board. */
    private static float width(BoatDoll.Slot slot) {
        float header = HEADER_INSET + Fonts.ORBITRON_12_BOLD.measureWidth(slot.title()) + HEADER_END;
        return Math.max(header, bricks(slot.kind(), 0f, 0f).width() + SIDE * 2f);
    }

    private static float height(BoatDoll.Kind kind) {
        return HEADER + bricks(kind, 0f, 0f).height() + FOOT;
    }

    /** A dark drafting board, so the sockets read against one ground and not the deck. */
    private static void board(CanvasContext c, float width, float height) {
        c.fillRect(0f, 0f, width, height, BOARD);
        for (float x = 24f; x < width; x += 24f) c.line(x, 0f, x, height, GRID, 1f);
        for (float y = 24f; y < height; y += 24f) c.line(0f, y, width, y, GRID, 1f);
        c.line(width, 0f, width, height, EDGE, 2f);
        for (int i = 1; i <= 6; i++) {
            c.line(width + i * 2f, 0f, width + i * 2f, height, new Color(0x05, 0x08, 0x0a, 120 - i * 18), 2f);
        }
    }

    /** The airframe every socket feeds: its tail name and pattern, on the plate the sockets share. */
    private static void core(CanvasContext c, CardTarget t, BoatDoll doll) {
        plate(c, t.left(), t.top(), t.width(), t.height());
        HardpointBricks.rim(c, t.left(), t.top(), t.width(), t.height(), CUT, MUTED, 1.4f);
        c.fillRect(t.left() + 8f, t.top() + 7f, 3f, 9f, SILVER);
        c.text(Fonts.ORBITRON_12_BOLD, Strings.get("boatDeckDollAirframe"), t.left() + 16f,
                t.top() + (HEADER - Fonts.ORBITRON_12_BOLD.getLineHeight()) * .5f, SILVER);
        c.text(Fonts.INSIGNIA_15_AA, clip(doll.name(), t.width() - 20f, Fonts.INSIGNIA_15_AA),
                t.left() + 10f, t.top() + 34f, AMBER);
        c.text(Fonts.ORBITRON_10, clip(doll.pattern(), t.width() - 20f, Fonts.ORBITRON_10),
                t.left() + 10f, t.top() + 60f, MUTED);
    }

    /**
     * One socket: the plate, its name, and its bricks with the fitted piece over
     * them. Selected or under the pointer, it wears the lab's amber brackets.
     */
    private static void console(CanvasContext c, CardTarget t, BoatDoll.Slot slot, boolean hot, double seconds) {
        float x = t.left(), y = t.top(), w = t.width(), h = t.height();
        Color tone = BoatHardware.tone(slot.kind());
        plate(c, x, y, w, h);
        HardpointBricks.rim(c, x, y, w, h, CUT, withAlpha(tone, 70), 1.2f);
        c.fillRect(x + 8f, y + 7f, 3f, 9f, tone);
        c.text(Fonts.ORBITRON_12_BOLD, clip(slot.title(), w - 24f, Fonts.ORBITRON_12_BOLD), x + 16f,
                y + (HEADER - Fonts.ORBITRON_12_BOLD.getLineHeight()) * .5f, SILVER);
        HardpointBricks.Layout bricks = bricks(slot.kind(), 0f, 0f);
        bricks = bricks(slot.kind(), x + (w - bricks.width()) * .5f, y + HEADER);
        HardpointBricks.drawEmpty(c, bricks, tone);
        BoatHardware.draw(c, bricks, BoatHardware.piece(slot.kind(), slot.itemId()), 255);
        if (slot.selected() || hot) {
            float pulse = (float) (.5 + .5 * Math.sin(seconds * 3.5));
            MechLabDollCanvas.drawCornerBrackets(c, x - 2f, y - 2f, w + 4f, h + 4f,
                    withAlpha(AMBER, (int) (180 + pulse * 65)), slot.selected() ? 2.4f : 1.4f, 17f, 5f);
        }
    }

    /** The socket under a carried piece, lit green where it would take it and red where it would not. */
    private static void dropMark(CanvasContext c, CardTarget t, boolean valid) {
        Color tone = valid ? TAKES : REFUSES;
        c.fillRect(t.left(), t.top(), t.width(), t.height(), withAlpha(tone, 34));
        HardpointBricks.rim(c, t.left(), t.top(), t.width(), t.height(), CUT, tone, 2f);
        MechLabDollCanvas.drawCornerBrackets(c, t.left() - 3f, t.top() - 3f, t.width() + 6f, t.height() + 6f,
                tone, 2.6f, 17f, 5f);
    }

    /** A wire from a socket into the near face of the core, with the lab's pulse running along it. */
    private static void wire(CanvasContext c, Placed socket, CardTarget core, double seconds, int index) {
        CardTarget t = socket.target();
        Color tone = BoatHardware.tone(socket.slot().kind());
        boolean selected = socket.slot().selected();
        float[][] path;
        if (t.bottom() <= core.top()) {
            float x = Math.max(core.left() + 14f, Math.min(core.right() - 14f, t.centerX()));
            float midY = (t.bottom() + core.top()) * 0.5f;
            path = new float[][]{{t.centerX(), t.bottom()}, {t.centerX(), midY}, {x, midY}, {x, core.top()}};
        } else if (t.top() >= core.bottom()) {
            path = new float[][]{{t.centerX(), t.top()}, {core.centerX(), core.bottom()}};
        } else if (t.right() <= core.left()) {
            path = new float[][]{{t.right(), core.centerY()}, {core.left(), core.centerY()}};
        } else {
            path = new float[][]{{t.left(), core.centerY()}, {core.right(), core.centerY()}};
        }
        for (int i = 0; i < path.length - 1; i++) {
            float[] a = path[i], b = path[i + 1];
            c.line(a[0], a[1], b[0], b[1], SHADOW, 5f);
            c.line(a[0], a[1], b[0], b[1], withAlpha(tone, selected ? 235 : 150), 2f);
            float phase = (float) ((seconds * (selected ? .45 : .18) + i * .18 + index * .11) % 1);
            c.fillRect(a[0] + (b[0] - a[0]) * phase - 1.5f, a[1] + (b[1] - a[1]) * phase - 1.5f, 3f, 3f,
                    withAlpha(tone, selected ? 255 : 170));
        }
        float[] end = path[path.length - 1];
        MechLabDollCanvas.drawAnchorMechanicalTick(c, end[0], end[1], tone, selected);
    }

    static void ring(CanvasContext c, float x, float y, float radius, Color color, float stroke) {
        int segments = 20;
        for (int i = 0; i < segments; i++) {
            double a = i * Math.PI * 2 / segments, b = (i + 1) * Math.PI * 2 / segments;
            c.line(x + radius * (float) Math.cos(a), y + radius * (float) Math.sin(a),
                    x + radius * (float) Math.cos(b), y + radius * (float) Math.sin(b), color, stroke);
        }
    }

    private static void plate(CanvasContext c, float x, float y, float w, float h) {
        c.fillRect(x + CUT, y, w - CUT * 2f, h, PLATE);
        c.fillQuad(x, y + CUT, x + CUT, y, x + CUT, y + h, x, y + h - CUT, PLATE);
        c.fillQuad(x + w - CUT, y, x + w, y + CUT, x + w, y + h - CUT, x + w - CUT, y + h, PLATE);
    }

    static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static String clip(String text, float width, BitmapFont font) {
        if (text == null) return "";
        if (font.measureWidth(text) <= width) return text;
        int end = text.length();
        while (end > 1 && font.measureWidth(text.substring(0, end) + "...") > width) end--;
        return text.substring(0, end) + "...";
    }
}
