package com.dillon.starsectormarines.tools.tilesetauthoring;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import java.util.function.Consumer;

/**
 * A walkthrough: one step to a screen, in order, with a way back.
 *
 * <p>The page this replaces put every command on one toolbar. All of them were
 * real and none of them said when to use it — seventeen controls in the order
 * they were written, with the cut, the annotation, the grouping and the export
 * interleaved. That is a palette for somebody who already knows the procedure.
 * A sequence teaches it: each screen asks one thing, says what it is for, and
 * will not move on until it has an answer.
 *
 * <p>Steps hold widgets in common — the picture of the sheet is wanted by
 * three of them — so a step re-parents what it needs on the way in rather than
 * every step owning its own copy. Swing moves a component when it is added
 * somewhere else, which is exactly the behaviour that makes this work.
 */
public final class TilesetWizard extends JPanel {

    private final JLabel counter = new JLabel();
    private final JLabel heading = new JLabel();
    private final JLabel blurb = new JLabel();
    private final JPanel body = new JPanel(new BorderLayout());
    private final JButton back = new JButton("Back");
    private final JButton next = new JButton("Next");
    private final JLabel blocker = new JLabel(" ");

    private List<WizardStep> steps = List.of();
    private int current;
    private final Runnable onLeave;
    private final Consumer<String> status;

    /**
     * @param onLeave called when Back is pressed on the first step — the way
     *                out of a walkthrough is back past its start
     * @param status  where a step's own progress messages go
     */
    public TilesetWizard(Runnable onLeave, Consumer<String> status) {
        super(new BorderLayout(0, 8));
        this.onLeave = onLeave;
        this.status = status;

        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 18f));
        blurb.setFont(blurb.getFont().deriveFont(Font.PLAIN, 12f));
        counter.setFont(counter.getFont().deriveFont(Font.PLAIN, 11f));
        counter.setForeground(new Color(0x60, 0x66, 0x70));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(10, 14, 8, 14));
        for (JComponent line : List.of(counter, heading, blurb)) {
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            header.add(line);
        }

        back.addActionListener(event -> goBack());
        next.addActionListener(event -> goNext());
        blocker.setFont(blocker.getFont().deriveFont(Font.PLAIN, 11f));
        blocker.setForeground(new Color(0x8a, 0x5a, 0x1a));

        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setBorder(BorderFactory.createEmptyBorder(6, 14, 10, 14));
        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(back);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(next);
        footer.add(blocker, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
    }

    /**
     * Let a label take the whole width it is offered.
     *
     * <p>A {@link JLabel}'s maximum size is its preferred size, so inside a
     * vertical {@code BoxLayout} it is given exactly the width its own font
     * metrics asked for and any shortfall clips the last character off. Text
     * that explains a screen cannot end in half a word.
     *
     * <p>Re-applied every time the text changes rather than once at build time.
     * The height half is read from the label's current preferred size, and a
     * label with no text yet is no pixels tall - pinning that once collapses the
     * header to nothing the moment it is given something to say.
     */
    private static void stretchable(JComponent label) {
        label.setMaximumSize(null);
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
    }

    /** Begin a walkthrough at its first step. */
    public void start(List<WizardStep> walkthrough) {
        if (walkthrough.isEmpty()) throw new IllegalArgumentException("a walkthrough needs a step");
        this.steps = List.copyOf(walkthrough);
        show(0);
    }

    /** The step on screen, or null before {@link #start} is called. */
    public WizardStep currentStep() {
        return steps.isEmpty() ? null : steps.get(current);
    }

    /** Which screen of how many, one-based, for a test or a caption. */
    public int currentIndex() {
        return current;
    }

    public int stepCount() {
        return steps.size();
    }

    /**
     * Re-read the step's own answer to whether it is finished.
     *
     * <p>A step's precondition is usually a selection somewhere in its body, so
     * nothing tells the wizard when it changes. The body calls this.
     */
    public void refresh() {
        WizardStep step = currentStep();
        if (step == null) return;
        String why = step.blocker();
        next.setEnabled(why == null);
        next.setText(step.nextLabel());
        blocker.setText(why == null ? " " : why);
    }

    private void show(int index) {
        current = Math.max(0, Math.min(index, steps.size() - 1));
        WizardStep step = steps.get(current);
        counter.setText("Step " + (current + 1) + " of " + steps.size());
        heading.setText(step.title());
        blurb.setText(step.blurb());
        for (JComponent line : List.of(counter, heading, blurb)) stretchable(line);
        body.removeAll();
        step.onEnter();
        body.add(step.body(), BorderLayout.CENTER);
        back.setText(current == 0 ? "← Workflows" : "Back");
        refresh();
        body.revalidate();
        body.repaint();
    }

    private void goBack() {
        if (current == 0) {
            onLeave.run();
            return;
        }
        show(current - 1);
    }

    private void goNext() {
        WizardStep step = steps.get(current);
        if (step.blocker() != null) return;
        if (step.isLast() || current == steps.size() - 1) {
            status.accept(step.title() + " — done");
            return;
        }
        step.onLeave();
        show(current + 1);
    }
}
