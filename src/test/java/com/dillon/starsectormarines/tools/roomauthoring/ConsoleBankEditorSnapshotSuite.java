package com.dillon.starsectormarines.tools.roomauthoring;

import com.dillon.starsectormarines.battle.world.gen.fit.ConsoleBankLayout.Cell;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayout;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Drives the actual fixture controls and grid events, then captures the editor at native UI scale. */
public final class ConsoleBankEditorSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "console-editor"; }
    @Override public String label() { return "Connected console bank editor"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        RoomLayout seed = new RoomLayout(RoomPurpose.BRIDGE, RoomFit.STANDARD, RoomShape.rectangle(14, 12),
                List.of(new LayoutOp.Flooring(0, 0, 14, 12, "ship.passage-deck"),
                        new LayoutOp.Lane(0, 0, 14, 2)), List.of(), false);
        RoomAuthoringPage[] page = new RoomAuthoringPage[1];
        JPanel frame = new JPanel(new BorderLayout());
        JLabel status = new JLabel(" ");
        List<Cell> path = new ArrayList<>();
        for (int y = 3; y <= 7; y++) path.add(new Cell(3, y));
        for (int x = 4; x <= 8; x++) path.add(new Cell(x, 7));
        RoomLayout[] edited = new RoomLayout[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                page[0] = new RoomAuthoringPage(new AuthoringPageContext(context.projectRoot(),
                        context.starsectorCore(), status::setText, () -> {}), seed);
                frame.add(page[0].component(), BorderLayout.CENTER);
                frame.add(status, BorderLayout.SOUTH);
                frame.setSize(1920, 1080);
                button(frame, "Next").doClick(); button(frame, "Next").doClick();
                button(frame, "Connected console bank").doClick();
                layout(frame);
                RoomGridView grid = find(frame, RoomGridView.class);
                grid.cellSize(64); layout(frame);
                for (Cell cell : List.of(new Cell(3, 3), new Cell(3, 7), new Cell(8, 7))) {
                    int x = cell.x() * 64 + 32, y = (11 - cell.y()) * 64 + 32;
                    grid.dispatchEvent(new MouseEvent(grid, MouseEvent.MOUSE_PRESSED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1));
                    grid.dispatchEvent(new MouseEvent(grid, MouseEvent.MOUSE_RELEASED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1));
                }
                button(frame, "Place bank").doClick();
                if (!page[0].hasUnsavedChanges() || !status.getText().startsWith("Console bank placed"))
                    throw new IllegalStateException("Editor failed to commit bank: " + status.getText());
                edited[0] = grid.currentLayout();
                if (!edited[0].ops().contains(new LayoutOp.ConsoleBank(path, false, true)))
                    throw new IllegalStateException("Editor recorded a different bank path");
            } catch (Exception failure) { throw new IllegalStateException(failure); }
        });
        BufferedImage backdrop;
        try (RoomPreview preview = new RoomPreview(context.projectRoot())) { backdrop = preview.render(edited[0], 64); }
        BufferedImage image = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            RoomGridView grid = find(frame, RoomGridView.class);
            grid.cellSize(64); grid.backdrop(backdrop); layout(frame);
            Graphics2D g = image.createGraphics(); frame.printAll(g); g.dispose();
            page[0].close();
        });
        return List.of(new SnapshotArtifact("console-bank-editor.png", image));
    }

    private static AbstractButton button(Container parent, String label) {
        for (Component child : parent.getComponents()) {
            if (child instanceof AbstractButton button && label.equals(button.getText())) return button;
            if (child instanceof Container container) {
                AbstractButton found = button(container, label); if (found != null) return found;
            }
        }
        return null;
    }
    private static <T> T find(Container parent, Class<T> type) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container container) { T found = find(container, type); if (found != null) return found; }
        }
        return null;
    }
    private static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container container) layout(container);
    }
}
