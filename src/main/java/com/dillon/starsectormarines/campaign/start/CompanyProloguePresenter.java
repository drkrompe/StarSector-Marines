package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.ops.CompanyPrologueDialog;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;

/** Waits for the campaign map after character creation or a load of an unfinished opening. */
public final class CompanyProloguePresenter implements EveryFrameScript {
    public static void install() {
        var sector = Global.getSector();
        if (sector == null) return;
        sector.removeTransientScriptsOfClass(CompanyProloguePresenter.class);
        if (CompanyPrologue.pending(sector.getPersistentData()))
            sector.addTransientScript(new CompanyProloguePresenter());
    }

    @Override public boolean isDone() {
        return Global.getSector() == null || !CompanyPrologue.pending(Global.getSector().getPersistentData());
    }
    @Override public boolean runWhilePaused() { return true; }
    @Override public void advance(float amount) {
        var sector = Global.getSector();
        if (sector == null || Global.getCurrentState() != GameState.CAMPAIGN || isDone()) return;
        var ui = sector.getCampaignUI();
        if (ui == null || ui.isShowingDialog() || ui.isShowingMenu() || ui.getCurrentCoreTab() != null
                || sector.getPlayerFleet() == null) return;
        // A refused display or an unexpected dismissal leaves the receipt pending.
        ui.showInteractionDialog(new CompanyPrologueDialog(), sector.getPlayerFleet());
    }
}
