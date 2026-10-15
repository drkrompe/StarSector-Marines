package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import java.util.List;

/** Shared full-canvas host for the opening story and its planetary yard visit. */
public final class CompanyStoryDialogDelegate implements CustomVisualDialogDelegate {
    private final Screen screen;
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

    public CompanyStoryDialogDelegate(Screen screen, Runnable onDismissed) {
        this.screen = screen;
        this.onDismissed = onDismissed;
    }
    private void attach() {
        if (!closed && position != null && dismiss != null) screen.attach(position, null, dismiss);
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
        onDismissed.run();
    }
}
