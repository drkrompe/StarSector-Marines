package com.dillon.starsectormarines.render;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.PositionAPI;

public class BridgePanelPlugin extends BaseCustomUIPanelPlugin {

    private final BridgeRenderer renderer = new BridgeRenderer();
    private PositionAPI position;
    private float dt;

    @Override
    public void positionChanged(PositionAPI position) {
        this.position = position;
    }

    @Override
    public void advance(float amount) {
        dt = amount;
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
    }
}
