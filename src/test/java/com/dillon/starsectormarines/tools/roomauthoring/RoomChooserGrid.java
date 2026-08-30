package com.dillon.starsectormarines.tools.roomauthoring;

import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import javax.swing.JComponent;
import javax.swing.Scrollable;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The rooms aboard, as pictures.
 *
 * <p>A list of twenty enum names is a list of twenty enum names. What an author
 * is choosing between is <em>rooms</em> — and the difference between a berth and
 * a boat bay is a thing you see at a glance and cannot read at all. The picture
 * is the room as it generates right now, authored document included, so opening
 * one is confirming what is already on screen rather than taking a guess at what
 * the name means.
 *
 * <p>Tiles come in as they are rendered rather than all at once. Each one is a
 * pass over a generated deck, and a grid that waited for the last of twenty
 * would show nothing for as long as it takes to draw them all.
 */
public final class RoomChooserGrid extends JComponent implements Scrollable {

    private static final int TILE_W = 240;
    private static final int TILE_H = 190;
    private static final int GAP = 12;
    private static final int CAPTION = 26;

    private static final Color BACKDROP = new Color(0x1a, 0x1f, 0x27);
    private static final Color TILE = new Color(0x23, 0x2a, 0x34);
    private static final Color TILE_PICKED = new Color(0x35, 0x4a, 0x5e);
    private static final Color EDGE = new Color(0x3c, 0x47, 0x55);
    private static final Color EDGE_PICKED = new Color(0x7f, 0xb2, 0xd8);
    private static final Color LABEL = new Color(0xe4, 0xec, 0xf4);
    private static final Color NOTE = new Color(0x93, 0xa2, 0xb2);
    private static final Color AUTHORED = new Color(0xd8, 0xc2, 0x7a);

    /** One room's tile: its picture, its size, and whether somebody has authored it. */
    public record Tile(RoomPurpose purpose, BufferedImage picture, String size, boolean authored) {}

    private final Map<RoomPurpose, Tile> tiles = new LinkedHashMap<>();
    private final List<RoomPurpose> order = new ArrayList<>();
    private RoomPurpose picked;

    private Consumer<RoomPurpose> onPick = purpose -> { };
    private Consumer<RoomPurpose> onOpen = purpose -> { };

    public RoomChooserGrid() {
        setBackground(BACKDROP);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                RoomPurpose hit = at(e.getX(), e.getY());
                if (hit == null) return;
                picked = hit;
                onPick.accept(hit);
                repaint();
                if (e.getClickCount() >= 2) onOpen.accept(hit);
            }
        });
    }

    public void onPick(Consumer<RoomPurpose> handler) {
        this.onPick = handler == null ? p -> { } : handler;
    }

    /** Double-clicking a room opens it, which is what a grid of things affords. */
    public void onOpen(Consumer<RoomPurpose> handler) {
        this.onOpen = handler == null ? p -> { } : handler;
    }

    public RoomPurpose picked() {
        return picked;
    }

    public void clear() {
        tiles.clear();
        order.clear();
        picked = null;
        revalidate();
        repaint();
    }

    /** Announce a room before its picture exists, so the grid fills in place. */
    public void expect(List<RoomPurpose> purposes) {
        for (RoomPurpose purpose : purposes) {
            if (tiles.containsKey(purpose)) continue;
            order.add(purpose);
            tiles.put(purpose, new Tile(purpose, null, "", false));
        }
        if (picked == null && !order.isEmpty()) picked = order.get(0);
        revalidate();
        repaint();
    }

    /** A room's picture has arrived. */
    public void show(Tile tile) {
        if (!tiles.containsKey(tile.purpose())) order.add(tile.purpose());
        tiles.put(tile.purpose(), tile);
        repaint();
    }

    private int columns() {
        return Math.max(1, (getWidth() + GAP) / (TILE_W + GAP));
    }

    private RoomPurpose at(int px, int py) {
        int columns = columns();
        int column = px / (TILE_W + GAP);
        int row = py / (TILE_H + GAP);
        if (column < 0 || column >= columns || row < 0) return null;
        int index = row * columns + column;
        return index >= 0 && index < order.size() ? order.get(index) : null;
    }

    @Override
    public Dimension getPreferredSize() {
        int columns = columns();
        int rows = (order.size() + columns - 1) / Math.max(1, columns);
        return new Dimension(columns * (TILE_W + GAP), Math.max(1, rows) * (TILE_H + GAP));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(BACKDROP);
        g.fillRect(0, 0, getWidth(), getHeight());

        int columns = columns();
        for (int i = 0; i < order.size(); i++) {
            Tile tile = tiles.get(order.get(i));
            int x = (i % columns) * (TILE_W + GAP);
            int y = (i / columns) * (TILE_H + GAP);
            paintTile(g, tile, x, y, tile.purpose() == picked);
        }
        g.dispose();
    }

    private void paintTile(Graphics2D g, Tile tile, int x, int y, boolean isPicked) {
        g.setColor(isPicked ? TILE_PICKED : TILE);
        g.fillRect(x, y, TILE_W, TILE_H);
        g.setColor(isPicked ? EDGE_PICKED : EDGE);
        g.drawRect(x, y, TILE_W - 1, TILE_H - 1);

        int frameH = TILE_H - CAPTION - 8;
        if (tile.picture() == null) {
            g.setColor(NOTE);
            g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
            g.drawString("drawing…", x + 10, y + frameH / 2);
        } else {
            // Fitted rather than cropped: a boat bay and a washroom differ by an
            // order of magnitude in floor, and the whole room is the point.
            BufferedImage picture = tile.picture();
            double scale = Math.min((TILE_W - 16.0) / picture.getWidth(),
                    (frameH - 8.0) / picture.getHeight());
            scale = Math.min(1.0, Math.max(0.02, scale));
            int w = Math.max(1, (int) (picture.getWidth() * scale));
            int h = Math.max(1, (int) (picture.getHeight() * scale));
            g.drawImage(picture, x + (TILE_W - w) / 2, y + 4 + (frameH - h) / 2, w, h, null);
        }

        g.setColor(LABEL);
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        String name = tile.purpose().name().toLowerCase().replace('_', ' ');
        g.drawString(name, x + 10, y + TILE_H - 12);

        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        if (tile.authored()) {
            g.setColor(AUTHORED);
            String mark = "authored";
            g.drawString(mark, x + TILE_W - 10 - g.getFontMetrics().stringWidth(mark),
                    y + TILE_H - 12);
        } else if (!tile.size().isEmpty()) {
            g.setColor(NOTE);
            g.drawString(tile.size(), x + TILE_W - 10 - g.getFontMetrics().stringWidth(tile.size()),
                    y + TILE_H - 12);
        }
    }

    // ---- Scrollable: a grid of fixed tiles scrolls by whole tiles ------------

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(TILE_W * 3 + GAP * 3, TILE_H * 2 + GAP * 2);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return 24;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return TILE_H + GAP;
    }

    /** Track the viewport's width so the columns reflow, but never its height. */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
