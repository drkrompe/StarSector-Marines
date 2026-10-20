package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import java.util.List;

/**
 * Shared full-canvas host for the opening story, its planetary yard visit and the Jangala arrival.
 * A storybook host holds the {@link MenuTheme} from its first attach until it is dismissed, by its
 * own controls or Escape alike; the arrival plays its own score and holds nothing.
 */
public final class CompanyStoryDialogDelegate implements CustomVisualDialogDelegate {
    private final Screen screen;
    private final boolean playsTheme;
    private final Runnable onDismissed;
    private Runnable dismiss;
    private PositionAPI position;
    private boolean closed;
    private final BaseCustomUIPanelPlugin panel = new BaseCustomUIPanelPlugin() {
        @Override public void positionChanged(PositionAPI value) { position = value; attach(); }
        @Override public void advance(float amount) { screen.advance(amount); }
        @Override public void render(float alpha) { screen.render(alpha); }
        @Override public void processInput(List<InputEventAPI> events) { screen.processInput(events); }
    };

    /** A storybook: plays the Marines menu theme while shown. */
    public static CompanyStoryDialogDelegate storybook(Screen screen, Runnable onDismissed) {
        return new CompanyStoryDialogDelegate(screen, true, onDismissed);
    }

    /** A screen that plays its own score (the arrival), so holds no theme. */
    public static CompanyStoryDialogDelegate scored(Screen screen, Runnable onDismissed) {
        return new CompanyStoryDialogDelegate(screen, false, onDismissed);
    }

    private CompanyStoryDialogDelegate(Screen screen, boolean playsTheme, Runnable onDismissed) {
        this.screen = screen;
        this.playsTheme = playsTheme;
        this.onDismissed = onDismissed;
    }
    private void attach() {
        if (closed || position == null || dismiss == null) return;
        screen.attach(position, null, dismiss);
        if (playsTheme) MenuTheme.shared().hold(this);
    }
    @Override public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        dismiss = callbacks::dismissDialog;
        position = panel.getPosition();
        attach();
    }
    @Override public CustomUIPanelPlugin getCustomPanelPlugin() { return panel; }
    @Override public float getNoiseAlpha() { return 0f; }
    @Override public void advance(float amount) { }
    @Override public void reportDismissed(int option) {
        if (closed) return;
        closed = true;
        screen.detach();
        // After the callback, so a hold it hands the theme to (the port behind the yard) lands first.
        try {
            onDismissed.run();
        } finally {
            if (playsTheme) MenuTheme.shared().release(this);
        }
    }
}
