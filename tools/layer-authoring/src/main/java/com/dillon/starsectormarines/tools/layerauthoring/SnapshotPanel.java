package com.dillon.starsectormarines.tools.layerauthoring;

import com.dillon.starsectormarines.tools.snapshot.SnapshotCatalog;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotRunner;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/** In-process front end for the same snapshot suites used by the command line. */
final class SnapshotPanel extends JPanel {

    private final SnapshotContext context;
    private final BooleanSupplier savedState;
    private final SnapshotRunner runner = new SnapshotRunner();
    private final JComboBox<Selection> selection = new JComboBox<>();
    private final JButton create = new JButton("Create snapshots");
    private final JLabel status = new JLabel(" ");

    SnapshotPanel(Path projectRoot, Path starsectorCore, BooleanSupplier savedState) {
        super(new BorderLayout());
        if (savedState == null) throw new IllegalArgumentException("saved-state guard is required");
        context = new SnapshotContext(projectRoot, starsectorCore);
        this.savedState = savedState;

        SnapshotCatalog catalog = SnapshotCatalog.discover();
        List<SnapshotSuite> suites = catalog.suites();
        if (suites.size() > 1) {
            selection.addItem(new Selection("All snapshots", suites));
        }
        for (SnapshotSuite suite : suites) {
            selection.addItem(new Selection(suite.label(), List.of(suite)));
        }

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));
        controls.add(new JLabel("Suite  "));
        controls.add(selection);
        controls.add(Box.createHorizontalStrut(8));
        controls.add(create);
        controls.add(Box.createHorizontalGlue());

        JLabel heading = new JLabel("Deterministic snapshots");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 18f));
        JLabel explanation = new JLabel("<html>Render saved authoring data and controlled "
                + "fixtures without launching Starsector. Outputs are written beneath "
                + "<b>build/snapshots</b>.</html>");
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        heading.setAlignmentX(LEFT_ALIGNMENT);
        explanation.setAlignmentX(LEFT_ALIGNMENT);
        controls.setAlignmentX(LEFT_ALIGNMENT);
        status.setAlignmentX(LEFT_ALIGNMENT);
        content.add(heading);
        content.add(Box.createVerticalStrut(10));
        content.add(explanation);
        content.add(Box.createVerticalStrut(20));
        content.add(controls);
        content.add(Box.createVerticalStrut(12));
        content.add(status);
        content.add(Box.createVerticalGlue());
        add(content, BorderLayout.CENTER);

        create.setEnabled(selection.getItemCount() > 0);
        create.addActionListener(event -> createSelected());
    }

    private void createSelected() {
        Selection selected = (Selection) selection.getSelectedItem();
        if (selected == null || selected.suites().isEmpty()) return;
        if (selected.includes("layers") && !savedState.getAsBoolean()) {
            JOptionPane.showMessageDialog(this,
                    "Save the layer JSON before creating layer snapshots.",
                    "Unsaved layer changes", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path outputRoot = context.projectRoot().resolve("build/snapshots");
        try {
            if (containsExistingPng(outputRoot, selected.suites())) {
                int choice = JOptionPane.showConfirmDialog(this,
                        "Replace existing PNGs for " + selected.label() + "?",
                        "Confirm snapshot overwrite", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) {
                    status.setText("Snapshot creation cancelled");
                    return;
                }
            }
        } catch (Exception failure) {
            showFailure(failure);
            return;
        }

        create.setEnabled(false);
        selection.setEnabled(false);
        status.setText("Creating " + selected.label() + "…");
        new SwingWorker<List<Path>, Void>() {
            @Override
            protected List<Path> doInBackground() throws Exception {
                return runner.create(context, selected.suites(), outputRoot, true);
            }

            @Override
            protected void done() {
                create.setEnabled(true);
                selection.setEnabled(true);
                try {
                    List<Path> outputs = get();
                    status.setText("Created " + outputs.size() + " PNGs in "
                            + outputRoot.toAbsolutePath());
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    showFailure(failure);
                } catch (ExecutionException failure) {
                    Throwable cause = failure.getCause();
                    showFailure(cause instanceof Exception exception ? exception : failure);
                }
            }
        }.execute();
    }

    private static boolean containsExistingPng(Path outputRoot,
                                               List<SnapshotSuite> suites) throws Exception {
        List<Path> roots = new ArrayList<>();
        for (SnapshotSuite suite : suites) {
            Path root = outputRoot.resolve(suite.id());
            if (Files.isDirectory(root)) roots.add(root);
        }
        for (Path root : roots) {
            try (Stream<Path> paths = Files.walk(root)) {
                if (paths.anyMatch(path -> Files.isRegularFile(path)
                        && path.getFileName().toString().toLowerCase().endsWith(".png"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void showFailure(Exception failure) {
        status.setText("Snapshot creation failed");
        JOptionPane.showMessageDialog(this, failure.getMessage(),
                "Snapshot creation failed", JOptionPane.ERROR_MESSAGE);
    }

    private record Selection(String label, List<SnapshotSuite> suites) {
        private Selection {
            suites = List.copyOf(suites);
        }

        private boolean includes(String suiteId) {
            return suites.stream().anyMatch(suite -> suite.id().equals(suiteId));
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
