package com.dillon.starsectormarines.tools.tilesetauthoring;

import javax.swing.JComponent;

/**
 * One screen of a walkthrough: a title, a sentence saying what this step is
 * for, and the working area for doing it.
 *
 * <p>A step owns the question it asks and the answer to whether it has been
 * answered. That is the whole reason the page is a sequence rather than a
 * palette: a command that cannot be run yet is not greyed out with no
 * explanation, it is simply not the screen you are on, and the one screen you
 * are on says what it wants.
 */
public interface WizardStep {

    /** The step's name, shown as the screen's heading. */
    String title();

    /** One sentence on what this step is for, shown under the heading. */
    String blurb();

    /**
     * The working area. Built once and reused, so a step may hold widgets
     * shared with other steps and re-parent them in {@link #onEnter()}.
     *
     * <p>Asking twice must give back the one screen, not a second one over the
     * first. The wizard asks twice on every entry, and a body that builds a new
     * container each time leaves the previous one parented and emptied - which
     * paints over its replacement as a blank screen.
     */
    JComponent body();

    /**
     * Called each time the screen is shown. Re-parent shared widgets and
     * refresh anything derived from the steps before this one.
     */
    default void onEnter() {
    }

    /**
     * Why this step is not finished, or {@code null} when it is.
     *
     * <p>The text is shown beside a disabled Next, so it says what to do rather
     * than what is wrong: "Pick a sheet to open", not "no selection".
     */
    default String blocker() {
        return null;
    }

    /**
     * Called when this step is left forwards. Do here whatever the next screen
     * needs and this one decided - opening the sheet the operator just picked,
     * for instance.
     */
    default void onLeave() {
    }

    /** What the button that leaves this step should say. */
    default String nextLabel() {
        return "Next";
    }

    /** A step that finishes the walkthrough rather than advancing. */
    default boolean isLast() {
        return false;
    }
}
