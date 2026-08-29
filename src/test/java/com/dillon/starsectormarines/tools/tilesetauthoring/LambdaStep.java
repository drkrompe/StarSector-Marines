package com.dillon.starsectormarines.tools.tilesetauthoring;

import javax.swing.JComponent;
import java.util.function.Supplier;

/**
 * A {@link WizardStep} declared inline, so a walkthrough reads as a list of
 * screens rather than as a file of anonymous classes.
 *
 * <p>The body is supplied lazily and built once. Steps share widgets — the
 * picture of the sheet belongs to three of them — and a shared widget must not
 * be built per step, only re-parented on the way in.
 */
public final class LambdaStep implements WizardStep {

    private final String title;
    private final String blurb;
    private final Supplier<JComponent> bodySupplier;
    private JComponent built;
    private Runnable onEnter = () -> {};
    private Runnable onLeave = () -> {};
    private Supplier<String> blocker = () -> null;
    private String nextLabel = "Next";
    private boolean last;

    public LambdaStep(String title, String blurb, Supplier<JComponent> body) {
        this.title = title;
        this.blurb = blurb;
        this.bodySupplier = body;
    }

    /** What to do each time this screen is shown. */
    public LambdaStep onEnter(Runnable action) {
        this.onEnter = action;
        return this;
    }

    /** What to do when this screen is left forwards. */
    public LambdaStep onLeave(Runnable action) {
        this.onLeave = action;
        return this;
    }

    /** What is still needed here, phrased as the thing to do. */
    public LambdaStep blockedWhen(Supplier<String> reason) {
        this.blocker = reason;
        return this;
    }

    public LambdaStep nextLabel(String label) {
        this.nextLabel = label;
        return this;
    }

    /** Mark this the walkthrough's last screen. */
    public LambdaStep last() {
        this.last = true;
        this.nextLabel = "Done";
        return this;
    }

    @Override public String title() {
        return title;
    }

    @Override public String blurb() {
        return blurb;
    }

    @Override public JComponent body() {
        if (built == null) built = bodySupplier.get();
        return built;
    }

    @Override public void onEnter() {
        onEnter.run();
    }

    @Override public void onLeave() {
        onLeave.run();
    }

    @Override public String blocker() {
        return blocker.get();
    }

    @Override public String nextLabel() {
        return nextLabel;
    }

    @Override public boolean isLast() {
        return last;
    }
}
