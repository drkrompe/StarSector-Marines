package com.dillon.starsectormarines.render;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.PositionAPI;

/**
 * Panel plugin behind the bridge intel screen. Owns its scene (just a spinning
 * cube placeholder) and hands it to {@link BridgeRenderer} once. The renderer
 * doesn't know or care that it's a cube — see {@link BridgeRenderer#setScene}.
 */
public class BridgePanelPlugin extends BaseCustomUIPanelPlugin {

    private final BridgeRenderer renderer = new BridgeRenderer();
    private final SceneNode cubeNode = new SceneNode();

    private PositionAPI position;
    private float rot;

    public BridgePanelPlugin() {
        cubeNode.drawable = new ProceduralCubeDrawable();
        SceneNode root = new SceneNode();
        root.addChild(cubeNode);
        renderer.setScene(root, new Camera());
    }

    @Override
    public void positionChanged(PositionAPI position) {
        this.position = position;
    }

    @Override
    public void advance(float amount) {
        rot += amount;
        cubeNode.rotation[0] = rot * 0.6f;
        cubeNode.rotation[1] = rot;
    }

    @Override
    public void render(float alphaMult) {
        if (position == null) return;
        renderer.render(
                position.getX(),
                position.getY(),
                position.getWidth(),
                position.getHeight(),
                alphaMult);
    }
}
