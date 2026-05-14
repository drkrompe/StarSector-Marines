package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.render.BridgeRenderer;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * Panel plugin used inside the Marine Operations custom dialog. Reuses
 * {@link BridgeRenderer} to draw the 3D scene full-bleed, then overlays the
 * {@link WidgetRoot} for clickable UI elements. The widget here is a tiny
 * placeholder square at the top-right that logs to verify input routing
 * across the FBO blit; the eventual mission-node sprites will plug into this
 * same tree.
 */
public class MarineOpsPanelPlugin extends BaseCustomUIPanelPlugin {

    private static final Logger LOG = Global.getLogger(MarineOpsPanelPlugin.class);

    private final BridgeRenderer renderer = new BridgeRenderer();
    private final WidgetRoot widgets = new WidgetRoot();

    private PositionAPI position;
    private float dt;
    private boolean widgetsBuilt;
    private Runnable onBack;

    /**
     * Wired by {@link MarineOpsDialogDelegate#init} with the dialog's dismiss
     * callback. The back button isn't added until this is set, so widget tree
     * stays consistent. Future mid-action gating just no-ops this Runnable.
     */
    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
        tryBuildWidgets();
    }

    @Override
    public void positionChanged(PositionAPI position) {
        this.position = position;
        tryBuildWidgets();
    }

    private void tryBuildWidgets() {
        if (position == null || onBack == null) return;
        widgets.clear();

        float pad = 16f;

        // Back button — bottom-right of canvas. Owned by us; we decide when
        // it's clickable, not Starsector's dialog chrome.
        float backW = 100f;
        float backH = 28f;
        ButtonWidget back = new ButtonWidget(
                position.getX() + position.getWidth() - backW - pad,
                position.getY() + pad,
                backW, backH,
                onBack);
        widgets.add(back);

        // Small diagnostic square — top-right, logs to confirm input routing.
        float diagW = 24f;
        float diagH = 24f;
        ButtonWidget diag = new ButtonWidget(
                position.getX() + position.getWidth() - diagW - pad,
                position.getY() + position.getHeight() - diagH - pad,
                diagW, diagH,
                () -> LOG.info("MarineOps: widget click registered (input routing OK)"));
        widgets.add(diag);

        widgetsBuilt = true;
    }

    @Override
    public void advance(float amount) {
        dt = amount;
        widgets.advance(amount);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        widgets.processInput(events);
    }

    @Override
    public void render(float alphaMult) {
        if (position == null) return;
        renderer.render(
                position.getX(),
                position.getY(),
                position.getWidth(),
                position.getHeight(),
                dt,
                alphaMult);
        if (widgetsBuilt) widgets.render(alphaMult);
    }
}
