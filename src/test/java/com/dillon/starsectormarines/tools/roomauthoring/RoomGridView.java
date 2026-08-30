package com.dillon.starsectormarines.tools.roomauthoring;

import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The room as a grid of cells, drawn to be edited rather than to be admired.
 *
 * <p>The room itself is drawn by the battle renderer and handed here as a
 * backdrop — law 17 keeps the picture where it belongs, and choosing between a
 * vent plate and hazard striping is a decision that can only be made by looking
 * at them. What this view adds on top is what a render cannot say: which cells
 * are deck at all, which are reserved circulation, and which step is anchored
 * where. Those are authoring facts, invisible in a render by design, and this is
 * the annotation law 17 explicitly allows.
 *
 * <p>The marks are drawn light over the art rather than instead of it. An
 * earlier version painted flat colours for everything and was perfectly clear
 * about reservations while being useless for the one question the deck screen
 * exists to answer.
 */
public final class RoomGridView extends JComponent {

    private static final Color VOID = new Color(0x14, 0x18, 0x1e);
    private static final Color DECK = new Color(0x36, 0x40, 0x4c);
    private static final Color LANE_WASH = new Color(0x4d, 0x7e, 0xa0, 0x66);
    private static final Color GRID = new Color(0x00, 0x00, 0x00, 60);
    private static final Color FIXTURE_FILL = new Color(0xd8, 0xc2, 0x7a);
    private static final Color PAVING = new Color(0x5a, 0x6a, 0x50);
    private static final Color WORK = new Color(0x7f, 0xd0, 0x8a);
    private static final Color OUTLINE = new Color(0x0c, 0x10, 0x16);
    private static final Color HOVER = new Color(0xff, 0xff, 0xff, 0x30);

    /** Ground kinds shown as a wash over the deck, so a repaint is legible at a glance. */
    private static Color washFor(GroundKind kind) {
        return switch (kind) {
            case STRIPED -> new Color(0xd0, 0xb0, 0x40, 0x66);
            case TILE -> new Color(0x90, 0xb8, 0xd0, 0x66);
            case BRICK -> new Color(0xc0, 0x80, 0x60, 0x66);
            default -> new Color(0xff, 0xff, 0xff, 0x22);
        };
    }

    private RoomDraft draft;
    /** The room as the renderer draws it, one image pixel-aligned to the grid. */
    private BufferedImage backdrop;
    private int cellPx = 22;
    private int hoverX = -1;
    private int hoverY = -1;

    /** What a click means, decided by whichever screen is showing. */
    private BiConsumer<Integer, Integer> onClick = (x, y) -> { };

    public RoomGridView() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (draft == null) return;
                int x = e.getX() / cellPx;
                int y = e.getY() / cellPx;
                if (x < 0 || y < 0 || x >= draft.width() || y >= draft.height()) return;
                onClick.accept(x, y);
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                hoverX = e.getX() / cellPx;
                hoverY = e.getY() / cellPx;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverX = -1;
                hoverY = -1;
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void show(RoomDraft draft) {
        this.draft = draft;
        revalidate();
        repaint();
    }

    /**
     * The picture to draw the marks over, or null while none has been made.
     *
     * <p>Kept until a newer one arrives rather than cleared while one is drawn,
     * so an edit does not blank the room for as long as a render takes.
     */
    public void backdrop(BufferedImage picture) {
        this.backdrop = picture;
        repaint();
    }

    /** What one cell is drawn at, which a backdrop has to match to line up. */
    public int cellPx() {
        return cellPx;
    }

    public void onClick(BiConsumer<Integer, Integer> handler) {
        this.onClick = handler == null ? (x, y) -> { } : handler;
    }

    public void cellSize(int px) {
        this.cellPx = Math.max(8, px);
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        if (draft == null) return new Dimension(240, 160);
        return new Dimension(draft.width() * cellPx, draft.height() * cellPx);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(VOID);
        g.fillRect(0, 0, getWidth(), getHeight());
        if (draft == null) {
            g.dispose();
            return;
        }

        // The room as it will look, under everything. Drawn at its own size
        // when it matches and scaled when it does not, because an edit that
        // resizes the room arrives before the new picture does.
        if (backdrop != null) {
            g.drawImage(backdrop, 0, 0,
                    draft.width() * cellPx, draft.height() * cellPx, null);
        }

        for (int x = 0; x < draft.width(); x++) {
            for (int y = 0; y < draft.height(); y++) {
                int px = x * cellPx;
                int py = y * cellPx;
                if (!draft.isFloor(x, y)) {
                    // Not deck at all: cover whatever the backdrop had there, so
                    // a hole in the footprint reads as a hole.
                    g.setColor(VOID);
                    g.fillRect(px, py, cellPx, cellPx);
                    continue;
                }
                if (backdrop == null) {
                    g.setColor(DECK);
                    g.fillRect(px, py, cellPx, cellPx);
                }
                if (draft.isLane(x, y)) {
                    g.setColor(LANE_WASH);
                    g.fillRect(px, py, cellPx, cellPx);
                }
                g.setColor(GRID);
                g.drawRect(px, py, cellPx, cellPx);
            }
        }

        // What stands on the deck, over the deck. Drawn inset so the cell it is
        // on stays visible around it, which is what makes a fixture on a lane
        // obvious rather than merely wrong.
        int inset = Math.max(2, cellPx / 6);
        g.setFont(getFont().deriveFont(Font.BOLD, Math.max(8f, cellPx * 0.42f)));
        for (int x = 0; x < draft.width(); x++) {
            for (int y = 0; y < draft.height(); y++) {
                List<LayoutOp> here = draft.at(x, y);
                if (here.isEmpty()) continue;
                int px = x * cellPx;
                int py = y * cellPx;
                for (LayoutOp op : here) {
                    if (op instanceof LayoutOp.Paving) {
                        g.setColor(PAVING);
                        g.drawRect(px + 1, py + 1, cellPx - 3, cellPx - 3);
                    }
                }
                for (LayoutOp op : here) {
                    if (op instanceof LayoutOp.Fixture fixture) {
                        // Outlined rather than filled: the fixture's own art is
                        // already under this, and covering it would hide the
                        // thing the author is placing.
                        g.setColor(FIXTURE_FILL);
                        g.setStroke(new BasicStroke(2f));
                        g.drawRect(px + inset, py + inset,
                                cellPx - inset * 2, cellPx - inset * 2);
                        if (fixture.affordance() != null && cellPx >= 16) {
                            g.setColor(WORK);
                            g.fillOval(px + cellPx - inset - 4, py + inset, 4, 4);
                        }
                    } else if (op instanceof LayoutOp.Task) {
                        g.setColor(WORK);
                        g.setStroke(new BasicStroke(2f));
                        g.drawOval(px + inset, py + inset,
                                cellPx - inset * 2, cellPx - inset * 2);
                    }
                }
            }
        }

        if (hoverX >= 0 && hoverX < draft.width() && hoverY >= 0 && hoverY < draft.height()) {
            g.setColor(HOVER);
            g.fillRect(hoverX * cellPx, hoverY * cellPx, cellPx, cellPx);
        }
        g.dispose();
    }
}
