package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.List;
import java.util.Map;

/** Standalone campaign opening; it needs neither a company nor a planet interaction. */
public final class CompanyPrologueDialog implements InteractionDialogPlugin {
    private InteractionDialogAPI dialog;
    private boolean closing;
    @Override public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        dialog.hideTextPanel();
        dialog.hideVisualPanel();
        dialog.showCustomVisualDialog(Global.getSettings().getScreenWidth(),
                Global.getSettings().getScreenHeight(), new Delegate());
    }
    private final class Delegate implements CustomVisualDialogDelegate {
        private final CompanyPrologueScreen screen = new CompanyPrologueScreen();
        private Runnable dismiss;
        private PositionAPI position;
        private final BaseCustomUIPanelPlugin panel = new BaseCustomUIPanelPlugin() {
            @Override public void positionChanged(PositionAPI value) { position = value; attach(); }
            @Override public void advance(float amount) { screen.advance(amount); }
            @Override public void render(float alpha) { screen.render(alpha); }
            @Override public void processInput(List<InputEventAPI> events) { screen.processInput(events); }
        };
        private void attach() { if (position != null && dismiss != null) screen.attach(position, null, dismiss); }
        @Override public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
            dismiss = callbacks::dismissDialog;
            position = panel.getPosition();
            attach();
        }
        @Override public CustomUIPanelPlugin getCustomPanelPlugin() { return panel; }
        @Override public float getNoiseAlpha() { return 0f; }
        @Override public void advance(float amount) { }
        @Override public void reportDismissed(int option) { screen.detach(); closing = true; closeIfRequested(); }
    }
    @Override public void advance(float amount) {
        closeIfRequested();
    }
    private void closeIfRequested() {
        if (!closing || dialog == null) return;
        try {
            closing = false;
            dialog.dismiss();
        } catch (RuntimeException failure) {
            // Some engine paths refuse dismissal during custom-panel teardown.
            closing = true;
            Global.getLogger(CompanyPrologueDialog.class).warn("Deferred prologue dismissal", failure);
        }
    }
    @Override public void optionSelected(String text, Object data) { }
    @Override public void optionMousedOver(String text, Object data) { }
    @Override public void backFromEngagement(EngagementResultAPI result) { }
    @Override public Object getContext() { return null; }
    @Override public Map<String, MemoryAPI> getMemoryMap() { return Map.of(); }
}
