package com.dillon.starsectormarines.tools.authoring;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/**
 * The workbench's message dialogs.
 *
 * <p>{@link JOptionPane#showMessageDialog} lays a plain string out on one line.
 * A sentence is fine; a paragraph becomes a dialog as wide as the desktop with
 * its own text running off the edge, which is what an authoring note or a
 * stack-trace-bearing failure actually looks like. So the body goes in a wrapping
 * text area with a real width, and grows a scrollbar rather than the screen.
 *
 * <p>Every dialog also offers <b>Copy to clipboard</b>. A failure in this tool is
 * usually the start of a conversation with a model about why, and retyping an
 * error from a screenshot is the worst way to begin one.
 */
public final class AuthoringMessages {

    private AuthoringMessages() {}

    /** Characters across before wrapping — comfortable prose measure, not a full screen. */
    private static final int COLUMNS = 72;
    /** Lines shown before the body scrolls instead of growing the dialog. */
    private static final int MAX_ROWS = 16;

    public static void info(Component parent, String title, String body) {
        show(parent, title, body, JOptionPane.INFORMATION_MESSAGE);
    }

    public static void warn(Component parent, String title, String body) {
        show(parent, title, body, JOptionPane.WARNING_MESSAGE);
    }

    public static void error(Component parent, String title, String body) {
        show(parent, title, body, JOptionPane.ERROR_MESSAGE);
    }

    /** Report a failure, with the exception's own message as the tail of the body. */
    public static void error(Component parent, String title, String body, Throwable failure) {
        show(parent, title, body + "\n\n" + describe(failure), JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Ask before doing something that cannot be undone, and say what it is.
     *
     * <p>The affirmative button is labelled with the act rather than with "OK":
     * the dialogs that need this one are the ones where the body is a paragraph
     * about what would be destroyed, and a person who reads only the buttons
     * should still be told which one destroys it.
     *
     * @return whether the operator chose to go ahead
     */
    public static boolean confirm(Component parent, String title, String body,
                                  String proceedLabel) {
        Object[] options = {proceedLabel, "Cancel"};
        int chosen = JOptionPane.showOptionDialog(parent, panel(title, body), title,
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null,
                options, options[1]);
        return chosen == 0;
    }

    public static void show(Component parent, String title, String body, int messageType) {
        JOptionPane.showMessageDialog(parent, panel(title, body), title, messageType);
    }

    static JComponent panel(String title, String body) {
        String text = body == null ? "" : body;
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setColumns(COLUMNS);
        area.setRows(Math.min(MAX_ROWS, Math.max(2, estimateRows(text))));
        area.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        // Read as dialog prose rather than as an editable field.
        area.setFont(UIManager.getFont("Label.font") == null
                ? area.getFont().deriveFont(Font.PLAIN)
                : UIManager.getFont("Label.font"));
        area.setBackground(UIManager.getColor("OptionPane.background"));
        area.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JButton copy = new JButton("Copy to clipboard");
        copy.addActionListener(event -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(title + "\n\n" + text), null);
            // Confirm in place: a second dialog to say a dialog was copied is absurd.
            copy.setText("Copied");
            Timer revert = new Timer(1400, ignored -> copy.setText("Copy to clipboard"));
            revert.setRepeats(false);
            revert.start();
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.setOpaque(false);
        actions.add(copy);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(
                Math.max(420, area.getPreferredSize().width + 24),
                Math.min(420, area.getPreferredSize().height + 52)));
        return panel;
    }

    /** Rows the body needs at {@link #COLUMNS} wide, counting its own line breaks. */
    static int estimateRows(String text) {
        int rows = 0;
        for (String line : text.split("\n", -1)) {
            rows += Math.max(1, (line.length() + COLUMNS - 1) / COLUMNS);
        }
        return rows;
    }

    /** Type, message and the first frames — enough to paste into a session and act on. */
    static String describe(Throwable failure) {
        if (failure == null) return "";
        StringBuilder out = new StringBuilder(failure.getClass().getName());
        if (failure.getMessage() != null) out.append(": ").append(failure.getMessage());
        StackTraceElement[] frames = failure.getStackTrace();
        for (int i = 0; i < Math.min(6, frames.length); i++) {
            out.append("\n    at ").append(frames[i]);
        }
        if (frames.length > 6) out.append("\n    ... ").append(frames.length - 6).append(" more");
        Throwable cause = failure.getCause();
        if (cause != null && cause != failure) {
            out.append("\nCaused by: ").append(cause.getClass().getName());
            if (cause.getMessage() != null) out.append(": ").append(cause.getMessage());
        }
        return out.toString();
    }
}
