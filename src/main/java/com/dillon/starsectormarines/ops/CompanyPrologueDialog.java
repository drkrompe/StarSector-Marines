package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

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
                Global.getSettings().getScreenHeight(), CompanyStoryDialogDelegate.storybook(
                        new CompanyPrologueScreen(), () -> { closing = true; closeIfRequested(); }));
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
