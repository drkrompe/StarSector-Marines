package com.dillon.starsectormarines.tools.tilesetauthoring;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * The first screen: what are you here to do?
 *
 * <p>Opening straight into the editor assumes the answer, and the answer it
 * assumes — "browse the sheets" — is the one that helps least. Somebody who
 * needs a wall does not know which of nine sheets has one, and somebody whose
 * new art just arrived does not need the sheet list at all. Asking first costs
 * one click and replaces a wall of commands with a sentence.
 */
public final class WorkflowChooser extends JPanel {

    private static final Color CARD = new Color(0xF4, 0xF6, 0xF8);
    private static final Color CARD_EDGE = new Color(0xC6, 0xCE, 0xD6);
    private static final Color HOVER = new Color(0xE6, 0xEE, 0xF7);

    public WorkflowChooser(Consumer<TilesetWorkflow> onChosen) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));

        JLabel heading = new JLabel("Tilesets");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 22f));
        JLabel sub = new JLabel("What are you doing?");
        sub.setFont(sub.getFont().deriveFont(Font.PLAIN, 13f));
        sub.setForeground(new Color(0x50, 0x58, 0x62));

        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        stretchable(heading);
        stretchable(sub);
        add(heading);
        add(Box.createVerticalStrut(4));
        add(sub);
        add(Box.createVerticalStrut(18));

        for (TilesetWorkflow workflow : TilesetWorkflow.values()) {
            JPanel card = card(workflow, onChosen);
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(card);
            add(Box.createVerticalStrut(10));
        }
        add(Box.createVerticalGlue());
    }

    private static JPanel card(TilesetWorkflow workflow, Consumer<TilesetWorkflow> onChosen) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(true);
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_EDGE),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel title = new JLabel(workflow.title());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        JLabel blurb = new JLabel("<html>" + workflow.blurb() + "</html>");
        blurb.setFont(blurb.getFont().deriveFont(Font.PLAIN, 12f));
        blurb.setForeground(new Color(0x44, 0x4C, 0x56));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        blurb.setAlignmentX(Component.LEFT_ALIGNMENT);
        stretchable(title);
        stretchable(blurb);
        card.add(title);
        card.add(Box.createVerticalStrut(4));
        card.add(blurb);

        // A whole card is the target, not a button inside it: the sentence is
        // the thing being chosen between, so the sentence has to be clickable.
        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                onChosen.accept(workflow);
            }
            @Override public void mouseEntered(MouseEvent event) {
                card.setBackground(HOVER);
            }
            @Override public void mouseExited(MouseEvent event) {
                card.setBackground(CARD);
            }
        });
        return card;
    }

    /**
     * Let a label take the whole width it is offered. A {@link JLabel}'s maximum
     * size is its preferred size, so a vertical {@code BoxLayout} gives it
     * exactly what its font metrics asked for and clips the last character off
     * anything that measured short.
     */
    private static void stretchable(JComponent label) {
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
    }

    /**
     * A keyboard-reachable button per workflow, for a caller that wants one
     * outside the cards; the cards themselves are mouse targets.
     */
    public static JButton button(TilesetWorkflow workflow, Consumer<TilesetWorkflow> onChosen) {
        JButton button = new JButton(workflow.title());
        button.addActionListener(event -> onChosen.accept(workflow));
        return button;
    }
}
